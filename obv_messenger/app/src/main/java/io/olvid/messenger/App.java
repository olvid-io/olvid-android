/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.emoji2.bundled.BundledEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.ObvBase64;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvBackupKeyInformation;
import io.olvid.engine.engine.types.ObvPushNotificationType;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.activities.ContactDetailsActivity;
import io.olvid.messenger.activities.LockScreenActivity;
import io.olvid.messenger.activities.MessageDetailsActivity;
import io.olvid.messenger.activities.ObvLinkActivity;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.appdialogs.AppDialogShowActivity;
import io.olvid.messenger.appdialogs.AppDialogTag;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.ConfigurationPojo;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils2;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.CallLogItem;
import io.olvid.messenger.databases.entity.CallLogItemContactJoin;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.jsons.JsonWebrtcMessage;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.gallery.GalleryActivity;
import io.olvid.messenger.group.GroupCreationActivity;
import io.olvid.messenger.group.GroupDetailsActivity;
import io.olvid.messenger.group.GroupV2DetailsActivity;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsActivity;
import io.olvid.messenger.services.AvailableSpaceHelper;
import io.olvid.messenger.services.BackupCloudProviderService;
import io.olvid.messenger.services.MDMConfigurationSingleton;
import io.olvid.messenger.services.MessageExpirationService;
import io.olvid.messenger.services.NetworkStateMonitorReceiver;
import io.olvid.messenger.services.PeriodicTasksScheduler;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.AppIcon;
import io.olvid.messenger.settings.AppIconSettingKt;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.webrtc.WebrtcCallActivity;
import io.olvid.messenger.webrtc.WebrtcCallService;


public class App extends Application implements DefaultLifecycleObserver {
    public static final String CAMERA_PICTURE_FOLDER = "camera_pictures";
    public static final String WEBCLIENT_ATTACHMENT_FOLDER = "webclient_attachment_folder";
    public static final String TIMESTAMP_FILE_NAME_FORMAT = "yyyy-MM-dd@HH-mm-ss";

    public static final String NEW_APP_DIALOG_BROADCAST_ACTION = "new_app_dialog_to_show";
    public static final String CURRENT_HIDDEN_PROFILE_CLOSED_BROADCAST_ACTION = "current_hidden_profile_closed";

    private static final LinkedHashMap<String, HashMap<String, Object>> dialogsToShow = new LinkedHashMap<>();
    private static final HashMap<BytesKey, LinkedHashMap<String, HashMap<String, Object>>> dialogsToShowForOwnedIdentity = new HashMap<>();
    private static final Lock dialogsToShowLock = new ReentrantLock();
    private static boolean blockAppDialogs = false;

    private static Application application;
    public static long appStartTimestamp = 0;

    private static Application getApplication() {
        return application;
    }
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static Context getContext() {
        return getApplication().getApplicationContext();
    }

    private static boolean isVisible = false;
    private static boolean isAppDialogShowing = false;
    private static boolean killActivitiesOnLockAndCloseHiddenProfileOnBackground = true;

    public static boolean isVisible() { return isVisible; }


    public static void doNotKillActivitiesOnLockOrCloseHiddenProfileOnBackground() {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
    }

    public static boolean shouldActivitiesBeKilledOnLockAndHiddenProfileClosedOnBackground() { return killActivitiesOnLockAndCloseHiddenProfileOnBackground; }

    public static AppIcon currentIcon;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
        appStartTimestamp = System.currentTimeMillis();

        SettingsActivity.setDefaultNightMode();

        currentIcon = AppIconSettingKt.getCurrentIcon();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && UnifiedForegroundService.willUnifiedForegroundServiceStartForeground()) {
                startForegroundService(new Intent(this, UnifiedForegroundService.class));
            } else {
                startService(new Intent(this, UnifiedForegroundService.class));
            }
        } catch (IllegalStateException e) {
            Logger.i("App was started in the background, unable to start UnifiedForegroundService.");
        }

        runThread(new AppStartupTasks());
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            PreviewUtils.purgeCache();
        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        isVisible = true;
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = true;
        UnifiedForegroundService.onAppForeground(getContext());

