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

import java.util.UUID;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocol;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;
import io.olvid.engine.protocol.protocol_engine.InitialProtocolState;
import io.olvid.engine.protocol.protocol_engine.OneWayDialogProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ProtocolStep;

public class SynchronizationProtocol extends ConcreteProtocol {
    public SynchronizationProtocol(ProtocolManagerSession protocolManagerSession, UID protocolInstanceUid, int currentStateId, Encoded encodedCurrentState, Identity ownedIdentity, PRNGService prng, ObjectMapper jsonObjectMapper) throws Exception {
        super(protocolManagerSession, protocolInstanceUid, currentStateId, encodedCurrentState, ownedIdentity, prng, jsonObjectMapper);
    }

    @Override
    public int getProtocolId() {
        return SYNCHRONIZATION_PROTOCOL_ID;
    }

//    public static UID computeOngoingProtocolInstanceUid(Identity ownedIdentity, UID currentDeviceUid, UID otherDeviceUid) {
//        byte[] bytesOwnedIdentity = ownedIdentity.getBytes();
//        byte[] seed = new byte[bytesOwnedIdentity.length + 2 * UID.UID_LENGTH];
//        System.arraycopy(bytesOwnedIdentity, 0, seed, 0, bytesOwnedIdentity.length);
//        if (currentDeviceUid.compareTo(otherDeviceUid) < 0) {
//            System.arraycopy(currentDeviceUid.getBytes(), 0, seed, bytesOwnedIdentity.length, UID.UID_LENGTH);
//            System.arraycopy(otherDeviceUid.getBytes(), 0, seed, bytesOwnedIdentity.length + UID.UID_LENGTH, UID.UID_LENGTH);
//        } else {
//            System.arraycopy(otherDeviceUid.getBytes(), 0, seed, bytesOwnedIdentity.length, UID.UID_LENGTH);
//            System.arraycopy(currentDeviceUid.getBytes(), 0, seed, bytesOwnedIdentity.length + UID.UID_LENGTH, UID.UID_LENGTH);
//        }
//
//        Seed prngSeed = new Seed(seed);
//        PRNG seededPRNG = Suite.getDefaultPRNG(0, prngSeed);
//        return new UID(seededPRNG);
//    }

    // region States

//    private static final int ONGOING_SYNC_STATE = 1;
    private static final int FINAL_STATE_ID = 99;

    @Override
    public int[] getFinalStateIds() {
        return new int[]{FINAL_STATE_ID};
    }

    @Override
    protected Class<?> getStateClass(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return InitialProtocolState.class;
//            case ONGOING_SYNC_STATE:
//                return OngoingSyncState.class;
            case FINAL_STATE_ID:
                return FinalState.class;
            default:
                return null;
        }
    }

