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

import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

public class ProcessWebsocketReceivedMessageOperation extends Operation {
    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final Identity ownedIdentity;
    private final UID deviceUid;
    private final byte[] messagePayload;

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public UID getDeviceUid() {
        return deviceUid;
    }

    public ProcessWebsocketReceivedMessageOperation(FetchManagerSessionFactory fetchManagerSessionFactory, Identity ownedIdentity, UID deviceUid, byte[] messagePayload, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(computeUniqueUid(ownedIdentity, messagePayload), onFinishCallback, onCancelCallback);

        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.ownedIdentity = ownedIdentity;
        this.deviceUid = deviceUid;
        this.messagePayload = messagePayload;
    }

    private static UID computeUniqueUid(Identity ownedIdentity, byte[] messagePayload) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        byte[] input = new byte[ownedIdentity.getBytes().length + messagePayload.length];
        System.arraycopy(ownedIdentity.getBytes(), 0, input, 0, ownedIdentity.getBytes().length);
        System.arraycopy(messagePayload, 0, input, ownedIdentity.getBytes().length, messagePayload.length);
        return new UID(sha256.digest(input));
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            boolean finished = false;

            try {
                Encoded[] parts = new Encoded(messagePayload).decodeList();
                if (parts.length != 4) {
                    return;
                }

                UID messageUid = parts[0].decodeUid();
                long serverTimestamp = parts[1].decodeLong();
                EncryptedBytes wrappedKey = parts[2].decodeEncryptedData();
                EncryptedBytes messageContent = parts[3].decodeEncryptedData();

                InboxMessage message = InboxMessage.get(fetchManagerSession, ownedIdentity, messageUid);
                if (message == null) {
                    message = InboxMessage.create(fetchManagerSession,
                            ownedIdentity,
                            messageUid,
                            messageContent,
                            wrappedKey,
                            serverTimestamp,
                            serverTimestamp, // we assume that downloadTimestamp is equal to serverTimestamp as websocket received messages are received immediately
                            System.currentTimeMillis(),
                            false
                    );
                    if (message == null) {
                        return;
                    }
                }
                finished = true;
            } catch (Exception e) {
                e.printStackTrace();
                fetchManagerSession.session.rollback();
            } finally {
                if (finished) {
                    fetchManagerSession.session.commit();
                    setFinished();
                } else {
                    if (hasNoReasonForCancel()) {
                        cancel(null);
                    }
                    processCancel();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            cancel(null);
            processCancel();
        }
    }
}
