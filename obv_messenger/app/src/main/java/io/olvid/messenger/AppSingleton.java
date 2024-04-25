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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.net.ssl.SSLContext;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvPushNotificationType;
import io.olvid.engine.engine.types.RegisterApiKeyResult;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.messenger.billing.BillingUtils;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.CustomSSLSocketFactory;
import io.olvid.messenger.customClasses.DatabaseKey;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Group2PendingMember;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.jsons.JsonExpiration;
import io.olvid.messenger.databases.tasks.ContactDisplayNameFormatChangedTask;
import io.olvid.messenger.databases.tasks.OwnedDevicesSynchronisationWithEngineTask;
import io.olvid.messenger.databases.tasks.backup.RestoreAppDataFromBackupTask;
import io.olvid.messenger.databases.tasks.migration.SetContactsAndPendingMembersFirstNamesTask;
import io.olvid.messenger.discussion.compose.ComposeMessageFragment;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.services.BackupCloudProviderService;
import io.olvid.messenger.services.MDMConfigurationSingleton;
import io.olvid.messenger.services.PeriodicTasksScheduler;
import io.olvid.messenger.settings.SettingsActivity;

public class AppSingleton {
    private static final String LATEST_IDENTITY_SHARED_PREFERENCE_KEY = "last_identity";
    private static final String FIREBASE_TOKEN_SHARED_PREFERENCE_KEY = "firefase_token";
    private static final String LAST_BUILD_EXECUTED_PREFERENCE_KEY = "last_build";
    private static final String LAST_ANDROID_SDK_VERSION_EXECUTED_PREFERENCE_KEY = "last_android_sdk_version";
    private static final String LAST_FTS_GLOBAL_SEARCH_VERSION_PREFERENCE_KEY = "last_fts_global_search_version";


    public static final String FYLE_DIRECTORY = "fyles";
    public static final String DISCUSSION_BACKGROUNDS_DIRECTORY = "discussion_backgrounds";
    public static final String CUSTOM_PHOTOS_DIRECTORY = "custom_photos";

    public static final String DEFAULT_DEVICE_DISPLAY_NAME = Build.BRAND + " " + Build.MODEL;

    private static final AppSingleton instance = new AppSingleton();

    private final Engine engine;
    private final CustomSSLSocketFactory sslSocketFactory;
    @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
    private final EngineNotificationProcessor engineNotificationProcessor;
    @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
    private final EngineNotificationProcessorForContacts engineNotificationProcessorForContacts;
    @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
    private final EngineNotificationProcessorForGroups engineNotificationProcessorForGroups;
    @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
    private final EngineNotificationProcessorForGroupsV2 engineNotificationProcessorForGroupsV2;
    @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
    private final EngineNotificationProcessorForMessages engineNotificationProcessorForMessages;
    private final ObjectMapper jsonObjectMapper;
    private final AppDatabase db;
    private final Observer<List<OwnedIdentity>> firstIdentitySelector;
    private final MutableLiveData<Integer> websocketConnectivityStateLiveData;

    @NonNull private final SharedPreferences sharedPreferences;