//    public static class OngoingSyncState extends ConcreteProtocolState {
//        private final UID otherDeviceUid;
//        private final long currentVersion;
//        private final long lastSeenOtherVersion;
//        private final HashMap<DictionaryKey, Encoded> currentSnapshotDict;
//        private final HashMap<DictionaryKey, Encoded> otherSnapshotDict; // can be null
//        private final boolean currentlyShowingDiff;
//
//        public OngoingSyncState(UID otherDeviceUid, long currentVersion, long lastSeenOtherVersion, HashMap<DictionaryKey, Encoded> currentSnapshotDict, HashMap<DictionaryKey, Encoded> otherSnapshotDict, boolean currentlyShowingDiff) {
//            super(ONGOING_SYNC_STATE);
//            this.otherDeviceUid = otherDeviceUid;
//            this.currentVersion = currentVersion;
//            this.lastSeenOtherVersion = lastSeenOtherVersion;
//            this.currentSnapshotDict = currentSnapshotDict;
//            this.otherSnapshotDict = otherSnapshotDict;
//            this.currentlyShowingDiff = currentlyShowingDiff;
//        }
//
//        @SuppressWarnings({"unused", "RedundantSuppression"})
//        public OngoingSyncState(Encoded encodedState) throws Exception {
//            super(ONGOING_SYNC_STATE);
//            Encoded[] list = encodedState.decodeList();
//            if (list.length == 5) { // the otherSnapshotDict is null
//                this.otherDeviceUid = list[0].decodeUid();
//                this.currentVersion = list[1].decodeLong();
//                this.lastSeenOtherVersion = list[2].decodeLong();
//                this.currentSnapshotDict = list[3].decodeDictionary();
//                this.otherSnapshotDict = null;
//                this.currentlyShowingDiff = list[4].decodeBoolean();
//            } else if (list.length == 6) { // the otherSnapshotDict is not null
//                this.otherDeviceUid = list[0].decodeUid();
//                this.currentVersion = list[1].decodeLong();
//                this.lastSeenOtherVersion = list[2].decodeLong();
//                this.currentSnapshotDict = list[3].decodeDictionary();
//                this.otherSnapshotDict = list[4].decodeDictionary();
//                this.currentlyShowingDiff = list[5].decodeBoolean();
//            } else {
//                throw new Exception();
//            }
//        }
//
//        @Override
//        public Encoded encode() {
//            if (otherSnapshotDict == null) {
//                return Encoded.of(new Encoded[]{
//                        Encoded.of(otherDeviceUid),
//                        Encoded.of(currentVersion),
//                        Encoded.of(lastSeenOtherVersion),
//                        Encoded.of(currentSnapshotDict),
//                        Encoded.of(currentlyShowingDiff),
//                });
//            } else {
//                return Encoded.of(new Encoded[]{
//                        Encoded.of(otherDeviceUid),
//                        Encoded.of(currentVersion),
//                        Encoded.of(lastSeenOtherVersion),
//                        Encoded.of(currentSnapshotDict),
//                        Encoded.of(otherSnapshotDict),
//                        Encoded.of(currentlyShowingDiff),
//                });
//            }
//        }
//    }


    public static class FinalState extends ConcreteProtocolState {
        protected FinalState() {
            super(FINAL_STATE_ID);
        }

        @Override
        public Encoded encode() {
            return Encoded.of(new Encoded[0]);
        }
    }


    // endregion



    // region Messages

    public static final int INITIATE_SINGLE_ITEM_SYNC_MESSAGE_ID = 0;
    public static final int SINGLE_ITEM_SYNC_MESSAGE_ID = 1;
//    public static final int INITIATE_SYNC_MESSAGE_ID = 2;
//    public static final int TRIGGER_SYNC_MESSAGE_ID = 3;
//    public static final int SNAPSHOT_SYNC_MESSAGE_ID = 4;
//    public static final int ATOM_PROCESSED_MESSAGE_ID = 5;

    @Override
    protected Class<?> getMessageClass(int protocolMessageId) {
        switch (protocolMessageId) {
            case INITIATE_SINGLE_ITEM_SYNC_MESSAGE_ID:
                return InitiateSingleItemSyncMessage.class;
            case SINGLE_ITEM_SYNC_MESSAGE_ID:
                return SingleItemSyncMessage.class;
//            case INITIATE_SYNC_MESSAGE_ID:
//                return InitiateSyncMessage.class;
//            case TRIGGER_SYNC_MESSAGE_ID:
//                return TriggerSyncMessage.class;
//            case SNAPSHOT_SYNC_MESSAGE_ID:
//                return SnapshotSyncMessage.class;
//            case ATOM_PROCESSED_MESSAGE_ID:
//                return AtomProcessedMessage.class;
            default:
                return null;
        }
    }

    public static class InitiateSingleItemSyncMessage extends ConcreteProtocolMessage {
        final ObvSyncAtom obvSyncAtom;

        public InitiateSingleItemSyncMessage(CoreProtocolMessage coreProtocolMessage, ObvSyncAtom obvSyncAtom) {
            super(coreProtocolMessage);
            this.obvSyncAtom = obvSyncAtom;
        }

        @SuppressWarnings("unused")
        public InitiateSingleItemSyncMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 1) {
                throw new Exception();
            }
            this.obvSyncAtom = ObvSyncAtom.of(list[0]);
        }

        @Override
        public int getProtocolMessageId() {
            return INITIATE_SINGLE_ITEM_SYNC_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    obvSyncAtom.encode(),
            };
        }
    }

    public static class SingleItemSyncMessage extends ConcreteProtocolMessage {
        final ObvSyncAtom obvSyncAtom;

        public SingleItemSyncMessage(CoreProtocolMessage coreProtocolMessage, ObvSyncAtom obvSyncAtom) {
            super(coreProtocolMessage);
            this.obvSyncAtom = obvSyncAtom;
        }

        @SuppressWarnings("unused")
        public SingleItemSyncMessage(ReceivedMessage receivedMessage) throws Exception {
            super(new CoreProtocolMessage(receivedMessage));
            Encoded[] list = receivedMessage.getInputs();
            if (list.length != 1) {
                throw new Exception();
            }
            this.obvSyncAtom = ObvSyncAtom.of(list[0]);
        }

        @Override
        public int getProtocolMessageId() {
            return SINGLE_ITEM_SYNC_MESSAGE_ID;
        }

        @Override
        public Encoded[] getInputs() {
            return new Encoded[]{
                    obvSyncAtom.encode(),
            };
        }
    }

