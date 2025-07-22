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
package io.olvid.messenger.customClasses

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.tasks.DeleteMessagesTask


class SecureDeleteEverywhereDialogBuilder(private val context: Context, private val type: Type, private val count: Int, private val offerToDeleteEverywhere: Boolean, remoteDeletingMakesSense: Boolean) : SecureAlertDialogBuilder(context, R.style.CustomAlertDialog) {
    private val actuallyOfferToDeleteOnOwnedDevices: Boolean
    private var deleteCallback: DeleteCallback? = null

    private var deletionChoice: DeletionChoice = DeletionChoice.LOCAL

    private var choiceLocal: RadioButton? = null
    private var choiceOwnedDevices: RadioButton? = null
    private var choiceEverywhere: RadioButton? = null
    private var deleteEverywhereWarning: TextView? = null
    private var deleteButton: Button? = null


    init {
        if (remoteDeletingMakesSense) {
            val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity()
            actuallyOfferToDeleteOnOwnedDevices = (bytesOwnedIdentity != null) && AppDatabase.getInstance().ownedDeviceDao().doesOwnedIdentityHaveAnotherDeviceWithChannel(bytesOwnedIdentity)
        } else {
            actuallyOfferToDeleteOnOwnedDevices = false
        }
    }

    enum class Type {
        DISCUSSION,
        MESSAGE
    }

    enum class DeletionChoice {
        LOCAL,
        OWNED_DEVICES,
        EVERYWHERE
    }

    fun setDeleteCallback(deleteCallback: DeleteCallback): SecureDeleteEverywhereDialogBuilder {
        this.deleteCallback = deleteCallback
        return this
    }

    private fun setDeletionChoice(deletionChoice: DeletionChoice) {
        this.deletionChoice = deletionChoice
        when (deletionChoice) {
            DeletionChoice.LOCAL -> {
                choiceLocal?.isChecked = true
                choiceOwnedDevices?.isChecked = false
                choiceEverywhere?.isChecked = false
                deleteEverywhereWarning?.visibility = View.GONE
                deleteButton?.setText(R.string.button_label_delete)
            }
            DeletionChoice.OWNED_DEVICES -> {
                choiceLocal?.isChecked = false
                choiceOwnedDevices?.isChecked = true
                choiceEverywhere?.isChecked = false
                deleteEverywhereWarning?.visibility = View.GONE
                deleteButton?.setText(R.string.button_label_delete_from_my_devices)
            }
            DeletionChoice.EVERYWHERE -> {
                choiceLocal?.isChecked = false
                choiceOwnedDevices?.isChecked = false
                choiceEverywhere?.isChecked = true
                deleteEverywhereWarning?.visibility = View.VISIBLE
                deleteButton?.setText(R.string.button_label_delete_everywhere)
            }
        }
    }

    override fun create(): AlertDialog {
        if (actuallyOfferToDeleteOnOwnedDevices && type == Type.MESSAGE) {
            deletionChoice = DeletionChoice.OWNED_DEVICES
        }
        val choices = actuallyOfferToDeleteOnOwnedDevices || offerToDeleteEverywhere


        val inflater = LayoutInflater.from(context)
        @SuppressLint("InflateParams") val dialogView = inflater.inflate(R.layout.dialog_view_delete_locally_or_everywhere, null)

        choiceLocal = dialogView.findViewById(R.id.radio_button_local)
        choiceOwnedDevices = dialogView.findViewById(R.id.radio_button_owned_devices)
        choiceEverywhere = dialogView.findViewById(R.id.radio_button_everywhere)
        deleteEverywhereWarning = dialogView.findViewById(R.id.delete_everywhere_warning)
        val cancelButton : Button = dialogView.findViewById(R.id.cancel_button)
        deleteButton = dialogView.findViewById(R.id.delete_button)

        if (!offerToDeleteEverywhere) {
            choiceEverywhere?.visibility = View.GONE
        }
        if (!actuallyOfferToDeleteOnOwnedDevices) {
            choiceOwnedDevices?.visibility = View.GONE
        }
        if (!choices) {
            choiceLocal?.visibility = View.GONE
        }

        val title = dialogView.findViewById<TextView>(R.id.delete_everywhere_title)
        val message = dialogView.findViewById<TextView>(R.id.delete_everywhere_message)
        when (type) {
            Type.DISCUSSION -> {
                title.text = context.resources.getQuantityString(R.plurals.dialog_title_delete_discussions, count)
                if (choices) {
                    message.text = context.resources.getQuantityString(R.plurals.dialog_message_delete_discussions_choice, count, count)
                } else {
                    message.text = context.resources.getQuantityString(R.plurals.dialog_message_delete_discussions, count, count)
                }
                deleteEverywhereWarning?.text = context.resources.getQuantityString(R.plurals.dialog_warning_delete_discussions_everywhere, count, count).formatMarkdown()
            }
            Type.MESSAGE -> {
                title.text = context.resources.getQuantityString(R.plurals.dialog_title_delete_messages, count)
                if (choices) {
                    message.text = context.resources.getQuantityString(R.plurals.dialog_message_delete_messages_choice, count, count)
                } else {
                    message.text = context.resources.getQuantityString(R.plurals.dialog_message_delete_messages, count, count)
                }
                deleteEverywhereWarning?.text = context.resources.getQuantityString(R.plurals.dialog_warning_delete_messages_everywhere, count, count).formatMarkdown()
            }
        }

        setView(dialogView)
        val alertDialog = super.create()

        // remove the dialog background to allow rounded corners
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // set the default deletion (if any)
        setDeletionChoice(deletionChoice)


        choiceLocal?.setOnClickListener { setDeletionChoice(DeletionChoice.LOCAL) }
        choiceOwnedDevices?.setOnClickListener { setDeletionChoice(DeletionChoice.OWNED_DEVICES) }
        choiceEverywhere?.setOnClickListener { setDeletionChoice(DeletionChoice.EVERYWHERE) }
        cancelButton.setOnClickListener { alertDialog.dismiss() }
        deleteButton?.setOnClickListener {
            alertDialog.dismiss()
            deleteCallback?.performDelete(deletionChoice)
        }

        return alertDialog
    }


