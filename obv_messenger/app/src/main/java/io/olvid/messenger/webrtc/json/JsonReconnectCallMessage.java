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

package io.olvid.messenger.webrtc.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.olvid.messenger.webrtc.WebrtcCallService;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JsonReconnectCallMessage extends JsonWebrtcProtocolMessage {
    public String sessionDescriptionType;
    public byte[] gzippedSessionDescription;
    public int reconnectCounter;
    public int peerReconnectCounterToOverride; // when sending a restart OFFER, this is the counter for the latest ANSWER received

    @SuppressWarnings("unused")
    public JsonReconnectCallMessage() {
    }

    public JsonReconnectCallMessage(String sessionDescriptionType, byte[] gzippedSessionDescription, int reconnectCounter, int peerReconnectCounterToOverride) {
        this.sessionDescriptionType = sessionDescriptionType;
        this.gzippedSessionDescription = gzippedSessionDescription;
        this.reconnectCounter = reconnectCounter;
        this.peerReconnectCounterToOverride = peerReconnectCounterToOverride;
    }

    @JsonProperty("sdt")
    public String getSessionDescriptionType() {
        return sessionDescriptionType;
    }

    @JsonProperty("sdt")
    public void setSessionDescriptionType(String sessionDescriptionType) {
        this.sessionDescriptionType = sessionDescriptionType;
    }

    @JsonProperty("sd")
    public byte[] getGzippedSessionDescription() {
        return gzippedSessionDescription;
    }

    @JsonProperty("sd")
    public void setGzippedSessionDescription(byte[] gzippedSessionDescription) {
        this.gzippedSessionDescription = gzippedSessionDescription;
    }

    @JsonProperty("rc")
    public int getReconnectCounter() {
        return reconnectCounter;
    }

    @JsonProperty("rc")
    public void setReconnectCounter(int reconnectCounter) {
        this.reconnectCounter = reconnectCounter;
    }

    @JsonProperty("prco")
    public int getPeerReconnectCounterToOverride() {
        return peerReconnectCounterToOverride;
    }

    @JsonProperty("prco")
    public void setPeerReconnectCounterToOverride(int peerReconnectCounterToOverride) {
        this.peerReconnectCounterToOverride = peerReconnectCounterToOverride;
    }

    @Override
    @JsonIgnore
    public int getMessageType() {
        return WebrtcCallService.RECONNECT_CALL_MESSAGE_TYPE;
    }
}