//        // we no longer poll for new messages on app foreground, this is almost always useless
//        if (AppSingleton.getCurrentIdentityLiveData().getValue() != null) {
//            AppSingleton.getEngine().downloadMessages(AppSingleton.getCurrentIdentityLiveData().getValue().bytesOwnedIdentity);
//        }

        runThread(() -> AvailableSpaceHelper.refreshAvailableSpace(false));

        AndroidNotificationManager.clearNeutralNotification();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isVisible = false;
        UnifiedForegroundService.onAppBackground(getContext());
    }

    // call this before launching an intent that will put the app in background so that the lock screen does not kill running activities
    // typically, when attaching a file to a discussion :)
    public static void prepareForStartActivityForResult(FragmentActivity activity) {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
        Intent lockIntent = new Intent(activity, LockScreenActivity.class);
        activity.startActivity(lockIntent);
    }
    public static void prepareForStartActivityForResult(Fragment fragment) {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
        Intent lockIntent = new Intent(fragment.getContext(), LockScreenActivity.class);
        fragment.startActivity(lockIntent);
    }

    public static void startActivityForResult(AppCompatActivity activity, Intent intent, int requestCode) {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
        Intent lockIntent = new Intent(activity, LockScreenActivity.class);
        activity.startActivity(lockIntent);
        //noinspection deprecation
        activity.startActivityForResult(intent, requestCode);
    }

    public static void startActivityForResult(Fragment fragment, Intent intent, int requestCode) {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
        Intent lockIntent = new Intent(fragment.getContext(), LockScreenActivity.class);
        fragment.startActivity(lockIntent);
        //noinspection deprecation
        fragment.startActivityForResult(intent, requestCode);
    }

    public static void openDiscussionActivity(Context activityContext, long discussionId) {
        Intent intent = new Intent(getContext(), DiscussionActivity.class);
        intent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityContext.startActivity(intent);
    }

    public static void openOneToOneDiscussionActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, boolean closeOtherDiscussions) {
        Intent intent = new Intent(getContext(), DiscussionActivity.class);
        intent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(DiscussionActivity.BYTES_CONTACT_IDENTITY_INTENT_EXTRA, bytesContactIdentity);
        if (closeOtherDiscussions) {
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        activityContext.startActivity(intent);
    }

    public static void openGroupDiscussionActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesGroupUid) {
        Intent intent = new Intent(getContext(), DiscussionActivity.class);
        intent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(DiscussionActivity.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA, bytesGroupUid);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityContext.startActivity(intent);
    }

    public static void openGroupV2DiscussionActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) {
        Intent intent = new Intent(getContext(), DiscussionActivity.class);
        intent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(DiscussionActivity.BYTES_GROUP_IDENTIFIER_INTENT_EXTRA, bytesGroupIdentifier);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityContext.startActivity(intent);
    }

    public static void openDiscussionGalleryActivity(Context activityContext, long discussionId, long messageId, long fyleId, boolean ascending) {
        Intent intent = new Intent(getContext(), GalleryActivity.class);
        intent.putExtra(GalleryActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
        intent.putExtra(GalleryActivity.INITIAL_MESSAGE_ID_INTENT_EXTRA, messageId);
        intent.putExtra(GalleryActivity.INITIAL_FYLE_ID_INTENT_EXTRA, fyleId);
        intent.putExtra(GalleryActivity.ASCENDING_INTENT_EXTRA, ascending);
        activityContext.startActivity(intent);
    }

    public static void openDraftGalleryActivity(Context activityContext, long draftMessageId, long fyleId) {
        Intent intent = new Intent(getContext(), GalleryActivity.class);
        intent.putExtra(GalleryActivity.DRAFT_INTENT_EXTRA, true);
        intent.putExtra(GalleryActivity.INITIAL_MESSAGE_ID_INTENT_EXTRA, draftMessageId);
        intent.putExtra(GalleryActivity.INITIAL_FYLE_ID_INTENT_EXTRA, fyleId);
        activityContext.startActivity(intent);
    }

    public static void openMessageGalleryActivity(Context activityContext, long messageId, long fyleId) {
        Intent intent = new Intent(getContext(), GalleryActivity.class);
        intent.putExtra(GalleryActivity.DRAFT_INTENT_EXTRA, false);
        intent.putExtra(GalleryActivity.INITIAL_MESSAGE_ID_INTENT_EXTRA, messageId);
        intent.putExtra(GalleryActivity.INITIAL_FYLE_ID_INTENT_EXTRA, fyleId);
        activityContext.startActivity(intent);
    }

    public static void openOwnedIdentityGalleryActivity(Context activityContext, byte[] bytesOwnedIdentity, @Nullable String sortOrder, boolean ascending, long messageId, long fyleId) {
        Intent intent = new Intent(getContext(), GalleryActivity.class);
        intent.putExtra(GalleryActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        if (sortOrder != null) {
            intent.putExtra(GalleryActivity.BYTES_OWNED_IDENTITY_SORT_ORDER_INTENT_EXTRA, sortOrder);
            intent.putExtra(GalleryActivity.ASCENDING_INTENT_EXTRA, ascending);
        }
        intent.putExtra(GalleryActivity.INITIAL_MESSAGE_ID_INTENT_EXTRA, messageId);
        intent.putExtra(GalleryActivity.INITIAL_FYLE_ID_INTENT_EXTRA, fyleId);
        activityContext.startActivity(intent);
    }

    public static void openDiscussionMediaGalleryActivity(Context activityContext, long discussionId) {
        Intent intent = new Intent(getContext(), io.olvid.messenger.discussion.gallery.DiscussionMediaGalleryActivity.class);
        intent.putExtra(GalleryActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
        activityContext.startActivity(intent);
    }


    public static void openGroupCreationActivity(Context activityContext) {
        Intent intent = new Intent(getContext(), GroupCreationActivity.class);
        activityContext.startActivity(intent);
    }

    public static void openGroupCreationActivityForCloning(Context activityContext, String absolutePhotoUrl, String serializedGroupDetails, String serializedGroupType, List<Contact> preselectedGroupMembers, List<Contact> preselectedGroupAdminMembers) {
        Intent intent = new Intent(getContext(), GroupCreationActivity.class);
        if (absolutePhotoUrl != null) {
            intent.putExtra(GroupCreationActivity.ABSOLUTE_PHOTO_URL_INTENT_EXTRA, absolutePhotoUrl);
        }
        if (serializedGroupDetails != null) {
            intent.putExtra(GroupCreationActivity.SERIALIZED_GROUP_DETAILS_INTENT_EXTRA, serializedGroupDetails);
        }
        if (serializedGroupType != null) {
            intent.putExtra(GroupCreationActivity.SERIALIZED_GROUP_TYPE_INTENT_EXTRA, serializedGroupType);
        }
        if (preselectedGroupMembers != null) {
            ArrayList<Parcelable> parcelables = new ArrayList<>(preselectedGroupMembers.size());
            for (Contact contact : preselectedGroupMembers) {
                parcelables.add(new BytesKey(contact.bytesContactIdentity));
            }
            intent.putParcelableArrayListExtra(GroupCreationActivity.PRESELECTED_GROUP_MEMBERS_INTENT_EXTRA, parcelables);
        }
        if (preselectedGroupAdminMembers != null) {
            ArrayList<Parcelable> parcelables = new ArrayList<>(preselectedGroupAdminMembers.size());
            for (Contact contact : preselectedGroupAdminMembers) {
                parcelables.add(new BytesKey(contact.bytesContactIdentity));
            }
            intent.putParcelableArrayListExtra(GroupCreationActivity.PRESELECTED_GROUP_ADMIN_MEMBERS_INTENT_EXTRA, parcelables);
        }
        activityContext.startActivity(intent);
    }


    private static void openAppDialogShower() {
        Intent newAppDialogIntent = new Intent(NEW_APP_DIALOG_BROADCAST_ACTION);
        newAppDialogIntent.setPackage(App.getContext().getPackageName());
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(newAppDialogIntent);
    }

    public static void openCurrentOwnedIdentityDetails(Context activityContext) {
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.setAction(MainActivity.FORWARD_ACTION);
        intent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, OwnedIdentityDetailsActivity.class.getName());
        activityContext.startActivity(intent);
    }

    public static void openContactDetailsActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        Intent intent = new Intent(getContext(), ContactDetailsActivity.class);
        intent.putExtra(ContactDetailsActivity.CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA, bytesContactIdentity);
        intent.putExtra(ContactDetailsActivity.CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        activityContext.startActivity(intent);
    }

    public static void startWebrtcCall(Context context, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        Intent serviceIntent = new Intent(getContext(), WebrtcCallService.class);
        serviceIntent.setAction(WebrtcCallService.ACTION_START_CALL);
        Bundle bytesContactIdentitiesBundle = new Bundle();
        bytesContactIdentitiesBundle.putByteArray(WebrtcCallService.SINGLE_CONTACT_IDENTITY_BUNDLE_KEY, bytesContactIdentity);
        serviceIntent.putExtra(WebrtcCallService.CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA, bytesContactIdentitiesBundle);
        serviceIntent.putExtra(WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        context.startService(serviceIntent);

        Intent activityIntent = new Intent(getContext(), WebrtcCallActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(activityIntent);
    }

    public static void startWebrtcMultiCall(Context context, byte[] bytesOwnedIdentity, List<Contact> contacts, byte[] bytesGroupOwnerAndUid, boolean groupV2) {
        Intent serviceIntent = new Intent(getContext(), WebrtcCallService.class);
        serviceIntent.setAction(WebrtcCallService.ACTION_START_CALL);
        serviceIntent.putExtra(WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        Bundle bytesContactIdentitiesBundle = new Bundle();
        int count = 0;
        for (Contact contact: contacts) {
            bytesContactIdentitiesBundle.putByteArray(Integer.toString(count), contact.bytesContactIdentity);
            count++;
        }
        serviceIntent.putExtra(WebrtcCallService.CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA, bytesContactIdentitiesBundle);
        if (bytesGroupOwnerAndUid != null) {
            serviceIntent.putExtra(WebrtcCallService.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA, bytesGroupOwnerAndUid);
            if (groupV2) {
                serviceIntent.putExtra(WebrtcCallService.GROUP_V2_INTENT_EXTRA, true);
            }
        }
        context.startService(serviceIntent);

        Intent activityIntent = new Intent(getContext(), WebrtcCallActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activityIntent);
    }

    public static void handleWebrtcMessage(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, JsonWebrtcMessage jsonWebrtcMessage, long downloadTimestamp, long serverTimestamp) {
        if (jsonWebrtcMessage.getCallIdentifier() == null || jsonWebrtcMessage.getMessageType() == null|| jsonWebrtcMessage.getSerializedMessagePayload() == null) {
            return;
        }
        int messageType = jsonWebrtcMessage.getMessageType();

        if (downloadTimestamp - serverTimestamp > WebrtcCallService.CALL_TIMEOUT_MILLIS) {
            if (messageType == WebrtcCallService.START_CALL_MESSAGE_TYPE) {
                runThread(() -> {
                    CallLogItem callLogItem = new CallLogItem(bytesOwnedIdentity, CallLogItem.TYPE_INCOMING, CallLogItem.STATUS_MISSED, serverTimestamp);
                    callLogItem.id = AppDatabase.getInstance().callLogItemDao().insert(callLogItem);
                    CallLogItemContactJoin callLogItemContactJoin = new CallLogItemContactJoin(callLogItem.id, bytesOwnedIdentity, bytesContactIdentity);
                    AppDatabase.getInstance().callLogItemDao().insert(callLogItemContactJoin);

                    AndroidNotificationManager.displayMissedCallNotification(bytesOwnedIdentity, bytesContactIdentity);

                    Discussion discussion = AppDatabase.getInstance().discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
                    if (discussion != null) {
                        Message missedCallMessage = Message.createPhoneCallMessage(AppDatabase.getInstance(), discussion.id, bytesContactIdentity, callLogItem);
                        AppDatabase.getInstance().messageDao().insert(missedCallMessage);
                        if (discussion.updateLastMessageTimestamp(missedCallMessage.timestamp)) {
                            AppDatabase.getInstance().discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                        }
                    }
                });
            }
            Logger.i("Discarded an outdated webrtc message (age = " + (downloadTimestamp - serverTimestamp) + "ms)");
            return;
        }

        Intent intent = new Intent(getContext(), WebrtcCallService.class);
        intent.setAction(WebrtcCallService.ACTION_MESSAGE);
        intent.putExtra(WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(WebrtcCallService.BYTES_CONTACT_IDENTITY_INTENT_EXTRA, bytesContactIdentity);
        intent.putExtra(WebrtcCallService.CALL_IDENTIFIER_INTENT_EXTRA, Logger.getUuidString(jsonWebrtcMessage.getCallIdentifier()));
        intent.putExtra(WebrtcCallService.MESSAGE_TYPE_INTENT_EXTRA, messageType);
        intent.putExtra(WebrtcCallService.SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA, jsonWebrtcMessage.getSerializedMessagePayload());

        if (messageType == WebrtcCallService.START_CALL_MESSAGE_TYPE
                || messageType == WebrtcCallService.NEW_ICE_CANDIDATE_MESSAGE_TYPE
                || messageType == WebrtcCallService.REMOVE_ICE_CANDIDATES_MESSAGE_TYPE
                || messageType == WebrtcCallService.ANSWERED_OR_REJECTED_ON_OTHER_DEVICE_MESSAGE_TYPE) {
            getContext().startService(intent);
        } else {
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        }
    }

    public static void openGroupDetailsActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) {
        Intent intent = new Intent(getContext(), GroupDetailsActivity.class);
        intent.putExtra(GroupDetailsActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(GroupDetailsActivity.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA, bytesGroupOwnerAndUid);
        activityContext.startActivity(intent);
    }

    public static void openGroupDetailsActivityForEditDetails(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) {
        Intent intent = new Intent(getContext(), GroupDetailsActivity.class);
        intent.putExtra(GroupDetailsActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(GroupDetailsActivity.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA, bytesGroupOwnerAndUid);
        intent.putExtra(GroupDetailsActivity.EDIT_DETAILS_INTENT_EXTRA, true);
        activityContext.startActivity(intent);
    }

    public static void openGroupV2DetailsActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) {
        Intent intent = new Intent(getContext(), GroupV2DetailsActivity.class);
        intent.putExtra(GroupV2DetailsActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(GroupV2DetailsActivity.BYTES_GROUP_IDENTIFIER_INTENT_EXTRA, bytesGroupIdentifier);
        activityContext.startActivity(intent);
    }

    public static void openGroupV2DetailsActivityForEditDetails(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) {
        Intent intent = new Intent(getContext(), GroupV2DetailsActivity.class);
        intent.putExtra(GroupV2DetailsActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(GroupV2DetailsActivity.BYTES_GROUP_IDENTIFIER_INTENT_EXTRA, bytesGroupIdentifier);
        intent.putExtra(GroupV2DetailsActivity.EDIT_DETAILS_INTENT_EXTRA, true);
        activityContext.startActivity(intent);
    }

    public static void openMessageDetails(Context activityContext, long messageId, boolean hasAttachments, boolean isInbound, boolean sentFromOtherDevice) {
        Intent intent = new Intent(getContext(), MessageDetailsActivity.class);
        intent.putExtra(MessageDetailsActivity.MESSAGE_ID_INTENT_EXTRA, messageId);
        intent.putExtra(MessageDetailsActivity.HAS_ATTACHMENT_INTENT_EXTRA, hasAttachments);
        intent.putExtra(MessageDetailsActivity.IS_INBOUND_INTENT_EXTRA, isInbound);
        intent.putExtra(MessageDetailsActivity.SENT_FROM_OTHER_DEVICE_INTENT_EXTRA, sentFromOtherDevice);
        activityContext.startActivity(intent);
    }

    public static void showMainActivityTab(Context activityContext, int tabId) {
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.putExtra(MainActivity.TAB_TO_SHOW_INTENT_EXTRA, tabId);
        activityContext.startActivity(intent);
    }

    public static void openLocationInMapApplication(Activity activity, String latitude, String longitude, String fallbackUri, @Nullable Runnable onOpenCallback) {
        try {
            Intent geoIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format("geo:%s,%s?q=%s,%s&z=17", latitude, longitude, latitude, longitude)));
            geoIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (getContext().getPackageManager().queryIntentActivities(geoIntent, PackageManager.MATCH_DEFAULT_ONLY).size() > 0) {
                openLocationIntent(activity, geoIntent, onOpenCallback);
                return;
            } else {
                Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri));
                fallbackIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (getContext().getPackageManager().queryIntentActivities(fallbackIntent, PackageManager.MATCH_DEFAULT_ONLY).size() > 0) {
                    openLocationIntent(activity, fallbackIntent, onOpenCallback);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        App.toast(R.string.toast_message_impossible_to_open_map, Toast.LENGTH_SHORT, Gravity.BOTTOM);
    }

    private static void openLocationIntent(Activity activity, Intent geoIntent, @Nullable Runnable onOpenCallback) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
        if (prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP_LOCATION, false)) {
            if (onOpenCallback != null) {
                onOpenCallback.run();
            }
            killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
            Intent lockIntent = new Intent(activity, LockScreenActivity.class);
            activity.startActivity(lockIntent);
            activity.startActivity(geoIntent);
        } else {
            View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_view_message_and_checkbox, null);
            TextView message = dialogView.findViewById(R.id.dialog_message);
            message.setText(R.string.dialog_message_open_external_app_location_warning);
            CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP_LOCATION, isChecked);
                editor.apply();
            });

            AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
            builder.setTitle(R.string.dialog_title_open_external_app_location_warning)
                    .setView(dialogView)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_proceed, (dialog, which) -> {
                        if (onOpenCallback != null) {
                            onOpenCallback.run();
                        }
                        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
                        Intent lockIntent = new Intent(activity, LockScreenActivity.class);
                        activity.startActivity(lockIntent);
                        activity.startActivity(geoIntent);
                    });
            builder.create().show();
        }
    }

    public static void openFyleInExternalViewer(Context context, FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus, Runnable onOpenCallback) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fyleAndStatus.getContentUriForExternalSharing(), fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (getContext().getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
            // if the mime type is not openable, try to fallback to the default mime type from extension
            String extension = StringUtils2.Companion.getExtensionFromFilename(fyleAndStatus.fyleMessageJoinWithStatus.fileName);
            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (type != null) {
                intent.setDataAndType(fyleAndStatus.getContentUriForExternalSharing(), type);
            }
            if (type == null || getContext().getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
                App.toast(R.string.toast_message_unable_to_open_file, Toast.LENGTH_SHORT);
                return;
            }
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
        if (prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP, false)) {
            if (onOpenCallback != null) {
                onOpenCallback.run();
            }
            killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
            Intent lockIntent = new Intent(context, LockScreenActivity.class);
            context.startActivity(lockIntent);
            context.startActivity(intent);
        } else {
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_view_message_and_checkbox, null);
            TextView message = dialogView.findViewById(R.id.dialog_message);
            message.setText(R.string.dialog_message_open_external_app_warning);
            CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP, isChecked);
                editor.apply();
            });

            AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog);
            builder.setTitle(R.string.dialog_title_open_external_app_warning)
                    .setView(dialogView)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_proceed, (dialog, which) -> {
                        if (onOpenCallback != null) {
                            onOpenCallback.run();
                        }
                        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
                        Intent lockIntent = new Intent(context, LockScreenActivity.class);
                        context.startActivity(lockIntent);
                        context.startActivity(intent);
                    });
            builder.create().show();
        }
    }

    public static void openLink(Context context, Uri uri) {
        if (context == null || uri == null) {
            return;
        }

        // first check if this is an Olvid link
        boolean olvidLink = ObvLinkActivity.ANY_PATTERN.matcher(uri.toString()).find();
        final AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                .setTitle(olvidLink ? R.string.dialog_title_confirm_open_olvid_link : R.string.dialog_title_confirm_open_link)
                .setMessage(uri.toString())
                .setNeutralButton(R.string.button_label_copy, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(uri.toString(), uri.toString());
                    clipboard.setPrimaryClip(clip);
                    toast(R.string.toast_message_link_copied, Toast.LENGTH_SHORT);
                })
                .setPositiveButton(R.string.button_label_ok, (dialog, which) -> {
                    if (olvidLink) {
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.setAction(MainActivity.LINK_ACTION);
                        intent.putExtra(MainActivity.LINK_URI_INTENT_EXTRA, uri.toString());
                        context.startActivity(intent);
                    } else {
                        try {
                            context.startActivity(new Intent(Intent.ACTION_VIEW, uri));

                            int unwraps = 0;
                            Context baseContext = context;
                            while (!(baseContext instanceof DiscussionActivity) && baseContext instanceof ContextThemeWrapper) {
                                baseContext = ((ContextThemeWrapper) baseContext).getBaseContext();
                                unwraps++;
                                if (unwraps > 10) {
                                    break;
                                }
                            }
                            if (baseContext instanceof DiscussionActivity) {
                                ((DiscussionActivity) baseContext).getDiscussionDelegate().doNotMarkAsReadOnPause();
                            }
                        } catch (Exception e) {
                            App.toast(R.string.toast_message_unable_to_open_url, Toast.LENGTH_SHORT);
                        }
                    }
                })
                .setNegativeButton(R.string.button_label_cancel, null);
        builder.create().show();
    }

    public static String absolutePathFromRelative(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        return new File(getContext().getNoBackupFilesDir(), relativePath).getAbsolutePath();
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({android.widget.Toast.LENGTH_SHORT, android.widget.Toast.LENGTH_LONG})
    public @interface ToastLength {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({android.view.Gravity.CENTER, android.view.Gravity.BOTTOM, android.view.Gravity.TOP})
    public @interface ToastGravity {}

    public static void toast(@StringRes final int resId, @ToastLength final int duration) {
        toast(getContext().getString(resId), duration, Gravity.BOTTOM);
    }

    public static void toast(@StringRes final int resId, @ToastLength final int duration, @ToastGravity final int gravity) {
        toast(getContext().getString(resId), duration, gravity);
    }

    public static void toast(final String message, @ToastLength final int duration, @ToastGravity final int gravity) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast toast;
            if (App.isVisible() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) {
                LayoutInflater layoutInflater = (LayoutInflater) App.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (layoutInflater == null) {
                    return;
                }
                @SuppressLint("InflateParams") View toastLayout = layoutInflater.inflate(R.layout.view_toast, null);
                TextView text = toastLayout.findViewById(R.id.toast_text);
                text.setText(message);
                toast = new Toast(getContext());
                toast.setDuration(duration);
                toast.setView(toastLayout);
            } else {
                toast = Toast.makeText(App.getContext(), message, duration);
            }
            switch (gravity) {
                case Gravity.TOP:
                    toast.setGravity(Gravity.TOP, 0, getContext().getResources().getDisplayMetrics().densityDpi * 96 / 160);
                    break;
                case Gravity.CENTER:
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    break;
                case Gravity.BOTTOM:
                default:
                    break;
            }
            toast.show();
        });
    }


    // region App-wide dialogs

    public static void releaseAppDialogShowing() {
        dialogsToShowLock.lock();
        isAppDialogShowing = false;
        dialogsToShowLock.unlock();
        showAppDialogs();
    }

    public static boolean requestAppDialogShowing() {
        dialogsToShowLock.lock();
        if (isAppDialogShowing) {
            dialogsToShowLock.unlock();
            return false;
        }
        isAppDialogShowing = true;
        dialogsToShowLock.unlock();
        return true;
    }

    public static void showAppDialogs() {
        if (blockAppDialogs) {
            return;
        }
        dialogsToShowLock.lock();
        if (!dialogsToShow.isEmpty()) {
            openAppDialogShower();
        } else if (AppSingleton.getBytesCurrentIdentity() != null) {
            BytesKey bytesKey = new BytesKey(AppSingleton.getBytesCurrentIdentity());
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map != null && !map.isEmpty()) {
                openAppDialogShower();
            }
        }
        dialogsToShowLock.unlock();
    }

    public static void showAppDialogsForSelectedIdentity(@NonNull byte[] bytesOwnedIdentity) {
        if (blockAppDialogs) {
            return;
        }
        dialogsToShowLock.lock();
        BytesKey bytesKey = new BytesKey(bytesOwnedIdentity);
        LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
        if (map != null && !map.isEmpty()) {
            openAppDialogShower();
        }
        dialogsToShowLock.unlock();
    }

    public static void setAppDialogsBlocked(boolean blocked) {
        if (blocked) {
            blockAppDialogs = true;
        } else {
            blockAppDialogs = false;
            showAppDialogs();
        }
    }

    public static void openAppDialogIdentityDeactivated(OwnedIdentity ownedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_IDENTITY_DEACTIVATED_OWNED_IDENTITY_KEY, ownedIdentity);
        showDialog(ownedIdentity.bytesOwnedIdentity, AppDialogShowActivity.DIALOG_IDENTITY_DEACTIVATED, dialogParameters);
    }

    public static void openAppDialogIdentityActivated(OwnedIdentity ownedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_IDENTITY_ACTIVATED_OWNED_IDENTITY_KEY, ownedIdentity);
        showDialog(ownedIdentity.bytesOwnedIdentity, AppDialogShowActivity.DIALOG_IDENTITY_ACTIVATED, dialogParameters);
    }

    public static void openAppDialogApiKeyPermissionsUpdated(OwnedIdentity ownedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_SUBSCRIPTION_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
        showDialog(ownedIdentity.bytesOwnedIdentity, AppDialogShowActivity.DIALOG_SUBSCRIPTION_UPDATED, dialogParameters);
    }

    public static void openAppDialogSubscriptionRequired(byte[] bytesOwnedIdentity, EngineAPI.ApiKeyPermission permission) {
        if (bytesOwnedIdentity == null) {
            return;
        }
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_SUBSCRIPTION_REQUIRED_FEATURE_KEY, permission);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_SUBSCRIPTION_REQUIRED, dialogParameters);
    }

    public static void openAppDialogNewVersionAvailable() {
        showDialog(null, AppDialogShowActivity.DIALOG_NEW_VERSION_AVAILABLE, new HashMap<>());
    }

    public static void openAppDialogOutdatedVersion() {
        showDialog(null, AppDialogShowActivity.DIALOG_OUTDATED_VERSION, new HashMap<>());
    }

    public static void openAppDialogCallInitiationNotSupported(byte[] bytesOwnedIdentity) {
        if (bytesOwnedIdentity == null) {
            return;
        }
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_CALL_INITIATION_NOT_SUPPORTED, new HashMap<>());
    }

    public static void openAppDialogKeycloakAuthenticationRequired(@NonNull byte[] bytesOwnedIdentity, @NonNull String clientId, @Nullable String clientSecret, @NonNull String keycloakServerUrl) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_ID_KEY, clientId);
        if (clientSecret != null) {
            dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_SECRET_KEY, clientSecret);
        }
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_SERVER_URL_KEY, keycloakServerUrl);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED, dialogParameters);
    }

    public static void openAppDialogKeycloakIdentityReplacement(@NonNull byte[] bytesOwnedIdentity, @NonNull String serverUrl, @Nullable String clientSecret, @NonNull String serializedAuthState) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERVER_URL_KEY, serverUrl);
        if (clientSecret != null) {
            dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_CLIENT_SECRET_KEY, clientSecret);
        }
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERIALIZED_AUTH_STATE_KEY, serializedAuthState);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT, dialogParameters);
    }

    public static void openAppDialogKeycloakUserIdChanged(@NonNull byte[] bytesOwnedIdentity, @NonNull String clientId, @Nullable String clientSecret, @NonNull String keycloakServerUrl) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_ID_KEY, clientId);
        if (clientSecret != null) {
            dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_SECRET_KEY, clientSecret);
        }
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED_SERVER_URL_KEY, keycloakServerUrl);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED, dialogParameters);
    }

    public static void openAppDialogKeycloakSignatureKeyChanged(@NonNull byte[] bytesOwnedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED, dialogParameters);
    }

    public static void openAppDialogKeycloakIdentityReplacementForbidden(@NonNull byte[] bytesOwnedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN, dialogParameters);
    }

    public static void openAppDialogKeycloakIdentityRevoked(byte[] bytesOwnedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED, dialogParameters);
    }

    public static void openAppDialogSdCardRingtoneBuggedAndroid9() {
        showDialog(null, AppDialogShowActivity.DIALOG_SD_CARD_RINGTONE_BUGGED_ANDROID_9, new HashMap<>());
    }

    public static void openAppDialogCertificateChanged(long untrustedCertificateId, @Nullable Long lastTrustedCertificateId) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_CERTIFICATE_CHANGED_UNTRUSTED_CERTIFICATE_ID_KEY, untrustedCertificateId);
        if (lastTrustedCertificateId != null) {
            dialogParameters.put(AppDialogShowActivity.DIALOG_CERTIFICATE_CHANGED_LAST_TRUSTED_CERTIFICATE_ID_KEY, lastTrustedCertificateId);
        }
        // we add a suffix to the dialog name so that multiple dialogs can be opened for multiple new certificates
        showDialog(null, AppDialogShowActivity.DIALOG_CERTIFICATE_CHANGED + "_" + untrustedCertificateId, dialogParameters);
    }

    public static void openAppDialogLowStorageSpace() {
        showDialog(null, AppDialogShowActivity.DIALOG_AVAILABLE_SPACE_LOW, new HashMap<>());
    }

    private static long backupRequireSignInCooldown = 0;
    public static void openAppDialogBackupRequiresSignIn() {
        if (System.currentTimeMillis() > backupRequireSignInCooldown) {
            backupRequireSignInCooldown = System.currentTimeMillis() + 3_600_000L; // add a 1 hour cooldown between dialogs
            showDialog(null, AppDialogShowActivity.DIALOG_BACKUP_REQUIRES_SIGN_IN, new HashMap<>());
        }
    }

    public static void openAppDialogConfigureHiddenProfileClosePolicy() {
        showDialog(null, AppDialogShowActivity.DIALOG_CONFIGURE_HIDDEN_PROFILE_CLOSE_POLICY, new HashMap<>());
    }

    public static void openAppDialogIntroducingMultiProfile() {
        showDialog(null, AppDialogShowActivity.DIALOG_INTRODUCING_MULTI_PROFILE, new HashMap<>());
    }

    public static void openAppDialogIntroducingGroupsV2() {
        showDialog(null, AppDialogShowActivity.DIALOG_INTRODUCING_GROUPS_V2, new HashMap<>());
    }

    public static void openAppDialogIntroducingMentions() {
        showDialog(null, AppDialogShowActivity.DIALOG_INTRODUCING_MENTIONS, new HashMap<>());
    }

    public static void openAppDialogIntroducingMarkdown() {
        showDialog(null, AppDialogShowActivity.DIALOG_INTRODUCING_MARKDOWN, new HashMap<>());
    }

    public static void openAppDialogIntroducingMultiDeviceAndDesktop() {
        showDialog(null, AppDialogShowActivity.DIALOG_INTRODUCING_MULTI_DEVICE, new HashMap<>());
    }

    public static void openAppDialogPromptUserForReadReceiptsIfRelevant() {
        if (SettingsActivity.getDefaultSendReadReceipt()) {
            // if read receipt are active, do not prompt
            return;
        }
        long lastReadReceiptAnswer = SettingsActivity.getLastReadReceiptPromptAnswerTimestamp();
        if (lastReadReceiptAnswer == -1 || (System.currentTimeMillis() - lastReadReceiptAnswer < 2 * 86_400_000L)) {
            // if user already answered, or answered less than 2 days ago, do not prompt
            return;
        }
        App.runThread(() -> {
            if (AppDatabase.getInstance().contactDao().countAll() == 0) {
                // if user has no contacts, do not prompt
                return;
            }
            showDialog(null, AppDialogShowActivity.DIALOG_PROMPT_USER_FOR_READ_RECEIPTS, new HashMap<>());
        });
    }

    private static void showDialog(@Nullable byte[] bytesDialogOwnedIdentity, String dialogTag, HashMap<String, Object> dialogParameters) {
        dialogsToShowLock.lock();
        if (bytesDialogOwnedIdentity == null) {
            dialogsToShow.put(dialogTag, dialogParameters);
        } else {
            BytesKey bytesKey = new BytesKey(bytesDialogOwnedIdentity);
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map == null) {
                map = new LinkedHashMap<>();
                dialogsToShowForOwnedIdentity.put(bytesKey, map);
            }
            map.put(dialogTag, dialogParameters);
        }
        dialogsToShowLock.unlock();
        if (!SettingsActivity.useApplicationLockScreen() || !UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            showAppDialogs();
        }
    }

    public static AppDialogTag getNextDialogTag() {
        dialogsToShowLock.lock();
        // first check if there is an ownedIdentity specific dialog to show
        byte[] currentIdentity = AppSingleton.getBytesCurrentIdentity();
        if (currentIdentity != null) {
            BytesKey bytesKey = new BytesKey(currentIdentity);
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map != null && !map.isEmpty()) {
                AppDialogTag dialogTag;
                if (map.containsKey(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED)) {
                    dialogTag = new AppDialogTag(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED, currentIdentity);
                } else {
                    dialogTag = new AppDialogTag(map.keySet().iterator().next(), currentIdentity);
                }
                dialogsToShowLock.unlock();
                return dialogTag;
            }
        }

        // there is no app specific dialog to show --> check for generic dialogs
        if (!dialogsToShow.isEmpty()) {
            AppDialogTag dialogTag = new AppDialogTag(dialogsToShow.keySet().iterator().next(), null);
            dialogsToShowLock.unlock();
            return dialogTag;
        }

        // there is no dialog to show
        dialogsToShowLock.unlock();
        return null;
    }

    public static HashMap<String, Object> getDialogParameters(AppDialogTag dialogTag) {
        if (dialogTag.bytesDialogOwnedIdentity == null) {
            return dialogsToShow.get(dialogTag.dialogTag);
        } else {
            BytesKey bytesKey = new BytesKey(dialogTag.bytesDialogOwnedIdentity);
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map != null) {
                return map.get(dialogTag.dialogTag);
            }
            return null;
        }
    }

    public static void removeDialog(AppDialogTag dialogTag) {
        dialogsToShowLock.lock();
        if (dialogTag.bytesDialogOwnedIdentity == null) {
            dialogsToShow.remove(dialogTag.dialogTag);
        } else {
            BytesKey bytesKey = new BytesKey(dialogTag.bytesDialogOwnedIdentity);
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map != null) {
                map.remove(dialogTag.dialogTag);
            }
        }
        dialogsToShowLock.unlock();
    }

    // endregion


    public static void runThread(Runnable runnable) {
        executorService.submit(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void setQrCodeImage(@NonNull ImageView imageView, @NonNull final String qrCodeData) {
        final WeakReference<ImageView> imageViewWeakReference = new WeakReference<>(imageView);
        App.runThread(() -> {
            try {
                HashMap<EncodeHintType, Object> hints = new HashMap<>();
                hints.put(EncodeHintType.MARGIN, 0);

                switch (SettingsActivity.getQrCorrectionLevel()) {
                    case "L":
                        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
                        break;
                    case "Q":
                        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
                        break;
                    case "H":
                        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                        break;
                    case "M":
                    default:
                        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                }
                BitMatrix qrcode = new MultiFormatWriter().encode(qrCodeData, BarcodeFormat.QR_CODE, 0, 0, hints);
                int w = qrcode.getWidth();
                int h = qrcode.getHeight();
                int onColor = ContextCompat.getColor(App.getContext(), R.color.black);
                int offColor = Color.TRANSPARENT;

                int[] pixels = new int[h * w];
                int offset = 0;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        pixels[offset++] = qrcode.get(x, y) ? onColor : offColor;
                    }
                }
                final Bitmap smallQrCodeBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                smallQrCodeBitmap.setPixels(pixels, 0, w, 0, 0, w, h);
                DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
                int width = metrics.widthPixels;
                int height = metrics.heightPixels;
                final int size = Math.min(height, width);
                final ImageView imageView1 = imageViewWeakReference.get();
                final Bitmap scaledBitmap = Bitmap.createScaledBitmap(smallQrCodeBitmap, size, size, false);
                if (imageView1 != null) {
                    new Handler(Looper.getMainLooper()).post(() -> imageView1.setImageBitmap(scaledBitmap));
                }
            } catch (Exception e) {
                final ImageView imageView1 = imageViewWeakReference.get();
                if (imageView1 != null) {
                    new Handler(Looper.getMainLooper()).post(() -> imageView1.setImageDrawable(null));
                }
                e.printStackTrace();
            }
        });
    }

    public static void refreshRegisterToPushNotification() {
        try {
            String token = AppSingleton.retrieveFirebaseToken();
            for (ObvIdentity obvOwnedIdentity : AppSingleton.getEngine().getOwnedIdentities()) {
                if (obvOwnedIdentity.isActive()) {
                    try {
                        AppSingleton.getEngine().registerToPushNotification(obvOwnedIdentity.getBytesIdentity(), ObvPushNotificationType.createAndroid(token), false, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    static class AppStartupTasks implements Runnable {

        @Override
        public void run() {
            //////////////////////////
            // initialize emoji2
            //////////////////////////
            EmojiCompat.Config emojiConfig = new BundledEmojiCompatConfig(getContext());
            if (!SettingsActivity.useSystemEmojis()) {
                emojiConfig.setReplaceAll(true);
            }
            EmojiCompat.init(emojiConfig);

            //////////////////////////
            // start monitoring network status
            //////////////////////////
            NetworkStateMonitorReceiver.startMonitoringNetwork(getContext());


            //////////////////////////
            // notify Engine whether autobackup is on or not
            //////////////////////////
            AppSingleton.getEngine().setAutoBackupEnabled(SettingsActivity.useAutomaticBackup(), true);


            //////////////////////////
            // register push notifications for all identities
            //////////////////////////
            refreshRegisterToPushNotification();

            //////////////////////////
            // start the MessageExpirationService (in case an alarm was missed)
            //////////////////////////
            Intent expirationIntent = new Intent(getContext(), MessageExpirationService.class);
            expirationIntent.setAction(MessageExpirationService.EXPIRE_MESSAGES_ACTION);
            getContext().sendBroadcast(expirationIntent);


            //////////////////////////
            // schedule all periodic tasks
            //////////////////////////
            PeriodicTasksScheduler.schedulePeriodicTasks(App.getContext());


            //////////////////////////
            // create and maintain dynamic shortcuts
            //////////////////////////
            ShortcutActivity.startPublishingShareTargets(getContext());


            //////////////////////////
            // check if there are settings pushed from an MDM
            //////////////////////////
            try {
                String mdmSettingsConfigurationUri = MDMConfigurationSingleton.getSettingsConfigurationUri();
                if (mdmSettingsConfigurationUri != null) {
                    Matcher matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(mdmSettingsConfigurationUri);
                    if (matcher.find()) {
                        ConfigurationPojo configurationPojo = AppSingleton.getJsonObjectMapper().readValue(ObvBase64.decode(matcher.group(2)), ConfigurationPojo.class);
                        if (configurationPojo.settings != null) {
                            configurationPojo.settings.toBackupPojo().restore();
                        }
                    }
                }
            } catch (Exception e) {
                Logger.e("Error parsing MDM pushed settings configuration");
                e.printStackTrace();
            }

            //////////////////////////
            // check if there is a WebDAV automatic backups configuration pushed from an MDM
            //////////////////////////
            try {
                configureMdmWebDavAutomaticBackups();
            } catch (Exception e) {
                Logger.e("Error configuring MDM pushed WebDav automatic backups");
                e.printStackTrace();
            }


            ///////////////////////
            // clean the CAMERA_PICTURE_FOLDER
            ///////////////////////
            File cameraPictureCacheDir = new File(getApplication().getCacheDir(), App.CAMERA_PICTURE_FOLDER);
            //noinspection ResultOfMethodCallIgnored
            cameraPictureCacheDir.mkdirs();
            File[] files = cameraPictureCacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        long modificationTime = file.lastModified();
                        // only delete files modified more than 1 day ago (this should leave enough time for the group v2 protocol to run!)
                        if (modificationTime < System.currentTimeMillis() - 86_400_000L) {
                            //noinspection ResultOfMethodCallIgnored
                            file.delete();
                        }
                    }
                }
            }

            ///////////////////////
            // clean the WEBCLIENT_ATTACHMENT_FOLDER
            ///////////////////////
            File attachments = new File(getApplication().getCacheDir(), App.WEBCLIENT_ATTACHMENT_FOLDER);
            //noinspection ResultOfMethodCallIgnored
            attachments.mkdirs();
            files = attachments.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                }
            }

            ///////////////////////
            // check if there are any sharing message in db, and ask LocationSharing service to handle them if needed
            ///////////////////////
            int count = AppDatabase.getInstance().messageDao().countOutboundSharingLocationMessages();
            if (count > 0) {
                try {
                    UnifiedForegroundService.LocationSharingSubService.syncSharingMessages();
                } catch (Exception ignored) {}
            }
        }


        private static EngineNotificationListener backupKeyListener = null;

        static void configureMdmWebDavAutomaticBackups() throws Exception {
            BackupCloudProviderService.CloudProviderConfiguration mdmAutoBackupConfiguration = MDMConfigurationSingleton.getAutoBackupConfiguration();
            if (mdmAutoBackupConfiguration != null) {
                BackupCloudProviderService.CloudProviderConfiguration currentAutoBackupConfiguration = SettingsActivity.getAutomaticBackupConfiguration();
                if (!mdmAutoBackupConfiguration.equals(currentAutoBackupConfiguration) || !SettingsActivity.useAutomaticBackup()) {
                    SettingsActivity.setAutomaticBackupConfiguration(mdmAutoBackupConfiguration);
                    SettingsActivity.setMdmAutomaticBackup(true);
                    if (!SettingsActivity.useAutomaticBackup()) {
                        SettingsActivity.setUseAutomaticBackup(true);
                        AppSingleton.getEngine().setAutoBackupEnabled(true, true);
                    }
                }

                String keyEscrowPublicKeyString = MDMConfigurationSingleton.getWebdavKeyEscrowPublicKeyString();
                if (!Objects.equals(keyEscrowPublicKeyString, SettingsActivity.getMdmWebdavKeyEscrowPublicKey())) {
                    if (keyEscrowPublicKeyString == null) {
                        // key escrow was deactivated --> clear the setting
                        SettingsActivity.setMdmWebdavKeyEscrowPublicKey(null);
                    } else {
                        // key escrow is active, but backup key is not in escrow yet
                        try {
                            ObvBackupKeyInformation backupKeyInformation = AppSingleton.getEngine().getBackupKeyInformation();
                            if (backupKeyInformation == null) {
                                // no key generated yet --> generate one and attempt to put it in escrow
                                backupKeyListener = new EngineNotificationListener() {
                                    Long registrationNumber = null;
                                    @Override
                                    public void callback(String notificationName, HashMap<String, Object> userInfo) {
                                        switch (notificationName) {
                                            case EngineNotifications.BACKUP_SEED_GENERATION_FAILED: {
                                                // do nothing, we will try again at next startup!
                                                break;
                                            }
                                            case EngineNotifications.NEW_BACKUP_SEED_GENERATED: {
                                                String backupKeyString = (String) userInfo.get(EngineNotifications.NEW_BACKUP_SEED_GENERATED_SEED_KEY);
                                                BackupCloudProviderService.uploadBackupKeyEscrow(mdmAutoBackupConfiguration, MDMConfigurationSingleton.getWebdavKeyEscrowPublicKey(), backupKeyString, new BackupCloudProviderService.OnKeyEscrowCallback() {
                                                    @Override
                                                    public void onKeyEscrowSuccess() {
                                                        // key was successfully put in escrow --> store it in settings
                                                        SettingsActivity.setMdmWebdavKeyEscrowPublicKey(keyEscrowPublicKeyString);
                                                    }

                                                    @Override
                                                    public void onKeyEscrowFailure(int error) {
                                                        // we generated a key but could not put it in escrow --> fallback to notifying the user a new key generation is required
                                                        showDialog(null, AppDialogShowActivity.DIALOG_KEY_ESCROW_REQUIRED, new HashMap<>());
                                                    }
                                                });
                                                break;
                                            }
                                            default: {
                                                return;
                                            }
                                        }

                                        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.BACKUP_SEED_GENERATION_FAILED, backupKeyListener);
                                        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.NEW_BACKUP_SEED_GENERATED, backupKeyListener);
                                        backupKeyListener = null;
                                    }

                                    @Override
                                    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
                                        this.registrationNumber = registrationNumber;
                                    }

                                    @Override
                                    public long getEngineNotificationListenerRegistrationNumber() {
                                        return registrationNumber;
                                    }

                                    @Override
                                    public boolean hasEngineNotificationListenerRegistrationNumber() {
                                        return registrationNumber != null;
                                    }
                                };
                                AppSingleton.getEngine().addNotificationListener(EngineNotifications.BACKUP_SEED_GENERATION_FAILED, backupKeyListener);
                                AppSingleton.getEngine().addNotificationListener(EngineNotifications.NEW_BACKUP_SEED_GENERATED, backupKeyListener);

                                AppSingleton.getEngine().generateBackupKey();
                            } else {
                                // backup key exists and is not in escrow --> prompt user to generate a new key
                                showDialog(null, AppDialogShowActivity.DIALOG_KEY_ESCROW_REQUIRED, new HashMap<>());
                            }
                        } catch (Exception e) {
                            // do nothing if there is an exception --> we were not able to check if a backup key exists
                        }
                    }
                }
            } else if (SettingsActivity.isMdmAutomaticBackup()) {
                // backups used to be configured by MDM but no longer are --> disable automatic backups and clear the previous configuration
                SettingsActivity.setAutomaticBackupConfiguration(null);
                SettingsActivity.setUseAutomaticBackup(false);
                SettingsActivity.setMdmAutomaticBackup(false);
                SettingsActivity.setMdmWebdavKeyEscrowPublicKey(null);
                AppSingleton.getEngine().setAutoBackupEnabled(false, false);
            }
        }
    }


}
