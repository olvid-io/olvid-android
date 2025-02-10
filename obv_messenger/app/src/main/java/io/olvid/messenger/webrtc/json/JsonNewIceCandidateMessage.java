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

import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.olvid.messenger.webrtc.WebrtcCallService;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JsonNewIceCandidateMessage extends JsonWebrtcProtocolMessage {
    public String sdp;
    public int sdpMLineIndex;
    @Nullable
    public String sdpMid;

    @SuppressWarnings("unused")
    public JsonNewIceCandidateMessage() {
    }

    public JsonNewIceCandidateMessage(String sdp, int sdpMLineIndex, @Nullable String sdpMid) {
        this.sdp = sdp;
        this.sdpMLineIndex = sdpMLineIndex;
        this.sdpMid = sdpMid;
    }

    @JsonProperty("sdp")
    public String getSdp() {
        return sdp;
    }

    @JsonProperty("sdp")
    public void setSdp(String sdp) {
        this.sdp = sdp;
    }

    @JsonProperty("li")
    public int getSdpMLineIndex() {
        return sdpMLineIndex;
    }

    @JsonProperty("li")
    public void setSdpMLineIndex(int sdpMLineIndex) {
        this.sdpMLineIndex = sdpMLineIndex;
    }

    @Nullable
    @JsonProperty("id")
    public String getSdpMid() {
        return sdpMid;
    }

    @JsonProperty("id")
    public void setSdpMid(@Nullable String sdpMid) {
        this.sdpMid = sdpMid;
    }

    @Override
    @JsonIgnore
    public int getMessageType() {
        return WebrtcCallService.NEW_ICE_CANDIDATE_MESSAGE_TYPE;
    }
}
