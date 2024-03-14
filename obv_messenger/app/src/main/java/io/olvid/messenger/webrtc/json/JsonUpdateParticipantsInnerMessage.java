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

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.olvid.messenger.webrtc.WebrtcCallService;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JsonUpdateParticipantsInnerMessage extends JsonDataChannelInnerMessage {
    public List<JsonContactBytesAndName> callParticipants;

    public JsonUpdateParticipantsInnerMessage() {
    }

    public JsonUpdateParticipantsInnerMessage(@NonNull Collection<WebrtcCallService.CallParticipant> callParticipants) {
        this.callParticipants = new ArrayList<>(callParticipants.size() + 1);
        for (WebrtcCallService.CallParticipant callParticipant : callParticipants) {
            if (callParticipant.peerState == WebrtcCallService.PeerState.CONNECTED ||
                    callParticipant.peerState == WebrtcCallService.PeerState.RECONNECTING) {
                // only add participants that are indeed part of the call
                //noinspection ConstantConditions --> we know callParticipant.contact is non null as this message can only be sent by the caller
                this.callParticipants.add(new JsonContactBytesAndName(callParticipant.bytesContactIdentity, callParticipant.contact.displayName, callParticipant.gatheringPolicy));
            }
        }
    }

    @JsonProperty("cp")
    public List<JsonContactBytesAndName> getCallParticipants() {
        return callParticipants;
    }

    @JsonProperty("cp")
    public void setCallParticipants(List<JsonContactBytesAndName> callParticipants) {
        this.callParticipants = callParticipants;
    }

    @Override
    @JsonIgnore
    public int getMessageType() {
        return WebrtcCallService.UPDATE_PARTICIPANTS_DATA_MESSAGE_TYPE;
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class JsonContactBytesAndName {
        public byte[] bytesContactIdentity;
        public String displayName;
        // th rawGatheringPolicy value is nullable at it was not present in the first call implementation
        public Integer rawGatheringPolicy;

        public JsonContactBytesAndName() {
        }

        @JsonIgnore
        public JsonContactBytesAndName(byte[] bytesContactIdentity, String displayName, @NonNull WebrtcCallService.GatheringPolicy gatheringPolicy) {
            this.bytesContactIdentity = bytesContactIdentity;
            this.displayName = displayName;
            switch (gatheringPolicy) {
                case GATHER_ONCE:
                    this.rawGatheringPolicy = 1;
                    break;
                case GATHER_CONTINUOUSLY:
                    this.rawGatheringPolicy = 2;
                    break;
            }
        }

        @JsonProperty("id")
        public byte[] getBytesContactIdentity() {
            return bytesContactIdentity;
        }

        @JsonProperty("id")
        public void setBytesContactIdentity(byte[] bytesContactIdentity) {
            this.bytesContactIdentity = bytesContactIdentity;
        }

        @JsonProperty("name")
        public String getDisplayName() {
            return displayName;
        }

        @JsonProperty("name")
        public void setDisplayName(String displayName) {
            this.displayName = displayName;
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
    }
}
