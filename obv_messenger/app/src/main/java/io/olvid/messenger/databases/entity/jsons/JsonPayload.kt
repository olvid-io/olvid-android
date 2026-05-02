/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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
package io.olvid.messenger.databases.entity.jsons

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class JsonPayload {
    @JsonProperty("message")
    var jsonMessage: JsonMessage? = null

    @JsonProperty("rr")
    var jsonReturnReceipt: JsonReturnReceipt? = null

    @JsonProperty("rtc")
    var jsonWebrtcCallMessage: JsonWebrtcCallMessage? = null

    @JsonProperty("ht")
    var jsonWebrtcHistoryTransferMessage: JsonWebrtcHistoryTransferMessage? = null

    @JsonProperty("htc")
    var jsonWebrtcHistoryTransferControl: JsonWebrtcHistoryTransferControl? = null

    @JsonProperty("settings")
    var jsonSharedSettings: JsonSharedSettings? = null

    @JsonProperty("qss")
    var jsonQuerySharedSettings: JsonQuerySharedSettings? = null

    @JsonProperty("upm")
    var jsonUpdateMessage: JsonUpdateMessage? = null

    @JsonProperty("delm")
    var jsonDeleteMessages: JsonDeleteMessages? = null

    @JsonProperty("deld")
    var jsonDeleteDiscussion: JsonDeleteDiscussion? = null

    @JsonProperty("reacm")
    var jsonReaction: JsonReaction? = null

    @JsonProperty("pvm")
    var jsonPollVote: JsonPollVote? = null

    @JsonProperty("scd")
    var jsonScreenCaptureDetection: JsonScreenCaptureDetection? = null

    @JsonProperty("lvo")
    var jsonLimitedVisibilityMessageOpened: JsonLimitedVisibilityMessageOpened? = null

    @JsonProperty("dr")
    var jsonDiscussionRead: JsonDiscussionRead? = null

    constructor(jsonMessage: JsonMessage?, jsonReturnReceipt: JsonReturnReceipt?) {
        this.jsonMessage = jsonMessage
        this.jsonReturnReceipt = jsonReturnReceipt
    }

    constructor()
}
