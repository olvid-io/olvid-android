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

package io.olvid.messenger.services;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.DummyActivity;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.customClasses.LockableActivity;
import io.olvid.messenger.customClasses.PreviewUtils;
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

    final NoExceptionSingleThreadExecutor executor = new NoExceptionSingleThreadExecutor("UnifiedForegroundService-Executor");

    private final ServiceBinder serviceBinder = new ServiceBinder();

    private LockSubService lockSubService = null;
    private WebClientSubService webClientSubService = null;


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

    private static void connectOrDisconnectWebSocket() {
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
        executor.shutdownNow();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        executor.execute(() -> {
            ComponentName intentComponent = rootIntent.getComponent();
            if (intentComponent != null) {
                if (WebrtcCallActivity.class.getName().equals(intentComponent.getClassName())
                        || WebrtcIncomingCallActivity.class.getName().equals(intentComponent.getClassName())) {
                    return;
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

            Intent intent = new Intent(this, DummyActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
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

    public static boolean willUnifiedForegroundServiceStartForegroun() {
        boolean showWebClientNotification = WebClientSubService.isRunning;
        boolean showLockScreenNotification = SettingsActivity.useApplicationLockScreen() && (!LockSubService.locked || SettingsActivity.keepLockServiceOpen());
        boolean showPermanentNotification = SettingsActivity.usePermanentWebSocket() || SettingsActivity.usePermanentForegroundService();

        return showLockScreenNotification || showWebClientNotification || showPermanentNotification;
    }

    private void stopOrRestartForegroundService() {
        boolean showWebClientNotification = webClientSubService != null && WebClientSubService.isRunning;
        boolean showLockScreenNotification = SettingsActivity.useApplicationLockScreen() && (!LockSubService.locked || SettingsActivity.keepLockServiceOpen());
        boolean showPermanentNotification = SettingsActivity.usePermanentWebSocket() || SettingsActivity.usePermanentForegroundService();

        if (!showLockScreenNotification && !showWebClientNotification && !showPermanentNotification) {
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
        PendingIntent openPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            openPendingIntent = PendingIntent.getActivity(App.getContext(), 0, openIntent, PendingIntent.FLAG_IMMUTABLE);
        } else {
            openPendingIntent = PendingIntent.getActivity(App.getContext(), 0, openIntent, 0);
        }
        builder.setContentIntent(openPendingIntent);


        if (showLockScreenNotification && !LockSubService.locked) {
            Intent lockIntent = new Intent(this, UnifiedForegroundService.class);
            lockIntent.setAction(LockSubService.LOCK_APP_ACTION);
            lockIntent.putExtra(SUB_SERVICE_INTENT_EXTRA, SUB_SERVICE_LOCK);
            PendingIntent lockPendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                disconnectPendingIntent = PendingIntent.getService(App.getContext(), 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE);
            } else {
                disconnectPendingIntent = PendingIntent.getService(App.getContext(), 0, disconnectIntent, 0);
            }
            builder.addAction(R.drawable.ic_webclient_disconnected, App.getContext().getString(R.string.notification_action_disconnect), disconnectPendingIntent);
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

        private final UnifiedForegroundService unifiedForegroundService;
        private final byte[] bytesOwnedIdentity;
        private final MutableLiveData<Boolean> serviceClosing; //if fragment still bound, used to indicate service closing to close fragment too
        private final Context webClientContext;

        public boolean isFragmentBound = false;
        public boolean isAlreadyRunning = false;
        public String qrCodeData;

        private WebClientManager manager;

        WebClientSubService(UnifiedForegroundService unifiedForegroundService, byte[] bytesOwnedIdentity) {
            isRunning = true;

            this.unifiedForegroundService = unifiedForegroundService;

            this.bytesOwnedIdentity = bytesOwnedIdentity;
            // used to notify fragment that service is closing
            this.serviceClosing = new MutableLiveData<>();

            //create new context with the same configuration than rest of the App to change language setting and return strings in the correct language
            this.webClientContext = App.getContext().createConfigurationContext(App.getContext().getResources().getConfiguration());
        }


        public Context getWebClientContext(){
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
            if (SettingsActivity.showErrorNotifications()) {
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

        private final UnifiedForegroundService unifiedForegroundService;

        LockSubService(UnifiedForegroundService unifiedForegroundService) {
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
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L*timeout, pendingLockIntent);
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
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L*timeout, pendingCloseHiddenProfileIntent);
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
}
