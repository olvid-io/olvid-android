/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.main.search

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.DropdownMenu
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.MessageDao.DiscussionAndMessage
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.tasks.PropagateBookmarkedMessageChangeTask
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.discussion.gallery.FyleListItem
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.discussions.getAnnotatedBody
import io.olvid.messenger.main.discussions.getAnnotatedTitle
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion

@Composable
fun GlobalSearchScreen(
    globalSearchViewModel: GlobalSearchViewModel,
    linkPreviewViewModel: LinkPreviewViewModel,
    bookmarks : List<DiscussionAndMessage>? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberLazyListState()
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }
    if (globalSearchViewModel.searching || globalSearchViewModel.noResults.value.not() || bookmarks.isNullOrEmpty().not()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(id = R.color.almostWhite)),
            contentAlignment = Alignment.TopStart
        ) {
            if (globalSearchViewModel.searching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                globalSearchViewModel.contactsFound?.takeIf { it.isNotEmpty() }?.let {
                    item {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(id = R.string.global_search_result_contacts),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(it) { contact ->
                        SearchResult(
                            contact = contact,
                            globalSearchViewModel = globalSearchViewModel
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                globalSearchViewModel.groupsFound?.takeIf { it.isNotEmpty() }?.let {
                    item {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(id = R.string.global_search_result_groups),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(it) { searchableDiscussion ->
                        SearchResult(
                            searchableDiscussion = searchableDiscussion,
                            globalSearchViewModel = globalSearchViewModel
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                globalSearchViewModel.otherDiscussionsFound?.takeIf { it.isNotEmpty() }?.let {
                    item {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(id = R.string.global_search_result_other_discussions),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(it) { searchableDiscussion ->
                        SearchResult(
                            searchableDiscussion = searchableDiscussion,
                            globalSearchViewModel = globalSearchViewModel
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                bookmarks?.takeIf { it.isNotEmpty() }?.let {
                    items(it) { message ->
                        SearchResult(
                            discussionAndMessage = message,
                            globalSearchViewModel = globalSearchViewModel,
                            menuItems = listOf(
                                stringResource(id = R.string.menu_action_unbookmark) to {
                                    App.runThread {
                                        AppDatabase.getInstance().messageDao().updateBookmarked(message.message.id, false)
                                        AppSingleton.getBytesCurrentIdentity()?.let { bytesOwnedIdentity ->
                                            PropagateBookmarkedMessageChangeTask(bytesOwnedIdentity, message.message, false).run()
                                        }
                                    }
                                }
                            )
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                globalSearchViewModel.messagesFound?.takeIf { it.isNotEmpty() }?.let {
                    item {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(id = R.string.global_search_result_messages) + (globalSearchViewModel.messageLimitReachedCount?.let { count -> " (${it.size}/$count)" } ?: ""),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(it) { discussionAndMessage ->
                        SearchResult(
                            discussionAndMessage = discussionAndMessage,
                            globalSearchViewModel = globalSearchViewModel
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                globalSearchViewModel.fylesFound?.takeIf { it.isNotEmpty() }?.let {
                    item {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(id = R.string.global_search_result_attachments) + (globalSearchViewModel.attachmentLimitReachedCount?.let { count -> " (${it.size}/$count)" } ?: ""),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(it) { fyle ->
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InitialView(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .requiredSize(20.dp),
                                    initialViewSetup = { view ->
                                        view.setFromCache(fyle.message.senderIdentifier)
                                    },
                                )
                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = AppSingleton.getContactCustomDisplayName(fyle.message.senderIdentifier)
                                        ?: stringResource(id = R.string.text_deleted_contact),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = StringUtils.getNiceDateString(
                                        context,
                                        fyle.message.timestamp
                                    ).toString(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            when (fyle.fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType) {
                                OpenGraph.MIME_TYPE -> {
                                    LinkListItem(
                                        fyleAndStatus = fyle.fyleAndStatus,
                                        onClick = {
                                            fyle.message.goto(context)
                                        },
                                        linkPreviewViewModel = linkPreviewViewModel,
                                        globalSearchViewModel = globalSearchViewModel
                                    )
                                }

                                else -> {
                                    FyleListItem(fyleAndStatus = fyle.fyleAndStatus,
                                        fileName = globalSearchViewModel.highlight(
                                            content = fyle.fyleAndStatus.fyleMessageJoinWithStatus.fileName
                                        ),
                                        extraHorizontalPadding = 4.dp,
                                        onClick = { fyle.message.goto(context) },
                                        onLongClick = {
                                            if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(
                                                    PreviewUtils.getNonNullMimeType(
                                                        fyle.fyleAndStatus.fyleMessageJoinWithStatus.mimeType,
                                                        fyle.fyleAndStatus.fyleMessageJoinWithStatus.fileName
                                                    )
                                                ) && SettingsActivity.useInternalImageViewer()
                                            ) {
                                                App.openMessageGalleryActivity(
                                                    context,
                                                    fyle.fyleAndStatus.fyleMessageJoinWithStatus.messageId,
                                                    fyle.fyleAndStatus.fyleMessageJoinWithStatus.fyleId
                                                )
                                            } else {
                                                App.openFyleInExternalViewer(
                                                    context,
                                                    fyle.fyleAndStatus,
                                                    null
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bookmarks != null) {
                MainScreenEmptyList(
                    icon = R.drawable.ic_star,
                    title = R.string.explanation_empty_bookmarks
                )
            } else {
                MainScreenEmptyList(
                    icon = R.drawable.ic_search_anything,
                    title = R.string.explanation_empty_global_search
                )
            }
        }
    }
}

fun Message.goto(context: Context) {
    context.startActivity(Intent(context, DiscussionActivity::class.java).apply {
        putExtra(
            DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA,
            discussionId
        )
        putExtra(
            DiscussionActivity.MESSAGE_ID_INTENT_EXTRA,
            id
        )
    })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResult(
    modifier: Modifier = Modifier,
    searchableDiscussion: SearchableDiscussion? = null,
    discussionAndMessage: DiscussionAndMessage? = null,
    contact: Contact? = null,
    menuItems: List<Pair<String, () -> Unit>>? = null,
    globalSearchViewModel: GlobalSearchViewModel
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .height(Min)
            .fillMaxWidth()
            .combinedClickable(onClick = {
                searchableDiscussion?.let {
                    App.openDiscussionActivity(context, it.discussionId)
                } ifNull {
                    contact?.let {
                        if (it.oneToOne) {
                            App.openOneToOneDiscussionActivity(
                                context,
                                it.bytesOwnedIdentity,
                                it.bytesContactIdentity,
                                false
                            )
                        } else {
                            App.openContactDetailsActivity(
                                context,
                                it.bytesOwnedIdentity,
                                it.bytesContactIdentity
                            )
                        }
                    } ifNull {
                        discussionAndMessage?.message?.goto(context)
                    }
                }
            },
                onLongClick = {
                    if (menuItems
                            .isNullOrEmpty()
                            .not()
                    ) {
                        menuExpanded = true
                    }
                }
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // contextual menu
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            menuItems?.forEach {
                DropdownMenuItem(
                    text = { Text(text = it.first) },
                    onClick = {
                        it.second()
                        menuExpanded = false
                    }
                )
            }
        }

        // InitialView
        InitialView(
            modifier = Modifier
                .padding(
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 8.dp
                )
                .requiredSize(48.dp),
            initialViewSetup = { view ->
                searchableDiscussion?.let {
                    view.setDiscussion(searchableDiscussion)
                }
                contact?.let {
                    view.setContact(contact)
                }
                discussionAndMessage?.discussion?.let {
                    view.setDiscussion(it)
                } ifNull {
                    discussionAndMessage?.message?.senderIdentifier?.let {
                        view.setFromCache(it)
                    }
                }
            },
        )

        // content
        Column(
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = searchableDiscussion?.title?.let { globalSearchViewModel.highlight(it) }
                    ?: contact?.let { AnnotatedString(contact.customDisplayName ?: contact.getIdentityDetails()
                        ?.formatFirstAndLastName(
                            SettingsActivity.getContactDisplayNameFormat(),
                            SettingsActivity.getUppercaseLastName()
                        ) ?: contact.getCustomDisplayName()) }?.let { globalSearchViewModel.highlight(it) }
                    ?: discussionAndMessage?.discussion?.getAnnotatedTitle(context)
                    ?: AnnotatedString(stringResource(id = R.string.text_deleted_contact)),
                color = colorResource(id = R.color.primary700),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Subtitle or matching message body
            (searchableDiscussion?.groupMemberNameList?.takeIf { it.isNotEmpty() }?.let {
                AnnotatedString(it)
            }
                ?: contact?.let {
                    contact.getIdentityDetails()?.let { identityDetails ->
                        if (contact.customDisplayName != null)
                            AnnotatedString(
                                identityDetails.formatDisplayName(
                                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                    SettingsActivity.getUppercaseLastName()
                                )
                            )
                        else
                            identityDetails.formatPositionAndCompany(
                                SettingsActivity.getContactDisplayNameFormat()
                            )?.let { AnnotatedString(it) }
                    }
                }
                ?: discussionAndMessage?.discussion?.getAnnotatedBody(context, discussionAndMessage.message.apply {
                    contentBody = globalSearchViewModel.truncateMessageBody(body = discussionAndMessage.message.getStringContent(context))
                }))?.let {
                Text(
                    text = globalSearchViewModel.highlight(it),
                    color = colorResource(id = R.color.greyTint),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            discussionAndMessage?.message?.timestamp?.let {
                // Date
                Text(
                    text = StringUtils.getLongNiceDateString(context, it) as String,
                    color = colorResource(id = R.color.grey),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
