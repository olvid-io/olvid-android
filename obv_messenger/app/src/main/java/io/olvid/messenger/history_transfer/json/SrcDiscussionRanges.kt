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

package io.olvid.messenger.history_transfer.json

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.ObvBytesKey
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
class SrcDiscussionRanges {
    var discussion: JsonDiscussionIdentifier? = null
    var title: String? = null

    @JvmField
    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer::class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer::class)
    var rangesByThreadAndSender: Map<ObvBytesKey, JsonRangesByThread>? = null

    @JsonIgnore
    fun getRangesByThreadAndSender(): Map<ObvBytesKey, Map<UUID, List<List<Long>>>>? {
        return rangesByThreadAndSender?.mapValues { (_, value) ->
            value.ranges?.mapKeys { (key, _) -> UUID.fromString(key) } ?: emptyMap()
        }
    }

    @JsonIgnore
    fun setRangesByThreadAndSender(ranges: Map<ObvBytesKey, Map<UUID, List<List<Long>>>>?) {
        rangesByThreadAndSender = ranges?.mapValues { (_, value) ->
            JsonRangesByThread(value.mapKeys { (key, _) -> Logger.getUuidString(key) })
        }
    }
}
