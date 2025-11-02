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
package io.olvid.messenger.main.invitations

import android.app.AlertDialog.Builder
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.fasterxml.jackson.core.JsonProcessingException
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonGroupDetails
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.ObvDialog
import io.olvid.engine.engine.types.ObvDialog.Category
import io.olvid.engine.engine.types.identities.ObvGroupV2
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.ABORT
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.GO_TO
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.IGNORE
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.REJECT
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.VALIDATE_SAS
import io.olvid.messenger.settings.SettingsActivity
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.min

class InvitationListViewModel : ViewModel() {

    enum class Action {
        GO_TO, ACCEPT, REJECT, IGNORE, VALIDATE_SAS, ABORT
    }

    var lastSas: String? = null
        private set
    var lastTimestamp: Long? = 0
        private set
    var lastSasDialogUUID: UUID? = null
        private set

    private fun setLastSas(lastSas: String?, uuid: UUID?, lastTimestamp: Long?) {
        this.lastSas = lastSas
        this.lastTimestamp = lastTimestamp
        lastSasDialogUUID = uuid
    }

    val invitations: LiveData<List<Invitation>> = InvitationRepository.invitations

    fun initialViewSetup(initialView: InitialView, invitation: Invitation) {
        val dialog = invitation.associatedDialog
        initialView.reset()
        val categoryId = dialog.category.id
        when (categoryId) {
            Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY -> {
                initialView.setGroup(dialog.category.bytesGroupOwnerAndUid)
            }

            Category.GROUP_V2_INVITATION_DIALOG_CATEGORY,
            Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY -> {
                initialView.setGroup(dialog.category.obvGroupV2.groupIdentifier.bytes)
            }

            Category.INVITE_SENT_DIALOG_CATEGORY,
            Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY,
            Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY,
            Category.SAS_EXCHANGE_DIALOG_CATEGORY,
            Category.SAS_CONFIRMED_DIALOG_CATEGORY,
            Category.ACCEPT_INVITE_DIALOG_CATEGORY,
            Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY,
            Category.INVITE_ACCEPTED_DIALOG_CATEGORY,
            Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY -> {
                val contactIdentity = dialog.category.bytesContactIdentity
                if (ContactCacheSingleton.getContactCustomDisplayName(contactIdentity) != null) {
                    initialView.setFromCache(contactIdentity)
                } else {
                    val invitationName =
                        if (categoryId == Category.INVITE_SENT_DIALOG_CATEGORY) dialog.category.contactDisplayNameOrSerializedDetails.toString()
                        else getFormattedDisplayName(dialog.category.contactDisplayNameOrSerializedDetails ?: "")
                    initialView.setInitial(contactIdentity, StringUtils.getInitial(invitationName ?: ""))
                }
            }

            else -> {
                val invitationName = getFormattedDisplayName(dialog.category.contactDisplayNameOrSerializedDetails ?: "")
                initialView.setInitial(
                    invitation.associatedDialog.category.bytesContactIdentity,
                    StringUtils.getInitial(
                        invitationName ?: ""
                    )
                )
            }
        }
    }

    private data class ContactAnnotation(val name: String, val annotation: String? = null)

