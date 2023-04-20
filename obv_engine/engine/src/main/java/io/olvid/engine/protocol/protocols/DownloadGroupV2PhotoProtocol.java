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
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class DownloadGroupV2PhotoProtocol extends ConcreteProtocol {
    public DownloadGroupV2PhotoProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return DOWNLOAD_GROUPS_V2_PHOTO_PROTOCOL_ID;
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
        private final GroupV2.Identifier groupIdentifier;
        private final GroupV2.ServerPhotoInfo serverPhotoInfo;

        public DownloadingPhotoState(GroupV2.Identifier groupIdentifier, GroupV2.ServerPhotoInfo serverPhotoInfo) {
            super(DOWNLOADING_PHOTO_STATE_ID);
            this.groupIdentifier = groupIdentifier;
            this.serverPhotoInfo = serverPhotoInfo;
        }

        @SuppressWarnings("unused")
        public DownloadingPhotoState(Encoded encodedState) throws Exception {
            super(DOWNLOADING_PHOTO_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 2) {
                throw new Exception();
            }
            this.groupIdentifier= GroupV2.Identifier.of(list[0]);
            this.serverPhotoInfo= GroupV2.ServerPhotoInfo.of(list[1]);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    groupIdentifier.encode(),
                    serverPhotoInfo.encode(),
            });
        }
    }

    public static class PhotoDownloadedState extends ConcreteProtocolState {
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
        private final GroupV2.Identifier groupIdentifier;
        private final GroupV2.ServerPhotoInfo serverPhotoInfo;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, GroupV2.Identifier groupIdentifier, GroupV2.ServerPhotoInfo serverPhotoInfo) {
            super(coreProtocolMessage);
            this.groupIdentifier = groupIdentifier;
            this.serverPhotoInfo = serverPhotoInfo;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 2) {
                throw new Exception();
            }
            this.groupIdentifier = GroupV2.Identifier.of(list[0]);
            this.serverPhotoInfo = GroupV2.ServerPhotoInfo.of(list[1]);
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    groupIdentifier.encode(),
                    serverPhotoInfo.encode(),
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
            if ("".equals(this.photoPathToDelete)) {
                // if the photo was deleted from the server, the GetUserDataServerMethod return an empty String
                encryptedPhoto = null;
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
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public QueryServerStep(InitialProtocolState startState, InitialMessage receivedMessage, DownloadGroupV2PhotoProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public DownloadingPhotoState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            if (receivedMessage.groupIdentifier.category == GroupV2.Identifier.CATEGORY_KEYCLOAK) {
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createGetKeycloakDataQuery(receivedMessage.groupIdentifier.serverUrl, receivedMessage.serverPhotoInfo.serverPhotoLabel)));
                ChannelMessageToSend messageToSend = new ServerGetPhotoMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new DownloadingPhotoState(receivedMessage.groupIdentifier, receivedMessage.serverPhotoInfo);
            } else {
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(getOwnedIdentity(), ServerQuery.Type.createGetUserDataQuery(receivedMessage.serverPhotoInfo.serverPhotoIdentity, receivedMessage.serverPhotoInfo.serverPhotoLabel)));
                ChannelMessageToSend messageToSend = new ServerGetPhotoMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());

                return new DownloadingPhotoState(receivedMessage.groupIdentifier, receivedMessage.serverPhotoInfo);
            }
        }
    }

    public static class ProcessPhotoStep extends ProtocolStep {
        private final DownloadingPhotoState startState;
        private final ServerGetPhotoMessage receivedMessage;

        public ProcessPhotoStep(DownloadingPhotoState startState, ServerGetPhotoMessage receivedMessage, DownloadGroupV2PhotoProtocol protocol) throws Exception {
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

            byte[] decryptedPhoto = Suite.getAuthEnc(startState.serverPhotoInfo.serverPhotoKey).decrypt(startState.serverPhotoInfo.serverPhotoKey, receivedMessage.encryptedPhoto);

            protocolManagerSession.identityDelegate.setGroupV2DownloadedPhoto(protocolManagerSession.session, getOwnedIdentity(), startState.groupIdentifier, startState.serverPhotoInfo, decryptedPhoto);

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