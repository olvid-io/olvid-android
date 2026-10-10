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

package io.olvid.messenger.main.contacts.suggested

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.ObvDialog
import io.olvid.engine.engine.types.sync.ObvSyncAtom
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.avatar.AvatarTarget
import io.olvid.messenger.designsystem.components.AnimatedEmoji
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidDragHandle
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidOutlinedActionButton
import io.olvid.messenger.designsystem.components.OlvidOutlinedSecondaryButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.launch

const val CARD_WIDTH = 320f
const val CARD_HEIGHT = 460f
const val CARD_ASPECT = CARD_WIDTH / CARD_HEIGHT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedContactsSheet(
    items: List<SuggestedContactItem>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (items.isEmpty()) {
        onDismiss()
        return
    }

    val compact = LocalWindowInfo.current.containerSize.height < 540 * LocalDensity.current.density

    ModalBottomSheet(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = if (compact) 0.dp else 48.dp),
        sheetState = sheetState,
        containerColor = colorResource(R.color.lighterGrey),
        contentColor = colorResource(R.color.almostBlack),
        dragHandle = {
            if (compact)
                Box {}
            else
                OlvidDragHandle()
        },
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets() },
    ) {
        val scope = rememberCoroutineScope()

        SuggestedContactsContent(
            items = items,
            onClose = {
                scope.launch { sheetState.hide() }
                    .invokeOnCompletion {
                        onDismiss()
                    }
            }
        )
    }
}

@Composable
private fun SuggestedContactsContent(
    items: List<SuggestedContactItem>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberSwipeStackState(items.size)
    var showOnboarding by rememberSaveable {
        mutableStateOf(!SettingsActivity.suggestedContactsOnboardingSeen)
    }
    var showList by rememberSaveable { mutableStateOf(false) }

    val currentItem = items.getOrNull(state.currentIndex.intValue)

    val onDismissed: (SwipeDirection, Invitation?) -> Unit = onDismissed@{ direction, invitation ->
        val item = currentItem ?: return@onDismissed
        when (invitation?.categoryId) {
            // sent invitation
            ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY -> {
                // abort invitation if swiped left
                if (direction == SwipeDirection.LEFT) {
                    abortInvitation(invitation)
                }
            }

            // received invitation
            ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY -> {
                respondToInvitation(invitation = invitation, accept = direction == SwipeDirection.RIGHT)
            }

            // no invitation
            else -> {
                when (direction) {
                    SwipeDirection.RIGHT -> inviteSuggestedContact(contact = item.contact)
                    SwipeDirection.LEFT -> ignoreSuggestedContact(contact = item.contact)
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(
            // remaining swipes while on the stack, the full total in the "see full list" view
            count = if (showList) items.size else (items.size - state.currentIndex.intValue).coerceAtLeast(0),
            onClose = onClose,
            showMenu = !showList,
            onBack = { showList = false },
            onShowList = { showList = true },
            onReplayOnboarding = { showOnboarding = true },
        )

        Crossfade(
            targetState = if (showList) 0 else if (state.isFinished) 1 else 2
        ) { elementToShow ->
            when(elementToShow) {
                0 -> SuggestedContactsList(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    items = items,
                    currentItem = state.currentIndex.intValue
                )

                1 -> DoneState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                                .asPaddingValues() + PaddingValues(horizontal = 24.dp)
                        )
                        .weight(1f),
                    onClose = onClose,
                    animateEmoji = SettingsActivity.useAnimatedEmojis(),
                )
                
                else -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val currentCardUiState: MutableState<SuggestedContactUiState?> = remember { mutableStateOf(null) }

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f, true)
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val scale = (maxWidth.value / CARD_WIDTH).coerceAtMost(maxHeight.value / CARD_HEIGHT).coerceAtMost(1f)


                        Box(
                            modifier = Modifier
                                .requiredSize(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                        ) {
                            SwipeableContactCardStack(
                                state = state,
                                items = items,
                                onMoreInfo = { item ->
                                    App.openContactDetailsActivity(
                                        context,
                                        item.contact.bytesOwnedIdentity,
                                        item.contact.bytesContactIdentity,
                                    )
                                },
                                onDismissed = { direction ->
                                    onDismissed(direction, currentCardUiState.value?.receivedInvitation ?: currentCardUiState.value?.sentInvitation)
                                },
                                currentCardUiState = currentCardUiState,
                            )
                        }
                    }
                    ActionButtons(
                        modifier = Modifier.padding(
                            WindowInsets.navigationBars.only(
                                WindowInsetsSides.Bottom
                            ).asPaddingValues() + PaddingValues(
                                bottom = 8.dp,
                                start = 16.dp,
                                end = 16.dp
                            )
                        ),
                        isOneToOne = currentCardUiState.value?.oneToOne == true,
                        isReceivedInvitation = currentCardUiState.value?.receivedInvitation != null,
                        isSentInvitation = currentCardUiState.value?.sentInvitation != null,
                        onLeft = { state.swipe(SwipeDirection.LEFT, { direction ->
                            onDismissed(direction, currentCardUiState.value?.receivedInvitation ?: currentCardUiState.value?.sentInvitation)
                        }) },
                        onRight = { state.swipe(SwipeDirection.RIGHT, { direction ->
                            onDismissed(direction, currentCardUiState.value?.receivedInvitation ?: currentCardUiState.value?.sentInvitation)
                        }) },
                    )
                }
            }
        }
    }

    if (showOnboarding) {
        SuggestedContactsOnboarding(
            items = items,
            onDismiss = {
                SettingsActivity.suggestedContactsOnboardingSeen = true
                showOnboarding = false
            },
        )
    }
}

