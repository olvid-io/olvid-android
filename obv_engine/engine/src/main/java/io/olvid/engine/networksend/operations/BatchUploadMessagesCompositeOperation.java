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

package io.olvid.engine.networksend.operations;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.datatypes.containers.StringAndBoolean;
import io.olvid.engine.networksend.coordinators.SendMessageCoordinator;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;


public class BatchUploadMessagesCompositeOperation extends Operation {
    // possible reasons for cancel
    public static final int RFC_BATCH_TOO_LARGE = 2;
    public static final int RFC_NETWORK_ERROR = 3;

    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final String server;
    private final SendMessageCoordinator.MessageBatchProvider messageBatchProvider;
    private IdentityAndUid[] messageIdentitiesAndUids;
    private Operation[] suboperations;

    public BatchUploadMessagesCompositeOperation(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, String server, boolean userContentMessages, SendMessageCoordinator.MessageBatchProvider messageBatchProvider, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(StringAndBoolean.computeUniqueUid(server, userContentMessages), onFinishCallback, onCancelCallback);
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.server = server;
        this.messageBatchProvider = messageBatchProvider;
        this.messageIdentitiesAndUids = null;
        this.suboperations = null;
    }

    public String getServer() {
        return server;
    }

    public IdentityAndUid[] getMessageIdentitiesAndUids() {
        return messageIdentitiesAndUids;
    }

    public List<IdentityAndUid> getIdentityInactiveMessageUids() {
        if (suboperations != null && suboperations.length > 0) {
            return ((BatchUploadMessagesOperation) suboperations[0]).getIdentityInactiveMessageUids();
        }
        return Collections.emptyList();
    }

    @Override
    public void doCancel() {
        for (Operation op: suboperations) {
            op.cancel(null);
        }
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        try {
            // first get some messageUids from the provider
            this.messageIdentitiesAndUids = messageBatchProvider.getBatchOFMessageUids();
            if (messageIdentitiesAndUids.length == 0) {
                suboperations = new Operation[0];
            } else {
                suboperations = new Operation[messageIdentitiesAndUids.length + 1];

                suboperations[0] = new BatchUploadMessagesOperation(sendManagerSessionFactory, sslSocketFactory, server, messageIdentitiesAndUids);
                for (int i = 0; i < messageIdentitiesAndUids.length; i++) {
                    suboperations[i + 1] = new TryToDeleteMessageAndAttachmentsOperation(sendManagerSessionFactory, messageIdentitiesAndUids[i].identity, messageIdentitiesAndUids[i].uid);
                    suboperations[i + 1].addDependency(suboperations[0]);
                }
            }

            // now run the suboperations
            if (suboperations.length > 0) {
                OperationQueue queue = new OperationQueue();
                for (Operation op : suboperations) {
                    queue.queue(op);
                }
                queue.execute(1, "BatchUploadMessagesCompositeOperation");
                queue.join();

                if (cancelWasRequested()) {
                    return;
                }

                for (Operation op : suboperations) {
                    if (op.isCancelled()) {
                        cancel(op.getReasonForCancel());
                        return;
                    }
                }
            }
            finished = true;
        } catch (Exception e) {
            Logger.x(e);
        } finally {
            if (finished) {
                setFinished();
            } else {
                cancel(null);
                processCancel();
            }
        }
    }

    public static UID computeUniqueUid(String server) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        return new UID(sha256.digest(server.getBytes(StandardCharsets.UTF_8)));
    }
}
