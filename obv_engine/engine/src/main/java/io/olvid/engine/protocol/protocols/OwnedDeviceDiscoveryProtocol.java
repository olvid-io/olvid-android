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

package io.olvid.engine.protocol.protocols;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.PreKeyBlobOnServer;
import io.olvid.engine.datatypes.containers.EncodedOwnedPreKey;
import io.olvid.engine.datatypes.containers.OwnedDeviceAndPreKey;
import io.olvid.engine.datatypes.containers.PreKey;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.identities.ObvOwnedDevice;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.EmptyProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class OwnedDeviceDiscoveryProtocol extends ConcreteProtocol {
    public OwnedDeviceDiscoveryProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return OWNED_DEVICE_DISCOVERY_PROTOCOL_ID;
    }

    // region States

    public static final int REQUEST_SENT_STATE_ID = 1;
    public static final int FINISHED_STATE_ID = 2;
    public static final int CANCELLED_STATE_ID = 3;
    public static final int UPLOADING_PRE_KEY_STATE_ID = 4;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{CANCELLED_STATE_ID, FINISHED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case REQUEST_SENT_STATE_ID:
                return RequestSentState.class;
            case FINISHED_STATE_ID:
                return FinishedState.class;
            case UPLOADING_PRE_KEY_STATE_ID:
                return UploadingPreKeyState.class;
            case CANCELLED_STATE_ID:
                return CancelledState.class;
            default:
                return null;
        }
    }

    public static class RequestSentState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public RequestSentState(Encoded encodedState) throws Exception {
            super(REQUEST_SENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public RequestSentState() {
            super(REQUEST_SENT_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    public static class FinishedState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public FinishedState(Encoded encodedState) throws Exception {
            super(FINISHED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public FinishedState() {
            super(FINISHED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    public static class UploadingPreKeyState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public UploadingPreKeyState(Encoded encodedState) throws Exception {
            super(UPLOADING_PRE_KEY_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public UploadingPreKeyState() {
            super(UPLOADING_PRE_KEY_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    public static class CancelledState extends ConcreteProtocolState {
        @SuppressWarnings("unused")
        public CancelledState(Encoded encodedState) throws Exception {
            super(CANCELLED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public CancelledState() {
            super(CANCELLED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    // endregion



    // region Messages

    public static final int INITIAL_MESSAGE_ID = 0;
    public static final int SERVER_QUERY_MESSAGE_ID = 1;
    public static final int TRIGGER_OWNED_DEVICE_DISCOVERY_MESSAGE_ID = 2;
    public static final int UPLOAD_PRE_KEY_MESSAGE_ID = 3;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case SERVER_QUERY_MESSAGE_ID:
                return ServerQueryMessage.class;
            case TRIGGER_OWNED_DEVICE_DISCOVERY_MESSAGE_ID:
                return TriggerOwnedDeviceDiscoveryMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        public InitialMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 0) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }


    public static class ServerQueryMessage extends ConcreteProtocolMessage {
        private final EncryptedBytes encryptedOwnedDeviceList;

        public ServerQueryMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            encryptedOwnedDeviceList = null;
        }

        @SuppressWarnings("unused")
        public ServerQueryMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            encryptedOwnedDeviceList = receivedMessage.getEncodedResponse().decodeEncryptedData();
        }

        @Override
        public int getProtocolMessageId() {
            return SERVER_QUERY_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    public static class TriggerOwnedDeviceDiscoveryMessage extends EmptyProtocolMessage {
        public TriggerOwnedDeviceDiscoveryMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        public TriggerOwnedDeviceDiscoveryMessage(ReceivedMessage receivedMessage) throws Exception {
            super(receivedMessage);
        }

        @Override
        public int getProtocolMessageId() {
            return TRIGGER_OWNED_DEVICE_DISCOVERY_MESSAGE_ID;
        }
    }

    public static class UploadPreKeyMessage extends ConcreteProtocolMessage {
        public UploadPreKeyMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        @SuppressWarnings("unused")
        public UploadPreKeyMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
        }

        @Override
        public int getProtocolMessageId() {
            return UPLOAD_PRE_KEY_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    // endregion





    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{SendRequestStep.class};
            case REQUEST_SENT_STATE_ID:
                return new Class[]{ProcessResponseStateStep.class};
            case UPLOADING_PRE_KEY_STATE_ID:
                return new Class[]{PreKeyUploadedStep.class};
            case FINISHED_STATE_ID:
            case CANCELLED_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class SendRequestStep extends ProtocolStep {
        private final InitialProtocolState startState;

        public SendRequestStep(InitialProtocolState startState, InitialMessage receivedMessage, OwnedDeviceDiscoveryProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
        }

        public SendRequestStep(InitialProtocolState startState, TriggerOwnedDeviceDiscoveryMessage receivedMessage, OwnedDeviceDiscoveryProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelOrPreKeyWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.OwnedDeviceDiscoveryQuery(getOwnedIdentity())));
            ChannelMessageToSend messageToSend = new ServerQueryMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

            return new RequestSentState();
        }
    }


    public static class ProcessResponseStateStep extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final RequestSentState startState;
        private final ServerQueryMessage receivedMessage;

        public ProcessResponseStateStep(RequestSentState startState, ServerQueryMessage receivedMessage, OwnedDeviceDiscoveryProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // decrypt the received device list
            byte[] decryptedPayload = protocolManagerSession.encryptionForIdentityDelegate.decrypt(protocolManagerSession.session, receivedMessage.encryptedOwnedDeviceList, getOwnedIdentity());
            if (decryptedPayload == null) {
                Logger.w("Unable to DECRYPT received OwnedDeviceDiscoveryProtocol payload (or expired query)!");
                return new CancelledState();
            }

            // we ignore the multi-device boolean received from the server --> it is only used when querying outside the protocol
            HashMap<UID, SignedPreKeyAndServerInfo> serverOwnedDevices = new HashMap<>();
            Long serverTimestamp = null;
            try {
                HashMap<DictionaryKey, Encoded> map = new Encoded(decryptedPayload).decodeDictionary();

                Encoded encodedTimestamp = map.get(new DictionaryKey("st"));
                if (encodedTimestamp != null) {
                    serverTimestamp = encodedTimestamp.decodeLong();
                }

                Encoded[] encodedDevices = map.get(new DictionaryKey("dev")).decodeList();
                for (Encoded encodedDevice : encodedDevices) {
                    HashMap<DictionaryKey, Encoded> deviceMap = encodedDevice.decodeDictionary();
                    UID deviceUid = deviceMap.get(new DictionaryKey("uid")).decodeUid();

                    Encoded encodedExpiration = deviceMap.get(new DictionaryKey("exp"));
                    Long expirationTimestamp = encodedExpiration == null ? null : encodedExpiration.decodeLong();

                    Encoded encodedRegistration = deviceMap.get(new DictionaryKey("reg"));
                    Long lastRegistrationTimestamp = encodedRegistration == null ? null : encodedRegistration.decodeLong();

                    Encoded encodedName = deviceMap.get(new DictionaryKey("name"));
                    String deviceName = null;
                    if (encodedName != null) {
                        try {
                            byte[] plaintext = protocolManagerSession.encryptionForIdentityDelegate.decrypt(protocolManagerSession.session, encodedName.decodeEncryptedData(), getOwnedIdentity());
                            byte[] bytesDeviceName = new Encoded(plaintext).decodeListWithPadding()[0].decodeBytes();
                            if (bytesDeviceName.length != 0) {
                                deviceName = new String(bytesDeviceName, StandardCharsets.UTF_8);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    Encoded encodedSignedPreKey = deviceMap.get(new DictionaryKey("prk"));

                    serverOwnedDevices.put(deviceUid, new SignedPreKeyAndServerInfo(
                            encodedSignedPreKey,
                            new ObvOwnedDevice.ServerDeviceInfo(deviceName, expirationTimestamp, lastRegistrationTimestamp)
                    ));
                }
            } catch (Exception e) {
                Logger.w("Unable to DECODE received OwnedDeviceDiscoveryProtocol payload!");
                return new CancelledState();
            }

            List<OwnedDeviceAndPreKey> oldOwnedDevices = protocolManagerSession.identityDelegate.getDevicesAndPreKeysOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            UID currentDeviceUid = null;
            PreKey currentDevicePreKeyOnServer = null;

            for (OwnedDeviceAndPreKey oldDevice : oldOwnedDevices) {
                SignedPreKeyAndServerInfo signedPreKeyAndServerInfo = serverOwnedDevices.remove(oldDevice.deviceUid);
                if (signedPreKeyAndServerInfo == null) {
                    // device was removed from the server
                    if (oldDevice.currentDevice) {
                        currentDeviceUid = oldDevice.deviceUid;
                        // our current device was removed! Do not deactivate it yet, but force a registerPushNotification so it gets deactivated if it should be
                        protocolManagerSession.pushNotificationDelegate.forceRegisterPushNotification(getOwnedIdentity(), true);
                    } else {
                        // a deviceUid was removed --> delete the channel and the deviceUid
                        protocolManagerSession.channelDelegate.deleteObliviousChannelIfItExists(protocolManagerSession.session, getOwnedIdentity(), oldDevice.deviceUid, getOwnedIdentity());
                        protocolManagerSession.identityDelegate.removeDeviceForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), oldDevice.deviceUid);
                    }
                } else {
                    if (oldDevice.currentDevice) {
                        currentDeviceUid = oldDevice.deviceUid;
                        if (signedPreKeyAndServerInfo.encodedSignedPreKey != null) {
                            PreKeyBlobOnServer preKeyBlob = PreKeyBlobOnServer.verifySignatureAndDecode(signedPreKeyAndServerInfo.encodedSignedPreKey, getOwnedIdentity(), oldDevice.deviceUid, serverTimestamp);
                            currentDevicePreKeyOnServer = preKeyBlob == null ? null : preKeyBlob.preKey;
                        } else {
                            currentDevicePreKeyOnServer = null;
                        }
                        continue;
                    }

                    final PreKeyBlobOnServer newPreKeyBlob;
                    final boolean preKeyChanged;

                    if (signedPreKeyAndServerInfo.encodedSignedPreKey != null) {
                        // there is a preKey on the server, check if it changed and has a valid signature
                        PreKeyBlobOnServer preKeyBlob = PreKeyBlobOnServer.verifySignatureAndDecode(signedPreKeyAndServerInfo.encodedSignedPreKey, getOwnedIdentity(), oldDevice.deviceUid, serverTimestamp);
                        if (preKeyBlob != null &&
                                (oldDevice.preKey == null || (!preKeyBlob.preKey.keyId.equals(oldDevice.preKey.keyId) && oldDevice.preKey.expirationTimestamp < preKeyBlob.preKey.expirationTimestamp))) {
                            newPreKeyBlob = preKeyBlob;
                            preKeyChanged = true;
                        } else {
                            newPreKeyBlob = new PreKeyBlobOnServer(oldDevice.preKey, null);
                            preKeyChanged = false;
                        }
                    } else if (oldDevice.preKey != null) {
                        // the preKey was removed!
                        Logger.w("A preKey was removed from the server, this should never happen...");
                        newPreKeyBlob = null;
                        preKeyChanged = true;
                    } else {
                        newPreKeyBlob = null;
                        preKeyChanged = false;
                    }


                    // the device exists both locally and on the server --> check what has changed
                    if (preKeyChanged || !Objects.equals(oldDevice.serverDeviceInfo, signedPreKeyAndServerInfo.serverDeviceInfo)) {
                        protocolManagerSession.identityDelegate.updateOwnedDevice(protocolManagerSession.session, getOwnedIdentity(), oldDevice.deviceUid, signedPreKeyAndServerInfo.serverDeviceInfo.displayName, signedPreKeyAndServerInfo.serverDeviceInfo.expirationTimestamp, signedPreKeyAndServerInfo.serverDeviceInfo.lastRegistrationTimestamp, newPreKeyBlob);
                    }
                }
            }

            // now create all new server devices locally
            for (Map.Entry<UID, SignedPreKeyAndServerInfo> entry : serverOwnedDevices.entrySet()) {
                ObvOwnedDevice.ServerDeviceInfo serverDeviceInfo = entry.getValue().serverDeviceInfo;
                PreKeyBlobOnServer preKeyBlob = entry.getValue().encodedSignedPreKey == null ? null : PreKeyBlobOnServer.verifySignatureAndDecode(entry.getValue().encodedSignedPreKey, getOwnedIdentity(), entry.getKey(), serverTimestamp);

                protocolManagerSession.identityDelegate.addDeviceForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity(), entry.getKey(), serverDeviceInfo.displayName, serverDeviceInfo.expirationTimestamp, serverDeviceInfo.lastRegistrationTimestamp, preKeyBlob, false);
            }

            if (serverTimestamp != null) {
                {
                    // delete expired pre keys (for our server)
                    protocolManagerSession.identityDelegate.expireContactAndOwnedPreKeys(protocolManagerSession.session, getOwnedIdentity(), getOwnedIdentity().getServer(), serverTimestamp);
                }

                {
                    final boolean generatePreKey;
                    final boolean uploadLatestPreKey;
                    EncodedOwnedPreKey latestPreKey = protocolManagerSession.identityDelegate.getLatestPreKeyForOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
                    boolean latestPreKeyIsValid = latestPreKey != null && latestPreKey.expirationTimestamp > (serverTimestamp + Constants.PRE_KEY_VALIDITY_DURATION - Constants.PRE_KEY_RENEWAL_INTERVAL);
                    if (currentDevicePreKeyOnServer != null) {
                        if (latestPreKeyIsValid) {
                            if (Objects.equals(latestPreKey.keyId, currentDevicePreKeyOnServer.keyId)) {
                                // our latest key is already on the server --> do nothing
                                generatePreKey = false;
                                uploadLatestPreKey = false;
                            } else {
                                // a different key is on the server --> do something!
                                if (currentDevicePreKeyOnServer.expirationTimestamp < latestPreKey.expirationTimestamp) {
                                    // our latest key is more recent --> upload it
                                    generatePreKey = false;
                                    uploadLatestPreKey = true;
                                } else {
                                    // our latest key is older, this should never happen! --> generate a new one
                                    Logger.e("Found an unknown newer PreKey on the server!");
                                    generatePreKey = true;
                                    uploadLatestPreKey = false;
                                }
                            }
                        } else {
                            // our local key is too old --> generate a new one
                            generatePreKey = true;
                            uploadLatestPreKey = false;
                        }
                    } else {
                        // there is no pre key on the server
                        if (latestPreKeyIsValid) {
                            // we have a suitable one --> upload it
                            generatePreKey = false;
                            uploadLatestPreKey = true;
                        } else {
                            // our local key is too old --> generate a new one
                            generatePreKey = true;
                            uploadLatestPreKey = false;
                        }
                    }

                    final byte[] encodedPreKeyToUpload;

                    if (generatePreKey) {
                        Encoded encodedNewPreKey = protocolManagerSession.identityDelegate.generateNewPreKey(protocolManagerSession.session, getOwnedIdentity(), serverTimestamp + Constants.PRE_KEY_VALIDITY_DURATION);
                        if (encodedNewPreKey != null) {
                            encodedPreKeyToUpload = encodedNewPreKey.getBytes();
                        } else {
                            encodedPreKeyToUpload = null;
                        }
                    } else if (uploadLatestPreKey) {
                        encodedPreKeyToUpload = latestPreKey.encodedSignedPreKey.getBytes();
                    } else {
                        encodedPreKeyToUpload = null;
                    }

                    if (encodedPreKeyToUpload != null && currentDeviceUid != null) {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.UploadPreKeyQuery(getOwnedIdentity(), currentDeviceUid, encodedPreKeyToUpload)));
                        ChannelMessageToSend messageToSend = new UploadPreKeyMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                        return new UploadingPreKeyState();
                    }
                }
            }

            return new FinishedState();
        }

        private static class SignedPreKeyAndServerInfo {
            public final Encoded encodedSignedPreKey;
            public final ObvOwnedDevice.ServerDeviceInfo serverDeviceInfo;

            public SignedPreKeyAndServerInfo(Encoded encodedSignedPreKey, ObvOwnedDevice.ServerDeviceInfo serverDeviceInfo) {
                this.encodedSignedPreKey = encodedSignedPreKey;
                this.serverDeviceInfo = serverDeviceInfo;
            }
        }
    }





    public static class PreKeyUploadedStep extends ProtocolStep {
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final RequestSentState startState;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final UploadPreKeyMessage receivedMessage;

        public PreKeyUploadedStep(RequestSentState startState, UploadPreKeyMessage receivedMessage, OwnedDeviceDiscoveryProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            return new FinishedState();
        }
    }



    // endregion
}
