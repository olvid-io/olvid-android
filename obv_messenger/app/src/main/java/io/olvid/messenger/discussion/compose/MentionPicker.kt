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

package io.olvid.messenger.discussion.compose

import android.text.Spanned
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.DiscussionInputEditText
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.designsystem.avatar.avatarTextColorArgb
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.mention.MentionUrlSpan
import io.olvid.messenger.discussion.mention.MentionViewModel
import io.olvid.messenger.main.contacts.ContactListItem
import io.olvid.messenger.main.contacts.highlight
import io.olvid.messenger.viewModels.FilteredContactListViewModel

@Composable
fun ColumnScope.MentionPicker(
    mentionViewModel: MentionViewModel = viewModel(),
    filteredContactListViewModel: FilteredContactListViewModel = viewModel(),
    discussionViewModel: DiscussionViewModel = viewModel(),
    inputEditText: DiscussionInputEditText?
) {
    val context = LocalContext.current
    val mentionStatus by mentionViewModel.mentionsStatus.observeAsState()
    val mentionCandidates by discussionViewModel.mentionCandidatesLiveData.observeAsState(emptyList())
    val filteredContacts by filteredContactListViewModel.filteredContacts.observeAsState()

    LaunchedEffect(mentionCandidates) {
        filteredContactListViewModel.setUnfilteredContacts(mentionCandidates)
    }

    LaunchedEffect(mentionStatus) {
        when (val status = mentionStatus) {
            is MentionViewModel.MentionStatus.Filter -> {
                val filter = status.text.lowercase().trim()
                filteredContactListViewModel.setSearchFilter(filter)
            }

            is MentionViewModel.MentionStatus.End -> {
                filteredContactListViewModel.setSearchFilter(null)
                val mention = status.mention
                val contact = status.contact
                val color = avatarTextColorArgb(
                    context,
                    mention.userIdentifier,
                    ContactCacheSingleton.getContactCustomHue(mention.userIdentifier)
                )
                inputEditText?.text?.let { editable ->
                    var mentionText: String = try {
                        val identityDetails = AppSingleton.getJsonObjectMapper()
                            .readValue(contact.identityDetails, JsonIdentityDetails::class.java)
                        "@" + identityDetails.formatDisplayName(
                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                            false
                        )
                    } catch (_: Exception) {
                        "@" + contact.displayName
                    }
                    mentionText += "\ufeff"
                    if (editable.length > mention.rangeEnd && editable[mention.rangeEnd] == ' ') {
                        editable.replace(mention.rangeEnd, mention.rangeEnd + 1, "")
                    }
                    editable.replace(mention.rangeStart, mention.rangeEnd, "$mentionText ")
                    mention.rangeEnd = mention.rangeStart + mentionText.length
                    editable.setSpan(
                        MentionUrlSpan(
                            mention.userIdentifier,
                            mentionText.length,
                            color,
                            null
                        ),
                        mention.rangeStart,
                        mention.rangeStart + mentionText.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    mentionViewModel.updateMentions(editable, -2)
                }
            }

            else -> {
                filteredContactListViewModel.setSearchFilter(null)
            }
        }
    }

    AnimatedVisibility(
        visible = mentionStatus is MentionViewModel.MentionStatus.Filter && !filteredContacts.isNullOrEmpty()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .animateContentSize()
        ) {
            items(filteredContacts?.size ?: 0) { index ->
                val contact = filteredContacts.orEmpty().getOrNull(index)?.contact
                ContactListItem(
                    modifier = Modifier.animateItem(),
                    title = AnnotatedString(
                        ContactCacheSingleton.getContactDetailsFirstLine(contact?.bytesContactIdentity)
                            .orEmpty()
                    ).highlight(
                        SpanStyle(
                            background = colorResource(id = R.color.searchHighlightColor),
                            color = colorResource(id = R.color.black)
                        ),
                        filteredContactListViewModel.filterPatterns
                    ),
                    body = ContactCacheSingleton.getContactDetailsSecondLine(contact?.bytesContactIdentity)
                        ?.run { AnnotatedString(this) }?.highlight(
                            SpanStyle(
                                background = colorResource(id = R.color.searchHighlightColor),
                                color = colorResource(id = R.color.black)
                            ),
                            filteredContactListViewModel.filterPatterns
                        ),
                    onClick = {
                        contact?.let { mentionViewModel.validateMention(it) }
                    },
                    avatar = contact,
                )
            }
        }
    }
}
