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

package io.olvid.messenger.share

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.defaultViewModelCreationExtras
import androidx.lifecycle.defaultViewModelProviderFactory
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.engine.Logger
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils2
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.discussion.compose.EphemeralSettingsGroup
import io.olvid.messenger.discussion.compose.EphemeralViewModel
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.group.components.GroupMembersViewModel
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.share.components.FromToCard
import io.olvid.messenger.share.components.OwnedIdentityPickerSheet
import io.olvid.messenger.share.components.ShareComposeBar
import io.olvid.messenger.share.components.ShareContentPreview
import io.olvid.messenger.share.components.ShareDestinationPickerSheet
import io.olvid.messenger.share.components.openHiddenProfileDialog
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheetScreen(
    viewModel: ShareViewModel,
    linkPreviewViewModel: LinkPreviewViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val ownedIdentity by AppSingleton.getCurrentIdentityLiveData().observeAsState()
    val openGraph by linkPreviewViewModel.openGraph.observeAsState()
    val scope = rememberCoroutineScope()

    var showRecipientsSheet by rememberSaveable { mutableStateOf(false) }
    var showIdentitySheet by rememberSaveable { mutableStateOf(false) }
    var openEphemeralSettings by rememberSaveable { mutableStateOf(false) }
    var recipientsNudge by remember { mutableIntStateOf(0) }
    val ephemeralViewModel : EphemeralViewModel = viewModel()
    val hasEphemeralSettings by ephemeralViewModel.getSettingsModified().observeAsState()

    val recipientsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val identitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun dismissRecipientsSheet() {
        scope.launch { recipientsSheetState.hide() }
            .invokeOnCompletion { if (!recipientsSheetState.isVisible) showRecipientsSheet = false }
    }

    fun dismissIdentitySheet() {
        scope.launch { identitySheetState.hide() }
            .invokeOnCompletion { if (!identitySheetState.isVisible) showIdentitySheet = false }
    }

    val activity = LocalActivity.current as? FragmentActivity

    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    // Select an owned identity as the share sender. Shared by the picker sheet, the hidden-profile
    // unlock dialog, and the From-row long-press so every entry point behaves identically: switch
    // identity, drop the recipient chips (the new profile has its own contacts/groups), and close
    // the identity sheet if it happens to be open.
    val chooseIdentity: (ByteArray) -> Unit = { bytesOwnedIdentity ->
        AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, null)
        viewModel.replaceSelectedDiscussions(emptyList())

        // also clear the groupMemberViewModel to clear any previously selected discussion
        viewModelStoreOwner?.let {
            val membersViewModelKey = Logger.toHexString(bytesOwnedIdentity)
            val provider = ViewModelProvider.create(
                viewModelStoreOwner,
                viewModelStoreOwner.defaultViewModelProviderFactory,
                viewModelStoreOwner.defaultViewModelCreationExtras
            )
            val groupMembersViewModel = provider[membersViewModelKey, GroupMembersViewModel::class]
            groupMembersViewModel.clearSelectedMembers()
        }

        dismissIdentitySheet()
    }

    // Resolve / clear the link preview whenever draftText changes. dismissedLinkPreviewUrl is
    // intentionally NOT a key: the in-effect call to clearLinkPreviewDismissal() would otherwise
    // re-fire the effect, cancelling + restarting the 300ms findLinkPreview debounce.
    LaunchedEffect(viewModel.draftText) {
        if (!SettingsActivity.isLinkPreviewOutbound) {
            linkPreviewViewModel.reset()
            return@LaunchedEffect
        }
        val url = StringUtils2.getLink(viewModel.draftText)?.second
        val dismissed = viewModel.dismissedLinkPreviewUrl
        when (url) {
            null -> {
                linkPreviewViewModel.reset()
                if (dismissed != null) viewModel.clearLinkPreviewDismissal()
            }
            dismissed -> linkPreviewViewModel.reset()
            else -> {
                if (dismissed != null) viewModel.clearLinkPreviewDismissal()
                linkPreviewViewModel.findLinkPreview(viewModel.draftText, 256, 256)
            }
        }
    }

    // remember the switchMap chain: without it every recomposition allocates a fresh LiveData +
    // observer (which momentarily emits null, flashing the list empty) and leaks the old observer.
    val identitiesLiveData = remember {
        AppSingleton.getCurrentIdentityLiveData().switchMap { current: OwnedIdentity? ->
            AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenExceptOne(
                current?.bytesOwnedIdentity ?: byteArrayOf()
            )
        }
    }
    val otherIdentities by identitiesLiveData.observeAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.almostWhite))
            .safeDrawingPadding(),
    ) {
        TopBar(
            ownedIdentity = ownedIdentity,
            selectedDiscussions = viewModel.selectedDiscussions,
            onClose = onClose,
            onPickIdentity = { showIdentitySheet = true },
            onPickIdentityLongPress = {
                activity?.let { openHiddenProfileDialog(it, chooseIdentity) }
            },
            onPickRecipients = { showRecipientsSheet = true },
            hasOtherIdentities = !otherIdentities.isNullOrEmpty(),
            recipientsNudge = recipientsNudge,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            contentAlignment = Alignment.Center,
        ) {
            ShareContentPreview(
                attachments = viewModel.attachments,
                openGraph = openGraph,
                onRemoveAttachment = { viewModel.removeAttachment(it.uri) },
                onDismissOpenGraph = {
                    val url = openGraph?.url ?: StringUtils2.getLink(viewModel.draftText)?.second
                    viewModel.markLinkPreviewDismissed(url)
                    linkPreviewViewModel.reset()
                },
            )
        }


        ShareComposeBar(
            text = viewModel.draftText,
            onTextChange = { viewModel.draftText = it },
            canSend = viewModel.canSend(),
            hasEphemeralSettings = hasEphemeralSettings == true,
            onPickEphemeralSettings = { openEphemeralSettings = true },
            onSend = {
                // Match the in-app compose flow (ComposeMessageController.kt:366): wait up to
                // 2s for any in-flight OpenGraph resolution so a fast Send still attaches the
                // preview the user saw.
                linkPreviewViewModel.waitForPreview {
                    val jsonExpiration = if (ephemeralViewModel.getValid().value == true) {
                        JsonExpiration().apply {
                            if (ephemeralViewModel.getReadOnce()) {
                                setReadOnce(true)
                            }
                            setVisibilityDuration(ephemeralViewModel.getVisibility())
                            setExistenceDuration(ephemeralViewModel.getExistence())
                        }
                    } else {
                        null
                    }

                    viewModel.send(context, linkPreviewViewModel.openGraph.value, jsonExpiration) {
                        // after sharing to a single discussion, navigate to it, through MainActivity
                        // so it opens in the app's task: left in this share task, the next share
                        // would just bring this task back instead of starting a new share sheet
                        if (viewModel.selectedDiscussions.size == 1) {
                            viewModel.selectedDiscussions.firstOrNull()?.let {
                                context.startActivity(
                                    Intent(context, MainActivity::class.java)
                                        .setAction(MainActivity.FORWARD_ACTION)
                                        .putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity::class.java.name)
                                        .putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, it.discussionId)
                                )
                            }
                        }
                        onClose()
                    }
                }
            },
            onBlockedSend = {
                if (viewModel.selectedDiscussions.isEmpty()) {
                    recipientsNudge++
                }
            },
        )
    }

    if (showRecipientsSheet) {
        ShareDestinationPickerSheet(
            sheetState = recipientsSheetState,
            initiallySelected = viewModel.selectedDiscussions,
            onConfirm = { picked ->
                viewModel.replaceSelectedDiscussions(picked)
                dismissRecipientsSheet()
            },
        )
    }

    if (showIdentitySheet) {
        OwnedIdentityPickerSheet(
            sheetState = identitySheetState,
            onDismiss = ::dismissIdentitySheet,
            onIdentityChosen = chooseIdentity,
            identities = otherIdentities,
        )
    }

    if (openEphemeralSettings) {
        EphemeralSettingsGroup(
            modifier = Modifier.safeDrawingPadding(),
            ephemeralViewModel = ephemeralViewModel,
            expanded = true,
        ) {
            openEphemeralSettings = false
        }
    }
}

@Composable
private fun TopBar(
    ownedIdentity: OwnedIdentity?,
    selectedDiscussions: List<SearchableDiscussion>,
    onClose: () -> Unit,
    onPickIdentity: () -> Unit,
    onPickIdentityLongPress: () -> Unit,
    onPickRecipients: () -> Unit,
    hasOtherIdentities: Boolean,
    recipientsNudge: Int,
) {
    val compact = LocalWindowInfo.current.containerSize.height < 540 * LocalDensity.current.density

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = if (compact) 0.dp else 12.dp, bottom = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .padding(8.dp)
                .size(24.dp),
            painter = painterResource(R.drawable.ic_close),
            tint = colorResource(R.color.almostBlack),
            contentDescription = stringResource(R.string.content_description_close_button),
        )
        FromToCard(
            modifier = Modifier.padding(start = 16.dp).weight(1f, false).widthIn(max = 500.dp),
            ownedIdentity = ownedIdentity,
            selectedDiscussions = selectedDiscussions,
            onPickIdentity = onPickIdentity,
            onPickIdentityLongClick = onPickIdentityLongPress,
            onPickRecipients = onPickRecipients,
            hasOtherIdentities = hasOtherIdentities,
            recipientsNudge = recipientsNudge,
        )
    }
}
