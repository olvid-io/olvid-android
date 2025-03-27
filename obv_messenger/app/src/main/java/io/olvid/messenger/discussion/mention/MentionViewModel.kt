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

package io.olvid.messenger.discussion.mention

import android.text.Editable
import android.text.Spanned
import androidx.core.text.getSpans
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.jsons.JsonUserMention
import java.util.regex.Pattern
import kotlin.math.max

class MentionViewModel : ViewModel() {

    companion object {
        val mentionPattern: Pattern =
            Pattern.compile("(^|\\s)@(\\S*)$", Pattern.CASE_INSENSITIVE)
    }

    val mentionsStatus: MutableLiveData<MentionStatus> = MutableLiveData(
        MentionStatus.None
    )
    var mentions: List<JsonUserMention>? = null
        private set
    private var ongoingMention: JsonUserMention? = null

    sealed interface MentionStatus {
        data object None : MentionStatus
        data class End(val mention: JsonUserMention, val contact: Contact) : MentionStatus
        data class Filter(val text: String) : MentionStatus
    }

    fun updateMentions(
        editable: Editable?,
        selectionIndex: Int = -2
    ) { // we use a default selectionIndex of -2 instead of -1, as -1 is the sectionEnd when first entering the discussion
        var correctedSelectionIndex = selectionIndex
        var firstNameCut = false
        mentions = if (editable.isNullOrEmpty()) {
            // avoid re-posting Cancel value everytime
            if (mentionsStatus.value !is MentionStatus.None) {
                mentionsStatus.value = MentionStatus.None
            }
            ongoingMention = null
            null
        } else {
            // iterate in reverse order so that editable.delete always uses the correct offsets
            editable.getSpans<MentionUrlSpan>().asList().asReversed().forEach { mentionUrlSpan ->
                val mention =
                    JsonUserMention(
                        mentionUrlSpan.userIdentifier,
                        editable.getSpanStart(mentionUrlSpan),
                        editable.getSpanEnd(mentionUrlSpan)
                    )
                if (mention.length != mentionUrlSpan.length) {
                    val firstName = AppSingleton.getCurrentIdentityLiveData().value?.takeIf {
                        it.bytesOwnedIdentity.contentEquals(mention.userIdentifier)
                    }?.identityDetails?.let {
                        try {
                            AppSingleton.getJsonObjectMapper()
                                .readValue(it, JsonIdentityDetails::class.java)?.firstName
                        } catch (e: Exception) {
                            null
                        }
                    } ?: AppSingleton.getContactFirstName(mention.userIdentifier)
                    if (firstName != null && mention.length > firstName.length + 1) {
                        // length mismatch, cut mention to firstname
                        editable.removeSpan(mentionUrlSpan)
                        editable.setSpan(
                            mentionUrlSpan.copy(length = 1 + firstName.length),
                            mention.rangeStart,
                            mention.rangeStart + 1 + firstName.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        editable.delete(mention.rangeStart + firstName.length + 1, mention.rangeEnd)
                        firstNameCut = true
                    } else {
                        // length mismatch, delete existing mention entirely or span only for cancelled
                        editable.removeSpan(mentionUrlSpan)
                        if (mention.userIdentifier != null) {
                            editable.delete(mention.rangeStart, mention.rangeEnd)
                            if (mention.rangeStart < correctedSelectionIndex) {
                                correctedSelectionIndex = max(
                                    mention.rangeStart,
                                    correctedSelectionIndex - mention.rangeEnd + mention.rangeStart
                                )
                            }
                        }
                    }
                }
            }

            // update span ranges after text update
            editable.getSpans<MentionUrlSpan>().mapNotNull { mentionUrlSpan ->
                JsonUserMention(
                    mentionUrlSpan.userIdentifier,
                    editable.getSpanStart(mentionUrlSpan),
                    editable.getSpanEnd(mentionUrlSpan)
                ).takeIf { it.length > 1 }
            }.takeIf { it.isEmpty().not() }
        }

        if (selectionIndex == -2 || firstNameCut) {
            return
        }

        // only match up to selectionIndex if selectionIndex >= 0
        val mentionMatcher = mentionPattern.matcher(
            if (correctedSelectionIndex > 0) {
                editable?.subSequence(0, correctedSelectionIndex)
            } else {
                editable?.toString()
            } ?: ""
        )

        var foundOngoing = false
        while (mentionMatcher.find()) {
            var startMention = mentionMatcher.start()
            if (mentionMatcher.group()[0].isWhitespace()) {
                startMention++
            }
            val endMention = mentionMatcher.end()

            // We do not consider a match an ongoing candidate if:
            //  - the @ symbol (startMention) is inside another mention
            //  - except if this mention is cancelled (probably we just backspaced into an unfinished mention)
            if (mentions?.any { it.rangeStart <= startMention && it.rangeEnd >= startMention && it.userIdentifier != null } == true) {
                continue
            }
            foundOngoing = true
            ongoingMention =
                JsonUserMention(
                    null,
                    startMention,
                    endMention
                )
            mentionsStatus.value =
                MentionStatus.Filter(mentionMatcher.group().replaceFirst("@", ""))
            break
        }
        if (!foundOngoing) {
            cancelOngoingMention()
        }
    }

    fun validateMention(contact: Contact) {
        val mention = JsonUserMention(
            contact.bytesContactIdentity,
            ongoingMention?.rangeStart ?: 0,
            ongoingMention?.rangeEnd ?: 0
        )
        mentionsStatus.value = MentionStatus.End(mention, contact)
        ongoingMention = null
    }

    private fun cancelOngoingMention() {
        if (ongoingMention != null || mentionsStatus.value !is MentionStatus.None) {
            mentionsStatus.value = MentionStatus.None
            ongoingMention = null
        }
    }
}