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

import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonPayload {
    JsonMessage jsonMessage;
    JsonReturnReceipt jsonReturnReceipt;
    JsonWebrtcMessage jsonWebrtcMessage;
    JsonSharedSettings jsonSharedSettings;
    JsonQuerySharedSettings jsonQuerySharedSettings;
    JsonUpdateMessage jsonUpdateMessage;
    JsonDeleteMessages jsonDeleteMessages;
    JsonDeleteDiscussion jsonDeleteDiscussion;
    JsonReaction jsonReaction;
    JsonPollVote jsonPollVote;
    JsonScreenCaptureDetection jsonScreenCaptureDetection;
    JsonLimitedVisibilityMessageOpened jsonLimitedVisibilityMessageOpened;
    JsonDiscussionRead jsonDiscussionRead;

    public JsonPayload(JsonMessage jsonMessage, JsonReturnReceipt jsonReturnReceipt) {
        this.jsonMessage = jsonMessage;
        this.jsonReturnReceipt = jsonReturnReceipt;
    }

    public JsonPayload() {
    }

    @JsonProperty("message")
    @Nullable
    public JsonMessage getJsonMessage() {
        return jsonMessage;
    }

    @JsonProperty("message")
    public void setJsonMessage(JsonMessage jsonMessage) {
        this.jsonMessage = jsonMessage;
    }

    @JsonProperty("rr")
    @Nullable
    public JsonReturnReceipt getJsonReturnReceipt() {
        return jsonReturnReceipt;
    }

    @JsonProperty("rr")
    public void setJsonReturnReceipt(JsonReturnReceipt jsonReturnReceipt) {
        this.jsonReturnReceipt = jsonReturnReceipt;
    }

    @JsonProperty("rtc")
    @Nullable
    public JsonWebrtcMessage getJsonWebrtcMessage() {
        return jsonWebrtcMessage;
    }

    @JsonProperty("rtc")
    public void setJsonWebrtcMessage(JsonWebrtcMessage jsonWebrtcMessage) {
        this.jsonWebrtcMessage = jsonWebrtcMessage;
    }

    @JsonProperty("settings")
    @Nullable
    public JsonSharedSettings getJsonSharedSettings() {
        return jsonSharedSettings;
    }

    @JsonProperty("settings")
    public void setJsonSharedSettings(JsonSharedSettings jsonSharedSettings) {
        this.jsonSharedSettings = jsonSharedSettings;
    }

    @JsonProperty("qss")
    @Nullable
    public JsonQuerySharedSettings getJsonQuerySharedSettings() {
        return jsonQuerySharedSettings;
    }

    @JsonProperty("qss")
    public void setJsonQuerySharedSettings(JsonQuerySharedSettings jsonQuerySharedSettings) {
        this.jsonQuerySharedSettings = jsonQuerySharedSettings;
    }

    @JsonProperty("upm")
    @Nullable
    public JsonUpdateMessage getJsonUpdateMessage() {
        return jsonUpdateMessage;
    }

    @JsonProperty("upm")
    public void setJsonUpdateMessage(JsonUpdateMessage jsonUpdateMessage) {
        this.jsonUpdateMessage = jsonUpdateMessage;
    }

    @JsonProperty("delm")
    @Nullable
    public JsonDeleteMessages getJsonDeleteMessages() {
        return jsonDeleteMessages;
    }

    @JsonProperty("delm")
    public void setJsonDeleteMessages(JsonDeleteMessages jsonDeleteMessages) {
        this.jsonDeleteMessages = jsonDeleteMessages;
    }

    @JsonProperty("deld")
    @Nullable
    public JsonDeleteDiscussion getJsonDeleteDiscussion() {
        return jsonDeleteDiscussion;
    }

    @JsonProperty("deld")
    public void setJsonDeleteDiscussion(JsonDeleteDiscussion jsonDeleteDiscussion) {
        this.jsonDeleteDiscussion = jsonDeleteDiscussion;
    }

    @JsonProperty("reacm")
    @Nullable
    public JsonReaction getJsonReaction() {
        return jsonReaction;
    }

    @JsonProperty("reacm")
    public void setJsonReaction(JsonReaction jsonReaction) {
        this.jsonReaction = jsonReaction;
    }

    @JsonProperty("pvm")
    @Nullable
    public JsonPollVote getJsonPollVote() {
        return jsonPollVote;
    }

    @JsonProperty("pvm")
    public void setJsonPollVote(JsonPollVote JsonPollVote) {
        this.jsonPollVote = JsonPollVote;
    }

    @JsonProperty("scd")
    @Nullable
    public JsonScreenCaptureDetection getJsonScreenCaptureDetection() {
        return jsonScreenCaptureDetection;
    }

    @JsonProperty("scd")
    public void setJsonScreenCaptureDetection(JsonScreenCaptureDetection jsonScreenCaptureDetection) {
        this.jsonScreenCaptureDetection = jsonScreenCaptureDetection;
    }

    @JsonProperty("lvo")
    @Nullable
    public JsonLimitedVisibilityMessageOpened getJsonLimitedVisibilityMessageOpened() {
        return jsonLimitedVisibilityMessageOpened;
    }

    @JsonProperty("lvo")
    public void setJsonLimitedVisibilityMessageOpened(JsonLimitedVisibilityMessageOpened jsonLimitedVisibilityMessageOpened) {
        this.jsonLimitedVisibilityMessageOpened = jsonLimitedVisibilityMessageOpened;
    }

    @JsonProperty("dr")
    @Nullable
    public JsonDiscussionRead getJsonDiscussionRead() {
        return jsonDiscussionRead;
    }

    @JsonProperty("dr")
    public void setJsonDiscussionRead(JsonDiscussionRead jsonDiscussionRead) {
        this.jsonDiscussionRead = jsonDiscussionRead;
    }
}
