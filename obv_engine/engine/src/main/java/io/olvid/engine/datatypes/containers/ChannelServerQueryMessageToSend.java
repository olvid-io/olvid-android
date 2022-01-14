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

package io.olvid.engine.datatypes.containers;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.encoder.Encoded;


public class ChannelServerQueryMessageToSend implements ChannelMessageToSend {
    private final SendChannelInfo sendChannelInfo;
    private final Encoded encodedElements;

    public ChannelServerQueryMessageToSend(Identity toIdentity, ServerQuery.Type serverQueryType, Encoded encodedElements) {
        this.sendChannelInfo = SendChannelInfo.createServerQueryChannelInfo(toIdentity, serverQueryType);
        this.encodedElements = encodedElements;
    }

    @Override
    public int getMessageType() {
        return MessageType.SERVER_QUERY_TYPE;
    }

    @Override
    public SendChannelInfo getSendChannelInfo() {
        return sendChannelInfo;
    }

    public Encoded getEncodedElements() {
        return encodedElements;
    }
}
