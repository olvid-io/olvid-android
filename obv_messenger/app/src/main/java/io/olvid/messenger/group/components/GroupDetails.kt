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

package io.olvid.messenger.group.components

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.engine.engine.types.JsonGroupDetails
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.customClasses.linkify
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.group.GroupV2DetailsViewModel
import io.olvid.messenger.group.SimpleGroup
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.contacts.ContactListItem
import io.olvid.messenger.settings.SettingsActivity
import java.io.File

@Composable
fun GroupDetailsScreen(
    groupV2DetailsViewModel: GroupV2DetailsViewModel = viewModel(),
    call: (Group2) -> Unit = {},
    imageClick: (String?) -> Unit = {},
    onInviteMembers: () -> Unit = {},
    onFullMembersList: () -> Unit = {},
    onEditMembers: () -> Unit = {},
    onEditAdmins: () -> Unit = {},
    onGroupType: () -> Unit = {}
) {
    val context = LocalContext.current
    val group by groupV2DetailsViewModel.group.observeAsState()
    val members by groupV2DetailsViewModel.groupMembers.observeAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(colorResource(R.color.almostWhite))
            .padding(24.dp)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            InitialView(
                modifier = Modifier
                    .size(112.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = false,
                            color = colorResource(R.color.blueOrWhiteOverlay)
                        ),
                    ) { imageClick(group?.customPhotoUrl ?: group?.photoUrl) },
                initialViewSetup = { initialView ->
                    group?.let {
                        initialView.setGroup2(it)
                    }
                })
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = group?.truncatedCustomName?.takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.text_unnamed_group),
                style = OlvidTypography.h3.copy(
                    color = colorResource(R.color.almostBlack),
                    lineHeight = 32.sp,
                    fontSize = 32.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            groupV2DetailsViewModel.detailsAndPhotos?.serializedGroupDetails?.let {
                val details = AppSingleton.getJsonObjectMapper().readValue(
                    it,
                    JsonGroupDetails::class.java
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details.description?.linkify(context) ?: AnnotatedString(""),
                    style = OlvidTypography.body1.copy(
                        color = colorResource(R.color.greyTint),
                        fontStyle = FontStyle.Italic
                    ),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OlvidActionButton(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.tab_discussions,
                    text = stringResource(R.string.button_label_discuss)
                ) {
                    group?.let {
                        App.openGroupV2DiscussionActivity(
                            context,
                            it.bytesOwnedIdentity,
                            it.bytesGroupIdentifier
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OlvidActionButton(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.tab_calls,
                    text = stringResource(R.string.button_label_call)
                ) {
                    group?.let { call(it) }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        AnimatedVisibility(
            visible = (group?.updateInProgress
                ?: Group2.UPDATE_NONE) != Group2.UPDATE_NONE
                    || groupV2DetailsViewModel.loaderState == LoaderState.SUCCESS
        ) {
            LaunchedEffect(group?.updateInProgress) {
                group?.updateInProgress?.let { updateInProgress ->
                    if (updateInProgress != Group2.UPDATE_NONE) {
                        groupV2DetailsViewModel.loaderState = LoaderState.LOADING
                    }
                }
            }
            SyncCard(
                loaderState = groupV2DetailsViewModel.loaderState,
                updateLoaderState = { groupV2DetailsViewModel.loaderState = it })
            Spacer(modifier = Modifier.height(16.dp))
        }
        group?.personalNote?.let {
            PersonalNoteCard(it)
            Spacer(modifier = Modifier.height(16.dp))
        }
        groupV2DetailsViewModel.detailsAndPhotos?.let { detailsAndPhoto ->
            val newDetails = remember(detailsAndPhoto.serializedPublishedDetails) {
                detailsAndPhoto.serializedPublishedDetails?.let {
                    AppSingleton.getJsonObjectMapper()
                        .readValue(
                            it,
                            JsonGroupDetails::class.java
                        )
                }
            }
            val oldDetails = remember(detailsAndPhoto.serializedGroupDetails) {
                AppSingleton.getJsonObjectMapper()
                    .readValue(
                        detailsAndPhoto.serializedGroupDetails,
                        JsonGroupDetails::class.java
                    )
            }
            var photoChanged by remember { mutableStateOf(false) }
            LaunchedEffect(
                detailsAndPhoto.serializedPublishedDetails,
                detailsAndPhoto.publishedPhotoUrl,
                detailsAndPhoto.photoUrl
            ) {
                photoChanged = if (detailsAndPhoto.serializedPublishedDetails == null) {
                    false
                } else if (detailsAndPhoto.publishedPhotoUrl.isNullOrEmpty()
                        .not() && detailsAndPhoto.photoUrl.isNullOrEmpty().not()
                ) {
                    // if the photo url changed, the photo might still be the same, so we compare the bytes
                    !File(App.absolutePathFromRelative(detailsAndPhoto.publishedPhotoUrl)!!).readBytes()
                        .contentEquals(File(App.absolutePathFromRelative(detailsAndPhoto.photoUrl)!!).readBytes())
                } else if (detailsAndPhoto.publishedPhotoUrl?.isEmpty() == true && detailsAndPhoto.photoUrl.isNullOrEmpty()
                        .not()
                ) {
                    false
                } else {
                    detailsAndPhoto.publishedPhotoUrl != detailsAndPhoto.photoUrl
                }
            }
            AnimatedVisibility(
                visible = newDetails != null && (newDetails != oldDetails || photoChanged),
                enter = EnterTransition.None
            ) {
                GroupUpdate(
                    bytesGroupIdentifier = groupV2DetailsViewModel.bytesGroupIdentifier
                        ?: byteArrayOf(),
                    name = newDetails?.name.orEmpty().takeIf { it != oldDetails.name.orEmpty() },
                    description = newDetails?.description.orEmpty()
                        .takeIf { it != oldDetails.description.orEmpty() },
                    photoUrl = detailsAndPhoto.publishedPhotoUrl.takeIf { photoChanged },
                    onAcceptUpdate = {
                        runCatching {
                            AppSingleton.getEngine().trustGroupV2PublishedDetails(
                                group?.bytesOwnedIdentity,
                                group?.bytesGroupIdentifier
                            )
                        }.onFailure {
                            App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT)
                        }
                    })
            }
        }
        if (group?.ownPermissionAdmin == true) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 6.dp),
                text = stringResource(R.string.label_group_administration),
                style = OlvidTypography.h3.copy(color = colorResource(R.color.almostBlack)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Column(
                modifier = Modifier
                    .background(
                        color = colorResource(R.color.lighterGrey),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clip(RoundedCornerShape(10.dp))
            ) {
                GroupActionRow(
                    icon = R.drawable.ic_members,
                    label = stringResource(R.string.button_label_edit_group_members),
                    onClick = onEditMembers
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    thickness = 1.dp,
                    color = colorResource(R.color.lightGrey)
                )
                GroupActionRow(
                    icon = R.drawable.ic_tool,
                    label = stringResource(R.string.label_group_type),
                    onClick = onGroupType
                )
                if (groupV2DetailsViewModel.groupType != SimpleGroup) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                        thickness = 1.dp,
                        color = colorResource(R.color.lightGrey)
                    )
                    GroupActionRow(
                        icon = R.drawable.ic_star_rounded,
                        label = stringResource(R.string.label_group_choose_admins),
                        onClick = onEditAdmins
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        members?.let {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 6.dp),
                text = stringResource(R.string.label_group_members) + " (${it.size + 1})",
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
                val nonAdminsReadOnly =
                    remember(groupV2DetailsViewModel.groupType) { groupV2DetailsViewModel.groupType.areNonAdminsReadOnly() }
                AppSingleton.getCurrentIdentityLiveData().value?.let {
                    GroupMemberItem(
                        member = GroupMember(
                            bytesIdentity = it.bytesOwnedIdentity,
                            contact = null,
                            jsonIdentityDetails = it.getIdentityDetails(),
                            fullSearchDisplayName = "",
                            isAdmin = group?.ownPermissionAdmin == true,
                            isYou = true,
                            pending = false,
                            selected = false
                        ),
                        keycloakManaged = it.keycloakManaged,
                        nonAdminsReadOnly = nonAdminsReadOnly,
                    )
                }
                it.take(5).forEach { member ->
                    GroupMemberItem(
                        member = member.toGroupMember(),
                        keycloakManaged = member.contact?.keycloakManaged == true,
                        nonAdminsReadOnly = nonAdminsReadOnly
                    )
                }
                if (it.size > 5) {
                    Row(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(
                                    bounded = true,
                                    color = colorResource(R.color.greyOverlay)
                                )
                            ) { onFullMembersList() }
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
            val invitableCount =
                members?.count { (it.contact?.hasChannelOrPreKey() == true || it.contact?.keycloakManaged == true) && it.contact?.active == true && it.contact?.oneToOne != true }
                    ?: 0
            if (invitableCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .background(
                            color = colorResource(R.color.lighterGrey),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = true,
                                color = colorResource(R.color.greyOverlay)
                            ),
                            onClick = onInviteMembers
                        )
                        .padding(vertical = 8.dp, horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = pluralStringResource(
                            R.plurals.explanation_invite_group_members,
                            invitableCount,
                            invitableCount
                        ),
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
}

@Composable
private fun GroupActionRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            tint = colorResource(R.color.almostBlack),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = OlvidTypography.body1.copy(color = colorResource(R.color.almostBlack))
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            tint = colorResource(R.color.almostBlack),
            contentDescription = null
        )
    }
}

@Composable
private fun SyncCard(loaderState: LoaderState, updateLoaderState: (LoaderState) -> Unit = {}) {
    Card(colors = CardDefaults.cardColors(containerColor = colorResource(R.color.almostWhite))) {
        Row(modifier = Modifier.padding(8.dp)) {
            Loader(
                modifier = Modifier.size(32.dp),
                strokeWidth = 4.dp,
                state = loaderState,
                onSuccess = {
                    updateLoaderState(LoaderState.NONE)
                },
                onError = {
                    App.toast(R.string.toast_message_unable_to_update_group, Toast.LENGTH_LONG)
                    updateLoaderState(LoaderState.NONE)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.label_group_update_in_progress_title),
                    style = OlvidTypography.h3.copy(
                        fontSize = 18.sp,
                        color = colorResource(R.color.primary700)
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.explanation_group_update_in_progress),
                    style = OlvidTypography.caption.copy(color = colorResource(R.color.greyTint))
                )
            }
        }
    }
}

@Composable
fun PersonalNoteCard(personalNote: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.lighterGrey))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.hint_personal_note),
                color = colorResource(R.color.almostBlack),
                style = OlvidTypography.h3
            )
            Text(
                text = personalNote.trim().linkify(context),
                style = OlvidTypography.body1.copy(
                    color = colorResource(R.color.greyTint),
                    fontStyle = FontStyle.Italic
                )
            )
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun GroupUpdate(
    modifier: Modifier = Modifier,
    bytesGroupIdentifier: ByteArray,
    name: String? = null,
    description: String? = null,
    photoUrl: String? = null,
    onAcceptUpdate: () -> Unit = {}
) {
    val changeCount = listOf(name != null, description != null, photoUrl != null).count { it }
    BoxWithConstraints(modifier = modifier) {
        val width = if (changeCount == 1)
            maxWidth
        else
            maxWidth * 3 / 4
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.text_group_details_updated),
                    style = OlvidTypography.h3,
                    color = colorResource(id = R.color.almostBlack)
                )
                Text(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .wrapContentWidth(align = Alignment.CenterHorizontally)
                        .background(
                            color = colorResource(id = R.color.red),
                            shape = CircleShape
                        )
                        .padding(horizontal = 4.dp),
                    text = changeCount.toString(),
                    fontSize = 12.sp,
                    color = colorResource(id = R.color.alwaysWhite)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .then(
                        if (changeCount > 1)
                            Modifier.height(IntrinsicSize.Min)
                        else
                            Modifier
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                name?.let {
                    Card(
                        modifier = Modifier
                            .width(width)
                            .then(
                                if (changeCount > 1)
                                    Modifier.fillMaxHeight()
                                else
                                    Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(R.color.lighterGrey),
                            contentColor = colorResource(R.color.almostBlack)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.explanation_new_group_v2_card_name),
                                style = OlvidTypography.body2
                            )
                            Text(
                                text = buildAnnotatedString {
                                    if (name.isEmpty()) {
                                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                            append(stringResource(R.string.text_unnamed_group))
                                        }
                                    } else {
                                        append(name)
                                    }
                                },
                                style = OlvidTypography.body2
                            )
                        }
                    }
                }
                description?.let {
                    Card(
                        modifier = Modifier
                            .width(width)
                            .then(
                                if (changeCount > 1)
                                    Modifier.fillMaxHeight()
                                else
                                    Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(R.color.lighterGrey),
                            contentColor = colorResource(R.color.almostBlack)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = stringResource(R.string.explanation_new_group_v2_card_description))
                            Text(text = description)
                        }
                    }
                }
                photoUrl?.let {
                    Card(
                        modifier = Modifier
                            .width(width)
                            .then(
                                if (changeCount > 1)
                                    Modifier.fillMaxHeight()
                                else
                                    Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(R.color.lighterGrey),
                            contentColor = colorResource(R.color.almostBlack)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialView(
                                modifier = Modifier.size(56.dp),
                                initialViewSetup = { initialView ->
                                    if (photoUrl.isEmpty()) {
                                        initialView.setGroup(bytesGroupIdentifier)
                                    } else {
                                        initialView.setPhotoUrl(
                                            bytesGroupIdentifier,
                                            it
                                        )
                                    }
                                })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.explanation_new_group_v2_card_photo))
                        }
                    }
                }
            }
            OlvidTextButton(
                modifier = Modifier.align(Alignment.End),
                text = stringResource(R.string.button_label_update),
                onClick = onAcceptUpdate
            )
        }
    }
}

