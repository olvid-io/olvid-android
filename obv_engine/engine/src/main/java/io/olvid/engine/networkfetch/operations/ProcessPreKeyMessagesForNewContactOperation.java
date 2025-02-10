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

package io.olvid.engine.networkfetch.operations;


import java.sql.SQLException;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.PriorityOperation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

public class ProcessPreKeyMessagesForNewContactOperation extends PriorityOperation {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final Identity ownedIdentity;
    private final Identity contactIdentity;

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public Identity getContactIdentity() {
        return contactIdentity;
    }

    public ProcessPreKeyMessagesForNewContactOperation(FetchManagerSessionFactory fetchManagerSessionFactory, Identity ownedIdentity, Identity contactIdentity, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(computeUniqueUid(ownedIdentity, contactIdentity), onFinishCallback, onCancelCallback);

        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.ownedIdentity = ownedIdentity;
        this.contactIdentity = contactIdentity;
    }

    @Override
    public long getPriority() {
        return 1;
    }

    private static UID computeUniqueUid(Identity ownedIdentity, Identity contactIdentity) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] input = new byte[ownedIdentity.getBytes().length + contactIdentity.getBytes().length];
        System.arraycopy(ownedIdentity.getBytes(), 0, input, 0, ownedIdentity.getBytes().length);
        System.arraycopy(contactIdentity.getBytes(), 0, input, ownedIdentity.getBytes().length, contactIdentity.getBytes().length);
        return new UID(sha256.digest(input));
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                List<InboxMessage> inboxMessages = InboxMessage.getPendingPreKeyMessages(fetchManagerSession, ownedIdentity, contactIdentity);
                Logger.i("Found " + inboxMessages.size() + " pending PreKey inbox messages to process following a contact addition.");
                for (InboxMessage inboxMessage : inboxMessages) {
                    fetchManagerSession.inboxMessageListener.messageWasDownloaded(inboxMessage.getNetworkReceivedMessage());
                }
            } catch (Exception e) {
                Logger.x(e);
            } finally {
                setFinished();
            }
        } catch (SQLException e) {
            Logger.x(e);
            cancel(null);
            processCancel();
        }
    }
}
