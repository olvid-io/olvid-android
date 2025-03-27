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

package io.olvid.messenger.databases.entity.jsons;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonWebrtcMessage {
    UUID callIdentifier;
    Integer messageType;
    String serializedMessagePayload;

    public JsonWebrtcMessage() {
    }

    @JsonProperty("ci")
    public UUID getCallIdentifier() {
        return callIdentifier;
    }

    @JsonProperty("ci")
    public void setCallIdentifier(UUID callIdentifier) {
        this.callIdentifier = callIdentifier;
    }

    @JsonProperty("mt")
    public Integer getMessageType() {
        return messageType;
    }

    @JsonProperty("mt")
    public void setMessageType(Integer messageType) {
        this.messageType = messageType;
    }

    @JsonProperty("smp")
    public String getSerializedMessagePayload() {
        return serializedMessagePayload;
    }

    @JsonProperty("smp")
    public void setSerializedMessagePayload(String serializedMessagePayload) {
        this.serializedMessagePayload = serializedMessagePayload;
    }
}
