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

package io.olvid.engine.networksend.operations;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networksend.databases.MessageHeader;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;


public class BatchUploadMessagesOperation extends Operation {
    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final String server;
    private final IdentityAndUid[] messageIdentitiesAndUids;
    private final List<IdentityAndUid> tooManyHeadersUnsentMessageUids;
    private final List<IdentityAndUid> identityInactiveMessageUids;

    public BatchUploadMessagesOperation(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, String server, IdentityAndUid[] messageIdentitiesAndUids) {
        super();
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.server = server;
        this.messageIdentitiesAndUids = messageIdentitiesAndUids;
        this.tooManyHeadersUnsentMessageUids = new ArrayList<>();
        this.identityInactiveMessageUids = new ArrayList<>();
    }

    public List<IdentityAndUid> getIdentityInactiveMessageUids() {
        return identityInactiveMessageUids;
    }

    public List<IdentityAndUid> getTooManyHeadersUnsentMessageUids() {
        return tooManyHeadersUnsentMessageUids;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            try {
                List<OutboxMessageAndHeaders> outboxMessageAndHeaders = new ArrayList<>();
                int totalHeaders = 0;

                Logger.d("BatchUploadMessagesOperation uploading a batch of " + messageIdentitiesAndUids.length);

                HashMap<Identity, List<UID>> messageUidsByIdentity = new HashMap<>();
                for (IdentityAndUid identityAndUid : messageIdentitiesAndUids) {
                    List<UID> list = messageUidsByIdentity.get(identityAndUid.identity);
                    if (list == null) {
                        list = new ArrayList<>();
                        messageUidsByIdentity.put(identityAndUid.identity, list);
                    }
                    list.add(identityAndUid.uid);
                }

                for (Map.Entry<Identity, List<UID>> entry : messageUidsByIdentity.entrySet()) {
                    Identity ownedIdentity = entry.getKey();
                    List<UID> messageUids = entry.getValue();
                    // we need to block sending message for any inactive ownedIdentity, but, if the ownedIdentity was deleted, we should send the message
                    // this is required for the OwnedIdentityDeletion protocol, to inform your contacts
                    if (!sendManagerSession.identityDelegate.isActiveOwnedIdentity(sendManagerSession.session,ownedIdentity)
                            && sendManagerSession.identityDelegate.isOwnedIdentity(sendManagerSession.session, ownedIdentity)) {
                        for (UID messageUid : messageUids) {
                            identityInactiveMessageUids.add(new IdentityAndUid(ownedIdentity, messageUid));
                        }
                    } else {
                        OutboxMessage[] outboxMessages = OutboxMessage.getManyWithoutUidFromServer(sendManagerSession, ownedIdentity, server, messageUids.toArray(new UID[0]));
                        for (OutboxMessage outboxMessage : outboxMessages) {
                            if (totalHeaders > Constants.MAX_UPLOAD_MESSAGE_BATCH_HEADER_COUNT) {
                                tooManyHeadersUnsentMessageUids.add(new IdentityAndUid(outboxMessage.getOwnedIdentity(), outboxMessage.getUid()));
                            } else {
                                MessageHeader[] headers = outboxMessage.getHeaders();
                                outboxMessageAndHeaders.add(new OutboxMessageAndHeaders(outboxMessage, headers));
                                totalHeaders += headers.length;
                            }
                        }
                    }
                }

                Logger.d("Total header count for this batch: " + totalHeaders);

                if (cancelWasRequested()) {
                    return;
                }

                BatchUploadMessagesServerMethod serverMethod = new BatchUploadMessagesServerMethod(server, outboxMessageAndHeaders.toArray(new OutboxMessageAndHeaders[0]));
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(true);

                sendManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        for (OutboxMessageAndHeaders outboxMessageAndHeader : serverMethod.getOutboxMessageAndHeaders()) {
                            outboxMessageAndHeader.outboxMessage.setUidFromServer(outboxMessageAndHeader.uidFromServer, outboxMessageAndHeader.nonce, outboxMessageAndHeader.timestampFromServer);
                        }

                        finished = true;
                        return;
                    case ServerMethod.OK_WITH_MALFORMED_SERVER_RESPONSE:
                        // unable to parse server response and get message Uids --> finish the operation
                        for (OutboxMessageAndHeaders outboxMessageAndHeader : outboxMessageAndHeaders) {
                            outboxMessageAndHeader.outboxMessage.setUidFromServer(new UID(new byte[UID.UID_LENGTH]), new byte[0], 0);
                        }
                        finished = true;
                        return;
                    case ServerMethod.PAYLOAD_TOO_LARGE: {
                        cancel(BatchUploadMessagesCompositeOperation.RFC_BATCH_TOO_LARGE);
                        break;
                    }
                    case ServerMethod.GENERAL_ERROR:
                    default:
                        cancel(BatchUploadMessagesCompositeOperation.RFC_NETWORK_ERROR);
                }
            } catch (Exception e) {
                Logger.x(e);
                sendManagerSession.session.rollback();
            } finally {
                if (finished) {
                    sendManagerSession.session.commit();
                    setFinished();
                } else {
                    if (hasNoReasonForCancel()) {
                        cancel(null);
                    }
                    processCancel();
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
            cancel(null);
            processCancel();
        }
    }
}

