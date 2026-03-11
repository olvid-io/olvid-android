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

package io.olvid.messenger.discussion

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.appcompat.app.AlertDialog.Builder
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.map
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason.FORCEFULLY_UNBLOCKED
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason.REVOKED
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.activities.ShortcutActivity
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder.DeletionChoice
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder.Type.DISCUSSION
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.tasks.DeleteMessagesTask
import io.olvid.messenger.databases.tasks.propagateMuteSettings
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.SearchBar
import io.olvid.messenger.designsystem.components.SelectionTopAppBar
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.DiscussionActivity.ScrollRequest
import io.olvid.messenger.discussion.search.DiscussionSearchViewModel
import io.olvid.messenger.discussion.settings.DiscussionSettingsActivity
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.webrtc.WebrtcCallService
import kotlinx.coroutines.delay

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DiscussionTopAppBar(
    discussion: Discussion?,
    discussionViewModel: DiscussionViewModel,
    discussionSearchViewModel: DiscussionSearchViewModel,
    invitationViewModel: InvitationListViewModel,
    lazyListState: LazyListState,
    supportFragmentManager: FragmentManager,
    toolbarClickedCallback: Runnable?,
    finishAndClearViewModel: () -> Unit,
    onBackPressed: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val discussionCustomization by discussionViewModel.discussionCustomization.observeAsState()
    val contactDiscussionSubtitle by discussionViewModel.discussionContacts.map { contacts ->
        if (contacts == null || contacts.size != 1)
            null
        else
            contacts[0].let {
                if (it.customDisplayName != null) {
                    it.displayName
                } else {
                    it.getIdentityDetails()?.formatPositionAndCompany(SettingsActivity.contactDisplayNameFormat)
                }
            }
    }.observeAsState()
    val discussionGroupMemberCount by discussionViewModel.discussionGroupMemberCountLiveData.observeAsState()

    val invitations by discussionViewModel.invitations.observeAsState()

    val currentTitle = when {
        discussion?.isLocked == true && discussion.title.isNullOrEmpty() -> {
            val spannableString =
                SpannableString(stringResource(R.string.text_unnamed_discussion))
            spannableString.setSpan(
                StyleSpan(Typeface.ITALIC),
                0,
                spannableString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.toString()
        }

        discussion?.discussionType == Discussion.TYPE_GROUP_V2 && discussion.title.isNullOrEmpty() -> {
            val spannableString =
                SpannableString(stringResource(R.string.text_unnamed_group))
            spannableString.setSpan(
                StyleSpan(Typeface.ITALIC),
                0,
                spannableString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.toString()
        }

        else -> discussion?.title
    }

    val currentSubtitle = when (discussion?.discussionType) {
        Discussion.TYPE_CONTACT -> {
            contactDiscussionSubtitle
        }
        Discussion.TYPE_GROUP_V2 -> {
            if (discussionGroupMemberCount?.count != -1) {
                if (discussionGroupMemberCount?.count != 0) {
                    pluralStringResource(
                        R.plurals.other_members_count,
                        discussionGroupMemberCount?.count?.plus(1) ?: 0,
                        discussionGroupMemberCount?.count?.plus(1) ?: 0
                    )
                } else {
                    val text = SpannableString(stringResource(R.string.text_empty_group))
                    val styleSpan = StyleSpan(Typeface.ITALIC)
                    text.setSpan(styleSpan, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.toString()
                }
            } else {
                null
            }
        }
        else -> null
    }

    val topBarRippleInteractionSource = remember { MutableInteractionSource() }

    SelectionTopAppBar(
        modifier = Modifier.indication(
            interactionSource = topBarRippleInteractionSource,
            indication = ripple(
                bounded = true,
                color = colorResource(R.color.almostBlack)
            )
        ),
        content = {
            if (discussionSearchViewModel.searchExpanded) {
                LaunchedEffect(discussionSearchViewModel.searchText) {
                    delay(300)
                    discussion?.id?.let { discussionId ->
                        discussionSearchViewModel.filter(
                            discussionId = discussionId,
                            filterString = discussionSearchViewModel.searchText,
                            firstVisibleMessageMessageId = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? Long
                        )
                    }
                }
                SearchBar(
                    modifier = Modifier.height(if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 40.dp else 48.dp).fillMaxWidth(),
                    searchText = discussionSearchViewModel.searchText,
                    onSearchTextChanged = { newText ->
                        discussionSearchViewModel.searchText = newText
                    },
                    placeholderText = stringResource(R.string.hint_search_message),
                    leadingIcon = discussionSearchViewModel.filterJob?.let {
                        {
                            OlvidCircularProgress(size = 24.dp)
                        }
                    },
                    requestFocus = discussionSearchViewModel.focusSearchOnOpen,
                    onClearClick = {
                        discussionSearchViewModel.reset()
                    }
                )
            } else {
                with(sharedTransitionScope) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var initializedInitialView by remember { mutableStateOf<InitialView?>(null) }
                        Box(
                            modifier = Modifier.size(40.dp)
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = discussionViewModel.fullScreenPhotoUrl == null,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                InitialView(
                                    modifier = Modifier
                                        .requiredSize(40.dp)
                                        .sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "profile-photo"),
                                            animatedVisibilityScope = this@AnimatedVisibility
                                        ),
                                    initialViewSetup = { initialView ->
                                        if (discussion?.isPreDiscussion == true && invitations.isNullOrEmpty()
                                                .not()
                                        ) {
                                            invitations?.firstOrNull()?.let {
                                                invitationViewModel.initialViewSetup(
                                                    initialView,
                                                    it
                                                )
                                            }
                                        } else {
                                            discussion?.let {
                                                initialView.setDiscussion(it)
                                            } ?: initialView.setUnknown()
                                        }
                                        initializedInitialView = initialView
                                    },
                                    onClick = {
                                        discussionViewModel.fullScreenPhotoUrl =
                                            initializedInitialView?.photoUrl
                                    })
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(weight = 1f, fill = true)
                                .clickable(
                                    interactionSource = topBarRippleInteractionSource,
                                    indication = null
                                ) {
                                    toolbarClickedCallback?.run()
                                }
                        ) {
                            currentTitle?.let { title ->
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = OlvidTypography.h2.copy(fontWeight = FontWeight.Medium),
                                    color = colorResource(R.color.almostBlack)
                                )
                            }
                            currentSubtitle?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = OlvidTypography.body1,
                                    color = colorResource(R.color.almostBlack)
                                )
                            }
                        }
                    }
                }
            }
        },
        title = currentTitle.orEmpty(),
        selection = discussionViewModel.selectedMessageIds,
        selectedStringResource = R.plurals.action_mode_title_discussion,
        selectionActions = buildList {
            add(R.drawable.ic_delete to {
                discussionViewModel.handleDeleteMessages(context)
            })
            if (discussionViewModel.areAllSelectedMessagesBookmarkable()) {
                if (discussionViewModel.areAllSelectedMessagesBookmarked()) {
                    add(R.drawable.ic_star_off to {
                        discussionViewModel.handleBookmarkMessages(false)
                    })
                } else {
                    add(R.drawable.ic_star to {
                        discussionViewModel.handleBookmarkMessages(true)
                    })
                }
            }
            if (discussionViewModel.areAllSelectedMessagesForwardable()) {
                add(R.drawable.ic_forward to {
                    discussionViewModel.handleForwardMessages(context)
                })
            }
        },
        disabledItems =
            buildList {
                if (discussionSearchViewModel.hasPrevious.not()) {
                    add(R.drawable.ic_search_next)
                }
                if (discussionSearchViewModel.hasNext.not()) {
                    add(R.drawable.ic_search_prev)
                }
            },
        actions = buildList {
            if (discussionSearchViewModel.searchExpanded) {
                add(R.drawable.ic_search_prev to {
                    discussionSearchViewModel.next()?.let { previous ->
                        discussionViewModel.scrollToMessageRequest =
                            ScrollRequest(
                                messageId = previous,
                                highlight = true,
                                triggeredBySearch = true
                            )
                    }
                })
                add(R.drawable.ic_search_next to {
                    discussionSearchViewModel.previous()?.let { previous ->
                        discussionViewModel.scrollToMessageRequest =
                            ScrollRequest(
                                previous,
                                highlight = true,
                                triggeredBySearch = true
                            )
                    }
                })
            } else {
                discussion?.takeIf { discussionSearchViewModel.searchExpanded.not() }
                    ?.apply {
                        add(R.drawable.ic_search to {
                            discussionSearchViewModel.searchExpanded = true
                            keyboardController?.show()
                        })
                        if (isNormalOrReadOnly) {
                            add(R.drawable.ic_phone to {

                                when (discussionType) {
                                    Discussion.TYPE_CONTACT -> {
                                        val contacts =
                                            discussionViewModel.discussionContacts.value
                                        if (!contacts.isNullOrEmpty() && contacts[0].hasChannelOrPreKey()) {
                                            App.startWebrtcCall(
                                                context,
                                                bytesOwnedIdentity,
                                                bytesDiscussionIdentifier
                                            )
                                        }
                                    }

                                    Discussion.TYPE_GROUP, Discussion.TYPE_GROUP_V2 -> {
                                        val contacts =
                                            discussionViewModel.discussionContacts.value
                                        if (contacts != null) {
                                            val bytesContactIdentities: ArrayList<BytesKey>?
                                            if (contacts.size > WebrtcCallService.MAX_GROUP_SIZE_TO_SELECT_ALL_BY_DEFAULT) {
                                                bytesContactIdentities = null
                                            } else {
                                                bytesContactIdentities =
                                                    ArrayList(contacts.size)
                                                for (contact in contacts) {
                                                    bytesContactIdentities.add(
                                                        BytesKey(contact.bytesContactIdentity)
                                                    )
                                                }
                                            }
                                            val multiCallStartDialogFragment =
                                                MultiCallStartDialogFragment.newInstance(
                                                    bytesOwnedIdentity,
                                                    bytesDiscussionIdentifier,
                                                    bytesContactIdentities
                                                )
                                            multiCallStartDialogFragment.show(
                                                supportFragmentManager,
                                                "dialog"
                                            )
                                        }
                                    }
                                }
                            })
                            if (discussionCustomization?.shouldMuteNotifications() == true) {
                                add(R.drawable.ic_notification_muted to {
                                    discussionCustomization?.discussionId?.let { discussionId ->
                                        val builder = SecureAlertDialogBuilder(
                                            context,
                                            R.style.CustomAlertDialog
                                        )
                                            .setTitle(R.string.dialog_title_unmute_notifications)
                                            .setPositiveButton(R.string.button_label_unmute_notifications) { _: DialogInterface?, _: Int ->
                                                App.runThread {
                                                    val reDiscussionCustomization =
                                                        AppDatabase.getInstance()
                                                            .discussionCustomizationDao()[discussionId]
                                                    reDiscussionCustomization?.let {
                                                        reDiscussionCustomization.prefMuteNotifications =
                                                            false
                                                        AppDatabase.getInstance()
                                                            .discussionCustomizationDao()
                                                            .update(
                                                                reDiscussionCustomization
                                                            )
                                                        discussionViewModel.discussion.value?.let {
                                                            it.propagateMuteSettings(
                                                                reDiscussionCustomization
                                                            )
                                                            AppSingleton.getEngine()
                                                                .profileBackupNeeded(
                                                                    it.bytesOwnedIdentity
                                                                )
                                                        }
                                                    }
                                                }
                                            }
                                            .setNegativeButton(
                                                R.string.button_label_cancel,
                                                null
                                            )

                                        discussionCustomization?.prefMuteNotificationsTimestamp?.let {
                                            builder.setMessage(
                                                context.getString(
                                                    R.string.dialog_message_unmute_notifications_muted_until,
                                                    StringUtils.getLongNiceDateString(
                                                        context,
                                                        it
                                                    )
                                                )
                                            )
                                        } ?: {
                                            builder.setMessage(R.string.dialog_message_unmute_notifications)
                                        }
                                        builder.create().show()
                                    }
                                })
                            }
                        }
                    }
            }
        },
        otherActions = buildList {
            discussion?.takeIf { discussionSearchViewModel.searchExpanded.not() }
                ?.apply {
                    if (isNormalOrReadOnly) {
                        add(R.string.menu_action_discussion_settings to {
                            discussionViewModel.markAsReadOnPause = false
                            discussion.apply {
                                val intent = Intent(
                                    context,
                                    DiscussionSettingsActivity::class.java
                                )
                                intent.putExtra(
                                    DiscussionSettingsActivity.DISCUSSION_ID_INTENT_EXTRA,
                                    id
                                )
                                context.startActivity(intent)
                            }
                        })
                        when (discussionType) {
                            Discussion.TYPE_CONTACT -> add(R.string.menu_action_contact_details to { toolbarClickedCallback?.run() })
                            Discussion.TYPE_GROUP, Discussion.TYPE_GROUP_V2 ->
                                add(R.string.menu_action_group_details to { toolbarClickedCallback?.run() })
                        }
                    }
                    add(R.string.label_gallery to {
                        discussionViewModel.markAsReadOnPause = false
                        discussion.apply {
                            App.openDiscussionMediaGalleryActivity(
                                context,
                                id
                            )
                        }
                    })
                    if (!discussion.active) {
                        add(R.string.menu_action_unblock_discussion to {
                            discussion.takeIf { it.isNormalOrReadOnly }
                                ?.apply {
                                    if (discussionType == Discussion.TYPE_CONTACT) {
                                        val notActiveReasons =
                                            AppSingleton.getEngine()
                                                .getContactActiveOrInactiveReasons(
                                                    bytesOwnedIdentity,
                                                    bytesDiscussionIdentifier
                                                )
                                        if (notActiveReasons != null) {
                                            if (notActiveReasons.contains(
                                                    REVOKED
                                                )
                                                && !notActiveReasons.contains(
                                                    FORCEFULLY_UNBLOCKED
                                                )
                                            ) {
                                                val builder: Builder =
                                                    SecureAlertDialogBuilder(
                                                        context,
                                                        R.style.CustomAlertDialog
                                                    )
                                                builder.setTitle(R.string.dialog_title_unblock_revoked_contact_discussion)
                                                    .setMessage(R.string.dialog_message_unblock_revoked_contact_discussion)
                                                    .setNegativeButton(
                                                        R.string.button_label_cancel,
                                                        null
                                                    )
                                                    .setPositiveButton(R.string.button_label_unblock) { _: DialogInterface?, _: Int ->
                                                        if (!AppSingleton.getEngine()
                                                                .forcefullyUnblockContact(
                                                                    bytesOwnedIdentity,
                                                                    bytesDiscussionIdentifier
                                                                )
                                                        ) {
                                                            App.toast(
                                                                R.string.toast_message_failed_to_unblock_contact,
                                                                Toast.LENGTH_SHORT
                                                            )
                                                        }
                                                    }
                                                builder.create().show()
                                                return@apply
                                            }
                                        }

                                        if (!AppSingleton.getEngine()
                                                .forcefullyUnblockContact(
                                                    bytesOwnedIdentity,
                                                    bytesDiscussionIdentifier
                                                )
                                        ) {
                                            App.toast(
                                                R.string.toast_message_failed_to_unblock_contact,
                                                Toast.LENGTH_SHORT
                                            )
                                        }
                                    }
                                }
                        })
                    }
                    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                        add(R.string.menu_action_create_shortcut to {
                            if (ShortcutManagerCompat.isRequestPinShortcutSupported(
                                    context
                                )
                            ) {
                                val discussion =
                                    discussionViewModel.discussion.value
                                if (discussion != null) {
                                    App.runThread {
                                        val title =
                                            discussion.title.takeUnless { it.isNullOrEmpty() }
                                                ?: context.getString(R.string.text_unnamed_discussion)
                                        val builder =
                                            ShortcutActivity.getShortcutInfo(
                                                discussion.id,
                                                title
                                            )
                                        if (builder != null) {
                                            try {
                                                val shortcutInfo =
                                                    builder.build()
                                                ShortcutManagerCompat.requestPinShortcut(
                                                    context,
                                                    shortcutInfo,
                                                    null
                                                )
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                }
                            } else {
                                App.toast(
                                    R.string.toast_message_shortcut_not_supported,
                                    Toast.LENGTH_SHORT
                                )
                            }
                        })
                    }
                    add(R.string.menu_action_delete_discussion to {
                        App.runThread {
                            val canRemoteDelete: Boolean
                            discussion.apply {
                                if (discussionType == Discussion.TYPE_GROUP_V2) {
                                    val group2 = AppDatabase.getInstance()
                                        .group2Dao()[bytesOwnedIdentity, bytesDiscussionIdentifier]
                                    canRemoteDelete =
                                        (group2 != null && group2.ownPermissionRemoteDeleteAnything
                                                && AppDatabase.getInstance()
                                            .group2MemberDao()
                                            .groupHasMembers(
                                                bytesOwnedIdentity,
                                                bytesDiscussionIdentifier
                                            ))
                                } else {
                                    canRemoteDelete = false
                                }
                                val builder: Builder =
                                    SecureDeleteEverywhereDialogBuilder(
                                        context,
                                        DISCUSSION,
                                        1,
                                        canRemoteDelete,
                                        true
                                    )
                                        .setDeleteCallback { deletionChoice: DeletionChoice? ->
                                            App.runThread(
                                                DeleteMessagesTask(
                                                    id,
                                                    deletionChoice,
                                                    false
                                                )
                                            )
                                            finishAndClearViewModel()
                                        }
                                Handler(Looper.getMainLooper()).post {
                                    builder.create().show()
                                }
                            }
                        }
                    })
                }

        }, redItems = listOf(R.string.menu_action_delete_discussion),
        onBackPressed = onBackPressed
    )
}