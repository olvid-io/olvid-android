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

package io.olvid.engine.networkfetch.operations;

import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.metamanager.SolveChallengeDelegate;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

public class CreateServerSessionCompositeOperation extends Operation implements Operation.OnFinishCallback {
    // possible reasons for cancel
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_SESSION_CANNOT_BE_FOUND = 2;
    public static final int RFC_SESSION_DOES_NOT_CONTAIN_A_CHALLENGE = 3;
    public static final int RFC_SESSION_DOES_NOT_CONTAIN_A_RESPONSE = 4;
    public static final int RFC_SESSION_DOES_NOT_CONTAIN_A_NONCE = 5;
    public static final int RFC_IDENTITY_NOT_FOUND = 6;
    public static final int RFC_INVALID_SESSION = 7;
//    public static final int RFC_API_KEY_REJECTED = 8;

    private final Identity ownedIdentity;
    private final Operation[] suboperations;

    private ServerSession.ApiKeyStatus apiKeyStatus;
    private List<ServerSession.Permission> permissions;
    private long apiKeyExpirationTimestamp;

    public CreateServerSessionCompositeOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, SolveChallengeDelegate solveChallengeDelegate, Operation.OnFinishCallback onFinishCallback, Operation.OnCancelCallback onCancelCallback) {
        super(ownedIdentity.computeUniqueUid(), onFinishCallback, onCancelCallback);
        this.ownedIdentity = ownedIdentity;

        this.suboperations = new Operation[3];
        suboperations[0] = new RequestChallengeOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity);
        suboperations[1] = new SolveChallengeOperation(fetchManagerSessionFactory, ownedIdentity, solveChallengeDelegate);
        suboperations[2] = new GetTokenOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, this);

        for (int i = 0; i<suboperations.length-1; i++) {
            suboperations[i+1].addDependency(suboperations[i]);
        }
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public ServerSession.ApiKeyStatus getApiKeyStatus() {
        return apiKeyStatus;
    }

    public List<ServerSession.Permission> getPermissions() {
        return permissions;
    }

    public long getApiKeyExpirationTimestamp() {
        return apiKeyExpirationTimestamp;
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
            queue.execute(1, "Engine-CreateServerSessionCompositeOperation");
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

    @Override
    public void onFinishCallback(Operation operation) {
        if (operation instanceof GetTokenOperation) {
            GetTokenOperation op = (GetTokenOperation) operation;
            apiKeyStatus = op.getApiKeyStatus();
            permissions = op.getPermissions();
            apiKeyExpirationTimestamp = op.getApiKeyExpirationTimestamp();
        }
    }
}