@Composable
private fun Header(
    count: Int,
    onClose: () -> Unit,
    onBack: () -> Unit,
    showMenu: Boolean,
    onShowList: () -> Unit,
    onReplayOnboarding: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = if (showMenu) onClose else onBack) {
            Icon(
                painter = painterResource(if (showMenu) R.drawable.ic_close else R.drawable.ic_arrow_back),
                contentDescription = stringResource(if (showMenu)  R.string.content_description_close_button else R.string.content_description_back_button),
                tint = colorResource(R.color.almostBlack),
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.label_suggested_contacts) + if (count > 0) " ($count)" else "",
            textAlign = TextAlign.Center,
            style = OlvidTypography.h2,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.almostBlack),
        )
        if (showMenu) {
            var expanded by remember { mutableStateOf(false) }
            IconButton(onClick = { expanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_three_dots_grey),
                    contentDescription = null,
                    tint = colorResource(R.color.almostBlack),
                )
                OlvidDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    OlvidDropdownMenuItem(
                        text = stringResource(R.string.menu_action_suggested_contacts_see_full_list),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_pref_list),
                                contentDescription = null,
                                tint = colorResource(R.color.almostBlack),
                            )
                        },
                        onClick = {
                            expanded = false
                            onShowList()
                        },
                    )
                    OlvidDropdownMenuItem(
                        text = stringResource(R.string.menu_action_suggested_contacts_replay_onboarding),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_info),
                                contentDescription = null,
                                tint = colorResource(R.color.almostBlack),
                            )
                        },
                        onClick = {
                            expanded = false
                            onReplayOnboarding()
                        },
                    )
                }
            }
        } else {
            // keep the title centered when the overflow menu is hidden (list view)
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun SuggestedContactsList(
    modifier: Modifier = Modifier,
    items: List<SuggestedContactItem>,
    currentItem: Int,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding =
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
                    + PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { _, it -> it.key },
        ) { index, item ->
            val cardState = rememberSuggestedContactUiState(item)
            if (currentItem != 0 && index == 0) {
                Text(
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp, top = 12.dp, end = 12.dp),
                    text = stringResource(R.string.label_suggested_contacts_already_swiped),
                    style = OlvidTypography.body1.copy(
                        color = colorResource(R.color.greyTint),
                    )
                )
            } else if (index == currentItem) {
                Text(
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp, top = 12.dp, end = 12.dp),
                    text = stringResource(R.string.label_suggested_contacts_not_swiped_yet),
                    style = OlvidTypography.body1.copy(
                        color = colorResource(R.color.greyTint),
                    )
                )
            }
            ContactRow(cardState, item.contact)
        }
    }
}

@Composable
private fun ContactRow(cardState: SuggestedContactUiState, contact: Contact) {
    val context = LocalContext.current

    SuggestedRowLayout(
        avatar = cardState.avatar,
        title = cardState.name,
        subtitle = cardState.description,
        onClick = {
            App.openContactDetailsActivity(
                context,
                cardState.bytesOwnedIdentity,
                cardState.bytesContactIdentity,
            )
        }
    ) {
        if (cardState.oneToOne)
            Icon(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(24.dp),
                painter = painterResource(R.drawable.ic_ok_outline),
                tint = colorResource(R.color.green),
                contentDescription = null,
            )
        else if (cardState.sentInvitation != null)
            Text(
                modifier = Modifier.padding(horizontal = 12.dp),
                text = stringResource(R.string.button_label_invited),
                style = OlvidTypography.body2.copy(
                    color = colorResource(R.color.greyTint)
                ),
                maxLines = 2
            )
        else if (cardState.receivedInvitation != null)
            OlvidTextButton(
                text = stringResource(R.string.button_label_accept_invitation),
                onClick = {
                    respondToInvitation(invitation = cardState.receivedInvitation, accept = true)
                },
                allowTwoLines = true
            )
        else
            OlvidTextButton(
                text = stringResource(R.string.button_label_invite),
                contentColor = colorResource(R.color.olvid_gradient_light),
                onClick = { inviteSuggestedContact(contact) },
            )
    }
}

