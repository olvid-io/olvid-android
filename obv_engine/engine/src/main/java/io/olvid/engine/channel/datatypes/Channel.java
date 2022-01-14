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

package io.olvid.engine.channel.datatypes;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.SendChannelInfo;



public abstract class Channel {
    protected int obliviousEngineVersion;

    public static UID post(ChannelManagerSession channelManagerSession, ChannelMessageToSend message, PRNGService prng) throws Exception {
        switch (message.getSendChannelInfo().getChannelType()) {
            case SendChannelInfo.OBLIVIOUS_CHANNEL_TYPE:
            case SendChannelInfo.ALL_CONFIRMED_OBLIVIOUS_CHANNELS_ON_SAME_SERVER_TYPE:
            case SendChannelInfo.ASYMMETRIC_CHANNEL_TYPE:
            case SendChannelInfo.ASYMMETRIC_BROADCAST_CHANNEL_TYPE:
            case SendChannelInfo.ALL_OWNED_CONFIRMED_OBLIVIOUS_CHANNELS_TYPE:
                return NetworkChannel.post(channelManagerSession, message, prng);
            case SendChannelInfo.LOCAL_TYPE:
                return LocalChannel.post(channelManagerSession, message, prng);
            case SendChannelInfo.USER_INTERFACE_TYPE:
                return UserInterfaceChannel.post(channelManagerSession, message, prng);
            case SendChannelInfo.SERVER_QUERY_TYPE:
                return ServerQueryChannel.post(channelManagerSession, message, prng);
        }
        return null;
    }

    public int getObliviousEngineVersion() {
        return obliviousEngineVersion;
    }
}