class OutboxMessageAndHeaders {
    final OutboxMessage outboxMessage;
    final MessageHeader[] headers;

    UID uidFromServer = null;
    byte[] nonce = null;
    long timestampFromServer = 0;

    public OutboxMessageAndHeaders(OutboxMessage outboxMessage, MessageHeader[] headers) {
        this.outboxMessage = outboxMessage;
        this.headers = headers;
    }
}

class BatchUploadMessagesServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/batchUploadMessages";

    private final String server;
    private final OutboxMessageAndHeaders[] outboxMessageAndHeaders;


    public OutboxMessageAndHeaders[] getOutboxMessageAndHeaders() {
        return outboxMessageAndHeaders;
    }

    BatchUploadMessagesServerMethod(String server, OutboxMessageAndHeaders[] outboxMessageAndHeaders) {
        this.server = server;
        this.outboxMessageAndHeaders = outboxMessageAndHeaders;
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
        Encoded[] encodeds = new Encoded[outboxMessageAndHeaders.length];
        for (int i=0; i<outboxMessageAndHeaders.length; i++) {
            MessageHeader[] headers = outboxMessageAndHeaders[i].headers;

            Encoded[] encodedHeaders = new Encoded[headers.length * 3];
            for (int j=0; j<headers.length; j++) {
                encodedHeaders[3*j] = Encoded.of(headers[j].getDeviceUid());
                encodedHeaders[3*j+1] = Encoded.of(headers[j].getWrappedKey());
                encodedHeaders[3*j+2] = Encoded.of(headers[j].getToIdentity());
            }
            encodeds[i] = Encoded.of(new Encoded[]{
                    Encoded.of(encodedHeaders),
                    Encoded.of(outboxMessageAndHeaders[i].outboxMessage.getEncryptedContent()),
                    Encoded.of(outboxMessageAndHeaders[i].outboxMessage.isApplicationMessage()),
                    Encoded.of(outboxMessageAndHeaders[i].outboxMessage.isVoipMessage())
            });
        }
        return Encoded.of(encodeds).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        if (returnStatus == ServerMethod.OK) {
            if (receivedData.length != outboxMessageAndHeaders.length) {
                returnStatus = ServerMethod.OK_WITH_MALFORMED_SERVER_RESPONSE;
            } else {
                try {
                    for (int i = 0; i < outboxMessageAndHeaders.length; i++) {
                        Encoded[] encodeds = receivedData[i].decodeList();
                        outboxMessageAndHeaders[i].uidFromServer = encodeds[0].decodeUid();
                        outboxMessageAndHeaders[i].nonce = encodeds[1].decodeBytes();
                        outboxMessageAndHeaders[i].timestampFromServer = encodeds[2].decodeLong();
                    }
                } catch (Exception e) {
                    Logger.x(e);
                    returnStatus = ServerMethod.OK_WITH_MALFORMED_SERVER_RESPONSE;
                }
            }
        }
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }
}
