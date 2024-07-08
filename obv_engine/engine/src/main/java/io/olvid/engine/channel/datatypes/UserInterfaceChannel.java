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
import java.util.HashMap;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.NoAcceptableChannelException;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelDialogMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.MessageType;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.notifications.ChannelNotifications;

public class UserInterfaceChannel extends Channel {
    private UserInterfaceChannel() { }

    private void doPost(ChannelManagerSession channelManagerSession, ChannelMessageToSend message, PRNGService prng) throws Exception {
        switch (message.getMessageType()) {
            case MessageType.DIALOG_MESSAGE_TYPE:
                ChannelDialogMessageToSend channelDialogMessageToSend = (ChannelDialogMessageToSend) message;
                HashMap<String, Object> userInfo = new HashMap<>();
                userInfo.put(ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG_SESSION_KEY, channelManagerSession.session);
                userInfo.put(ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG_CHANNEL_DIALOG_MESSAGE_TO_SEND_KEY, channelDialogMessageToSend);
                channelManagerSession.notificationPostingDelegate.postNotification(ChannelNotifications.NOTIFICATION_NEW_UI_DIALOG, userInfo);
                break;
            default:
                Logger.i("Trying to post a message of type " + message.getMessageType() + " on a UserInterfaceChannel.");
        }
    }

    public static UID post(ChannelManagerSession channelManagerSession, ChannelMessageToSend message, PRNGService prng) throws Exception {
        UserInterfaceChannel[] userInterfaceChannels = acceptableChannelsForPosting(channelManagerSession, message);
        if (userInterfaceChannels.length == 0) {
            Logger.i("No acceptable channels were found for posting");
            throw new NoAcceptableChannelException();
        }
        for (UserInterfaceChannel userInterfaceChannel: userInterfaceChannels) {
            userInterfaceChannel.doPost(channelManagerSession, message, prng);
        }
        return null;
    }

    private static UserInterfaceChannel[] acceptableChannelsForPosting(ChannelManagerSession channelManagerSession, ChannelMessageToSend message) throws SQLException {
        //noinspection SwitchStatementWithTooFewBranches
        switch (message.getSendChannelInfo().getChannelType()) {
            case SendChannelInfo.USER_INTERFACE_TYPE:
                // Check that the toIdentity is an OwnedIdentity
                if (channelManagerSession.identityDelegate.isOwnedIdentity(channelManagerSession.session, message.getSendChannelInfo().getToIdentity())
                        || Objects.equals(message.getSendChannelInfo().getToIdentity().getServer(), Constants.EPHEMERAL_IDENTITY_SERVER)) {
                    return new UserInterfaceChannel[]{
                            new UserInterfaceChannel()
                    };
                } else {
                    return new UserInterfaceChannel[0];
                }
            default:
                return new UserInterfaceChannel[0];
        }
    }
}
