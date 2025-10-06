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

package io.olvid.messenger.main.contacts

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback

@Composable
fun ContactInvitationPopup(
    contactOrKeycloakDetails: ContactListViewModel.ContactOrKeycloakDetails,
    onDismiss: () -> Unit,
    onInvite: (contact: Contact) -> Unit,
    onOpenDetails: (contact: Contact) -> Unit,
    onOpenDiscussion: (bytesOwnedIdentity: ByteArray, bytesContactIdentity: ByteArray) -> Unit,
) {
    var inviteWasJustSent by remember { mutableStateOf(false) }
    val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity() ?: return
    val bytesContactIdentity = contactOrKeycloakDetails.contact?.bytesContactIdentity
        ?: contactOrKeycloakDetails.keycloakUserDetails?.identity ?: return
    val successComposition by rememberLottieComposition(LottieCompositionSpec.Asset("confetti.zip"))
    val groups by AppDatabase.getInstance().discussionDao()
        .getContactNotLockedGroupDiscussionsWithGroupMembersNames(
            bytesContactIdentity,
            bytesOwnedIdentity
        ).observeAsState()
    val inviteSent by AppDatabase.getInstance()
        .invitationDao()
        .getContactOneToOneInvitation(
            bytesOwnedIdentity,
            bytesContactIdentity
        )
        .observeAsState()
    var inviteWasSent = false
    val contactDiscussion by AppDatabase.getInstance()
        .discussionDao()
        .getByContactLiveData(
            bytesOwnedIdentity,
            bytesContactIdentity
        )
        .observeAsState()
    val name = contactOrKeycloakDetails.contact?.getCustomDisplayName()
        ?: contactOrKeycloakDetails.getAnnotatedName().toString()
    var addContactSuccess: Boolean? by rememberSaveable { mutableStateOf(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(48.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.dialogBackground))
        ) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .border(1.dp, Color.White.copy(alpha = .2f), shape = CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.content_description_close_button),
                        tint = colorResource(id = R.color.almostBlack)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(40.dp))
                    InitialView(
                        modifier = Modifier.size(144.dp),
                        initialViewSetup = contactOrKeycloakDetails.getInitialViewSetup()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (!inviteWasSent && inviteSent == null && addContactSuccess != true) {
                        Text(
                            text = name,
                            textAlign = TextAlign.Center,
                            color = colorResource(R.color.almostBlack),
                            style = OlvidTypography.h2,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.label_contact_not_one_to_one),
                            textAlign = TextAlign.Center,
                            color = Color(0xFF8B8D97),
                            style = OlvidTypography.body1
                        )
                        groups?.let {
                            if (it.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy((-10).dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    it.forEachIndexed { index, group ->
                                        InitialView(
                                            modifier = Modifier.size(
                                                24.dp
                                            ),
                                            initialViewSetup = { initialView ->
                                                initialView.setDiscussion(group.discussion)
                                            })
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (groups?.size == 1) {
                                            stringResource(
                                                R.string.text_common_group,
                                                groups?.first()?.discussion?.title.orEmpty()
                                            )
                                        } else {
                                            pluralStringResource(
                                                R.plurals.text_common_groups,
                                                (groups?.size ?: 1) - 1,
                                                groups?.first()?.discussion?.title.orEmpty(),
                                                (groups?.size ?: 1) - 1
                                            )
                                        },
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF8B8D97),
                                    style = OlvidTypography.body1
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        OlvidActionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(if (contactOrKeycloakDetails.contactType == ContactListViewModel.ContactType.CONTACT) R.string.button_label_invite else R.string.button_label_add),
                            onClick = {
                                contactOrKeycloakDetails.contact?.let {
                                    onInvite(it)
                                    inviteWasJustSent = true
                                } ?: run {
                                    contactOrKeycloakDetails.keycloakUserDetails?.identity?.let {
                                        KeycloakManager.addContact(
                                            bytesOwnedIdentity,
                                            contactOrKeycloakDetails.keycloakUserDetails.id,
                                            contactOrKeycloakDetails.keycloakUserDetails.identity,
                                            object : KeycloakCallback<Void?> {
                                                override fun success(result: Void?) {
                                                    addContactSuccess = true
                                                    inviteWasJustSent = true
                                                }

                                                override fun failed(rfc: Int) {
                                                    App.toast(
                                                        R.string.toast_message_error_retry,
                                                        Toast.LENGTH_SHORT
                                                    )
                                                }
                                            })
                                    }
                                }
                            },
                            enabled = addContactSuccess == null && contactOrKeycloakDetails.contact?.hasChannelOrPreKey() != false
                        )
                        contactOrKeycloakDetails.contact?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            OlvidActionButton(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = Color.Transparent,
                                contentColor = colorResource(R.color.almostBlack),
                                outlinedColor = colorResource(R.color.darkGrey),
                                text = stringResource(R.string.button_label_see_details),
                                onClick = {
                                    onDismiss.invoke()
                                    onOpenDetails.invoke(it)
                                }
                            )
                        }
                    } else {
                        inviteWasSent = true
                        if (addContactSuccess == true) {
                            Spacer(modifier = Modifier.height(24.dp))
                        } else {
                            LaunchedEffect(contactDiscussion) {
                                contactDiscussion?.let {
                                    onOpenDiscussion.invoke(bytesOwnedIdentity, bytesContactIdentity)
                                    onDismiss.invoke()
                                }
                            }
                        }
                        Text(
                            text = stringResource(
                                if (contactOrKeycloakDetails.contactType == ContactListViewModel.ContactType.CONTACT) R.string.toast_message_invite_sent else R.string.toast_message_contact_added,
                                name
                            ),
                            textAlign = TextAlign.Center,
                            color = colorResource(R.color.almostBlack),
                            style = OlvidTypography.h2,
                            fontWeight = FontWeight.Bold
                        )
                        if (contactOrKeycloakDetails.contactType == ContactListViewModel.ContactType.CONTACT) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.invitation_status_description_invite_sent,
                                    contactOrKeycloakDetails.getAnnotatedName().toString()
                                ),
                                textAlign = TextAlign.Center,
                                color = Color(0xFF8B8D97),
                                style = OlvidTypography.body1
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                            OlvidActionButton(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = Color.Transparent,
                                contentColor = colorResource(R.color.olvid_gradient_light),
                                outlinedColor = colorResource(R.color.olvid_gradient_light),
                                text = stringResource(R.string.button_label_abort),
                                onClick = {
                                    inviteSent?.let { invitation ->
                                        AppSingleton.getEngine()
                                            .abortProtocol(invitation.associatedDialog)
                                    }
                                    onDismiss()
                                }
                            )
                            contactOrKeycloakDetails.contact?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                OlvidActionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = Color.Transparent,
                                    contentColor = colorResource(R.color.almostBlack),
                                    outlinedColor = colorResource(R.color.darkGrey),
                                    text = stringResource(R.string.button_label_see_details),
                                    onClick = {
                                        onDismiss.invoke()
                                        onOpenDetails.invoke(it)
                                    }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(24.dp))
                            OlvidActionButton(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = Color.Transparent,
                                contentColor = colorResource(R.color.olvid_gradient_light),
                                outlinedColor = colorResource(R.color.olvid_gradient_light),
                                text = stringResource(R.string.button_label_discuss),
                                onClick = {
                                    onOpenDiscussion.invoke(bytesOwnedIdentity, bytesContactIdentity)
                                    onDismiss.invoke()
                                },
                                enabled = contactDiscussion != null
                            )
                        }
                    }
                }
                if (inviteWasJustSent && (inviteSent != null || addContactSuccess == true)) {
                    val successProgress by animateLottieCompositionAsState(successComposition)
                    if (successProgress < 1) {
                        LottieAnimation(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            composition = successComposition,
                            progress = { successProgress }
                        )
                    }
                }
            }
        }
    }
}
//
//
//@Preview
//@Composable
//private fun ContactInvitationPopupPreview() {
//    ContactInvitationPopup(
//        contactOrKeycloakDetails = ContactListViewModel.ContactOrKeycloakDetails(
//            Contact(
//                ByteArray(0),
//                ByteArray(0),
//                null,
//                "Joanne",
//                "Joanne",
//                ByteArray(0),
//                "Joanne",
//                "",
//                Contact.PUBLISHED_DETAILS_NOTHING_NEW,
//                1,
//                1,
//                1,
//                null,
//                null,
//                false,
//                null,
//                null,
//                true,
//                false,
//                true,
//                2,
//                true,
//                true,
//                true,
//            )
//        ),
//        onDismiss = {},
//        onInvite = {},
//    )
//}