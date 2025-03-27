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

package io.olvid.engine.datatypes.containers;


import io.olvid.engine.encoder.Encoded;

public class ChannelProtocolMessageToSend implements ChannelMessageToSend {
    private final SendChannelInfo sendChannelInfo;
    private final Encoded encodedElements;
    private final boolean partOfFullRatchetProtocolOfTheSendSeed;
    private final boolean hasUserContent;

    public ChannelProtocolMessageToSend(SendChannelInfo sendChannelInfo, Encoded encodedElements, boolean partOfFullRatchetProtocolOfTheSendSeed, boolean hasUserContent) {
        this.sendChannelInfo = sendChannelInfo;
        this.encodedElements = encodedElements;
        this.partOfFullRatchetProtocolOfTheSendSeed = partOfFullRatchetProtocolOfTheSendSeed;
        this.hasUserContent = hasUserContent;
    }

    @Override
    public int getMessageType() {
        return MessageType.PROTOCOL_MESSAGE_TYPE;
    }

    @Override
    public SendChannelInfo getSendChannelInfo() {
        return sendChannelInfo;
    }

    public Encoded getEncodedElements() {
        return encodedElements;
    }

    public boolean isPartOfFullRatchetProtocolOfTheSendSeed() {
        return partOfFullRatchetProtocolOfTheSendSeed;
    }

    public boolean hasUserContent() {
        return hasUserContent;
    }
}
