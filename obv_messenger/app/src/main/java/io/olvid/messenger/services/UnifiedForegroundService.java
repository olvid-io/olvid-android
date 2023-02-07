/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.services;


import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.ObvPostMessageOutput;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.HandlerExecutor;
import io.olvid.messenger.customClasses.LockScreenOrNotActivity;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.tasks.UpdateLocationMessageTask;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.webclient.WebClientManager;
import io.olvid.messenger.webclient.datatypes.Constants;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass;
import io.olvid.messenger.webrtc.WebrtcCallActivity;
import io.olvid.messenger.webrtc.WebrtcIncomingCallActivity;

public class UnifiedForegroundService extends Service {
    public static final int SERVICE_ID = 8957;

    public static final String SUB_SERVICE_INTENT_EXTRA = "sub_service_intent_extra";

    public static final int SUB_SERVICE_LOCK = 1;
    public static final int SUB_SERVICE_WEB_CLIENT = 2;
    public static final int SUB_SERVICE_MESSAGE_SENDING = 3;
    public static final int SUB_SERVICE_LOCATION_SHARING = 4;

    final NoExceptionSingleThreadExecutor executor = new NoExceptionSingleThreadExecutor("UnifiedForegroundService-Executor");

    private final ServiceBinder serviceBinder = new ServiceBinder();

    private LockSubService lockSubService = null;
    private WebClientSubService webClientSubService = null;
    private MessageSendingSubService messageSendingSubService = null;
    private LocationSharingSubService locationSharingSubService = null;

