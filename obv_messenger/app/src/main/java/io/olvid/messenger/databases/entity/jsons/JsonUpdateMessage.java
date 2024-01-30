/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonUpdateMessage {
    String body;
    byte[] groupUid;
    byte[] groupOwner;
    byte[] groupV2Identifier;
    public JsonOneToOneMessageIdentifier oneToOneIdentifier;
    JsonMessageReference messageReference;
    JsonLocation jsonLocation;
    List<JsonUserMention> jsonUserMentions;

    public static JsonUpdateMessage of(Discussion discussion, Message message) throws Exception {
        JsonUpdateMessage jsonUpdateMessage = new JsonUpdateMessage();
        jsonUpdateMessage.jsonUserMentions = message.getMentions();
        jsonUpdateMessage.messageReference = JsonMessageReference.of(message);
        switch (discussion.discussionType) {
            case Discussion.TYPE_GROUP:
                jsonUpdateMessage.setGroupOwnerAndUid(discussion.bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                jsonUpdateMessage.groupV2Identifier = discussion.bytesDiscussionIdentifier;
                break;
            case Discussion.TYPE_CONTACT:
            default:
                jsonUpdateMessage.oneToOneIdentifier = new JsonOneToOneMessageIdentifier(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
        }
        return jsonUpdateMessage;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @JsonProperty("loc")
    public JsonLocation getJsonLocation() {
        return jsonLocation;
    }

    @JsonProperty("loc")
    public void setJsonLocation(JsonLocation jsonLocation) {
        this.jsonLocation = jsonLocation;
    }

    @JsonProperty("um")
    public List<JsonUserMention> getJsonUserMentions() {
        return jsonUserMentions;
    }

    @JsonProperty("um")
    public void setJsonUserMentions(@Nullable List<JsonUserMention> jsonUserMentions) {
        this.jsonUserMentions = jsonUserMentions;
    }

    @JsonProperty("guid")
    public byte[] getGroupUid() {
        return groupUid;
    }

    @JsonProperty("guid")
    public void setGroupUid(byte[] groupUid) {
        this.groupUid = groupUid;
    }

    @JsonProperty("go")
    public byte[] getGroupOwner() {
        return groupOwner;
    }

    @JsonProperty("go")
    public void setGroupOwner(byte[] groupOwner) {
        this.groupOwner = groupOwner;
    }

    @JsonProperty("gid2")
    public byte[] getGroupV2Identifier() {
        return groupV2Identifier;
    }

    @JsonProperty("gid2")
    public void setGroupV2Identifier(byte[] groupV2Identifier) {
        this.groupV2Identifier = groupV2Identifier;
    }

    @JsonProperty("o2oi")
    public JsonOneToOneMessageIdentifier getOneToOneIdentifier() {
        return oneToOneIdentifier;
    }

    @JsonProperty("o2oi")
    public void setOneToOneIdentifier(JsonOneToOneMessageIdentifier oneToOneIdentifier) {
        this.oneToOneIdentifier = oneToOneIdentifier;
    }
    @JsonProperty("ref")
    public JsonMessageReference getMessageReference() {
        return messageReference;
    }

    @JsonProperty("ref")
    public void setMessageReference(JsonMessageReference messageReference) {
        this.messageReference = messageReference;
    }

    @JsonIgnore
    public void setGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) throws Exception {
        if (bytesGroupOwnerAndUid.length < 32) {
            throw new Exception();
        }
        byte[] bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
        byte[] bytesGroupUid = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
        setGroupOwner(bytesGroupOwner);
        setGroupUid(bytesGroupUid);
    }

    @JsonIgnore
    public void sanitizeJsonUserMentions() {
        if (jsonUserMentions != null) {
            ArrayList<JsonUserMention> sanitizedMentions = new ArrayList<>();
            for (JsonUserMention mention : jsonUserMentions) {
                if (mention.getUserIdentifier() != null) {
                    sanitizedMentions.add(mention);
                }
            }
            jsonUserMentions = sanitizedMentions;
        }
    }
}
