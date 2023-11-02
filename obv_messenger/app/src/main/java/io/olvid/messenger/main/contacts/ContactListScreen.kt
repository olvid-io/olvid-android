/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.main.contacts

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.RefreshingIndicator
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactOrKeycloakDetails
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.*
import io.olvid.messenger.settings.SettingsActivity

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ContactListScreen(
    contactListViewModel: ContactListViewModel,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onClick: (contact: ContactOrKeycloakDetails) -> Unit,
    contactMenu: ContactMenu,
) {

    val contacts by contactListViewModel.filteredContacts.observeAsState()
    val refreshState = rememberPullRefreshState(refreshing, onRefresh)

    AppCompatTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            val lazyListState = rememberLazyListState()
            contacts?.let { list ->
                if (list.isEmpty().not()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = lazyListState,
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        itemsIndexed(items = list) { index, contactOrKeycloakDetails ->
                            Column {
                                // Additional results headers
                                if (contactOrKeycloakDetails.contactType == CONTACT && contactOrKeycloakDetails.contact?.oneToOne != true && list.getOrNull(
                                        index - 1
                                    )?.contact?.oneToOne != false
                                ) {
                                    AdditionalResultsHeader(
                                        drawable.ic_not_one_to_one,
                                        string.label_users_below_not_in_contacts
                                    )
                                }

                                if (contactOrKeycloakDetails.contactType == KEYCLOAK && list.getOrNull(
                                        index - 1
                                    )?.contactType != KEYCLOAK
                                ) {
                                    AdditionalResultsHeader(
                                        drawable.ic_keycloak_directory,
                                        string.label_users_below_from_keycloak
                                    )
                                }

                                when (contactOrKeycloakDetails.contactType) {
                                    KEYCLOAK_SEARCHING -> {
                                        KeycloakSearching()
                                    }
                                    KEYCLOAK_MORE_RESULTS -> {
                                        KeycloakMissingCount()
                                    }
                                    else -> {
                                        // contacts
                                        Box {
                                            ContactListItem(
                                                title = contactOrKeycloakDetails.getAnnotatedName()
                                                    .highlight(
                                                        SpanStyle(background = colorResource(id = color.accentOverlay)),
                                                        contactListViewModel.filterPatterns
                                                    ),
                                                body = contactOrKeycloakDetails.getAnnotatedDescription()
                                                    ?.highlight(
                                                        SpanStyle(background = colorResource(id = color.accentOverlay)),
                                                        contactListViewModel.filterPatterns
                                                    ),
                                                shouldAnimateChannel = contactOrKeycloakDetails.contact?.shouldShowChannelCreationSpinner() == true && contactOrKeycloakDetails.contact.active,
                                                publishedDetails = contactOrKeycloakDetails.contact?.newPublishedDetails == Contact.PUBLISHED_DETAILS_NEW_SEEN || contactOrKeycloakDetails.contact?.newPublishedDetails == Contact.PUBLISHED_DETAILS_NEW_UNSEEN,
                                                publishedDetailsNotification = contactOrKeycloakDetails.contact?.newPublishedDetails == Contact.PUBLISHED_DETAILS_NEW_UNSEEN,
                                                onClick = { onClick(contactOrKeycloakDetails) },
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
                                                            initialView.setInitial(
                                                                keycloakUserDetails.identity,
                                                                StringUtils.getInitial(name)
                                                            )
                                                            initialView.setKeycloakCertified(true)
                                                        }
                                                        else -> {}
                                                    }
                                                },
                                                onRenameContact = if (contactOrKeycloakDetails.contactType != KEYCLOAK) {
                                                    {
                                                        contactOrKeycloakDetails.contact?.let {
                                                            contactMenu.rename(
                                                                contactOrKeycloakDetails.contact
                                                            )
                                                        }
                                                    }
                                                } else null,
                                                onCallContact = if (contactOrKeycloakDetails.contactType != KEYCLOAK) {
                                                    {
                                                        contactOrKeycloakDetails.contact?.let {
                                                            contactMenu.call(
                                                                contactOrKeycloakDetails.contact
                                                            )
                                                        }
                                                    }
                                                } else null,
                                                onDeleteContact = if (contactOrKeycloakDetails.contactType != KEYCLOAK) {
                                                    {
                                                        contactOrKeycloakDetails.contact?.let {
                                                            contactMenu.delete(
                                                                contactOrKeycloakDetails.contact
                                                            )
                                                        }
                                                    }
                                                } else null,
                                            )
                                            if (index < list.size - 1) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 68.dp, end = 12.dp)
                                                        .requiredHeight(1.dp)
                                                        .align(Alignment.BottomStart)
                                                        .background(
                                                            color = colorResource(id = color.lightGrey)
                                                        )
                                                )
                                            }
                                        }
                                    }
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
                                title = string.explanation_empty_contact_list,
                                subtitle = string.explanation_empty_contact_list_sub
                            )
                    }
                }
            }

            RefreshingIndicator(refreshing = refreshing, refreshState = refreshState)
        }
    }
}

@Composable
private fun KeycloakSearching() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally
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
private fun KeycloakMissingCount() {
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
            text = stringResource(id = string.text_keycloak_missing_some_search_result),
            color = colorResource(id = color.grey),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic
        )
    }
}

@Preview
@Composable
private fun KeycloakMissingCountPreview() {
    AppCompatTheme {
        KeycloakMissingCount()
    }
}

@Composable
private fun AdditionalResultsHeader(@DrawableRes drawableRes: Int, @StringRes stringRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(56.dp)
            .padding(top = 16.dp, start = 20.dp, end = 8.dp),
        verticalAlignment = CenterVertically,
    ) {
        Image(
            modifier = Modifier.requiredSize(32.dp),
            painter = painterResource(id = drawableRes),
            contentDescription = ""
        )
        Spacer(modifier = Modifier.requiredWidth(20.dp))
        Text(
            text = stringResource(id = stringRes).uppercase(),
            color = colorResource(id = color.primary700),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}