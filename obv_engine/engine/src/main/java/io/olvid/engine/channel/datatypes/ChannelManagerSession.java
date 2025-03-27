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

package io.olvid.engine.channel.datatypes;


import java.sql.SQLException;

import io.olvid.engine.datatypes.Session;
import io.olvid.engine.metamanager.EncryptionForIdentityDelegate;
import io.olvid.engine.metamanager.FullRatchetProtocolStarterDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.NetworkFetchDelegate;
import io.olvid.engine.metamanager.NetworkSendDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.PreKeyEncryptionDelegate;
import io.olvid.engine.metamanager.ProtocolDelegate;
import io.olvid.engine.protocol.datatypes.ProtocolStarterDelegate;


public class ChannelManagerSession implements AutoCloseable {
    public final Session session;
    public final FullRatchetProtocolStarterDelegate fullRatchetProtocolStarterDelegate;
    public final NetworkFetchDelegate networkFetchDelegate;
    public final NetworkSendDelegate networkSendDelegate;
    public final ProtocolDelegate protocolDelegate;
    public final EncryptionForIdentityDelegate encryptionForIdentityDelegate;
    public final PreKeyEncryptionDelegate preKeyEncryptionDelegate;
    public final IdentityDelegate identityDelegate;
    public final NotificationPostingDelegate notificationPostingDelegate;
    public final ProtocolStarterDelegate protocolStarterDelegate;

    public ChannelManagerSession(Session session, FullRatchetProtocolStarterDelegate fullRatchetProtocolStarterDelegate, NetworkFetchDelegate networkFetchDelegate, NetworkSendDelegate networkSendDelegate, ProtocolDelegate protocolDelegate, EncryptionForIdentityDelegate encryptionForIdentityDelegate, PreKeyEncryptionDelegate preKeyEncryptionDelegate, IdentityDelegate identityDelegate, NotificationPostingDelegate notificationPostingDelegate, ProtocolStarterDelegate protocolStarterDelegate) {
        this.session = session;
        this.fullRatchetProtocolStarterDelegate = fullRatchetProtocolStarterDelegate;
        this.networkFetchDelegate = networkFetchDelegate;
        this.networkSendDelegate = networkSendDelegate;
        this.protocolDelegate = protocolDelegate;
        this.encryptionForIdentityDelegate = encryptionForIdentityDelegate;
        this.preKeyEncryptionDelegate = preKeyEncryptionDelegate;
        this.identityDelegate = identityDelegate;
        this.notificationPostingDelegate = notificationPostingDelegate;
        this.protocolStarterDelegate = protocolStarterDelegate;
    }

    @Override
    public void close() throws SQLException {
        session.close();
    }
}
