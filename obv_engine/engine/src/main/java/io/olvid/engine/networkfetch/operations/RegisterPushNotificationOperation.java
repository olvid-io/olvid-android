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

package io.olvid.engine.networkfetch.operations;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.PushNotificationTypeAndParameters;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.PushNotificationConfiguration;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;


public class RegisterPushNotificationOperation extends Operation {
    // possible reasons for cancel
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_INVALID_SERVER_SESSION = 2;
    public static final int RFC_ANOTHER_DEVICE_IS_ALREADY_REGISTERED = 3;
    public static final int RFC_PUSH_NOTIFICATION_CONFIGURATION_NOT_FOUND = 4;
    public static final int RFC_DEVICE_UID_TO_REPLACE_NOT_FOUND = 5;


    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private UID deviceUid; // will be set during execution

    public RegisterPushNotificationOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, Operation.OnFinishCallback onFinishCallback, Operation.OnCancelCallback onCancelCallback) {
        super(ownedIdentity.computeUniqueUid(), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.deviceUid = null; // will be set during execution
    }


    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getDeviceUid() {
        return deviceUid;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                byte[] serverSessionToken = ServerSession.getToken(fetchManagerSession, ownedIdentity);
                if (serverSessionToken == null) {
                    cancel(RFC_INVALID_SERVER_SESSION);
                    return;
                }
                if (cancelWasRequested()) {
                    return;
                }
                PushNotificationConfiguration pushNotificationConfiguration = PushNotificationConfiguration.get(fetchManagerSession, ownedIdentity);
                if (pushNotificationConfiguration == null) {
                    cancel(RFC_PUSH_NOTIFICATION_CONFIGURATION_NOT_FOUND);
                    return;
                }
                this.deviceUid = pushNotificationConfiguration.getDeviceUid();

                String deviceName = fetchManagerSession.identityDelegate.getCurrentDeviceDisplayName(fetchManagerSession.session, ownedIdentity);
                byte[] encodedDeviceName = Encoded.of(new Encoded[]{
                        Encoded.of(deviceName == null ? new byte[0] : deviceName.getBytes(StandardCharsets.UTF_8))
                }).getBytes();

                byte[] plaintext = new byte[((encodedDeviceName.length - 1) | 127) + 1];
                System.arraycopy(encodedDeviceName, 0, plaintext, 0, encodedDeviceName.length);

                EncryptedBytes encryptedDeviceNameForFirstRegistration = Suite.getPublicKeyEncryption(ownedIdentity.getEncryptionPublicKey()).encrypt(
                        ownedIdentity.getEncryptionPublicKey(),
                        plaintext,
                        Suite.getDefaultPRNGService(0));

                List<String> keycloakPushTopics = fetchManagerSession.identityDelegate.getKeycloakPushTopics(fetchManagerSession.session, ownedIdentity);
                RegisterPushNotificationServerMethod serverMethod = new RegisterPushNotificationServerMethod(
                        ownedIdentity,
                        serverSessionToken,
                        pushNotificationConfiguration.getDeviceUid(),
                        pushNotificationConfiguration.getPushNotificationTypeAndParameters(),
                        keycloakPushTopics,
                        encryptedDeviceNameForFirstRegistration
                );
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(fetchManagerSession.identityDelegate.isActiveOwnedIdentity(fetchManagerSession.session, ownedIdentity));

                fetchManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        fetchManagerSession.identityDelegate.reactivateOwnedIdentityIfNeeded(fetchManagerSession.session, ownedIdentity);
                        if (pushNotificationConfiguration.shouldReactivateCurrentDevice()) {
                            pushNotificationConfiguration.clearKickOtherDevices();
                        }
                        finished = true;
                        return;
                    case ServerMethod.INVALID_SESSION:
                        ServerSession.deleteCurrentTokenIfEqualTo(fetchManagerSession, serverSessionToken, ownedIdentity);
                        fetchManagerSession.session.commit();
                        cancel(RFC_INVALID_SERVER_SESSION);
                        return;
                    case ServerMethod.ANOTHER_DEVICE_IS_ALREADY_REGISTERED:
                        fetchManagerSession.identityDelegate.deactivateOwnedIdentity(fetchManagerSession.session, ownedIdentity);
                        fetchManagerSession.session.commit();
                        cancel(RFC_ANOTHER_DEVICE_IS_ALREADY_REGISTERED);
                        return;
                    case ServerMethod.DEVICE_IS_NOT_REGISTERED:
                        // this only happens when configuration was set to reactivate curent device, but the provided deviceUidToReplace is invalid/inactive
                        if (pushNotificationConfiguration.shouldReactivateCurrentDevice()) {
                            pushNotificationConfiguration.clearKickOtherDevices();
                            fetchManagerSession.session.commit();
                        }
                        cancel(RFC_DEVICE_UID_TO_REPLACE_NOT_FOUND);
                        return;
                    default:
                        cancel(RFC_NETWORK_ERROR);
                        return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                fetchManagerSession.session.rollback();
            } finally {
                if (finished) {
                    fetchManagerSession.session.commit();
                    setFinished();
                } else {
                    if (hasNoReasonForCancel()) {
                        cancel(null);
                    }
                    processCancel();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}


class RegisterPushNotificationServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/registerPushNotification";

    private final String server;
    private final Identity ownedIdentity;
    private final byte[] token;
    private final UID deviceUid;
    private final PushNotificationTypeAndParameters pushNotificationTypeAndParameters;
    private final String[] keycloakPushTopics;
    private final EncryptedBytes encryptedDeviceNameForFirstRegistration;

    RegisterPushNotificationServerMethod(Identity ownedIdentity, byte[] token, UID deviceUid, PushNotificationTypeAndParameters pushNotificationTypeAndParameters, List<String> keycloakPushTopics, EncryptedBytes encryptedDeviceNameForFirstRegistration) {
        this.server = ownedIdentity.getServer();
        this.ownedIdentity = ownedIdentity;
        this.token = token;
        this.deviceUid = deviceUid;
        this.pushNotificationTypeAndParameters = pushNotificationTypeAndParameters;
        this.keycloakPushTopics = keycloakPushTopics == null ? new String[0] : keycloakPushTopics.toArray(new String[0]);
        this.encryptedDeviceNameForFirstRegistration = encryptedDeviceNameForFirstRegistration;
    }

    @Override
    protected String getServer() {
        return server;
    }

    @Override
    protected String getServerMethod() {
        return SERVER_METHOD_PATH;
    }

    @Override
    protected byte[] getDataToSend() {
        Encoded extraInfo;
        switch (pushNotificationTypeAndParameters.pushNotificationType) {
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_ANDROID: {
                extraInfo = Encoded.of(new Encoded[]{
                        Encoded.of(pushNotificationTypeAndParameters.token),
                        Encoded.of(pushNotificationTypeAndParameters.identityMaskingUid),
                });
                break;
            }
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_ANDROID:
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_WINDOWS:
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_LINUX:
            case PushNotificationTypeAndParameters.PUSH_NOTIFICATION_TYPE_WEBSOCKET_DAEMON:
            default: {
                extraInfo = Encoded.of(new Encoded[0]);
                break;
            }
        }

        if (pushNotificationTypeAndParameters.deviceUidToReplace != null) {
            return Encoded.of(new Encoded[]{
                    Encoded.of(ownedIdentity),
                    Encoded.of(token),
                    Encoded.of(deviceUid),
                    Encoded.of(new byte[]{pushNotificationTypeAndParameters.pushNotificationType}),
                    extraInfo,
                    Encoded.of(pushNotificationTypeAndParameters.reactivateCurrentDevice),
                    Encoded.of(keycloakPushTopics),
                    Encoded.of(encryptedDeviceNameForFirstRegistration),
                    Encoded.of(pushNotificationTypeAndParameters.deviceUidToReplace),
            }).getBytes();
        } else {
            return Encoded.of(new Encoded[]{
                    Encoded.of(ownedIdentity),
                    Encoded.of(token),
                    Encoded.of(deviceUid),
                    Encoded.of(new byte[]{pushNotificationTypeAndParameters.pushNotificationType}),
                    extraInfo,
                    Encoded.of(pushNotificationTypeAndParameters.reactivateCurrentDevice),
                    Encoded.of(keycloakPushTopics),
                    Encoded.of(encryptedDeviceNameForFirstRegistration),
            }).getBytes();
        }
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        // Nothing to parse here
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return false;
    }
}