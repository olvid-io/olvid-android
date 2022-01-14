/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.engine.datatypes.containers;


import io.olvid.engine.channel.datatypes.ChannelReceivedMessage;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.Encoded;

public class ProtocolReceivedMessage {
    private final UID messageUid;
    private final Identity ownedIdentity;
    private final Encoded encodedElements;
    private final ReceptionChannelInfo receptionChannelInfo;
    private final long serverTimestamp;

    public ProtocolReceivedMessage(UID messageUid, Identity ownedIdentity, Encoded encodedElements, ReceptionChannelInfo receptionChannelInfo, long serverTimestamp) {
        this.messageUid = messageUid;
        this.ownedIdentity = ownedIdentity;
        this.encodedElements = encodedElements;
        this.receptionChannelInfo = receptionChannelInfo;
        this.serverTimestamp = serverTimestamp;
    }

    public UID getMessageUid() {
        return messageUid;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public Encoded getEncodedElements() {
        return encodedElements;
    }

    public ReceptionChannelInfo getReceptionChannelInfo() {
        return receptionChannelInfo;
    }

    public long getServerTimestamp() {
        return serverTimestamp;
    }

    public static ProtocolReceivedMessage of(ChannelReceivedMessage message) {
        if (message.getMessageType() != MessageType.PROTOCOL_MESSAGE_TYPE) {
            return null;
        }
        return new ProtocolReceivedMessage(message.getMessageUid(),
                message.getOwnedIdentity(),
                message.getEncodedElements(),
                message.getReceptionChannelInfo(),
                message.getMessage().getServerTimestamp());
    }
}
