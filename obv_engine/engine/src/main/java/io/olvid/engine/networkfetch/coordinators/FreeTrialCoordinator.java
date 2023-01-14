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

package io.olvid.engine.networkfetch.coordinators;


import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.OperationQueue;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;
import io.olvid.engine.networkfetch.operations.FreeTrialOperation;

public class FreeTrialCoordinator {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final OperationQueue freeTrialOperationQueue;

    public FreeTrialCoordinator(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory) {
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;

        freeTrialOperationQueue = new OperationQueue(true);
        freeTrialOperationQueue.execute(1, "Engine-FreeTrialCoordinator");
    }

    private void queueNewFreeTrialOperation(Identity ownedIdentity, boolean retrieveApiKey) {
        FreeTrialOperation op = new FreeTrialOperation(fetchManagerSessionFactory, sslSocketFactory, ownedIdentity, retrieveApiKey);
        freeTrialOperationQueue.queue(op);
    }

    public void queryFreeTrial(Identity ownedIdentity) {
        queueNewFreeTrialOperation(ownedIdentity, false);
    }

    public void startFreeTrial(Identity ownedIdentity) {
        queueNewFreeTrialOperation(ownedIdentity, true);
    }
}
