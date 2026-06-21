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

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.linkify
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidActionRow
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.contacts.ContactListItem
import io.olvid.messenger.main.discussions.getAnnotatedTitle
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ContactDetailsScreen(
    contactDetailsViewModel: ContactDetailsViewModel = viewModel(),
    imageClick: (String?) -> Unit = {},
    onIntroduce: () -> Unit = {},
    onFullGroupsList: () -> Unit = {},
    onTrustOrigins: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
) {
    val context = LocalContext.current
    val contactAndInvitation = contactDetailsViewModel.contactAndInvitation?.observeAsState()
    val contact = contactAndInvitation?.value?.contact

    var disableEnterTransitionScope by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(300.milliseconds)
        disableEnterTransitionScope = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(colorResource(R.color.almostWhite))
            .padding(24.dp)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        with(sharedTransitionScope) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = false,
                            color = colorResource(R.color.blueOrWhiteOverlay)
                        ),
                    ) { imageClick(contact?.customPhotoUrl ?: contact?.photoUrl) }
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = contactDetailsViewModel.fullScreenPhotoUrl == null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    InitialView(
                        modifier = Modifier
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "profile-photo"),
                                animatedVisibilityScope = this
                            ),
                        initialViewSetup = { initialView ->
                            contact?.let {
                                initialView.setContact(it)
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // nickname/name
        Text(
            text = contact?.getCustomDisplayName() ?: "",
            style = OlvidTypography.h3.copy(
                color = colorResource(R.color.almostBlack),
                lineHeight = 32.sp,
                fontSize = 32.sp
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (contact?.displayName != contact?.getCustomDisplayName()) {
            Text(
                text = contact?.displayName.orEmpty(),
                style = OlvidTypography.body1.copy(
                    color = colorResource(R.color.almostBlack),
                    fontWeight = FontWeight.SemiBold
                ),
                textAlign = TextAlign.Center
            )
        }

        // position and company
        contactDetailsViewModel.publishedAndTrustedDetails.firstOrNull()?.identityDetails?.formatPositionAndCompany(
            SettingsActivity.contactDisplayNameFormat
        )?.let { positionAndCompany ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = positionAndCompany.linkify(context),
                style = OlvidTypography.body1.copy(
                    color = colorResource(R.color.almostBlack),
                    fontWeight = FontWeight.SemiBold
                ),
                textAlign = TextAlign.Center
            )
        }

        // personal note
        contact?.personalNote?.let { personalNote ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = personalNote.linkify(context),
                style = OlvidTypography.body1.copy(
                    color = colorResource(R.color.greyTint),
                    fontStyle = FontStyle.Italic
                ),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // revoked
        AnimatedVisibility(
            visible = contactDetailsViewModel.activeOrInactiveReasons.contains(
                ObvContactActiveOrInactiveReason.REVOKED
            ),
            enter = if (disableEnterTransitionScope) EnterTransition.None else fadeIn() + expandVertically()
        ) {
            Column {
                RevokedCard(
                    isForcefullyUnblocked = contactDetailsViewModel.activeOrInactiveReasons.contains(
                        ObvContactActiveOrInactiveReason.FORCEFULLY_UNBLOCKED
                    ),
                    onUnblock = { contactDetailsViewModel.unblockRevoked(context) },
                    onReblock = { contactDetailsViewModel.reblockRevoked(context) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // not one to one
        val invitation = contactAndInvitation?.value?.invitation
        AnimatedVisibility(
            visible = contact?.oneToOne == false && contact.active,
            enter = if (disableEnterTransitionScope) EnterTransition.None else fadeIn() + expandVertically()
        ) {
            Column {
                NotOneToOneCard(
                    displayName = contact?.getCustomDisplayName() ?: "",
                    invitation = invitation,
                    onInvite = contactDetailsViewModel::inviteContact,
                    onAccept = contactDetailsViewModel::acceptInvitation,
                    onReject = contactDetailsViewModel::rejectInvitation,
                    onAbort = contactDetailsViewModel::abortInvitation
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        AnimatedVisibility(
            visible = contact?.active == true && (contact.oneToOne || contact.hasChannelOrPreKey()),
            enter = if (disableEnterTransitionScope) EnterTransition.None else fadeIn() + expandVertically()
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (contact?.oneToOne == true) {
                    OlvidActionButton(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.tab_discussions,
                        text = stringResource(R.string.button_label_discuss)
                    ) {
                        contactAndInvitation.value?.contact?.let {
                            App.openOneToOneDiscussionActivity(
                                context,
                                it.bytesOwnedIdentity,
                                it.bytesContactIdentity,
                                true
                            )
                        }
                    }
                }
                if (contactAndInvitation?.value?.contact?.hasChannelOrPreKey() == true) {
                    OlvidActionButton(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.tab_calls,
                        text = stringResource(R.string.button_label_call)
                    ) {
                        contactAndInvitation.value?.contact?.let { contact ->
                            App.startWebrtcCall(
                                context,
                                contact.bytesOwnedIdentity,
                                contact.bytesContactIdentity
                            )
                        }
                    }
                }
            }
        }

        // no recent
        AnimatedVisibility(
            visible = contact?.recentlyOnline == false,
            enter = if (disableEnterTransitionScope) EnterTransition.None else fadeIn() + expandVertically()
        ) {
            Column {
                OfflineCard()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // channel
        AnimatedVisibility(
            visible = contact?.active == true && contact.shouldShowChannelCreationSpinner(),
            enter = if (disableEnterTransitionScope) EnterTransition.None else fadeIn() + expandVertically()
        ) {
            Column {
                ChannelCard(onRestartEstablishment = contactDetailsViewModel::restartChannelEstablishment)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        // contact update
        // index 0 = published (new/incoming), index 1 = trusted (current/accepted)
        val newDetails = remember(contactDetailsViewModel.publishedAndTrustedDetails) {
            contactDetailsViewModel.publishedAndTrustedDetails.takeIf { it.size == 2 }
                ?.firstOrNull()
        }
        val oldDetails = remember(contactDetailsViewModel.publishedAndTrustedDetails) {
            if (contactDetailsViewModel.publishedAndTrustedDetails.size == 2)
                contactDetailsViewModel.publishedAndTrustedDetails[1]
            else
                contactDetailsViewModel.publishedAndTrustedDetails.firstOrNull()
        }
        val newName = remember(newDetails) {
            newDetails?.identityDetails?.formatFirstAndLastName(
                SettingsActivity.contactDisplayNameFormat,
                SettingsActivity.uppercaseLastName
            )
        }
        val oldName = remember(oldDetails) {
            oldDetails?.identityDetails?.formatFirstAndLastName(
                SettingsActivity.contactDisplayNameFormat,
                SettingsActivity.uppercaseLastName
            )
        }
        val newPositionAndCompany = remember(newDetails) {
            newDetails?.identityDetails?.formatPositionAndCompany(
                SettingsActivity.contactDisplayNameFormat
            )
        }
        val oldPositionAndCompany = remember(oldDetails) {
            oldDetails?.identityDetails?.formatPositionAndCompany(
                SettingsActivity.contactDisplayNameFormat
            )
        }
        var photoChanged by remember { mutableStateOf(false) }
        LaunchedEffect(newDetails, oldDetails) {
            photoChanged = if (newDetails == null) {
                false
            } else if (!newDetails.photoUrl.isNullOrEmpty() && !oldDetails?.photoUrl.isNullOrEmpty()) {
                // if the photo url changed, the photo might still be the same, so we compare the bytes
                withContext(Dispatchers.IO) {
                    runCatching {
                        !File(App.absolutePathFromRelative(newDetails.photoUrl)!!).readBytes()
                            .contentEquals(File(App.absolutePathFromRelative(oldDetails.photoUrl)!!).readBytes())
                    }.getOrDefault(newDetails.photoUrl != oldDetails.photoUrl)
                }
            } else if (newDetails.photoUrl.isNullOrEmpty() && !oldDetails?.photoUrl.isNullOrEmpty()) {
                false
            } else {
                newDetails.photoUrl != oldDetails?.photoUrl
            }
        }
        val nameChanged = newDetails != null && newName != oldName
        val positionChanged = newDetails != null && newPositionAndCompany != oldPositionAndCompany
        AnimatedVisibility(
            visible = newDetails != null && (nameChanged || positionChanged || photoChanged),
            enter = EnterTransition.None
        ) {
            ContactUpdate(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = if (contact?.oneToOne == true) 8.dp else 0.dp),
                bytesContactIdentity = contact?.bytesContactIdentity ?: byteArrayOf(),
                name = newName.takeIf { nameChanged },
                positionAndCompany = newPositionAndCompany.takeIf { positionChanged },
                photoUrl = newDetails?.photoUrl.takeIf { photoChanged },
                onAcceptUpdate = {
                    runCatching {
                        AppSingleton.getEngine().trustPublishedContactDetails(
                            contact?.bytesOwnedIdentity,
                            contact?.bytesContactIdentity
                        )
                    }.onFailure {
                        App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT)
                    }
                }
            )
        }

        if (contact?.oneToOne == true) {
            Column(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .background(
                        color = colorResource(R.color.lighterGrey),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clip(RoundedCornerShape(10.dp))
            ) {
                OlvidActionRow(
                    icon = R.drawable.ic_contact_introduction,
                    label = stringResource(
                        R.string.dialog_title_introduce_contact,
                        contactAndInvitation.value?.contact?.firstNameOrCustom.orEmpty()
                    )
                ) {
                    onIntroduce()
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = colorResource(R.color.lightGrey)
                )
                OlvidActionRow(
                    icon = R.drawable.ic_gallery,
                    label = stringResource(R.string.label_gallery)
                ) {
                    contactDetailsViewModel.openGallery(context)
                }
            }
        }

        val groups = contactDetailsViewModel.groupDiscussions?.observeAsState()
        groups?.value?.let { groups ->
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp, bottom = 6.dp),
                text = if (groups.isEmpty()) {
                    stringResource(R.string.label_not_part_of_a_group)
                } else {
                    stringResource(R.string.label_groups_common) + " (${groups.size})"
                },
                style = OlvidTypography.h3.copy(color = colorResource(R.color.almostBlack)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Column(
                modifier = Modifier
                    .background(
                        color = colorResource(R.color.lighterGrey),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
            ) {
                groups.take(5).forEach { group ->
                    ContactListItem(
                        modifier = Modifier.fillMaxWidth(),
                        padding = PaddingValues(4.dp),
                        title = group.discussion.getAnnotatedTitle(
                            context
                        ),
                        body = AnnotatedString(group.groupMemberNames.orEmpty()),
                        onClick = {
                            App.openGroupV2DiscussionActivity(
                                context,
                                group.discussion.bytesOwnedIdentity,
                                group.discussion.bytesDiscussionIdentifier
                            )
                        },
                        initialViewSetup = { initialView ->
                            initialView.setDiscussion(group.discussion)
                        }
                    )
                }
                if (groups.size > 5) {
                    Row(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(
                                    bounded = true,
                                    color = colorResource(R.color.greyOverlay)
                                )
                            ) { onFullGroupsList() }
                            .height(56.dp)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.label_see_all),
                            style = OlvidTypography.body1.copy(color = colorResource(R.color.greyTint))
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            tint = colorResource(R.color.greyTint),
                            contentDescription = null
                        )
                    }
                }
            }
        }

        // trust origins
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .background(
                    color = colorResource(R.color.lighterGrey),
                    shape = RoundedCornerShape(10.dp)
                )
                .clip(RoundedCornerShape(10.dp))
        ) {
            OlvidActionRow(
                icon = R.drawable.ic_shield_outline,
                label = stringResource(
                    R.string.label_trust_origins
                )
            ) {
                onTrustOrigins()
            }
        }
    }
}
