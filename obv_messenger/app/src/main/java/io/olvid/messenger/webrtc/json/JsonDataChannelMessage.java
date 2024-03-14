/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.webrtc.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class JsonDataChannelMessage {
    public int messageType;
    public String serializedMessage;

    public JsonDataChannelMessage() {
    }

    @JsonProperty("t")
    public int getMessageType() {
        return messageType;
    }

    @JsonProperty("t")
    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    @JsonProperty("m")
    public String getSerializedMessage() {
        return serializedMessage;
    }

    @JsonProperty("m")
    public void setSerializedMessage(String serializedMessage) {
        this.serializedMessage = serializedMessage;
    }
}