//    public static class InitiateSyncMessage extends ConcreteProtocolMessage {
//        final UID otherDeviceUid;
//
//        public InitiateSyncMessage(CoreProtocolMessage coreProtocolMessage, UID otherDeviceUid) {
//            super(coreProtocolMessage);
//            this.otherDeviceUid = otherDeviceUid;
//        }
//
//        @SuppressWarnings("unused")
//        public InitiateSyncMessage(ReceivedMessage receivedMessage) throws Exception {
//            super(new CoreProtocolMessage(receivedMessage));
//            Encoded[] list = receivedMessage.getInputs();
//            if (list.length != 1) {
//                throw new Exception();
//            }
//            this.otherDeviceUid = list[0].decodeUid();
//        }
//
//        @Override
//        public int getProtocolMessageId() {
//            return INITIATE_SYNC_MESSAGE_ID;
//        }
//
//        @Override
//        public Encoded[] getInputs() {
//            return new Encoded[]{
//                    Encoded.of(otherDeviceUid),
//            };
//        }
//    }

//    public static class TriggerSyncMessage extends ConcreteProtocolMessage {
//        private final boolean forceSendSnapshot;
//        public TriggerSyncMessage(CoreProtocolMessage coreProtocolMessage, boolean forceSendSnapshot) {
//            super(coreProtocolMessage);
//            this.forceSendSnapshot = forceSendSnapshot;
//        }
//
//        @SuppressWarnings("unused")
//        public TriggerSyncMessage(ReceivedMessage receivedMessage) throws Exception {
//            super(new CoreProtocolMessage(receivedMessage));
//            Encoded[] list = receivedMessage.getInputs();
//            if (list.length != 1) {
//                throw new Exception();
//            }
//            this.forceSendSnapshot = list[0].decodeBoolean();
//        }
//
//        @Override
//        public int getProtocolMessageId() {
//            return TRIGGER_SYNC_MESSAGE_ID;
//        }
//
//        @Override
//        public Encoded[] getInputs() {
//            return new Encoded[]{
//                    Encoded.of(forceSendSnapshot),
//            };
//        }
//    }

