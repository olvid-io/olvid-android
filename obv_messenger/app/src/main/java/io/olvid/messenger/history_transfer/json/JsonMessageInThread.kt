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
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonLocation
import io.olvid.messenger.databases.entity.jsons.JsonMessageReference
import io.olvid.messenger.databases.entity.jsons.JsonPoll
import io.olvid.messenger.databases.entity.jsons.JsonUserMention


@JsonIgnoreProperties(ignoreUnknown = true)
class JsonMessageInThread {
    var sequenceNumber: Long? = null
    var uidFromServer: ByteArray? = null // for received messages, this is the messageUidFromServer. For other messages, this is null
    var timestamp: Long? = null
    var status: Int? = null // null for inbound messages
    var body: String? = null
    var reply: JsonMessageReference? = null
    var expiration: Long? = null // this is an existence expiration, messages with read once or limited visibility are never transferred
    var mentions: List<JsonUserMention>? = null
    var forwarded: Boolean? = null
    var edited: Boolean? = null // never mark as edited unseen
    var bookmarked: Boolean? = null
    var location: JsonLocation? = null
    var poll: JsonPoll? = null
    var reactions: List<JsonReactionToMessage>? = null
    var pollVotes: List<JsonPollVoteForMessage>? = null

    var attachments: List<JsonAttachment>? = null



    companion object {
        const val STATUS_SENT_FROM_ANOTHER_DEVICE = 1
        const val STATUS_SENT = 2
        const val STATUS_DELIVERED = 3
        const val STATUS_DELIVERED_AND_READ = 4
        const val STATUS_UNDELIVERED = 5
        const val STATUS_DELIVERED_ALL = 6
        const val STATUS_DELIVERED_ALL_READ_ONE = 7
        const val STATUS_DELIVERED_ALL_READ_ALL = 8

        // for outbound messages, this gives the status to serialize
        @JsonIgnore
        fun jsonMessageStatusFromOutboundMessageStatus(outboundMessageStatus: Int) : Int? {
            return when(outboundMessageStatus) {
                Message.STATUS_SENT -> STATUS_SENT
                Message.STATUS_DELIVERED -> STATUS_DELIVERED
                Message.STATUS_DELIVERED_AND_READ -> STATUS_DELIVERED_AND_READ
                Message.STATUS_UNDELIVERED -> STATUS_UNDELIVERED
                Message.STATUS_DELIVERED_ALL -> STATUS_DELIVERED_ALL
                Message.STATUS_DELIVERED_ALL_READ_ONE -> STATUS_DELIVERED_ALL_READ_ONE
                Message.STATUS_DELIVERED_ALL_READ_ALL -> STATUS_DELIVERED_ALL_READ_ALL
                // "else" includes Message.STATUS_UNPROCESSED, Message.STATUS_PROCESSING, Message.STATUS_SENT_FROM_ANOTHER_DEVICE (and inbound message statuses)
                else -> STATUS_SENT_FROM_ANOTHER_DEVICE
            }
        }

        // for any received JsonMessageInThread. this is the status to give to the new Message (inbound or outbound)
        @JsonIgnore
        fun messageStatusFromJsonMessageStatus(jsonMessageStatus: Int?, messageType: Int) : Int {
            return if (jsonMessageStatus == null || messageType == Message.TYPE_INBOUND_MESSAGE) {
                Message.STATUS_READ // by default, mark inbound messages as read
            } else {
                when (jsonMessageStatus) {
                    STATUS_SENT_FROM_ANOTHER_DEVICE -> Message.STATUS_SENT_FROM_ANOTHER_DEVICE
                    STATUS_SENT -> Message.STATUS_SENT
                    STATUS_DELIVERED -> Message.STATUS_DELIVERED
                    STATUS_DELIVERED_AND_READ -> Message.STATUS_DELIVERED_AND_READ
                    STATUS_UNDELIVERED -> Message.STATUS_UNDELIVERED
                    STATUS_DELIVERED_ALL -> Message.STATUS_DELIVERED_ALL
                    STATUS_DELIVERED_ALL_READ_ONE -> Message.STATUS_DELIVERED_ALL_READ_ONE
                    STATUS_DELIVERED_ALL_READ_ALL -> Message.STATUS_DELIVERED_ALL_READ_ALL
                    else -> Message.STATUS_SENT_FROM_ANOTHER_DEVICE // for unknown statuses
                }
            }
        }
    }
}