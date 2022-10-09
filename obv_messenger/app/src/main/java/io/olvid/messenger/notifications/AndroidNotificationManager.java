/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.messenger.notifications;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.ContactDetailsActivity;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.webrtc.WebrtcCallActivity;

@SuppressLint("UnspecifiedImmutableFlag")
public class AndroidNotificationManager {
    private static final String DISCUSSION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX = "discussion_";
    private static final String GROUP_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX = "group_";
    private static final String MESSAGE_REACTION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX = "message_reaction_";
    private static final String DISCUSSION_MESSAGE_REACTION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX = "discussion_reaction_";
    private static final String KEY_MESSAGE_NOTIFICATION_CHANNEL_VERSION = "message_channel_version";
    private static final String KEY_DISCUSSION_NOTIFICATION_CHANNEL_VERSION_PREFIX = "discussion_channel_version_";

    public static final String MESSAGE_NOTIFICATION_CHANNEL_ID = "message";
    public static final String UNIFIED_SERVICE_NOTIFICATION_CHANNEL_ID = "unified";
    public static final String WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID = "calls";
    public static final String MEDIA_PLAYER_SERVICE_NOTIFICATION_CHANNEL_ID = "media_player";

    public static final String DISCUSSION_NOTIFICATION_CHANNELS_GROUP_ID = "discussions";
    public static final String DISCUSSION_NOTIFICATION_CHANNEL_ID_PREFIX = "discussion_";

    public static final String KEYCLOAK_NOTIFICATION_CHANNEL_ID = "keycloak";

    private static final long DELAY_BETWEEN_SAME_CHANNEL_VIBRATE = 3_000; // 3 seconds

    private static final NoExceptionSingleThreadExecutor executor = new NoExceptionSingleThreadExecutor("AndroidNotificationManagerExecutor");

    private static final HashSet<Integer> hiddenIdentityNotificationIdsToClear = new HashSet<>();
    private static final HashSet<Long> hiddenIdentityNotificationDiscussionIdsToClear = new HashSet<>();


