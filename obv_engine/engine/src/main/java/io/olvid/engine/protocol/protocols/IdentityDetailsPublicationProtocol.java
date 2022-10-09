/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

import java.util.Arrays;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
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

public class IdentityDetailsPublicationProtocol extends ConcreteProtocol {
    public IdentityDetailsPublicationProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return IDENTITY_DETAILS_PUBLICATION_PROTOCOL_ID;
    }





    // region States

    public static final int UPLOADING_PHOTO_STATE_ID = 1;
    public static final int DETAILS_SENT_STATE_ID = 2;
    public static final int DETAILS_RECEIVED_STATE_ID = 3;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{DETAILS_SENT_STATE_ID, DETAILS_RECEIVED_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
            case UPLOADING_PHOTO_STATE_ID:
                return UploadingPhotoState.class;
            case DETAILS_SENT_STATE_ID:
                return DetailsSentState.class;
            case DETAILS_RECEIVED_STATE_ID:
                return DetailsReceivedState.class;
            default:
                return null;
        }
    }

    public static class UploadingPhotoState extends ConcreteProtocolState {
        private final String jsonIdentityDetailsWithVersionAndPhoto;

        public UploadingPhotoState(Encoded encodedState) throws Exception {
            super(UPLOADING_PHOTO_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 1) {
                throw new Exception();
            }
            this.jsonIdentityDetailsWithVersionAndPhoto = list[0].decodeString();
        }

        public UploadingPhotoState(String jsonIdentityDetailsWithVersionAndPhoto) {
            super(UPLOADING_PHOTO_STATE_ID);
            this.jsonIdentityDetailsWithVersionAndPhoto = jsonIdentityDetailsWithVersionAndPhoto;
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    Encoded.of(jsonIdentityDetailsWithVersionAndPhoto),
            });
        }
    }

    public static class DetailsSentState extends ConcreteProtocolState {
        public DetailsSentState(Encoded encodedState) throws Exception{
            super(DETAILS_SENT_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public DetailsSentState() {
            super(DETAILS_SENT_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }

    }

    public static class DetailsReceivedState extends ConcreteProtocolState {
        public DetailsReceivedState(Encoded encodedState) throws Exception{
            super(DETAILS_RECEIVED_STATE_ID);
            Encoded[] list = encodedState.decodeList();
            if (list.length != 0) {
                throw new Exception();
            }
        }

        public DetailsReceivedState() {
            super(DETAILS_RECEIVED_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }


    // endregion




    // region Messages

    public static final int INITIAL_MESSAGE_ID = 0;
    public static final int SERVER_PUT_PHOTO_MESSAGE_ID = 1;
    public static final int SEND_DETAILS_MESSAGE_ID = 2;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIAL_MESSAGE_ID:
                return InitialMessage.class;
            case SERVER_PUT_PHOTO_MESSAGE_ID:
                return ServerPutPhotoMessage.class;
            case SEND_DETAILS_MESSAGE_ID:
                return SendDetailsMessage.class;
            default:
                return null;
        }
    }

    public static class InitialMessage extends ConcreteProtocolMessage {
        private final int version;

        public InitialMessage(CoreProtocolMessage coreProtocolMessage, int version) {
            super(coreProtocolMessage);
            this.version = version;
        }

        public InitialMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.version = (int) receivedMessage.getInputs()[0].decodeLong();
        }

        @Override
        public int getProtocolMessageId() {
            return INITIAL_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(version),
            };
        }
    }

    public static class ServerPutPhotoMessage extends ConcreteProtocolMessage {
        private ServerPutPhotoMessage(CoreProtocolMessage coreProtocolMessage) {
            super(coreProtocolMessage);
        }

        public ServerPutPhotoMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getEncodedResponse() != null) {
                throw new Exception();
            }
        }

        @Override
        public int getProtocolMessageId() {
            return SERVER_PUT_PHOTO_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[0];
        }
    }

    public static class SendDetailsMessage extends ConcreteProtocolMessage {
        private final String jsonIdentityDetailsWithVersionAndPhoto;

        public SendDetailsMessage(CoreProtocolMessage coreProtocolMessage, String jsonIdentityDetailsWithVersionAndPhoto) {
            super(coreProtocolMessage);
            this.jsonIdentityDetailsWithVersionAndPhoto = jsonIdentityDetailsWithVersionAndPhoto;
        }

        public SendDetailsMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            if (receivedMessage.getInputs().length != 1) {
                throw new Exception();
            }
            this.jsonIdentityDetailsWithVersionAndPhoto = receivedMessage.getInputs()[0].decodeString();
        }

        @Override
        public int getProtocolMessageId() {
            return SEND_DETAILS_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    Encoded.of(jsonIdentityDetailsWithVersionAndPhoto),
            };
        }
    }


    // endregion






    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{StartPhotoUploadStep.class, ReceiveDetailsStep.class};
            case UPLOADING_PHOTO_STATE_ID:
                return new Class[]{SendDetailsStep.class};
            case DETAILS_SENT_STATE_ID:
            case DETAILS_RECEIVED_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class StartPhotoUploadStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final InitialMessage receivedMessage;

        public StartPhotoUploadStep(InitialProtocolState startState, InitialMessage receivedMessage, IdentityDetailsPublicationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            Identity ownedIdentity = getOwnedIdentity();
            JsonIdentityDetailsWithVersionAndPhoto[] jsons = protocolManagerSession.identityDelegate.getOwnedIdentityPublishedAndLatestDetails(protocolManagerSession.session, ownedIdentity);

            // check that the published details match the version we are trying to publish
            if (jsons == null) {
                return null;
            }
            if (jsons[0].getVersion() != receivedMessage.version) {
                Logger.i("Version mismatch in IdentityDetailsPublicationProtocol " + jsons[0].getVersion() + " " + receivedMessage.version);
                return null;
            }

            JsonIdentityDetailsWithVersionAndPhoto publishedDetails = jsons[0];

            if (publishedDetails.getPhotoUrl() != null && (publishedDetails.getPhotoServerLabel() == null || publishedDetails.getPhotoServerKey() == null)) {
                // we need to upload a photo
                UID photoServerLabel = new UID(getPrng());
                AuthEnc authEnc = Suite.getDefaultAuthEnc(0);
                AuthEncKey photoServerKey = authEnc.generateKey(getPrng());

                publishedDetails.setPhotoServerKey(Encoded.of(photoServerKey).getBytes());
                publishedDetails.setPhotoServerLabel(photoServerLabel.getBytes());

                // store the label and key in the details
                protocolManagerSession.identityDelegate.setOwnedIdentityDetailsServerLabelAndKey(protocolManagerSession.session, ownedIdentity, publishedDetails.getVersion(), photoServerLabel, photoServerKey);

                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createServerQueryChannelInfo(ownedIdentity, ServerQuery.Type.createPutUserDataQuery(ownedIdentity, photoServerLabel, publishedDetails.getPhotoUrl(), photoServerKey)));
                ChannelMessageToSend messageToSend = new ServerPutPhotoMessage(coreProtocolMessage).generateChannelServerQueryMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());


                String jsonPublishedDetails = protocol.getJsonObjectMapper().writeValueAsString(publishedDetails);
                return new UploadingPhotoState(jsonPublishedDetails);
            } else {
                // we can directly send the details

                Identity[] contactIdentities = protocolManagerSession.identityDelegate.getContactsOfOwnedIdentity(protocolManagerSession.session, ownedIdentity);
                if (contactIdentities.length > 0) {
                    String jsonPublishedDetails = protocol.getJsonObjectMapper().writeValueAsString(publishedDetails);

                    SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsInfosForMultipleIdentities(contactIdentities, ownedIdentity);
                    for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                        try {
                            CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(sendChannelInfo);
                            ChannelMessageToSend messageToSend = new SendDetailsMessage(coreProtocolMessage, jsonPublishedDetails).generateChannelProtocolMessageToSend();
                            protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                        } catch (Exception e) {
                            Logger.d("One contact with no channel during IdentityDetailsPublicationProtocol.StartPhotoUploadStep");
                        }
                    }
                }

                return new DetailsSentState();
            }
        }
    }

    public static class SendDetailsStep extends ProtocolStep {
        private final UploadingPhotoState startState;
        private final ServerPutPhotoMessage receivedMessage;

        public SendDetailsStep(UploadingPhotoState startState, ServerPutPhotoMessage receivedMessage, IdentityDetailsPublicationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            Identity[] contactIdentities = protocolManagerSession.identityDelegate.getContactsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            if (contactIdentities.length > 0) {
                SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsInfosForMultipleIdentities(contactIdentities, getOwnedIdentity());
                for (SendChannelInfo sendChannelInfo : sendChannelInfos) {
                    try {
                        CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(sendChannelInfo);
                        ChannelMessageToSend messageToSend = new SendDetailsMessage(coreProtocolMessage, startState.jsonIdentityDetailsWithVersionAndPhoto).generateChannelProtocolMessageToSend();
                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                    } catch (Exception e) {
                        Logger.d("One contact with no channel during IdentityDetailsPublicationProtocol.SendDetailsStep");
                    }
                }
            }
            return new DetailsSentState();
        }
    }

    public static class ReceiveDetailsStep extends ProtocolStep {
        private final InitialProtocolState startState;
        private final SendDetailsMessage receivedMessage;

        public ReceiveDetailsStep(InitialProtocolState startState, SendDetailsMessage receivedMessage, IdentityDetailsPublicationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            Identity contactIdentity = receivedMessage.getReceptionChannelInfo().getRemoteIdentity();
            Identity ownedIdentity = getOwnedIdentity();
            JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto = protocol.getJsonObjectMapper().readValue(receivedMessage.jsonIdentityDetailsWithVersionAndPhoto, JsonIdentityDetailsWithVersionAndPhoto.class);

            if (jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerLabel() != null && jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey() != null) {

                JsonIdentityDetailsWithVersionAndPhoto publishedDetails = protocolManagerSession.identityDelegate.getContactPublishedAndTrustedDetails(protocolManagerSession.session, ownedIdentity, contactIdentity)[0];

                if (! (Arrays.equals(jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerLabel(), publishedDetails.getPhotoServerLabel()) &&
                        ((jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey() == null && publishedDetails.getPhotoServerKey() == null) ||
                        (jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey() != null && publishedDetails.getPhotoServerKey() != null && new Encoded(jsonIdentityDetailsWithVersionAndPhoto.getPhotoServerKey()).decodeSymmetricKey().equals(new Encoded(publishedDetails.getPhotoServerKey()).decodeSymmetricKey()))) &&
                            publishedDetails.getPhotoUrl() != null)) {
                    // we need to download the photo, so we start a child protocol

                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(
                            SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
                            DOWNLOAD_IDENTITY_PHOTO_CHILD_PROTOCOL_ID,
                            new UID(getPrng()),
                            false
                    );
                    ChannelMessageToSend messageToSend = new DownloadIdentityPhotoChildProtocol.InitialMessage(coreProtocolMessage, contactIdentity, receivedMessage.jsonIdentityDetailsWithVersionAndPhoto).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                }
            }

            // update the contact published details
            protocolManagerSession.identityDelegate.setContactPublishedDetails(protocolManagerSession.session, contactIdentity, ownedIdentity, jsonIdentityDetailsWithVersionAndPhoto, false);

            return new DetailsReceivedState();
        }
    }

    // endregion
}