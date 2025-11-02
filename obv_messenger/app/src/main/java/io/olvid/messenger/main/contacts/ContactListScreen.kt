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

@file:OptIn(ExperimentalFoundationApi::class)

package io.olvid.messenger.main.contacts

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.plus
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.attachments.constantSp
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.RefreshingIndicator
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactOrKeycloakDetails
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.CONTACT
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK_MORE_RESULTS
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.Normalizer

const val DEFAULT_CHAR_GROUP = "#"

private fun getContactGroupKey(
    contactDetails: ContactOrKeycloakDetails,
    default: String = DEFAULT_CHAR_GROUP
): String {
    if (contactDetails.contactType == KEYCLOAK_MORE_RESULTS) {
        // put last
        return ""
    }
    val firstChar = contactDetails.getAnnotatedName().text.firstOrNull() ?: return default

    // Normalize to remove accents
    val normalizedChar = Normalizer.normalize(firstChar.toString(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "") // Remove diacritical marks (accents)
        .firstOrNull() ?: return default

    // keep only letters
    return if (normalizedChar.isLetter()) {
        normalizedChar.uppercaseChar().toString()
    } else {
        default
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ContactListScreen(
    modifier: Modifier = Modifier,
    contactListViewModel: ContactListViewModel,
    refreshing: Boolean,
    onRefresh: (() -> Unit)?,
    onClick: (contact: ContactOrKeycloakDetails) -> Unit,
    onInvite: ((contact: Contact) -> Unit)?,
    onScrollStart: (() -> Unit)? = null,
    onSelectionDone: (() -> Unit)? = null,
    contactMenu: ContactMenu? = null,
    selectable: Boolean = false,
    showOthers: Boolean = true,
    emptyContactTabContent: (@Composable () -> Unit)? = null,
    applyHorizontalSafePadding: Boolean = true,
    addPlusButtonBottomPadding: Boolean = false,
) {

    val contacts by contactListViewModel.filteredContacts.observeAsState()
    val refreshState = onRefresh?.let { rememberPullRefreshState(refreshing, onRefresh) }

    val pages =
        remember(contactListViewModel.keycloakManaged.value, contactListViewModel.isFiltering()) {
            linkedMapOf<ContactListPage, (ContactOrKeycloakDetails) -> Boolean>().apply {
                this[ContactListPage.CONTACTS] = {
                    if (showOthers) {
                        it.contact?.oneToOne == true
                    } else {
                        it.contactType == CONTACT
                    }
                }
                if (showOthers) {
                    this[ContactListPage.OTHERS] = { it.contact?.oneToOne == false }
                }
                if (contactListViewModel.keycloakManaged.value) {
                    this[ContactListPage.DIRECTORY] = { it.contactType != CONTACT }
                }
            }
        }
    val pageTypes = remember(pages) { pages.keys.toList() }

    Box(
        modifier = modifier
            .then(refreshState?.let { Modifier.pullRefresh(it) } ?: Modifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val pagerState =
                rememberPagerState { if (contactListViewModel.isFiltering() && showOthers.not()) 1 else pages.size }
            LaunchedEffect(pagerState.currentPage) {
                snapshotFlow { pagerState.currentPage }.collect { pageIndex ->
                    val page = pageTypes[pageIndex]
                    if (page == ContactListPage.DIRECTORY || (showOthers.not() && contactListViewModel.getFilter().isNullOrEmpty().not())) {
                        if (contactListViewModel.getFilter() == null) {
                            contactListViewModel.setFilter("")
                        }
                        if (contactListViewModel.getFilter() == "") {
                            contactListViewModel.refreshKeycloakSearch()
                        }
                    }
                }
            }
            val coroutineScope = rememberCoroutineScope()
            if (pageTypes.size > 1) {
                if (contactListViewModel.isFiltering() && showOthers.not()) {
                    if (contactListViewModel.filteredContacts.value.isNullOrEmpty().not()) {
                        FilterChipsBar(
                            contactListViewModel = contactListViewModel,
                            pages = pageTypes
                        ) {
                            if (contactListViewModel.filteredPages.remove(it).not()) {
                                contactListViewModel.filteredPages.add(it)
                            }
                        }
                    }
                } else {
                    Header(
                        pagerState,
                        pageTypes,
                        coroutineScope,
                        contactListViewModel,
                        contacts,
                        pages
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = if (contactListViewModel.keycloakManaged.value) 2 else 1
            ) { pageIndex ->
                val page = pageTypes[pageIndex]
                val filter =
                    if (contactListViewModel.isFiltering() && showOthers.not()) { contactOrKeycloakDetails ->
                        val passesPageFilter =
                            if (contactListViewModel.filteredPages.isEmpty()) {
                                true
                            } else {
                                contactListViewModel.filteredPages.any {
                                    pages[it]!!(contactOrKeycloakDetails)
                                }
                            }
                        if (passesPageFilter) {
                            if (contactOrKeycloakDetails.contactType == KEYCLOAK) {
                                ContactCacheSingleton.getContactCacheInfo(
                                    contactOrKeycloakDetails.keycloakUserDetails?.identity
                                ) == null
                            } else {
                                true
                            }
                        } else {
                            false
                        }
                    } else pages[page]!!
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        contacts?.filter(filter)
                            ?.groupBy {
                                getContactGroupKey(it)
                            }
                            ?.let { grouped ->
                                val sortedGrouped = grouped.toSortedMap(compareBy { key ->
                                    // put defaults first and empty last
                                    when (key) {
                                        DEFAULT_CHAR_GROUP -> {
                                            Char.MIN_VALUE.toString()
                                        }

                                        "" -> {
                                            Char.MAX_VALUE.toString()
                                        }

                                        else -> {
                                            key
                                        }
                                    }
                                })
                                if (sortedGrouped.isEmpty().not()) {
                                    val lazyListState = rememberLazyListState()
                                    onScrollStart?.let {
                                        LaunchedEffect(key1 = lazyListState.isScrollInProgress) {
                                            if (lazyListState.isScrollInProgress) {
                                                // when we start scrolling, dismiss soft keyboard
                                                onScrollStart()
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then (
                                                if (applyHorizontalSafePadding)
                                                    Modifier.systemBarsHorizontalPadding()
                                                else
                                                    Modifier
                                            ),
                                        state = lazyListState,
                                        contentPadding = WindowInsets.safeDrawing
                                            .only(WindowInsetsSides.Bottom)
                                            .asPaddingValues(LocalDensity.current)
                                                + if (addPlusButtonBottomPadding) PaddingValues(bottom = 80.dp + dimensionResource(R.dimen.tab_bar_size)) else PaddingValues(bottom = 16.dp),
                                    ) {
                                        sortedGrouped.forEach { (initial, list) ->
                                            if (initial.isNotEmpty()) {
                                                stickyHeader {
                                                    Text(
                                                        modifier = Modifier
                                                            .cutoutHorizontalPadding()
                                                            .systemBarsHorizontalPadding()
                                                            .padding(
                                                                start = 4.dp,
                                                                top = 4.dp,
                                                            )
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(colorResource(R.color.whiteOverlay)),
                                                        text = initial,
                                                        textAlign = TextAlign.Center,
                                                        style = OlvidTypography.h2.copy(
                                                            fontSize = constantSp(20),
                                                            color = colorResource(id = R.color.greyTint),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                }
                                            }
                                            items(items = list) { contactOrKeycloakDetails ->

                                                when (contactOrKeycloakDetails.contactType) {

                                                    KEYCLOAK_MORE_RESULTS -> {
                                                        KeycloakMissingCount(
                                                            contactOrKeycloakDetails.additionalSearchResults
                                                        )
                                                    }

                                                    else -> {
                                                        Contact(
                                                            contactOrKeycloakDetails = contactOrKeycloakDetails,
                                                            contactListViewModel = contactListViewModel,
                                                            contactMenu = contactMenu,
                                                            onClick =
                                                                if (selectable) {
                                                                    { contact ->
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
                                                                        onClick(contact)
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
                                                                                && onInvite != null
                                                                                && contactOrKeycloakDetails.contact?.oneToOne == false
                                                                                && contactOrKeycloakDetails.contact.active
                                                                                && contactOrKeycloakDetails.contact.shouldShowChannelCreationSpinner().not()
                                                                                     -> {
                                                                            {
                                                                                val inviteSent =
                                                                                    AppDatabase.getInstance()
                                                                                        .invitationDao()
                                                                                        .getContactOneToOneInvitation(
                                                                                            contactOrKeycloakDetails.contact.bytesOwnedIdentity,
                                                                                            contactOrKeycloakDetails.contact.bytesContactIdentity
                                                                                        )
                                                                                        .observeAsState()
                                                                                OlvidTextButton(
                                                                                    text =
                                                                                        if (inviteSent.value == null) stringResource(
                                                                                            R.string.button_label_invite
                                                                                        ) else stringResource(
                                                                                            R.string.button_label_invited
                                                                                        ),
                                                                                    contentColor =
                                                                                        if (inviteSent.value == null) colorResource(
                                                                                            R.color.olvid_gradient_light
                                                                                        ) else colorResource(
                                                                                            R.color.greyTint
                                                                                        ),
                                                                                    enabled =
                                                                                        inviteSent.value == null,
                                                                                    onClick = {
                                                                                        onInvite.invoke(
                                                                                            contactOrKeycloakDetails.contact
                                                                                        )
                                                                                    },
                                                                                )
                                                                            }
                                                                        }

                                                                        contactOrKeycloakDetails.contactType == KEYCLOAK
                                                                                && ContactCacheSingleton.getContactCacheInfo(
                                                                            contactOrKeycloakDetails.keycloakUserDetails?.identity
                                                                        ) == null -> {
                                                                            {
                                                                                OlvidTextButton(
                                                                                    text = stringResource(
                                                                                        R.string.button_label_add
                                                                                    ),
                                                                                    onClick = {
                                                                                        onClick.invoke(
                                                                                            contactOrKeycloakDetails
                                                                                        )
                                                                                    }
                                                                                )
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
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(bottom = 48.dp),
                                        contentAlignment = Center
                                    ) {
                                        if (contactListViewModel.isFiltering()) {
                                            MainScreenEmptyList(
                                                bottomPadding = if (showOthers) 64.dp else 0.dp,
                                                icon = R.drawable.ic_contacts_filter,
                                                title = R.string.explanation_no_contact_match_filter,
                                                subtitle = null
                                            )
                                        } else if (emptyContactTabContent == null) {
                                            MainScreenEmptyList(
                                                icon = R.drawable.tab_contacts,
                                                title = when (page) {
                                                    ContactListPage.CONTACTS -> R.string.explanation_empty_contact_list
                                                    ContactListPage.OTHERS -> R.string.explanation_empty_other_contact_list
                                                    ContactListPage.DIRECTORY -> R.string.explanation_empty_directory
                                                },
                                                subtitle = when (page) {
                                                    ContactListPage.CONTACTS -> R.string.explanation_empty_contact_list_sub
                                                    ContactListPage.OTHERS -> R.string.explanation_empty_other_contact_list_sub
                                                    else -> null
                                                }
                                            )
                                        } else {
                                            emptyContactTabContent.invoke()
                                        }
                                    }
                                }
                            }
                    }

                    @Suppress("RemoveRedundantQualifierName")
                    androidx.compose.animation.AnimatedVisibility(
                        visible = contactListViewModel.keycloakSearchInProgress && page == ContactListPage.DIRECTORY,
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
            IconButton(
                modifier = Modifier
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

@Composable
private fun Header(
    pagerState: PagerState,
    pages: List<ContactListPage>,
    coroutineScope: CoroutineScope,
    contactListViewModel: ContactListViewModel,
    contacts: List<ContactOrKeycloakDetails>?,
    filters: Map<ContactListPage, (ContactOrKeycloakDetails) -> Boolean>
) {
    TabRow(
        selectedTabIndex = pagerState.currentPage.coerceAtMost(pages.size - 1),
        backgroundColor = colorResource(id = R.color.almostWhite),
        contentColor = colorResource(id = R.color.almostBlack),
    ) {
        pages.forEachIndexed { index, page ->
            val filter = filters[page]!!
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
                        Text(
                            text = stringResource(page.labelResId),
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (page == ContactListPage.DIRECTORY && contactListViewModel.keycloakSearchInProgress) {
                        BadgedBox(badge = {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(16.dp),
                                color = colorResource(id = R.color.olvid_gradient_light),
                                strokeWidth = 2.dp
                            )
                        }) {
                            Text(
                                text = stringResource(page.labelResId),
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        BadgedBox(badge = {
                            Badge(
                                modifier = Modifier.padding(4.dp),
                                backgroundColor = colorResource(id = R.color.olvid_gradient_light),
                            ) {
                                Text(
                                    color = colorResource(id = R.color.almostWhite),
                                    text = contacts?.filter(filter)?.let {
                                        if (it.isNotEmpty() && it.last().contactType == KEYCLOAK_MORE_RESULTS) {
                                            (it.size - 1).toString() + "+"
                                        } else {
                                            it.size.toString()
                                        }
                                    }
                                        ?: ""
                                )
                            }
                        }) {
                            Text(
                                text = stringResource(page.labelResId),
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
        modifier = Modifier.background(colorResource(id = R.color.almostWhite)),
        padding = PaddingValues(horizontal = 8.dp),
        title = contactOrKeycloakDetails.getAnnotatedName()
            .highlight(
                SpanStyle(
                    background = colorResource(id = R.color.searchHighlightColor),
                    color = colorResource(id = R.color.black)
                ),
                contactListViewModel.filterPatterns
            ),
        body = contactOrKeycloakDetails.getAnnotatedDescription()
            ?.highlight(
                SpanStyle(
                    background = colorResource(id = R.color.searchHighlightColor),
                    color = colorResource(id = R.color.black)
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
        initialViewSetup = contactOrKeycloakDetails.getInitialViewSetup(),
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
            .cutoutHorizontalPadding()
            .systemBarsHorizontalPadding()
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
        CircularProgressIndicator(color = colorResource(id = R.color.olvid_gradient_light))
        Text(
            text = stringResource(id = R.string.label_searching_company_directory),
            textAlign = TextAlign.Center,
            color = colorResource(
                id = R.color.grey
            )
        )
    }
}

@Preview
@Composable
private fun KeycloakSearchingPreview() {
    KeycloakSearching()
}

@Composable
private fun KeycloakMissingCount(missingResults: Int?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cutoutHorizontalPadding()
            .systemBarsHorizontalPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = colorResource(
                    id = R.color.lighterGrey
                )
            )
            .cutoutHorizontalPadding()

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
                ?: stringResource(id = R.string.text_keycloak_missing_some_search_result),
            color = colorResource(id = R.color.grey),
            textAlign = TextAlign.Center,
            style = OlvidTypography.body2,
            fontStyle = FontStyle.Italic
        )
    }
}

@Preview
@Composable
private fun KeycloakMissingCountPreview() {
    KeycloakMissingCount(5)
}

@Composable
private fun FilterChipsBar(
    contactListViewModel: ContactListViewModel,
    pages: List<ContactListPage>,
    onChipSelected: (ContactListPage) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pages.forEach { page ->
            FilterChip(
                text = stringResource(id = page.labelResId),
                selected = contactListViewModel.filteredPages.contains(page),
                onClick = { onChipSelected(page) },
                icon = when (page) {
                    ContactListPage.CONTACTS -> R.drawable.ic_contacts
                    ContactListPage.DIRECTORY -> R.drawable.ic_keycloak_directory_white
                    else -> null
                }
            )
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: Int?
) {
    val darkMode = SettingsActivity.forcedDarkMode ?: isSystemInDarkTheme()
    val backgroundColor = if (selected) {
        Color(if (darkMode) 0x21FFFFFF else 0xFFD9DCED)
    } else Color.Transparent
    val textColor = Color(if (darkMode) 0xFFBDC0DD else 0xFF423E70)
    val borderColor = if (darkMode) Color(0x66BDC0DD) else Color(0xFFCED0E6)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(30.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = true,
                    color = colorResource(R.color.olvid_gradient_light),
                ),
                onClick = onClick,)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = text, color = textColor, fontWeight = FontWeight.SemiBold)
            AnimatedVisibility(visible = selected) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp)
                )
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun FilterChipPreview() {
    FilterChipsBar(
        contactListViewModel = viewModel(),
        listOf(ContactListPage.CONTACTS, ContactListPage.DIRECTORY)
    ) { }
}