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
class JsonWebrtcHistoryTransferMessage {
    @JsonProperty("id")
    var transferId: String? = null
    @JsonProperty("ice")
    var iceCandidates: List<JsonWebrtcHistoryTransferIceCandidate>? = null
    @JsonProperty("sdp")
    var sdp: JsonWebrtcHistoryTransferSdp? = null

    @JsonIgnoreProperties(ignoreUnknown = true)
    class JsonWebrtcHistoryTransferIceCandidate {
        @JsonProperty("mid")
        var mid: String? = null
        @JsonProperty("mli")
        var sdpMLineIndex: Int? = null
        @JsonProperty("sdp")
        var sdp: String? = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class JsonWebrtcHistoryTransferSdp {
        @JsonProperty("t")
        var type: String? = null // "offer" or "answer"
        @JsonProperty("sdp")
        var sdp: String? = null
    }
}
