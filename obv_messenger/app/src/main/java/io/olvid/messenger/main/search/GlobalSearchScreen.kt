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

package io.olvid.messenger.main.search

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.DropdownMenu
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.dao.MessageDao.DiscussionAndMessage
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.plus
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.discussion.gallery.FyleListItem
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.message.OutboundMessageStatus
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.contacts.CustomTab
import io.olvid.messenger.main.discussions.getAnnotatedBody
import io.olvid.messenger.main.discussions.getAnnotatedTitle
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion
import kotlinx.coroutines.launch

fun LazyPagingItems<*>?.isLoading() =
    (this?.loadState?.refresh == LoadState.Loading) || (this?.loadState?.append == LoadState.Loading)

@Composable
fun GlobalSearchScreen(
    modifier: Modifier = Modifier,
    globalSearchViewModel: GlobalSearchViewModel,
    linkPreviewViewModel: LinkPreviewViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val messages = globalSearchViewModel.messagesFound?.collectAsLazyPagingItems()
    val attachments = globalSearchViewModel.fylesFound?.collectAsLazyPagingItems()
    val links = globalSearchViewModel.linksFound?.collectAsLazyPagingItems()
    val pages = listOf(
        // first -> label, second -> hasResults
        R.string.global_search_result_contacts to {
            globalSearchViewModel.contactsFound.isNullOrEmpty().not()
        },
        R.string.global_search_result_groups to {
            globalSearchViewModel.otherDiscussionsFound.isNullOrEmpty()
                .not() || globalSearchViewModel.groupsFound.isNullOrEmpty().not()
        },
        R.string.global_search_result_messages to { (messages?.itemCount ?: 0) > 0 },
        R.string.global_search_result_attachments to { (attachments?.itemCount ?: 0) > 0 },
        R.string.global_search_result_links to { (links?.itemCount ?: 0) > 0 },
    )

    val pagerState = rememberPagerState { pages.size }
    val loading =
        globalSearchViewModel.searching || messages.isLoading() || attachments.isLoading() || links.isLoading()

    Column(modifier = modifier) {
        var neverSwitchedTab by remember { mutableStateOf(true) }

        LaunchedEffect(pagerState.isScrollInProgress) {
            if (pagerState.isScrollInProgress) {
                neverSwitchedTab = false
            }
        }

        LaunchedEffect(loading) {
            if (!loading && neverSwitchedTab) {
                if (!globalSearchViewModel.contactsFound.isNullOrEmpty()) {
                    pagerState.requestScrollToPage(0)
                } else if (!globalSearchViewModel.otherDiscussionsFound.isNullOrEmpty() || !globalSearchViewModel.groupsFound.isNullOrEmpty()) {
                    pagerState.requestScrollToPage(1)
                } else if ((messages?.itemCount ?: 0) > 0) {
                    pagerState.requestScrollToPage(2)
                } else if ((attachments?.itemCount ?: 0) > 0) {
                    pagerState.requestScrollToPage(3)
                } else if ((links?.itemCount ?: 0) > 0) {
                    pagerState.requestScrollToPage(4)
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                backgroundColor = colorResource(id = R.color.almostWhite),
                contentColor = colorResource(id = R.color.almostBlack),
                edgePadding = 0.dp
            ) {
                pages.forEachIndexed { index, page ->
                    CustomTab(
                        selected = pagerState.currentPage == index,
                        horizontalTextPadding = 8.dp,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                modifier = Modifier.alpha(if (page.second()) 1f else .3f),
                                text = stringResource(id = page.first),
                                softWrap = false,
                            )
                        }
                    )
                }
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        HorizontalPager(
            state = pagerState
        ) { page ->
            val lazyListState = rememberLazyListState()
            LaunchedEffect(lazyListState.isScrollInProgress) {
                if (lazyListState.isScrollInProgress) {
                    keyboardController?.hide()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorResource(id = R.color.almostWhite))
                    .systemBarsHorizontalPadding()
                    .cutoutHorizontalPadding(),
                contentAlignment = Alignment.TopStart
            ) {
                when (page) {
                    0 -> {
                        if (globalSearchViewModel.contactsFound.isNullOrEmpty()) {
                            if (!loading) {
                                NoResultsFound(0)
                            }
                        } else {
                            LazyColumn(
                                state = lazyListState,
                                contentPadding = WindowInsets.safeDrawing
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(LocalDensity.current)
                                        + PaddingValues(bottom = 80.dp + dimensionResource(R.dimen.tab_bar_size))
                            ) {
                                globalSearchViewModel.contactsFound?.takeIf { it.isNotEmpty() }
                                    ?.let {
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
                            }
                        }
                    }

                    1 -> {
                        if (globalSearchViewModel.otherDiscussionsFound.isNullOrEmpty()
                            && globalSearchViewModel.groupsFound.isNullOrEmpty()) {
                            if (!loading) {
                                NoResultsFound(1)
                            }
                        } else {
                            LazyColumn(
                                state = lazyListState,
                                contentPadding = WindowInsets.safeDrawing
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(LocalDensity.current)
                                        + PaddingValues(bottom = 80.dp + dimensionResource(R.dimen.tab_bar_size))
                            ) {
                                globalSearchViewModel.groupsFound?.takeIf { it.isNotEmpty() }
                                    ?.let {
                                        items(it) { searchableDiscussion ->
                                            SearchResult(
                                                searchableDiscussion = searchableDiscussion,
                                                globalSearchViewModel = globalSearchViewModel
                                            )
                                        }
                                    }

                                globalSearchViewModel.otherDiscussionsFound?.takeIf { it.isNotEmpty() }
                                    ?.let {
                                        item {
                                            Text(
                                                modifier = Modifier.padding(8.dp),
                                                text = stringResource(id = R.string.global_search_result_other_discussions),
                                                style = OlvidTypography.h2
                                            )
                                        }
                                        items(it) { searchableDiscussion ->
                                            SearchResult(
                                                searchableDiscussion = searchableDiscussion,
                                                globalSearchViewModel = globalSearchViewModel
                                            )
                                        }
                                    }
                            }
                        }
                    }

                    2 -> {
                        messages?.takeIf { it.itemCount > 0 }?.let {
                            LazyColumn(
                                state = lazyListState,
                                contentPadding = WindowInsets.safeDrawing
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(LocalDensity.current)
                                        + PaddingValues(bottom = 80.dp + dimensionResource(R.dimen.tab_bar_size))
                            ) {
                                items(
                                    count = messages.itemCount,
                                    key = messages.itemKey { it.message.id },
                                    contentType = messages.itemContentType { it.message.messageType }
                                ) { index ->
                                    val discussionAndMessage = messages[index]
                                    discussionAndMessage?.let {
                                        SearchResult(
                                            discussionAndMessage = it,
                                            globalSearchViewModel = globalSearchViewModel
                                        )
                                    }
                                }
                            }
                        } ?: run {
                            if (!loading) {
                                NoResultsFound(2)
                            }
                        }
                    }

                    3 -> {
                        attachments?.takeIf { it.itemCount > 0 }?.let {
                            LazyColumn(
                                state = lazyListState,
                                contentPadding = WindowInsets.safeDrawing
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(LocalDensity.current)
                                        + PaddingValues(bottom = 80.dp + dimensionResource(R.dimen.tab_bar_size), top = 8.dp)
                            ) {
                                items(
                                    count = attachments.itemCount,
                                    key = attachments.itemKey { "${it.fyleAndStatus.fyleMessageJoinWithStatus.messageId}-${it.fyleAndStatus.fyleMessageJoinWithStatus.fyleId}" }
                                ) { index ->
                                    attachments[index]?.let { fyle ->
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
                                                    text = ContactCacheSingleton.getContactCustomDisplayName(
                                                        fyle.message.senderIdentifier
                                                    )
                                                        ?: stringResource(id = R.string.text_deleted_contact),
                                                    style = OlvidTypography.body2.copy(
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = StringUtils.getNiceDateString(
                                                        context,
                                                        fyle.message.timestamp
                                                    ).toString(),
                                                    style = OlvidTypography.body2.copy(
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            }
                                            FyleListItem(
                                                fyleAndStatus = fyle.fyleAndStatus,
                                                fileName = globalSearchViewModel.highlight(
                                                    content = fyle.fyleAndStatus.fyleMessageJoinWithStatus.fileName
                                                ),
                                                extraHorizontalPadding = 4.dp,
                                                onClick = {
                                                    fyle.message.goto(
                                                        context,
                                                        globalSearchViewModel.filter
                                                    )
                                                },
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
                                                            fyle.fyleAndStatus.fyleMessageJoinWithStatus.fyleId,
                                                            true
                                                        )
                                                    } else {
                                                        App.openFyleViewer(
                                                            context,
                                                            fyle.fyleAndStatus
                                                        ) {
                                                            fyle.fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened()
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } ?: run {
                            if (!loading) {
                                NoResultsFound(3)
                            }
                        }
                    }


                    4 -> {
                        links?.takeIf { it.itemCount > 0 }?.let {
                            LazyColumn(
                                state = lazyListState,
                                contentPadding = WindowInsets.safeDrawing
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(LocalDensity.current)
                                        + PaddingValues(bottom = 80.dp + dimensionResource(R.dimen.tab_bar_size), top = 8.dp)
                            ) {
                                items(
                                    count = links.itemCount,
                                    key = links.itemKey { "${it.fyleAndStatus.fyleMessageJoinWithStatus.messageId}-${it.fyleAndStatus.fyleMessageJoinWithStatus.fyleId}" }
                                ) { index ->
                                    links[index]?.let { fyle ->
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
                                                    text = ContactCacheSingleton.getContactCustomDisplayName(
                                                        fyle.message.senderIdentifier
                                                    )
                                                        ?: stringResource(id = R.string.text_deleted_contact),
                                                    style = OlvidTypography.body2.copy(
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = StringUtils.getNiceDateString(
                                                        context,
                                                        fyle.message.timestamp
                                                    ).toString(),
                                                    style = OlvidTypography.body2.copy(
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                )
                                            }
                                            LinkListItem(
                                                fyleAndStatus = fyle.fyleAndStatus,
                                                onClick = {
                                                    fyle.message.goto(
                                                        context,
                                                        globalSearchViewModel.filter
                                                    )
                                                },
                                                linkPreviewViewModel = linkPreviewViewModel,
                                                globalSearchViewModel = globalSearchViewModel
                                            )
                                        }
                                    }
                                }
                            }
                        } ?: run {
                            if (!loading) {
                                NoResultsFound(4)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoResultsFound(tabIndex: Int = -1) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MainScreenEmptyList(
            icon = when (tabIndex) {
                0, 1 -> R.drawable.ic_contacts_filter
                else -> R.drawable.ic_search_anything
            },
            title = when (tabIndex) {
                0 -> R.string.explanation_no_contact_match_filter
                1 -> R.string.explanation_no_group_match_filter
                2 -> R.string.explanation_no_message_found
                3 -> R.string.explanation_no_attachment_found
                4 -> R.string.explanation_no_link_found
                else -> R.string.explanation_empty_global_search
            }
        )
    }
}

fun Message.goto(context: Context, searchQuery: String? = null) {
    context.startActivity(Intent(context, DiscussionActivity::class.java).apply {
        putExtra(
            DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA,
            discussionId
        )
        putExtra(
            DiscussionActivity.MESSAGE_ID_INTENT_EXTRA,
            id
        )
        searchQuery?.let {
            putExtra(DiscussionActivity.SEARCH_QUERY_INTENT_EXTRA, it)
        }
    })
}

@Composable
fun SearchResult(
    modifier: Modifier = Modifier,
    searchableDiscussion: SearchableDiscussion? = null,
    discussionAndMessage: DiscussionAndMessage? = null,
    contact: Contact? = null,
    menuItems: List<Pair<String, () -> Unit>>? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    globalSearchViewModel: GlobalSearchViewModel? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .height(Min)
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    onClick?.let {
                        onClick()
                    } ifNull {
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
                                discussionAndMessage?.message?.goto(
                                    context,
                                    globalSearchViewModel?.filter
                                )
                            }
                        }
                    }
                },
                onLongClick = {
                    onLongClick?.let {
                        onLongClick()
                    } ?: run {
                        if (menuItems
                                .isNullOrEmpty()
                                .not()
                        ) {
                            menuExpanded = true
                        }
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
            selected = selected,
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
                modifier = Modifier.fillMaxWidth(),
                text = searchableDiscussion?.title?.let { globalSearchViewModel?.highlight(it) }
                    ?: contact?.let {
                        AnnotatedString(
                            contact.customDisplayName ?: contact.getIdentityDetails()
                                ?.formatFirstAndLastName(
                                    SettingsActivity.contactDisplayNameFormat,
                                    SettingsActivity.uppercaseLastName
                                ) ?: contact.getCustomDisplayName()
                        )
                    }?.let { globalSearchViewModel?.highlight(it) }
                    ?: discussionAndMessage?.discussion?.getAnnotatedTitle(context)
                    ?: AnnotatedString(stringResource(id = R.string.text_deleted_contact)),
                color = colorResource(id = R.color.primary700),
                style = OlvidTypography.body1.copy(
                    fontWeight = FontWeight.Medium
                ),
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
                                    SettingsActivity.uppercaseLastName
                                )
                            )
                        else
                            identityDetails.formatPositionAndCompany(
                                SettingsActivity.contactDisplayNameFormat
                            )?.let { AnnotatedString(it) }
                    }
                }
                ?: discussionAndMessage?.discussion?.getAnnotatedBody(
                    context,
                    discussionAndMessage.message.apply {
                        contentBody = globalSearchViewModel?.truncateMessageBody(
                            body = getStringContent(context)
                        ) ?: getStringContent(context)
                    }))?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    discussionAndMessage?.message?.let { lastMessage ->
                        OutboundMessageStatus(
                            modifier = Modifier.padding(end = 4.dp),
                            size = 14.dp,
                            message = lastMessage
                        )
                    }
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = globalSearchViewModel?.highlight(it) ?: it,
                        color = colorResource(id = R.color.greyTint),
                        style = OlvidTypography.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            discussionAndMessage?.message?.timestamp?.let {
                // Date
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = StringUtils.getLongNiceDateString(context, it) as String,
                    color = colorResource(id = R.color.grey),
                    style = OlvidTypography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
