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


import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.AttachmentKeyAndMetadata;
import io.olvid.engine.datatypes.containers.MessageType;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class ChannelReceivedApplicationMessage {
    private final ChannelReceivedMessage message;
    private final AttachmentKeyAndMetadata[] attachmentsKeyAndMetadata;
    private final byte[] messagePayload;

    private ChannelReceivedApplicationMessage(ChannelReceivedMessage message, AttachmentKeyAndMetadata[] attachmentsKeyAndMetadata, byte[] messagePayload) {
        this.message = message;
        this.attachmentsKeyAndMetadata = attachmentsKeyAndMetadata;
        this.messagePayload = messagePayload;
    }

    public static ChannelReceivedApplicationMessage of(ChannelReceivedMessage channelReceivedMessage) {
        if (channelReceivedMessage.getMessageType() != MessageType.APPLICATION_MESSAGE_TYPE) {
            return null;
        }
        ReceptionChannelInfo channelInfo = channelReceivedMessage.getReceptionChannelInfo();
        if (channelInfo.getChannelType() != ReceptionChannelInfo.OBLIVIOUS_CHANNEL_TYPE) {
            return null;
        }

        try {
            Encoded[] listOfEncoded = channelReceivedMessage.getEncodedElements().decodeList();
            AttachmentKeyAndMetadata[] attachmentsKeyAndMetadata = new AttachmentKeyAndMetadata[listOfEncoded.length-1];

            for (int i=0; i<listOfEncoded.length-1; i++) {
                Encoded[] encodedParts = listOfEncoded[i].decodeList();
                if (encodedParts.length != 2) {
                    throw new DecodingException();
                }
                attachmentsKeyAndMetadata[i] = new AttachmentKeyAndMetadata(
                        (AuthEncKey) encodedParts[0].decodeSymmetricKey(),
                        encodedParts[1].decodeBytes()
                );
            }

            byte[] messagePayload = listOfEncoded[listOfEncoded.length-1].decodeBytes();

            return new ChannelReceivedApplicationMessage(channelReceivedMessage, attachmentsKeyAndMetadata, messagePayload);
        } catch (DecodingException | ClassCastException e) {
            return null;
        }
    }


    public ChannelReceivedMessage getMessage() {
        return message;
    }

    public AttachmentKeyAndMetadata[] getAttachmentsKeyAndMetadata() {
        return attachmentsKeyAndMetadata;
    }

    public Identity getOwnedIdentity() {
        return message.getOwnedIdentity();
    }

    public UID getMessageUid(){
        return message.getMessageUid();
    }

    public byte[] getMessagePayload() {
        return messagePayload;
    }
}
