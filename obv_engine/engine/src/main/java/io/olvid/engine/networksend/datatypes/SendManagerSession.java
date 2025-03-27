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

package io.olvid.engine.networksend.datatypes;


import java.sql.SQLException;

import io.olvid.engine.datatypes.Session;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networksend.databases.OutboxAttachment;
import io.olvid.engine.networksend.databases.OutboxMessage;
import io.olvid.engine.networksend.databases.ReturnReceipt;

public class SendManagerSession implements AutoCloseable {
    public final Session session;
    public final OutboxMessage.NewOutboxMessageListener newOutboxMessageListener;
    public final OutboxAttachment.OutboxAttachmentCanBeSentListener outboxAttachmentCanBeSentListener;
    public final OutboxAttachment.OutboxAttachmentCancelRequestedListener outboxAttachmentCancelRequestedListener;
    public final NotificationPostingDelegate notificationPostingDelegate;
    public final ReturnReceipt.NewReturnReceiptListener newReturnReceiptListener;
    public final IdentityDelegate identityDelegate;
    public final String engineBaseDirectory;

    public SendManagerSession(Session session,
                              OutboxMessage.NewOutboxMessageListener newOutboxMessageListener,
                              OutboxAttachment.OutboxAttachmentCanBeSentListener outboxAttachmentCanBeSentListener,
                              OutboxAttachment.OutboxAttachmentCancelRequestedListener outboxAttachmentCancelRequestedListener,
                              NotificationPostingDelegate notificationPostingDelegate,
                              ReturnReceipt.NewReturnReceiptListener newReturnReceiptListener,
                              IdentityDelegate identityDelegate,
                              String engineBaseDirectory) {
        this.session = session;
        this.newOutboxMessageListener = newOutboxMessageListener;
        this.outboxAttachmentCanBeSentListener = outboxAttachmentCanBeSentListener;
        this.outboxAttachmentCancelRequestedListener = outboxAttachmentCancelRequestedListener;
        this.notificationPostingDelegate = notificationPostingDelegate;
        this.newReturnReceiptListener = newReturnReceiptListener;
        this.identityDelegate = identityDelegate;
        this.engineBaseDirectory = engineBaseDirectory;
    }

    @Override
    public void close() throws SQLException {
        session.close();
    }
}
