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
package io.olvid.messenger.discussion.compose

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.entity.jsons.JsonMessageReference
import java.io.File

class ComposeMessageViewModel(
    private val discussionIdLiveData: LiveData<Long>,
    discussionCustomization: LiveData<DiscussionCustomization?>
) : ViewModel() {
    private val db: AppDatabase = AppDatabase.getInstance()
    private val recordingLiveData: MutableLiveData<Boolean> = MutableLiveData(false)
    private val draftMessageFyles: LiveData<List<FyleAndStatus>?> =
        discussionIdLiveData.switchMap { discussionId: Long? ->
            if (discussionId == null) {
                return@switchMap null
            }
            db.fyleDao().getDiscussionDraftFyles(discussionId)
        }
    private val draftMessageFylesEditMode = MediatorLiveData<List<FyleAndStatus>?>()
    private val draftMessage: LiveData<Message> = discussionIdLiveData.switchMap { discussionId: Long? ->
        if (discussionId == null) {
            return@switchMap null
        }
        db.messageDao().getDiscussionDraftMessage(discussionId)
    }
    private val draftMessageEditMode = MediatorLiveData<Message?>()
    private val draftMessageReply: LiveData<Message?> = draftMessage.switchMap { draftMessage: Message? ->
        if (draftMessage?.jsonReply == null) {
            return@switchMap MutableLiveData<Message?>(null)
        }
        val jsonReply: JsonMessageReference = try {
            AppSingleton.getJsonObjectMapper().readValue(
                draftMessage.jsonReply,
                JsonMessageReference::class.java
            )
        } catch (_: Exception) {
            return@switchMap MutableLiveData<Message?>(null)
        }
        db.messageDao().getBySenderSequenceNumberAsync(
            jsonReply.senderSequenceNumber,
            jsonReply.senderThreadIdentifier,
            jsonReply.senderIdentifier,
            draftMessage.discussionId
        )
    }
    private val draftMessageReplyEditMode = MediatorLiveData<Message?>()
    private val draftMessageEdit = MutableLiveData<Message?>(null)
    val ephemeralSettingsChanged: LiveData<Boolean>
    var rawNewMessageText: CharSequence? = null
        private set

    // region take picture from discussion
    var photoOrVideoUri: Uri? = null
    var photoOrVideoFile: File? = null
    var openEphemeralSettings by mutableStateOf(false)

    init {
        draftMessageEditMode.addSource(draftMessage) { draft: Message? ->
            draftMessageEditMode.value = if (draftMessageEdit.value != null) null else draft
        }
        draftMessageEditMode.addSource(draftMessageEdit) { edit: Message? ->
            draftMessageEditMode.value = if (edit != null) null else draftMessage.value
        }
        draftMessageFylesEditMode.addSource(draftMessageFyles) { draftFyles: List<FyleAndStatus>? ->
            draftMessageFylesEditMode.value = if (draftMessageEdit.value != null) null else draftFyles
        }
        draftMessageFylesEditMode.addSource(draftMessageEdit) { edit: Message? ->
            draftMessageFylesEditMode.value = if (edit != null) null else draftMessageFyles.value
        }
        draftMessageReplyEditMode.addSource(draftMessageReply) { message: Message? ->
            draftMessageReplyEditMode.value = if (draftMessageEdit.value != null) null else message
        }
        draftMessageReplyEditMode.addSource(draftMessageEdit) { edit: Message? ->
            draftMessageReplyEditMode.value = if (edit != null) null else draftMessageReply.value
        }
        ephemeralSettingsChanged = EphemeralSettingsChangedLiveData(
            draftMessage,
            discussionCustomization,
            draftMessageEdit
        )
    }

    fun setRecording(recording: Boolean) {
        recordingLiveData.postValue(recording)
    }

    fun getRecordingLiveData(): LiveData<Boolean> {
        return recordingLiveData
    }

    fun setNewMessageText(newMessageText: CharSequence?) {
        rawNewMessageText = newMessageText
    }

    val trimmedNewMessageText: String?
        get() = if (rawNewMessageText != null && rawNewMessageText.toString()
                .trim().isNotEmpty()
        ) {
            rawNewMessageText.toString().trim()
        } else null

    fun getDraftMessageFyles(): LiveData<List<FyleAndStatus>?> {
        return draftMessageFylesEditMode
    }

    fun getDraftMessage(): LiveData<Message?> {
        return draftMessageEditMode
    }

    fun getDraftMessageReply(): LiveData<Message?> {
        return draftMessageReplyEditMode
    }

    fun getDraftMessageEdit(): LiveData<Message?> {
        return draftMessageEdit
    }

    fun setDraftMessageEdit(message: Message) {
        draftMessageEdit.value = message
    }

    fun clearDraftMessageEdit() {
        draftMessageEdit.value = null
    }

    fun hasAttachments(): Boolean {
        return draftMessageFyles.value.isNullOrEmpty().not()
    }

    class EphemeralSettingsChangedLiveData(
        draftMessage: LiveData<Message>,
        discussionCustomization: LiveData<DiscussionCustomization?>,
        draftMessageEdit: LiveData<Message?>
    ) : MediatorLiveData<Boolean>() {
        private var draftMessageExpiration: JsonExpiration? = null
        private var discussionCustomizationExpiration: JsonExpiration? = null
        private var isEditing = false

        init {
            addSource(draftMessage) { message: Message? -> onDraftMessageChanged(message) }
            addSource(discussionCustomization) { customization -> onDiscussionCustomizationChanged(customization) }
            addSource(draftMessageEdit) { editedMessage: Message? -> onDraftMessageEditChanged(editedMessage) }
            value = false
        }

        private fun onDraftMessageChanged(message: Message?) {
            draftMessageExpiration = if (message?.jsonExpiration == null) {
                null
            } else {
                try {
                    AppSingleton.getJsonObjectMapper()
                        .readValue(message.jsonExpiration, JsonExpiration::class.java)
                } catch (_: Exception) {
                    null
                }
            }
            compareJsonExpirations()
        }

        private fun onDiscussionCustomizationChanged(discussionCustomization: DiscussionCustomization?) {
            discussionCustomizationExpiration = discussionCustomization?.expirationJson
            compareJsonExpirations()
        }

        private fun onDraftMessageEditChanged(editedMessage: Message?) {
            isEditing = editedMessage != null
            compareJsonExpirations()
        }

        private fun compareJsonExpirations() {
            value =
                (!isEditing && draftMessageExpiration != null && (discussionCustomizationExpiration != null || !draftMessageExpiration!!.likeNull())
                        && draftMessageExpiration != discussionCustomizationExpiration)
        }
    }

    // endregion
    val discussionId: Long?
        get() = discussionIdLiveData.value
}