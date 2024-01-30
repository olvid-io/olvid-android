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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class DownloadIdentityPhotoChildProtocol extends ConcreteProtocol {
    public DownloadIdentityPhotoChildProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return DOWNLOAD_IDENTITY_PHOTO_CHILD_PROTOCOL_ID;
    }





    // region States

    public static final int DOWNLOADING_PHOTO_STATE_ID = 1;
    public static final int PHOTO_DOWNLOADED_STATE_ID = 2;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{PHOTO_DOWNLOADED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case DOWNLOADING_PHOTO_STATE_ID:
                return DownloadingPhotoState.class;
            case PHOTO_DOWNLOADED_STATE_ID:
                return PhotoDownloadedState.class;
            default:
                return null;
        }
    }

    public static class DownloadingPhotoState extends ConcreteProtocolState {
        private final Identity contactIdentity;
        private final String jsonIdentityDetailsWithVersionAndPhoto;

        public DownloadingPhotoState(Encoded encodedState) throws Exception {
            super(DOWNLOADING_PHOTO_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            this.contactIdentity = list[0].decodeIdentity();
            this.jsonIdentityDetailsWithVersionAndPhoto = list[1].decodeString();
        }

        public DownloadingPhotoState(Identity contactIdentity, String jsonIdentityDetailsWithVersionAndPhoto) {
            super(DOWNLOADING_PHOTO_STATE_ID);
            this.contactIdentity = contactIdentity;
            this.jsonIdentityDetailsWithVersionAndPhoto = jsonIdentityDetailsWithVersionAndPhoto;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(jsonIdentityDetailsWithVersionAndPhoto),
            });
        }
    }

    public static class PhotoDownloadedState extends ConcreteProtocolState {
        public PhotoDownloadedState(Encoded encodedState) throws Exception{
            super(PHOTO_DOWNLOADED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public PhotoDownloadedState() {
            super(PHOTO_DOWNLOADED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }

    // endregion




    // region Messages

    public static final int INITIAL_MESSAGE_ID = 0;
    public static final int SERVER_GET_PHOTO_MESSAGE_ID = 1;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case SERVER_GET_PHOTO_MESSAGE_ID:
                return ServerGetPhotoMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        private final Identity contactIdentity;
        private final String jsonIdentityDetailsWithVersionAndPhoto;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, Identity contactIdentity, String jsonIdentityDetailsWithVersionAndPhoto) {
            super(coreProtocolMessage);
            this.contactIdentity = contactIdentity;
            this.jsonIdentityDetailsWithVersionAndPhoto = jsonIdentityDetailsWithVersionAndPhoto;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 2) {
                throw new Exception();
            }
            this.contactIdentity = receivedMessage.getInputs()[0].decodeIdentity();
            this.jsonIdentityDetailsWithVersionAndPhoto = receivedMessage.getInputs()[1].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(contactIdentity),
                    Encoded.of(jsonIdentityDetailsWithVersionAndPhoto),
            };
        }
    }

    public static class ServerGetPhotoMessage extends ConcreteProtocolMessage {
        private final EncryptedBytes encryptedPhoto;
        private final String photoPathToDelete;

        private ServerGetPhotoMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
            this.encryptedPhoto = null;
            this.photoPathToDelete = null;
        }

        public ServerGetPhotoMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() == null) {
                throw new Exception();
            }
            this.photoPathToDelete = receivedMessage.getEncodedResponse().decodeString();
            if (this.photoPathToDelete.equals("")) {
                // if the photo was deleted from the server, the GetUserDataServerMethod return an empty String
                this.encryptedPhoto = null;
            } else {
                try (FileInputStream fis = new FileInputStream(new File(receivedMessage.getProtocolManagerSession().engineBaseDirectory, this.photoPathToDelete))) {
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[32768];
                        int c;
                        while ((c = fis.read(buffer)) > 0) {
                            baos.write(buffer, 0, c);
                        }
                        this.encryptedPhoto = new EncryptedBytes(baos.toByteArray());
                    }
                }
            }
        }

        @Override
        public int getProtocolMessageId() {
            return SERVER_GET_PHOTO_MESSAGE_ID;
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
                return new Class[]{QueryServerStep.class};
            case DOWNLOADING_PHOTO_STATE_ID:
                return new Class[]{ProcessPhotoStep.class};
            case PHOTO_DOWNLOADED_STATE_ID:
            default:
                return new Class[0];
        }
    }


    public static class QueryServerStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public QueryServerStep(InitialProtocolState startState, InitialMessage receivedMessage, DownloadIdentityPhotoChildProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public DownloadingPhotoState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().readValue(receivedMessage.jsonIdentityDetailsWithVersionAndPhoto, JsonIdentityDetailsWithVersionAndPhoto.class);

            if (jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerLabel() == null || jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey() == null) {
                return null;
            }
            UID photoServerLabel = new UID(jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerLabel());

            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), new ServerQuery.GetUserDataQuery(receivedMessage.contactIdentity, photoServerLabel)));
            ChannelMessageToSend messageToSend = new ServerGetPhotoMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            return new DownloadingPhotoState(receivedMessage.contactIdentity, receivedMessage.jsonIdentityDetailsWithVersionAndPhoto);
        }
    }

    public static class ProcessPhotoStep extends ProtocolStep {
        private final DownloadingPhotoState startState;
        private final ServerGetPhotoMessage receivedMessage;

        public ProcessPhotoStep(DownloadingPhotoState startState, ServerGetPhotoMessage receivedMessage, DownloadIdentityPhotoChildProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.encryptedPhoto == null) {
                // photo was delete from the server
                return new PhotoDownloadedState();
            }
            JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().readValue(startState.jsonIdentityDetailsWithVersionAndPhoto, JsonIdentityDetailsWithVersionAndPhoto.class);

            AuthEncKey key = (AuthEncKey) new Encoded(jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey()).decodeSymmetricKey();
            byte[] decryptedPhoto = Suite.getAuthEnc(key).decrypt(key, receivedMessage.encryptedPhoto);

            // check whether you downloaded your own photo or a contact photo
            if (startState.contactIdentity.equals(getOwnedIdentity())) {
                protocolManagerSession.identityDelegate.setOwnedDetailsDownloadedPhoto(protocolManagerSession.session, getOwnedIdentity(), jsonIdentityDetailsWithVersionAndPhoto.getVersion(), decryptedPhoto);
            } else {
                protocolManagerSession.identityDelegate.setContactDetailsDownloadedPhoto(protocolManagerSession.session, startState.contactIdentity, getOwnedIdentity(), jsonIdentityDetailsWithVersionAndPhoto.getVersion(), decryptedPhoto);
            }
            try {
                //noinspection ResultOfMethodCallIgnored
                new File(protocolManagerSession.engineBaseDirectory, receivedMessage.photoPathToDelete).delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new PhotoDownloadedState();
        }
    }

    // endregion
}