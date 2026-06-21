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
package io.olvid.messenger.contact

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason
import io.olvid.engine.engine.types.identities.ObvTrustOrigin
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.DiscussionDao.DiscussionAndGroupMembersNames
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TRUST_ORIGIN_ORDER = listOf(
    ObvTrustOrigin.TYPE.DIRECT,
    ObvTrustOrigin.TYPE.KEYCLOAK,
    ObvTrustOrigin.TYPE.INTRODUCTION,
    ObvTrustOrigin.TYPE.SERVER_GROUP_V2,
    ObvTrustOrigin.TYPE.GROUP
)

data class TrustOriginModel(val title: String, val details: AnnotatedString, val timestamp: String)

class ContactDetailsViewModel : ViewModel() {
    var groupDiscussions: LiveData<MutableList<DiscussionAndGroupMembersNames>?>? = null
        private set
    var contactAndInvitation: LiveData<ContactAndInvitation?>? = null
        private set
    var fullScreenPhotoUrl by mutableStateOf<String?>(null)
    var trustOrigins by mutableStateOf<List<Pair<ObvTrustOrigin.TYPE, List<TrustOriginModel>>>?>(
        null
    )
        private set

    var publishedAndTrustedDetails by mutableStateOf<List<JsonIdentityDetailsWithVersionAndPhoto>>(
        emptyList()
    )

    var activeOrInactiveReasons by mutableStateOf<Set<ObvContactActiveOrInactiveReason>>(emptySet())


    fun setContactBytes(context: Context, bytesOwnedIdentity: ByteArray, bytesContactIdentity: ByteArray) {
        this.groupDiscussions = AppDatabase.getInstance().discussionDao()
            .getContactNotLockedGroupDiscussionsWithGroupMembersNames(
                bytesContactIdentity,
                bytesOwnedIdentity
            )
        this.contactAndInvitation = ContactAndInvitationLiveData(
            AppDatabase.getInstance().contactDao()
                .getAsync(bytesOwnedIdentity, bytesContactIdentity),
            AppDatabase.getInstance().invitationDao()
                .getContactOneToOneInvitation(bytesOwnedIdentity, bytesContactIdentity)
        )
        refresh(
            context = context,
            bytesOwnedIdentity = bytesOwnedIdentity,
            bytesContactIdentity = bytesContactIdentity
        )
    }

    private fun refresh(
        context: Context,
        bytesOwnedIdentity: ByteArray,
        bytesContactIdentity: ByteArray
    ) {
        viewModelScope.launch {
            data class RefreshResult(
                val details: List<JsonIdentityDetailsWithVersionAndPhoto>?,
                val origins: Result<List<Pair<ObvTrustOrigin.TYPE, List<TrustOriginModel>>>>,
                val reasons: Set<ObvContactActiveOrInactiveReason>,
            )
            val result = withContext(Dispatchers.IO) {
                val details = runCatching {
                    AppSingleton.getEngine()
                        .getContactPublishedAndTrustedDetails(
                            bytesOwnedIdentity,
                            bytesContactIdentity
                        )
                        .toList()
                }.getOrNull()
                val origins = runCatching {
                    val originsByType = AppSingleton.getEngine()
                        .getContactTrustOrigins(bytesOwnedIdentity, bytesContactIdentity)
                        .groupBy { it.type }
                    TRUST_ORIGIN_ORDER.mapNotNull { type ->
                        originsByType[type]?.let {
                            type to it.map { origin ->
                                TrustOriginModel(
                                    origin.type.localizedName(context),
                                    origin.toAnnotatedString(context, bytesOwnedIdentity),
                                    StringUtils.getNiceDateString(context, origin.timestamp)
                                        .toString()
                                )
                            }
                        }
                    }
                }
                val reasons = AppSingleton.getEngine().getContactActiveOrInactiveReasons(
                    bytesOwnedIdentity, bytesContactIdentity
                ) ?: emptySet()
                RefreshResult(details, origins, reasons)
            }
            result.details?.let { publishedAndTrustedDetails = it }
            result.origins
                .onSuccess { trustOrigins = it }
                .onFailure { App.toast(R.string.message_error_trust_origin, Toast.LENGTH_SHORT) }
            activeOrInactiveReasons = result.reasons
        }
    }