//    public static class SnapshotSyncMessage extends ConcreteProtocolMessage {
//        private final long senderCurrentVersion;
//        private final long lastSeenRecipientVersion;
//        private final HashMap<DictionaryKey, Encoded> snapshotDict;
//
//        public SnapshotSyncMessage(CoreProtocolMessage coreProtocolMessage, long senderCurrentVersion, long lastSeenRecipientVersion, HashMap<DictionaryKey, Encoded> snapshotDict) {
//            super(coreProtocolMessage);
//            this.senderCurrentVersion = senderCurrentVersion;
//            this.lastSeenRecipientVersion = lastSeenRecipientVersion;
//            this.snapshotDict = snapshotDict;
//        }
//
//        @SuppressWarnings("unused")
//        public SnapshotSyncMessage(ReceivedMessage receivedMessage) throws Exception {
//            super(new CoreProtocolMessage(receivedMessage));
//            Encoded[] list = receivedMessage.getInputs();
//            if (list.length != 3) {
//                throw new Exception();
//            }
//            this.senderCurrentVersion = list[0].decodeLong();
//            this.lastSeenRecipientVersion = list[1].decodeLong();
//            this.snapshotDict = list[2].decodeDictionary();
//        }
//
//        @Override
//        public int getProtocolMessageId() {
//            return SNAPSHOT_SYNC_MESSAGE_ID;
//        }
//
//        @Override
//        public Encoded[] getInputs() {
//            return new Encoded[]{
//                    Encoded.of(senderCurrentVersion),
//                    Encoded.of(lastSeenRecipientVersion),
//                    Encoded.of(snapshotDict),
//            };
//        }
//    }

//    public static class AtomProcessedMessage extends EmptyProtocolMessage {
//        public AtomProcessedMessage(CoreProtocolMessage coreProtocolMessage) {
//            super(coreProtocolMessage);
//        }
//
//        @SuppressWarnings("unused")
//        public AtomProcessedMessage(ReceivedMessage receivedMessage) throws Exception {
//            super(receivedMessage);
//        }
//
//        @Override
//        public int getProtocolMessageId() {
//            return ATOM_PROCESSED_MESSAGE_ID;
//        }
//    }


    // endregion





    // region Steps

    @Override
    protected Class<?>[] getPossibleStepClasses(int stateId) {
        switch (stateId) {
            case INITIAL_STATE_ID:
                return new Class[]{SendSingleItemSyncMessageStep.class, ProcessSingleItemSyncMessageStep.class/*, UpdateStateAndSendSyncMessageStep.class*/};
//            case ONGOING_SYNC_STATE:
//                return new Class[]{UpdateStateAndSendSyncMessageStep.class};
            case FINAL_STATE_ID:
            default:
                return new Class[0];
        }
    }

    public static class SendSingleItemSyncMessageStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final InitiateSingleItemSyncMessage receivedMessage;

        public SendSingleItemSyncMessageStep(InitialProtocolState startState, InitiateSingleItemSyncMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // simply send the ObvSyncItem to all other devices
            UID[] otherDeviceUids = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
            if (otherDeviceUids.length > 0) {
                try {
                    CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createAllOwnedConfirmedObliviousChannelsInfo(getOwnedIdentity()));
                    ChannelMessageToSend messageToSend = new SingleItemSyncMessage(coreProtocolMessage, receivedMessage.obvSyncAtom).generateChannelProtocolMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
                } catch (NoAcceptableChannelException ignored) { }

//                // send an AtomProcessedMessage to all ongoing instances of the synchronisation protocol
//                UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
//                for (UID otherDeviceUid : otherDeviceUids) {
//                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
//                            ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID,
//                            computeOngoingProtocolInstanceUid(getOwnedIdentity(), currentDeviceUid, otherDeviceUid),
//                            false);
//                    ChannelMessageToSend message = new AtomProcessedMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
//                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());
//                }
            }


            return new FinalState();
        }
    }

    public static class ProcessSingleItemSyncMessageStep extends ProtocolStep {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final InitialProtocolState startState;
        private final SingleItemSyncMessage receivedMessage;

        public ProcessSingleItemSyncMessageStep(InitialProtocolState startState, SingleItemSyncMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
            this.startState = startState;
            this.receivedMessage = receivedMessage;
        }

        @Override
        public ConcreteProtocolState executeStep() throws Exception {
            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();

            // check whether this item should be processed at app level or identity manager level
            if (receivedMessage.obvSyncAtom.isAppSyncItem()) {
                // create a one way app dialog to send the sync item to the app
                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(getOwnedIdentity(), DialogType.createSyncItemToApplyDialog(receivedMessage.obvSyncAtom), UUID.randomUUID()));
                ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
            } else {
                // request the identity manager to process the sync item
                protocolManagerSession.identityDelegate.processSyncItem(protocolManagerSession.session, getOwnedIdentity(), receivedMessage.obvSyncAtom);
            }

//            {
//                // for all instances of the synchronisation protocol, send an AtomProcessedMessage to refresh the protocol's currentSnapshot
//                UID[] otherDeviceUids = protocolManagerSession.identityDelegate.getOtherDeviceUidsOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
//                if (otherDeviceUids.length > 0) { // this should always be the case!
//                    UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
//                    for (UID otherDeviceUid : otherDeviceUids) {
//                        CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createLocalChannelInfo(getOwnedIdentity()),
//                                ConcreteProtocol.SYNCHRONIZATION_PROTOCOL_ID,
//                                computeOngoingProtocolInstanceUid(getOwnedIdentity(), currentDeviceUid, otherDeviceUid),
//                                false);
//                        ChannelMessageToSend message = new AtomProcessedMessage(coreProtocolMessage).generateChannelProtocolMessageToSend();
//                        protocolManagerSession.channelDelegate.post(protocolManagerSession.session, message, getPrng());
//                    }
//                }
//            }

            return new FinalState();
        }
    }