    public static void onAppForeground(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            // cancel any pending lock
            Intent lockIntent = new Intent(LockSubService.LOCK_APP_ACTION, null, context, UnifiedForegroundService.class);
            lockIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_LOCK);
            PendingIntent pendingLockIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pendingLockIntent = PendingIntent.getService(context, 0, lockIntent, PendingIntent.FLAG_MUTABLE);
            } else {
                pendingLockIntent = PendingIntent.getService(context, 0, lockIntent, 0);
            }
            alarmManager.cancel(pendingLockIntent);

            // cancel any pending close hidden profile
            Intent closeHiddenProfileIntent = new Intent(LockSubService.CLOSE_HIDDEN_PROFILE_ACTION, null, context, UnifiedForegroundService.class);
            closeHiddenProfileIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_LOCK);
            PendingIntent pendingCloseHiddenProfileIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pendingCloseHiddenProfileIntent = PendingIntent.getService(context, 0, closeHiddenProfileIntent, PendingIntent.FLAG_MUTABLE);
            } else {
                pendingCloseHiddenProfileIntent = PendingIntent.getService(context, 0, closeHiddenProfileIntent, 0);
            }
            alarmManager.cancel(pendingCloseHiddenProfileIntent);
        }

        connectOrDisconnectWebSocket();
    }

    public static void onAppBackground(Context context) {
        LockSubService.scheduleBackgroundLockIfNeeded(context);
        LockSubService.scheduleHiddenProfileCloseIfNeeded(context);

        connectOrDisconnectWebSocket();
    }

    public static void lockApplication(FragmentActivity activity, Integer customMessageResourceId) {
        Intent lockIntent = new Intent(LockSubService.LOCK_APP_ACTION, null, activity, UnifiedForegroundService.class);
        lockIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_LOCK);
        if (customMessageResourceId != null) {
            lockIntent.putExtra(UnifiedForegroundService.LockSubService.CUSTOM_LOCK_SCREEN_MESSAGE_RESOURCE_ID_INTENT_EXTRA, customMessageResourceId);
        }
        activity.startService(lockIntent);
    }

    public static void connectOrDisconnectWebSocket() {
        if (App.isVisible() || WebClientSubService.isRunning || SettingsActivity.usePermanentWebSocket()) {
            if (SettingsActivity.shareAppVersion()) {
                AppSingleton.getEngine().connectWebsocket("android", Integer.toString(android.os.Build.VERSION.SDK_INT), BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
            } else {
                AppSingleton.getEngine().connectWebsocket(null, null, 0, null);
            }
        } else {
            // only disconnect websocket after 3 seconds
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!App.isVisible() && !WebClientSubService.isRunning && !SettingsActivity.usePermanentWebSocket()) {
                    AppSingleton.getEngine().disconnectWebsocket();
                }
            }, 3000);
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        stopOrRestartForegroundService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (lockSubService != null) {
            lockSubService.onDestroy();
            lockSubService = null;
        }
        if (webClientSubService != null) {
            webClientSubService.stopService();
        }
        if (messageSendingSubService != null) {
            messageSendingSubService.onDestroy();
            messageSendingSubService = null;
        }
        if (locationSharingSubService != null) {
            locationSharingSubService.onDestroy();
            locationSharingSubService = null;
        }
        executor.shutdownNow();
    }

    private static boolean removingExtraTasks = false;

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (removingExtraTasks) {
            return;
        }
        executor.execute(() -> {
            ComponentName intentComponent = rootIntent.getComponent();
            if (intentComponent != null) {
                if (WebrtcCallActivity.class.getName().equals(intentComponent.getClassName())
                        || WebrtcIncomingCallActivity.class.getName().equals(intentComponent.getClassName())) {
                    return;
                }
                try {
                    if (LockScreenOrNotActivity.class.isAssignableFrom(Class.forName(intentComponent.getClassName()))) {
                        return;
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (lockSubService != null) {
                lockSubService.onTaskRemoved();
            }
            if (webClientSubService != null) {
                webClientSubService.onTaskRemoved();
            }

            // purge the in-memory thumbnails cache to reclaim memory
            PreviewUtils.purgeCache();
        });
    }

    public static void finishAndRemoveExtraTasks(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager != null) {
                    List<ActivityManager.AppTask> appTasks = activityManager.getAppTasks();
                    if (appTasks != null && appTasks.size() > 1) {
                        removingExtraTasks = true;
                        for (ActivityManager.AppTask appTask : appTasks) {
                            ActivityManager.RecentTaskInfo taskInfo = appTask.getTaskInfo();
                            if (!taskInfo.isRunning) {
                                Logger.e("Removing empty task");
                                appTask.finishAndRemoveTask();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                removingExtraTasks = false;
            }
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        executor.execute(() -> {
            if (intent != null && intent.hasExtra(SUB_SERVICE_INTENT_EXTRA)) {
                int subService = intent.getIntExtra(SUB_SERVICE_INTENT_EXTRA, -1);
                switch (subService) {
                    case SUB_SERVICE_LOCK: {
                        if (intent.getAction() != null) {
                            if (lockSubService == null) {
                                lockSubService = new LockSubService(this);
                            }
                            lockSubService.onStartCommand(intent.getAction(), intent);
                        }
                        break;
                    }
                    case SUB_SERVICE_WEB_CLIENT: {
                        if (intent.getAction() != null) {
                            if (webClientSubService == null) {
                                if (AppSingleton.getBytesCurrentIdentity() == null) {
                                    break;
                                }
                                webClientSubService = new WebClientSubService(this, AppSingleton.getBytesCurrentIdentity());
                            }
                            webClientSubService.onStartCommand(intent.getAction(), intent);
                        }
                        break;
                    }
                    case SUB_SERVICE_MESSAGE_SENDING: {
                        if (intent.getAction() != null) {
                            if (messageSendingSubService == null) {
                                messageSendingSubService = new MessageSendingSubService(this);
                            }
                            messageSendingSubService.onStartCommand(intent.getAction(), intent);
                        }
                        break;
                    }
                    case SUB_SERVICE_LOCATION_SHARING: {
                        if (intent.getAction() != null) {
                            if (locationSharingSubService == null) {
                                locationSharingSubService = new LocationSharingSubService(this);
                            }
                            locationSharingSubService.onStartCommand(intent.getAction(), intent);
                        }
                    }
                }
            } else {
                stopOrRestartForegroundService();
            }
        });
        return START_NOT_STICKY;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (webClientSubService != null) {
            webClientSubService.onUnbind();
        }
        return false;
    }

    public static boolean willUnifiedForegroundServiceStartForeground() {
        boolean showWebClientNotification = WebClientSubService.isRunning;
        boolean showLockScreenNotification = SettingsActivity.useApplicationLockScreen() && (!LockSubService.locked || SettingsActivity.keepLockServiceOpen());
        boolean showPermanentNotification = SettingsActivity.usePermanentWebSocket() || SettingsActivity.usePermanentForegroundService();
        boolean showMessageSendingNotification = MessageSendingSubService.isSendingMessage;
        boolean showLocationSharingNotification = LocationSharingSubService.isSharingLocation;

        return showLockScreenNotification || showWebClientNotification || showPermanentNotification || showMessageSendingNotification || showLocationSharingNotification;
    }

    private void stopOrRestartForegroundService() {
        boolean showWebClientNotification = webClientSubService != null && WebClientSubService.isRunning;
        boolean showLockScreenNotification = SettingsActivity.useApplicationLockScreen() && (!LockSubService.locked || SettingsActivity.keepLockServiceOpen());
        boolean showPermanentNotification = SettingsActivity.usePermanentWebSocket() || SettingsActivity.usePermanentForegroundService();
        boolean showMessageSendingNotification = SettingsActivity.useSendingForegroundService() && MessageSendingSubService.isSendingMessage;
        boolean showLocationSharingNotification = locationSharingSubService != null && LocationSharingSubService.isSharingLocation;

        if (!showLockScreenNotification && !showWebClientNotification && !showPermanentNotification && !showMessageSendingNotification && !showLocationSharingNotification) {
            stopForeground(true);
            return;
        }


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, AndroidNotificationManager.UNIFIED_SERVICE_NOTIFICATION_CHANNEL_ID);
        builder.setWhen(0)
                .setPriority(NotificationCompat.PRIORITY_MIN);

        ////////
        // Title and Icon
        ////////
        if (showWebClientNotification) {
            builder.setContentTitle(getString(R.string.notification_title_webclient_connected))
                    .setSmallIcon(R.drawable.ic_webclient_animated);
        } else if (showMessageSendingNotification) {
            builder.setContentTitle(getString(R.string.notification_title_sending_message))
                    .setSmallIcon(R.drawable.ic_sending_animated);
        } else if (showLocationSharingNotification) {
            builder.setContentTitle(getString(R.string.notification_title_sharing_location))
                    .setSmallIcon(R.drawable.ic_satelite_animated);
        } else if (showLockScreenNotification) {
            if (LockSubService.locked) {
                builder.setContentTitle(getString(R.string.notification_title_olvid_is_locked))
                        .setSmallIcon(R.drawable.ic_lock_white);
            } else {
                builder.setContentTitle(getString(R.string.notification_title_olvid_is_unlocked))
                        .setSmallIcon(R.drawable.ic_lock_open);
            }
        } else if (SettingsActivity.useApplicationLockScreen() && LockSubService.locked) {
            builder.setContentTitle(getString(R.string.notification_title_olvid_is_running))
                    .setSmallIcon(R.drawable.ic_lock_white);
        } else {
            builder.setContentTitle(getString(R.string.notification_title_olvid_is_running))
                    .setSmallIcon(R.drawable.ic_lock_open);
        }



        ////////
        // Actions
        ////////
        Intent openIntent = new Intent(this, MainActivity.class);
        if (showLocationSharingNotification) {
            Pair<byte[], Long> bytesOwnedIdentityAndDiscussionId = LocationSharingSubService.getSingleSharingOwnedIdentityAndDiscussionId();
            if (bytesOwnedIdentityAndDiscussionId != null) {
                openIntent.setAction(MainActivity.FORWARD_ACTION);
                openIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
                openIntent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, bytesOwnedIdentityAndDiscussionId.second);
                openIntent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, bytesOwnedIdentityAndDiscussionId.first);
            }
        }
        PendingIntent openPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            openPendingIntent = PendingIntent.getActivity(App.getContext(), 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            openPendingIntent = PendingIntent.getActivity(App.getContext(), 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        builder.setContentIntent(openPendingIntent);


        if (showLockScreenNotification && !LockSubService.locked) {
            Intent lockIntent = new Intent(this, UnifiedForegroundService.class);
            lockIntent.setAction(LockSubService.LOCK_APP_ACTION);
            lockIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_LOCK);
            PendingIntent lockPendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                lockPendingIntent = PendingIntent.getService(App.getContext(), 0, lockIntent, PendingIntent.FLAG_IMMUTABLE);
            } else {
                lockPendingIntent = PendingIntent.getService(App.getContext(), 0, lockIntent, 0);
            }
            builder.addAction(R.drawable.ic_lock, getString(R.string.notification_action_lock), lockPendingIntent);
        }

        if (showWebClientNotification) {
            Intent disconnectIntent = new Intent(this, UnifiedForegroundService.class);
            disconnectIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_WEB_CLIENT);
            disconnectIntent.setAction(WebClientSubService.ACTION_DISCONNECT);
            PendingIntent disconnectPendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                disconnectPendingIntent = PendingIntent.getService(App.getContext(), 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE);
            } else {
                disconnectPendingIntent = PendingIntent.getService(App.getContext(), 0, disconnectIntent, 0);
            }
            builder.addAction(R.drawable.ic_webclient_disconnected, App.getContext().getString(R.string.notification_action_disconnect), disconnectPendingIntent);
        }

        if (showMessageSendingNotification) {
            Intent dismissIntent = new Intent(this, UnifiedForegroundService.class);
            dismissIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_MESSAGE_SENDING);
            dismissIntent.setAction(MessageSendingSubService.DISMISS_ACTION);
            PendingIntent dismissPendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dismissPendingIntent = PendingIntent.getService(App.getContext(), 0, dismissIntent, PendingIntent.FLAG_IMMUTABLE);
            } else {
                dismissPendingIntent = PendingIntent.getService(App.getContext(), 0, dismissIntent, 0);
            }
            builder.addAction(R.drawable.ic_close, App.getContext().getString(R.string.notification_action_dismiss), dismissPendingIntent);
        }

        if (showLocationSharingNotification) {
            Intent stopSharingIntent = new Intent(this, UnifiedForegroundService.class);
            stopSharingIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_LOCATION_SHARING);
            stopSharingIntent.setAction(LocationSharingSubService.STOP_SHARING_ACTION);
            PendingIntent stopSharingPendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                stopSharingPendingIntent = PendingIntent.getService(App.getContext(), 0, stopSharingIntent, PendingIntent.FLAG_IMMUTABLE);
            } else {
                stopSharingPendingIntent = PendingIntent.getService(App.getContext(), 0, stopSharingIntent, 0);
            }
            builder.addAction(R.drawable.ic_close, App.getContext().getString(R.string.notification_action_stop_sharing), stopSharingPendingIntent);
        }

        if (App.isVisible()) {
            stopForeground(true);
        }
        startForeground(SERVICE_ID, builder.build());
    }


    public class ServiceBinder extends Binder {
        public WebClientSubService getWebClientService() {
            if (webClientSubService == null) {
                if (AppSingleton.getBytesCurrentIdentity() == null) {
                    return null;
                }
                webClientSubService = new WebClientSubService(UnifiedForegroundService.this, AppSingleton.getBytesCurrentIdentity());
            }
            webClientSubService.isFragmentBound = true;
            return webClientSubService;
        }
    }




    public static class WebClientSubService {

        public static final String ACTION_CONNECT = "connect";
        public static final String ACTION_DISCONNECT = "disconnect";
        public static final String CONNECTION_DATA_INTENT_EXTRA = "connection_data";

        public static final String READY_FOR_BIND_BROADCAST_ACTION = "ready_for_bind_broadcast_action";

        private static boolean isRunning = false;

        @NonNull private final UnifiedForegroundService unifiedForegroundService;
        private byte[] bytesOwnedIdentity;
        private final MutableLiveData<Boolean> serviceClosing; //if fragment still bound, used to indicate service closing to close fragment too
        private final Context webClientContext;

        public boolean isFragmentBound = false;
        public boolean isAlreadyRunning = false;
        public String qrCodeData;

        private WebClientManager manager;

        WebClientSubService(@NonNull UnifiedForegroundService unifiedForegroundService, byte[] bytesOwnedIdentity) {
            isRunning = true;

            this.unifiedForegroundService = unifiedForegroundService;

            this.bytesOwnedIdentity = bytesOwnedIdentity;
            // used to notify fragment that service is closing
            this.serviceClosing = new MutableLiveData<>();

            //create new context with the same configuration than rest of the App to change language setting and return strings in the correct language
            this.webClientContext = App.getContext().createConfigurationContext(App.getContext().getResources().getConfiguration());
        }


        public Context getWebClientContext() {
            return webClientContext;
        }

        void onStartCommand(@NonNull String action, @NonNull Intent intent) {
            switch (action) {
                case ACTION_CONNECT: {
                    // qr code data is send in intent as an extra
                    String intentData = intent.getStringExtra(CONNECTION_DATA_INTENT_EXTRA);
                    if (intentData == null) {
                        Logger.e("No extra data, unable to start webclient service");
                        stopService();
                        break;
                    }
                    // start qrcode data parsing
                    if (!intentData.startsWith(Constants.QRCODE_URL_SCHEME)) {
                        Logger.e("Invalid QrCode uri, unable to start webclient");
                        stopServiceWithNotification(WebClientManager.StopServiceReason.INVALID_QR_CODE);
                        break;
                    }
                    String qrCodeData = intentData.substring(Constants.QRCODE_URL_SCHEME.length());
                    // handle if a webclient instance was already running
                    if (this.manager != null) {
                        Logger.w("A webclient connection is already running");
                        isAlreadyRunning = true;
                        this.qrCodeData = qrCodeData; //keep in memory if user wants to restart
                    } else {
                        // create new webclient manager instance
                        this.manager = new WebClientManager(this, qrCodeData);
                        unifiedForegroundService.stopOrRestartForegroundService();
                    }

                    Intent lockIntent = new Intent(READY_FOR_BIND_BROADCAST_ACTION);
                    lockIntent.setPackage(App.getContext().getPackageName());
                    LocalBroadcastManager.getInstance(unifiedForegroundService).sendBroadcast(lockIntent);
                    break;
                }
                case ACTION_DISCONNECT: {
                    Logger.d("Disconnecting webclient service");
                    // send bye colissimo
                    ColissimoOuterClass.Colissimo byeColissimo = ColissimoOuterClass.Colissimo.newBuilder().setType(ColissimoOuterClass.ColissimoType.BYE).build();
                    if (this.manager == null || !this.manager.sendColissimo(byeColissimo)) {
                        Logger.w("Unable to send bye colissimo");
                    } else {
                        Logger.d("Sent bye colissimo");
                    }
                    // stop service after a timer to be sure that bye message have time to be sent
                    new Handler(Looper.getMainLooper()).postDelayed(this::stopService, 500);
                    break;
                }
                default: {
                    Logger.e("Invalid intent action given");
                    break;
                }
            }
        }

        public void restartService() {
            if (this.manager != null && this.manager.getCurrentState() == WebClientManager.State.LISTENING) {
                //send bye message before restarting manager if current WC was connected
                //for example user has 2 tabs open, close first tab connection before starting second tab
                this.manager.sendColissimo(ColissimoOuterClass.Colissimo.newBuilder().setType(ColissimoOuterClass.ColissimoType.BYE).build());
            }
            if (this.manager != null) {
                this.manager.stop();
            }
            // update current identity before restarting WebClientManager
            if (AppSingleton.getBytesCurrentIdentity() != null) {
                this.bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
            }
            this.manager = new WebClientManager(this, this.qrCodeData);
            unifiedForegroundService.stopOrRestartForegroundService();
        }

        public boolean verifySasCode(@Nullable String sasCodeReceived) {
            if (this.manager == null) {
                Logger.e("Received sas code but no manager has been found, exiting");
                stopService();
                return false;
            }
            return this.manager.sasCodeVerification(sasCodeReceived);
        }

        public void onUnbind() {
            isFragmentBound = false;
            //if fragment unbinding in another state than listening, it means user has closed fragment by pressing back button, so stop service because protocol can't be completed
            if (this.manager != null && this.manager.getCurrentState() != WebClientManager.State.LISTENING) {
                stopService();
            }
        }

        void onTaskRemoved() {
            if (!SettingsActivity.keepWebclientAliveAfterClose()) {
                ColissimoOuterClass.Colissimo byeColissimo = ColissimoOuterClass.Colissimo.newBuilder().setType(ColissimoOuterClass.ColissimoType.BYE).build();
                if (!this.manager.sendColissimo(byeColissimo)) {
                    Logger.w("Unable to send bye colissimo");
                }
                stopService();
            }
        }


        public void stopService() {
            isRunning = false;
            if (unifiedForegroundService.webClientSubService == this) {
                unifiedForegroundService.webClientSubService = null;
            }

            if (this.manager != null && this.manager.getCurrentState() == WebClientManager.State.FINISHING) {
                Logger.i("Trying to stop webclient service while manager in FINISHING state, ignoring");
                return;
            }
            //if fragment is still bound, notify it to close
            if (isFragmentBound) {
                //alerting fragment that service is closing, fragment will unbind, which will call stopService() again
                this.serviceClosing.postValue(true);
                return;
            }

            if (this.manager != null) {
                final WebClientManager manager = this.manager;
                new Handler(Looper.getMainLooper()).post(manager::stop);
                this.manager = null;
            }

            // update notification
            unifiedForegroundService.stopOrRestartForegroundService();
        }

        // API for manager
        public void stopServiceWithNotification(WebClientManager.StopServiceReason stopServiceReason) {
            if (this.manager != null && this.manager.getCurrentState() == WebClientManager.State.FINISHING) {
                Logger.d("Trying to stop webclient (with notification) service while manager in FINISHING state, ignoring");
                return;
            }
            if (SettingsActivity.showWebclientErrorNotifications()) {
                String notificationTitle = null;
                switch (stopServiceReason) {
                    case PROTOCOL_ERROR:
                        notificationTitle = App.getContext().getString(R.string.webclient_error_protocol);
                        break;
                    case SESSION_CLOSED_REMOTELY:
                        notificationTitle = App.getContext().getString(R.string.webclient_sessions_closed_remotely);
                        break;
                    case CONNECTION_ERROR:
                        notificationTitle = App.getContext().getString(R.string.webclient_error_connection);
                        break;
                    case INTERNAL_ERROR:
                        notificationTitle = App.getContext().getString(R.string.webclient_error_internal);
                        break;
                    case INVALID_QR_CODE:
                        notificationTitle = App.getContext().getString(R.string.webclient_error_invalid_qrcode);
                        break;
                    case WEB_PERMISSION_DENIED:
                        App.openAppDialogSubscriptionRequired(bytesOwnedIdentity, EngineAPI.ApiKeyPermission.WEB_CLIENT);
                        break;
                }
                if (notificationTitle != null) {
                    AndroidNotificationManager.displayWebclientDisconnectedNotification(notificationTitle);
                }
            }
            stopService();
        }

        public byte[] getBytesOwnedIdentity() {
            return bytesOwnedIdentity;
        }

        public WebClientManager getManager() {
            return manager;
        }

        public boolean isAlreadyRunning() {
            return isAlreadyRunning;
        }

        @Nullable
        public WebClientManager.State getCurrentState() {
            if (this.manager != null) {
                return this.manager.getCurrentState();
            }
            return null;
        }

        public MutableLiveData<String> getSasCodeLiveData() {
            return this.manager.getSasCodeLiveData();
        }

        @Nullable
        public MutableLiveData<Boolean> getServiceClosingLiveData() {
            return this.serviceClosing;
        }
    }







    public static class LockSubService {
        public static final String UNLOCK_APP_ACTION = "unlock_app_action";
        public static final String LOCK_APP_ACTION = "lock_app_action";
        public static final String LOCK_SETTINGS_DEACTIVATED_ACTION = "lock_settings_deactivated_action";
        public static final String LOCK_SETTINGS_ACTIVATED_ACTION = "lock_settings_activated_action";
        public static final String CLOSE_HIDDEN_PROFILE_ACTION = "close_hidden_profile_action";

        public static final String CUSTOM_LOCK_SCREEN_MESSAGE_RESOURCE_ID_INTENT_EXTRA = "custom_lock_screen_message_resource_id";

        public static final String APP_LOCKED_BROADCAST_ACTION = "app_locked_broadcast_action";
        public static final String APP_UNLOCKED_BROADCAST_ACTION = "app_unlocked_broadcast_action";

        private static boolean locked = true;

        public static boolean isApplicationLocked() {
            return locked && SettingsActivity.useApplicationLockScreen();
        }

        @NonNull private final UnifiedForegroundService unifiedForegroundService;

        LockSubService(@NonNull UnifiedForegroundService unifiedForegroundService) {
            this.unifiedForegroundService = unifiedForegroundService;
            if (!SettingsActivity.useApplicationLockScreen()) {
                unlockApplication();
            }
        }

        void onTaskRemoved() {
            lockApplication(null);
        }

        void onDestroy() {
            locked = true;

            if (SettingsActivity.getHiddenProfileClosePolicy() == SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_SCREEN_LOCK) {
                AppSingleton.getInstance().closeHiddenProfile();
            }

            Intent lockIntent = new Intent(APP_LOCKED_BROADCAST_ACTION);
            lockIntent.setPackage(App.getContext().getPackageName());
            LocalBroadcastManager.getInstance(unifiedForegroundService).sendBroadcast(lockIntent);
        }

        void onStartCommand(@NonNull String action, @NonNull Intent intent) {
            switch (action) {
                case UNLOCK_APP_ACTION:
                    unlockApplication();
                    break;
                case LOCK_APP_ACTION: {
                    int customLockScreenMessageResourceId = intent.getIntExtra(CUSTOM_LOCK_SCREEN_MESSAGE_RESOURCE_ID_INTENT_EXTRA, -1);
                    if (customLockScreenMessageResourceId == -1) {
                        lockApplication(null);
                    } else {
                        lockApplication(customLockScreenMessageResourceId);
                    }
                    break;
                }
                case CLOSE_HIDDEN_PROFILE_ACTION: {
                    AppSingleton.getInstance().closeHiddenProfile();
                    break;
                }
                case LOCK_SETTINGS_DEACTIVATED_ACTION: {
                    unifiedForegroundService.stopOrRestartForegroundService();
                    break;
                }
                case LOCK_SETTINGS_ACTIVATED_ACTION: {
                    locked = false;
                    unifiedForegroundService.stopOrRestartForegroundService();
                    break;
                }
            }
        }


        private static void scheduleBackgroundLockIfNeeded(Context context) {
            if (!SettingsActivity.useApplicationLockScreen() || App.isVisible()) {
                return;
            }
            int timeout = SettingsActivity.getLockGraceTime();
            if (timeout == -1) {
                return;
            }
            Intent lockIntent = new Intent(LOCK_APP_ACTION, null, context, UnifiedForegroundService.class);
            lockIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_LOCK);
            if (timeout == 0) {
                try {
                    context.startService(lockIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }
            PendingIntent pendingLockIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pendingLockIntent = PendingIntent.getService(context, 0, lockIntent, PendingIntent.FLAG_MUTABLE);
            } else {
                pendingLockIntent = PendingIntent.getService(context, 0, lockIntent, 0);
            }
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingLockIntent);
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L * timeout, pendingLockIntent);
            }
        }


        private static void scheduleHiddenProfileCloseIfNeeded(Context context) {
            if (SettingsActivity.getHiddenProfileClosePolicy() != SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_BACKGROUND
                    || App.isVisible()
                    || !AppSingleton.isCurrentIdentityHidden()) {
                return;
            }
            int timeout = SettingsActivity.getHiddenProfileClosePolicyBackgroundGraceDelay();
            if (timeout <= 0) {
                AppSingleton.getInstance().closeHiddenProfile();
                return;
            }

            Intent closeHiddenProfileIntent = new Intent(CLOSE_HIDDEN_PROFILE_ACTION, null, context, UnifiedForegroundService.class);
            closeHiddenProfileIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_LOCK);
            PendingIntent pendingCloseHiddenProfileIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pendingCloseHiddenProfileIntent = PendingIntent.getService(context, 0, closeHiddenProfileIntent, PendingIntent.FLAG_MUTABLE);
            } else {
                pendingCloseHiddenProfileIntent = PendingIntent.getService(context, 0, closeHiddenProfileIntent, 0);
            }
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingCloseHiddenProfileIntent);
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L * timeout, pendingCloseHiddenProfileIntent);
            }
        }

        private void unlockApplication() {
            locked = false;
            unifiedForegroundService.stopOrRestartForegroundService();
            scheduleBackgroundLockIfNeeded(unifiedForegroundService);

            Intent lockIntent = new Intent(APP_UNLOCKED_BROADCAST_ACTION);
            lockIntent.setPackage(App.getContext().getPackageName());
            LocalBroadcastManager.getInstance(unifiedForegroundService).sendBroadcast(lockIntent);
        }

        private void lockApplication(Integer customLockScreenMessageResourceId) {
            locked = true;
            unifiedForegroundService.stopOrRestartForegroundService();

            if (SettingsActivity.getHiddenProfileClosePolicy() == SettingsActivity.HIDDEN_PROFILE_CLOSE_POLICY_SCREEN_LOCK) {
                AppSingleton.getInstance().closeHiddenProfile();
            }

            Intent lockIntent = new Intent(APP_LOCKED_BROADCAST_ACTION);
            if (customLockScreenMessageResourceId != null) {
                lockIntent.putExtra(LockableActivity.CUSTOM_LOCK_SCREEN_MESSAGE_RESOURCE_ID_INTENT_EXTRA, customLockScreenMessageResourceId);
            }
            lockIntent.setPackage(App.getContext().getPackageName());
            LocalBroadcastManager.getInstance(unifiedForegroundService).sendBroadcast(lockIntent);
        }
    }




    public static void processPostMessageOutput(ObvPostMessageOutput postMessageOutput) {
        try {
            HashSet<BytesKey> messageIdentifiers = new HashSet<>();
            for (byte[] messageIdentifier : postMessageOutput.getMessageIdentifierByContactIdentity().values()) {
                if (messageIdentifier != null) {
                    messageIdentifiers.add(new BytesKey(messageIdentifier));
                }
            }
            for (BytesKey messageIdentifier : messageIdentifiers) {
                Intent messageUploadedIntent = new Intent(App.getContext(), UnifiedForegroundService.class);
                messageUploadedIntent.setAction(MessageSendingSubService.MESSAGE_POSTED_ACTION);
                messageUploadedIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_MESSAGE_SENDING);
                messageUploadedIntent.putExtra(MessageSendingSubService.MESSAGE_IDENTIFIER_INTENT_EXTRA, messageIdentifier.bytes);
                App.getContext().startService(messageUploadedIntent);
            }
        } catch (Exception ignored) { }
    }

    public static void processUploadedMessageIdentifier(byte[] engineMessageIdentifier) {
        try {
            // notify the UnifiedForegroundService.MessageSendingSubService that the message was fully uploaded
            Intent messageUploadedIntent = new Intent(App.getContext(), UnifiedForegroundService.class);
            messageUploadedIntent.setAction(UnifiedForegroundService.MessageSendingSubService.MESSAGE_UPLOADED_ACTION);
            messageUploadedIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_MESSAGE_SENDING);
            messageUploadedIntent.putExtra(UnifiedForegroundService.MessageSendingSubService.MESSAGE_IDENTIFIER_INTENT_EXTRA, engineMessageIdentifier);
            App.getContext().startService(messageUploadedIntent);
        } catch (Exception ignored) { }
    }



    public static class MessageSendingSubService {
        public static final String MESSAGE_POSTED_ACTION = "message_posted";
        public static final String MESSAGE_UPLOADED_ACTION = "message_uploaded";
        public static final String DISMISS_ACTION = "dismiss";
        public static final String MESSAGE_IDENTIFIER_INTENT_EXTRA = "message_identifier"; // byte[]

        @NonNull private final UnifiedForegroundService unifiedForegroundService;
        @NonNull private final HashSet<BytesKey> currentlySendingMessages; // this set contains message identifiers that have been passed to the engine but are not yet fully uploaded
        @NonNull private final HashSet<BytesKey> finishedSendingMessages; // this set contains message identifiers that were fully sent but for which we were not yet notified they have been passed to the engine

        static boolean isSendingMessage = false;

        MessageSendingSubService(@NonNull UnifiedForegroundService unifiedForegroundService) {
            this.unifiedForegroundService = unifiedForegroundService;
            this.currentlySendingMessages = new HashSet<>();
            this.finishedSendingMessages = new HashSet<>();
        }

        synchronized void onStartCommand(@NonNull String action, @NonNull Intent intent) {
            switch (action) {
                case MESSAGE_POSTED_ACTION: {
                    byte[] messageIdentifier = intent.getByteArrayExtra(MESSAGE_IDENTIFIER_INTENT_EXTRA);
                    if (messageIdentifier != null) {
                        BytesKey key = new BytesKey(messageIdentifier);
                        if (!finishedSendingMessages.remove(key)) {
                            currentlySendingMessages.add(key);
                            if (!isSendingMessage) {
                                isSendingMessage = true;
                                unifiedForegroundService.stopOrRestartForegroundService();
                            }
                        }
                    }
                    break;
                }
                case MESSAGE_UPLOADED_ACTION: {
                    byte[] messageIdentifier = intent.getByteArrayExtra(MESSAGE_IDENTIFIER_INTENT_EXTRA);
                    if (messageIdentifier != null) {
                        BytesKey key = new BytesKey(messageIdentifier);
                        if (!currentlySendingMessages.remove(key)) {
                            finishedSendingMessages.add(key);
                        } else if (currentlySendingMessages.isEmpty()) {
                            // if we just remove the last sending message --> stop the foreground service
                            isSendingMessage = false;
                            // delay stop service by 200ms to allow liveData to be updated (for vibrateOnSend of widget)
                            new Handler(Looper.getMainLooper()).postDelayed(unifiedForegroundService::stopOrRestartForegroundService, 200);
                        }
                    }
                    break;
                }
                case DISMISS_ACTION: {
                    // clear all hashsets and dismiss notification
                    currentlySendingMessages.clear();
                    finishedSendingMessages.clear();

                    isSendingMessage = false;
                    unifiedForegroundService.stopOrRestartForegroundService();
                    break;
                }
            }
        }

        void onDestroy() {
            isSendingMessage = false;
            currentlySendingMessages.clear();
            finishedSendingMessages.clear();
        }
    }


    public static class LocationSharingSubService {
        static boolean isSharingLocation = false;

        public static final String START_SHARING_ACTION = "start_sharing";
        public static final String STOP_SHARING_IN_DISCUSSION_ACTION = "stop_sharing_in_discussion";
        public static final String STOP_SHARING_ACTION = "stop_sharing";
        public static final String SYNC_SHARING_MESSAGES_ACTION = "sync"; // check share location message in db and expires them or restart sharing
        public static final String DISCUSSION_ID_INTENT_EXTRA = "discussion_id"; // long
        public static final String SHARING_EXPIRATION_INTENT_EXTRA = "sharing_duration"; // long (optional)
        public static final String SHARING_INTERVAL_INTENT_EXTRA = "sharing_interval"; // long
        public static final String MESSAGE_ID_INTENT_EXTRA = "message_id"; // id of message to update with new location

        @NonNull
        private final static HashMap<Long, LocationSharer> locationSharersByDiscussionIds = new HashMap<>();
        private static UnifiedForegroundService unifiedForegroundService = null;

        private final LocationManager locationManager;

        LocationSharingSubService(@NonNull UnifiedForegroundService unifiedForegroundService) {
            LocationSharingSubService.unifiedForegroundService = unifiedForegroundService;
            this.locationManager = (LocationManager) App.getContext().getSystemService(Context.LOCATION_SERVICE);
        }

        synchronized void onStartCommand(@NonNull String action, @NonNull Intent intent) {
            switch (action) {
                case START_SHARING_ACTION: {
                    long discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1);
                    long messageId = intent.getLongExtra(MESSAGE_ID_INTENT_EXTRA, -1);
                    long shareExpiration = intent.getLongExtra(SHARING_EXPIRATION_INTENT_EXTRA, -1); // optionally filled (in ms)
                    long shareInterval = intent.getLongExtra(SHARING_INTERVAL_INTENT_EXTRA, -1); // (in ms)
                    if (discussionId != -1 && shareInterval != -1 && messageId != -1) {
                        App.runThread(() -> {
                            synchronized (locationSharersByDiscussionIds) {
                                if (locationSharersByDiscussionIds.containsKey(discussionId)
                                        && locationSharersByDiscussionIds.get(discussionId) != null) {
                                    Logger.i("LocationSharingSubService: already sharing location for this discussion: stop sharing for previous message");
                                    stopSharingLocationSync(discussionId);
                                }
                                Discussion discussion = AppDatabase.getInstance().discussionDao().getById(discussionId);
                                if (discussion != null) {
                                    // if expiration is not set use null value (endless sharing)
                                    Long nullableShareExpiration = shareExpiration == -1 ? null : shareExpiration;
                                    LocationSharer locationSharer = new LocationSharer(discussion.bytesOwnedIdentity, discussionId, nullableShareExpiration, shareInterval, locationManager, messageId);
                                    if (locationSharer.startSharing()) {
                                        locationSharersByDiscussionIds.put(discussionId, locationSharer);
                                        locationSharer.messageId = messageId;
                                    } else {
                                        Logger.w("LocationSharingSubService: impossible to start sharing location");
                                    }
                                }
                                if (!isSharingLocation && !locationSharersByDiscussionIds.isEmpty()) {
                                    isSharingLocation = true;
                                }
                            }
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (unifiedForegroundService != null) {
                                    unifiedForegroundService.stopOrRestartForegroundService();
                                }
                            });
                        });
                    } else {
                        Logger.e("LocationSharingSubService: START_SHARING_ACTION: invalid intent received");
                    }
                    break;
                }
                case STOP_SHARING_IN_DISCUSSION_ACTION: {
                    long discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1);
                    if (discussionId != -1) {
                        endSharingInDiscussion(discussionId, true);
                    } else {
                        Logger.e("LocationSharingSubService: STOP_SHARING_IN_DISCUSSION_ACTION: invalid intent received");
                    }
                    break;
                }
                case STOP_SHARING_ACTION: {
                    // stop all sharers and clear list
                    synchronized (locationSharersByDiscussionIds) {
                        List<Long> discussionIds = new ArrayList<>(locationSharersByDiscussionIds.keySet());
                        for (long discussionId : discussionIds) {
                            endSharingInDiscussion(discussionId, true);
                        }
                    }
                    break;
                }
                case SYNC_SHARING_MESSAGES_ACTION: {
                    List<Message> shareMessages = AppDatabase.getInstance().messageDao().getOutboundSharingLocationMessages();
                    for (Message message : shareMessages) {
                        Message.JsonLocation jsonLocation = message.getJsonLocation();

                        // check if sharing expired (and update message.locationType if needed)
                        if (message.isSharingExpired()) {
                            // post an update message to mark as finished locally and remotely
                            App.runThread(UpdateLocationMessageTask.createPostEndOfSharingMessageTask(message.discussionId, message.id));
                        } else {
                            // if sharing did not expire: restart it
                            Intent restartSharingIntent = new Intent();
                            restartSharingIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_LOCATION_SHARING);
                            restartSharingIntent.setAction(LocationSharingSubService.START_SHARING_ACTION);
                            restartSharingIntent.putExtra(LocationSharingSubService.DISCUSSION_ID_INTENT_EXTRA, message.discussionId);
                            restartSharingIntent.putExtra(LocationSharingSubService.MESSAGE_ID_INTENT_EXTRA, message.id);
                            restartSharingIntent.putExtra(LocationSharingSubService.SHARING_EXPIRATION_INTENT_EXTRA, jsonLocation.getSharingExpiration());
                            restartSharingIntent.putExtra(LocationSharingSubService.SHARING_INTERVAL_INTENT_EXTRA, jsonLocation.getSharingInterval());
                            onStartCommand(START_SHARING_ACTION, restartSharingIntent);
                        }
                    }
                    break;
                }
            }
        }

        // check if a discussion currently shared
        public static boolean isDiscussionSharingLocation(long discussionId) {
            if (isSharingLocation) {
                synchronized (locationSharersByDiscussionIds) {
                    LocationSharer locationSharer = locationSharersByDiscussionIds.get(discussionId);
                    if (locationSharer != null) {
                        if (locationSharer.shareExpiration != null && locationSharer.shareExpiration < System.currentTimeMillis()) {
                            endSharingInDiscussion(discussionId, true);
                        } else {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        // if sharing location in exactly one discussion, return the discussion id of this discussion
        public static Pair<byte[], Long> getSingleSharingOwnedIdentityAndDiscussionId() {
            if (isSharingLocation) {
                synchronized (locationSharersByDiscussionIds) {
                    if (locationSharersByDiscussionIds.size() == 1) {
                        for (Map.Entry<Long, LocationSharer> entry : locationSharersByDiscussionIds.entrySet()) {
                            return new Pair<>(entry.getValue().bytesOwnedIdentity, entry.getKey());
                        }
                    }
                }
            }
            return null;
        }

        public static void stopSharingLocation(Context context, long discussionId) {
            try {
                Intent stopSharingLocationIntent = new Intent(context, UnifiedForegroundService.class);
                stopSharingLocationIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_LOCATION_SHARING);
                stopSharingLocationIntent.setAction(UnifiedForegroundService.LocationSharingSubService.STOP_SHARING_IN_DISCUSSION_ACTION);
                stopSharingLocationIntent.putExtra(UnifiedForegroundService.LocationSharingSubService.DISCUSSION_ID_INTENT_EXTRA, discussionId);
                context.startService(stopSharingLocationIntent);
            } catch (Exception ignored) { }
        }

        // used when deleting sharing message: need to update message before deleting it
        public static void stopSharingLocationSync(long discussionId) {
            endSharingInDiscussion(discussionId, false);
        }

        // stop sharing location in a discussion
        private static void endSharingInDiscussion(long discussionId, boolean runTaskOnNewThread) {
            synchronized (locationSharersByDiscussionIds) {
                if (!locationSharersByDiscussionIds.containsKey(discussionId)) {
                    Logger.e("LocationSharingSubService: trying to stop sharing for a non sharing discussion");
                    // Try to stop sharing even if not marked as sharing in service
                    App.runThread(() -> {
                        List<Message> currentlySharingMessages = AppDatabase.getInstance().messageDao().getCurrentlySharingOutboundLocationMessagesInDiscussion(discussionId);
                        if (currentlySharingMessages != null) {
                            for (Message message : currentlySharingMessages) {
                                UpdateLocationMessageTask.createPostEndOfSharingMessageTask(discussionId, message.id).run();
                            }
                        }
                    });
                    return;
                }

                LocationSharer locationSharer = locationSharersByDiscussionIds.remove(discussionId);
                if (locationSharer != null) {
                    // send message to notify sharing end
                    UpdateLocationMessageTask task = UpdateLocationMessageTask.createPostEndOfSharingMessageTask(discussionId, locationSharer.messageId);
                    if (runTaskOnNewThread) {
                        App.runThread(task);
                    } else {
                        task.run();
                    }
                    // stop location manager updates
                    if (ActivityCompat.checkSelfPermission(App.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(App.getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        LocationManagerCompat.removeUpdates(locationSharer.locationManager, locationSharer);
                    }
                }

                // see if there are still sharing in progress in other discussions
                if (isSharingLocation && locationSharersByDiscussionIds.isEmpty()) {
                    isSharingLocation = false;
                }
            }
            if (unifiedForegroundService != null) {
                unifiedForegroundService.stopOrRestartForegroundService();
            }
        }

        void onDestroy() {
            // stop all sharers and clear list
            List<Long> discussionIds;
            synchronized (locationSharersByDiscussionIds) {
                discussionIds = new ArrayList<>(locationSharersByDiscussionIds.keySet());
            }

            for (Long discussionId : discussionIds) {
                endSharingInDiscussion(discussionId, true);
            }
        }

        public static class LocationSharer implements LocationListenerCompat {
            protected final byte[] bytesOwnedIdentity;
            protected final long discussionId;
            protected final @Nullable Long shareExpiration;
            protected final long shareInterval;
            protected final LocationManager locationManager;
            private final Executor executor;
            private final LocationRequestCompat locationRequest;

            protected long messageId;

            LocationSharer(byte[] bytesOwnedIdentity, long discussionId, @Nullable Long shareExpiration, long shareInterval, @NotNull LocationManager locationManager, long messageId) {
                this.bytesOwnedIdentity = bytesOwnedIdentity;
                this.discussionId = discussionId;
                this.locationManager = locationManager;
                this.shareExpiration = shareExpiration;
                this.shareInterval = shareInterval;
                this.messageId = messageId;

                // get executor depending on android version
                executor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? App.getContext().getMainExecutor() : new HandlerExecutor(Looper.getMainLooper());

                // location request
                locationRequest = new LocationRequestCompat.Builder(0)
                        .setIntervalMillis(shareInterval)
                        .setMinUpdateIntervalMillis(shareInterval)
                        .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
                        .build();
            }

            public boolean startSharing() {
                String provider = null;
                try {
                    provider = locationManager.getBestProvider(new Criteria(), true);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (provider != null) {
                    if (ActivityCompat.checkSelfPermission(App.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(App.getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        LocationManagerCompat.requestLocationUpdates(locationManager, provider, locationRequest, executor, this);
                    } else {
                        Logger.d("LocationSharingSubService: Location permission disabled");
                    }
                } else {
                    Logger.d("LocationSharingSubService: no provider found");
                }
                return true;
            }

            @Override
            public void onLocationChanged(@NonNull Location location) {
                try {
                    // check sharing expiration lazily
                    if (shareExpiration != null && shareExpiration < System.currentTimeMillis()) {
                        endSharingInDiscussion(discussionId, true);
                        return;
                    }

                    UpdateLocationMessageTask task = UpdateLocationMessageTask.createPostSharingLocationUpdateMessage(discussionId, messageId, location);
                    App.runThread(task);
                }
                catch (Exception e) {
                    Logger.d("LocationSharingSubService: exception in onLocationChanged");
                    e.printStackTrace();
                }
            }

            // if location is disabled and then re-enabled when app is in background location updates won't arrive until app is open to foreground
            @Override
            public void onProviderEnabled(@NonNull String provider) {
                LocationListenerCompat.super.onProviderEnabled(provider);
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                LocationListenerCompat.super.onProviderDisabled(provider);
            }
        }
    }
}