    private AppSingleton() {
        this.bytesCurrentIdentity = null;
        this.jsonObjectMapper = new ObjectMapper();
        this.jsonObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_app), Context.MODE_PRIVATE);

        int lastBuildExecuted = sharedPreferences.getInt(LAST_BUILD_EXECUTED_PREFERENCE_KEY, BuildConfig.VERSION_CODE);
        int lastAndroidSdkVersionExecuted = sharedPreferences.getInt(LAST_ANDROID_SDK_VERSION_EXECUTED_PREFERENCE_KEY, 0);
        int lastFtsGlobalSearchVersion = sharedPreferences.getInt(LAST_FTS_GLOBAL_SEARCH_VERSION_PREFERENCE_KEY, 0);

        if (lastBuildExecuted != 0 && lastBuildExecuted < 89) {
            runNoBackupFolderMigration();
        }
        if (lastBuildExecuted != 0 && lastBuildExecuted < 120) {
            SettingsActivity.setContactDisplayNameFormat(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY);
        }

        if (lastBuildExecuted != 0 && lastBuildExecuted < 136) {
            // clear deprecated scaled_turn preference
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("pref_key_scaled_turn");
            editor.apply();
        }

        if (lastBuildExecuted != 0 && lastBuildExecuted < 153) {
            // if the user has customized attach icon order, add the emoji icon so they see it
            List<Integer> icons = SettingsActivity.getComposeMessageIconPreferredOrder();
            if (icons != null && !icons.contains(ComposeMessageFragment.ICON_EMOJI)) {
                icons.add(0, ComposeMessageFragment.ICON_EMOJI);
                SettingsActivity.setComposeMessageIconPreferredOrder(icons);
            }
        }

        if (lastBuildExecuted != 0 && lastBuildExecuted < 205) {
            // we fixed the rename task, run it again to fix all user display names
            App.runThread(new ContactDisplayNameFormatChangedTask());
        }

        if (lastBuildExecuted != 0 && lastBuildExecuted < 219) {
            // clear removed dialog hide preference
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("user_dialog_hide_battery_optimization");
            editor.remove("user_dialog_hide_background_restricted");
            editor.remove("user_dialog_hide_alarm_scheduling");
            editor.remove("user_dialog_hide_allow_notifications");
            editor.remove("user_dialog_hide_full_screen_notification");
            editor.remove("pref_key_last_backup_reminder_timestamp");
            editor.apply();
        }

        if (lastBuildExecuted != 0 && lastBuildExecuted < 226) {
            // we added a first name to contacts and group v2 pending members
            App.runThread(new SetContactsAndPendingMembersFirstNamesTask());
        }

        if (lastBuildExecuted != 0 && lastBuildExecuted < 229) {
            // if the user has customized attach icon order, add the send location icon so they see it
            List<Integer> icons = SettingsActivity.getComposeMessageIconPreferredOrder();
            if (icons != null && !icons.contains(ComposeMessageFragment.ICON_SEND_LOCATION)) {
                icons.add(0, ComposeMessageFragment.ICON_SEND_LOCATION);
                SettingsActivity.setComposeMessageIconPreferredOrder(icons);
            }
        }

        {
            // generate App directories
            File fylesDirectory = new File(App.getContext().getNoBackupFilesDir(), FYLE_DIRECTORY);
            //noinspection ResultOfMethodCallIgnored
            fylesDirectory.mkdirs();
            File discussionBackgroundsDirectory = new File(App.getContext().getNoBackupFilesDir(), DISCUSSION_BACKGROUNDS_DIRECTORY);
            //noinspection ResultOfMethodCallIgnored
            discussionBackgroundsDirectory.mkdirs();
            File customPhotosDirectory = new File(App.getContext().getNoBackupFilesDir(), CUSTOM_PHOTOS_DIRECTORY);
            //noinspection ResultOfMethodCallIgnored
            customPhotosDirectory.mkdirs();
        }

        SSLContext sslContext = null;
        if (BuildConfig.ENABLE_SSL_HANDSHAKE_VERIFICATION) {
            try {
                sslContext = SSLContext.getDefault();
            } catch (Exception e) {
                Log.e("Logger", "Failed to initialize custom SSLSocketFactory");
                e.printStackTrace();
            }
        }
        if (sslContext != null) {
            this.sslSocketFactory = new CustomSSLSocketFactory(sslContext.getSocketFactory());
        } else {
            this.sslSocketFactory = null;
        }

        // set current App capabilities
        //noinspection ArraysAsListWithZeroOrOneArgument
        ObvCapability.currentCapabilities.addAll(Arrays.asList(
                // add App capabilities here
                ObvCapability.WEBRTC_CONTINUOUS_ICE
        ));


        // initialize Engine
        try {
            System.loadLibrary("crypto_1_1");
            this.engine = new Engine(App.getContext().getNoBackupFilesDir(), new AppBackupAndSyncDelegate(), DatabaseKey.get(DatabaseKey.ENGINE_DATABASE_SECRET), this.sslSocketFactory,
                    new Logger.LogOutputter() {
                        @Override
                        public void d(String s, String s1) {
                            Log.d(s, s1);
                        }

                        @Override
                        public void i(String s, String s1) {
                            Log.i(s, s1);
                        }

                        @Override
                        public void w(String s, String s1) {
                            Log.w(s, s1);
                        }

                        @Override
                        public void e(String s, String s1) {
                            Log.e(s, s1);
                        }
                    },
                    SettingsActivity.useDebugLogLevel() ? Logger.DEBUG : BuildConfig.LOG_LEVEL);
        } catch (Exception e) {
            Log.e("Engine", "Error starting obv engine!");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        this.engineNotificationProcessor = new EngineNotificationProcessor(this.engine);
        this.engineNotificationProcessorForContacts = new EngineNotificationProcessorForContacts(this.engine);
        this.engineNotificationProcessorForGroups = new EngineNotificationProcessorForGroups(this.engine);
        this.engineNotificationProcessorForGroupsV2 = new EngineNotificationProcessorForGroupsV2(this.engine);
        this.engineNotificationProcessorForMessages = new EngineNotificationProcessorForMessages(this.engine);
        this.websocketConnectivityStateLiveData = new MutableLiveData<>(0);
        this.engine.startSendingNotifications();

        db = AppDatabase.getInstance();
        bytesCurrentIdentityLiveData = new MutableLiveData<>();
        currentIdentityLiveData = Transformations.switchMap(bytesCurrentIdentityLiveData, (byte[] bytesOwnedIdentity) -> {
            if (bytesOwnedIdentity != null) {
                return AppDatabase.getInstance().ownedIdentityDao().getLiveData(bytesOwnedIdentity);
            }
            return new MutableLiveData<>(null);
        });
        availableIdentities = db.ownedIdentityDao().getAllNotHiddenLiveData();
        aNonHiddenIdentityHasCallsPermission = new MediatorLiveData<>();
        Callable<Boolean> computeOtherIdentityHasCallsPermission = () -> {
            List<OwnedIdentity> ownedIdentities = availableIdentities.getValue();
            if (ownedIdentities != null) {
                for (OwnedIdentity ownedIdentity : ownedIdentities) {
                    if (!Arrays.equals(ownedIdentity.bytesOwnedIdentity, bytesCurrentIdentityLiveData.getValue())
                            && ownedIdentity.getApiKeyPermissions().contains(EngineAPI.ApiKeyPermission.CALL)) {
                        return true;
                    }
                }
            }
            return false;
        };
        aNonHiddenIdentityHasCallsPermission.addSource(availableIdentities, ownedIdentities -> {
            try {
                aNonHiddenIdentityHasCallsPermission.setValue(computeOtherIdentityHasCallsPermission.call());
            } catch (Exception ignored) {}
        });
        aNonHiddenIdentityHasCallsPermission.addSource(bytesCurrentIdentityLiveData, ownedIdentities -> {
            try {
                aNonHiddenIdentityHasCallsPermission.setValue(computeOtherIdentityHasCallsPermission.call());
            } catch (Exception ignored) {}
        });

        // add a dummy observer to this liveData, so that it is indeed computed
        new Handler(Looper.getMainLooper()).post(() -> aNonHiddenIdentityHasCallsPermission.observeForever((Boolean canCall) -> Logger.d("aNonHiddenIdentityHasCallsPermission changed " + canCall)));
        if (this.sslSocketFactory != null) {
            this.sslSocketFactory.loadKnownCertificates();
        }

        // this observer is used in case there is no latest identity, or the latest identity cannot be found
        firstIdentitySelector = new Observer<>() {
            @Override
            public void onChanged(List<OwnedIdentity> ownedIdentities) {
                if (ownedIdentities != null) {
                    if (!ownedIdentities.isEmpty()) {
                        selectIdentity(ownedIdentities.get(0).bytesOwnedIdentity, null);
                    } else {
                        selectIdentity(null, null);
                    }
                    availableIdentities.removeObserver(this);
                }
            }
        };
        selectLatestOpenedIdentity();

        this.contactNamesCache = new MutableLiveData<>();
        this.contactHuesCache = new MutableLiveData<>();
        this.contactPhotoUrlsCache = new MutableLiveData<>();
        this.contactKeycloakManagedCache = new MutableLiveData<>();
        this.contactInactiveCache = new MutableLiveData<>();
        this.contactOneToOneCache = new MutableLiveData<>();
        this.contactTrustLevelCache = new MutableLiveData<>();

        Observer<OwnedIdentity> currentIdentityObserverForNameCache = new Observer<>() {
            byte[] bytesPreviousOwnedIdentity = null;

            @Override
            public void onChanged(OwnedIdentity ownedIdentity) {
                if (ownedIdentity == null) {
                    bytesPreviousOwnedIdentity = null;
                    App.runThread(() -> instance.reloadCachedDisplayNamesAndHues(null));
                } else if (!Arrays.equals(bytesPreviousOwnedIdentity, ownedIdentity.bytesOwnedIdentity)) {
                    bytesPreviousOwnedIdentity = ownedIdentity.bytesOwnedIdentity;
                    App.runThread(() -> instance.reloadCachedDisplayNamesAndHues(ownedIdentity));
                }
            }
        };

        this.deactivatedIdentities = new HashSet<>();

        new Handler(Looper.getMainLooper()).post(() -> currentIdentityLiveData.observeForever(currentIdentityObserverForNameCache));

        if (lastBuildExecuted != BuildConfig.VERSION_CODE || lastAndroidSdkVersionExecuted != Build.VERSION.SDK_INT) {
            App.runThread(() -> {
                AndroidNotificationManager.createChannels(lastBuildExecuted);
                runBuildUpgrade(lastBuildExecuted, lastAndroidSdkVersionExecuted);
            });
        }

        if (lastFtsGlobalSearchVersion != AppDatabase.DB_FTS_GLOBAL_SEARCH_VERSION) {
            App.runThread(() -> {
                runFtsGlobalSearchRebuild(lastFtsGlobalSearchVersion);
            });
        }

        App.runThread(() -> {
            try {
                // get all ownedIdentities and give relevant ones to KeycloakManager
                ObvIdentity[] ownedIdentities = this.engine.getOwnedIdentities();
                for (ObvIdentity ownedIdentity : ownedIdentities) {
                    if (ownedIdentity.isKeycloakManaged() && ownedIdentity.isActive()) {
                        ObvKeycloakState keycloakState = this.engine.getOwnedIdentityKeycloakState(ownedIdentity.getBytesIdentity());
                        if (keycloakState != null) {
                            KeycloakManager.getInstance().registerKeycloakManagedIdentity(
                                    ownedIdentity,
                                    keycloakState.keycloakServer,
                                    keycloakState.clientId,
                                    keycloakState.clientSecret,
                                    keycloakState.jwks,
                                    keycloakState.signatureKey,
                                    keycloakState.serializedAuthState,
                                    keycloakState.ownApiKey,
                                    keycloakState.latestRevocationListTimestamp,
                                    keycloakState.latestGroupUpdateTimestamp,
                                    false
                            );
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void setCreatedGroupEphemeralSettings(JsonExpiration jsonExpiration) {
        if (instance.engineNotificationProcessorForGroupsV2 != null) {
            instance.engineNotificationProcessorForGroupsV2.setCreatedGroupEphemeralSettings(jsonExpiration);
        }
    }

    public static LiveData<Integer> getWebsocketConnectivityStateLiveData() {
        return instance.websocketConnectivityStateLiveData;
    }

    public static void setWebsocketConnectivityState(int state) {
        instance.websocketConnectivityStateLiveData.postValue(state);
    }

    // region Owned identity list and current management

    private final NoExceptionSingleThreadExecutor executor = new NoExceptionSingleThreadExecutor("AppSingleton-currentIdentitySelector-Executor");
    @Nullable private byte[] bytesCurrentIdentity;
    private boolean currentIdentityIsHidden;
    @NonNull private final MutableLiveData<byte[]> bytesCurrentIdentityLiveData;
    @NonNull private final LiveData<OwnedIdentity> currentIdentityLiveData;
    @NonNull private final LiveData<List<OwnedIdentity>> availableIdentities;
    @NonNull private final MediatorLiveData<Boolean> aNonHiddenIdentityHasCallsPermission;

    public interface IdentitySelectedCallback {

        void onIdentitySelected(@Nullable OwnedIdentity ownedIdentity);
    }

    public void selectNextIdentity(boolean selectPreviousInstead) {
        executor.execute(() -> {
            List<OwnedIdentity> ownedIdentities = AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenSortedSync();
            int pos = -1;
            for (int i = 0; i < ownedIdentities.size(); i++) {
                if (Arrays.equals(ownedIdentities.get(i).bytesOwnedIdentity, bytesCurrentIdentity)) {
                    pos = i;
                    break;
                }
            }
            int posToSelect;
            if (selectPreviousInstead) {
                posToSelect = pos - 1;
            } else {
                posToSelect = pos + 1;
            }
            if (posToSelect < 0) {
                posToSelect = ownedIdentities.size() - 1;
            } else {
                posToSelect = posToSelect % ownedIdentities.size();
            }
            if (posToSelect != pos) {
                selectIdentity(ownedIdentities.get(posToSelect).bytesOwnedIdentity, null);
            }
        });
    }

    public void selectIdentity(byte[] bytesIdentity, IdentitySelectedCallback callback) {
        executor.execute(() -> {
            AndroidNotificationManager.clearHiddenIdentityNotifications();
            if (bytesIdentity != null) {
                OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(bytesIdentity);
                if (ownedIdentity != null) {
                    this.bytesCurrentIdentity = bytesIdentity;
                    this.bytesCurrentIdentityLiveData.postValue(bytesIdentity);

                    if (ownedIdentity.isHidden()) {
                        currentIdentityIsHidden = true;
                    } else {
                        currentIdentityIsHidden = false;
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(LATEST_IDENTITY_SHARED_PREFERENCE_KEY, Logger.toHexString(bytesIdentity));
                        editor.apply();
                    }

                    if (callback != null) {
                        callback.onIdentitySelected(ownedIdentity);
                    }
                    // show any identity specific app dialog immediately
                    App.showAppDialogsForSelectedIdentity(bytesIdentity);
                    KeycloakManager.showAuthenticationRequiredNotificationForSelectedIdentityIfNeeded(bytesIdentity);
                    return;
                }
            }

            this.bytesCurrentIdentity = null;
            this.bytesCurrentIdentityLiveData.postValue(null);
            if (callback != null) {
                callback.onIdentitySelected(null);
            }
        });
    }

    @Nullable
    public static byte[] getBytesCurrentIdentity() {
        return instance.bytesCurrentIdentity;
    }

    @NonNull
    public static LiveData<OwnedIdentity> getCurrentIdentityLiveData() {
        return instance.currentIdentityLiveData;
    }

    public static boolean getOtherProfileHasCallsPermission() {
        Boolean canCall = instance.aNonHiddenIdentityHasCallsPermission.getValue();
        return canCall != null && canCall;
    }

    private void selectLatestOpenedIdentity() {
        String lastIdentityHexString = sharedPreferences.getString(LATEST_IDENTITY_SHARED_PREFERENCE_KEY, null);
        if (lastIdentityHexString != null) {
            selectIdentity(Logger.fromHexString(lastIdentityHexString), ownedIdentity -> {
                if (ownedIdentity == null) {
                    new Handler(Looper.getMainLooper()).post(() -> availableIdentities.observeForever(firstIdentitySelector));
                }
            });
        } else {
            new Handler(Looper.getMainLooper()).post(() -> availableIdentities.observeForever(firstIdentitySelector));
        }
    }

    public void ownedIdentityBecameHidden(@NonNull byte[] bytesOwnedIdentity) {
        if (Arrays.equals(bytesCurrentIdentity, bytesOwnedIdentity)) {
            currentIdentityIsHidden = true;
        }
        String latestIdentityHex = sharedPreferences.getString(LATEST_IDENTITY_SHARED_PREFERENCE_KEY, null);
        if (Logger.toHexString(bytesOwnedIdentity).equals(latestIdentityHex)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(LATEST_IDENTITY_SHARED_PREFERENCE_KEY);
            editor.apply();
        }
    }

    public void ownedIdentityWasDeleted(byte[] bytesOwnedIdentity) {
        if (Arrays.equals(bytesCurrentIdentity, bytesOwnedIdentity)) {
            new Handler(Looper.getMainLooper()).post(() -> availableIdentities.observeForever(firstIdentitySelector));
        }
        String latestIdentityHex = sharedPreferences.getString(LATEST_IDENTITY_SHARED_PREFERENCE_KEY, null);
        if (Logger.toHexString(bytesOwnedIdentity).equals(latestIdentityHex)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(LATEST_IDENTITY_SHARED_PREFERENCE_KEY);
            editor.apply();
        }
    }

    public static boolean isCurrentIdentityHidden() {
        return instance.currentIdentityIsHidden;
    }

    public void closeHiddenProfile() {
        executor.execute(() -> {
            if (currentIdentityIsHidden && App.shouldActivitiesBeKilledOnLockAndHiddenProfileClosedOnBackground()) {
                selectLatestOpenedIdentity();

                Intent closeOpenActivitiesIntent = new Intent(App.CURRENT_HIDDEN_PROFILE_CLOSED_BROADCAST_ACTION);
                closeOpenActivitiesIntent.setPackage(App.getContext().getPackageName());
                LocalBroadcastManager.getInstance(App.getContext()).sendBroadcast(closeOpenActivitiesIntent);
            }
        });
    }

    // endregion

    // region Identity creation and restore (and active status)

    @NonNull private final HashSet<BytesKey> deactivatedIdentities;

    public static void markIdentityActive(@NonNull OwnedIdentity ownedIdentity, boolean active) {
        synchronized (instance) {
            if (active) {
                if (instance.deactivatedIdentities.remove(new BytesKey(ownedIdentity.bytesOwnedIdentity))) {
                    // only called if the HashSet indeed contained the identity
                    if (Arrays.equals(ownedIdentity.bytesOwnedIdentity, getInstance().bytesCurrentIdentity)) {
                        App.openAppDialogIdentityActivated(ownedIdentity);

                        if (ownedIdentity.keycloakManaged) {
                            try {
                                ObvIdentity obvIdentity = getEngine().getOwnedIdentity(ownedIdentity.bytesOwnedIdentity);
                                ObvKeycloakState keycloakState = getEngine().getOwnedIdentityKeycloakState(ownedIdentity.bytesOwnedIdentity);
                                if (obvIdentity != null && keycloakState != null) {
                                    KeycloakManager.getInstance().registerKeycloakManagedIdentity(
                                            obvIdentity,
                                            keycloakState.keycloakServer,
                                            keycloakState.clientId,
                                            keycloakState.clientSecret,
                                            keycloakState.jwks,
                                            keycloakState.signatureKey,
                                            keycloakState.serializedAuthState,
                                            keycloakState.ownApiKey,
                                            keycloakState.latestRevocationListTimestamp,
                                            keycloakState.latestGroupUpdateTimestamp,
                                            false
                                    );
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                if (BuildConfig.USE_BILLING_LIB) {
                    BillingUtils.newIdentityAvailableForSubscription(ownedIdentity.bytesOwnedIdentity);
                }
            } else {
                instance.deactivatedIdentities.add(new BytesKey(ownedIdentity.bytesOwnedIdentity));
                App.openAppDialogIdentityDeactivated(ownedIdentity);
            }
        }
    }


    public interface GenerateIdentitySuccessCallback {
        void run(ObvIdentity ownedIdentity);
    }

    public void generateIdentity(@NonNull final String server,
                                 @Nullable final UUID apiKey,
                                 @NonNull final JsonIdentityDetails identityDetails,
                                 @Nullable final String absolutePhotoUrl,
                                 @Nullable final String customDisplayName,
                                 @Nullable final byte[] unlockPassword,
                                 @Nullable final byte[] unlockSalt,
                                 @Nullable final String keycloakServer,
                                 @Nullable final String clientId,
                                 @Nullable final String clientSecret,
                                 @Nullable final JsonWebKeySet jwks,
                                 @Nullable final JsonWebKey signatureKey,
                                 @Nullable final String serializedKeycloakState,
                                 @Nullable final GenerateIdentitySuccessCallback successCallback,
                                 @Nullable final Runnable failureCallback) {
        App.runThread(() -> {
            ObvKeycloakState keycloakState = null;
            if (keycloakServer != null && serializedKeycloakState != null && jwks != null && clientId != null && signatureKey != null) {
                keycloakState = new ObvKeycloakState(keycloakServer, clientId, clientSecret, jwks, signatureKey, serializedKeycloakState, null,0, 0);
            }
            ObvIdentity obvOwnedIdentity = engine.generateOwnedIdentity(server, identityDetails, keycloakState, DEFAULT_DEVICE_DISPLAY_NAME);

            if (obvOwnedIdentity != null) {
                if (keycloakState != null) {
                    KeycloakManager.getInstance().registerKeycloakManagedIdentity(obvOwnedIdentity, keycloakServer, clientId, clientSecret, jwks, signatureKey, serializedKeycloakState, null, 0, 0, true);
                }

                OwnedIdentity ownedIdentity;
                try {
                    ownedIdentity = new OwnedIdentity(obvOwnedIdentity, OwnedIdentity.API_KEY_STATUS_UNKNOWN);
                    ownedIdentity.customDisplayName = customDisplayName;
                    ownedIdentity.unlockPassword = unlockPassword;
                    ownedIdentity.unlockSalt = unlockSalt;
                    db.ownedIdentityDao().insert(ownedIdentity);
                } catch (Exception e) {
                    // unable to create ownedIdentity on app side, try delete on engine side
                    try {
                        engine.deleteOwnedIdentity(obvOwnedIdentity.getBytesIdentity());
                    } catch (Exception ex) {
                        // nothing more we can do here!
                    }
                    if (failureCallback != null) {
                        failureCallback.run();
                    }
                    return;
                }

                if (apiKey != null) {
                    App.runThread(() -> {
                        RegisterApiKeyResult result;
                        do {
                            result = engine.registerOwnedIdentityApiKeyOnServer(obvOwnedIdentity.getBytesIdentity(), apiKey);
                            try {
                                // sleep 5s before retrying to register --> this leaves some time for the engine to create the first server session
                                Thread.sleep(5_000);
                            } catch (InterruptedException ignored) { }
                        } while (result == RegisterApiKeyResult.WAIT_FOR_SERVER_SESSION);
                    });
                } else if (BuildConfig.USE_BILLING_LIB) {
                    BillingUtils.newIdentityAvailableForSubscription(ownedIdentity.bytesOwnedIdentity);
                }

                if (absolutePhotoUrl != null) {
                    try {
                        engine.updateOwnedIdentityPhoto(obvOwnedIdentity.getBytesIdentity(), absolutePhotoUrl);
                        engine.publishLatestIdentityDetails(obvOwnedIdentity.getBytesIdentity());
                    } catch (Exception e) {
                        // error with the photo, too bad...
                    }
                }

                // in case we have an MDM configuration, reload it and reconfigure backups
                MDMConfigurationSingleton.reloadMDMConfiguration();
                try {
                    App.AppStartupTasks.configureMdmWebDavAutomaticBackups();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                App.runThread(new OwnedDevicesSynchronisationWithEngineTask(ownedIdentity.bytesOwnedIdentity));

                selectIdentity(ownedIdentity.bytesOwnedIdentity, (OwnedIdentity ignored) -> {
                    if (successCallback != null) {
                        successCallback.run(obvOwnedIdentity);
                    }

                    String token = AppSingleton.retrieveFirebaseToken();
                    try {
                        engine.registerToPushNotification(obvOwnedIdentity.getBytesIdentity(), ObvPushNotificationType.createAndroid(token), false, null);
                    } catch (Exception e) {
                        Logger.d("Error registering newly generated Identity to push notification.");
                        e.printStackTrace();
                    }
                });
            } else {
                if (failureCallback != null) {
                    failureCallback.run();
                }
            }
        });
    }

    EngineNotificationListener backupRestoredListener;

    public void restoreBackup(final Context activityContext, final String backupSeed, final byte[] encryptedBackupContent, final Runnable successCallback, final Runnable failureCallback) {
        App.runThread(() -> {
            Logger.w("💾 Initiating à backup restore");
            if (AppDatabase.getInstance().ownedIdentityDao().countAll() != 0) {
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT);
                if (failureCallback != null) {
                    failureCallback.run();
                }
                return;
            }

            ObvIdentity[] obvOwnedIdentities = engine.restoreOwnedIdentitiesFromBackup(backupSeed, encryptedBackupContent, DEFAULT_DEVICE_DISPLAY_NAME);
            if (obvOwnedIdentities != null && obvOwnedIdentities.length != 0) {
                try {
                    final String token = AppSingleton.retrieveFirebaseToken();

                    db.runInTransaction((Callable<Void>) () -> {
                        for (ObvIdentity obvOwnedIdentity : obvOwnedIdentities) {
                            OwnedIdentity ownedIdentity = new OwnedIdentity(obvOwnedIdentity, OwnedIdentity.API_KEY_STATUS_UNKNOWN);

                            try {
                                if (getEngine().getOwnedIdentityPublishedAndLatestDetails(obvOwnedIdentity.getBytesIdentity()).length == 2) {
                                    ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_EXIST;
                                }
                            } catch (Exception ignored) {
                                // failed to check if there are unpublished details, nobody cares!
                            }
                            // set ownedIdentity capabilities
                            List<ObvCapability> ownCapabilities = engine.getOwnCapabilities(ownedIdentity.bytesOwnedIdentity);
                            if (ownCapabilities != null) {
                                for (ObvCapability obvCapability : ownCapabilities) {
                                    switch (obvCapability) {
                                        case WEBRTC_CONTINUOUS_ICE:
                                            ownedIdentity.capabilityWebrtcContinuousIce = true;
                                            break;
                                        case ONE_TO_ONE_CONTACTS:
                                            ownedIdentity.capabilityOneToOneContacts = true;
                                            break;
                                        case GROUPS_V2:
                                            ownedIdentity.capabilityGroupsV2 = true;
                                            break;
                                    }
                                }
                            }

                            db.ownedIdentityDao().insert(ownedIdentity);

                            Logger.w("💾 Restored own identity");

                            if (obvOwnedIdentity.isKeycloakManaged() && obvOwnedIdentity.isActive()) {
                                Logger.w("💾 Identity is keycloak managed --> registering with the KeycloakManager");
                                try {
                                    ObvKeycloakState keycloakState = engine.getOwnedIdentityKeycloakState(obvOwnedIdentity.getBytesIdentity());
                                    if (keycloakState != null) {
                                        KeycloakManager.getInstance().registerKeycloakManagedIdentity(
                                                obvOwnedIdentity,
                                                keycloakState.keycloakServer,
                                                keycloakState.clientId,
                                                keycloakState.clientSecret,
                                                keycloakState.jwks,
                                                keycloakState.signatureKey,
                                                keycloakState.serializedAuthState,
                                                null,
                                                0,
                                                0,
                                                false
                                        );
                                    }
                                } catch (Exception ignored) {
                                    Logger.w("💾 Unable to register with the KeycloakManager");
                                }
                            }

                            if (obvOwnedIdentity.isActive()) {
                                try {
                                    engine.registerToPushNotification(obvOwnedIdentity.getBytesIdentity(), ObvPushNotificationType.createAndroid(token), false, null);
                                } catch (Exception e) {
                                    Logger.d("Error registering newly generated Identity to push notification.");
                                    e.printStackTrace();
                                }
                            }
                        }
                        return null;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    if (failureCallback != null) {
                        failureCallback.run();
                    }
                    return;
                }

                for (ObvIdentity obvOwnedIdentity : obvOwnedIdentities) {
                    // mark the owned identity as restored so we do not get untrusted device notification
                    OwnedDevicesSynchronisationWithEngineTask.Companion.ownedIdentityWasTransferredOrRestored(obvOwnedIdentity.getBytesIdentity());
                    // synchronize devices with engine
                    App.runThread(new OwnedDevicesSynchronisationWithEngineTask(obvOwnedIdentity.getBytesIdentity()));
                }

                // once contacts and groups are restored in engine we can restore nicknames and settings
                backupRestoredListener = new EngineNotificationListener() {
                    private Long registrationNumber = null;

                    @Override
                    public void callback(String notificationName, HashMap<String, Object> userInfo) {
                        if (EngineNotifications.ENGINE_BACKUP_RESTORATION_FINISHED.equals(notificationName)) {
                            Logger.w("💾 Received backup restore finished notification --> restoring app data");
                            getEngine().removeNotificationListener(EngineNotifications.ENGINE_BACKUP_RESTORATION_FINISHED, this);
                            backupRestoredListener = null;

                            String appDataBackup = engine.decryptAppDataBackup(backupSeed, encryptedBackupContent);

                            if (appDataBackup != null) {
                                Boolean success = new RestoreAppDataFromBackupTask(appDataBackup).call();
                                if (success != null && !success) {
                                    Logger.w("💾 Failed to restore some app data");
                                    App.toast(R.string.toast_message_unable_to_restore_app_data, Toast.LENGTH_LONG);
                                } else {
                                    Logger.w("💾 App data successfully restored");
                                }
                            }

                            List<OwnedIdentity> ownedIdentities = db.ownedIdentityDao().getAllNotHidden();
                            byte[] bytesOwnedIdentityToSelect;
                            if (ownedIdentities == null || ownedIdentities.isEmpty()) {
                                bytesOwnedIdentityToSelect = null;
                            } else {
                                bytesOwnedIdentityToSelect = ownedIdentities.get(0).bytesOwnedIdentity;
                            }
                            selectIdentity(bytesOwnedIdentityToSelect, (OwnedIdentity ignored) -> {
                                if (successCallback != null) {
                                    successCallback.run();
                                }
                                App.openCurrentOwnedIdentityDetails(activityContext);

                                try {
                                    engine.downloadAllUserData();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }

                    @Override
                    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
                        this.registrationNumber = registrationNumber;
                    }

                    @Override
                    public long getEngineNotificationListenerRegistrationNumber() {
                        if (registrationNumber != null) {
                            return registrationNumber;
                        }
                        return -1;
                    }

                    @Override
                    public boolean hasEngineNotificationListenerRegistrationNumber() {
                        return registrationNumber != null;
                    }
                };

                getEngine().addNotificationListener(EngineNotifications.ENGINE_BACKUP_RESTORATION_FINISHED, backupRestoredListener);

                // before restoring the contacts, we reset the default discussion ephemeral settings to avoid wrongfully setting them
                SettingsActivity.setDefaultDiscussionReadOnce(false);
                SettingsActivity.setDefaultDiscussionVisibilityDuration(null);
                SettingsActivity.setDefaultDiscussionExistenceDuration(null);

                Logger.w("💾 Initiating contact and groups restore");
                engine.restoreContactsAndGroupsFromBackup(backupSeed, encryptedBackupContent, obvOwnedIdentities);
                Logger.w("💾 Contacts and groups restored on Engine side");
            } else {
                if (failureCallback != null) {
                    failureCallback.run();
                }
            }
        });
    }

    // endregion

    // region Singleton methods

    public static void storeFirebaseToken(String token) {
        SharedPreferences.Editor editor = instance.sharedPreferences.edit();
        editor.putString(FIREBASE_TOKEN_SHARED_PREFERENCE_KEY, token);
        editor.apply();
    }

    @Nullable
    public static String retrieveFirebaseToken() {
        if (SettingsActivity.disablePushNotifications()) {
            return null;
        } else {
            return instance.sharedPreferences.getString(FIREBASE_TOKEN_SHARED_PREFERENCE_KEY, null);
        }
    }

    private static void saveLastExecutedVersions(@SuppressWarnings("SameParameterValue") int buildVersion, @SuppressWarnings("SameParameterValue") int androidSdkVersion) {
        SharedPreferences.Editor editor = instance.sharedPreferences.edit();
        editor.putInt(LAST_BUILD_EXECUTED_PREFERENCE_KEY, buildVersion);
        editor.putInt(LAST_ANDROID_SDK_VERSION_EXECUTED_PREFERENCE_KEY, androidSdkVersion);
        editor.apply();
    }

    private static void saveLastFtsGlobalSearchVersion(@SuppressWarnings("SameParameterValue") int lastFtsGlobalSearchVersion) {
        SharedPreferences.Editor editor = instance.sharedPreferences.edit();
        editor.putInt(LAST_FTS_GLOBAL_SEARCH_VERSION_PREFERENCE_KEY, lastFtsGlobalSearchVersion);
        editor.apply();
    }

    public static AppSingleton getInstance() {
        return instance;
    }

    public static Engine getEngine() {
        return instance.engine;
    }

    public static CustomSSLSocketFactory getSslSocketFactory() {
        return instance.sslSocketFactory;
    }

    public static ObjectMapper getJsonObjectMapper() {
        return instance.jsonObjectMapper;
    }

    // endregion

    // region Contact names and info caches for main thread access

    @NonNull private final MutableLiveData<HashMap<BytesKey, Pair<String, String>>> contactNamesCache; // the first element of the pair is the full display name, the second the first name (or custom name for both, if set)
    @NonNull private final MutableLiveData<HashMap<BytesKey, Integer>> contactHuesCache;
    @NonNull private final MutableLiveData<HashMap<BytesKey, String>> contactPhotoUrlsCache;
    @NonNull private final MutableLiveData<HashSet<BytesKey>> contactKeycloakManagedCache;
    @NonNull private final MutableLiveData<HashSet<BytesKey>> contactInactiveCache;
    @NonNull private final MutableLiveData<HashSet<BytesKey>> contactOneToOneCache;
    @NonNull private final MutableLiveData<HashMap<BytesKey, Integer>> contactTrustLevelCache;

    @NonNull
    public static LiveData<HashMap<BytesKey, Pair<String, String>>> getContactNamesCache() {
        return getInstance().contactNamesCache;
    }

    @NonNull
    public static LiveData<HashMap<BytesKey, Integer>> getContactHuesCache() {
        return getInstance().contactHuesCache;
    }

    @NonNull
    public static LiveData<HashMap<BytesKey, String>> getContactPhotoUrlsCache() {
        return getInstance().contactPhotoUrlsCache;
    }

    @NonNull
    public static LiveData<HashSet<BytesKey>> getContactKeycloakManagedCache() {
        return getInstance().contactKeycloakManagedCache;
    }

    @NonNull
    public static LiveData<HashSet<BytesKey>> getContactInactiveCache() {
        return getInstance().contactInactiveCache;
    }

    @NonNull
    public static LiveData<HashSet<BytesKey>> getContactOneToOneCache() {
        return getInstance().contactOneToOneCache;
    }

    @NonNull
    public static LiveData<HashMap<BytesKey, Integer>> getContactTrustLevelCache() {
        return getInstance().contactTrustLevelCache;
    }

    public static void reloadCachedDisplayNamesAndHues() {
        instance.reloadCachedDisplayNamesAndHues(getCurrentIdentityLiveData().getValue());
    }

    private void reloadCachedDisplayNamesAndHues(@Nullable OwnedIdentity ownedIdentity) {
        if (ownedIdentity == null) {
            getInstance().contactNamesCache.postValue(new HashMap<>());
            getInstance().contactHuesCache.postValue(new HashMap<>());
            getInstance().contactPhotoUrlsCache.postValue(new HashMap<>());
            getInstance().contactKeycloakManagedCache.postValue(new HashSet<>());
            getInstance().contactInactiveCache.postValue(new HashSet<>());
            getInstance().contactOneToOneCache.postValue(new HashSet<>());
            getInstance().contactTrustLevelCache.postValue(new HashMap<>());
            return;
        }
        List<Contact> contacts = AppDatabase.getInstance().contactDao().getAllForOwnedIdentitySync(ownedIdentity.bytesOwnedIdentity);
        HashMap<BytesKey, Pair<String, String>> contactNamesHashMap = new HashMap<>();
        HashMap<BytesKey, Integer> contactHuesHashMap = new HashMap<>();
        HashMap<BytesKey, String> contactPhotoUrlsHashMap = new HashMap<>();
        HashSet<BytesKey> contactKeycloakManagedHashSet = new HashSet<>();
        HashSet<BytesKey> contactInactiveHashSet = new HashSet<>();
        HashSet<BytesKey> contactOneToOneHashSet = new HashSet<>();
        HashMap<BytesKey, Integer> contactTrustLevelHashMap = new HashMap<>();
        for (Contact contact : contacts) {
            BytesKey key = new BytesKey(contact.bytesContactIdentity);
            contactNamesHashMap.put(key, new Pair<>(contact.getCustomDisplayName(), contact.getFirstNameOrCustom()));
            if (contact.customNameHue != null) {
                contactHuesHashMap.put(key, contact.customNameHue);
            }
            if (contact.getCustomPhotoUrl() != null) {
                contactPhotoUrlsHashMap.put(key, contact.getCustomPhotoUrl());
            }
            if (contact.keycloakManaged) {
                contactKeycloakManagedHashSet.add(key);
            }
            if (!contact.active) {
                contactInactiveHashSet.add(key);
            }
            if (contact.oneToOne) {
                contactOneToOneHashSet.add(key);
            }
            contactTrustLevelHashMap.put(key, contact.trustLevel);
        }

        BytesKey ownKey = new BytesKey(ownedIdentity.bytesOwnedIdentity);
        contactNamesHashMap.put(ownKey, new Pair<>(App.getContext().getString(R.string.text_you), App.getContext().getString(R.string.text_you)));
        if (ownedIdentity.photoUrl != null) {
            contactPhotoUrlsHashMap.put(ownKey, ownedIdentity.photoUrl);
        }
        if (ownedIdentity.keycloakManaged) {
            contactKeycloakManagedHashSet.add(ownKey);
        }
        if (!ownedIdentity.active) {
            contactInactiveHashSet.add(ownKey);
        }

        List<Group2PendingMember> pendingMembers = AppDatabase.getInstance().group2PendingMemberDao().getAll(ownedIdentity.bytesOwnedIdentity);
        for (Group2PendingMember pendingMember : pendingMembers) {
            BytesKey key = new BytesKey(pendingMember.bytesContactIdentity);
            if (!contactNamesHashMap.containsKey(key)) {
                contactNamesHashMap.put(key, new Pair<>(pendingMember.displayName, pendingMember.getFirstName()));
            }
        }

        getInstance().contactNamesCache.postValue(contactNamesHashMap);
        getInstance().contactHuesCache.postValue(contactHuesHashMap);
        getInstance().contactPhotoUrlsCache.postValue(contactPhotoUrlsHashMap);
        getInstance().contactKeycloakManagedCache.postValue(contactKeycloakManagedHashSet);
        getInstance().contactInactiveCache.postValue(contactInactiveHashSet);
        getInstance().contactOneToOneCache.postValue(contactOneToOneHashSet);
        getInstance().contactTrustLevelCache.postValue(contactTrustLevelHashMap);
    }

    @Nullable
    public static String getContactCustomDisplayName(byte[] bytesContactIdentity) {
        if (getContactNamesCache().getValue() == null) {
            return null;
        }
        Pair<String, String> cache = getContactNamesCache().getValue().get(new BytesKey(bytesContactIdentity));
        if (cache != null) {
            return cache.first;
        }
        return null;
    }

    @Nullable
    public static String getContactFirstName(byte[] bytesContactIdentity) {
        if (getContactNamesCache().getValue() == null) {
            return null;
        }
        Pair<String, String> cache = getContactNamesCache().getValue().get(new BytesKey(bytesContactIdentity));
        if (cache != null) {
            return cache.second;
        }
        return null;
    }

    @Nullable
    public static Integer getContactCustomHue(byte[] bytesContactIdentity) {
        if (getContactHuesCache().getValue() == null) {
            return null;
        }
        return getContactHuesCache().getValue().get(new BytesKey(bytesContactIdentity));
    }

    @Nullable
    public static String getContactPhotoUrl(byte[] bytesContactIdentity) {
        if (getContactPhotoUrlsCache().getValue() == null) {
            return null;
        }
        return getContactPhotoUrlsCache().getValue().get(new BytesKey(bytesContactIdentity));
    }

    public static boolean getContactKeycloakManaged(byte[] bytesContactIdentity) {
        if (getContactKeycloakManagedCache().getValue() == null) {
            return false;
        }
        return getContactKeycloakManagedCache().getValue().contains(new BytesKey(bytesContactIdentity));
    }

    public static boolean getContactInactive(byte[] bytesContactIdentity) {
        if (getContactInactiveCache().getValue() == null) {
            return false;
        }
        return getContactInactiveCache().getValue().contains(new BytesKey(bytesContactIdentity));
    }

    public static boolean getContactOneToOne(byte[] bytesContactIdentity) {
        if (getContactOneToOneCache().getValue() == null) {
            return false;
        }
        return getContactOneToOneCache().getValue().contains(new BytesKey(bytesContactIdentity));
    }

    @Nullable
    public static Integer getContactTrustLevel(byte[] bytesContactIdentity) {
        if (getContactTrustLevelCache().getValue() == null) {
            return null;
        }
        return getContactTrustLevelCache().getValue().get(new BytesKey(bytesContactIdentity));
    }

    public static void updateCachedCustomDisplayName(@NonNull byte[] bytesContactIdentity, @NonNull String customDisplayName, @NonNull String firstNameOrCustom) {
        if (getContactNamesCache().getValue() == null) {
            return;
        }
        HashMap<BytesKey, Pair<String, String>> hashMap = getContactNamesCache().getValue();
        hashMap.put(new BytesKey(bytesContactIdentity), new Pair<>(customDisplayName, firstNameOrCustom));
        getInstance().contactNamesCache.postValue(hashMap);
    }

    public static void updateCachedCustomHue(byte[] bytesContactIdentity, Integer customHue) {
        if (getContactHuesCache().getValue() == null) {
            return;
        }
        HashMap<BytesKey, Integer> hashMap = getContactHuesCache().getValue();
        if (customHue != null) {
            hashMap.put(new BytesKey(bytesContactIdentity), customHue);
        } else {
            hashMap.remove(new BytesKey(bytesContactIdentity));
        }
        getInstance().contactHuesCache.postValue(hashMap);
    }

    public static void updateCachedPhotoUrl(byte[] bytesContactIdentity, String photoUrl) {
        if (getContactPhotoUrlsCache().getValue() == null) {
            return;
        }
        HashMap<BytesKey, String> hashMap = getContactPhotoUrlsCache().getValue();
        if (photoUrl != null) {
            hashMap.put(new BytesKey(bytesContactIdentity), photoUrl);
        } else {
            hashMap.remove(new BytesKey(bytesContactIdentity));
        }
        getInstance().contactPhotoUrlsCache.postValue(hashMap);
    }

    public static void updateCachedKeycloakManaged(byte[] bytesContactIdentity, boolean managed) {
        if (getContactKeycloakManagedCache().getValue() == null) {
            return;
        }
        HashSet<BytesKey> hashSet = getContactKeycloakManagedCache().getValue();
        if (managed) {
            hashSet.add(new BytesKey(bytesContactIdentity));
        } else {
            hashSet.remove(new BytesKey(bytesContactIdentity));
        }
        getInstance().contactKeycloakManagedCache.postValue(hashSet);
    }

    public static void updateCachedActive(byte[] bytesContactIdentity, boolean active) {
        if (getContactInactiveCache().getValue() == null) {
            return;
        }
        HashSet<BytesKey> hashSet = getContactInactiveCache().getValue();
        if (active) {
            hashSet.remove(new BytesKey(bytesContactIdentity));
        } else {
            hashSet.add(new BytesKey(bytesContactIdentity));
        }
        getInstance().contactInactiveCache.postValue(hashSet);
    }

    public static void updateCachedOneToOne(byte[] bytesContactIdentity, boolean oneToOne) {
        if (getContactOneToOneCache().getValue() == null) {
            return;
        }
        HashSet<BytesKey> hashSet = getContactOneToOneCache().getValue();
        if (oneToOne) {
            hashSet.add(new BytesKey(bytesContactIdentity));
        } else {
            hashSet.remove(new BytesKey(bytesContactIdentity));
        }
        getInstance().contactOneToOneCache.postValue(hashSet);
    }


    public static void updateCachedTrustLevel(byte[] bytesContactIdentity, int trustLevel) {
        if (getContactTrustLevelCache().getValue() == null) {
            return;
        }
        HashMap<BytesKey, Integer> hashMap = getContactTrustLevelCache().getValue();
        hashMap.put(new BytesKey(bytesContactIdentity), trustLevel);
        getInstance().contactTrustLevelCache.postValue(hashMap);
    }


    public static void updateCacheContactDeleted(byte[] bytesContactIdentity) {
        BytesKey key = new BytesKey(bytesContactIdentity);
        HashMap<BytesKey, Pair<String, String>> namesHashMap = getContactNamesCache().getValue();
        if (namesHashMap != null && namesHashMap.remove(key) != null) {
            getInstance().contactNamesCache.postValue(namesHashMap);
        }
        HashMap<BytesKey, Integer> huesHashMap = getContactHuesCache().getValue();
        if (huesHashMap != null && huesHashMap.remove(key) != null) {
            getInstance().contactHuesCache.postValue(huesHashMap);
        }
        HashMap<BytesKey, String> photosHashMap = getContactPhotoUrlsCache().getValue();
        if (photosHashMap != null && photosHashMap.remove(key) != null) {
            getInstance().contactPhotoUrlsCache.postValue(photosHashMap);
        }
        HashSet<BytesKey> keycloakHashSet = getContactKeycloakManagedCache().getValue();
        if (keycloakHashSet != null && keycloakHashSet.remove(key)) {
            getInstance().contactKeycloakManagedCache.postValue(keycloakHashSet);
        }
        HashSet<BytesKey> inactiveHashSet = getContactInactiveCache().getValue();
        if (inactiveHashSet != null && inactiveHashSet.remove(key)) {
            getInstance().contactInactiveCache.postValue(inactiveHashSet);
        }
        HashSet<BytesKey> oneToOneHashSet = getContactOneToOneCache().getValue();
        if (oneToOneHashSet != null && oneToOneHashSet.remove(key)) {
            getInstance().contactOneToOneCache.postValue(oneToOneHashSet);
        }
        HashMap<BytesKey, Integer> trustLevelHashMap = getContactTrustLevelCache().getValue();
        if (trustLevelHashMap != null && trustLevelHashMap.remove(key) != null) {
            getInstance().contactTrustLevelCache.postValue(trustLevelHashMap);
        }
    }

    // endregion

    // region Upgrade after new build

    private void runBuildUpgrade(int lastBuildExecuted, int ignoredLastAndroidSdkVersionExecuted) {
        try {
            if (lastBuildExecuted < 88) {
                // trigger the download of all waiting userData in the engine
                engine.downloadAllUserData();
            }
            if (lastBuildExecuted < 127) {
                // recompute the number of images in all messages as the filtering method was changed
                long migrationStartTime = System.currentTimeMillis();
                AppDatabase db = AppDatabase.getInstance();
                List<Message> messages = db.messageDao().getAllWithImages();
                for (Message message: messages) {
                    if (message.recomputeAttachmentCount(db)) {
                        db.messageDao().updateAttachmentCount(message.id, message.totalAttachmentCount, message.imageCount, message.wipedAttachmentCount, message.imageResolutions);
                    }
                }
                Logger.i("Build 126/127 image migration performed in " + (System.currentTimeMillis()-migrationStartTime) + "ms");
            }
            if (lastBuildExecuted != 0 && lastBuildExecuted < 145) {
                App.openAppDialogIntroducingMultiProfile();
            }
            if (lastBuildExecuted != 0 && lastBuildExecuted < 157) {
                if ("null".equals(SettingsActivity.getScaledTurn())) {
                    SettingsActivity.resetScaledTurn();
                }
                try {
                    String googleDriveEmail = SettingsActivity.migrateAutomaticBackupAccount();
                    if (googleDriveEmail != null) {
                        SettingsActivity.setAutomaticBackupConfiguration(BackupCloudProviderService.CloudProviderConfiguration.buildGoogleDrive(googleDriveEmail));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (lastBuildExecuted != 0 && lastBuildExecuted < 183) {
                App.openAppDialogIntroducingGroupsV2();
            }
            if (lastBuildExecuted < 193) {
                // recompute the number of images in all messages as the filtering method was changed
                long migrationStartTime = System.currentTimeMillis();
                AppDatabase db = AppDatabase.getInstance();
                List<Message> messages = db.messageDao().getAllWithLinkPreview();
                for (Message message: messages) {
                    if (message.recomputeAttachmentCount(db)) {
                        db.messageDao().updateAttachmentCount(message.id, message.totalAttachmentCount, message.imageCount, message.wipedAttachmentCount, message.imageResolutions);
                    }
                }
                Logger.i("Build 193 link-preview migration performed in " + (System.currentTimeMillis()-migrationStartTime) + "ms");
            }
            if (lastBuildExecuted != 0 && lastBuildExecuted < 197) {
                App.openAppDialogIntroducingMentions();
            }
            if (lastBuildExecuted != 0 && lastBuildExecuted < 204) {
                App.openAppDialogIntroducingMarkdown();
            }
            if (lastBuildExecuted < 206) {
                AppSingleton.getEngine().setAllOwnedDeviceNames(DEFAULT_DEVICE_DISPLAY_NAME);
            }
            if (lastBuildExecuted != 0 && lastBuildExecuted < 220) {
                App.openAppDialogIntroducingMultiDeviceAndDesktop();
            }

            PeriodicTasksScheduler.resetAllPeriodicTasksFollowingAnUpdate(App.getContext());
            saveLastExecutedVersions(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT);
        } catch (Exception e) {
            // upgrade failed, will be tried again at next startup...
            Logger.w("Build Upgrade failed");
            e.printStackTrace();
        }
    }

    private void runNoBackupFolderMigration() {
        String[] filesToMigrate = new String[]{"inbound_attachments", "identity_photos", "discussion_backgrounds", "fyles", "engine_db.sqlite"};
        File filesDir = App.getContext().getFilesDir();
        File noBackupFilesDir = App.getContext().getNoBackupFilesDir();
        //noinspection ResultOfMethodCallIgnored
        noBackupFilesDir.mkdirs();
        for (String fileToMigrate: filesToMigrate) {
            File oldFile = new File(filesDir, fileToMigrate);
            File newFile = new File(noBackupFilesDir, fileToMigrate);
            if (oldFile.exists()) {
                if (!oldFile.renameTo(newFile)) {
                    Log.e("Logger", "Error migrating " + fileToMigrate + " to no backup folder.");
                }
            }
        }

        File databaseDir = App.getContext().getDatabasePath("app_database").getParentFile();
        String[] dbFilesToMigrate = new String[]{"app_database", "app_database-wal", "app_database-shm"};
        for (String dbFileToMigrate: dbFilesToMigrate) {
            File oldFile = new File(databaseDir, dbFileToMigrate);
            File newFile = new File(noBackupFilesDir, dbFileToMigrate);
            if (oldFile.exists()) {
                if (!oldFile.renameTo(newFile)) {
                    Log.e("Logger", "Error migrating " + dbFileToMigrate + " to no backup folder.");
                }
            }
        }
    }

    private void runFtsGlobalSearchRebuild(int lastFtsGlobalSearchVersion) {
        try {
            Logger.i("FTS rebuild required");
            long startTime = System.currentTimeMillis();
            if (lastFtsGlobalSearchVersion < 1) {
                // initial rebuild to index all messages received before the creation of the FTS table
                Logger.i("- Rebuilding message table FTS");
                AppDatabase.getInstance().rawDao().executeRawQuery(new SimpleSQLiteQuery("INSERT INTO message_table_fts(message_table_fts) VALUES ('rebuild')"));
            }
            saveLastFtsGlobalSearchVersion(AppDatabase.DB_FTS_GLOBAL_SEARCH_VERSION);
            Logger.i("FTS rebuild finished in " + (System.currentTimeMillis() - startTime) + "ms");
        } catch (Exception e) {
            // rebuild failed, will be tried again at next startup...
            Logger.w("FTS rebuild failed");
            e.printStackTrace();
        }
    }

    // endregion
}
