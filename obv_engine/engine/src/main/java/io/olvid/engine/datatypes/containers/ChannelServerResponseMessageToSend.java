/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.encoder.Encoded;

public class ChannelServerResponseMessageToSend implements ChannelMessageToSend {

    private final SendChannelInfo sendChannelInfo;
    private final Encoded encodedElements;
    private final Encoded encodedServerResponse;

    public ChannelServerResponseMessageToSend(Identity toIdentity, Encoded encodedServerResponse, Encoded encodedElements) {
        this.sendChannelInfo = SendChannelInfo.createLocalChannelInfo(toIdentity);
        this.encodedElements = encodedElements;
        this.encodedServerResponse = encodedServerResponse;
    }

    @Override
    public int getMessageType() {
        return MessageType.SERVER_RESPONSE_TYPE;
    }

    @Override
    public SendChannelInfo getSendChannelInfo() {
        return sendChannelInfo;
    }

    public Encoded getEncodedElements() {
        return encodedElements;
    }

    public Encoded getEncodedServerResponse() {
        return encodedServerResponse;
    }
}