    public static void clearHiddenIdentityNotifications() {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            for (Integer id: hiddenIdentityNotificationIdsToClear) {
                notificationManager.cancel(id);
            }
            hiddenIdentityNotificationIdsToClear.clear();

            SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();

            for (Long discussionId: hiddenIdentityNotificationDiscussionIdsToClear) {
                notificationManager.cancel(getMessageNotificationId(discussionId));
                editor.remove(DISCUSSION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId);

                notificationManager.cancel(getGroupNotificationId(discussionId));
                editor.remove(GROUP_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId);
            }
            editor.apply();
            hiddenIdentityNotificationDiscussionIdsToClear.clear();
        });
    }

    // region Create notification channels

    public static void createChannels(int lastExecutedBuild) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = App.getContext().getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }

        if (lastExecutedBuild < 89) {
            // delete legacy channels
            notificationManager.deleteNotificationChannel("olvid_message_notification");
            notificationManager.deleteNotificationChannel("olvid_invitation_notification");
            notificationManager.deleteNotificationChannel("olvid_lock_service_notification");
        }

        if (lastExecutedBuild < 124) {
            notificationManager.deleteNotificationChannel("lock_service");
            notificationManager.deleteNotificationChannel("webclient");
        }

        NotificationChannel messageChannel = buildMessageNotificationChannel(getCurrentMessageChannelVersion());

        NotificationChannel unifiedForegroundServiceChannel = new NotificationChannel(
                UNIFIED_SERVICE_NOTIFICATION_CHANNEL_ID,
                App.getContext().getString(R.string.notification_channel_unified_service_name),
                NotificationManager.IMPORTANCE_LOW);
        unifiedForegroundServiceChannel.setDescription(App.getContext().getString(R.string.notification_channel_unified_service_description));
        unifiedForegroundServiceChannel.setShowBadge(false);
        unifiedForegroundServiceChannel.enableVibration(false);
        unifiedForegroundServiceChannel.enableLights(false);
        unifiedForegroundServiceChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

        NotificationChannel webrtcCallServiceChannel = new NotificationChannel(
                WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID,
                App.getContext().getString(R.string.notification_channel_webrtc_call_service_name),
                NotificationManager.IMPORTANCE_HIGH);
        webrtcCallServiceChannel.setDescription(App.getContext().getString(R.string.notification_channel_webrtc_call_service_description));
        webrtcCallServiceChannel.setShowBadge(false);
        webrtcCallServiceChannel.enableVibration(false);
        webrtcCallServiceChannel.enableLights(false);
        webrtcCallServiceChannel.setSound(null, null);
        webrtcCallServiceChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationChannel mediaPlayerServiceChannel = new NotificationChannel(
                MEDIA_PLAYER_SERVICE_NOTIFICATION_CHANNEL_ID,
                App.getContext().getString(R.string.notification_channel_media_player_service_name),
                NotificationManager.IMPORTANCE_LOW);
        mediaPlayerServiceChannel.setDescription(App.getContext().getString(R.string.notification_channel_media_player_service_description));
        mediaPlayerServiceChannel.setShowBadge(false);
        mediaPlayerServiceChannel.enableVibration(false);
        mediaPlayerServiceChannel.enableLights(false);
        mediaPlayerServiceChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        notificationManager.createNotificationChannels(Arrays.asList(
                messageChannel,
                unifiedForegroundServiceChannel,
                webrtcCallServiceChannel,
                mediaPlayerServiceChannel
        ));

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            validateSoundForAndroidPie(null);
        }
    }

    public static void updateMessageChannel(boolean validateSoundForAndroidPie) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = App.getContext().getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            int currentVersion = getCurrentMessageChannelVersion();
            NotificationChannel newMessageChannel = buildMessageNotificationChannel(currentVersion + 1);
            notificationManager.createNotificationChannel(newMessageChannel);
            setCurrentMessageChannelVersion(currentVersion + 1);
            deleteMessageNotificationChannel(notificationManager, currentVersion);

            if (validateSoundForAndroidPie
                    && Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                validateSoundForAndroidPie(null);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private static void validateSoundForAndroidPie(Long discussionId) {
        // test if the notification sound works --> bug on Android 9 (Pie) with sounds on SD card
        try {
            NotificationCompat.Builder builder;
            if (discussionId == null) {
                builder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion());
            } else {
                builder = new NotificationCompat.Builder(App.getContext(), DISCUSSION_NOTIFICATION_CHANNEL_ID_PREFIX + discussionId + "_" + getCurrentDiscussionChannelVersion(discussionId));
            }
            builder.setSmallIcon(R.drawable.ic_o)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentTitle(App.getContext().getString(R.string.text_neutral_notification))
                    .setContentText(App.getContext().getString(R.string.text_neutral_notification_click_to_open));

            int notificationId = getNeutralNotificationId();
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(notificationId, builder.build());

            clearNeutralNotification();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.w("An exception occurred --> we remove the sound from the notification channel");
            SettingsActivity.setMessageRingtone(Uri.EMPTY.toString());
            updateMessageChannel(false);
            App.openAppDialogSdCardRingtoneBuggedAndroid9();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private static NotificationChannel buildMessageNotificationChannel(int version) {
        NotificationChannel messageChannel = new NotificationChannel(
                MESSAGE_NOTIFICATION_CHANNEL_ID + version,
                App.getContext().getString(R.string.notification_channel_message_name),
                NotificationManager.IMPORTANCE_HIGH);
        messageChannel.setDescription(App.getContext().getString(R.string.notification_channel_message_description));
        messageChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        messageChannel.setShowBadge(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            messageChannel.setAllowBubbles(true);
        }

        String colorString = SettingsActivity.getMessageLedColor();
        if (colorString == null) {
            messageChannel.enableLights(false);
        } else {
            int color = 0xff000000 + Integer.parseInt(colorString.substring(1), 16);
            messageChannel.setLightColor(color);
            messageChannel.enableLights(true);
        }

        long[] pattern = SettingsActivity.getMessageVibrationPattern();
        if (pattern == null) {
            messageChannel.enableVibration(false);
        } else {
            messageChannel.setVibrationPattern(pattern);
        }

        messageChannel.setSound(SettingsActivity.getMessageRingtone(), new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).build());

        return messageChannel;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void deleteMessageNotificationChannel(NotificationManager notificationManager, int version) {
        notificationManager.deleteNotificationChannel(MESSAGE_NOTIFICATION_CHANNEL_ID + version);
    }

    private static int getCurrentMessageChannelVersion() {
        return App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE).getInt(KEY_MESSAGE_NOTIFICATION_CHANNEL_VERSION, 0);
    }

    private static void setCurrentMessageChannelVersion(int value) {
        SharedPreferences.Editor editor = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_MESSAGE_NOTIFICATION_CHANNEL_VERSION, value);
        editor.apply();
    }

    public static void updateDiscussionChannel(long discussionId, boolean validateSoundForAndroidPie) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = App.getContext().getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
            int currentVersion = getCurrentDiscussionChannelVersion(discussionId);
            if (discussionCustomization != null && discussionCustomization.prefUseCustomMessageNotification) {
                NotificationChannelGroup notificationGroup = new NotificationChannelGroup(DISCUSSION_NOTIFICATION_CHANNELS_GROUP_ID, App.getContext().getString(R.string.notification_channel_discussion_group_name));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    notificationGroup.setDescription(App.getContext().getString(R.string.notification_channel_discussion_group_description));
                }
                notificationManager.createNotificationChannelGroup(notificationGroup);
                NotificationChannel discussionChannel = buildDiscussionNotificationChannel(discussionId, currentVersion + 1, discussionCustomization);
                notificationManager.createNotificationChannel(discussionChannel);
                setCurrentDiscussionChannelVersion(discussionId, currentVersion + 1);

                if (validateSoundForAndroidPie
                        && Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                    validateSoundForAndroidPie(discussionId);
                }
            }
            deleteDiscussionNotificationChannel(notificationManager, discussionId, currentVersion);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private static NotificationChannel buildDiscussionNotificationChannel(long discussionId, int version, @NonNull DiscussionCustomization discussionCustomization) {
        NotificationChannel discussionChannel = new NotificationChannel(
                DISCUSSION_NOTIFICATION_CHANNEL_ID_PREFIX + discussionId + "_" + version,
                App.getContext().getString(R.string.notification_channel_discussion_name, discussionId),
                NotificationManager.IMPORTANCE_HIGH);
        discussionChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        discussionChannel.setShowBadge(true);
        discussionChannel.setGroup(DISCUSSION_NOTIFICATION_CHANNELS_GROUP_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            discussionChannel.setAllowBubbles(true);
        }

        if (discussionCustomization.prefMessageNotificationLedColor == null) {
            discussionChannel.enableLights(false);
        } else {
            int color = 0xff000000 + Integer.parseInt(discussionCustomization.prefMessageNotificationLedColor.substring(1), 16);
            discussionChannel.setLightColor(color);
            discussionChannel.enableLights(true);
        }

        long[] pattern = null;
        if (discussionCustomization.prefMessageNotificationVibrationPattern != null) {
            try {
                pattern = SettingsActivity.intToVibrationPattern(Integer.parseInt(discussionCustomization.prefMessageNotificationVibrationPattern));
            } catch (Exception ignored) {}
        }
        if (pattern == null) {
            discussionChannel.enableVibration(false);
        } else {
            discussionChannel.setVibrationPattern(pattern);
        }

        Uri uri = Settings.System.DEFAULT_NOTIFICATION_URI;
        if (discussionCustomization.prefMessageNotificationRingtone != null) {
            try {
                uri = Uri.parse(discussionCustomization.prefMessageNotificationRingtone);
            } catch (Exception ignored) {}
        }
        discussionChannel.setSound(uri, new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).build());

        return discussionChannel;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void deleteDiscussionNotificationChannel(NotificationManager notificationManager, long discussionId, int version) {
        notificationManager.deleteNotificationChannel(DISCUSSION_NOTIFICATION_CHANNEL_ID_PREFIX + discussionId + "_" + version);
    }

    private static int getCurrentDiscussionChannelVersion(long discussionId) {
        return App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE).getInt(KEY_DISCUSSION_NOTIFICATION_CHANNEL_VERSION_PREFIX + discussionId, 0);
    }

    private static void setCurrentDiscussionChannelVersion(long discussionId, int value) {
        SharedPreferences.Editor editor = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_DISCUSSION_NOTIFICATION_CHANNEL_VERSION_PREFIX + discussionId, value);
        editor.apply();
    }

    private static String getChannelId(@NonNull NotificationManagerCompat notificationManager, @Nullable DiscussionCustomization discussionCustomization) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && discussionCustomization != null && discussionCustomization.prefUseCustomMessageNotification) {
            String channelId = DISCUSSION_NOTIFICATION_CHANNEL_ID_PREFIX + discussionCustomization.discussionId + "_" + getCurrentDiscussionChannelVersion(discussionCustomization.discussionId);
            if (notificationManager.getNotificationChannel(channelId) != null) {
                return channelId;
            }
        }
        return MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion();
    }

    // endregion

    private static Long currentShowingDiscussionId;

    public static void setCurrentShowingDiscussionId(Long currentShowingDiscussionId) {
        AndroidNotificationManager.currentShowingDiscussionId = currentShowingDiscussionId;
    }

    public static Long getCurrentShowingDiscussionId() {
        return currentShowingDiscussionId;
    }

    // region Neutral notification

    private static void displayNeutralNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion())
                .setSmallIcon(R.drawable.ic_o)
                .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentTitle(App.getContext().getString(R.string.text_neutral_notification))
                .setContentText(App.getContext().getString(R.string.text_neutral_notification_click_to_open))
                .setVibrate(new long[0]);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(SettingsActivity.getMessageRingtone());
        }

        String colorString = SettingsActivity.getMessageLedColor();
        if (colorString != null) {
            int color = 0xff000000 + Integer.parseInt(colorString.substring(1), 16);
            builder.setLights(color, 500, 2000);
        }


        int notificationId = getNeutralNotificationId();

        // CONTENT INTENT
        Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
        PendingIntent contentPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }
        builder.setContentIntent(contentPendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
        notificationManager.notify(notificationId, builder.build());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            vibrate(null);
        }
    }

    public static void clearNeutralNotification() {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getNeutralNotificationId());
        });
    }

    // endregion


    // region Group notification

    public static void displayGroupMemberNotification(Group group, Contact contact, boolean added, long discussionId) {
        executor.execute(() -> {
            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussionId);
            if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications()) {
                return;
            }
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            String channelId = getChannelId(notificationManager, discussionCustomization);
            OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(group.bytesOwnedIdentity);
            if (ownedIdentity == null || ownedIdentity.shouldMuteNotifications()) {
                if (ownedIdentity != null && ownedIdentity.shouldShowNeutralNotification()) {
                    displayNeutralNotification();
                }
                return;
            }
            if (SettingsActivity.isNotificationContentHidden()) {
                displayNeutralNotification();
                return;
            }

            JsonPojoGroupNotification groupNotification = loadGroupNotification(discussionId);
            if (groupNotification == null) {
                groupNotification = new JsonPojoGroupNotification();
                groupNotification.addedMembers = new ArrayList<>();
                groupNotification.removedMembers = new ArrayList<>();
            }
            if (added) {
                groupNotification.addedMembers.add(contact.getCustomDisplayName());
            } else {
                groupNotification.removedMembers.add(contact.getCustomDisplayName());
            }
            saveGroupNotification(discussionId, groupNotification);


            NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(App.getContext(), channelId)
                    .setSmallIcon(R.drawable.ic_o)
                    .setContentTitle(App.getContext().getString(R.string.notification_public_title_group_member));

            NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), channelId)
                    .setSmallIcon(R.drawable.ic_o)
                    .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setPublicVersion(publicBuilder.build())
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentTitle(group.getCustomName())
                    .setVibrate(new long[0]);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (discussionCustomization == null || !discussionCustomization.prefUseCustomMessageNotification) {
                    builder.setSound(SettingsActivity.getMessageRingtone());
                } else {
                    try {
                        Uri uri = Uri.parse(discussionCustomization.prefMessageNotificationRingtone);
                        builder.setSound(uri);
                    } catch (Exception e) {
                        builder.setSound(SettingsActivity.getMessageRingtone());
                    }
                }
            }

            String colorString;
            if (discussionCustomization == null || !discussionCustomization.prefUseCustomMessageNotification) {
                colorString = SettingsActivity.getMessageLedColor();
            } else {
                colorString = discussionCustomization.prefMessageNotificationLedColor;
            }
            if (colorString != null) {
                int color = 0xff000000 + Integer.parseInt(colorString.substring(1), 16);
                builder.setLights(color, 500, 2000);
            }


            InitialView initialView = new InitialView(App.getContext());
            int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
            initialView.setSize(size, size);
            initialView.setGroup(group);
            Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            initialView.drawOnCanvas(new Canvas(largeIcon));
            builder.setLargeIcon(largeIcon);

            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (groupNotification.addedMembers.size() > 0) {
                StyleSpan sp = new StyleSpan(Typeface.BOLD);
                SpannableString ss = new SpannableString(App.getContext().getResources().getQuantityString(R.plurals.notification_text_header_added_count, groupNotification.addedMembers.size(), groupNotification.addedMembers.size()));
                ss.setSpan(sp, 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(ss);
                sb.append(TextUtils.join(App.getContext().getString(R.string.text_contact_names_separator), groupNotification.addedMembers));
                if (groupNotification.removedMembers.size() > 0) {
                    sb.append("\n");
                }
            }


            if (groupNotification.removedMembers.size() > 0) {
                StyleSpan sp = new StyleSpan(Typeface.BOLD);
                SpannableString ss = new SpannableString(App.getContext().getResources().getQuantityString(R.plurals.notification_text_header_removed_count, groupNotification.removedMembers.size(), groupNotification.removedMembers.size()));
                ss.setSpan(sp, 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(ss);
                sb.append(TextUtils.join(App.getContext().getString(R.string.text_contact_names_separator), groupNotification.removedMembers));
            }
            builder.setContentText(sb);

            int notificationId = getGroupNotificationId(discussionId);

            // CONTENT INTENT
            Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
            contentIntent.setAction(MainActivity.FORWARD_ACTION);
            contentIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
            contentIntent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
            contentIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, group.bytesOwnedIdentity);
            PendingIntent contentPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(contentPendingIntent);

            // DISMISS INTENT
            Intent dismissIntent = new Intent(App.getContext(), NotificationActionService.class);
            dismissIntent.setAction(NotificationActionService.ACTION_GROUP_CLEAR);
            dismissIntent.putExtra(NotificationActionService.EXTRA_DISCUSSION_ID, discussionId);
            PendingIntent dismissPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                dismissPendingIntent = PendingIntent.getService(App.getContext(), notificationId, dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                dismissPendingIntent = PendingIntent.getService(App.getContext(), notificationId, dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setDeleteIntent(dismissPendingIntent);

            if (ownedIdentity.isHidden()) {
                hiddenIdentityNotificationDiscussionIdsToClear.add(discussionId);
            }

            notificationManager.notify(notificationId, builder.build());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                vibrate(discussionCustomization);
            }
        });
    }

    public static void clearGroupNotification(final long discussionId) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getGroupNotificationId(discussionId));
            SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(GROUP_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId);
            editor.apply();
        });
    }

    private static JsonPojoGroupNotification loadGroupNotification(long discussionId) {
        SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
        String jsonNotifications = sharedPreferences.getString(GROUP_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId, null);
        JsonPojoGroupNotification groupNotification = null;
        if (jsonNotifications != null) {
            try {
                groupNotification = AppSingleton.getJsonObjectMapper().readValue(jsonNotifications, JsonPojoGroupNotification.class);
            } catch (Exception e) {
                Logger.w("Error parsing JSON from notifications preference.");
                e.printStackTrace();
            }
        }
        return groupNotification;
    }

    private static void saveGroupNotification(long discussionId, JsonPojoGroupNotification groupNotification) {
        try {
            SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            String jsonNotifications = AppSingleton.getJsonObjectMapper().writeValueAsString(groupNotification);
            editor.putString(GROUP_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId, jsonNotifications);
            editor.apply();
        } catch (Exception e) {
            Logger.w("Error storing JSON in notifications preference.");
            e.printStackTrace();
        }
    }

    // endregion


    // region Missed call notification

    public static void displayMissedCallNotification(@NonNull byte[] bytesOwnedIdentity, @NonNull byte[] bytesContactIdentity) {
        executor.execute(() -> {
            OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(bytesOwnedIdentity);
            if (ownedIdentity == null || ownedIdentity.shouldMuteNotifications()) {
                if (ownedIdentity != null && ownedIdentity.shouldShowNeutralNotification()) {
                    displayNeutralNotification();
                }
                return;
            }

            if (SettingsActivity.isNotificationContentHidden()) {
                displayNeutralNotification();
                return;
            }

            Discussion discussion = AppDatabase.getInstance().discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
            if (discussion == null) {
                return;
            }

            displayMissedCallNotificationInternal(discussion, ownedIdentity.isHidden(), null);
        });
    }

    public static void displayMissedCallNotification(@NonNull Discussion discussion, String message) {
        executor.execute(() -> {
            OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(discussion.bytesOwnedIdentity);
            if (ownedIdentity == null || ownedIdentity.shouldMuteNotifications()) {
                if (ownedIdentity != null && ownedIdentity.shouldShowNeutralNotification()) {
                    displayNeutralNotification();
                }
                return;
            }

            displayMissedCallNotificationInternal(discussion, ownedIdentity.isHidden(), message);
        });
    }

    private static void displayMissedCallNotificationInternal(@NonNull Discussion discussion, boolean ownedIdentityIsHidden, String message) {
        // this kind of notification only makes sense for one to one discussions
        if (!discussion.canPostMessages() || discussion.discussionType != Discussion.TYPE_CONTACT) {
            return;
        }

        NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion())
                .setSmallIcon(R.drawable.ic_phone_busy_in)
                .setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_missed_call));


        NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion())
                .setSmallIcon(R.drawable.ic_phone_busy_in)
                .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setPublicVersion(publicBuilder.build())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVibrate(new long[0])
                .setContentTitle(App.getContext().getResources().getString(R.string.notification_title_missed_call, discussion.title));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(SettingsActivity.getMessageRingtone());
        }

        if (message != null) {
            builder.setContentText(App.getContext().getString(R.string.text_you) + App.getContext().getString(R.string.text_name_and_message_separator) + message);
        }

        String colorString = SettingsActivity.getMessageLedColor();
        if (colorString != null) {
            int color = 0xff000000 + Integer.parseInt(colorString.substring(1), 16);
            builder.setLights(color, 500, 2000);
        }

        InitialView initialView = new InitialView(App.getContext());
        int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
        initialView.setSize(size, size);
        initialView.setDiscussion(discussion);
        Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        initialView.drawOnCanvas(new Canvas(largeIcon));
        builder.setLargeIcon(largeIcon);

        // CONTENT INTENT
        Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
        contentIntent.setAction(MainActivity.FORWARD_ACTION);
        contentIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
        contentIntent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussion.id);
        contentIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, discussion.bytesOwnedIdentity);
        PendingIntent contentPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), getMissedCallNotificationId(discussion.id), contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), getMissedCallNotificationId(discussion.id), contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }
        builder.setContentIntent(contentPendingIntent);

        // SEND MESSAGE ACTION
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            RemoteInput remoteInput = new RemoteInput.Builder(NotificationActionService.KEY_TEXT_REPLY)
                    .setLabel(App.getContext().getString(R.string.hint_notification_message))
                    .build();

            Intent sendMessageIntent = new Intent(App.getContext(), NotificationActionService.class);
            sendMessageIntent.setAction(NotificationActionService.ACTION_MISSED_CALL_MESSAGE);
            sendMessageIntent.putExtra(NotificationActionService.EXTRA_DISCUSSION_ID, discussion.id);
            PendingIntent sendMessagePendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                sendMessagePendingIntent = PendingIntent.getService(App.getContext(), getMissedCallNotificationId(discussion.id), sendMessageIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            } else {
                sendMessagePendingIntent = PendingIntent.getService(App.getContext(), getMissedCallNotificationId(discussion.id), sendMessageIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            NotificationCompat.Action sendMessageAction = new NotificationCompat.Action.Builder(R.drawable.ic_send, App.getContext().getString(R.string.notification_action_send_message), sendMessagePendingIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(SettingsActivity.isNotificationSuggestionAllowed())
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .build();
            builder.addAction(sendMessageAction);
        }

        // CALL BACK ACTION
        Intent callBackIntent = new Intent(App.getContext(), WebrtcCallActivity.class);
        callBackIntent.setAction(WebrtcCallActivity.CALL_BACK_ACTION);
        callBackIntent.putExtra(WebrtcCallActivity.CALL_BACK_EXTRA_BYTES_OWNED_IDENTITY, discussion.bytesOwnedIdentity);
        callBackIntent.putExtra(WebrtcCallActivity.CALL_BACK_EXTRA_BYTES_CONTACT_IDENTITY, discussion.bytesDiscussionIdentifier);
        callBackIntent.putExtra(WebrtcCallActivity.CALL_BACK_EXTRA_DISCUSSION_ID, discussion.id);
        callBackIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent callBackPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            callBackPendingIntent = PendingIntent.getActivity(App.getContext(), getMissedCallNotificationId(discussion.id), callBackIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            callBackPendingIntent = PendingIntent.getActivity(App.getContext(), getMissedCallNotificationId(discussion.id), callBackIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Action callBackAction = new NotificationCompat.Action.Builder(R.drawable.ic_answer_call, App.getContext().getString(R.string.notification_action_call_back), callBackPendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL)
                .setShowsUserInterface(true)
                .build();
        builder.addAction(callBackAction);


        int notificationId = getMissedCallNotificationId(discussion.id);
        if (ownedIdentityIsHidden) {
            hiddenIdentityNotificationIdsToClear.add(notificationId);
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
        notificationManager.notify(notificationId, builder.build());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            vibrate(null);
        }
    }


    public static void clearMissedCallNotification(long discussionId) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getMissedCallNotificationId(discussionId));
        });
    }

    public static void clearAllMissedCallNotifications() {
        executor.execute(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationManager notificationManager = (NotificationManager) App.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager == null) {
                    return;
                }
                for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
                    if ((notification.getId() & 0xff000000) == 0x4000000) {
                        notificationManager.cancel(notification.getId());
                    }
                }
            }
        });
    }

    // endregion




    // region Message & Reactions notification

    @SuppressLint("UseSparseArrays")
    private static final HashMap<Integer, Long> messageLastVibrationTimestamp = new HashMap<>();

    public static void displayReceivedMessageNotification(@NonNull Discussion discussion, @Nullable Message message, @Nullable Contact contact, @Nullable OwnedIdentity ownedIdentity) {
        executor.execute(() -> {
            if ((currentShowingDiscussionId != null) && (discussion.id == currentShowingDiscussionId)) {
                return;
            }
            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussion.id);
            if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications()) {
                return;
            }
            if (ownedIdentity != null && ownedIdentity.shouldMuteNotifications()) {
                if (ownedIdentity.shouldShowNeutralNotification()) {
                    displayNeutralNotification();
                }
                return;
            }
            if (SettingsActivity.isNotificationContentHidden()) {
                displayNeutralNotification();
                return;
            }

            JsonPojoDiscussionNotification discussionNotification;
            if (message == null) {
                discussionNotification = loadDiscussionNotification(discussion.id);
            } else {
                discussionNotification = addMessageNotification(discussion, message, contact, ownedIdentity);
            }
            if (discussionNotification == null) {
                clearReceivedMessageAndReactionsNotification(discussion.id);
                return;
            }

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            NotificationCompat.Builder builder = getEmptyMessageNotificationBuilder(notificationManager, discussion, discussionCustomization, discussionNotification, contact != null);

            populateMessageNotificationBuilder(builder, discussion, discussionNotification);

            if (ownedIdentity != null && ownedIdentity.isHidden()) {
                hiddenIdentityNotificationDiscussionIdsToClear.add(discussion.id);
            }

            int notificationId = getMessageNotificationId(discussion.id);

            notificationManager.notify(notificationId, builder.build());
            if (contact != null) {
                if (messageLastVibrationTimestamp.containsKey(notificationId)) {
                    Long timestamp = messageLastVibrationTimestamp.get(notificationId);
                    if (timestamp == null || timestamp < (System.currentTimeMillis() - DELAY_BETWEEN_SAME_CHANNEL_VIBRATE)) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            vibrate(discussionCustomization);
                        }
                        messageLastVibrationTimestamp.put(notificationId, System.currentTimeMillis());
                    }
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        vibrate(discussionCustomization);
                    }
                    messageLastVibrationTimestamp.put(notificationId, System.currentTimeMillis());
                }
            }
        });
    }

    public static void remoteDeleteMessageNotification(@NonNull Discussion discussion, long messageId) {
        executor.execute(() -> {
            clearMessageReactionsNotification(messageId);

            JsonPojoDiscussionNotification discussionNotification = loadDiscussionNotification(discussion.id);
            if (discussionNotification == null) {
                return;
            }
            boolean modified = false;
            for (JsonPojoDiscussionNotification.JsonPojoMessageNotification messageNotification : discussionNotification.messageNotifications) {
                if (messageNotification.messageId == messageId) {
                    modified = true;
                    messageNotification.content = App.getContext().getString(R.string.text_message_content_remote_deleted);
                    break;
                }
            }
            if (modified) {
                saveDiscussionNotification(discussion.id, discussionNotification);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
                NotificationCompat.Builder builder = getEmptyMessageNotificationBuilder(notificationManager, discussion, null, discussionNotification, false);
                populateMessageNotificationBuilder(builder, discussion, discussionNotification);
                notificationManager.notify(getMessageNotificationId(discussion.id), builder.build());
            }
        });
    }

    public static void expireMessageNotification(long discussionId, long messageId) {
        executor.execute(() -> {
            clearMessageReactionsNotification(messageId);

            JsonPojoDiscussionNotification discussionNotification = loadDiscussionNotification(discussionId);
            if (discussionNotification == null) {
                return;
            }
            boolean modified = false;
            for (JsonPojoDiscussionNotification.JsonPojoMessageNotification messageNotification : discussionNotification.messageNotifications) {
                if (messageNotification.messageId == messageId) {
                    modified = true;
                    messageNotification.content = App.getContext().getString(R.string.text_message_content_wiped);
                    break;
                }
            }
            if (modified) {
                saveDiscussionNotification(discussionId, discussionNotification);
                Discussion discussion = AppDatabase.getInstance().discussionDao().getById(discussionId);
                if (discussion == null) {
                    return;
                }

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
                NotificationCompat.Builder builder = getEmptyMessageNotificationBuilder(notificationManager, discussion, null, discussionNotification, false);
                populateMessageNotificationBuilder(builder, discussion, discussionNotification);
                notificationManager.notify(getMessageNotificationId(discussionId), builder.build());
            }
        });
    }

    public static void editMessageNotification(@NonNull Discussion discussion, long messageId, String newContent) {
        executor.execute(() -> {
            JsonPojoDiscussionNotification discussionNotification = loadDiscussionNotification(discussion.id);
            if (discussionNotification == null) {
                return;
            }
            boolean modified = false;
            for (JsonPojoDiscussionNotification.JsonPojoMessageNotification messageNotification : discussionNotification.messageNotifications) {
                if (messageNotification.messageId == messageId) {
                    modified = true;
                    messageNotification.content = newContent;
                    break;
                }
            }
            if (modified) {
                saveDiscussionNotification(discussion.id, discussionNotification);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
                NotificationCompat.Builder builder = getEmptyMessageNotificationBuilder(notificationManager, discussion, null, discussionNotification, false);
                populateMessageNotificationBuilder(builder, discussion, discussionNotification);
                notificationManager.notify(getMessageNotificationId(discussion.id), builder.build());
            }
        });
    }

    private static JsonPojoDiscussionNotification loadDiscussionNotification(long discussionId) {
        SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
        String jsonNotifications = sharedPreferences.getString(DISCUSSION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId, null);
        JsonPojoDiscussionNotification discussionNotification = null;
        if (jsonNotifications != null) {
            try {
                discussionNotification = AppSingleton.getJsonObjectMapper().readValue(jsonNotifications, JsonPojoDiscussionNotification.class);
            } catch (Exception e) {
                Logger.w("Error parsing JSON from notifications preference.");
                e.printStackTrace();
            }
        }
        return discussionNotification;
    }

    private static void saveDiscussionNotification(long discussionId, JsonPojoDiscussionNotification discussionNotification) {
        try {
            SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            String jsonNotifications = AppSingleton.getJsonObjectMapper().writeValueAsString(discussionNotification);
            editor.putString(DISCUSSION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId, jsonNotifications);
            editor.apply();
        } catch (Exception e) {
            Logger.w("Error storing JSON in notifications preference.");
            e.printStackTrace();
        }
    }

    @Nullable
    private static JsonPojoDiscussionNotification addMessageNotification(@NonNull Discussion discussion, @Nullable Message message, @Nullable Contact contact, @Nullable OwnedIdentity ownedIdentity) {
        JsonPojoDiscussionNotification discussionNotification = loadDiscussionNotification(discussion.id);
        if (discussionNotification == null) {
            if (ownedIdentity == null) {
                return null;
            }
            discussionNotification = new JsonPojoDiscussionNotification();
            if (discussion.canPostMessages() && discussion.discussionType != Discussion.TYPE_CONTACT) {
                discussionNotification.discussionInitialBytes = discussion.bytesDiscussionIdentifier;
                discussionNotification.isGroup = true;
            } else {
                if (contact == null) {
                    return null;
                }
                discussionNotification.discussionInitialBytes = contact.bytesContactIdentity;
                discussionNotification.isGroup = false;
            }
            discussionNotification.title = discussion.title;
            discussionNotification.photoUrl = discussion.photoUrl;
            discussionNotification.bytesOwnedIdentity = ownedIdentity.bytesOwnedIdentity;
            discussionNotification.ownDisplayName = ownedIdentity.getCustomDisplayName();
            discussionNotification.ownPhotoUrl = ownedIdentity.photoUrl;
        }

        if (discussionNotification.messageNotifications == null) {
            discussionNotification.messageNotifications = new ArrayList<>();
        }

        if (message != null) {
            Collections.sort(discussionNotification.messageNotifications);
            if (contact != null) {
                discussionNotification.messageNotifications.add(new JsonPojoDiscussionNotification.JsonPojoMessageNotification(message.id, (long)message.sortIndex, contact.getCustomDisplayName(), contact.getCustomPhotoUrl(), contact.bytesContactIdentity, message.getStringContent(App.getContext())));
            } else {
                discussionNotification.messageNotifications.add(new JsonPojoDiscussionNotification.JsonPojoMessageNotification(message.id, (long)message.sortIndex, null, null, null, message.getStringContent(App.getContext())));
            }
            saveDiscussionNotification(discussion.id, discussionNotification);
        }
        return discussionNotification;
    }

    private static void populateMessageNotificationBuilder(NotificationCompat.Builder builder, @NonNull Discussion discussion, JsonPojoDiscussionNotification discussionNotification) {
        if (discussionNotification == null) {
            SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
            String jsonNotifications = sharedPreferences.getString(DISCUSSION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussion.id, null);
            if (jsonNotifications != null) {
                try {
                    discussionNotification = AppSingleton.getJsonObjectMapper().readValue(jsonNotifications, JsonPojoDiscussionNotification.class);
                } catch (IOException e) {
                    Logger.w("Error parsing JSON from notifications preference.");
                }
            }
        }

        if (discussionNotification == null || discussionNotification.messageNotifications == null || discussionNotification.messageNotifications.size() == 0) {
            return;
        }

        Collections.sort(discussionNotification.messageNotifications);

        InitialView initialView = new InitialView(App.getContext());
        if (discussionNotification.ownPhotoUrl == null) {
            initialView.setInitial(discussionNotification.bytesOwnedIdentity, StringUtils.getInitial(discussionNotification.ownDisplayName));
        } else {
            initialView.setPhotoUrl(discussionNotification.bytesOwnedIdentity, discussionNotification.ownPhotoUrl);
        }
        int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
        initialView.setSize(size, size);
        Bitmap personIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        initialView.drawOnCanvas(new Canvas(personIcon));

        Person.Builder myBuilder = new Person.Builder()
                .setIcon(IconCompat.createWithBitmap(personIcon))
                .setName(App.getContext().getString(R.string.text_you));

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(myBuilder.build());
        if (discussionNotification.isGroup) {
            messagingStyle.setConversationTitle(discussion.title);
            messagingStyle.setGroupConversation(true);
        } else {
            messagingStyle.setGroupConversation(false);
        }
        for (JsonPojoDiscussionNotification.JsonPojoMessageNotification messageNotification: discussionNotification.messageNotifications) {
            messagingStyle.addMessage(messageNotification.getMessage());
        }
        builder.setStyle(messagingStyle);
    }

    private static NotificationCompat.Builder getEmptyMessageNotificationBuilder(@NonNull NotificationManagerCompat notificationManager, @NonNull Discussion discussion, DiscussionCustomization discussionCustomization, JsonPojoDiscussionNotification discussionNotification, boolean withSound) {
        int messageCount = discussionNotification.messageNotifications.size();

        String channelId = getChannelId(notificationManager, discussionCustomization);

        NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(App.getContext(), channelId)
                .setSmallIcon(R.drawable.ic_o)
                .setContentTitle(App.getContext().getResources().getQuantityString(R.plurals.notification_public_title_new_messages, messageCount, messageCount));


        NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), channelId)
                .setSmallIcon(R.drawable.ic_o)
                .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                .setAllowSystemGeneratedContextualActions(SettingsActivity.isNotificationSuggestionAllowed())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setPublicVersion(publicBuilder.build())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setNumber(messageCount)
                .setVibrate(new long[0]);

        String colorString;
        if (discussionCustomization == null || !discussionCustomization.prefUseCustomMessageNotification) {
            colorString = SettingsActivity.getMessageLedColor();
        } else {
            colorString = discussionCustomization.prefMessageNotificationLedColor;
        }
        if (colorString != null) {
            int color = 0xff000000 + Integer.parseInt(colorString.substring(1), 16);
            builder.setLights(color, 500, 2000);
        }

        if (withSound) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (discussionCustomization == null || !discussionCustomization.prefUseCustomMessageNotification) {
                    builder.setSound(SettingsActivity.getMessageRingtone());
                } else {
                    try {
                        Uri uri = Uri.parse(discussionCustomization.prefMessageNotificationRingtone);
                        builder.setSound(uri);
                    } catch (Exception e) {
                        builder.setSound(SettingsActivity.getMessageRingtone());
                    }
                }
            }
        } else {
            builder.setOnlyAlertOnce(true);
        }

        InitialView initialView = new InitialView(App.getContext());
        int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
        initialView.setSize(size, size);
        initialView.setDiscussion(discussion);
        Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        initialView.drawOnCanvas(new Canvas(largeIcon));
        builder.setLargeIcon(largeIcon);

        // CONTENT INTENT
        Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
        contentIntent.setAction(MainActivity.FORWARD_ACTION);
        contentIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
        contentIntent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussion.id);
        contentIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, discussion.bytesOwnedIdentity);
        PendingIntent contentPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), getMessageNotificationId(discussion.id), contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), getMessageNotificationId(discussion.id), contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }
        builder.setContentIntent(contentPendingIntent);

        // DISMISS INTENT
        Intent dismissIntent = new Intent(App.getContext(), NotificationActionService.class);
        dismissIntent.setAction(NotificationActionService.ACTION_DISCUSSION_CLEAR);
        dismissIntent.putExtra(NotificationActionService.EXTRA_DISCUSSION_ID, discussion.id);
        PendingIntent dismissPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            dismissPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationId(discussion.id), dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            dismissPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationId(discussion.id), dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }
        builder.setDeleteIntent(dismissPendingIntent);

        // REPLY ACTION
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            RemoteInput remoteInput = new RemoteInput.Builder(NotificationActionService.KEY_TEXT_REPLY)
                    .setLabel(App.getContext().getString(R.string.hint_notification_reply))
                    .build();

            Intent replyIntent = new Intent(App.getContext(), NotificationActionService.class);
            replyIntent.setAction(NotificationActionService.ACTION_DISCUSSION_REPLY);
            replyIntent.putExtra(NotificationActionService.EXTRA_DISCUSSION_ID, discussion.id);
            PendingIntent replyPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                replyPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationId(discussion.id), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            } else {
                replyPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationId(discussion.id), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(R.drawable.ic_send, App.getContext().getString(R.string.notification_action_reply), replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(SettingsActivity.isNotificationSuggestionAllowed())
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .build();
            builder.addAction(replyAction);
        }

        // MARK AS READ ACTION
        Intent markAsReadIntent = new Intent(App.getContext(), NotificationActionService.class);
        markAsReadIntent.setAction(NotificationActionService.ACTION_DISCUSSION_MARK_AS_READ);
        markAsReadIntent.putExtra(NotificationActionService.EXTRA_DISCUSSION_ID, discussion.id);
        PendingIntent markAsReadPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            markAsReadPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationId(discussion.id), markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            markAsReadPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationId(discussion.id), markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Action markAsReadAction = new NotificationCompat.Action.Builder(R.drawable.ic_ok, App.getContext().getString(R.string.notification_action_mark_as_read), markAsReadPendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build();
        builder.addAction(markAsReadAction);


        // MUTE ACTION
        Intent muteIntent = new Intent(App.getContext(), MuteDiscussionDialogActivity.class);
        muteIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        muteIntent.putExtra(MuteDiscussionDialogActivity.DISCUSSION_ID_INTENT_EXTRA, discussion.id);
        PendingIntent mutePendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mutePendingIntent = PendingIntent.getActivity(App.getContext(), getMessageNotificationId(discussion.id), muteIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            mutePendingIntent = PendingIntent.getActivity(App.getContext(), getMessageNotificationId(discussion.id), muteIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Action muteAction = new NotificationCompat.Action.Builder(R.drawable.ic_notification_muted, App.getContext().getString(R.string.notification_action_mute), mutePendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE)
                .setShowsUserInterface(true)
                .build();
        builder.addAction(muteAction);

        return builder;
    }


    public static void displayReactionNotification(OwnedIdentity ownedIdentity, Discussion discussion, Message message, @Nullable String emoji, Contact contact) {
        executor.execute(() -> {
            DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussion.id);
            if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications()) {
                return;
            }
            if (ownedIdentity != null && ownedIdentity.shouldMuteNotifications()) {
                return;
            }
            if (SettingsActivity.isNotificationContentHidden()) {
                return;
            }

            SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);

            JsonMessageReactionsNotification jsonMessageReactionsNotification = null;
            try {
                String previousReactionsString = sharedPreferences.getString(MESSAGE_REACTION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + message.id, null);
                if (previousReactionsString != null) {
                    jsonMessageReactionsNotification = AppSingleton.getJsonObjectMapper().readValue(previousReactionsString, JsonMessageReactionsNotification.class);
                    if (jsonMessageReactionsNotification.reactions == null) {
                        jsonMessageReactionsNotification.reactions = new ArrayList<>();
                    }
                }
            } catch (Exception ignored) {}
            if (jsonMessageReactionsNotification == null) {
                jsonMessageReactionsNotification = new JsonMessageReactionsNotification();
                jsonMessageReactionsNotification.reactions = new ArrayList<>();
            }
            boolean found = false;
            for (JsonMessageReactionsNotification.JsonReaction jsonReaction : jsonMessageReactionsNotification.reactions) {
                if (Arrays.equals(jsonReaction.bytesContactIdentity, contact.bytesContactIdentity)) {
                    found = true;
                    if (emoji == null) {
                        jsonMessageReactionsNotification.reactions.remove(jsonReaction);
                    } else {
                        jsonReaction.emoji = emoji;
                    }
                    break;
                }
            }

            if (!found) {
                if (emoji == null) {
                    return;
                }
                JsonMessageReactionsNotification.JsonReaction newJsonReaction = new JsonMessageReactionsNotification.JsonReaction();
                newJsonReaction.bytesContactIdentity = contact.bytesContactIdentity;
                newJsonReaction.contactDisplayName = contact.getCustomDisplayName();
                newJsonReaction.emoji = emoji;
                jsonMessageReactionsNotification.reactions.add(newJsonReaction);
            }

            SharedPreferences.Editor editor = sharedPreferences.edit();
            if (jsonMessageReactionsNotification.reactions.isEmpty()) {
                editor.remove(MESSAGE_REACTION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + message.id);
                editor.apply();
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
                notificationManager.cancel(getReactionNotificationId(message.id));
                return;
            } else if (jsonMessageReactionsNotification.reactions.size() == 1 && !found) {
                JsonDiscussionMessageReactionsNotification jsonDiscussionMessageReactionsNotification = null;

                String discussionMessageIdString = sharedPreferences.getString(DISCUSSION_MESSAGE_REACTION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussion.id, null);
                if (discussionMessageIdString != null) {
                    try {
                        jsonDiscussionMessageReactionsNotification = AppSingleton.getJsonObjectMapper().readValue(discussionMessageIdString, JsonDiscussionMessageReactionsNotification.class);
                        if (jsonDiscussionMessageReactionsNotification.messageIds == null) {
                            jsonDiscussionMessageReactionsNotification.messageIds = new ArrayList<>();
                        }
                    } catch (Exception ignored) { }
                }
                if (jsonDiscussionMessageReactionsNotification == null) {
                    jsonDiscussionMessageReactionsNotification = new JsonDiscussionMessageReactionsNotification();
                    jsonDiscussionMessageReactionsNotification.messageIds = new ArrayList<>();
                }
                jsonDiscussionMessageReactionsNotification.messageIds.add(message.id);
                try {
                    editor.putString(DISCUSSION_MESSAGE_REACTION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussion.id, AppSingleton.getJsonObjectMapper().writeValueAsString(jsonDiscussionMessageReactionsNotification));
                    editor.apply();
                } catch (Exception ignored) { }
            }

            try {
                editor.putString(MESSAGE_REACTION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + message.id, AppSingleton.getJsonObjectMapper().writeValueAsString(jsonMessageReactionsNotification));
                editor.apply();
            } catch (Exception ignored) { }


            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            String channelId = getChannelId(notificationManager, discussionCustomization);

            NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(App.getContext(), channelId)
                    .setSmallIcon(R.drawable.ic_o)
                    .setContentTitle(App.getContext().getString(R.string.notification_public_title_new_reaction));


            NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), channelId)
                    .setSmallIcon(R.drawable.ic_o)
                    .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setPublicVersion(publicBuilder.build())
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVibrate(new long[0]);

            String colorString;
            if (discussionCustomization == null || !discussionCustomization.prefUseCustomMessageNotification) {
                colorString = SettingsActivity.getMessageLedColor();
            } else {
                colorString = discussionCustomization.prefMessageNotificationLedColor;
            }
            if (colorString != null) {
                int color = 0xff000000 + Integer.parseInt(colorString.substring(1), 16);
                builder.setLights(color, 500, 2000);
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (discussionCustomization == null || !discussionCustomization.prefUseCustomMessageNotification) {
                    builder.setSound(SettingsActivity.getMessageRingtone());
                } else {
                    try {
                        Uri uri = Uri.parse(discussionCustomization.prefMessageNotificationRingtone);
                        builder.setSound(uri);
                    } catch (Exception e) {
                        builder.setSound(SettingsActivity.getMessageRingtone());
                    }
                }
            }

            InitialView initialView = new InitialView(App.getContext());
            int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
            initialView.setSize(size, size);
            initialView.setDiscussion(discussion);
            Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            initialView.drawOnCanvas(new Canvas(largeIcon));
            builder.setLargeIcon(largeIcon);

            String messageContent = message.getStringContent(App.getContext());
            if (message.jsonExpiration != null) {
                try {
                    Message.JsonExpiration jsonExpiration = AppSingleton.getJsonObjectMapper().readValue(message.jsonExpiration, Message.JsonExpiration.class);
                    if (jsonExpiration != null) {
                        if ((jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce()) || jsonExpiration.getVisibilityDuration() != null) {
                            messageContent = App.getContext().getString(R.string.text_message_content_hidden);
                        }
                    }
                } catch (Exception e) {
                    messageContent = "";
                }
            }

            if (messageContent.length() == 0) {
                messageContent = App.getContext().getString(R.string.your_message);
            }
            if (jsonMessageReactionsNotification.reactions.size() == 1) {
                JsonMessageReactionsNotification.JsonReaction jsonReaction = jsonMessageReactionsNotification.reactions.get(0);
                builder.setContentTitle(jsonReaction.contactDisplayName);
                builder.setContentText(App.getContext().getString(R.string.notification_text_reacted_to, jsonReaction.emoji, messageContent));
            } else {
                builder.setContentTitle(App.getContext().getString(R.string.x_reactions_to, jsonMessageReactionsNotification.reactions.size(), messageContent));
                NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
                for (int i = Math.max(0, jsonMessageReactionsNotification.reactions.size() - 5); i < jsonMessageReactionsNotification.reactions.size(); i++) {
                    JsonMessageReactionsNotification.JsonReaction jsonReaction = jsonMessageReactionsNotification.reactions.get(i);
                    inboxStyle.addLine(App.getContext().getString(R.string.notification_text_xx_reacted, jsonReaction.contactDisplayName, jsonReaction.emoji));
                }
                builder.setStyle(inboxStyle);
            }

            int notificationId = getReactionNotificationId(message.id);

            // CONTENT INTENT
            Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
            contentIntent.setAction(MainActivity.FORWARD_ACTION);
            contentIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
            contentIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, discussion.bytesOwnedIdentity);
            contentIntent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussion.id);
            contentIntent.putExtra(DiscussionActivity.MESSAGE_ID_INTENT_EXTRA, message.id);
            PendingIntent contentPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(contentPendingIntent);

            // DISMISS INTENT
            Intent dismissIntent = new Intent(App.getContext(), NotificationActionService.class);
            dismissIntent.setAction(NotificationActionService.ACTION_MESSAGE_REACTION_CLEAR);
            dismissIntent.putExtra(NotificationActionService.EXTRA_MESSAGE_ID, message.id);
            PendingIntent dismissPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                dismissPendingIntent = PendingIntent.getService(App.getContext(), notificationId, dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                dismissPendingIntent = PendingIntent.getService(App.getContext(), notificationId, dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setDeleteIntent(dismissPendingIntent);

            if (ownedIdentity != null && ownedIdentity.isHidden()) {
                hiddenIdentityNotificationIdsToClear.add(notificationId);
            }

            notificationManager.notify(notificationId, builder.build());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                vibrate(discussionCustomization);
            }
        });
    }

    public static void clearReceivedMessageAndReactionsNotification(final long discussionId) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getMessageNotificationId(discussionId));
            SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(DISCUSSION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId);
            editor.apply();
            String discussionReactionMessageIds = sharedPreferences.getString(DISCUSSION_MESSAGE_REACTION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId, null);
            if (discussionReactionMessageIds != null) {
                try {
                    JsonDiscussionMessageReactionsNotification jsonDiscussionMessageReactionsNotification = AppSingleton.getJsonObjectMapper().readValue(discussionReactionMessageIds, JsonDiscussionMessageReactionsNotification.class);
                    if (jsonDiscussionMessageReactionsNotification.messageIds != null) {
                        for (long messageId : jsonDiscussionMessageReactionsNotification.messageIds) {
                            clearMessageReactionsNotification(messageId);
                        }
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    public static void clearMessageReactionsNotification(final long messageId) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getReactionNotificationId(messageId));
            SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(MESSAGE_REACTION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + messageId);
            editor.apply();
        });
    }

    // endregion


    // region Invitation notification

    public static void displayInvitationNotification(Invitation invitation) {
        executor.execute(() -> {
            OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(invitation.bytesOwnedIdentity);
            if (ownedIdentity == null || ownedIdentity.shouldMuteNotifications()) {
                if (ownedIdentity != null && ownedIdentity.shouldShowNeutralNotification()) {
                    displayNeutralNotification();
                }
                return;
            }
            if (SettingsActivity.isNotificationContentHidden()) {
                displayNeutralNotification();
                return;
            }

            Intent intent = new Intent(App.getContext(), MainActivity.class);
            if (invitation.categoryId == ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY) {
                // for one to one invites --> direct to the contact details card (this allows to see pending details)
                intent.setAction(MainActivity.FORWARD_ACTION);
                intent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, ContactDetailsActivity.class.getName());
                intent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, invitation.bytesOwnedIdentity);
                intent.putExtra(ContactDetailsActivity.CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA, invitation.bytesOwnedIdentity);
                intent.putExtra(ContactDetailsActivity.CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA, invitation.bytesContactIdentity);
            } else {
                intent.putExtra(MainActivity.TAB_TO_SHOW_INTENT_EXTRA, MainActivity.INVITATIONS_TAB);
                intent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, invitation.bytesOwnedIdentity);
            }
            PendingIntent pendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getActivity(App.getContext(), getInvitationNotificationId(invitation.dialogUuid), intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = PendingIntent.getActivity(App.getContext(), getInvitationNotificationId(invitation.dialogUuid), intent, PendingIntent.FLAG_CANCEL_CURRENT);
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion());
            builder.setSmallIcon(R.drawable.ic_o)
                    .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVibrate(new long[0])
                    .setContentIntent(pendingIntent);

            String colorString = SettingsActivity.getMessageLedColor();
            if (colorString != null) {
                int color = 0xff000000 + Integer.parseInt(colorString.substring(1), 16);
                builder.setLights(color, 500, 2000);
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setSound(SettingsActivity.getMessageRingtone());
            }

            NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion());
            publicBuilder.setSmallIcon(R.drawable.ic_o);

            InitialView initialView = new InitialView(App.getContext());
            switch (invitation.associatedDialog.getCategory().getId()) {
                case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY: {
                    String displayName;
                    try {
                        JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(invitation.associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                        displayName = identityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                    } catch (Exception e) {
                        return;
                    }
                    // We do not have a photoUrl yet
                    initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), StringUtils.getInitial(displayName));
                    builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_new_invitation, displayName));
                    builder.setContentText(App.getContext().getString(R.string.invitation_status_accept_invite));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_new_invitation));
                    break;
                }
                case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY: {
                    String displayName;
                    try {
                        JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(invitation.associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                        displayName = identityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                    } catch (Exception e) {
                        return;
                    }
                    // We do not have a photoUrl yet
                    initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), StringUtils.getInitial(displayName));
                    builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_exchange_sas, displayName));
                    builder.setContentText(App.getContext().getResources().getString(R.string.notification_content_exchange_sas, new String(invitation.associatedDialog.getCategory().getSasToDisplay(), StandardCharsets.UTF_8)));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_exchange_sas));
                    break;
                }
                case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                {
                    String displayName;
                    try {
                        JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(invitation.associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                        displayName = identityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                    } catch (Exception e) {
                        return;
                    }
                    Contact mediator = AppDatabase.getInstance().contactDao().get(invitation.associatedDialog.getBytesOwnedIdentity(), invitation.associatedDialog.getCategory().getBytesMediatorOrGroupOwnerIdentity());
                    if (mediator == null) {
                        return;
                    }
                    // We do not have a photoUrl yet
                    String mediatorDisplayName = mediator.getCustomDisplayName();
                    initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), StringUtils.getInitial(displayName));
                    builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_new_invitation, displayName));
                    builder.setContentText(App.getContext().getString(R.string.notification_content_mediator_invite, mediatorDisplayName));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_new_invitation));
                    break;
                }
                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY: {
                    Contact groupOwner = AppDatabase.getInstance().contactDao().get(invitation.associatedDialog.getBytesOwnedIdentity(), invitation.associatedDialog.getCategory().getBytesMediatorOrGroupOwnerIdentity());
                    if (groupOwner == null) {
                        return;
                    }
                    // We do not have a photoUrl yet
                    String groupOwnerDisplayName = groupOwner.getCustomDisplayName();
                    initialView.setGroup(invitation.associatedDialog.getCategory().getBytesGroupOwnerAndUid());
                    try {
                        JsonGroupDetails groupDetails = AppSingleton.getJsonObjectMapper().readValue(invitation.associatedDialog.getCategory().getSerializedGroupDetails(), JsonGroupDetails.class);
                        builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_group_invitation, groupDetails.getName()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    builder.setContentText(App.getContext().getResources().getString(R.string.notification_content_group_invite, groupOwnerDisplayName));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_group_invitation));
                    break;
                }
                case ObvDialog.Category.MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY: {
                    String displayName;
                    try {
                        JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(invitation.associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                        displayName = identityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                    } catch (Exception e) {
                        return;
                    }
                    // we do not have a photoUrl yet, just show the initial
                    initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), StringUtils.getInitial(displayName));
                    builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_contact_added, displayName));
                    builder.setContentText(App.getContext().getString(R.string.notification_content_contact_added));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_contact_added));
                    break;
                }
                case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY: {
                    Contact contact = AppDatabase.getInstance().contactDao().get(invitation.bytesOwnedIdentity, invitation.bytesContactIdentity);
                    if (contact == null) {
                        return;
                    }
                    initialView.setContact(contact);
                    builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_one_to_one_invitation, contact.getCustomDisplayName()));
                    builder.setContentText(App.getContext().getResources().getString(R.string.notification_content_one_to_one_invitation));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_one_to_one_invitation));
                    break;
                }
                case ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY: {
                    Contact inviter = AppDatabase.getInstance().contactDao().get(invitation.associatedDialog.getBytesOwnedIdentity(), invitation.associatedDialog.getCategory().getBytesMediatorOrGroupOwnerIdentity());
                    if (inviter == null) {
                        return;
                    }
                    ObvGroupV2 groupV2 = invitation.associatedDialog.getCategory().getObvGroupV2();
                    initialView.setGroup(groupV2.groupIdentifier.getBytes());
                    try {
                        JsonGroupDetails groupDetails = AppSingleton.getJsonObjectMapper().readValue(groupV2.detailsAndPhotos.serializedGroupDetails, JsonGroupDetails.class);
                        if (groupDetails.getName() == null) {
                            builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_unnamed_group_invitation));
                        } else {
                            builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_group_invitation, groupDetails.getName()));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    builder.setContentText(App.getContext().getResources().getString(R.string.notification_content_group_invite, inviter.getCustomDisplayName()));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_group_invitation));
                    break;
                }
                case ObvDialog.Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY: {
                    ObvGroupV2 groupV2 = invitation.associatedDialog.getCategory().getObvGroupV2();
                    initialView.setGroup(groupV2.groupIdentifier.getBytes());
                    try {
                        JsonGroupDetails groupDetails = AppSingleton.getJsonObjectMapper().readValue(groupV2.detailsAndPhotos.serializedGroupDetails, JsonGroupDetails.class);
                        if (groupDetails.getName() == null) {
                            builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_unnamed_group_invitation));
                        } else {
                            builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_group_invitation, groupDetails.getName()));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    builder.setContentText(App.getContext().getResources().getString(R.string.notification_content_group_v2_frozen_invite));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_group_invitation));
                    break;
                }
                default:
                    return;
            }

            // add accept/reject buttons
            switch (invitation.associatedDialog.getCategory().getId()) {
                case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY:
                case ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY: {
                    Intent acceptIntent = new Intent(App.getContext(), NotificationActionService.class);
                    acceptIntent.setAction(NotificationActionService.ACTION_ACCEPT_INVITATION);
                    acceptIntent.putExtra(NotificationActionService.EXTRA_INVITATION_DIALOG_UUID, Logger.getUuidString(invitation.dialogUuid));
                    PendingIntent acceptPendingIntent;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        acceptPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationId(invitation.dialogUuid), acceptIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    } else {
                        acceptPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationId(invitation.dialogUuid), acceptIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                    }
                    builder.addAction(R.drawable.ic_ok, App.getContext().getString(R.string.notification_action_accept), acceptPendingIntent);

                    Intent rejectIntent = new Intent(App.getContext(), NotificationActionService.class);
                    rejectIntent.setAction(NotificationActionService.ACTION_REJECT_INVITATION);
                    rejectIntent.putExtra(NotificationActionService.EXTRA_INVITATION_DIALOG_UUID, Logger.getUuidString(invitation.dialogUuid));
                    PendingIntent rejectPendingIntent;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        rejectPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationId(invitation.dialogUuid), rejectIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    } else {
                        rejectPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationId(invitation.dialogUuid), rejectIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                    }
                    builder.addAction(R.drawable.ic_close, App.getContext().getString(R.string.notification_action_reject), rejectPendingIntent);
                    break;
                }
                case ObvDialog.Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY: {
                    Intent rejectIntent = new Intent(App.getContext(), NotificationActionService.class);
                    rejectIntent.setAction(NotificationActionService.ACTION_REJECT_INVITATION);
                    rejectIntent.putExtra(NotificationActionService.EXTRA_INVITATION_DIALOG_UUID, Logger.getUuidString(invitation.dialogUuid));
                    PendingIntent rejectPendingIntent;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        rejectPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationId(invitation.dialogUuid), rejectIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    } else {
                        rejectPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationId(invitation.dialogUuid), rejectIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                    }
                    builder.addAction(R.drawable.ic_close, App.getContext().getString(R.string.notification_action_reject), rejectPendingIntent);
                    break;
                }
            }

            int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
            initialView.setSize(size, size);
            Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            initialView.drawOnCanvas(new Canvas(largeIcon));
            builder.setLargeIcon(largeIcon);
            builder.setPublicVersion(publicBuilder.build());


            int notificationId = getInvitationNotificationId(invitation.dialogUuid);
            if (ownedIdentity.isHidden()) {
                hiddenIdentityNotificationIdsToClear.add(notificationId);
            }

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(notificationId, builder.build());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                vibrate(null);
            }
        });
    }

    public static void clearInvitationNotification(UUID invitationDialogUuid) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getInvitationNotificationId(invitationDialogUuid));
        });
    }

    // endregion

    // region webclient

    public static void displayWebclientDisconnectedNotification(String notificationTitle) {
        executor.execute(() -> {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion())
                    .setSmallIcon(R.drawable.ic_webclient_disconnected)
                    .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setContentTitle(notificationTitle)
                    .setVibrate(new long[0]);

            int notificationId = getNeutralNotificationId();

            Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
            PendingIntent contentPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(contentPendingIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(notificationId, builder.build());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                vibrate(null);
            }
        });
    }

    public static void displayWebclientActivityAfterInactivityNotification(String notificationTitle) {
        executor.execute(() -> {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion())
                    .setSmallIcon(R.drawable.ic_webclient_connection_running)
                    .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setContentTitle(notificationTitle)
                    .setVibrate(new long[0]);

            int notificationId = getNeutralNotificationId();

            Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
            PendingIntent contentPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(contentPendingIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(notificationId, builder.build());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                vibrate(null);
            }
        });
    }

    // endregion

    // region Keycloak

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void createKeycloakChannel() {
        NotificationChannel keycloakChannel = new NotificationChannel(
                KEYCLOAK_NOTIFICATION_CHANNEL_ID,
                App.getContext().getString(R.string.notification_channel_keycloak_name),
                NotificationManager.IMPORTANCE_HIGH);
        keycloakChannel.setDescription(App.getContext().getString(R.string.notification_channel_keycloak_description));
        keycloakChannel.setShowBadge(false);
        keycloakChannel.enableVibration(true);
        keycloakChannel.setVibrationPattern(new long[]{0, 100});
        keycloakChannel.enableLights(true);
        keycloakChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
        notificationManager.createNotificationChannel(keycloakChannel);
    }

    private static int getKeycloakNotificationId(byte[] bytesOwnedIdentity) {
        return Arrays.hashCode(bytesOwnedIdentity);
    }

    public static void displayKeycloakAuthenticationRequiredNotification(byte[] bytesOwnedIdentity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            createKeycloakChannel();
        }
        executor.execute(() -> {
            OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(bytesOwnedIdentity);
            if (ownedIdentity == null || (ownedIdentity.isHidden() && !Arrays.equals(AppSingleton.getBytesCurrentIdentity(), bytesOwnedIdentity))) {
                if (ownedIdentity != null && ownedIdentity.prefShowNeutralNotificationWhenHidden) {
                    displayNeutralNotification();
                }
                return;
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), KEYCLOAK_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_warning_outline)
                    .setColor(ContextCompat.getColor(App.getContext(), R.color.red))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentTitle(App.getContext().getString(R.string.text_notification_keycloak_authentication_required_title))
                    .setContentText(App.getContext().getString(R.string.text_notification_keycloak_authentication_required_message))
                    .setVibrate(new long[0]);

            int channelId = getKeycloakNotificationId(bytesOwnedIdentity);

            // CONTENT INTENT
            Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
            contentIntent.putExtra(MainActivity.KEYCLOAK_AUTHENTICATION_NEEDED_EXTRA, bytesOwnedIdentity);
            PendingIntent contentPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(contentPendingIntent);

            NotificationCompat.Action authenticateAction = new NotificationCompat.Action.Builder(R.drawable.ic_lock, App.getContext().getString(R.string.notification_action_authenticate), contentPendingIntent)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_NONE)
                    .setShowsUserInterface(true)
                    .build();
            builder.addAction(authenticateAction);

            if (ownedIdentity.isHidden()) {
                hiddenIdentityNotificationIdsToClear.add(channelId);
            }
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(channelId, builder.build());

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Vibrator v = (Vibrator) App.getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(new long[]{0, 100}, -1);
                }
            }
        });
    }

    public static void clearKeycloakAuthenticationRequiredNotification(byte[] bytesOwnedIdentity) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getKeycloakNotificationId(bytesOwnedIdentity));
        });
    }

    // endregion

    // region blocked certificate

    public static void displayConnectionBlockedNotification(long untrustedCertificateId, @Nullable Long lastTrustedCertificateId) {
        executor.execute(() -> {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID  + getCurrentMessageChannelVersion())
                    .setSmallIcon(R.drawable.ic_warning_outline)
                    .setColor(ContextCompat.getColor(App.getContext(), R.color.red))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setOnlyAlertOnce(true)
                    .setContentTitle(App.getContext().getString(R.string.text_notification_connection_blocked_title))
                    .setContentText(App.getContext().getString(R.string.text_notification_connection_blocked_message))
                    .setVibrate(new long[0]);

            int notificationId = getCertificateBlockedNotificationId(untrustedCertificateId);

            // CONTENT INTENT
            Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
            contentIntent.putExtra(MainActivity.BLOCKED_CERTIFICATE_ID_EXTRA, untrustedCertificateId);
            if (lastTrustedCertificateId != null) {
                contentIntent.putExtra(MainActivity.LAST_TRUSTED_CERTIFICATE_ID_EXTRA, lastTrustedCertificateId);
            }
            PendingIntent contentPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), notificationId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(contentPendingIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(notificationId, builder.build());

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Vibrator v = (Vibrator) App.getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(new long[]{0, 100}, -1);
                }
            }
        });
    }

    public static void clearConnectionBlockedNotification(long untrustedCertificateId) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getCertificateBlockedNotificationId(untrustedCertificateId));
        });
    }

    // endregion


    private static int getMessageNotificationId(long discussionId) {
        return (int) (0xffffff & discussionId);
    }

    private static int getInvitationNotificationId(UUID invitationDialogUuid) {
        return (int) (0xffffff & invitationDialogUuid.getLeastSignificantBits()) | 0x1000000;
    }

    private static int getGroupNotificationId(long discussionId) {
        return (int) (0xffffff & discussionId) | 0x2000000;
    }

    private static int getNeutralNotificationId() {
        return 0x3000000;
    }

    private static int getMissedCallNotificationId(long discussionId) {
        return (int) (0xffffff & discussionId) | 0x4000000;
    }

    private static int getReactionNotificationId(long messageId) {
        return (int) (0xffffff & messageId) | 0x5000000;
    }

    private static int getCertificateBlockedNotificationId(long untrustedCertificateId) {
        return (int) (0xffffff & untrustedCertificateId) | 0x6000000;
    }

    private static void vibrate(@Nullable DiscussionCustomization discussionCustomization) {
        Vibrator v = (Vibrator) App.getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            long[] pattern;
            if (discussionCustomization == null || !discussionCustomization.prefUseCustomMessageNotification || discussionCustomization.prefMessageNotificationVibrationPattern == null) {
                pattern = SettingsActivity.getMessageVibrationPattern();
            } else {
                pattern = SettingsActivity.intToVibrationPattern(Integer.parseInt(discussionCustomization.prefMessageNotificationVibrationPattern));
            }
            if (pattern != null) {
                v.vibrate(pattern, -1);
            }
        }
    }




    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JsonMessageReactionsNotification {
        public List<JsonReaction> reactions;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class JsonReaction {
            public byte[] bytesContactIdentity;
            public String contactDisplayName;
            public String emoji;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JsonDiscussionMessageReactionsNotification {
        public List<Long> messageIds;
    }


    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JsonPojoGroupNotification {
        List<String> addedMembers;
        List<String> removedMembers;

        public JsonPojoGroupNotification() {
        }

        public List<String> getAddedMembers() {
            return addedMembers;
        }

        public void setAddedMembers(List<String> addedMembers) {
            this.addedMembers = addedMembers;
        }

        public List<String> getRemovedMembers() {
            return removedMembers;
        }

        public void setRemovedMembers(List<String> removedMembers) {
            this.removedMembers = removedMembers;
        }
    }


    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JsonPojoDiscussionNotification {
        String title;
        String photoUrl;
        byte[] discussionInitialBytes;
        boolean isGroup;
        byte[] bytesOwnedIdentity;
        String ownDisplayName;
        String ownPhotoUrl;
        List<JsonPojoMessageNotification> messageNotifications;

        public JsonPojoDiscussionNotification() {
        }

        public byte[] getBytesOwnedIdentity() {
            return bytesOwnedIdentity;
        }

        public void setBytesOwnedIdentity(byte[] bytesOwnedIdentity) {
            this.bytesOwnedIdentity = bytesOwnedIdentity;
        }

        public String getOwnDisplayName() {
            return ownDisplayName;
        }

        public void setOwnDisplayName(String ownDisplayName) {
            this.ownDisplayName = ownDisplayName;
        }

        public String getPhotoUrl() {
            return photoUrl;
        }

        public void setPhotoUrl(String photoUrl) {
            this.photoUrl = photoUrl;
        }

        public String getOwnPhotoUrl() {
            return ownPhotoUrl;
        }

        public void setOwnPhotoUrl(String ownPhotoUrl) {
            this.ownPhotoUrl = ownPhotoUrl;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public byte[] getDiscussionInitialBytes() {
            return discussionInitialBytes;
        }

        public void setDiscussionInitialBytes(byte[] discussionInitialBytes) {
            this.discussionInitialBytes = discussionInitialBytes;
        }

        public boolean isGroup() {
            return isGroup;
        }

        public void setGroup(boolean group) {
            isGroup = group;
        }

        public List<JsonPojoMessageNotification> getMessageNotifications() {
            return messageNotifications;
        }

        public void setMessageNotifications(List<JsonPojoMessageNotification> messageNotifications) {
            this.messageNotifications = messageNotifications;
        }



        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class JsonPojoMessageNotification implements Comparable<JsonPojoMessageNotification> {
            long messageId;
            long timestamp;
            String sender;
            String senderPhotoUrl; // relative path
            byte[] senderByteIdentity;
            String content;

            public JsonPojoMessageNotification(long messageId, long timestamp, String sender, String senderPhotoUrl, byte[] senderByteIdentity, String content) {
                this.messageId = messageId;
                this.timestamp = timestamp;
                this.sender = sender;
                this.senderPhotoUrl = senderPhotoUrl;
                this.senderByteIdentity = senderByteIdentity;
                this.content = content;
            }

            public JsonPojoMessageNotification() {
            }

            public long getMessageId() {
                return messageId;
            }

            public void setMessageId(long messageId) {
                this.messageId = messageId;
            }

            public byte[] getSenderByteIdentity() {
                return senderByteIdentity;
            }

            public void setSenderByteIdentity(byte[] senderByteIdentity) {
                this.senderByteIdentity = senderByteIdentity;
            }

            public long getTimestamp() {
                return timestamp;
            }

            public void setTimestamp(long timestamp) {
                this.timestamp = timestamp;
            }

            public String getSender() {
                return sender;
            }

            public void setSender(String sender) {
                this.sender = sender;
            }

            public String getSenderPhotoUrl() {
                return senderPhotoUrl;
            }

            public void setSenderPhotoUrl(String senderPhotoUrl) {
                this.senderPhotoUrl = senderPhotoUrl;
            }

            public String getContent() {
                return content;
            }

            public void setContent(String content) {
                this.content = content;
            }

            @Override
            public int compareTo(@NonNull JsonPojoMessageNotification other) {
                if (timestamp > other.timestamp) {
                    return 1;
                } else {
                    return -1;
                }
            }

            @JsonIgnore
            NotificationCompat.MessagingStyle.Message getMessage() {
                if (senderByteIdentity != null && sender != null) {
                    InitialView initialView = new InitialView(App.getContext());
                    if (senderPhotoUrl == null) {
                        initialView.setInitial(senderByteIdentity, StringUtils.getInitial(sender));
                    } else {
                        initialView.setPhotoUrl(senderByteIdentity, senderPhotoUrl);
                    }
                    int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
                    initialView.setSize(size, size);
                    Bitmap personIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    initialView.drawOnCanvas(new Canvas(personIcon));

                    Person person = new Person.Builder()
                            .setName(sender)
                            .setIcon(IconCompat.createWithBitmap(personIcon))
                            .build();

                    return new NotificationCompat.MessagingStyle.Message(content, timestamp, person);
                } else {
                    return new NotificationCompat.MessagingStyle.Message(content, timestamp, (Person) null);
                }
            }
        }
    }
}
