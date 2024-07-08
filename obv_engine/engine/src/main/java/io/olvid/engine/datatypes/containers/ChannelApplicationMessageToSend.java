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

package io.olvid.engine.datatypes.containers;


import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;

public class ChannelApplicationMessageToSend implements ChannelMessageToSend {
    private final SendChannelInfo sendChannelInfo;
    private final Attachment[] attachments;
    private final byte[] messagePayload;
    private final byte[] extendedMessagePayload;
    private final boolean hasUserContent;
    private final boolean isVoipMessage;

    public ChannelApplicationMessageToSend(Identity[] toIdentities, Identity fromIdentity, byte[] messagePayload, byte[] extendedMessagePayload, Attachment[] attachments, boolean hasUserContent, boolean isVoipMessage) throws Exception {
        SendChannelInfo[] sendChannelInfos = SendChannelInfo.createAllConfirmedObliviousChannelsOrPreKeysInfoForMultipleIdentities(toIdentities, fromIdentity);
        if (sendChannelInfos.length != 1) {
            Logger.e("Error: trying to create a ChannelApplicationMessageToSend for identities on different servers");
            throw new Exception();
        }
        this.sendChannelInfo = sendChannelInfos[0];
        this.messagePayload = messagePayload;
        this.extendedMessagePayload = extendedMessagePayload;
        this.attachments = attachments;
        this.hasUserContent = hasUserContent;
        this.isVoipMessage = isVoipMessage;
    }


    @Override
    public int getMessageType() {
        return MessageType.APPLICATION_MESSAGE_TYPE;
    }

    @Override
    public SendChannelInfo getSendChannelInfo() {
        return sendChannelInfo;
    }

    public byte[] getMessagePayload() {
        return messagePayload;
    }

    public byte[] getExtendedMessagePayload() {
        return extendedMessagePayload;
    }

    public Attachment[] getAttachments() {
        return attachments;
    }

    public boolean hasUserContent() {
        return hasUserContent;
    }

    public boolean isVoipMessage() {
        return isVoipMessage;
    }

    public static class Attachment {
        private final String url;
        private final boolean deleteAfterSend;
        private final long attachmentLength;
        private final byte[] metadata;

        public Attachment(String url, boolean deleteAfterSend, long attachmentLength, byte[] metadata) {
            this.url = url;
            this.deleteAfterSend = deleteAfterSend;
            this.attachmentLength = attachmentLength;
            this.metadata = metadata;
        }

        public String getUrl() {
            return url;
        }

        public boolean isDeleteAfterSend() {
            return deleteAfterSend;
        }

        public long getAttachmentLength() {
            return attachmentLength;
        }

        public byte[] getMetadata() {
            return metadata;
        }
    }
}