@Composable
fun GroupMemberItem(
    modifier: Modifier = Modifier,
    member: GroupMember,
    keycloakManaged: Boolean,
    nonAdminsReadOnly: Boolean,
    onInvite: ((contact: Contact) -> Unit)? = null
) {
    val context = LocalContext.current
    ContactListItem(
        modifier = modifier,
        padding = PaddingValues(4.dp),
        title = AnnotatedString(
            member.getDisplayName(context) + if (member.isYou) " (${context.getString(R.string.text_you)})" else ""
        ),
        body = (ContactCacheSingleton.getContactDetailsSecondLine(member.bytesIdentity)
            ?: member.jsonIdentityDetails?.formatPositionAndCompany(""))?.takeIf { it.isNotEmpty() }
            ?.let { AnnotatedString(it) },
        onClick = {
            if (member.contact != null) {
                if (member.contact.oneToOne) {
                    App.openOneToOneDiscussionActivity(
                        context,
                        member.contact.bytesOwnedIdentity,
                        member.contact.bytesContactIdentity,
                        false
                    )
                } else {
                    App.openContactDetailsActivity(
                        context,
                        member.contact.bytesOwnedIdentity,
                        member.contact.bytesContactIdentity
                    )
                }
            }
        },
        initialViewSetup = { initialView ->
            if (member.isYou) {
                initialView.setFromCache(member.bytesIdentity)
            } else {
                member.contact?.let {
                    initialView.setContact(member.contact)
                } ifNull {
                    member.jsonIdentityDetails?.let {
                        initialView.setInitial(
                            member.bytesIdentity,
                            StringUtils.getInitial(
                                it.formatDisplayName(
                                    SettingsActivity.contactDisplayNameFormat,
                                    SettingsActivity.uppercaseLastName
                                )
                            )
                        )
                    } ifNull {
                        initialView.setUnknown()
                    }
                    initialView.setKeycloakCertified(keycloakCertified = keycloakManaged)
                }
            }
        },
        endContent = {
            onInvite?.let {
                member.contact?.let { contact ->
                    val inviteSent = AppDatabase.getInstance().invitationDao()
                        .getContactOneToOneInvitation(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity
                        ).observeAsState()
                    OlvidTextButton(
                        text = if (inviteSent.value == null) stringResource(R.string.button_label_invite) else stringResource(
                            R.string.button_label_invited
                        ),
                        contentColor = if (inviteSent.value == null) colorResource(R.color.olvid_gradient_light) else colorResource(
                            R.color.greyTint
                        ),
                        enabled = inviteSent.value == null,
                        onClick = {
                            onInvite(member.contact)
                        },
                    )
                }
            } ?: AdminEndLabel(
                admin = member.isAdmin,
                pending = member.pending,
                nonAdminsReadOnly = nonAdminsReadOnly,
            )
        }
    )
}


