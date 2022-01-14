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


import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelMessageToSend;
import io.olvid.engine.datatypes.containers.MessageToSend;
import io.olvid.engine.datatypes.containers.MessageType;
import io.olvid.engine.datatypes.containers.NetworkReceivedMessage;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.metamanager.EncryptionForIdentityDelegate;

public class AsymmetricChannel extends NetworkChannel {
    private final UID toDeviceUid;
    private final Identity toIdentity;
    private final EncryptionForIdentityDelegate encryptionForIdentityDelegate;

    public AsymmetricChannel(UID toDeviceUid, Identity toIdentity, EncryptionForIdentityDelegate encryptionForIdentityDelegate) {
        this.toDeviceUid = toDeviceUid;
        this.toIdentity = toIdentity;
        this.encryptionForIdentityDelegate = encryptionForIdentityDelegate;
    }


    @Override
    public MessageToSend.Header wrapMessageKey(AuthEncKey messageKey, PRNGService prng, boolean partOfFullRatchetProtocol) {
        if (encryptionForIdentityDelegate == null) {
            return null;
        }
        EncryptedBytes wrappedKey = encryptionForIdentityDelegate.wrap(messageKey, toIdentity, prng);
        return new MessageToSend.Header(toDeviceUid, toIdentity, wrappedKey);
    }


    public static AuthEncKeyAndChannelInfo unwrapMessageKey(ChannelManagerSession channelManagerSession, NetworkReceivedMessage.Header header) throws SQLException {
        if (channelManagerSession.encryptionForIdentityDelegate == null) {
            return null;
        }
        AuthEncKey messageKey = channelManagerSession.encryptionForIdentityDelegate.unwrap(channelManagerSession.session, header.getWrappedKey(), header.getOwnedIdentity());
        return new AuthEncKeyAndChannelInfo(messageKey, ReceptionChannelInfo.createAsymmetricChannelInfo());
    }



    public static AsymmetricChannel[] acceptableChannelsForPosting(ChannelMessageToSend message, EncryptionForIdentityDelegate encryptionForIdentityDelegate) {
        if (message.getMessageType() != MessageType.PROTOCOL_MESSAGE_TYPE) {
            // Only protocol messages may be sent through ASYMMETRIC_CHANNEL_TYPE
            return new AsymmetricChannel[0];
        }
        switch (message.getSendChannelInfo().getChannelType()) {
            case SendChannelInfo.ASYMMETRIC_CHANNEL_TYPE:
                UID[] remoteDeviceUids = message.getSendChannelInfo().getRemoteDeviceUids();
                List<AsymmetricChannel> channelList = new ArrayList<>();
                for (UID deviceUid: remoteDeviceUids) {
                    channelList.add(new AsymmetricChannel(deviceUid, message.getSendChannelInfo().getToIdentity(), encryptionForIdentityDelegate));
                }
                return channelList.toArray(new AsymmetricChannel[0]);

            case SendChannelInfo.ASYMMETRIC_BROADCAST_CHANNEL_TYPE:
                return new AsymmetricChannel[]{
                        new AsymmetricChannel(Constants.BROADCAST_UID, message.getSendChannelInfo().getToIdentity(), encryptionForIdentityDelegate)
                };
            default:
                return new AsymmetricChannel[0];
        }
    }

}
