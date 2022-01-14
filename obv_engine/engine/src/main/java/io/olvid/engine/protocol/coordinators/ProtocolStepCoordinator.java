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

package io.olvid.engine.protocol.coordinators;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.HashMap;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoDuplicateOperationQueue;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.DialogType;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
//import io.olvid.engine.protocol.databases.PostponedGroupManagementReceivedMessage;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSessionFactory;
import io.olvid.engine.protocol.datatypes.ProtocolReceivedMessageProcessorDelegate;
import io.olvid.engine.protocol.protocol_engine.OneWayDialogProtocolMessage;
import io.olvid.engine.protocol.protocol_engine.ProtocolOperation;

public class ProtocolStepCoordinator implements ProtocolReceivedMessageProcessorDelegate, Operation.OnFinishCallback, Operation.OnCancelCallback {
    private final ProtocolManagerSessionFactory protocolManagerSessionFactory;
    private final PRNGService prng;
    private final ObjectMapper jsonObjectMapper;

    private final NoDuplicateOperationQueue protocolOperationQueue;
    private final HashMap<UID, Integer> stepFailedAttemptCount;

    public ProtocolStepCoordinator(ProtocolManagerSessionFactory protocolManagerSessionFactory, PRNGService prng, ObjectMapper jsonObjectMapper) {
        this.protocolManagerSessionFactory = protocolManagerSessionFactory;
        this.prng = prng;
        this.jsonObjectMapper = jsonObjectMapper;

        protocolOperationQueue = new NoDuplicateOperationQueue();
        protocolOperationQueue.execute(1, "Engine-ProtocolStepCoordinator");
        stepFailedAttemptCount = new HashMap<>();
    }

    private void queueNewProtocolOperation(UID receivedMessageUid) {
        ProtocolOperation op = new ProtocolOperation(protocolManagerSessionFactory, receivedMessageUid, prng, jsonObjectMapper, this, this);
        protocolOperationQueue.queue(op);
    }

    public void initialQueueing() {
        try (ProtocolManagerSession protocolManagerSession = protocolManagerSessionFactory.getSession()) {
            // TODO: also cleanup protocol instances: implement a clean abort in each protocol, and call it when the protocol is stalled
            ReceivedMessage.deleteExpiredMessagesWithNoProtocol(protocolManagerSession);

            ReceivedMessage[] receivedMessages = ReceivedMessage.getAll(protocolManagerSession);
            if (receivedMessages.length > 0) {
                Logger.d("Found " + receivedMessages.length + " ReceivedMessage to (attempt to) process.");
                for (ReceivedMessage receivedMessage : receivedMessages) {
                    queueNewProtocolOperation(receivedMessage.getUid());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void processReceivedMessage(UID messageUid) {
        queueNewProtocolOperation(messageUid);
    }

    @Override
    public void onFinishCallback(Operation operation) {
        Logger.d("Running onFinishCallback for " + operation.getClass());
        ProtocolOperation protocolOperation = (ProtocolOperation) operation;
        UID protocolInstanceUid = protocolOperation.getProtocolInstanceUid();
        Identity protocolOwnedIdentity = protocolOperation.getProtocolOwnedIdentity();
        if ((protocolInstanceUid == null) || (protocolOwnedIdentity == null)) {
            Logger.w("The ProtocolOperation finished, but either the protocolInstanceUid or the protocolOwnedIdentity is not properly set.");
            return;
        }
        try (ProtocolManagerSession protocolManagerSession = protocolManagerSessionFactory.getSession()){
            for (ReceivedMessage receivedMessage : ReceivedMessage.getAll(protocolManagerSession, protocolInstanceUid, protocolOwnedIdentity)) {
                protocolManagerSession.protocolReceivedMessageProcessorDelegate.processReceivedMessage(receivedMessage.getUid());
            }
            protocolManagerSession.session.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCancelCallback(Operation operation) {
        Logger.d("Running onCancelCallback for " + operation.getClass());
        if (operation.hasNoReasonForCancel()) {
            return;
        }
        Logger.d("ProtocolOperation cancelled for RFC " + operation.getReasonForCancel());
        switch (operation.getReasonForCancel()) {
            case ProtocolOperation.RFC_DELEGATE_NOT_SET:
            case ProtocolOperation.RFC_MESSAGE_NOT_FOUND:
            case ProtocolOperation.RFC_UNABLE_TO_FIND_STEP_TO_EXECUTE:
                break;
            case ProtocolOperation.RFC_THE_STEP_TO_EXECUTE_FAILED: {
                // check how many times this step has failed before
                UID messageUid = ((ProtocolOperation) operation).getReceivedMessageUid();
                Integer failedAttemps = stepFailedAttemptCount.get(messageUid);
                if (failedAttemps == null) {
                    failedAttemps = 0;
                }
                failedAttemps++;
                if (failedAttemps >= 5) {
                    // the step failed 5 times --> we can delete it
                    try (ProtocolManagerSession protocolManagerSession = protocolManagerSessionFactory.getSession()) {
                        ReceivedMessage message = ReceivedMessage.get(protocolManagerSession, ((ProtocolOperation) operation).getReceivedMessageUid());
                        message.delete();
                        protocolManagerSession.session.commit();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                } else {
                    // retry to execute the step
                    stepFailedAttemptCount.put(messageUid, failedAttemps);
                    queueNewProtocolOperation(messageUid);
                }
                break;
            }
            case ProtocolOperation.RFC_UNABLE_TO_RECONSTRUCT_MESSAGE:
            case ProtocolOperation.RFC_UNABLE_TO_RECONSTRUCT_PROTOCOL: {
                // Delete the protocol message
                try (ProtocolManagerSession protocolManagerSession = protocolManagerSessionFactory.getSession()) {
                    ReceivedMessage message = ReceivedMessage.get(protocolManagerSession, ((ProtocolOperation) operation).getReceivedMessageUid());
                    if (message != null) {
                        message.delete();
                        protocolManagerSession.session.commit();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                break;
            }
            case ProtocolOperation.RFC_DIALOG_RESPONSE_CANNOT_BE_PROCESSED: {
                // Delete the protocol message and the UI dialog
                try (ProtocolManagerSession protocolManagerSession = protocolManagerSessionFactory.getSession()) {
                    ReceivedMessage message = ReceivedMessage.get(protocolManagerSession, ((ProtocolOperation) operation).getReceivedMessageUid());
                    message.delete();

                    CoreProtocolMessage coreProtocolMessage = new CoreProtocolMessage(SendChannelInfo.createUserInterfaceChannelInfo(message.getToIdentity(), DialogType.createDeleteDialog(), message.getUserDialogUuid()), message.getProtocolId(), message.getProtocolInstanceUid(), false);
                    ChannelMessageToSend messageToSend = new OneWayDialogProtocolMessage(coreProtocolMessage).generateChannelDialogMessageToSend();
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, messageToSend, prng);

                    protocolManagerSession.session.commit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            default:
                Logger.w("Unknown RFC for ProtocolOperation: " + operation.getReasonForCancel());
        }
    }
}