@Composable
private fun SuggestedRowLayout(
    avatar: AvatarTarget?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                onClick?.let {
                    Modifier.clickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick
                    )
                } ?: Modifier
            )
            .padding(horizontal = 16.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            modifier = Modifier.size(40.dp),
            target = avatar,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = OlvidTypography.body1,
                color = colorResource(R.color.almostBlack),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}

private fun inviteSuggestedContact(contact: Contact) {
    App.runThread {
        runCatching {
            if (contact.hasChannelOrPreKey()) {
                AppSingleton.getEngine().startOneToOneInvitationProtocol(
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity,
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

private fun ignoreSuggestedContact(contact: Contact) {
    App.runThread {
        AppSingleton.getEngine().propagateAppSyncAtomToOtherDevicesIfNeeded(contact.bytesOwnedIdentity,
            ObvSyncAtom.createStopSuggestingContact(contact.bytesContactIdentity))
        AppDatabase.getInstance().contactDao().updateStopSuggesting(contact.bytesOwnedIdentity, contact.bytesContactIdentity, true)
    }
}

@Composable
private fun ActionButtons(
    modifier: Modifier = Modifier,
    isOneToOne: Boolean,
    isReceivedInvitation: Boolean,
    isSentInvitation: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isOneToOne) {
            OlvidOutlinedActionButton(
                modifier = Modifier.weight(1f),
                outlinedColor = colorResource(R.color.green),
                contentColor = colorResource(R.color.green),
                text = stringResource(R.string.button_label_ok),
                large = true,
                onClick = onRight,
            )
        } else if (isReceivedInvitation) {
            OlvidOutlinedActionButton(
                modifier = Modifier.weight(1f),
                outlinedColor = colorResource(R.color.red),
                contentColor = colorResource(R.color.red),
                text = stringResource(R.string.button_label_reject),
                large = true,
                onClick = onLeft,
            )
            OlvidActionButton(
                modifier = Modifier.weight(1f),
                containerColor = colorResource(R.color.green),
                text = stringResource(R.string.button_label_accept),
                large = true,
                onClick = onRight,
            )
        } else if (isSentInvitation) {
            OlvidOutlinedActionButton(
                modifier = Modifier.weight(1f),
                outlinedColor = colorResource(R.color.red),
                contentColor = colorResource(R.color.red),
                icon = R.drawable.ic_remove_member,
                text = stringResource(R.string.button_label_uninvite),
                large = true,
                onClick = onLeft,
            )
            OlvidOutlinedActionButton(
                modifier = Modifier.weight(1f),
                outlinedColor = colorResource(R.color.green),
                contentColor = colorResource(R.color.green),
                text = stringResource(R.string.button_label_ok),
                large = true,
                onClick = onRight,
            )
        } else {
            OlvidOutlinedSecondaryButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.button_label_suggested_contact_later),
                large = true,
                onClick = onLeft,
            )
            OlvidActionButton(
                modifier = Modifier.weight(1f),
                containerColor = colorResource(R.color.green),
                icon = R.drawable.ic_add_member,
                text = stringResource(R.string.button_label_invite),
                large = true,
                onClick = onRight,
            )
        }
    }
}

@Composable
private fun DoneState(
    modifier: Modifier = Modifier,
    animateEmoji: Boolean = true,
    onClose: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (animateEmoji) {
            AnimatedEmoji(
                size = 96f,
                shortEmoji = "\uD83C\uDF89",
                autoPlay = true,
                loop = false
            )
        } else {
            Text(
                text = "\uD83C\uDF89",
                fontSize = 96.sp,
                color = Color.Black // any non-transparent color is good
            )
        }
        Spacer(modifier = Modifier
            .height(24.dp)
            .weight(1f, false))
        Text(
            text = stringResource(R.string.label_suggested_contacts_all_done),
            textAlign = TextAlign.Center,
            style = OlvidTypography.h2,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.almostBlack),
        )
        Spacer(modifier = Modifier
            .height(24.dp)
            .weight(1f, false))
        OlvidOutlinedActionButton(
            text = stringResource(R.string.button_label_close),
            onClick = onClose,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Preview
@Composable
private fun DoneStatePreview() {
    Box(
        modifier = Modifier
            .background(colorResource(R.color.alwaysWhite))
            .padding(all = 24.dp)
    ) {
        DoneState(animateEmoji = false) { }
    }
}