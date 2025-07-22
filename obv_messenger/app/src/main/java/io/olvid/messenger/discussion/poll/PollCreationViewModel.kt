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

package io.olvid.messenger.discussion.poll

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.engine.Logger
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.PollVote
import io.olvid.messenger.databases.entity.jsons.JsonPayload
import io.olvid.messenger.databases.entity.jsons.JsonPoll
import io.olvid.messenger.databases.entity.jsons.JsonPollCandidate
import io.olvid.messenger.databases.entity.jsons.JsonPollVote
import io.olvid.messenger.databases.tasks.PostMessageInDiscussionTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.days


data class PollAnswer(val uuid: UUID, val text: String)

class PollCreationViewModel : ViewModel() {

    companion object {
        const val MIN_ANSWERS = 2
        const val MAX_ANSWERS = 12
        val NONE_ANSWER = PollAnswer(UUID(0,0), "None")
    }

    var discussionId = -1L

    var multipleChoice by mutableStateOf(false)

    var question by mutableStateOf("")
        private set

    var answers = mutableStateListOf<PollAnswer>()
        private set

    var quizAnswer by mutableStateOf<UUID?>(null)

    var expirationDateEnabled by mutableStateOf(false)
    var expirationDate by mutableLongStateOf(System.currentTimeMillis() + 1.days.inWholeMilliseconds)
    var showDatePicker by mutableStateOf(false)
        private set
    var showTimePicker by mutableStateOf(false)
        private set

    var duplicateAnswers by mutableStateOf(emptyList<UUID>())
        private set

    var hasNoneAnswer by mutableStateOf(false)
        private set

    init {
        repeat(MIN_ANSWERS) {
            addAnswer()
        }
    }

    fun updateQuestion(newQuestion: String) {
        question = newQuestion
    }

    fun updateAnswerText(answerId: UUID, newText: String) {
        val index = answers.indexOfFirst { it.uuid == answerId }
        if (index != -1) {
            answers[index] = answers[index].copy(text = newText)
        }
        if (newText.isBlank() && answers.size > MIN_ANSWERS) {
            removeAnswer(answerId)
        }
        validateAnswers()
    }

    fun updateMultipleChoice(value: Boolean) {
        multipleChoice = value
    }

    fun updateHasNoneAnswer(value: Boolean) {
        hasNoneAnswer = value
        validateAnswers()
    }

    fun updateQuizAnswer(value: UUID?) {
        quizAnswer = if (value == quizAnswer) {
            UUID(0,0)
        } else {
            updateMultipleChoice(false)
            value
        }
    }

    fun isQuizModeEnabled() = quizAnswer != null

    fun enableQuizMode(enabled: Boolean) {
        quizAnswer = if (enabled) UUID(0,0) else null
    }

    fun toggleDatePicker() {
        showDatePicker = !showDatePicker
        showTimePicker = false
    }

    fun toggleTimePicker() {
        showTimePicker = !showTimePicker
        showDatePicker = false
    }

    fun reorderAnswers(from: Int, to: Int) {
        val fromIndex = from.coerceIn(0 .. answers.lastIndex)
        val toIndex = to.coerceIn(0 .. answers.lastIndex)
        val item = answers.removeAt(fromIndex)
        answers.add(toIndex, item)
    }

    fun canSendPoll(): Boolean {
        return question.isNotBlank() && answers.count { it.text.isNotBlank() } >= MIN_ANSWERS && duplicateAnswers.isEmpty() && (expirationDate > System.currentTimeMillis())
    }

    private fun addAnswer() {
        if (answers.size < MAX_ANSWERS) {
            answers.add(PollAnswer(UUID.randomUUID(), ""))
        }
        validateAnswers()
    }

    private fun removeAnswer(answerId: UUID) {
        answers.removeIf { it.uuid == answerId }
        validateAnswers()
    }

    private fun validateAnswers() {
        val duplicated =
            answers.groupBy { it.text.trim() }.filter { it.key.isNotBlank() && it.value.size > 1 }
                .flatMap { it.value }
        duplicateAnswers = duplicated.map { it.uuid }

        if (answers.size >= MIN_ANSWERS && answers.size < MAX_ANSWERS && answers.all { it.text.isNotBlank() }) {
            answers.add(PollAnswer(UUID.randomUUID(), ""))
        }

        if (hasNoneAnswer) {
            if (answers.last() != NONE_ANSWER) {
                answers.remove(NONE_ANSWER)

                // if we already have the max number of answers, remove the last one to add the "None" answer
                if (answers.size == MAX_ANSWERS) {
                    answers.removeAt(answers.size - 1)
                }
                answers.add(NONE_ANSWER)
            }
        } else {
            answers.remove(NONE_ANSWER)
        }
    }

