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

package io.olvid.engine.metamanager;


import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ProtocolReceivedDialogResponse;
import io.olvid.engine.datatypes.containers.ProtocolReceivedMessage;
import io.olvid.engine.datatypes.containers.ProtocolReceivedServerResponse;

public interface ProtocolDelegate {
    void abortProtocol(Session session, UID protocolInstanceUid, Identity ownedIdentity) throws Exception;
    void process(Session session, ProtocolReceivedMessage message) throws Exception;
    void process(Session session, ProtocolReceivedDialogResponse message) throws Exception;
    void process(Session session, ProtocolReceivedServerResponse message) throws Exception;

    boolean isChannelCreationInProgress(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws Exception;
}
