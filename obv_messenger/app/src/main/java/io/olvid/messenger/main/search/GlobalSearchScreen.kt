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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.olvid.messenger.R.color
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.discussion.gallery.FyleListItem
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel.SearchableDiscussion

@Composable
fun GlobalSearchScreen(
    globalSearchViewModel: GlobalSearchViewModel,
    linkPreviewViewModel: LinkPreviewViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            (context as? Activity)?.window?.decorView?.windowToken?.let { windowToken ->
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken,0)
            }
        }
    }
    if (globalSearchViewModel.searching.value || globalSearchViewModel.noResults.value.not()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(id = color.almostWhite)),
            contentAlignment = Alignment.TopStart
        ) {
            if (globalSearchViewModel.searching.value) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(bottom = 80.dp)) {
                globalSearchViewModel.contactsFound?.takeIf { it.isNotEmpty() }?.let {
                    item {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(id = string.global_search_result_contacts),
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
                            text = stringResource(id = string.global_search_result_groups),
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
                            text = stringResource(id = string.global_search_result_other_discussions),
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

                globalSearchViewModel.messagesFound?.takeIf { it.isNotEmpty() }?.let {
                    item {
                        Text(
                            modifier = Modifier.padding(8.dp),
                            text = stringResource(id = string.global_search_result_messages),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(it) { message ->
                        SearchResult(
                            message = message,
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
                            text = stringResource(id = string.global_search_result_attachments),
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
                                    text = AppSingleton.getContactCustomDisplayName(fyle.message.senderIdentifier) ?: stringResource(id = string.text_deleted_contact),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.weight(1f))
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
                                                );
                                            } else {
                                                App.openFyleInExternalViewer(
                                                    context,
                                                    fyle.fyleAndStatus,
                                                    null
                                                );
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
            MainScreenEmptyList(icon = R.drawable.ic_search_anything, title = R.string.explanation_empty_global_search)
        }
    }
}

fun Message.goto(context: Context) {
    context.startActivity(Intent(context, MainActivity::class.java).apply {
        action = MainActivity.FORWARD_ACTION
        putExtra(
            MainActivity.FORWARD_TO_INTENT_EXTRA,
            DiscussionActivity::class.java.name
        )
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

@Composable
private fun SearchResult(
    searchableDiscussion: SearchableDiscussion? = null,
    message: Message? = null,
    contact: Contact? = null,
    globalSearchViewModel: GlobalSearchViewModel
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .height(Min)
            .fillMaxWidth()
            .clickable {
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
                        message?.goto(context)
                    }
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                message?.senderIdentifier?.let {
                    view.setFromCache(message.senderIdentifier)
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
                    ?: AnnotatedString(message?.let { AppSingleton.getContactCustomDisplayName(message.senderIdentifier) }
                        ?: stringResource(id = string.text_deleted_contact)),
                color = colorResource(id = color.primary700),
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
                    contact.getIdentityDetails()?.let {
                        if (contact.customDisplayName != null)
                            AnnotatedString(
                                it.formatDisplayName(
                                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                    SettingsActivity.getUppercaseLastName()
                                )
                            )
                        else
                            it.formatPositionAndCompany(
                                SettingsActivity.getContactDisplayNameFormat()
                            )?.let { AnnotatedString(it) }
                    }
                }
                ?: message?.getStringContent(context)?.let {
                    globalSearchViewModel.truncateMessageBody(body = it)
                })?.let {
                Text(
                    text = if (message?.status == Message.STATUS_DRAFT)
                        AnnotatedString(stringResource(id = string.text_draft_message_prefix, "")) + globalSearchViewModel.highlight(it)
                    else
                        globalSearchViewModel.highlight(it),
                    color = colorResource(id = color.greyTint),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            message?.timestamp?.let {
                // Date
                Text(
                    text = StringUtils.getLongNiceDateString(context, it) as String,
                    color = colorResource(id = color.grey),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