//    public static class UpdateStateAndSendSyncMessageStep extends ProtocolStep {
//        private final ConcreteProtocolState startState;
//        private final boolean ignoreMessage;
//        private boolean sendOurSnapshot;
//        private final UID otherDeviceUid;
//        private final long currentVersion;
//        private final long lastSeenOtherVersion;
//        private final HashMap<DictionaryKey, Encoded> currentSnapshotDict;
//        private final HashMap<DictionaryKey, Encoded> otherSnapshotDict;
//        private final boolean currentlyShowingDiff;
//        private final long receivedCurrentVersion;
//        private final long receivedLastSeenOtherVersion;
//        private final HashMap<DictionaryKey, Encoded> receivedSnapshotDict;
//
//        @SuppressWarnings("unused")
//        public UpdateStateAndSendSyncMessageStep(InitialProtocolState startState, InitiateSyncMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
//            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
//            this.startState = null;
//            this.ignoreMessage = false;
//            this.sendOurSnapshot = false;
//            this.otherDeviceUid = receivedMessage.otherDeviceUid;
//            this.currentVersion = -1;
//            this.lastSeenOtherVersion = -1;
//            this.currentSnapshotDict = null;
//            this.otherSnapshotDict = null;
//            this.currentlyShowingDiff = false;
//            this.receivedCurrentVersion = -1;
//            this.receivedLastSeenOtherVersion = -1;
//            this.receivedSnapshotDict = null;
//        }
//
//        @SuppressWarnings("unused")
//        public UpdateStateAndSendSyncMessageStep(InitialProtocolState startState, TriggerSyncMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
//            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
//            this.startState = null;
//            this.ignoreMessage = true;
//            this.sendOurSnapshot = false;
//            this.otherDeviceUid = null;
//            this.currentVersion = -1;
//            this.lastSeenOtherVersion = -1;
//            this.currentSnapshotDict = null;
//            this.otherSnapshotDict = null;
//            this.currentlyShowingDiff = false;
//            this.receivedCurrentVersion = -1;
//            this.receivedLastSeenOtherVersion = -1;
//            this.receivedSnapshotDict = null;
//        }
//
//        @SuppressWarnings("unused")
//        public UpdateStateAndSendSyncMessageStep(InitialProtocolState startState, SnapshotSyncMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
//            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
//            this.startState = null;
//            this.ignoreMessage = false;
//            this.sendOurSnapshot = false;
//            this.otherDeviceUid = receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid();
//            this.currentVersion = -1;
//            this.lastSeenOtherVersion = -1;
//            this.currentSnapshotDict = null;
//            this.otherSnapshotDict = null;
//            this.currentlyShowingDiff = false;
//            this.receivedCurrentVersion = receivedMessage.senderCurrentVersion;
//            this.receivedLastSeenOtherVersion = receivedMessage.lastSeenRecipientVersion;
//            this.receivedSnapshotDict = receivedMessage.snapshotDict;
//        }
//
//        @SuppressWarnings("unused")
//        public UpdateStateAndSendSyncMessageStep(InitialProtocolState startState, AtomProcessedMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
//            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
//            this.startState = null;
//            this.ignoreMessage = true;
//            this.sendOurSnapshot = false;
//            this.otherDeviceUid = null;
//            this.currentVersion = -1;
//            this.lastSeenOtherVersion = -1;
//            this.currentSnapshotDict = null;
//            this.otherSnapshotDict = null;
//            this.currentlyShowingDiff = false;
//            this.receivedCurrentVersion = -1;
//            this.receivedLastSeenOtherVersion = -1;
//            this.receivedSnapshotDict = null;
//        }
//
//        @SuppressWarnings("unused")
//        public UpdateStateAndSendSyncMessageStep(OngoingSyncState startState, TriggerSyncMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
//            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
//            this.startState = startState;
//            this.ignoreMessage = false;
//            this.sendOurSnapshot = receivedMessage.forceSendSnapshot;
//            this.otherDeviceUid = startState.otherDeviceUid;
//            this.currentVersion = startState.currentVersion;
//            this.lastSeenOtherVersion = startState.lastSeenOtherVersion;
//            this.currentSnapshotDict = startState.currentSnapshotDict;
//            this.otherSnapshotDict = startState.otherSnapshotDict;
//            this.currentlyShowingDiff = startState.currentlyShowingDiff;
//            this.receivedCurrentVersion = -1;
//            this.receivedLastSeenOtherVersion = -1;
//            this.receivedSnapshotDict = null;
//        }
//
//        @SuppressWarnings("unused")
//        public UpdateStateAndSendSyncMessageStep(OngoingSyncState startState, InitiateSyncMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
//            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
//            this.startState = startState;
//            this.ignoreMessage = true;
//            this.sendOurSnapshot = false;
//            this.otherDeviceUid = null;
//            this.currentVersion = -1;
//            this.lastSeenOtherVersion = -1;
//            this.currentSnapshotDict = null;
//            this.otherSnapshotDict = null;
//            this.currentlyShowingDiff = false;
//            this.receivedCurrentVersion = -1;
//            this.receivedLastSeenOtherVersion = -1;
//            this.receivedSnapshotDict = null;
//        }
//
//        @SuppressWarnings("unused")
//        public UpdateStateAndSendSyncMessageStep(OngoingSyncState startState, SnapshotSyncMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
//            super(ReceptionChannelInfo.createAnyObliviousChannelWithOwnedDeviceInfo(), receivedMessage, protocol);
//            this.startState = startState;
//            this.ignoreMessage = !Objects.equals(receivedMessage.getReceptionChannelInfo().getRemoteDeviceUid(), startState.otherDeviceUid);
//            this.sendOurSnapshot = false;
//            this.otherDeviceUid = startState.otherDeviceUid;
//            this.currentVersion = startState.currentVersion;
//            this.lastSeenOtherVersion = startState.lastSeenOtherVersion;
//            this.currentSnapshotDict = startState.currentSnapshotDict;
//            this.otherSnapshotDict = startState.otherSnapshotDict;
//            this.currentlyShowingDiff = startState.currentlyShowingDiff;
//            this.receivedCurrentVersion = receivedMessage.senderCurrentVersion;
//            this.receivedLastSeenOtherVersion = receivedMessage.lastSeenRecipientVersion;
//            this.receivedSnapshotDict = receivedMessage.snapshotDict;
//        }
//
//        @SuppressWarnings("unused")
//        public UpdateStateAndSendSyncMessageStep(OngoingSyncState startState, AtomProcessedMessage receivedMessage, SynchronizationProtocol protocol) throws Exception {
//            super(ReceptionChannelInfo.createLocalChannelInfo(), receivedMessage, protocol);
//            this.startState = startState;
//            this.ignoreMessage = !startState.currentlyShowingDiff; // simply ignore atom processed messages if not currently showing a diff
//            this.sendOurSnapshot = false;
//            this.otherDeviceUid = startState.otherDeviceUid;
//            this.currentVersion = startState.currentVersion;
//            this.lastSeenOtherVersion = startState.lastSeenOtherVersion;
//            this.currentSnapshotDict = startState.currentSnapshotDict;
//            this.otherSnapshotDict = startState.otherSnapshotDict;
//            this.currentlyShowingDiff = startState.currentlyShowingDiff;
//            this.receivedCurrentVersion = -1;
//            this.receivedLastSeenOtherVersion = -1;
//            this.receivedSnapshotDict = null;
//        }
//
//
//        @Override
//        public ConcreteProtocolState executeStep() throws Exception {
//            ProtocolManagerSession protocolManagerSession = getProtocolManagerSession();
//
//            // check if message should be ignored
//            if (ignoreMessage) {
//                return startState == null ? new FinalState() : startState;
//            }
//
//            // check the protocolUid matches what we expect
//            UID currentDeviceUid = protocolManagerSession.identityDelegate.getCurrentDeviceUidOfOwnedIdentity(protocolManagerSession.session, getOwnedIdentity());
//            if (!Objects.equals(computeOngoingProtocolInstanceUid(getOwnedIdentity(), currentDeviceUid, otherDeviceUid), getProtocolInstanceUid())) {
//                // this can only happen if the startState is the InitialProtocolState, otherwise the protocolInstanceUid has already been checked
//                Logger.w("In SynchronizationProtocol.UpdateStateAndSendSyncMessageStep, bad protocolInstanceUid!");
//                return new FinalState();
//            }
//
//            // check the otherDeviceUid has a channel --> if not, finish the protocol. A new one will be started whenever a channel is created
//            if (!protocolManagerSession.channelDelegate.checkIfObliviousChannelIsConfirmed(protocolManagerSession.session, getOwnedIdentity(), otherDeviceUid, getOwnedIdentity())) {
//                return new FinalState();
//            }
//
//
//            ObvSyncSnapshot currentSnapshot = (currentSnapshotDict == null) ? null : ObvSyncSnapshot.fromEncodedDictionary(currentSnapshotDict, protocolManagerSession.identityBackupAndSyncDelegate, protocolManagerSession.appBackupAndSyncDelegate);
//            ObvSyncSnapshot otherSnapshot = (otherSnapshotDict == null) ? null : ObvSyncSnapshot.fromEncodedDictionary(otherSnapshotDict, protocolManagerSession.identityBackupAndSyncDelegate, protocolManagerSession.appBackupAndSyncDelegate);
//            ObvSyncSnapshot receivedSnapshot = (receivedSnapshotDict == null) ? null : ObvSyncSnapshot.fromEncodedDictionary(receivedSnapshotDict, protocolManagerSession.identityBackupAndSyncDelegate, protocolManagerSession.appBackupAndSyncDelegate);
//
//            long updatedCurrentVersion;
//            long updatedLastSeenOtherVersion;
//            ObvSyncSnapshot updatedCurrentSnapshot;
//            ObvSyncSnapshot updatedOtherSnapshot;
//
//            // if we received a snapshot, check if it is outdated
//            if (receivedCurrentVersion != -1 && receivedSnapshot != null) {
//                if (lastSeenOtherVersion > receivedCurrentVersion) {
//                    // we received an old snapshot, ignore it
//                    return startState == null ? new FinalState() : startState;
//                } else if (lastSeenOtherVersion == receivedCurrentVersion) {
//                    // numbers are equal: weird! Check if the snapshot has changed
//                    if (otherSnapshot != null && otherSnapshot.areContentsTheSame(receivedSnapshot)) {
//                        return startState;
//                    }
//                }
//                updatedLastSeenOtherVersion = receivedCurrentVersion;
//                updatedOtherSnapshot = receivedSnapshot;
//            } else {
//                // no change
//                updatedLastSeenOtherVersion = lastSeenOtherVersion;
//                updatedOtherSnapshot = otherSnapshot;
//            }
//
//            // check the other device has a coherent version of our snapshot
//            if (receivedLastSeenOtherVersion != -1 && receivedLastSeenOtherVersion > currentVersion) {
//                // the other device has a larger version than our current --> we probably restarted the protocol and not them
//                // 1. update our currentVersion to be larger
//                updatedCurrentVersion = receivedLastSeenOtherVersion + 1;
//                // 2. we need to send a snapshot in all cases
//                sendOurSnapshot = true;
//            } else {
//                updatedCurrentVersion = currentVersion;
//            }
//
//            // get our current snapshot and see if anything changed
//            updatedCurrentSnapshot = ObvSyncSnapshot.get(getOwnedIdentity(), protocolManagerSession.identityBackupAndSyncDelegate, protocolManagerSession.appBackupAndSyncDelegate);
//            boolean currentSnapshotChanged = !updatedCurrentSnapshot.areContentsTheSame(currentSnapshot);
//
//            // if something changed, increment version and send the new snapshot to other device
//            if (currentSnapshotChanged) {
//                updatedCurrentVersion++;
//                sendOurSnapshot = true;
//            }
//
//            //////
//            // decide whether we should compute a diff to show to the user. This will be the case if:
//            // - we are currently showing a diff
//            // - OR we are not showing a diff, but we received a snapshot with a receivedLastSeenOtherVersion == currentVersion
//            // In both cases, if the diff we compute is empty, we stop showing a diff to the user
//
//            boolean shouldComputeDiff = updatedOtherSnapshot != null && (this.currentlyShowingDiff || (receivedLastSeenOtherVersion == currentVersion));
//
//            List<ObvSyncDiff> computedDiffs;
//            if (shouldComputeDiff) {
//                // we compute a diff between our updatedSnapshot and the updatedOtherSnapshot
//                computedDiffs = updatedCurrentSnapshot.computeDiff(updatedOtherSnapshot);
//            } else {
//                computedDiffs = Collections.emptyList();
//            }
//
//            HashMap<DictionaryKey, Encoded> updatedCurrentSnapshotDict = updatedCurrentSnapshot.toEncodedDictionary(protocolManagerSession.identityBackupAndSyncDelegate, protocolManagerSession.appBackupAndSyncDelegate);
//
//            if (sendOurSnapshot) {
//                CoreProtocolMessage coreProtocolMessage = buildCoreProtocolMessage(SendChannelInfo.createObliviousChannelInfo(getOwnedIdentity(), getOwnedIdentity(), new UID[]{this.otherDeviceUid}, true));
//                ChannelMessageToSend messageToSend = new SnapshotSyncMessage(coreProtocolMessage, updatedCurrentVersion, updatedLastSeenOtherVersion, updatedCurrentSnapshotDict).generateChannelProtocolMessageToSend();
//                protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, getPrng());
//            }
//
//            boolean shouldShowDiff = !computedDiffs.isEmpty();
//            if (shouldShowDiff) {
//                // TODO: send the diff to the app
//            } else if (currentlyShowingDiff) {
//                // TODO: send an empty diff to the app
//            }
//
//            return new OngoingSyncState(
//                    otherDeviceUid,
//                    updatedCurrentVersion,
//                    updatedLastSeenOtherVersion,
//                    updatedCurrentSnapshotDict,
//                    updatedOtherSnapshot == null ? null : updatedOtherSnapshot.toEncodedDictionary(protocolManagerSession.identityBackupAndSyncDelegate, protocolManagerSession.appBackupAndSyncDelegate),
//                    shouldShowDiff
//            );
//        }
//    }

    // endregion
}
