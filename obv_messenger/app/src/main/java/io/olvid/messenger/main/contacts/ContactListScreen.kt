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

@file:OptIn(ExperimentalFoundationApi::class)

package io.olvid.messenger.main.contacts

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Badge
import androidx.compose.material.BadgedBox
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.RefreshingIndicator
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactOrKeycloakDetails
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.CONTACT
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK_MORE_RESULTS
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class ContactFilterTab(val label: String, val filter: (ContactOrKeycloakDetails) -> Boolean)

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun ContactListScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    contactListViewModel: ContactListViewModel,
    refreshing: Boolean,
    onRefresh: (() -> Unit)?,
    onClick: (contact: ContactOrKeycloakDetails) -> Unit,
    onInvite: (contact: Contact) -> Unit,
    onScrollStart: (() -> Unit)? = null,
    onSelectionDone: (() -> Unit)? = null,
    contactMenu: ContactMenu? = null,
    selectable: Boolean = false
) {

    val contacts by contactListViewModel.filteredContacts.observeAsState()
    val refreshState = onRefresh?.let { rememberPullRefreshState(refreshing, onRefresh) }
    val tabs = arrayListOf(
        ContactFilterTab(
            label = stringResource(id = R.string.contact_list_tab_contact),
            filter = { contactOrKeycloakDetails -> contactOrKeycloakDetails.contact?.oneToOne == true }
        ),
        ContactFilterTab(
            label = stringResource(id = R.string.contact_list_tab_others),
            filter = { contactOrKeycloakDetails -> contactOrKeycloakDetails.contact?.oneToOne == false }
        )
    )
    if (contactListViewModel.keycloakManaged.value) {
        tabs.add(ContactFilterTab(
            label = stringResource(id = R.string.contact_list_tab_directory),
            filter = { contactOrKeycloakDetails -> contactOrKeycloakDetails.contactType != CONTACT }
        ))
    }


    AppCompatTheme {
        Box(
            modifier = modifier
                .then(refreshState?.let { Modifier.pullRefresh(it) } ?: Modifier)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val pagerState =
                    rememberPagerState { if (contactListViewModel.keycloakManaged.value) 3 else 2 }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        if (page == 2) {
                            if (contactListViewModel.getFilter() == null) {
                                contactListViewModel.setFilter("")
                            } else if (contactListViewModel.getFilter() == ""){
                                contactListViewModel.refreshKeycloakSearch()
                            }
                        }
                    }
                }
                val coroutineScope = rememberCoroutineScope()
                Header(pagerState, tabs, coroutineScope, contactListViewModel, contacts)
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = if (contactListViewModel.keycloakManaged.value) 2 else 1
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            contacts?.filter { tabs[page].filter(it) }?.let { list ->
                                if (list.isEmpty().not()) {
                                    val lazyListState = rememberLazyListState()
                                    onScrollStart?.let {
                                        LaunchedEffect(key1 = lazyListState.isScrollInProgress) {
                                            if (lazyListState.isScrollInProgress) {
                                                // when we start scolling, dismiss soft keyboard
                                                onScrollStart()
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        state = lazyListState,
                                        contentPadding = PaddingValues(bottom = 80.dp),
                                    ) {
                                        items(items = list) { contactOrKeycloakDetails ->

                                            when (contactOrKeycloakDetails.contactType) {

                                                KEYCLOAK_MORE_RESULTS -> {
                                                    KeycloakMissingCount(contactOrKeycloakDetails.additionalSearchResults)
                                                }

                                                else -> {
                                                    Contact(
                                                        contactOrKeycloakDetails = contactOrKeycloakDetails,
                                                        contactListViewModel = contactListViewModel,
                                                        contactMenu = contactMenu,
                                                        onClick =
                                                        if (selectable) {
                                                            {
                                                                contactListViewModel.selectedContacts.find {
                                                                    it.bytesContactIdentity.contentEquals(
                                                                        contactOrKeycloakDetails.contact?.bytesContactIdentity
                                                                    )
                                                                }?.let {
                                                                    contactListViewModel.selectedContacts.remove(
                                                                        it
                                                                    )
                                                                } ifNull {
                                                                    contactOrKeycloakDetails.contact?.let { contact ->
                                                                        contactListViewModel.selectedContacts.add(
                                                                            contact
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            onClick
                                                        },
                                                        endContent =
                                                        if (selectable) {
                                                            {
                                                                Checkbox(
                                                                    checked = contactListViewModel.selectedContacts.any {
                                                                        it.bytesContactIdentity.contentEquals(
                                                                            contactOrKeycloakDetails.contact?.bytesContactIdentity
                                                                        )
                                                                    },
                                                                    onCheckedChange = { checked ->
                                                                        contactOrKeycloakDetails.contact?.let { contact ->
                                                                            if (checked) {
                                                                                contactListViewModel.selectedContacts.add(
                                                                                    contact
                                                                                )
                                                                            } else {
                                                                                contactListViewModel.selectedContacts.removeIf {
                                                                                    it.bytesContactIdentity.contentEquals(
                                                                                        contact.bytesContactIdentity
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                        } else {
                                                            when {
                                                                contactOrKeycloakDetails.contactType == CONTACT
                                                                        && contactOrKeycloakDetails.contact?.oneToOne == false
                                                                        && contactOrKeycloakDetails.contact.shouldShowChannelCreationSpinner()
                                                                    .not() -> {
                                                                    {
                                                                        TextButton(onClick = {
                                                                            onInvite.invoke(
                                                                                contactOrKeycloakDetails.contact
                                                                            )
                                                                        }) {
                                                                            Text(
                                                                                text = stringResource(
                                                                                    id = R.string.button_label_invite
                                                                                )
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                contactOrKeycloakDetails.contactType == KEYCLOAK
                                                                        && AppSingleton.getContactCacheInfo(contactOrKeycloakDetails.keycloakUserDetails?.identity) == null -> {
                                                                    {
                                                                        TextButton(onClick = {
                                                                            onClick.invoke(
                                                                                contactOrKeycloakDetails
                                                                            )
                                                                        }) {
                                                                            Text(
                                                                                text = stringResource(
                                                                                    id = R.string.button_label_add
                                                                                )
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                else -> {
                                                                    null
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                        contentAlignment = Center
                                    ) {
                                        if (contactListViewModel.isFiltering())
                                            MainScreenEmptyList(
                                                icon = drawable.ic_contacts_filter,
                                                title = string.explanation_no_contact_match_filter,
                                                subtitle = null
                                            )
                                        else
                                            MainScreenEmptyList(
                                                icon = drawable.tab_contacts,
                                                title = when (page) {
                                                    0 -> string.explanation_empty_contact_list
                                                    1 -> R.string.explanation_empty_other_contact_list
                                                    else -> R.string.explanation_empty_directory
                                                },
                                                subtitle = when (page) {
                                                    0 -> string.explanation_empty_contact_list_sub
                                                    1 -> R.string.explanation_empty_other_contact_list_sub
                                                    else -> null
                                                }
                                            )
                                    }
                                }
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = contactListViewModel.keycloakSearchInProgress && page == 2,
                            enter = EnterTransition.None,
                            exit = fadeOut(),
                        ) {
                            KeycloakSearching()
                        }
                    }
                }
            }
            refreshState?.let {
                RefreshingIndicator(refreshing = refreshing, refreshState = refreshState)
            }
            if (selectable) {
                IconButton(modifier = Modifier
                    .padding(bottom = 32.dp)
                    .align(BottomEnd)
                    .requiredSize(56.dp)
                    .background(
                        color = Color(0xFF2F65F5).copy(alpha = if (contactListViewModel.selectedContacts.isNotEmpty()) 1f else .6f),
                        shape = RoundedCornerShape(size = 12.92308.dp)
                    ),
                    enabled = contactListViewModel.selectedContacts.isNotEmpty(),
                    onClick = { onSelectionDone?.invoke() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_forward),
                        tint = Color.White,
                        contentDescription = "validate"
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    pagerState: PagerState,
    tabs: ArrayList<ContactFilterTab>,
    coroutineScope: CoroutineScope,
    contactListViewModel: ContactListViewModel,
    contacts: List<ContactOrKeycloakDetails>?,
) {
    TabRow(
        selectedTabIndex = pagerState.currentPage,
        backgroundColor = colorResource(id = R.color.almostWhite),
        contentColor = colorResource(id = R.color.almostBlack),
    ) {
        tabs.forEachIndexed { index, tab ->
            CustomTab(
                selected = pagerState.currentPage == index,
                horizontalTextPadding = 4.dp,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                text = {
                    if (contactListViewModel.getFilter().isNullOrEmpty()) {
                        Text(text = tab.label, softWrap = false, overflow = TextOverflow.Ellipsis)
                    } else if (index == 2 && contactListViewModel.keycloakSearchInProgress) {
                        BadgedBox(badge = {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(16.dp),
                                color = colorResource(id = color.olvid_gradient_light),
                                strokeWidth = 2.dp
                            )
                        }) {
                            Text(
                                text = tab.label,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        BadgedBox(badge = {
                            Badge(
                                modifier = Modifier.padding(4.dp),
                                backgroundColor = colorResource(id = color.olvid_gradient_light),
                            ) {
                                Text(
                                    color = colorResource(id = R.color.almostWhite),
                                    text = contacts?.filter { tab.filter(it) }?.let { list ->
                                        if (list.isNotEmpty() && list.last().contactType == KEYCLOAK_MORE_RESULTS) {
                                            (list.size - 1).toString() + "+"
                                        } else {
                                            list.size.toString()
                                        }
                                    }
                                        ?: ""
                                )
                            }
                        }) {
                            Text(
                                text = tab.label,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun Contact(
    contactOrKeycloakDetails: ContactOrKeycloakDetails,
    contactListViewModel: ContactListViewModel,
    contactMenu: ContactMenu? = null,
    onClick: (contact: ContactOrKeycloakDetails) -> Unit,
    endContent: (@Composable () -> Unit)? = null
) {
    ContactListItem(
        title = contactOrKeycloakDetails.getAnnotatedName()
            .highlight(
                SpanStyle(
                    background = colorResource(id = color.searchHighlightColor),
                    color = colorResource(id = color.black)
                ),
                contactListViewModel.filterPatterns
            ),
        body = contactOrKeycloakDetails.getAnnotatedDescription()
            ?.highlight(
                SpanStyle(
                    background = colorResource(id = color.searchHighlightColor),
                    color = colorResource(id = color.black)
                ),
                contactListViewModel.filterPatterns
            ),
        shouldAnimateChannel = contactOrKeycloakDetails.contact?.shouldShowChannelCreationSpinner() == true && contactOrKeycloakDetails.contact.active,
        publishedDetails = contactOrKeycloakDetails.contact?.newPublishedDetails == Contact.PUBLISHED_DETAILS_NEW_SEEN || contactOrKeycloakDetails.contact?.newPublishedDetails == Contact.PUBLISHED_DETAILS_NEW_UNSEEN,
        publishedDetailsNotification = contactOrKeycloakDetails.contact?.newPublishedDetails == Contact.PUBLISHED_DETAILS_NEW_UNSEEN,
        onClick = {
            onClick(
                contactOrKeycloakDetails
            )
        },
        endContent = endContent,
        initialViewSetup = { initialView ->
            when (contactOrKeycloakDetails.contactType) {
                CONTACT -> contactOrKeycloakDetails.contact?.let {
                    initialView.setContact(
                        it
                    )
                }

                KEYCLOAK -> contactOrKeycloakDetails.keycloakUserDetails?.let { keycloakUserDetails ->
                    val identityDetails =
                        keycloakUserDetails.getIdentityDetails(
                            null
                        )
                    val name =
                        identityDetails.formatFirstAndLastName(
                            SettingsActivity.getContactDisplayNameFormat(),
                            SettingsActivity.getUppercaseLastName()
                        )
                    AppSingleton.getContactPhotoUrl(keycloakUserDetails.identity)?.let {
                        initialView.setPhotoUrl(keycloakUserDetails.identity, it)
                    } ?:
                    initialView.setInitial(
                        keycloakUserDetails.identity,
                        StringUtils.getInitial(
                            name
                        )
                    )
                    initialView.setKeycloakCertified(
                        true
                    )
                }

                else -> {}
            }
        },
        onRenameContact = if (contactMenu != null && contactOrKeycloakDetails.contactType != KEYCLOAK) {
            {
                contactOrKeycloakDetails.contact?.let {
                    contactMenu.rename(
                        contactOrKeycloakDetails.contact
                    )
                }
            }
        } else null,
        onCallContact = if (contactMenu != null && contactOrKeycloakDetails.contactType != KEYCLOAK) {
            {
                contactOrKeycloakDetails.contact?.let {
                    contactMenu.call(
                        contactOrKeycloakDetails.contact
                    )
                }
            }
        } else null,
        onDeleteContact = if (contactMenu != null && contactOrKeycloakDetails.contactType != KEYCLOAK) {
            {
                contactOrKeycloakDetails.contact?.let {
                    contactMenu.delete(
                        contactOrKeycloakDetails.contact
                    )
                }
            }
        } else null,
    )
}

@Composable
private fun KeycloakSearching() {
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 64.dp)
            .fillMaxWidth()
            .background(
                color = colorResource(id = R.color.dialogBackground),
                shape = RoundedCornerShape(8.dp)
            )
            .border(1.dp, colorResource(id = R.color.greyTint), RoundedCornerShape(8.dp))
            .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = colorResource(id = color.olvid_gradient_light))
        Text(
            text = stringResource(id = string.label_searching_company_directory),
            color = colorResource(
                id = color.grey
            )
        )
    }
}

@Preview
@Composable
private fun KeycloakSearchingPreview() {
    AppCompatTheme {
        KeycloakSearching()
    }
}

@Composable
private fun KeycloakMissingCount(missingResults: Int?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = colorResource(
                    id = color.lighterGrey
                )
            )

    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .align(Center),
            text = missingResults?.let {
                pluralStringResource(
                    id = R.plurals.text_keycloak_missing_search_result,
                    missingResults,
                    missingResults
                )
            }
                ?: stringResource(id = string.text_keycloak_missing_some_search_result),
            color = colorResource(id = color.grey),
            textAlign = TextAlign.Center,
            style = OlvidTypography.body2,
            fontStyle = FontStyle.Italic
        )
    }
}

@Preview
@Composable
private fun KeycloakMissingCountPreview() {
    AppCompatTheme {
        KeycloakMissingCount(5)
    }
}
