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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.olvid.messenger.webrtc.WebrtcCallService;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class JsonMutedInnerMessage extends JsonDataChannelInnerMessage {
    public boolean muted;

    @SuppressWarnings("unused")
    public JsonMutedInnerMessage() {
    }

    public JsonMutedInnerMessage(boolean muted) {
        this.muted = muted;
    }

    @JsonProperty("muted")
    public boolean isMuted() {
        return muted;
    }

    @JsonProperty("muted")
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    @Override
    @JsonIgnore
    public int getMessageType() {
        return WebrtcCallService.MUTED_DATA_MESSAGE_TYPE;
    }
}
