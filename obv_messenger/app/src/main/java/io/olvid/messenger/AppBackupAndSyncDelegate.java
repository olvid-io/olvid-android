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

package io.olvid.messenger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvPushNotificationType;
import io.olvid.engine.engine.types.SimpleEngineNotificationListener;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.engine.types.sync.ObvProfileBackupSnapshot;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.entity.backups.AppDeviceSnapshot;
import io.olvid.messenger.databases.entity.sync.AppSyncSnapshot;
import io.olvid.messenger.databases.tasks.OwnedDevicesSynchronisationWithEngineTask;
import io.olvid.messenger.openid.KeycloakManager;

public class AppBackupAndSyncDelegate implements ObvBackupAndSyncDelegate {
    EngineNotificationListener engineNotificationListener;
    @Override
    public String getTag() {
        return "app";
    }

    @Override
    public ObvSyncSnapshotNode getSyncSnapshot(Identity ownedIdentity) {
        return AppSyncSnapshot.of(AppDatabase.getInstance(), ownedIdentity.getBytes());
    }


    @Override
    public RestoreFinishedCallback restoreOwnedIdentity(ObvIdentity obvOwnedIdentity, ObvSyncSnapshotNode node) throws Exception {
        if (!(node instanceof AppSyncSnapshot)) {
            throw new Exception();
        }

        if (!Arrays.equals(((AppSyncSnapshot) node).owned_identity, obvOwnedIdentity.getBytesIdentity())) {
            Logger.e("Trying to restoreOwnedIdentity for the wrong owned identity");
            throw new Exception();
        }

        // actually create the App-side owned identity
        OwnedIdentity ownedIdentity = new OwnedIdentity(obvOwnedIdentity, OwnedIdentity.API_KEY_STATUS_UNKNOWN);
        ownedIdentity.capabilityWebrtcContinuousIce = ObvCapability.currentCapabilities.contains(ObvCapability.WEBRTC_CONTINUOUS_ICE);
        ownedIdentity.capabilityOneToOneContacts = ObvCapability.currentCapabilities.contains(ObvCapability.ONE_TO_ONE_CONTACTS);
        ownedIdentity.capabilityGroupsV2 = ObvCapability.currentCapabilities.contains(ObvCapability.GROUPS_V2);
        AppDatabase.getInstance().ownedIdentityDao().insert(ownedIdentity);


        return new RestoreFinishedCallback() {
            @Override
            public void onRestoreSuccess() {
                // launch in another thread as the engine transaction is still committing when this is called
                App.runThread(() -> {
                    // sleep a bit to let the engine call all onCommit callbacks before sending it some requests
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) { }

                    // mark the owned identity as transferred so we do not get untrusted device notification
                    OwnedDevicesSynchronisationWithEngineTask.Companion.ownedIdentityWasTransferredOrRestored(obvOwnedIdentity.getBytesIdentity());
                    // synchronize devices with engine
                    new OwnedDevicesSynchronisationWithEngineTask(obvOwnedIdentity.getBytesIdentity()).run();

                    // register to push topics
                    try {
                        final String token = AppSingleton.retrieveFirebaseToken();
                        AppSingleton.getEngine().registerToPushNotification(obvOwnedIdentity.getBytesIdentity(), ObvPushNotificationType.createAndroid(token), false, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // register for keycloak
                    if (obvOwnedIdentity.isKeycloakManaged()) {
                        try {
                            ObvKeycloakState keycloakState = AppSingleton.getEngine().getOwnedIdentityKeycloakState(obvOwnedIdentity.getBytesIdentity());
                            if (keycloakState != null) {
                                KeycloakManager.registerKeycloakManagedIdentity(
                                        obvOwnedIdentity,
                                        keycloakState.keycloakServer,
                                        keycloakState.clientId,
                                        keycloakState.clientSecret,
                                        keycloakState.jwks,
                                        keycloakState.signatureKey,
                                        keycloakState.serializedAuthState,
                                        keycloakState.transferRestricted,
                                        null,
                                        0,
                                        0,
                                        false
                                );
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            @Override
            public void onRestoreFailure() {
                // if the restore fails, delete the OwnedIdentity we just created
                OwnedIdentity ownedIdentityObject = AppDatabase.getInstance().ownedIdentityDao().get(obvOwnedIdentity.getBytesIdentity());
                if (ownedIdentityObject != null) {
                    AppDatabase.getInstance().ownedIdentityDao().delete(ownedIdentityObject);
                }
            }
        };
    }

    @Override
    public RestoreFinishedCallback restoreSyncSnapshot(ObvSyncSnapshotNode node) throws Exception {
        if (!(node instanceof AppSyncSnapshot)) {
            throw new Exception();
        }

        // create a notification listener for engine snapshot finished
        // This listener is stored as a class member to keep a reference to it
        // Just in case, unregister any existing listener that could mess up our snapshot restore!
        if (engineNotificationListener != null) {
            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.ENGINE_SNAPSHOT_RESTORATION_FINISHED, engineNotificationListener);
        }

        engineNotificationListener = new SimpleEngineNotificationListener(EngineNotifications.ENGINE_SNAPSHOT_RESTORATION_FINISHED) {
            @Override
            public void callback(HashMap<String, Object> userInfo) {
                AppSingleton.getEngine().removeNotificationListener(EngineNotifications.ENGINE_SNAPSHOT_RESTORATION_FINISHED, this);
                engineNotificationListener = null;
                try {
                    ((AppSyncSnapshot) node).restore(AppDatabase.getInstance());

                    // select the new identity so it will be opened when the onboarding is over
                    AppSingleton.getInstance().selectIdentity(((AppSyncSnapshot) node).owned_identity, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };


        return new RestoreFinishedCallback() {
            @Override
            public void onRestoreSuccess() {
                // register the listener
                AppSingleton.getEngine().addNotificationListener(EngineNotifications.ENGINE_SNAPSHOT_RESTORATION_FINISHED, engineNotificationListener);
            }

            @Override
            public void onRestoreFailure() {
                // nothing to rollback, we simply need to clear our listener
                engineNotificationListener = null;
            }
        };
    }

    @Override
    public byte[] serialize(SerializationContext serializationContext, ObvSyncSnapshotNode snapshotNode) throws Exception {
        switch (serializationContext) {
            case DEVICE:
                if (!(snapshotNode instanceof AppDeviceSnapshot)) {
                    throw new Exception("AppBackupDelegate can only serialize AppDeviceSnapshot");
                }
                break;
            case PROFILE:
                if (!(snapshotNode instanceof AppSyncSnapshot)) {
                    throw new Exception("AppBackupDelegate can only serialize AppSyncSnapshot");
                }
                break;
        }

        return AppSingleton.getJsonObjectMapper().writeValueAsBytes(snapshotNode);
    }

    @Override
    public ObvSyncSnapshotNode deserialize(SerializationContext serializationContext, byte[] serializedSnapshotNode) throws Exception {
        switch (serializationContext) {
            case DEVICE:
                return AppSingleton.getJsonObjectMapper().readValue(serializedSnapshotNode, AppDeviceSnapshot.class);
            case PROFILE:
            default:
                return AppSingleton.getJsonObjectMapper().readValue(serializedSnapshotNode, AppSyncSnapshot.class);
        }
    }

    @Override
    public ObvSyncSnapshotNode getDeviceSnapshot() {
        return AppDeviceSnapshot.of(AppDatabase.getInstance());
    }

    @Override
    public Map<String, String> getAdditionalProfileInfo(Identity ownedIdentity) {
        return Map.of(ObvProfileBackupSnapshot.INFO_PLATFORM, "android");
    }
}
