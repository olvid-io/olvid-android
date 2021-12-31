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

package io.olvid.engine.networksend.operations;

import java.io.File;
import java.sql.SQLException;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndUid;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;

public class TryToDeleteMessageAndAttachmentsOperation extends Operation {
    private final Identity ownedIdentity;
    private final UID messageUid;
    private final SendManagerSessionFactory sendManagerSessionFactory;

    public TryToDeleteMessageAndAttachmentsOperation(SendManagerSessionFactory sendManagerSessionFactory, Identity ownedIdentity, UID messageUid) {
        super(IdentityAndUid.computeUniqueUid(ownedIdentity, messageUid), null, null);
        this.ownedIdentity = ownedIdentity;
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.messageUid = messageUid;
    }

    // possible reasons for cancel
    // None!

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        OutboxMessage outboxMessage;
        try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            try {
                try {
                    outboxMessage = OutboxMessage.get(sendManagerSession, ownedIdentity, messageUid);
                } catch (SQLException e) {
                    return;
                }

                if (outboxMessage == null) {
                    finished = true;
                    return;
                }
                if (outboxMessage.getUidFromServer() == null) {
                    finished = true;
                    return;
                }

                OutboxAttachment[] outboxAttachments = outboxMessage.getAttachments();
                for (OutboxAttachment outboxAttachment: outboxAttachments) {
                    if (!outboxAttachment.isAcknowledged()) {
                        finished = true;
                        return;
                    }
                }

                // everything has been acknowledged OR cancelled, we can proceed to delete everything

                for (OutboxAttachment outboxAttachment: outboxAttachments) {
                    if (outboxAttachment.shouldBeDeletedAfterSend()) {
                        File attachmentFile = new File(sendManagerSession.engineBaseDirectory, outboxAttachment.getUrl());
                        if (attachmentFile.isFile()) {
                            if (! attachmentFile.delete()) {
                                // We were unable to delete the file
                                //    -> abort to avoid loose files
                                cancel(null);
                                return;
                            }
                        }
                    }
                }

                sendManagerSession.session.startTransaction();
                outboxMessage.delete();
                finished = true;
            } catch (Exception e) {
                e.printStackTrace();
                sendManagerSession.session.rollback();
            } finally {
                if (finished) {
                    sendManagerSession.session.commit();
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
