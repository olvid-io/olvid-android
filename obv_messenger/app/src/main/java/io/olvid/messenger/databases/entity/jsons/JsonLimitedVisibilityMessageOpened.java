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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

import io.olvid.messenger.databases.entity.Discussion;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonLimitedVisibilityMessageOpened {
    public JsonMessageReference messageReference;
    public byte[] groupUid;
    public byte[] groupOwner;
    public byte[] groupV2Identifier;
    public JsonOneToOneMessageIdentifier oneToOneIdentifier;

    public static JsonLimitedVisibilityMessageOpened of(Discussion discussion) throws Exception {
        JsonLimitedVisibilityMessageOpened jsonLimitedVisibilityMessageOpened = new JsonLimitedVisibilityMessageOpened();

        switch (discussion.discussionType) {
            case Discussion.TYPE_GROUP:
                jsonLimitedVisibilityMessageOpened.setGroupOwnerAndUid(discussion.bytesDiscussionIdentifier);
                break;
            case Discussion.TYPE_GROUP_V2:
                jsonLimitedVisibilityMessageOpened.groupV2Identifier = discussion.bytesDiscussionIdentifier;
                break;
            case Discussion.TYPE_CONTACT:
            default:
                jsonLimitedVisibilityMessageOpened.oneToOneIdentifier = new JsonOneToOneMessageIdentifier(discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier);
                break;
        }
        return jsonLimitedVisibilityMessageOpened;
    }

    @JsonProperty("m")
    public JsonMessageReference getMessageReference() {
        return messageReference;
    }

    @JsonProperty("m")
    public void setMessageReference(JsonMessageReference messageReference) {
        this.messageReference = messageReference;
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
}
