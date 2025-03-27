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

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.olvid.messenger.webrtc.WebrtcCallService;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JsonNewParticipantOfferMessage extends JsonWebrtcProtocolMessage {
    public String sessionDescriptionType;
    public byte[] gzippedSessionDescription;
    public Integer rawGatheringPolicy;


    public JsonNewParticipantOfferMessage() {
    }

    @JsonIgnore
    public JsonNewParticipantOfferMessage(String sessionDescriptionType, byte[] gzippedSessionDescription, @NonNull WebrtcCallService.GatheringPolicy gatheringPolicy) {
        this.sessionDescriptionType = sessionDescriptionType;
        this.gzippedSessionDescription = gzippedSessionDescription;
        switch (gatheringPolicy) {
            case GATHER_ONCE:
                this.rawGatheringPolicy = 1;
                break;
            case GATHER_CONTINUOUSLY:
                this.rawGatheringPolicy = 2;
                break;
        }
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

    @JsonProperty("gp")
    public Integer getRawGatheringPolicy() {
        return rawGatheringPolicy;
    }

    @JsonProperty("gp")
    public void setRawGatheringPolicy(Integer rawGatheringPolicy) {
        this.rawGatheringPolicy = rawGatheringPolicy;
    }

    @JsonIgnore
    @NonNull
    public WebrtcCallService.GatheringPolicy getGatheringPolicy() {
        if (rawGatheringPolicy != null && rawGatheringPolicy == 2) {
            return WebrtcCallService.GatheringPolicy.GATHER_CONTINUOUSLY;
        }
        return WebrtcCallService.GatheringPolicy.GATHER_ONCE;
    }


    @Override
    @JsonIgnore
    public int getMessageType() {
        return WebrtcCallService.NEW_PARTICIPANT_OFFER_MESSAGE_TYPE;
    }
}
