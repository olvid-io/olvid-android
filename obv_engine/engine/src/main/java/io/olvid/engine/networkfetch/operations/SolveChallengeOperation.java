/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import java.sql.SQLException;

import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.metamanager.SolveChallengeDelegate;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;


public class SolveChallengeOperation extends Operation {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final Identity identity;
    private final SolveChallengeDelegate solveChallengeDelegate;

    public SolveChallengeOperation(FetchManagerSessionFactory fetchManagerSessionFactory, Identity identity, SolveChallengeDelegate solveChallengeDelegate) {
        super(identity.computeUniqueUid(), null, null);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.identity = identity;
        this.solveChallengeDelegate = solveChallengeDelegate;
    }

    public Identity getIdentity() {
        return identity;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        ServerSession serverSession;
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                serverSession = ServerSession.get(fetchManagerSession, identity);

                if (serverSession == null) {
                    cancel(CreateServerSessionCompositeOperation.RFC_SESSION_CANNOT_BE_FOUND);
                    return;
                }
                if (serverSession.getResponse() != null || serverSession.getToken() != null) {
                    finished = true;
                    return;
                }
                if (serverSession.getChallenge() == null) {
                    cancel(CreateServerSessionCompositeOperation.RFC_SESSION_DOES_NOT_CONTAIN_A_CHALLENGE);
                    return;
                }

                PRNGService prng = Suite.getPRNGService(PRNG.PRNG_HMAC_SHA256);
                byte[] response;
                try {
                    response = solveChallengeDelegate.solveChallenge(serverSession.getChallenge(), identity, prng);
                } catch (Exception e) {
                    cancel(CreateServerSessionCompositeOperation.RFC_IDENTITY_NOT_FOUND);
                    return;
                }

                fetchManagerSession.session.startTransaction();
                serverSession.setResponseForChallenge(serverSession.getChallenge(), response);

                finished = true;
            } catch (Exception e) {
                e.printStackTrace();
                fetchManagerSession.session.rollback();
            } finally {
                if (finished) {
                    fetchManagerSession.session.commit();
                    setFinished();
                } else {
                    cancel(null);
                    processCancel();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
