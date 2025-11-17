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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.UUID;

import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonPollVote {

    @JsonProperty("pcuid")
    public UUID pollCandidateUuid;
    @JsonProperty("v")
    public Boolean voted;
    @JsonProperty("ver")
    public Integer version;
    // Message entity
    @JsonProperty("guid")
    public byte[] groupUid;
    @JsonProperty("go")
    public byte[] groupOwner;
    @JsonProperty("gid2")
    public byte[] groupV2Identifier;
    @JsonProperty("o2oi")
    public JsonOneToOneMessageIdentifier oneToOneIdentifier;
    @JsonProperty("ref")
    public JsonMessageReference messageReference;
    @JsonProperty("ost")
    public Long originalServerTimestamp;

    public static JsonPollVote of(Discussion discussion, Message message) throws Exception {
        JsonPollVote jsonPollVote = new JsonPollVote();

        jsonPollVote.messageReference = JsonMessageReference.of(message);
        switch (discussion.discussionType) {
            case Discussion.TYPE_GROUP:
                jsonPollVote.setGroupOwnerAndUid(discussion.bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                jsonPollVote.groupV2Identifier = discussion.bytesDiscussionIdentifier;
                break;
            case Discussion.TYPE_CONTACT:
            default:
                jsonPollVote.oneToOneIdentifier = new JsonOneToOneMessageIdentifier(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
        }
        return jsonPollVote;
    }


    @JsonIgnore
    public void setGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) throws Exception {
        if (bytesGroupOwnerAndUid.length < 32) {
            throw new Exception();
        }
        byte[] bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
        byte[] bytesGroupUid = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
        groupOwner = bytesGroupOwner;
        groupUid = bytesGroupUid;
    }
}
