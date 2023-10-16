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

package io.olvid.engine.protocol.datatypes;


import java.sql.SQLException;

import io.olvid.engine.datatypes.Session;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.metamanager.ChannelDelegate;
import io.olvid.engine.metamanager.EncryptionForIdentityDelegate;
import io.olvid.engine.metamanager.EngineOwnedIdentityCleanupDelegate;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.metamanager.NotificationListeningDelegate;
import io.olvid.engine.metamanager.NotificationPostingDelegate;
import io.olvid.engine.metamanager.ProtocolDelegate;
import io.olvid.engine.metamanager.PushNotificationDelegate;

public class ProtocolManagerSession implements AutoCloseable {
    public final Session session;
    public final ChannelDelegate channelDelegate;
    public final IdentityDelegate identityDelegate;
    public final EncryptionForIdentityDelegate encryptionForIdentityDelegate;
    public final ProtocolReceivedMessageProcessorDelegate protocolReceivedMessageProcessorDelegate;
    public final ProtocolStarterDelegate protocolStarterDelegate;
    public final ProtocolDelegate protocolDelegate;
    public final NotificationPostingDelegate notificationPostingDelegate;
    public final NotificationListeningDelegate notificationListeningDelegate;
    public final EngineOwnedIdentityCleanupDelegate engineOwnedIdentityCleanupDelegate;
    public final PushNotificationDelegate pushNotificationDelegate;
    public final String engineBaseDirectory;
    public final ObvBackupAndSyncDelegate identityBackupAndSyncDelegate;
    public final ObvBackupAndSyncDelegate appBackupAndSyncDelegate;

    public ProtocolManagerSession(Session session, ChannelDelegate channelDelegate, IdentityDelegate identityDelegate, EncryptionForIdentityDelegate encryptionForIdentityDelegate, ProtocolReceivedMessageProcessorDelegate protocolReceivedMessageProcessorDelegate, ProtocolStarterDelegate protocolStarterDelegate, ProtocolDelegate protocolDelegate, NotificationPostingDelegate notificationPostingDelegate, NotificationListeningDelegate notificationListeningDelegate, EngineOwnedIdentityCleanupDelegate engineOwnedIdentityCleanupDelegate, PushNotificationDelegate pushNotificationDelegate, String engineBaseDirectory, ObvBackupAndSyncDelegate identityBackupAndSyncDelegate, ObvBackupAndSyncDelegate appBackupAndSyncDelegate) {
        this.session = session;
        this.channelDelegate = channelDelegate;
        this.identityDelegate = identityDelegate;
        this.encryptionForIdentityDelegate = encryptionForIdentityDelegate;
        this.protocolReceivedMessageProcessorDelegate = protocolReceivedMessageProcessorDelegate;
        this.protocolStarterDelegate = protocolStarterDelegate;
        this.protocolDelegate = protocolDelegate;
        this.notificationPostingDelegate = notificationPostingDelegate;
        this.notificationListeningDelegate = notificationListeningDelegate;
        this.engineOwnedIdentityCleanupDelegate = engineOwnedIdentityCleanupDelegate;
        this.pushNotificationDelegate = pushNotificationDelegate;
        this.engineBaseDirectory = engineBaseDirectory;
        this.identityBackupAndSyncDelegate = identityBackupAndSyncDelegate;
        this.appBackupAndSyncDelegate = appBackupAndSyncDelegate;
    }

    @Override
    public void close() throws SQLException {
        session.close();
    }
}
