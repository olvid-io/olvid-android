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

package io.olvid.engine.protocol.datatypes;


import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelDialogMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelProtocolMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelServerQueryMessageToSend;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.encoder.Encoded;

public class GenericProtocolMessageToSend {
    private final SendChannelInfo sendChannelInfo;
    private final Encoded encodedElements;
    private final boolean partOfFullRatchetProtocolOfTheSendSeed;
    private final boolean hasUserContent;

    public GenericProtocolMessageToSend(SendChannelInfo sendChannelInfo, int protocolId, UID protocolInstanceUid, int protocolMessageId, Encoded[] inputs, boolean partOfFullRatchetProtocolOfTheSendSeed, boolean hasUserContent) {
        this.sendChannelInfo = sendChannelInfo;
        this.encodedElements = encode(protocolId, protocolInstanceUid, protocolMessageId, inputs);
        this.partOfFullRatchetProtocolOfTheSendSeed = partOfFullRatchetProtocolOfTheSendSeed;
        this.hasUserContent = hasUserContent;
    }

    private static Encoded encode(int protocolId, UID protocolInstanceUid, int protocolMessageId, Encoded[] inputs) {
        return Encoded.of(new Encoded[]{
                Encoded.of(protocolId),
                Encoded.of(protocolInstanceUid),
                Encoded.of(protocolMessageId),
                Encoded.of(inputs)
        });
    }

    public ChannelProtocolMessageToSend generateChannelProtocolMessageToSend() {
        switch (sendChannelInfo.getChannelType()) {
            case SendChannelInfo.LOCAL_TYPE:
            case SendChannelInfo.OBLIVIOUS_CHANNEL_TYPE:
            case SendChannelInfo.ASYMMETRIC_CHANNEL_TYPE:
            case SendChannelInfo.ALL_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_ON_SAME_SERVER_TYPE:
            case SendChannelInfo.ASYMMETRIC_BROADCAST_CHANNEL_TYPE:
            case SendChannelInfo.ALL_OWNED_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_TYPE:
            case SendChannelInfo.OBLIVIOUS_CHANNEL_OR_PRE_KEY_TYPE:
                return new ChannelProtocolMessageToSend(sendChannelInfo, encodedElements, partOfFullRatchetProtocolOfTheSendSeed, hasUserContent);
            default:
                return null;
        }
    }

    public ChannelDialogMessageToSend generateChannelDialogMessageToSend() {
        //noinspection SwitchStatementWithTooFewBranches
        switch (sendChannelInfo.getChannelType()) {
            case SendChannelInfo.USER_INTERFACE_TYPE:
                return new ChannelDialogMessageToSend(sendChannelInfo.getDialogUuid(), sendChannelInfo.getToIdentity(), sendChannelInfo.getDialogType(), encodedElements);
            default:
                return null;
        }
    }

    public ChannelServerQueryMessageToSend generateChannelServerQueryMessageToSend() {
        //noinspection SwitchStatementWithTooFewBranches
        switch (sendChannelInfo.getChannelType()) {
            case SendChannelInfo.SERVER_QUERY_TYPE:
                return new ChannelServerQueryMessageToSend(sendChannelInfo.getToIdentity(), sendChannelInfo.getServerQueryType(), encodedElements);
            default:
                return null;
        }
    }

}