@Composable
fun AdminEndLabel(
    admin: Boolean,
    pending: Boolean,
    nonAdminsReadOnly: Boolean,
) {
    when {
        admin && pending -> AnnotatedString(stringResource(R.string.label_pending_admin))
        admin -> AnnotatedString(stringResource(R.string.label_admin))
        nonAdminsReadOnly && pending -> AnnotatedString(stringResource(R.string.label_pending_read_only))
        nonAdminsReadOnly -> AnnotatedString(stringResource(R.string.label_read_only))
        pending -> AnnotatedString(stringResource(R.string.label_pending))
        else -> null
    }?.let {
        Text(
            modifier = Modifier
                .width(80.dp)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center,
            text = it,
            color = colorResource(id = R.color.greyTint),
            style = OlvidTypography.subtitle1,
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun GroupUpdatePreview() {
    GroupUpdate(
        modifier = Modifier.padding(8.dp),
        bytesGroupIdentifier = byteArrayOf(),
        name = "Group name",
        description = "Group description",
        photoUrl = null
    )
}

//@Composable
//private fun GroupCard(
//    modifier: Modifier = Modifier,
//    bytesGroupIdentifier: ByteArray,
//    jsonGroupDetails: JsonGroupDetails,
//    photoUrl: String?,
//    newUpdate: Boolean = false,
//    onAcceptUpdate: () -> Unit = {}
//) {
//    val context = LocalContext.current
//    Card(
//        modifier = modifier.fillMaxWidth(),
//        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.lighterGrey_75))
//    ) {
//        Text(
//            modifier = Modifier
//                .background(
//                    color = colorResource(if (newUpdate) R.color.red else R.color.green),
//                    shape = RoundedCornerShape(
//                        topStart = 4.dp,
//                        bottomStart = 0.dp,
//                        bottomEnd = 4.dp,
//                        topEnd = 0.dp
//                    )
//                )
//                .padding(vertical = 2.dp, horizontal = 8.dp),
//            text = stringResource(if (newUpdate) R.string.label_group_card_published_update else R.string.label_group_card).uppercase(),
//            style = OlvidTypography.caption.copy(color = colorResource(R.color.almostWhite))
//        )
//        Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
//            InitialView(initialViewSetup = { initialView ->
//                photoUrl?.let {
//                    initialView.setPhotoUrl(
//                        bytesGroupIdentifier,
//                        it
//                    )
//                } ifNull {
//                    initialView.setGroup(bytesGroupIdentifier)
//                }
//            })
//            Spacer(modifier = Modifier.width(8.dp))
//            Column {
//                Text(text = buildAnnotatedString {
//                    if (jsonGroupDetails.name.isNullOrEmpty()) {
//                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
//                            append(stringResource(R.string.text_unnamed_group))
//                        }
//                    } else {
//                        append(jsonGroupDetails.name)
//                    }
//                })
//                jsonGroupDetails.description?.let { description ->
//                    Text(description.linkify(context = context))
//                }
//            }
//        }
//        if (newUpdate) {
//            Text(
//                modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
//                text = stringResource(R.string.explanation_new_group_v2_card),
//                style = OlvidTypography.caption.copy(color = colorResource(R.color.greyTint))
//            )
//            TextButton(
//                modifier = Modifier.fillMaxWidth(),
//                colors = ButtonDefaults.textButtonColors(contentColor = colorResource(R.color.olvid_gradient_light)),
//                onClick = onAcceptUpdate
//            ) {
//                Text(
//                    text = stringResource(R.string.button_label_update),
//                    textAlign = TextAlign.Center
//                )
//            }
//        }
//    }
//}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun GroupDetailsScreenPreview() {
    GroupDetailsScreen()
}

//@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
//@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
//@Composable
//fun GroupCardPreview() {
//    GroupCard(
//        modifier = Modifier.height(120.dp).padding(16.dp),
//        newUpdate = true,
//        bytesGroupIdentifier = byteArrayOf(),
//        jsonGroupDetails = JsonGroupDetails(),
//        photoUrl = null
//    )
//}


@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SyncCardPreview() {
    SyncCard(loaderState = LoaderState.LOADING)
}
