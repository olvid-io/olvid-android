/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

package io.olvid.messenger.openid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import net.openid.appauth.AuthState;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.lang.HashUtil;
import org.json.JSONException;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.engine.types.RegisterApiKeyResult;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff;
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff;

public class KeycloakManager {
    private static KeycloakManager INSTANCE = null;

    public static final int MAX_FAIL_COUNT = 5;
    public static final long SYNCHRONIZATION_INTERVAL_MS = 21_600_000L; // synchronize with keycloak server every 6 hours
    public static final long OWN_SIGNED_DETAILS_RENEWAL_INTERVAL_MILLIS = 7 * 86_400_000L; // refresh our own details if they are more than 1 week old
    public static final long REVOCATION_LIST_LATEST_TIMESTAMP_OVERLAP_MILLIS = 3_600_000L; // time to subtract from latest query time when getting revocation list --> this leaves 1h for the server to finish writing down any new revocation

    private final HashMap<BytesKey, KeycloakManagerState> ownedIdentityStates;
    private final HashSet<BytesKey> currentlySyncingOwnedIdentities;
    private final HashSet<BytesKey> authenticationRequiredOwnedIdentities;
    private final NoExceptionSingleThreadExecutor executor;
    private final Timer retryTimer;


    public KeycloakManager() {
        ownedIdentityStates = new HashMap<>();
        currentlySyncingOwnedIdentities = new HashSet<>();
        authenticationRequiredOwnedIdentities = new HashSet<>();
        executor = new NoExceptionSingleThreadExecutor("KeycloakManager-Executor");
        retryTimer = new Timer("KeycloakManager-Retry timer");
    }