    fun interface DeleteCallback {
        fun performDelete(deletionChoice: DeletionChoice)
    }

    companion object {
        fun openForSingleMessage(context: Context, message: Message, discussion: Discussion? = null) {
            App.runThread {
                val offerToRemoteDeleteEverywhere: Boolean
                val remoteDeletingMakesSense: Boolean = listOf(
                    Message.TYPE_INBOUND_MESSAGE,
                    Message.TYPE_OUTBOUND_MESSAGE,
                    Message.TYPE_INBOUND_EPHEMERAL_MESSAGE
                ).contains(message.messageType) && (message.wipeStatus == Message.WIPE_STATUS_NONE)
                if (remoteDeletingMakesSense) {
                    val disc = discussion ?: AppDatabase.getInstance().discussionDao().getById(message.discussionId)
                    when (disc?.discussionType) {
                        Discussion.TYPE_GROUP_V2 -> {
                            val group2 = AppDatabase.getInstance()
                                .group2Dao()[disc.bytesOwnedIdentity, disc.bytesDiscussionIdentifier]
                            offerToRemoteDeleteEverywhere = if (group2 != null) {
                                (((group2.ownPermissionEditOrRemoteDeleteOwnMessages && (message.messageType == Message.TYPE_OUTBOUND_MESSAGE))
                                        || (group2.ownPermissionRemoteDeleteAnything && ((message.messageType == Message.TYPE_INBOUND_MESSAGE) || (message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE))))
                                        && AppDatabase.getInstance().group2MemberDao()
                                    .groupHasMembers(
                                        disc.bytesOwnedIdentity,
                                        disc.bytesDiscussionIdentifier
                                    ))
                            } else {
                                false
                            }
                        }

                        Discussion.TYPE_GROUP -> {
                            offerToRemoteDeleteEverywhere =
                                (disc.isNormal && (message.messageType == Message.TYPE_OUTBOUND_MESSAGE))
                                        && AppDatabase.getInstance().contactGroupJoinDao()
                                    .groupHasMembers(
                                        disc.bytesOwnedIdentity,
                                        disc.bytesDiscussionIdentifier
                                    )
                        }

                        Discussion.TYPE_CONTACT -> {
                            offerToRemoteDeleteEverywhere =
                                (disc.isNormal && (message.messageType == Message.TYPE_OUTBOUND_MESSAGE))
                        }

                        else -> {
                            return@runThread
                        }
                    }
                } else {
                    offerToRemoteDeleteEverywhere = false
                }

                val builder = SecureDeleteEverywhereDialogBuilder(
                    context,
                    Type.MESSAGE,
                    1,
                    offerToRemoteDeleteEverywhere,
                    remoteDeletingMakesSense
                )
                    .setDeleteCallback { deletionChoice: DeletionChoice ->
                        App.runThread(DeleteMessagesTask(listOf(message.id), deletionChoice))
                    }
                Handler(Looper.getMainLooper()).post { builder.create().show() }
            }
        }
    }
}
