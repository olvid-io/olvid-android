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
    public JsonMessage getJsonMessage() {
        return jsonMessage;
    }

    @JsonProperty("message")
    public void setJsonMessage(JsonMessage jsonMessage) {
        this.jsonMessage = jsonMessage;
    }

    @JsonProperty("rr")
    public JsonReturnReceipt getJsonReturnReceipt() {
        return jsonReturnReceipt;
    }

    @JsonProperty("rr")
    public void setJsonReturnReceipt(JsonReturnReceipt jsonReturnReceipt) {
        this.jsonReturnReceipt = jsonReturnReceipt;
    }

    @JsonProperty("rtc")
    public JsonWebrtcMessage getJsonWebrtcMessage() {
        return jsonWebrtcMessage;
    }

    @JsonProperty("rtc")
    public void setJsonWebrtcMessage(JsonWebrtcMessage jsonWebrtcMessage) {
        this.jsonWebrtcMessage = jsonWebrtcMessage;
    }

    @JsonProperty("settings")
    public JsonSharedSettings getJsonSharedSettings() {
        return jsonSharedSettings;
    }

    @JsonProperty("settings")
    public void setJsonSharedSettings(JsonSharedSettings jsonSharedSettings) {
        this.jsonSharedSettings = jsonSharedSettings;
    }

    @JsonProperty("qss")
    public JsonQuerySharedSettings getJsonQuerySharedSettings() {
        return jsonQuerySharedSettings;
    }

    @JsonProperty("qss")
    public void setJsonQuerySharedSettings(JsonQuerySharedSettings jsonQuerySharedSettings) {
        this.jsonQuerySharedSettings = jsonQuerySharedSettings;
    }

    @JsonProperty("upm")
    public JsonUpdateMessage getJsonUpdateMessage() {
        return jsonUpdateMessage;
    }

    @JsonProperty("upm")
    public void setJsonUpdateMessage(JsonUpdateMessage jsonUpdateMessage) {
        this.jsonUpdateMessage = jsonUpdateMessage;
    }

    @JsonProperty("delm")
    public JsonDeleteMessages getJsonDeleteMessages() {
        return jsonDeleteMessages;
    }

    @JsonProperty("delm")
    public void setJsonDeleteMessages(JsonDeleteMessages jsonDeleteMessages) {
        this.jsonDeleteMessages = jsonDeleteMessages;
    }

    @JsonProperty("deld")
    public JsonDeleteDiscussion getJsonDeleteDiscussion() {
        return jsonDeleteDiscussion;
    }

    @JsonProperty("deld")
    public void setJsonDeleteDiscussion(JsonDeleteDiscussion jsonDeleteDiscussion) {
        this.jsonDeleteDiscussion = jsonDeleteDiscussion;
    }

    @JsonProperty("reacm")
    public JsonReaction getJsonReaction() {
        return jsonReaction;
    }

    @JsonProperty("reacm")
    public void setJsonReaction(JsonReaction jsonReaction) {
        this.jsonReaction = jsonReaction;
    }

    @JsonProperty("scd")
    public JsonScreenCaptureDetection getJsonScreenCaptureDetection() {
        return jsonScreenCaptureDetection;
    }

    @JsonProperty("scd")
    public void setJsonScreenCaptureDetection(JsonScreenCaptureDetection jsonScreenCaptureDetection) {
        this.jsonScreenCaptureDetection = jsonScreenCaptureDetection;
    }

    @JsonProperty("lvo")
    public JsonLimitedVisibilityMessageOpened getJsonLimitedVisibilityMessageOpened() {
        return jsonLimitedVisibilityMessageOpened;
    }

    @JsonProperty("lvo")
    public void setJsonLimitedVisibilityMessageOpened(JsonLimitedVisibilityMessageOpened jsonLimitedVisibilityMessageOpened) {
        this.jsonLimitedVisibilityMessageOpened = jsonLimitedVisibilityMessageOpened;
    }

    @JsonProperty("dr")
    public JsonDiscussionRead getJsonDiscussionRead() {
        return jsonDiscussionRead;
    }

    @JsonProperty("dr")
    public void setJsonDiscussionRead(JsonDiscussionRead jsonDiscussionRead) {
        this.jsonDiscussionRead = jsonDiscussionRead;
    }
}
