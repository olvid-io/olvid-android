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

package io.olvid.messenger.history_transfer.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.olvid.engine.engine.types.ObvBytesKey

@JsonIgnoreProperties(ignoreUnknown = true)
class JsonZipExport {
    var bytesOwnedIdentity: ByteArray? = null

    @JsonSerialize(keyUsing = ObvBytesKey.KeySerializer::class)
    @JsonDeserialize(keyUsing = ObvBytesKey.KeyDeserializer::class)
    var sha256s: Map<ObvBytesKey, Long>? = null
    var discussions: MutableList<JsonZipDiscussion>? = null

    var messages: MutableList<JsonZipMessages>? = null


    companion object {
        const val DISCUSSION_AND_MESSAGES_JSON_FILE_NAME = "discussions_and_messages.json"
        const val ATTACHMENTS_DIRECTORY_NAME = "files/"
    }
}

