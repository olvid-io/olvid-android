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

import java.util.Arrays;
import java.util.List;

import io.olvid.messenger.webrtc.WebrtcCallService;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JsonStartCallMessage extends JsonWebrtcProtocolMessage {
    public String sessionDescriptionType;
    public byte[] gzippedSessionDescription;
    public String turnUserName;
    public String turnPassword;
    public List<String> turnServers;
    public int participantCount;
    public byte[] bytesGroupOwner;
    public byte[] groupId;
    public byte[] groupV2Identifier;
    public Integer rawGatheringPolicy;


    @SuppressWarnings("unused")
    public JsonStartCallMessage() {
    }

    @JsonIgnore
    public JsonStartCallMessage(String sessionDescriptionType, byte[] gzippedSessionDescription, String turnUserName, String turnPassword, List<String> turnServers, int participantCount, byte[] bytesGroupOwnerAndUid, boolean isGroupV2, @NonNull WebrtcCallService.GatheringPolicy gatheringPolicy) {
        this.sessionDescriptionType = sessionDescriptionType;
        this.gzippedSessionDescription = gzippedSessionDescription;
        this.turnUserName = turnUserName;
        this.turnPassword = turnPassword;
        this.turnServers = turnServers;
        this.participantCount = participantCount;
        this.setBytesGroupOwnerAndUid(bytesGroupOwnerAndUid, isGroupV2);
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

    @JsonProperty("tu")
    public String getTurnUserName() {
        return turnUserName;
    }

    @JsonProperty("tu")
    public void setTurnUserName(String turnUserName) {
        this.turnUserName = turnUserName;
    }

    @JsonProperty("tp")
    public String getTurnPassword() {
        return turnPassword;
    }

    @JsonProperty("tp")
    public void setTurnPassword(String turnPassword) {
        this.turnPassword = turnPassword;
    }

    @JsonProperty("ts")
    public List<String> getTurnServers() {
        return turnServers;
    }

    @JsonProperty("ts")
    public void setTurnServers(List<String> turnServers) {
        this.turnServers = turnServers;
    }

    @JsonProperty("c")
    public int getParticipantCount() {
        return participantCount;
    }

    @JsonProperty("c")
    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }

    @JsonProperty("go")
    public byte[] getBytesGroupOwner() {
        return bytesGroupOwner;
    }

    @JsonProperty("go")
    public void setBytesGroupOwner(byte[] bytesGroupOwner) {
        this.bytesGroupOwner = bytesGroupOwner;
    }

    @JsonProperty("gi")
    public byte[] getGroupId() {
        return groupId;
    }

    @JsonProperty("gi")
    public void setGroupId(byte[] groupId) {
        this.groupId = groupId;
    }

    @JsonProperty("gid2")
    public byte[] getGroupV2Identifier() {
        return groupV2Identifier;
    }

    @JsonProperty("gid2")
    public void setGroupV2Identifier(byte[] groupV2Identifier) {
        this.groupV2Identifier = groupV2Identifier;
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
    public byte[] getBytesGroupOwnerAndUid() {
        if (this.groupV2Identifier != null) {
            return groupV2Identifier;
        } else if (this.bytesGroupOwner == null || this.groupId == null) {
            return null;
        }
        byte[] bytesGroupOwnerAndUid = new byte[bytesGroupOwner.length + groupId.length];
        System.arraycopy(this.bytesGroupOwner, 0, bytesGroupOwnerAndUid, 0, this.bytesGroupOwner.length);
        System.arraycopy(this.groupId, 0, bytesGroupOwnerAndUid, this.bytesGroupOwner.length, this.groupId.length);
        return bytesGroupOwnerAndUid;
    }

    @JsonIgnore
    public void setBytesGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid, boolean isGroupV2) {
        if (isGroupV2) {
            this.groupV2Identifier = bytesGroupOwnerAndUid;
            this.bytesGroupOwner = null;
            this.groupId = null;
        } else if (bytesGroupOwnerAndUid == null || bytesGroupOwnerAndUid.length < 32) {
            this.groupV2Identifier = null;
            this.bytesGroupOwner = null;
            this.groupId = null;
        } else {
            this.groupV2Identifier = null;
            this.bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
            this.groupId = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
        }
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
        return WebrtcCallService.START_CALL_MESSAGE_TYPE;
    }
}
