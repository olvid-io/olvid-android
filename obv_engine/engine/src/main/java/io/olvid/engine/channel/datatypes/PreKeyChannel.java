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

package io.olvid.engine.channel.datatypes;


import java.sql.SQLException;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.AuthEncKeyAndChannelInfo;
import io.olvid.engine.datatypes.containers.MessageToSend;
import io.olvid.engine.datatypes.containers.NetworkReceivedMessage;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.metamanager.PreKeyEncryptionDelegate;

public class PreKeyChannel extends NetworkChannel {

    private final Session session;
    private final Identity fromIdentity;
    private final Identity toIdentity;
    private final UID toDeviceUid;
    private final PreKeyEncryptionDelegate preKeyEncryptionDelegate;

    public PreKeyChannel(Session session, Identity fromIdentity, Identity toIdentity, UID toDeviceUid, PreKeyEncryptionDelegate preKeyEncryptionDelegate) {
        this.session = session;
        this.fromIdentity = fromIdentity;
        this.toIdentity = toIdentity;
        this.toDeviceUid = toDeviceUid;
        this.preKeyEncryptionDelegate = preKeyEncryptionDelegate;
    }

    @Override
    public MessageToSend.Header wrapMessageKey(AuthEncKey messageKey, PRNGService prng, boolean partOfFullRatchetProtocol) {
        if (preKeyEncryptionDelegate == null) {
            return null;
        }
        EncryptedBytes wrappedKey = preKeyEncryptionDelegate.wrapWithPreKey(session, messageKey, fromIdentity, toIdentity, toDeviceUid, prng);
        return new MessageToSend.Header(toDeviceUid, toIdentity, wrappedKey);
    }

    public static AuthEncKeyAndChannelInfo unwrapMessageKey(ChannelManagerSession channelManagerSession, NetworkReceivedMessage.Header header) throws SQLException {
        if (channelManagerSession.preKeyEncryptionDelegate == null) {
            return null;
        }
        return channelManagerSession.preKeyEncryptionDelegate.unwrapWithPreKey(channelManagerSession.session, header.getWrappedKey(), header.getOwnedIdentity());
    }
}