    fun listGroupMembersAsync(associatedDialog: ObvDialog): AnnotatedString {
        return buildAnnotatedString {
            val bytesOwnedIdentity: ByteArray = associatedDialog.bytesOwnedIdentity

            if (associatedDialog.category
                    .id == Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY
            ) {
                associatedDialog.category.pendingGroupMemberIdentities.map { contactIdentity ->
                    AppDatabase.getInstance()
                        .contactDao()[bytesOwnedIdentity, contactIdentity.bytesIdentity]?.getCustomDisplayName()
                        ?.let {
                            ContactAnnotation(
                                it,
                                "${
                                    String(
                                        android.util.Base64.encode(
                                            bytesOwnedIdentity,
                                            android.util.Base64.NO_PADDING
                                        )
                                    )
                                }|${
                                    String(
                                        android.util.Base64.encode(
                                            contactIdentity.bytesIdentity,
                                            android.util.Base64.NO_PADDING
                                        )
                                    )
                                }"
                            )
                        } ?: ContactAnnotation(
                        contactIdentity.identityDetails.formatDisplayName(
                            SettingsActivity.contactDisplayNameFormat,
                            SettingsActivity.uppercaseLastName
                        )
                    )
                }.sortedWith(Comparator { cs1, cs2 ->
                    val minLen = min(cs1.name.length, cs2.name.length)
                    var i = 0
                    while (i < minLen) {
                        if (cs1.name[i] != cs2.name[i]) {
                            return@Comparator cs1.name[i].code - cs2.name[i].code
                        }
                        i++
                    }
                    cs2.name.length - cs1.name.length
                }).forEachIndexed { index, contactAnnotation ->
                    contactAnnotation.annotation?.let {
                        pushStringAnnotation(
                            tag = "CONTACT",
                            annotation = contactAnnotation.annotation
                        )
                        withStyle(
                            style = SpanStyle(
                                color = Color.Blue,
                                textDecoration = TextDecoration.Underline,
                            )
                        ) {
                            append(contactAnnotation.name)
                        }
                        pop()
                    } ifNull {
                        append(
                            contactAnnotation.name
                        )
                    }
                    if (index < associatedDialog.category.pendingGroupMemberIdentities.size - 1) {
                        append(App.getContext().getString(R.string.text_contact_names_separator))
                    }
                }
            } else if (associatedDialog.category
                    .id == Category.GROUP_V2_INVITATION_DIALOG_CATEGORY
                || associatedDialog.category
                    .id == Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY
            ) {
                associatedDialog.category.obvGroupV2.pendingGroupMembers.map { groupV2Member ->
                    AppDatabase.getInstance()
                        .contactDao()[bytesOwnedIdentity, groupV2Member.bytesIdentity]?.getCustomDisplayName()
                        ?.let {
                            ContactAnnotation(
                                it,
                                "${
                                    String(
                                        android.util.Base64.encode(
                                            bytesOwnedIdentity,
                                            android.util.Base64.NO_PADDING
                                        )
                                    )
                                }|${
                                    String(
                                        android.util.Base64.encode(
                                            groupV2Member.bytesIdentity,
                                            android.util.Base64.NO_PADDING
                                        )
                                    )
                                }"
                            )
                        } ?: kotlin.run {
                        try {
                            val identityDetails =
                                AppSingleton.getJsonObjectMapper().readValue(
                                    groupV2Member.serializedDetails,
                                    JsonIdentityDetails::class.java
                                )
                            ContactAnnotation(
                                identityDetails.formatDisplayName(
                                    SettingsActivity.contactDisplayNameFormat,
                                    SettingsActivity.uppercaseLastName
                                )
                            )
                        } catch (_: JsonProcessingException) {
                            ContactAnnotation("???")
                        }
                    }
                }.sortedWith(Comparator { cs1, cs2 ->
                    val minLen = min(cs1.name.length, cs2.name.length)
                    var i = 0
                    while (i < minLen) {
                        if (cs1.name[i] != cs2.name[i]) {
                            return@Comparator cs1.name[i].code - cs2.name[i].code
                        }
                        i++
                    }
                    cs2.name.length - cs1.name.length
                }).forEachIndexed { index, contactAnnotation ->
                    contactAnnotation.annotation?.let {
                        pushStringAnnotation(
                            tag = "CONTACT",
                            annotation = contactAnnotation.annotation
                        )
                        withStyle(
                            style = SpanStyle(
                                textDecoration = TextDecoration.Underline,
                            )
                        ) {
                            append(contactAnnotation.name)
                        }
                        pop()
                    } ifNull {
                        append(
                            contactAnnotation.name
                        )
                    }
                    if (index < associatedDialog.category.obvGroupV2.pendingGroupMembers.size - 1) {
                        append(App.getContext().getString(R.string.text_contact_names_separator))
                    }
                }
            }
            // still empty -> nobody
            if (length == 0) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(App.getContext().getString(R.string.text_nobody))
                }
            }
        }
    }

    private fun getFormattedDisplayName(serializedDetails: String): String? {
        return runCatching {
            AppSingleton.getJsonObjectMapper()
                .readValue(serializedDetails, JsonIdentityDetails::class.java)
                .formatDisplayName(
                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                    SettingsActivity.uppercaseLastName
                )
        }.getOrNull()
    }

    fun displayStatusDescriptionTextAsync(associatedDialog: ObvDialog): String? {
        val context = App.getContext()
        return when (associatedDialog.category.id) {
            Category.INVITE_SENT_DIALOG_CATEGORY ->
                context.getString(
                    R.string.invitation_status_description_invite_sent,
                    associatedDialog.category.contactDisplayNameOrSerializedDetails
                )

            Category.ACCEPT_INVITE_DIALOG_CATEGORY ->
                getFormattedDisplayName(associatedDialog.category.contactDisplayNameOrSerializedDetails)?.let {
                    context.getString(R.string.invitation_status_description_accept_invite, it)
                }

            Category.SAS_EXCHANGE_DIALOG_CATEGORY ->
                getFormattedDisplayName(associatedDialog.category.contactDisplayNameOrSerializedDetails)?.let {
                    context.getString(R.string.invitation_status_description_enter_their_sas, it)
                }

            Category.SAS_CONFIRMED_DIALOG_CATEGORY ->
                getFormattedDisplayName(associatedDialog.category.contactDisplayNameOrSerializedDetails)?.let {
                    context.getString(R.string.invitation_status_description_give_him_sas, it)
                }

            Category.INVITE_ACCEPTED_DIALOG_CATEGORY ->
                getFormattedDisplayName(associatedDialog.category.contactDisplayNameOrSerializedDetails)?.let {
                    context.getString(R.string.invitation_status_description_invite_accepted, it)
                }

            Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY -> {
                val mediator = AppDatabase.getInstance()
                    .contactDao()[associatedDialog.bytesOwnedIdentity, associatedDialog.category.bytesMediatorOrGroupOwnerIdentity]
                getFormattedDisplayName(associatedDialog.category.contactDisplayNameOrSerializedDetails)?.let { displayName ->
                    if (mediator != null) {
                        context.getString(
                            R.string.invitation_status_description_accept_mediator_invite,
                            mediator.getCustomDisplayName(),
                            displayName
                        )
                    } else {
                        context.getString(
                            R.string.invitation_status_description_accept_mediator_invite_deleted,
                            displayName
                        )
                    }
                }
            }

            Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY -> {
                val mediator = AppDatabase.getInstance()
                    .contactDao()[associatedDialog.bytesOwnedIdentity, associatedDialog.category.bytesMediatorOrGroupOwnerIdentity]
                getFormattedDisplayName(associatedDialog.category.contactDisplayNameOrSerializedDetails)?.let { displayName ->
                    if (mediator != null) {
                        context.getString(
                            R.string.invitation_status_description_mediator_invite_accepted,
                            displayName,
                            mediator.getCustomDisplayName()
                        )
                    } else {
                        context.getString(
                            R.string.invitation_status_description_mediator_invite_accepted_deleted,
                            displayName
                        )
                    }
                }
            }

            Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY, Category.GROUP_V2_INVITATION_DIALOG_CATEGORY -> {
                val groupOwner =
                    AppDatabase.getInstance()
                        .contactDao()[associatedDialog.bytesOwnedIdentity, associatedDialog.category
                        .bytesMediatorOrGroupOwnerIdentity]
                if (groupOwner != null) context.getString(
                    R.string.invitation_status_description_accept_group_invite,
                    groupOwner.getCustomDisplayName()
                )
                else context.getString(R.string.invitation_status_description_contact_or_mediator_deleted)
            }

            Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY ->
                context.getString(R.string.invitation_status_description_group_v2_frozen_invitation)

            Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY -> {
                val contact = AppDatabase.getInstance()
                    .contactDao()[associatedDialog.bytesOwnedIdentity, associatedDialog.category
                    .bytesContactIdentity]
                if (contact != null) {
                    context.getString(
                        R.string.invitation_status_description_one_to_one_invitation_sent,
                        contact.getCustomDisplayName()
                    )
                } else {
                    context.getString(R.string.invitation_status_description_one_to_one_invitation_sent_deleted)
                }
            }

            Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY -> {
                val contact = AppDatabase.getInstance()
                    .contactDao()[associatedDialog.bytesOwnedIdentity, associatedDialog.category
                    .bytesContactIdentity]
                if (contact != null) {
                    context.getString(
                        R.string.invitation_status_description_one_to_one_invitation,
                        contact.getCustomDisplayName()
                    )
                } else {
                    context.getString(R.string.invitation_status_description_contact_or_mediator_deleted)
                }
            }

            else -> null
        }
    }

    fun invitationClicked(
        action: Action,
        invitation: Invitation,
        lastSas: String?,
        context: Context
    ) {
        val dialog = invitation.associatedDialog
        when (action) {
            GO_TO, Action.ACCEPT ->
                when (dialog.category.id) {
                    Category.ACCEPT_INVITE_DIALOG_CATEGORY -> {
                        try {
                            dialog.setResponseToAcceptInvite(true)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }

                    Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY -> {
                        try {
                            dialog.setResponseToAcceptMediatorInvite(true)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }

                    Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY, Category.GROUP_V2_INVITATION_DIALOG_CATEGORY -> {
                        try {
                            dialog.setResponseToAcceptGroupInvite(true)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }

                    Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY -> {
                        try {
                            dialog.setResponseToAcceptOneToOneInvitation(true)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }
                }

            REJECT ->
                when (dialog.category.id) {
                    Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY -> {
                        try {
                            dialog.setAbortOneToOneInvitationSent(true)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }

                    Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY -> {
                        try {
                            dialog.setResponseToAcceptOneToOneInvitation(false)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }

                    Category.GROUP_V2_INVITATION_DIALOG_CATEGORY, Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY -> {
                        try {
                            dialog.setResponseToAcceptGroupInvite(false)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }
                }

            IGNORE ->
                when (dialog.category.id) {
                    Category.ACCEPT_INVITE_DIALOG_CATEGORY -> {
                        try {
                            dialog.setResponseToAcceptInvite(false)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }

                    Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY -> {
                        try {
                            dialog.setResponseToAcceptMediatorInvite(false)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }

                    Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY -> {
                        try {
                            dialog.setResponseToAcceptGroupInvite(false)
                            AppSingleton.getEngine().respondToDialog(dialog)
                        } catch (e: Exception) {
                            Logger.x(e)
                        }
                    }
                }

            VALIDATE_SAS ->
                if (dialog.category.id == Category.SAS_EXCHANGE_DIALOG_CATEGORY && lastSas != null) {
                    try {
                        setLastSas(lastSas, dialog.uuid, invitation.invitationTimestamp)
                        dialog.setResponseToSasExchange(lastSas.toByteArray(StandardCharsets.UTF_8))
                        AppSingleton.getEngine().respondToDialog(dialog)
                    } catch (e: Exception) {
                        Logger.x(e)
                    }
                }

            ABORT ->
                when (dialog.category.id) {
                    Category.INVITE_SENT_DIALOG_CATEGORY, Category.INVITE_ACCEPTED_DIALOG_CATEGORY, Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY, Category.SAS_EXCHANGE_DIALOG_CATEGORY, Category.SAS_CONFIRMED_DIALOG_CATEGORY -> {
                        val builder =
                            Builder(context, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_abort_invitation)
                                .setPositiveButton(R.string.button_label_abort) { _, _ ->
                                    try {
                                        AppSingleton.getEngine()
                                            .abortProtocol(invitation.associatedDialog)
                                    } catch (e: Exception) {
                                        Logger.x(e)
                                    }
                                }
                                .setNegativeButton(R.string.button_label_cancel, null)
                        builder.create().show()
                    }

                    Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY -> {
                        val builder =
                            Builder(context, R.style.CustomAlertDialog)
                                .setTitle(R.string.dialog_title_abort_invitation)
                                .setPositiveButton(R.string.button_label_ok) { _, _ ->
                                    try {
                                        val obvDialog = invitation.associatedDialog
                                        obvDialog.setAbortOneToOneInvitationSent(true)
                                        AppSingleton.getEngine().respondToDialog(obvDialog)
                                    } catch (e: Exception) {
                                        Logger.x(e)
                                    }
                                }
                                .setNegativeButton(R.string.button_label_cancel, null)
                        builder.create().show()
                    }
                }
        }
    }
}

fun Invitation.getAnnotatedTitle(context: Context): AnnotatedString {
    return buildAnnotatedString {
        when (associatedDialog.category.id) {
            Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY -> {
                try {
                    val groupDetails = AppSingleton.getJsonObjectMapper().readValue(
                        associatedDialog.category.serializedGroupDetails,
                        JsonGroupDetails::class.java
                    )
                    append(groupDetails.name)
                } catch (e: Exception) {
                    Logger.x(e)
                }
            }

            Category.GROUP_V2_INVITATION_DIALOG_CATEGORY, Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY -> {
                try {
                    val groupDetails = AppSingleton.getJsonObjectMapper().readValue(
                        associatedDialog.category.obvGroupV2.detailsAndPhotos.serializedGroupDetails,
                        JsonGroupDetails::class.java
                    )
                    if (groupDetails.isEmpty) {
                        associatedDialog.category.obvGroupV2.getReadableMembers()?.let {
                            append(it)
                        } ifNull {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(context.getString(R.string.text_unnamed_group))
                            }
                        }
                    } else {
                        append(groupDetails.name)
                    }
                } catch (e: Exception) {
                    Logger.x(e)
                }
            }

            Category.INVITE_SENT_DIALOG_CATEGORY -> {
                append(
                    ContactCacheSingleton.getContactCustomDisplayName(associatedDialog.category.bytesContactIdentity)
                        ?: associatedDialog.category.contactDisplayNameOrSerializedDetails
                )
            }

            Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY, Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY -> {
                append(
                    ContactCacheSingleton.getContactCustomDisplayName(associatedDialog.category.bytesContactIdentity)
                        ?: context.getString(R.string.text_deleted_contact)
                )
            }

            Category.SAS_EXCHANGE_DIALOG_CATEGORY,
            Category.SAS_CONFIRMED_DIALOG_CATEGORY,
            Category.ACCEPT_INVITE_DIALOG_CATEGORY,
            Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY,
            Category.INVITE_ACCEPTED_DIALOG_CATEGORY,
            Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY -> {
                try {
                    AppSingleton.getJsonObjectMapper().readValue(
                        associatedDialog.category.contactDisplayNameOrSerializedDetails,
                        JsonIdentityDetails::class.java
                    ).formatDisplayName(
                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                        SettingsActivity.uppercaseLastName
                    )
                } catch (e: Exception) {
                    Logger.x(e)
                    null
                }?.let {
                    append(it)
                }
            }


            else -> {
                try {
                    AppSingleton.getJsonObjectMapper().readValue(
                        associatedDialog.category.contactDisplayNameOrSerializedDetails,
                        JsonIdentityDetails::class.java
                    ).formatDisplayName(
                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                        SettingsActivity.uppercaseLastName
                    )
                } catch (e: Exception) {
                    Logger.x(e)
                    null
                }?.let { append(it) }
            }
        }
    }
}

fun Invitation.getAnnotatedDate(context: Context) =
    AnnotatedString(StringUtils.getNiceDateString(context, getTimestamp()).toString())

fun Invitation.getTimestamp(): Long {
    return when (associatedDialog.category.id) {
        Category.ACCEPT_INVITE_DIALOG_CATEGORY,
        Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY,
        Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY,
        Category.SAS_EXCHANGE_DIALOG_CATEGORY,
        Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY ->
            associatedDialog.category.serverTimestamp

        else -> invitationTimestamp
    }
}

fun ObvGroupV2.getReadableMembers(): String? {
    return runCatching {
        StringUtils.joinContactDisplayNames(
            otherGroupMembers?.map {
                ContactCacheSingleton.getContactCustomDisplayName(it.bytesIdentity)
            }.orEmpty()
                .plus(pendingGroupMembers?.map {
                    AppSingleton.getJsonObjectMapper().readValue(
                        it.serializedDetails,
                        JsonIdentityDetails::class.java
                    ).formatDisplayName(
                        SettingsActivity.contactDisplayNameFormat,
                        SettingsActivity.uppercaseLastName
                    )
                }.orEmpty()).toTypedArray()
        )
    }.getOrNull()
}