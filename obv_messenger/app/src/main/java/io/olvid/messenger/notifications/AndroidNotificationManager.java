/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

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
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.webrtc.WebrtcCallActivity;

public class AndroidNotificationManager {
    private static final String DISCUSSION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX = "discussion_";
    private static final String GROUP_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX = "group_";
    private static final String KEY_MESSAGE_NOTIFICATION_CHANNEL_VERSION = "message_channel_version";

    public static final String MESSAGE_NOTIFICATION_CHANNEL_ID = "message";
    public static final String UNIFIED_SERVICE_NOTIFICATION_CHANNEL_ID = "unified";

    public static final String WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID = "calls";
    public static final String MEDIA_PLAYER_SERVICE_NOTIFICATION_CHANNEL_ID = "media_player";

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
                notificationManager.cancel(getMessageNotificationChannelId(discussionId));
                editor.remove(DISCUSSION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId);

                notificationManager.cancel(getGroupNotificationChannelId(discussionId));
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
            validateSoundForAndroidPie();
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
                validateSoundForAndroidPie();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private static void validateSoundForAndroidPie() {
        // test if the notification sound works --> bug on Android 9 (Pie) with sounds on SD card
        try {
            displayNeutralNotification();
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


        int channelId = getNeutralNotificationChannelId();

        // CONTENT INTENT
        Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
        PendingIntent contentPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }
        builder.setContentIntent(contentPendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
        notificationManager.notify(channelId, builder.build());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            vibrate();
        }
    }

    public static void clearNeutralNotification() {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getNeutralNotificationChannelId());
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


            NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion())
                    .setSmallIcon(R.drawable.ic_o)
                    .setContentTitle(App.getContext().getString(R.string.notification_public_title_group_member));

            NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion())
                    .setSmallIcon(R.drawable.ic_o)
                    .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setPublicVersion(publicBuilder.build())
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentTitle(group.getCustomName())
                    .setVibrate(new long[0]);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setSound(SettingsActivity.getMessageRingtone());
            }

            String colorString = SettingsActivity.getMessageLedColor();
            if (colorString != null) {
                int color = 0xff000000 + Integer.parseInt(colorString.substring(1), 16);
                builder.setLights(color, 500, 2000);
            }


            InitialView initialView = new InitialView(App.getContext());
            if (group.getCustomPhotoUrl() != null) {
                initialView.setPhotoUrl(group.bytesGroupOwnerAndUid, group.getCustomPhotoUrl());
            } else {
                initialView.setGroup(group.bytesGroupOwnerAndUid);
            }
            int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
            initialView.setSize(size, size);
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

            int channelId = getGroupNotificationChannelId(discussionId);

            // CONTENT INTENT
            Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
            contentIntent.setAction(MainActivity.FORWARD_ACTION);
            contentIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
            contentIntent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
            contentIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, group.bytesOwnedIdentity);
            PendingIntent contentPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(contentPendingIntent);

            // DISMISS INTENT
            Intent dismissIntent = new Intent(App.getContext(), NotificationActionService.class);
            dismissIntent.setAction(NotificationActionService.ACTION_GROUP_CLEAR);
            dismissIntent.putExtra(NotificationActionService.EXTRA_DISCUSSION_ID, discussionId);
            PendingIntent dismissPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                dismissPendingIntent = PendingIntent.getService(App.getContext(), channelId, dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                dismissPendingIntent = PendingIntent.getService(App.getContext(), channelId, dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setDeleteIntent(dismissPendingIntent);

            if (ownedIdentity.isHidden()) {
                hiddenIdentityNotificationDiscussionIdsToClear.add(discussionId);
            }

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(channelId, builder.build());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                vibrate();
            }
        });
    }

    public static void clearGroupNotification(final long discussionId) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getGroupNotificationChannelId(discussionId));
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
        if (discussion.photoUrl == null) {
            initialView.setInitial(discussion.bytesContactIdentity, App.getInitial(discussion.title));
        } else {
            initialView.setPhotoUrl(discussion.bytesContactIdentity, discussion.photoUrl);
        }
        int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
        initialView.setSize(size, size);
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
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), getMissedCallNotificationChannelId(discussion.id), contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), getMissedCallNotificationChannelId(discussion.id), contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
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
                sendMessagePendingIntent = PendingIntent.getService(App.getContext(), getMissedCallNotificationChannelId(discussion.id), sendMessageIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            } else {
                sendMessagePendingIntent = PendingIntent.getService(App.getContext(), getMissedCallNotificationChannelId(discussion.id), sendMessageIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            NotificationCompat.Action sendMessageAction = new NotificationCompat.Action.Builder(R.drawable.ic_send, App.getContext().getString(R.string.notification_action_send_message), sendMessagePendingIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .build();
            builder.addAction(sendMessageAction);
        }

        // CALL BACK ACTION
        Intent callBackIntent = new Intent(App.getContext(), WebrtcCallActivity.class);
        callBackIntent.setAction(WebrtcCallActivity.CALL_BACK_ACTION);
        callBackIntent.putExtra(WebrtcCallActivity.CALL_BACK_EXTRA_BYTES_OWNED_IDENTITY, discussion.bytesOwnedIdentity);
        callBackIntent.putExtra(WebrtcCallActivity.CALL_BACK_EXTRA_BYTES_CONTACT_IDENTITY, discussion.bytesContactIdentity);
        callBackIntent.putExtra(WebrtcCallActivity.CALL_BACK_EXTRA_DISCUSSION_ID, discussion.id);
        callBackIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent callBackPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            callBackPendingIntent = PendingIntent.getActivity(App.getContext(), getMissedCallNotificationChannelId(discussion.id), callBackIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            callBackPendingIntent = PendingIntent.getActivity(App.getContext(), getMissedCallNotificationChannelId(discussion.id), callBackIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Action callBackAction = new NotificationCompat.Action.Builder(R.drawable.ic_answer_call, App.getContext().getString(R.string.notification_action_call_back), callBackPendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL)
                .setShowsUserInterface(true)
                .build();
        builder.addAction(callBackAction);


        int notificationId = getMissedCallNotificationChannelId(discussion.id);
        if (ownedIdentityIsHidden) {
            hiddenIdentityNotificationIdsToClear.add(notificationId);
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
        notificationManager.notify(notificationId, builder.build());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            vibrate();
        }
    }


    public static void clearMissedCallNotification(long discussionId) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getMissedCallNotificationChannelId(discussionId));
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




    // region Message notification

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
                clearReceivedMessageNotification(discussion.id);
                return;
            }

            NotificationCompat.Builder builder = getEmptyMessageNotificationBuilder(discussion, discussionNotification, contact != null);

            populateMessageNotificationBuilder(builder, discussion, discussionNotification);

            if (ownedIdentity != null && ownedIdentity.isHidden()) {
                hiddenIdentityNotificationDiscussionIdsToClear.add(discussion.id);
            }

            int notificationId = getMessageNotificationChannelId(discussion.id);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(notificationId, builder.build());
            if (contact != null) {
                if (messageLastVibrationTimestamp.containsKey(notificationId)) {
                    Long timestamp = messageLastVibrationTimestamp.get(notificationId);
                    if (timestamp == null || timestamp < (System.currentTimeMillis() - DELAY_BETWEEN_SAME_CHANNEL_VIBRATE)) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            vibrate();
                        }
                        messageLastVibrationTimestamp.put(notificationId, System.currentTimeMillis());
                    }
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        vibrate();
                    }
                    messageLastVibrationTimestamp.put(notificationId, System.currentTimeMillis());
                }
            }
        });
    }

    public static void remoteDeleteMessageNotification(@NonNull Discussion discussion, long messageId) {
        executor.execute(() -> {
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

                NotificationCompat.Builder builder = getEmptyMessageNotificationBuilder(discussion, discussionNotification, false);
                populateMessageNotificationBuilder(builder, discussion, discussionNotification);
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
                notificationManager.notify(getMessageNotificationChannelId(discussion.id), builder.build());
            }
        });
    }

    public static void expireMessageNotification(long discussionId, long messageId) {
        executor.execute(() -> {
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

                NotificationCompat.Builder builder = getEmptyMessageNotificationBuilder(discussion, discussionNotification, false);
                populateMessageNotificationBuilder(builder, discussion, discussionNotification);
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
                notificationManager.notify(getMessageNotificationChannelId(discussionId), builder.build());
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

                NotificationCompat.Builder builder = getEmptyMessageNotificationBuilder(discussion, discussionNotification, false);
                populateMessageNotificationBuilder(builder, discussion, discussionNotification);
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
                notificationManager.notify(getMessageNotificationChannelId(discussion.id), builder.build());
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
            if (discussion.bytesGroupOwnerAndUid != null) {
                discussionNotification.discussionInitialBytes = discussion.bytesGroupOwnerAndUid;
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

    private static void populateMessageNotificationBuilder(NotificationCompat.Builder builder, Discussion discussion, JsonPojoDiscussionNotification discussionNotification) {
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
            initialView.setInitial(discussionNotification.bytesOwnedIdentity, App.getInitial(discussionNotification.ownDisplayName));
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

    private static NotificationCompat.Builder getEmptyMessageNotificationBuilder(Discussion discussion, JsonPojoDiscussionNotification discussionNotification, boolean withSound) {
        int messageCount = discussionNotification.messageNotifications.size();
        String title = discussionNotification.title;
        byte[] discussionInitialBytes = discussionNotification.discussionInitialBytes;
        boolean isGroup = discussionNotification.isGroup;

        NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion())
                .setSmallIcon(R.drawable.ic_o)
                .setContentTitle(App.getContext().getResources().getQuantityString(R.plurals.notification_public_title_new_messages, messageCount, messageCount));


        NotificationCompat.Builder builder = new NotificationCompat.Builder(App.getContext(), MESSAGE_NOTIFICATION_CHANNEL_ID + getCurrentMessageChannelVersion())
                .setSmallIcon(R.drawable.ic_o)
                .setColor(ContextCompat.getColor(App.getContext(), R.color.olvid_gradient_dark))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setPublicVersion(publicBuilder.build())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setNumber(messageCount)
                .setVibrate(new long[0]);

        String colorString = SettingsActivity.getMessageLedColor();
        if (colorString != null) {
            int color = 0xff000000 + Integer.parseInt(colorString.substring(1), 16);
            builder.setLights(color, 500, 2000);
        }

        if (withSound) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setSound(SettingsActivity.getMessageRingtone());
            }
        } else {
            builder.setOnlyAlertOnce(true);
        }

        InitialView initialView = new InitialView(App.getContext());
        if (isGroup) {
            if (discussionNotification.photoUrl == null) {
                initialView.setGroup(discussionInitialBytes);
            } else {
                initialView.setPhotoUrl(discussionInitialBytes, discussionNotification.photoUrl);
            }
            builder.setContentTitle(App.getContext().getResources().getQuantityString(R.plurals.notification_title_new_group_messages, messageCount, messageCount, title));
        } else {
            if (discussionNotification.photoUrl == null) {
                initialView.setInitial(discussionInitialBytes, App.getInitial(title));
            } else {
                initialView.setPhotoUrl(discussionInitialBytes, discussionNotification.photoUrl);
            }
            builder.setContentTitle(App.getContext().getResources().getQuantityString(R.plurals.notification_title_new_messages, messageCount, messageCount, title));
        }
        int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
        initialView.setSize(size, size);
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
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), getMessageNotificationChannelId(discussion.id), contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            contentPendingIntent = PendingIntent.getActivity(App.getContext(), getMessageNotificationChannelId(discussion.id), contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }
        builder.setContentIntent(contentPendingIntent);

        // DISMISS INTENT
        Intent dismissIntent = new Intent(App.getContext(), NotificationActionService.class);
        dismissIntent.setAction(NotificationActionService.ACTION_DISCUSSION_CLEAR);
        dismissIntent.putExtra(NotificationActionService.EXTRA_DISCUSSION_ID, discussion.id);
        PendingIntent dismissPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            dismissPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationChannelId(discussion.id), dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            dismissPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationChannelId(discussion.id), dismissIntent, PendingIntent.FLAG_CANCEL_CURRENT);
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
                replyPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationChannelId(discussion.id), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            } else {
                replyPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationChannelId(discussion.id), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(R.drawable.ic_send, App.getContext().getString(R.string.notification_action_reply), replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
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
            markAsReadPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationChannelId(discussion.id), markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            markAsReadPendingIntent = PendingIntent.getService(App.getContext(), getMessageNotificationChannelId(discussion.id), markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT);
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
            mutePendingIntent = PendingIntent.getActivity(App.getContext(), getMessageNotificationChannelId(discussion.id), muteIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            mutePendingIntent = PendingIntent.getActivity(App.getContext(), getMessageNotificationChannelId(discussion.id), muteIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Action muteAction = new NotificationCompat.Action.Builder(R.drawable.ic_notification_muted, App.getContext().getString(R.string.notification_action_mute), mutePendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE)
                .setShowsUserInterface(true)
                .build();
        builder.addAction(muteAction);

        return builder;
    }


    public static void clearReceivedMessageNotification(final long discussionId) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getMessageNotificationChannelId(discussionId));
            SharedPreferences sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_notifications), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(DISCUSSION_NOTIFICATION_SHARED_PREFERENCE_KEY_PREFIX + discussionId);
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
            intent.putExtra(MainActivity.TAB_TO_SHOW_INTENT_EXTRA, MainActivity.INVITATIONS_TAB);
            intent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, invitation.bytesOwnedIdentity);
            PendingIntent pendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getActivity(App.getContext(), getInvitationNotificationChannelId(invitation.dialogUuid), intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = PendingIntent.getActivity(App.getContext(), getInvitationNotificationChannelId(invitation.dialogUuid), intent, PendingIntent.FLAG_CANCEL_CURRENT);
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
                    initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), App.getInitial(displayName));
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
                    initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), App.getInitial(displayName));
                    builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_exchange_sas, displayName));
                    builder.setContentText(App.getContext().getResources().getString(R.string.notification_content_exchange_sas, new String(invitation.associatedDialog.getCategory().getSasToDisplay(), StandardCharsets.UTF_8)));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_exchange_sas));
                    break;
                }
                case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                case ObvDialog.Category.INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_CATEGORY: {
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
                    initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), App.getInitial(displayName));
                    builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_new_invitation, displayName));
                    builder.setContentText(App.getContext().getString(R.string.notification_content_mediator_invite, mediatorDisplayName));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_new_invitation));
                    break;
                }
                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                case ObvDialog.Category.INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_CATEGORY: {
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
                case ObvDialog.Category.AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_CATEGORY: {
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
                    String mediatorDisplayName = mediator.getCustomDisplayName();
                    // we do not have a photoUrl yet, just show the initial
                    initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), App.getInitial(displayName));
                    builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_contact_added, displayName));
                    builder.setContentText(App.getContext().getString(R.string.notification_content_mediator_invite, mediatorDisplayName));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_contact_added));
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
                    initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), App.getInitial(displayName));
                    builder.setContentTitle(App.getContext().getResources().getString(R.string.notification_title_contact_added, displayName));
                    builder.setContentText(App.getContext().getString(R.string.notification_content_contact_added));
                    publicBuilder.setContentTitle(App.getContext().getResources().getString(R.string.notification_public_title_contact_added));
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
                    Intent acceptIntent = new Intent(App.getContext(), NotificationActionService.class);
                    acceptIntent.setAction(NotificationActionService.ACTION_ACCEPT_INVITATION);
                    acceptIntent.putExtra(NotificationActionService.EXTRA_INVITATION_DIALOG_UUID, invitation.dialogUuid.toString());
                    PendingIntent acceptPendingIntent;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        acceptPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationChannelId(invitation.dialogUuid), acceptIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    } else {
                        acceptPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationChannelId(invitation.dialogUuid), acceptIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                    }
                    builder.addAction(R.drawable.ic_ok, App.getContext().getString(R.string.notification_action_accept), acceptPendingIntent);

                    Intent rejectIntent = new Intent(App.getContext(), NotificationActionService.class);
                    rejectIntent.setAction(NotificationActionService.ACTION_REJECT_INVITATION);
                    rejectIntent.putExtra(NotificationActionService.EXTRA_INVITATION_DIALOG_UUID, invitation.dialogUuid.toString());
                    PendingIntent rejectPendingIntent;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        rejectPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationChannelId(invitation.dialogUuid), rejectIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    } else {
                        rejectPendingIntent = PendingIntent.getService(App.getContext(), getInvitationNotificationChannelId(invitation.dialogUuid), rejectIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                    }
                    builder.addAction(R.drawable.ic_close, App.getContext().getString(R.string.notification_action_reject), rejectPendingIntent);
            }

            int size = App.getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
            initialView.setSize(size, size);
            Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            initialView.drawOnCanvas(new Canvas(largeIcon));
            builder.setLargeIcon(largeIcon);
            builder.setPublicVersion(publicBuilder.build());


            int notificationId = getInvitationNotificationChannelId(invitation.dialogUuid);
            if (ownedIdentity.isHidden()) {
                hiddenIdentityNotificationIdsToClear.add(notificationId);
            }

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(notificationId, builder.build());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                vibrate();
            }
        });
    }

    public static void clearInvitationNotification(UUID invitationDialogUuid) {
        executor.execute(() -> {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.cancel(getInvitationNotificationChannelId(invitationDialogUuid));
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

            int channelId = getNeutralNotificationChannelId();

            Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
            PendingIntent contentPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(contentPendingIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(channelId, builder.build());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                vibrate();
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

            int channelId = getNeutralNotificationChannelId();

            Intent contentIntent = new Intent(App.getContext(), MainActivity.class);
            PendingIntent contentPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                contentPendingIntent = PendingIntent.getActivity(App.getContext(), channelId, contentIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(contentPendingIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(App.getContext());
            notificationManager.notify(channelId, builder.build());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                vibrate();
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


    private static int getMessageNotificationChannelId(long discussionId) {
        return (int) (0xffffff & discussionId);
    }

    private static int getInvitationNotificationChannelId(UUID invitationDialogUuid) {
        return (int) (0xffffff & invitationDialogUuid.getLeastSignificantBits()) | 0x1000000;
    }

    private static int getGroupNotificationChannelId(long discussionId) {
        return (int) (0xffffff & discussionId) | 0x2000000;
    }

    private static int getNeutralNotificationChannelId() {
        return 0x3000000;
    }

    private static int getMissedCallNotificationChannelId(long discussionId) {
        return (int) (0xffffff & discussionId) | 0x4000000;
    }

    private static void vibrate() {
        Vibrator v = (Vibrator) App.getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            long[] pattern = SettingsActivity.getMessageVibrationPattern();
            if (pattern != null) {
                v.vibrate(pattern, -1);
            }
        }
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
                        initialView.setInitial(senderByteIdentity, App.getInitial(sender));
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
