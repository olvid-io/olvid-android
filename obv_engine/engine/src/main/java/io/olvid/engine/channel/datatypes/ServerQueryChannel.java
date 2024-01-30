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
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelServerQueryMessageToSend;
import io.olvid.engine.datatypes.containers.MessageType;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.containers.ServerQuery;

public class ServerQueryChannel extends Channel {
    private ServerQueryChannel() { }

    private void doPost(ChannelManagerSession channelManagerSession, ChannelMessageToSend message, PRNGService prng) throws Exception {
        switch (message.getMessageType()) {
            case MessageType.SERVER_QUERY_TYPE:
                ChannelServerQueryMessageToSend channelServerQueryMessageToSend = (ChannelServerQueryMessageToSend) message;
                ServerQuery serverQuery = new ServerQuery(
                        channelServerQueryMessageToSend.getEncodedElements(),
                        channelServerQueryMessageToSend.getSendChannelInfo().getToIdentity(),
                        channelServerQueryMessageToSend.getSendChannelInfo().getServerQueryType()
                );
                channelManagerSession.networkFetchDelegate.createPendingServerQuery(channelManagerSession.session, serverQuery);
                break;
            default:
                Logger.i("Trying to post a message of type " + message.getMessageType() + " on a ServerQueryChannel.");
        }
    }

    public static UID post(ChannelManagerSession channelManagerSession, ChannelMessageToSend message, PRNGService prng) throws Exception {
        ServerQueryChannel[] serverQueryChannels = acceptableChannelsForPosting(channelManagerSession, message);
        if (serverQueryChannels.length == 0) {
            Logger.i("No acceptable channels were found for posting");
            throw new Exception();
        }
        for (ServerQueryChannel serverQueryChannel: serverQueryChannels) {
            serverQueryChannel.doPost(channelManagerSession, message, prng);
        }
        return null;
    }

    private static ServerQueryChannel[] acceptableChannelsForPosting(ChannelManagerSession channelManagerSession, ChannelMessageToSend message) throws SQLException {
        switch (message.getSendChannelInfo().getChannelType()) {
            case SendChannelInfo.SERVER_QUERY_TYPE:
                // Check that the toIdentity is an OwnedIdentity
                if (channelManagerSession.identityDelegate.isOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getToIdentity())
                        || Objects.equals(message.getSendChannelInfo().getToIdentity().getServer(), Constants.EPHEMERAL_IDENTITY_SERVER)) {
                    return new ServerQueryChannel[]{
                            new ServerQueryChannel()
                    };
                } else {
                    return new ServerQueryChannel[0];
                }
            default:
                return new ServerQueryChannel[0];
        }
    }

}
