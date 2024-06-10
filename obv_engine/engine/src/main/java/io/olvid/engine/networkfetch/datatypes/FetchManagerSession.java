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

package io.olvid.engine.networkfetch.datatypes;


import java.sql.SQLException;

import io.olvid.engine.datatypes.Session;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.networkfetch.databases.InboxAttachment;
import io.olvid.engine.networkfetch.databases.InboxMessage;
import io.olvid.engine.networkfetch.databases.PendingServerQuery;
import io.olvid.engine.networkfetch.databases.PushNotificationConfiguration;

public class FetchManagerSession implements AutoCloseable {

    public final Session session;
    public final InboxMessage.InboxMessageListener inboxMessageListener;
    public final InboxMessage.ExtendedPayloadListener extendedPayloadListener;
    public final InboxMessage.MarkAsListedAndDeleteOnServerListener markAsListedAndDeleteOnServerListener;
    public final InboxAttachment.InboxAttachmentListener inboxAttachmentListener;
    public final PushNotificationConfiguration.NewPushNotificationConfigurationListener newPushNotificationConfigurationListener;
    public final PendingServerQuery.PendingServerQueryListener pendingServerQueryListener;
    public final IdentityDelegate identityDelegate;
    public final String engineBaseDirectory;
    public final NotificationPostingDelegate notificationPostingDelegate;
    public final CreateServerSessionDelegate createServerSessionDelegate;

    public FetchManagerSession(Session session,
                               InboxMessage.InboxMessageListener inboxMessageListener,
                               InboxMessage.ExtendedPayloadListener extendedPayloadListener,
                               InboxMessage.MarkAsListedAndDeleteOnServerListener markAsListedAndDeleteOnServerListener,
                               InboxAttachment.InboxAttachmentListener inboxAttachmentListener,
                               PushNotificationConfiguration.NewPushNotificationConfigurationListener newPushNotificationConfigurationListener,
                               PendingServerQuery.PendingServerQueryListener pendingServerQueryListener,
                               IdentityDelegate identityDelegate,
                               String engineBaseDirectory,
                               NotificationPostingDelegate notificationPostingDelegate,
                               CreateServerSessionDelegate createServerSessionDelegate) {
        this.session = session;
        this.inboxMessageListener = inboxMessageListener;
        this.extendedPayloadListener = extendedPayloadListener;
        this.markAsListedAndDeleteOnServerListener = markAsListedAndDeleteOnServerListener;
        this.inboxAttachmentListener = inboxAttachmentListener;
        this.newPushNotificationConfigurationListener = newPushNotificationConfigurationListener;
        this.pendingServerQueryListener = pendingServerQueryListener;
        this.identityDelegate = identityDelegate;
        this.engineBaseDirectory = engineBaseDirectory;
        this.notificationPostingDelegate = notificationPostingDelegate;
        this.createServerSessionDelegate = createServerSessionDelegate;
    }

    @Override
    public void close() throws SQLException {
        session.close();
    }
}
