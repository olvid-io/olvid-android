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
package io.olvid.messenger.webrtc.json

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.olvid.messenger.webrtc.WebrtcCallService

@JsonIgnoreProperties(ignoreUnknown = true)
class JsonScreenSharingInnerMessage : JsonDataChannelInnerMessage {
    @JsonProperty("screenSharing")
    var isScreenSharing = false

    @Suppress("unused")
    constructor()
    constructor(screenSharing: Boolean) {
        isScreenSharing = screenSharing
    }

    @JsonIgnore
    override fun getMessageType(): Int {
        return WebrtcCallService.SCREEN_SHARING_DATA_MESSAGE_TYPE
    }
}
