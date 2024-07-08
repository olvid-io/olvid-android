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
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelDialogResponseMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelProtocolMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelServerResponseMessageToSend;
import io.olvid.engine.datatypes.containers.MessageType;
import io.olvid.engine.datatypes.containers.ProtocolReceivedDialogResponse;
import io.olvid.engine.datatypes.containers.ProtocolReceivedMessage;
import io.olvid.engine.datatypes.containers.ProtocolReceivedServerResponse;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;

public class LocalChannel extends Channel {
    private final Identity toIdentity;

    private LocalChannel(Identity toIdentity) {
        this.toIdentity = toIdentity;
    }

    private void doPost(ChannelManagerSession channelManagerSession, ChannelMessageToSend message, PRNGService prng) throws Exception {
        switch (message.getMessageType()) {
            case MessageType.PROTOCOL_MESSAGE_TYPE:
                ChannelProtocolMessageToSend protocolMessageToSend = (ChannelProtocolMessageToSend) message;
                UID messageUid = new UID(prng);
                ProtocolReceivedMessage receivedMessage = new ProtocolReceivedMessage(messageUid, toIdentity, protocolMessageToSend.getEncodedElements(), ReceptionChannelInfo.createLocalChannelInfo(), System.currentTimeMillis());
                channelManagerSession.protocolDelegate.process(channelManagerSession.session, receivedMessage);
                break;
            case MessageType.DIALOG_RESPONSE_MESSAGE_TYPE:
                ChannelDialogResponseMessageToSend dialogMessageToSend = (ChannelDialogResponseMessageToSend) message;
                ProtocolReceivedDialogResponse protocolReceivedDialogResponse = new ProtocolReceivedDialogResponse(dialogMessageToSend.getUuid(), dialogMessageToSend.getEncodedUserDialogResponse(), toIdentity, dialogMessageToSend.getEncodedElements(), ReceptionChannelInfo.createLocalChannelInfo());
                channelManagerSession.protocolDelegate.process(channelManagerSession.session, protocolReceivedDialogResponse);
                break;
            case MessageType.SERVER_RESPONSE_TYPE:
                ChannelServerResponseMessageToSend serverResponseMessageToSend = (ChannelServerResponseMessageToSend) message;
                ProtocolReceivedServerResponse protocolReceivedServerResponse = new ProtocolReceivedServerResponse(serverResponseMessageToSend.getEncodedServerResponse(), toIdentity, serverResponseMessageToSend.getEncodedElements(), ReceptionChannelInfo.createLocalChannelInfo());
                channelManagerSession.protocolDelegate.process(channelManagerSession.session, protocolReceivedServerResponse);
                break;
            default:
                Logger.i("Trying to post a message of type " + message.getMessageType() + " on a LocalChannel.");
        }
    }


    public static UID post(ChannelManagerSession channelManagerSession, ChannelMessageToSend message, PRNGService prng) throws Exception {
        LocalChannel[] localChannels = LocalChannel.acceptableChannelsForPosting(channelManagerSession, message);
        if (localChannels.length == 0) {
            Logger.i("No acceptable channels were found for posting");
            throw new NoAcceptableChannelException();
        }
        for (LocalChannel localChannel: localChannels) {
            localChannel.doPost(channelManagerSession, message, prng);
        }
        return null;
    }


    private static LocalChannel[] acceptableChannelsForPosting(ChannelManagerSession channelManagerSession, ChannelMessageToSend message) throws SQLException {
        //noinspection SwitchStatementWithTooFewBranches
        switch (message.getSendChannelInfo().getChannelType()) {
            case SendChannelInfo.LOCAL_TYPE:
                // Check that the toIdentity is an OwnedIdentity
                if (channelManagerSession.identityDelegate.isOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getToIdentity())
                        || Objects.equals(message.getSendChannelInfo().getToIdentity().getServer(), Constants.EPHEMERAL_IDENTITY_SERVER)) {
                    return new LocalChannel[]{
                            new LocalChannel(message.getSendChannelInfo().getToIdentity())
                    };
                } else {
                    return new LocalChannel[0];
                }
            default:
                return new LocalChannel[0];
        }
    }
}
