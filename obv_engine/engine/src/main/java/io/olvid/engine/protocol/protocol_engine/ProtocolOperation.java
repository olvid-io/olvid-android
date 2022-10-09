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

package io.olvid.engine.protocol.protocol_engine;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.protocol.databases.LinkBetweenProtocolInstances;
import io.olvid.engine.protocol.databases.ProtocolInstance;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.GenericProtocolMessageToSend;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSessionFactory;

public final class ProtocolOperation extends Operation {
    // possible reasons for cancel
    public static final int RFC_DELEGATE_NOT_SET = 1;
    public static final int RFC_MESSAGE_NOT_FOUND = 2;
    public static final int RFC_UNABLE_TO_RECONSTRUCT_PROTOCOL = 3;
    public static final int RFC_UNABLE_TO_RECONSTRUCT_MESSAGE = 4;
    public static final int RFC_UNABLE_TO_FIND_STEP_TO_EXECUTE = 5;
    public static final int RFC_THE_STEP_TO_EXECUTE_FAILED = 6;
    public static final int RFC_DIALOG_RESPONSE_CANNOT_BE_PROCESSED = 7;


    private final ProtocolManagerSessionFactory protocolManagerSessionFactory;
    private final UID receivedMessageUid;
    private final PRNGService prng;
    private final ObjectMapper jsonObjectMapper;

    // The following 2 variables are set during operation execution to used in the onFinishCallback of the coordinator
    private UID protocolInstanceUid;
    private Identity protocolOwnedIdentity;

    public UID getReceivedMessageUid() {
        return receivedMessageUid;
    }

    public UID getProtocolInstanceUid() {
        return protocolInstanceUid;
    }

    public Identity getProtocolOwnedIdentity() {
        return protocolOwnedIdentity;
    }

    public ProtocolOperation(ProtocolManagerSessionFactory protocolManagerSessionFactory, UID receivedMessageUid, PRNGService prng, ObjectMapper jsonObjectMapper, Operation.OnFinishCallback onFinishCallback, Operation.OnCancelCallback onCancelCallback) {
        super(receivedMessageUid, onFinishCallback, onCancelCallback);
        this.protocolManagerSessionFactory = protocolManagerSessionFactory;
        this.receivedMessageUid = receivedMessageUid;
        this.prng = prng;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        try (ProtocolManagerSession protocolManagerSession = protocolManagerSessionFactory.getSession()) {
            boolean finished = false;
            try {
                ReceivedMessage message = ReceivedMessage.get(protocolManagerSession, receivedMessageUid);
                if (message == null) {
                    cancel(RFC_MESSAGE_NOT_FOUND);
                    return;
                }

                int protocolId = message.getProtocolId();
                // Set this for use in the onFinishCallback
                this.protocolInstanceUid = message.getProtocolInstanceUid();
                this.protocolOwnedIdentity = message.getToIdentity();

                protocolManagerSession.session.startTransaction();

                ProtocolInstance protocolInstance = null;
                ConcreteProtocol protocol = null;
                try {
                    protocolInstance = ProtocolInstance.get(protocolManagerSession, protocolInstanceUid, protocolOwnedIdentity);
                    if (protocolInstance == null) {
                        protocolInstance = ProtocolInstance.create(protocolManagerSession, protocolInstanceUid, protocolOwnedIdentity, protocolId, new InitialProtocolState());
                        if (protocolInstance != null) {
                            protocol = ConcreteProtocol.getConcreteProtocolInInitialState(protocolManagerSession, protocolId, protocolInstanceUid, protocolOwnedIdentity, prng, jsonObjectMapper);
                        }
                    } else {
                        protocol = ConcreteProtocol.getConcreteProtocol(protocolInstance, prng, jsonObjectMapper);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    protocol = null;
                }
                if (protocol == null) {
                    cancel(RFC_UNABLE_TO_RECONSTRUCT_PROTOCOL);
                    return;
                }


                ConcreteProtocolMessage concreteProtocolMessage = protocol.getConcreteProtocolMessage(message);
                if (concreteProtocolMessage == null) {
                    cancel(RFC_UNABLE_TO_RECONSTRUCT_MESSAGE);
                    return;
                }

                ProtocolStep stepToExecute = protocol.getStepToExecute(concreteProtocolMessage);
                if (stepToExecute == null) {
                    // special case if the received message is a protocol dialog response: we delete the dialog
                    if (message.getUserDialogUuid() != null) {
                        cancel(RFC_DIALOG_RESPONSE_CANNOT_BE_PROCESSED);
                    } else {
                        cancel(RFC_UNABLE_TO_FIND_STEP_TO_EXECUTE);
                    }
                    return;
                }


                // run the step
                Logger.d("Executing step " + stepToExecute.getClass());
                OperationQueue queue = new OperationQueue();
                queue.queue(stepToExecute);
                queue.execute(1, "Engine-ProtocolOperation");
                queue.join();


                if (stepToExecute.isCancelled() || (stepToExecute.getEndState() == null)) {
                    Logger.i("Step " + stepToExecute.getClass() + " failed");
                    cancel(RFC_THE_STEP_TO_EXECUTE_FAILED);
                    return;
                }
                Logger.d("Finished step " + stepToExecute.getClass() + ". It reached state " + stepToExecute.getEndState().getClass());

                ConcreteProtocolState endState = stepToExecute.getEndState();
                protocolInstance.updateCurrentState(endState);
                protocol.updateCurrentState(endState);

                // Notify linked parent protocol
                GenericProtocolMessageToSend parentNotificationMessage = LinkBetweenProtocolInstances.getGenericProtocolMessageToSendWhenChildProtocolInstanceReachesAState(
                        protocolManagerSession,
                        protocol.getProtocolInstanceUid(),
                        protocol.getOwnedIdentity(),
                        protocol.getCurrentState()
                );
                if (parentNotificationMessage != null) {
                    if (protocolManagerSession.channelDelegate == null) {
                        Logger.w("Unable to run notify parent protocol as the ChannelDelegate is not set yet.");
                        throw new Exception();
                    }
                    protocolManagerSession.channelDelegate.post(protocolManagerSession.session, parentNotificationMessage.generateChannelProtocolMessageToSend(), prng);
                }

                if (protocol.hasReachedFinalState()) {
                    // Delete the associated ProtocolInstance
                    protocolInstance.delete();

                    // Delete all remaining ReceivedMessage for this protocol
                    if (protocol.eraseReceivedMessagesAfterReachingAFinalState) {
                        for (ReceivedMessage receivedMessage : ReceivedMessage.getAll(protocolManagerSession, protocol.getProtocolInstanceUid(), protocol.getOwnedIdentity())) {
                            receivedMessage.delete();
                        }
                    }
                }

                message.delete();

                finished = true;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (finished) {
                    protocolManagerSession.session.commit();
                    setFinished();
                } else {
                    protocolManagerSession.session.rollback();
                    if (hasNoReasonForCancel()) {
                        cancel(null);
                    }
                    processCancel();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Logger.e("SQLException in getSession.");
        }
    }
}