    fun refreshPublishedAndTrustedDetails(
        bytesOwnedIdentity: ByteArray?,
        bytesContactIdentity: ByteArray?,
    ) {
        if (bytesOwnedIdentity == null || bytesContactIdentity == null) return
        viewModelScope.launch {
            val details = withContext(Dispatchers.IO) {
                runCatching {
                    AppSingleton.getEngine()
                        .getContactPublishedAndTrustedDetails(
                            bytesOwnedIdentity,
                            bytesContactIdentity
                        )
                        .toList()
                }.getOrNull()
            } ?: return@launch
            publishedAndTrustedDetails = details
        }
    }

    fun unblockRevoked(context: Context) {
        val contact = contactAndInvitation?.value?.contact ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                AppSingleton.getEngine().forcefullyUnblockContact(
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity
                )
            }
            if (success) {
                refresh(
                    context = context,
                    bytesOwnedIdentity = contact.bytesOwnedIdentity,
                    bytesContactIdentity = contact.bytesContactIdentity
                )
            } else {
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT)
            }
        }
    }

    fun reblockRevoked(context: Context) {
        val contact = contactAndInvitation?.value?.contact ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                AppSingleton.getEngine().reBlockForcefullyUnblockedContact(
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity
                )
            }
            if (success) {
                refresh(
                    context = context,
                    bytesOwnedIdentity = contact.bytesOwnedIdentity,
                    bytesContactIdentity = contact.bytesContactIdentity
                )
            } else {
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT)
            }
        }
    }

    fun inviteContact() {
        val contact = contactAndInvitation?.value?.contact ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (contact.hasChannelOrPreKey()) {
                        AppSingleton.getEngine().startOneToOneInvitationProtocol(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity
                        )
                    }
                    if (contact.keycloakManaged) {
                        val jsonIdentityDetails = contact.getIdentityDetails()
                        if (jsonIdentityDetails != null && jsonIdentityDetails.signedUserDetails != null) {
                            AppSingleton.getEngine().addKeycloakContact(
                                contact.bytesOwnedIdentity,
                                contact.bytesContactIdentity,
                                jsonIdentityDetails.signedUserDetails
                            )
                        }
                    }
                }.onFailure { Logger.x(it) }
            }
        }
    }

    fun acceptInvitation() {
        val invitation = contactAndInvitation?.value?.invitation ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val obvDialog = invitation.associatedDialog
                    obvDialog.setResponseToAcceptOneToOneInvitation(true)
                    AppSingleton.getEngine().respondToDialog(obvDialog)
                }.onFailure { Logger.x(it) }
            }
        }
    }

    fun rejectInvitation() {
        val invitation = contactAndInvitation?.value?.invitation ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val obvDialog = invitation.associatedDialog
                    obvDialog.setResponseToAcceptOneToOneInvitation(false)
                    AppSingleton.getEngine().respondToDialog(obvDialog)
                }.onFailure { Logger.x(it) }
            }
        }
    }

    fun abortInvitation() {
        val invitation = contactAndInvitation?.value?.invitation ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val obvDialog = invitation.associatedDialog
                    obvDialog.setAbortOneToOneInvitationSent(true)
                    AppSingleton.getEngine().respondToDialog(obvDialog)
                }.onFailure { Logger.x(it) }
            }
        }
    }

    fun openGallery(context: Context) {
        val contact = contactAndInvitation?.value?.contact ?: return
        App.runThread {
            AppDatabase.getInstance().discussionDao()
                .getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity)
                ?.let { App.openDiscussionMediaGalleryActivity(context, it.id) }
        }
    }

    fun restartChannelEstablishment() {
        val contact = contactAndInvitation?.value?.contact ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    AppSingleton.getEngine().restartAllOngoingChannelEstablishmentProtocols(
                        contact.bytesOwnedIdentity,
                        contact.bytesContactIdentity
                    )
                    true
                }.getOrElse {
                    false
                }
            }
            if (success) {
                App.toast(R.string.toast_message_channel_restart_sucessful, Toast.LENGTH_SHORT)
            } else {
                App.toast(R.string.toast_message_channel_restart_failed, Toast.LENGTH_SHORT)
            }
        }
    }

    class ContactAndInvitationLiveData(
        contactLiveData: LiveData<Contact?>,
        invitationLiveData: LiveData<Invitation?>
    ) : MediatorLiveData<ContactAndInvitation?>() {
        var contact: Contact? = null
        var invitation: Invitation? = null

        init {
            addSource<Contact?>(
                contactLiveData,
                Observer { contact: Contact? -> this.updateContact(contact) })
            addSource<Invitation?>(
                invitationLiveData,
                Observer { invitation: Invitation? -> this.updateInvitation(invitation) })
        }

        private fun updateContact(contact: Contact?) {
            this.contact = contact
            value = contact?.let { ContactAndInvitation(contact, invitation) }
        }

        private fun updateInvitation(invitation: Invitation?) {
            this.invitation = invitation
            value = contact?.let { ContactAndInvitation(it, invitation) }
        }
    }

    class ContactAndInvitation(val contact: Contact, val invitation: Invitation?)
}