    public static KeycloakManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new KeycloakManager();
        }
        return INSTANCE;
    }

    // region public methods


    public void registerKeycloakManagedIdentity(@NonNull ObvIdentity obvIdentity, String keycloakServerUrl, String clientId, String clientSecret, JsonWebKeySet jwks, JsonWebKey signatureKey, @Nullable String serializedKeycloakState, boolean transferRestricted, @Nullable String ownApiKey, long latestRevocationListTimestamp, long latestGroupUpdateTimestamp, boolean firstKeycloakBinding) {
        executor.execute(() -> {
            AuthState authState = null;
            if (serializedKeycloakState != null) {
                try {
                    authState = AuthState.jsonDeserialize(serializedKeycloakState);
                } catch (JSONException e) {
                    Logger.d("Error deserializing AuthState");
                }
            }
            KeycloakManagerState keycloakManagerState = new KeycloakManagerState(obvIdentity, keycloakServerUrl, clientId, clientSecret, jwks, signatureKey, authState, ownApiKey, transferRestricted, latestRevocationListTimestamp, latestGroupUpdateTimestamp);

            BytesKey identityBytesKey = new BytesKey(keycloakManagerState.bytesOwnedIdentity);
            ownedIdentityStates.put(identityBytesKey, keycloakManagerState);

            if (!firstKeycloakBinding) {
                synchronizeIdentityWithKeycloak(identityBytesKey, 0);
            }
        });
    }

    public void unregisterKeycloakManagedIdentity(@NonNull byte[] bytesOwnedIdentity) {
        executor.execute(() -> {
            BytesKey identityBytesKey = new BytesKey(bytesOwnedIdentity);
            ownedIdentityStates.remove(identityBytesKey);
            currentlySyncingOwnedIdentities.remove(identityBytesKey);
            AndroidNotificationManager.clearKeycloakAuthenticationRequiredNotification(bytesOwnedIdentity);
        });
    }

    public static boolean isOwnedIdentityTransferRestricted(@NonNull byte[] bytesOwnedIdentity) {
        if (INSTANCE != null) {
            KeycloakManagerState kms = INSTANCE.ownedIdentityStates.get(new BytesKey(bytesOwnedIdentity));
            return kms != null && kms.transferRestricted;
        }
        return false;
    }

    public static void syncAllManagedIdentities() {
        if (INSTANCE != null) {
            getInstance().synchronizeAllManagedIdentities();
        }
    }

    public static void forceSyncManagedIdentity(byte[] bytesOwnedIdentity) {
        if (INSTANCE != null) {
            BytesKey identityBytesKey = new BytesKey(bytesOwnedIdentity);
            KeycloakManagerState kms = getInstance().ownedIdentityStates.get(identityBytesKey);
            if (kms != null) {
                kms.lastSynchronization = 0;
                getInstance().synchronizeIdentityWithKeycloak(identityBytesKey, 0);
            }
        }
    }

    public static void resetLatestGroupDownloadTimestamp(byte[] bytesOwnedIdentity) {
        if (INSTANCE != null) {
            BytesKey identityBytesKey = new BytesKey(bytesOwnedIdentity);
            KeycloakManagerState kms = getInstance().ownedIdentityStates.get(identityBytesKey);
            if (kms != null) {
                kms.latestGroupUpdateTimestamp = 0;
            }
        }
    }

    public static void forceSelfTestAndReauthentication(byte[] bytesOwnedIdentity) {
        if (INSTANCE != null) {
            BytesKey identityBytesKey = new BytesKey(bytesOwnedIdentity);
            KeycloakManagerState kms = getInstance().ownedIdentityStates.get(identityBytesKey);
            if (kms != null) {
                getInstance().selfTestAndPromptForAuthentication(identityBytesKey);
            }
        }
    }

    public static void processPushTopicNotification(String pushTopic) {
        if (INSTANCE != null) {
            try {
                Collection<ObvIdentity> obvIdentities = AppSingleton.getEngine().getOwnedIdentitiesWithKeycloakPushTopic(pushTopic);
                for (ObvIdentity obvIdentity : obvIdentities) {
                    forceSyncManagedIdentity(obvIdentity.getBytesIdentity());
                }
            } catch (Exception e) {
                Logger.d("Failed to retrieve identities with a push topic...");
                e.printStackTrace();
            }
        }
    }

    public static void showAuthenticationRequiredNotificationForSelectedIdentityIfNeeded(byte[] bytesOwnedIdentity) {
        if (INSTANCE != null) {
            if (INSTANCE.authenticationRequiredOwnedIdentities.contains(new BytesKey(bytesOwnedIdentity))) {
                AndroidNotificationManager.displayKeycloakAuthenticationRequiredNotification(bytesOwnedIdentity);
            }
        }
    }

    public void reAuthenticationSuccessful(byte[] bytesOwnedIdentity, JsonWebKeySet jwks, AuthState authState) {
        executor.execute(() -> {
            BytesKey identityBytesKey = new BytesKey(bytesOwnedIdentity);
            AndroidNotificationManager.clearKeycloakAuthenticationRequiredNotification(bytesOwnedIdentity);

            authenticationRequiredOwnedIdentities.remove(identityBytesKey);
            KeycloakManagerState kms = ownedIdentityStates.get(identityBytesKey);
            if (kms == null) {
                return;
            }
            // reset the synchronization time to force a full re-sync
            kms.lastSynchronization = 0;
            kms.jwks = jwks;
            kms.authState = authState;

            try {
                AppSingleton.getEngine().saveKeycloakJwks(bytesOwnedIdentity, jwks.toJson());
                AppSingleton.getEngine().saveKeycloakAuthState(bytesOwnedIdentity, authState.jsonSerializeString());
            } catch (Exception e) {
                // failed to save to engine
            }

            // after any authentication, re-sync details
            synchronizeIdentityWithKeycloak(identityBytesKey, 0);
        });
    }


    public void uploadOwnIdentity(@NonNull byte[] bytesOwnedIdentity, @NonNull KeycloakCallback<Void> callback) {
        executor.execute(() -> {
            BytesKey identityBytesKey = new BytesKey(bytesOwnedIdentity);
            KeycloakManagerState kms = ownedIdentityStates.get(identityBytesKey);
            if (kms == null) {
                // this identity is not managed, fail
                callback.failed(KeycloakTasks.RFC_IDENTITY_NOT_MANAGED);
                return;
            }

            if (kms.authState == null) {
                // authentication required
                callback.failed(KeycloakTasks.RFC_AUTHENTICATION_REQUIRED);
                synchronizeIdentityWithKeycloak(identityBytesKey, 0);
                return;
            }

            // This method is only called after user consent for revocation, so do not ask next time
            kms.autoRevokeOnNextSync = true;

            KeycloakTasks.uploadOwnIdentity(App.getContext(), kms.serverUrl, kms.authState, kms.clientSecret, bytesOwnedIdentity, new KeycloakCallbackWrapper<>(identityBytesKey, new KeycloakCallback<Void>() {
                @Override
                public void success(Void result) {
                    callback.success(result);

                    // once an identity is uploaded, rerun a synchronization to download the new API key and update local identity
                    kms.autoRevokeOnNextSync = false;
                    synchronizeIdentityWithKeycloak(identityBytesKey, 0);
                }

                @Override
                public void failed(int rfc) {
                    switch (rfc) {
                        case KeycloakTasks.RFC_AUTHENTICATION_REQUIRED:
                            selfTestAndPromptForAuthentication(identityBytesKey);
                            break;
                        case KeycloakTasks.RFC_IDENTITY_ALREADY_UPLOADED:
                            App.openAppDialogKeycloakIdentityReplacementForbidden(bytesOwnedIdentity);
                            break;
                        case KeycloakTasks.RFC_IDENTITY_REVOKED:
                            // let the caller handle the error
                            break;
                        case KeycloakTasks.RFC_SERVER_ERROR:
                        case KeycloakTasks.RFC_NETWORK_ERROR:
                        case KeycloakTasks.RFC_UNKNOWN_ERROR:
                        default:
                            // retry with a delay
                            retryTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    synchronizeIdentityWithKeycloak(identityBytesKey, 1);
                                }
                            }, 500);

                            break;
                    }

                    callback.failed(rfc);
                }
            }));
        });
    }


    public void search(byte[] bytesOwnedIdentity, String searchQuery, KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Integer>> callback) {
        executor.execute(() -> {
            BytesKey identityBytesKey = new BytesKey(bytesOwnedIdentity);
            KeycloakManagerState kms = ownedIdentityStates.get(identityBytesKey);
            if (kms == null) {
                // this identity is not managed, fail
                callback.failed(KeycloakTasks.RFC_IDENTITY_NOT_MANAGED);
                return;
            }

            if (kms.authState == null) {
                callback.failed(KeycloakTasks.RFC_AUTHENTICATION_REQUIRED);
                synchronizeIdentityWithKeycloak(identityBytesKey, 0);
                return;
            }

            KeycloakTasks.search(App.getContext(), kms.serverUrl, kms.authState, kms.clientSecret, searchQuery, new KeycloakCallbackWrapper<>(identityBytesKey, new KeycloakCallback<Pair<List<JsonKeycloakUserDetails>, Integer>>() {
                @Override
                public void success(Pair<List<JsonKeycloakUserDetails>, Integer> result) {
                    callback.success(result);
                }

                @Override
                public void failed(int rfc) {
                    Logger.d("Search failed with rfc " + rfc);

                    switch (rfc) {
                        case KeycloakTasks.RFC_AUTHENTICATION_REQUIRED:
                            selfTestAndPromptForAuthentication(identityBytesKey);
                            break;
                        case KeycloakTasks.RFC_NETWORK_ERROR:
                        case KeycloakTasks.RFC_SERVER_ERROR:
                        case KeycloakTasks.RFC_UNKNOWN_ERROR:
                        default:
                            // nothing to do, we should not retry
                            break;
                    }

                    callback.failed(rfc);
                }
            }));
        });
    }


    public void addContact(byte[] bytesOwnedIdentity, String contactUserId, byte[] bytesContactIdentity, KeycloakCallback<Void> callback) {
        executor.execute(() -> {
            BytesKey identityBytesKey = new BytesKey(bytesOwnedIdentity);
            KeycloakManagerState kms = ownedIdentityStates.get(identityBytesKey);
            if (kms == null || kms.authState == null || kms.jwks == null) {
                // this identity is not managed, fail
                callback.failed(KeycloakTasks.RFC_IDENTITY_NOT_MANAGED);
                return;
            }

            KeycloakTasks.addContact(App.getContext(), kms.serverUrl, kms.authState, kms.clientSecret, kms.jwks, kms.signatureKey, bytesOwnedIdentity, contactUserId, bytesContactIdentity, new KeycloakCallbackWrapper<>(identityBytesKey, new KeycloakCallback<Void>() {
                @Override
                public void success(Void result) {
                    callback.success(null);
                }

                @Override
                public void failed(int rfc) {
                    Logger.d("Add contact failed with rfc " + rfc);

                    switch (rfc) {
                        case KeycloakTasks.RFC_AUTHENTICATION_REQUIRED:
                            selfTestAndPromptForAuthentication(identityBytesKey);
                            break;
                        case KeycloakTasks.RFC_NETWORK_ERROR:
                        case KeycloakTasks.RFC_SERVER_ERROR:
                        case KeycloakTasks.RFC_UNKNOWN_ERROR:
                        case KeycloakTasks.RFC_INVALID_SIGNATURE:
                        default:
                            // nothing to do, we should not retry
                            break;
                    }

                    callback.failed(rfc);
                }
            }));
        });
    }

    // endregion

    private void selfTestAndPromptForAuthentication(@NonNull BytesKey identityBytesKey) {
        executor.execute(() -> {
            KeycloakManagerState kms = ownedIdentityStates.get(identityBytesKey);
            if (kms != null) {
                String nonce = AppSingleton.getEngine().getOwnedIdentityKeycloakSelfRevocationTestNonce(kms.bytesOwnedIdentity, kms.serverUrl);
                KeycloakTasks.selfRevocationTest(kms.serverUrl, nonce == null ? "" : nonce, new KeycloakCallback<Boolean>() {
                    @Override
                    public void success(Boolean result) {
                        // only unbind if nonce is non-null
                        if (nonce != null && result != null && result) {
                            // the server returned true --> the identity is no longer managed
                            AppSingleton.getEngine().unbindOwnedIdentityFromKeycloak(kms.bytesOwnedIdentity);
                            App.openAppDialogKeycloakIdentityRevoked(kms.bytesOwnedIdentity);
                        } else {
                            // require an authentication: either nonce still exists, or nonce is null
                            authenticationRequiredOwnedIdentities.add(identityBytesKey);
                            AndroidNotificationManager.displayKeycloakAuthenticationRequiredNotification(identityBytesKey.bytes);
                            App.openAppDialogKeycloakAuthenticationRequired(kms.bytesOwnedIdentity, kms.clientId, kms.clientSecret, kms.serverUrl);
                        }
                    }

                    @Override
                    public void failed(int rfc) {
                        // in case of failure, we do nothing --> this is probably only a network error, and it will be tried again
                        // we do not want to prompt the user to authenticate in case of permanent connection error with the keycloak
                    }
                });
            }
        });
    }

    private void synchronizeAllManagedIdentities() {
        executor.execute(() -> {
            for (BytesKey identityBytesKey: ownedIdentityStates.keySet()) {
                synchronizeIdentityWithKeycloak(identityBytesKey, 0);
            }
        });
    }


    private void synchronizeIdentityWithKeycloak(final BytesKey identityBytesKey, final int failedAttempts) {
        executor.execute(() -> {
            KeycloakManagerState kms = ownedIdentityStates.get(identityBytesKey);
            if (kms == null) {
                return;
            }

            if (System.currentTimeMillis() - kms.lastSynchronization < SYNCHRONIZATION_INTERVAL_MS) {
                return;
            }

            /////////
            // mark the identity as currently syncing --> un-mark it as soon as success or failure
            if (!currentlySyncingOwnedIdentities.add(identityBytesKey)) {
                // if we are already syncing, return
                return;
            }

            if (kms.jwks == null || kms.authState == null || !kms.authState.isAuthorized()) {
                // if jwks is null, or if we are not authenticated --> full authentication round
                currentlySyncingOwnedIdentities.remove(identityBytesKey);

                // authentication required --> open authentication app dialog
                selfTestAndPromptForAuthentication(identityBytesKey);
                return;
            }


            KeycloakTasks.getOwnDetails(App.getContext(), kms.serverUrl, kms.authState, kms.clientSecret, kms.jwks, Math.max(0, kms.latestRevocationListTimestamp - REVOCATION_LIST_LATEST_TIMESTAMP_OVERLAP_MILLIS), new KeycloakCallbackWrapper<>(identityBytesKey, new KeycloakCallback<>() {
                @Override
                public void success(Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff> result) {
                    executor.execute(() -> {
                        currentlySyncingOwnedIdentities.remove(identityBytesKey);

                        KeycloakUserDetailsAndStuff keycloakUserDetailsAndStuff = result.first;
                        KeycloakServerRevocationsAndStuff keycloakServerRevocationsAndStuff = result.second;

                        if (keycloakUserDetailsAndStuff == null || keycloakUserDetailsAndStuff.userDetails == null || keycloakServerRevocationsAndStuff == null) {
                            // this should never happen --> failed
                            failed(KeycloakTasks.RFC_BAD_RESPONSE);
                            return;
                        }

                        Logger.d("Successfully downloaded own details from keycloak server");

                        KeycloakManagerState kms = ownedIdentityStates.get(identityBytesKey);
                        if (kms == null) {
                            return;
                        }
                        if (kms.authState == null) {
                            synchronizeIdentityWithKeycloak(identityBytesKey, 0);
                            return;
                        }

                        // check if version is outdated
                        if (keycloakServerRevocationsAndStuff.minimumBuildVersions != null) {
                            Integer minimumBuildVersion = keycloakServerRevocationsAndStuff.minimumBuildVersions.get("android");
                            if (minimumBuildVersion != null && minimumBuildVersion > BuildConfig.VERSION_CODE) {
                                App.openAppDialogOutdatedVersion();
                            }
                        }

                        try {
                            // verify that the signature key matches what is stored, ask for user confirmation otherwise
                            if (keycloakUserDetailsAndStuff.signatureKey != null) {
                                JsonWebKey previousKey = AppSingleton.getEngine().getOwnedIdentityKeycloakSignatureKey(kms.bytesOwnedIdentity);
                                if (previousKey == null) {
                                    AppSingleton.getEngine().setOwnedIdentityKeycloakSignatureKey(kms.bytesOwnedIdentity, keycloakUserDetailsAndStuff.signatureKey);
                                    kms.signatureKey = keycloakUserDetailsAndStuff.signatureKey;
                                } else if (!Arrays.equals(previousKey.calculateThumbprint(HashUtil.SHA_256), keycloakUserDetailsAndStuff.signatureKey.calculateThumbprint(HashUtil.SHA_256))) {
                                    App.openAppDialogKeycloakSignatureKeyChanged(kms.bytesOwnedIdentity);
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            // in case of exception, do nothing
                            return;
                        }

                        JsonKeycloakUserDetails userDetails = keycloakUserDetailsAndStuff.userDetails;

                        try {
                            String previousId = AppSingleton.getEngine().getOwnedIdentityKeycloakUserId(kms.bytesOwnedIdentity);
                            if (previousId == null) {
                                AppSingleton.getEngine().setOwnedIdentityKeycloakUserId(kms.bytesOwnedIdentity, userDetails.getId());
                            } else if (!previousId.equals(userDetails.getId())) {
                                // user Id changed on keycloak --> probably an authentication with the wrong login
                                // check the identity and only update id locally if the identity is the same
                                if (Arrays.equals(userDetails.getIdentity(), kms.bytesOwnedIdentity)) {
                                    AppSingleton.getEngine().setOwnedIdentityKeycloakUserId(kms.bytesOwnedIdentity, userDetails.getId());
                                } else {
                                    App.openAppDialogKeycloakUserIdChanged(kms.bytesOwnedIdentity, kms.clientId, kms.clientSecret, kms.serverUrl);
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            // in case of exception, do nothing
                            return;
                        }

                        // check if ownedIdentity was never uploaded
                        if (userDetails.getIdentity() == null
                                || userDetails.getIdentity().length == 0
                                || (kms.autoRevokeOnNextSync && keycloakServerRevocationsAndStuff.revocationAllowed && !Arrays.equals(userDetails.getIdentity(), identityBytesKey.bytes))) {
                            currentlySyncingOwnedIdentities.add(identityBytesKey);

                            // upload the key to the server
                            KeycloakTasks.uploadOwnIdentity(App.getContext(), kms.serverUrl, kms.authState, kms.clientSecret, identityBytesKey.bytes, new KeycloakCallback<Void>() {
                                @Override
                                public void success(Void result) {
                                    Logger.d("Successfully uploaded own key");
                                    currentlySyncingOwnedIdentities.remove(identityBytesKey);

                                    // uploaded our own key --> re-sync
                                    kms.autoRevokeOnNextSync = false;
                                    synchronizeIdentityWithKeycloak(identityBytesKey, 0);
                                }

                                @Override
                                public void failed(int rfc) {
                                    Logger.d("Failed to upload own key " + rfc);
                                    currentlySyncingOwnedIdentities.remove(identityBytesKey);

                                    // we retry with a delay
                                    switch (rfc) {
                                        case KeycloakTasks.RFC_AUTHENTICATION_REQUIRED:
                                        case KeycloakTasks.RFC_IDENTITY_REVOKED:
                                            kms.authState = null;
                                            break;
                                        case KeycloakTasks.RFC_IDENTITY_ALREADY_UPLOADED:
                                        case KeycloakTasks.RFC_SERVER_ERROR:
                                        case KeycloakTasks.RFC_NETWORK_ERROR:
                                        case KeycloakTasks.RFC_UNKNOWN_ERROR:
                                            break;
                                    }
                                    if (failedAttempts < MAX_FAIL_COUNT) {
                                        retryTimer.schedule(new TimerTask() {
                                            @Override
                                            public void run() {
                                                synchronizeIdentityWithKeycloak(identityBytesKey, failedAttempts + 1);
                                            }
                                        }, 500L << failedAttempts);
                                    }
                                }
                            });
                            return;
                        } else if (!Arrays.equals(userDetails.getIdentity(), identityBytesKey.bytes) && keycloakServerRevocationsAndStuff.revocationAllowed) {
                            // revocation is possible but was not requested
                            App.openAppDialogKeycloakIdentityReplacement(identityBytesKey.bytes, kms.serverUrl, kms.clientSecret, kms.authState.jsonSerializeString());
                            return;
                        } else if (!Arrays.equals(userDetails.getIdentity(), identityBytesKey.bytes)) {
                            // revocation required, but not possible
                            App.openAppDialogKeycloakIdentityReplacementForbidden(identityBytesKey.bytes);
                            return;
                        }


                        ////////
                        // if we reach this point, our correct identity is on the server
                        // --> compare the details and update locally if needed
                        JsonIdentityDetails serverJsonIdentityDetails = userDetails.getIdentityDetails(keycloakUserDetailsAndStuff.signedUserDetails);
                        if (!kms.identityDetails.equals(serverJsonIdentityDetails)
                                || (kms.ownDetailsSignatureTimestamp == null && userDetails.getTimestamp() != null)
                                || (kms.ownDetailsSignatureTimestamp != null && userDetails.getTimestamp() != null && kms.ownDetailsSignatureTimestamp + OWN_SIGNED_DETAILS_RENEWAL_INTERVAL_MILLIS < userDetails.getTimestamp())) {
                            try {
                                Logger.i("Refreshing keycloak owned details in engine");
                                AppSingleton.getEngine().updateLatestIdentityDetails(identityBytesKey.bytes, serverJsonIdentityDetails);
                                AppSingleton.getEngine().publishLatestIdentityDetails(identityBytesKey.bytes);
                                kms.identityDetails = serverJsonIdentityDetails;
                                kms.ownDetailsSignatureTimestamp = userDetails.getTimestamp();
                            } catch (Exception e) {
                                // engine exception --> simply retry
                                if (failedAttempts < MAX_FAIL_COUNT) {
                                    retryTimer.schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            synchronizeIdentityWithKeycloak(identityBytesKey, failedAttempts + 1);
                                        }
                                    }, 500L << failedAttempts);
                                }
                                return;
                            }
                        }

                        // update API key if needed
                        if (keycloakUserDetailsAndStuff.apiKey != null) {
                            // update API key if needed
                            try {
                                UUID newApiKey = UUID.fromString(keycloakUserDetailsAndStuff.apiKey);
                                if (!Objects.equals(Logger.getUuidString(newApiKey), kms.ownApiKey)) {
                                    App.runThread(() -> registerMeApiKeyOnServer(kms, identityBytesKey, newApiKey));
                                }
                            } catch (Exception e) {
                                // do nothing
                            }
                        }

                        if (kms.transferRestricted != keycloakServerRevocationsAndStuff.transferRestricted) {
                            AppSingleton.getEngine().updateKeycloakTransferRestrictedIfNeeded(kms.bytesOwnedIdentity, kms.serverUrl, keycloakServerRevocationsAndStuff.transferRestricted);
                            kms.transferRestricted = keycloakServerRevocationsAndStuff.transferRestricted;
                        }

                        AppSingleton.getEngine().updateKeycloakPushTopicsIfNeeded(kms.bytesOwnedIdentity, kms.serverUrl, keycloakUserDetailsAndStuff.pushTopics);
                        AppSingleton.getEngine().setOwnedIdentityKeycloakSelfRevocationTestNonce(kms.bytesOwnedIdentity, kms.serverUrl, keycloakUserDetailsAndStuff.selfRevocationTestNonce);

                        // update revocation list and latest revocation list timestamp in a transaction
                        if (keycloakServerRevocationsAndStuff.signedRevocations != null) {
                            AppSingleton.getEngine().updateKeycloakRevocationList(identityBytesKey.bytes, keycloakServerRevocationsAndStuff.currentServerTimestamp, keycloakServerRevocationsAndStuff.signedRevocations);
                            kms.latestRevocationListTimestamp = keycloakServerRevocationsAndStuff.currentServerTimestamp;
                        }

                        ///////////////
                        // now synchronize groups too
                        KeycloakTasks.getGroups(App.getContext(), kms.serverUrl, kms.authState, kms.clientSecret, kms.bytesOwnedIdentity, Math.max(0, kms.latestGroupUpdateTimestamp - REVOCATION_LIST_LATEST_TIMESTAMP_OVERLAP_MILLIS), new KeycloakCallbackWrapper<>(identityBytesKey, new KeycloakCallback<Long>() {
                            @Override
                            public void success(Long timestamp) {
                                if (timestamp != null) {
                                    kms.latestGroupUpdateTimestamp = timestamp;
                                }
                            }

                            @Override
                            public void failed(int rfc) {
                                // do nothing, this might be an old keycloak server with no groups support
                            }
                        }));

                        /////////
                        // synchronization finished successfully!!!
                        // --> update the last sync timestamp
                        kms.lastSynchronization = System.currentTimeMillis();
                        retryTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                synchronizeIdentityWithKeycloak(identityBytesKey, 0);
                            }
                        }, SYNCHRONIZATION_INTERVAL_MS + 1_000);
                    });
                }

                @Override
                public void failed(int rfc) {
                    Logger.d("Fail downloaded owned details " + rfc);
                    currentlySyncingOwnedIdentities.remove(identityBytesKey);

                    switch (rfc) {
                        case KeycloakTasks.RFC_AUTHENTICATION_REQUIRED:
                            selfTestAndPromptForAuthentication(identityBytesKey);
                            break;
                        case KeycloakTasks.RFC_SERVER_ERROR:
                        case KeycloakTasks.RFC_INVALID_SIGNATURE:
                        case KeycloakTasks.RFC_BAD_RESPONSE:
                        case KeycloakTasks.RFC_NETWORK_ERROR:
                        case KeycloakTasks.RFC_UNKNOWN_ERROR:
                            // retry after a small delay
                            if (failedAttempts < MAX_FAIL_COUNT) {
                                retryTimer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        synchronizeIdentityWithKeycloak(identityBytesKey, failedAttempts + 1);
                                    }
                                }, 500L << failedAttempts);
                            }
                            break;
                    }
                }
            }));
        });
    }

    private static void registerMeApiKeyOnServer(KeycloakManagerState kms, BytesKey identityBytesKey, @NonNull UUID apiKey) {
        // retry at most 10 times
        for (int i=0; i<10; i++) {
            RegisterApiKeyResult registerApiKeyResult = AppSingleton.getEngine().registerOwnedIdentityApiKeyOnServer(identityBytesKey.bytes, apiKey);
            Logger.d("Registering Keycloak api key on server: " + registerApiKeyResult);
            switch (registerApiKeyResult) {
                case SUCCESS:
                    try {
                        AppSingleton.getEngine().saveKeycloakApiKey(identityBytesKey.bytes, Logger.getUuidString(apiKey));
                        kms.ownApiKey = Logger.getUuidString(apiKey);
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case WAIT_FOR_SERVER_SESSION:
                    // wait a bit, then try again
                    try {
                        Thread.sleep((3 + i) * 1000);
                    } catch (InterruptedException ignored) { }
                    break;
                case INVALID_KEY:
                case FAILED:
                    // non-retriable failure, abort
                    return;
            }
        }
    }

    private static class KeycloakManagerState {
        @NonNull final byte[] bytesOwnedIdentity;
        @NonNull JsonIdentityDetails identityDetails;
        @Nullable Long ownDetailsSignatureTimestamp;
        @NonNull final String serverUrl;
        @NonNull final String clientId;
        @Nullable final String clientSecret;
        @Nullable JsonWebKeySet jwks;
        @Nullable JsonWebKey signatureKey;
        @Nullable AuthState authState;
        @Nullable String ownApiKey;
        boolean transferRestricted;
        long lastSynchronization;
        boolean autoRevokeOnNextSync;
        long latestRevocationListTimestamp;
        long latestGroupUpdateTimestamp;

        public KeycloakManagerState(@NonNull ObvIdentity obvIdentity, @NonNull String serverUrl, @NonNull String clientId, @Nullable String clientSecret, @Nullable JsonWebKeySet jwks, @Nullable JsonWebKey signatureKey, @Nullable AuthState authState, @Nullable String ownApiKey, boolean transferRestricted, long latestRevocationListTimestamp, long latestGroupUpdateTimestamp) {
            this.bytesOwnedIdentity = obvIdentity.getBytesIdentity();
            this.identityDetails = obvIdentity.getIdentityDetails();

            if (identityDetails.getSignedUserDetails() != null) {
                try {
                    JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                            .setSkipSignatureVerification()
                            .setSkipAllValidators()
                            .build();
                    JwtContext context = jwtConsumer.process(identityDetails.getSignedUserDetails());
                    JsonKeycloakUserDetails jsonKeycloakUserDetails = AppSingleton.getJsonObjectMapper().readValue(context.getJwtClaims().getRawJson(), JsonKeycloakUserDetails.class);
                    ownDetailsSignatureTimestamp = jsonKeycloakUserDetails.getTimestamp();
                } catch (Exception e) {
                    e.printStackTrace();
                    ownDetailsSignatureTimestamp = null;
                }
            } else {
                ownDetailsSignatureTimestamp = null;
            }

            this.serverUrl = serverUrl;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.jwks = jwks;
            this.signatureKey = signatureKey;
            this.authState = authState;
            this.ownApiKey = ownApiKey;
            this.transferRestricted = transferRestricted;
            this.lastSynchronization = 0;
            this.autoRevokeOnNextSync = false;
            this.latestRevocationListTimestamp = latestRevocationListTimestamp;
            this.latestGroupUpdateTimestamp = latestGroupUpdateTimestamp;
        }
    }

    public class KeycloakCallbackWrapper<T> implements KeycloakCallback<T> {
        private final BytesKey identityBytesKey;
        private final String oldAccessToken;
        private final KeycloakCallback<T> callback;

        public KeycloakCallbackWrapper(BytesKey identityBytesKey, KeycloakCallback<T> callback) {
            this.identityBytesKey = identityBytesKey;
            this.callback = callback;
            // capture the current value of the access token, so that at success time we can check whether it was refreshed and save it to engine
            KeycloakManagerState kms = ownedIdentityStates.get(identityBytesKey);
            if (kms != null && kms.authState != null) {
                this.oldAccessToken = kms.authState.getAccessToken();
            } else {
                this.oldAccessToken = null;
            }
        }

        @Override
        public void success(T result) {
            executor.execute(() -> {
                KeycloakManagerState kms = ownedIdentityStates.get(identityBytesKey);
                if (kms != null && kms.authState != null && !Objects.equals(kms.authState.getAccessToken(), oldAccessToken)) {
                    // access token was refreshed --> update authState in engine
                    try {
                        AppSingleton.getEngine().saveKeycloakAuthState(identityBytesKey.bytes, kms.authState.jsonSerializeString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // we successfully called an authenticated API point --> clear any authentication required notification
                AndroidNotificationManager.clearKeycloakAuthenticationRequiredNotification(identityBytesKey.bytes);
                authenticationRequiredOwnedIdentities.remove(identityBytesKey);

                // call the wrapped callback
                callback.success(result);
            });
        }

        @Override
        public void failed(int rfc) {
            executor.execute(() -> callback.failed(rfc));
        }
    }


    public interface KeycloakCallback<T> {
        void success(T result);
        void failed(int rfc);
    }
}
