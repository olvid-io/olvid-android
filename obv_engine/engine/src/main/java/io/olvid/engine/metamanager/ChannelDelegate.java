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


import java.sql.SQLException;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;

public interface ChannelDelegate {
    // post a channel message to send
    UID post(Session session, ChannelMessageToSend channelMessageToSend, PRNGService prng) throws Exception;


    // Oblivious Channels management
    void createObliviousChannel(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity, Seed seed, int obliviousEngineVersion) throws Exception;
    void confirmObliviousChannel(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity) throws Exception;
    void updateObliviousChannelSendSeed(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity, Seed seed, int obliviousEngineVersion) throws Exception;
    void updateObliviousChannelReceiveSeed(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity, Seed seed, int obliviousEngineVersion) throws Exception;


    UID[] getConfirmedObliviousChannelDeviceUids(Session session, Identity ownedIdentity, Identity remoteIdentity) throws Exception;
    void deleteObliviousChannelsWithContact(Session session, Identity ownedIdentity, Identity remoteIdentity) throws Exception;
    void deleteObliviousChannelIfItExists(Session session, Identity ownedIdentity, UID remoteDeviceUid, Identity remoteIdentity) throws Exception;
    void deleteAllChannelsForOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
}