    private fun getMessageBody(context: Context): String {
        return buildString {
            appendLine("📊\u0020**${question.trim()}**")
            answers.forEachIndexed { index, answer ->
                if (answer == NONE_ANSWER) {
                    appendLine("${index + 1}. ${context.getString(R.string.text_none_answer)}")
                    return@forEachIndexed
                }
                if (answer.text.isNotBlank()) {
                    appendLine("${index + 1}. ${answer.text.trim()}")
                }
            }
            if (multipleChoice) {
                appendLine("✅\u0020*${context.getString(R.string.explanation_poll_answer_multiple_choice)}*")
            }
            if (expirationDateEnabled) {
                appendLine("⏱️\u0020*${StringUtils.getLongNiceDateString(context, expirationDate)}*")
            }
        }
    }

    fun sendPoll(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            PostMessageInDiscussionTask(
                getMessageBody(context = context),
                discussionId,
                false,
                null,
                null,
                JsonPoll().apply {
                    this.answerType = "string"
                    this.question = this@PollCreationViewModel.question.trim()
                    this.candidates = answers.filter { it.text.isNotBlank() }.map {
                        JsonPollCandidate().apply {
                            uuid = it.uuid
                            text = it.text.trim()
                        }
                    }
                    this.multipleChoice = this@PollCreationViewModel.multipleChoice
                    this.expiration = this@PollCreationViewModel.expirationDate.takeIf { expirationDateEnabled }
                }).run()
        }
    }
}

fun Message.postPollVote(voteUuid: UUID, voted: Boolean): Boolean {
    // No need to check the expiration, this is already checked before calling postPollVote
    val db = AppDatabase.getInstance()
    val discussion = db.discussionDao().getById(discussionId)

    if (discussion == null || !discussion.isNormalOrReadOnly) {
        Logger.e("Trying to vote for a poll in a locked discussion!!!")
        return true
    }
    val contacts: List<Contact> = when (discussion.discussionType) {
        Discussion.TYPE_CONTACT ->
            buildList {
                db.contactDao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]?.let {
                    add(it)
                }
            }

        Discussion.TYPE_GROUP -> db.contactGroupJoinDao().getGroupContactsSync(
            discussion.bytesOwnedIdentity,
            discussion.bytesDiscussionIdentifier
        )

        Discussion.TYPE_GROUP_V2 -> db.group2MemberDao().getGroupMemberContactsSync(
            discussion.bytesOwnedIdentity,
            discussion.bytesDiscussionIdentifier
        )

        else -> {
            Logger.e("Unknown discussion type for poll!!!")
            return true
        }
    }

    val byteContactIdentities = ArrayList<ByteArray>(contacts.size)
    for (contact in contacts) {
        byteContactIdentities.add(contact.bytesContactIdentity)
    }

    // also notify other owned devices
    if (db.ownedDeviceDao()
            .doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity)
    ) {
        byteContactIdentities.add(discussion.bytesOwnedIdentity)
    }

    poll?.let { poll ->
        val pollVote: PollVote
        if (poll.multipleChoice) {
            pollVote = db.pollVoteDao().get(
                messageId = id,
                voter = discussion.bytesOwnedIdentity,
                voteUuid = voteUuid
            )?.apply {
                this.serverTimestamp = System.currentTimeMillis()
                this.version = version.inc()
                this.voted = voted
            } ?: PollVote(
                messageId = id,
                voteUuid = voteUuid,
                voter = discussion.bytesOwnedIdentity,
                serverTimestamp = System.currentTimeMillis(),
                voted = voted,
                version = 0
            )

            db.pollVoteDao().upsert(pollVote)
        } else {
            // we cannot do an upsert here, as in some cases we are changing the primary key (the vote uuid changes)

            // fetch all existing votes to get the largest version number
            val votes = db.pollVoteDao().getAllByVoter(
                messageId = id,
                voter = discussion.bytesOwnedIdentity,
            ).sortedBy { it.version }

            pollVote = PollVote(
                messageId = id,
                voteUuid = voteUuid,
                voter = discussion.bytesOwnedIdentity,
                serverTimestamp = System.currentTimeMillis(),
                voted = voted,
                version = votes.lastOrNull()?.version?.inc() ?: 0
            )

            // delete any previous vote
            votes.forEach {
                db.pollVoteDao().delete(it)
            }

            db.pollVoteDao().insert(pollVote)
        }

        // for group discussions with no members (or discussion with self), no need to actually send something
        if (byteContactIdentities.isEmpty()) {
            return true
        }

        runCatching {
            val jsonPollVote = JsonPollVote.of(discussion, this)
            jsonPollVote.pollCandidateUuid = voteUuid
            jsonPollVote.version = pollVote.version
            jsonPollVote.voted = pollVote.voted
            val jsonPayload = JsonPayload()
            jsonPayload.jsonPollVote = jsonPollVote

            val postMessageOutput = AppSingleton.getEngine().post(
                AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload),
                null,
                arrayOfNulls(0),
                byteContactIdentities,
                discussion.bytesOwnedIdentity,
                true,
                false
            )
            return postMessageOutput.isMessagePostedForAtLeastOneContact
        }.onFailure {
            Logger.x(it)
        }
    }
    return false
}

fun JsonPollCandidate.getText(context: Context) : String {
    return if (uuid == PollCreationViewModel.NONE_ANSWER.uuid) context.getString(R.string.text_none_answer) else text.orEmpty()
}