fun ObvTrustOrigin.toAnnotatedString(
    context: Context,
    bytesOwnedIdentity: ByteArray
): AnnotatedString {
    when (this.type) {
        ObvTrustOrigin.TYPE.DIRECT -> return buildAnnotatedString {
            append(context.getString(R.string.trust_origin_direct_type))
        }

        ObvTrustOrigin.TYPE.INTRODUCTION -> {
            return buildAnnotatedString {
                append(context.getString(R.string.trust_origin_introduction_type))
                val identityDetails = this@toAnnotatedString.mediatorOrGroupOwner.identityDetails
                if (identityDetails != null) {
                    val displayName = identityDetails.formatDisplayName(
                        SettingsActivity.contactDisplayNameFormat,
                        SettingsActivity.uppercaseLastName
                    )
                    withLink(
                        LinkAnnotation.Clickable(
                        tag = "CONTACT",
                        styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = {
                            App.openContactDetailsActivity(
                                context,
                                bytesOwnedIdentity,
                                this@toAnnotatedString.mediatorOrGroupOwner.bytesIdentity
                            )
                        }
                    )) {
                        append(displayName)
                    }
                } else {
                    val deletedStr = context.getString(R.string.text_deleted_contact)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(deletedStr)
                    pop()
                }
            }
        }

        ObvTrustOrigin.TYPE.GROUP -> {
            return buildAnnotatedString {
                append(context.getString(R.string.trust_origin_group_type))
                val identityDetails = this@toAnnotatedString.mediatorOrGroupOwner.identityDetails
                if (identityDetails != null) {
                    val displayName = identityDetails.formatDisplayName(
                        SettingsActivity.contactDisplayNameFormat,
                        SettingsActivity.uppercaseLastName
                    )
                    withLink(
                        LinkAnnotation.Clickable(
                        tag = "CONTACT",
                        styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = {
                            App.openContactDetailsActivity(
                                context,
                                bytesOwnedIdentity,
                                this@toAnnotatedString.mediatorOrGroupOwner.bytesIdentity
                            )
                        }
                    )) {
                        append(displayName)
                    }
                } else {
                    val deletedStr = context.getString(R.string.text_deleted_contact)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(deletedStr)
                    pop()
                }
            }
        }

        ObvTrustOrigin.TYPE.KEYCLOAK -> return buildAnnotatedString {
            append(
                context.getString(
                    R.string.trust_origin_keycloak_type,
                    this@toAnnotatedString.keycloakServer
                )
            )
        }

        ObvTrustOrigin.TYPE.SERVER_GROUP_V2 -> {
            val group2 = AppDatabase.getInstance()
                .group2Dao()[bytesOwnedIdentity, this.bytesGroupIdentifier]
            return buildAnnotatedString {
                append(context.getString(R.string.trust_origin_group_v2_type))
                if (group2 == null) {
                    val deletedStr = context.getString(R.string.text_deleted_group)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(deletedStr)
                    pop()
                } else {
                    withLink(
                        LinkAnnotation.Clickable(
                        tag = "GROUP",
                        styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = {
                            App.openGroupV2DetailsActivity(
                                context,
                                bytesOwnedIdentity,
                                this@toAnnotatedString.bytesGroupIdentifier
                            )
                        }
                    )) {
                        append(group2.truncatedCustomName)
                    }
                }
            }
        }

        else -> return buildAnnotatedString {
            append(context.getString(R.string.trust_origin_unknown_type))
        }
    }
}

fun ObvTrustOrigin.TYPE.localizedName(context: Context): String {
    return when (this) {
        ObvTrustOrigin.TYPE.KEYCLOAK -> context.getString(R.string.trust_origin_keycloak_title)
        ObvTrustOrigin.TYPE.DIRECT -> context.getString(R.string.trust_origin_direct_title)
        ObvTrustOrigin.TYPE.INTRODUCTION -> context.getString(R.string.trust_origin_introduction_title)
        ObvTrustOrigin.TYPE.GROUP, ObvTrustOrigin.TYPE.SERVER_GROUP_V2 -> context.getString(R.string.trust_origin_group_title)
    }
}
