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

import io.olvid.messenger.webrtc.WebrtcCallService;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JsonRelayInnerMessage extends JsonDataChannelInnerMessage {
    public byte[] to;
    public int relayedMessageType;
    public String serializedMessagePayload;

    public JsonRelayInnerMessage() {
    }

    public JsonRelayInnerMessage(byte[] to, int relayedMessageType, String serializedMessagePayload) {
        this.to = to;
        this.relayedMessageType = relayedMessageType;
        this.serializedMessagePayload = serializedMessagePayload;
    }

    public byte[] getTo() {
        return to;
    }

    public void setTo(byte[] to) {
        this.to = to;
    }

    @JsonProperty("mt")
    public void setMessageType(int messageType) {
        this.relayedMessageType = messageType;
    }

    @JsonProperty("mt")
    public int getRelayedMessageType() {
        return relayedMessageType;
    }

    @JsonProperty("smp")
    public String getSerializedMessagePayload() {
        return serializedMessagePayload;
    }

    @JsonProperty("smp")
    public void setSerializedMessagePayload(String serializedMessagePayload) {
        this.serializedMessagePayload = serializedMessagePayload;
    }

    @Override
    public int getMessageType() {
        return WebrtcCallService.RELAY_DATA_MESSAGE_TYPE;
    }
}
