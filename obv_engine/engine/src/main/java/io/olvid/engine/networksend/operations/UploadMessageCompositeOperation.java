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

package io.olvid.engine.networksend.operations;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;


public class UploadMessageCompositeOperation extends Operation {
    // possible reasons for cancel
    public static final int RFC_MESSAGE_NOT_FOUND_IN_DATABASE = 1;
    public static final int RFC_NETWORK_ERROR = 3;
    public static final int RFC_IDENTITY_IS_INACTIVE = 10;
    public static final int RFC_OK_WITH_MALFORMED_SERVER_RESPONSE = 11;

    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final UID messageUid;
    private final Operation[] suboperations;

    public UploadMessageCompositeOperation(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, UID messageUid, Operation.OnFinishCallback onFinishCallback, Operation.OnCancelCallback onCancelCallback) {
        super(IdentityAndUid.computeUniqueUid(ownedIdentity, messageUid), onFinishCallback, onCancelCallback);
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.messageUid = messageUid;
        this.suboperations = new Operation[2];

        suboperations[0] = new UploadMessageAndGetUidsOperation(sendManagerSessionFactory, sslSocketFactory, ownedIdentity, messageUid);
        suboperations[1] = new TryToDeleteMessageAndAttachmentsOperation(sendManagerSessionFactory, ownedIdentity, messageUid);

        for (int i = 0; i<suboperations.length-1; i++) {
            suboperations[i+1].addDependency(suboperations[i]);
        }
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getMessageUid() {
        return messageUid;
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
            OperationQueue queue = new OperationQueue();
            for (Operation op: suboperations) {
                queue.queue(op);
            }
            queue.execute(1, "UploadMessageCompositeOperation");
            queue.join();

            if (cancelWasRequested()) {
                return;
            }

            for (Operation op: suboperations) {
                if (op.isCancelled()) {
                    cancel(op.getReasonForCancel());
                    return;
                }
            }
            finished = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (finished) {
                setFinished();
            } else {
                cancel(null);
                processCancel();
            }
        }
    }
}
