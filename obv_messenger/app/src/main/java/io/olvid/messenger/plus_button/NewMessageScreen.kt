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

package io.olvid.messenger.plus_button

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.ScanButton
import io.olvid.messenger.designsystem.components.SearchBar
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.contacts.ContactInvitationPopup
import io.olvid.messenger.main.contacts.ContactListScreen
import io.olvid.messenger.main.contacts.ContactListViewModel
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.CONTACT
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK
import io.olvid.messenger.main.contacts.ContactListViewModel.ContactType.KEYCLOAK_MORE_RESULTS
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    contactListViewModel: ContactListViewModel,
    dismissParent: () -> Unit,
    onNewContact: () -> Unit,
    onNewGroup: () -> Unit,
    onGoToScanScreen: (link: String?) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val keyboardController = LocalSoftwareKeyboardController.current
    val contacts by contactListViewModel.filteredContacts.observeAsState(emptyList())
    val context = LocalContext.current
    val showHeader = contacts.isNotEmpty() || contactListViewModel.keycloakManaged.value || contactListViewModel.isFiltering()

    Scaffold(
        modifier = Modifier
            .consumeWindowInsets(WindowInsets.safeDrawing)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentColor = colorResource(R.color.almostBlack),
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                MediumTopAppBar(
                    modifier = Modifier.align(Alignment.TopCenter),
                    title = {
                        if (showHeader) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 16.dp)
                                    .requiredHeight(if (LocalTextStyle.current.fontSize > 23.sp) 192.dp else 64.dp),
                            ) {
                                if (LocalTextStyle.current.fontSize <= 23.sp) {
                                    Spacer(Modifier.height(4.dp))
                                }
                                // Search bar
                                SearchBar(
                                    backgroundColor = colorResource(R.color.almostWhite),
                                    searchText = contactListViewModel.getFilter().orEmpty(),
                                    placeholderText = stringResource(R.string.hint_search_contact_name),
                                    onSearchTextChanged = { contactListViewModel.setFilter(it) },
                                    onClearClick = { contactListViewModel.setFilter(null) })

                                //////////
                                // ugly hack to only show the buttons in the expanded view
                                if (LocalTextStyle.current.fontSize > 23.sp) {
                                    // Buttons
                                    Spacer(Modifier.height(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(colorResource(R.color.almostWhite))
                                    ) {
                                        TextButton(
                                            modifier = Modifier.height(56.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            onClick = onNewContact,
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = colorResource(R.color.almostBlack),
                                            ),
                                            shape = RectangleShape
                                        ) {
                                            Icon(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .border(width = 1.dp, color = colorResource(R.color.greyTint), shape = CircleShape)
                                                    .padding(10.dp),
                                                painter = painterResource(R.drawable.ic_add_member),
                                                contentDescription = null
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                modifier = Modifier.weight(1f, true),
                                                text = stringResource(R.string.button_label_new_contact),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = OlvidTypography.body1.copy(fontWeight = FontWeight.Medium)
                                            )
                                        }

                                        TextButton(
                                            modifier = Modifier.height(56.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            onClick = onNewGroup,
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = colorResource(R.color.almostBlack),
                                            ),
                                            shape = RectangleShape
                                        ) {
                                            Icon(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .border(width = 1.dp, color = colorResource(R.color.greyTint), shape = CircleShape)
                                                    .padding(8.dp),
                                                painter = painterResource(R.drawable.ic_group),
                                                contentDescription = null
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                modifier = Modifier.weight(1f, true),
                                                text = stringResource(R.string.button_label_new_group),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = OlvidTypography.body1.copy(fontWeight = FontWeight.Medium)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    },
                    actions = {
                        ScanButton(onGoToScanScreen)
                        var mainMenuOpened by remember {
                            mutableStateOf(false)
                        }
                        IconButton(
                            onClick = { mainMenuOpened = !mainMenuOpened }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                tint = colorResource(R.color.almostBlack),
                                contentDescription = "menu"
                            )
                            OlvidDropdownMenu(
                                expanded = mainMenuOpened,
                                onDismissRequest = { mainMenuOpened = false }) {
                                OlvidDropdownMenuItem(
                                    text = stringResource(R.string.menu_action_import_from_clipboard),
                                    onClick = {
                                        mainMenuOpened = false
                                        (App.getContext()
                                            .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.let { clipboard ->
                                            val clipData = clipboard.primaryClip
                                            if ((clipData != null) && (clipData.itemCount > 0)) {
                                                val textChars = clipData.getItemAt(0).text
                                                if (textChars != null) {
                                                    val matcher =
                                                        ObvLinkActivity.ANY_PATTERN.matcher(
                                                            textChars
                                                        )
                                                    if (matcher.find()) {
                                                        onGoToScanScreen(textChars.toString())
                                                        return@let
                                                    }
                                                }
                                                App.toast(
                                                    R.string.toast_message_unrecognized_url,
                                                    Toast.LENGTH_SHORT
                                                )
                                            }
                                        } ?: App.toast(
                                            R.string.toast_message_invalid_clipboard_data,
                                            Toast.LENGTH_SHORT
                                        )
                                    })
                            }
                        }
                    },
                    collapsedHeight = 64.dp,
                    expandedHeight = if (showHeader) 256.dp else 64.dp,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = colorResource(id = R.color.almostBlack),
                    ),
                )

                AnimatedVisibility(
                    visible = scrollBehavior.state.collapsedFraction == 0f,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().height(64.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.padding(start = 20.dp).weight(1f, true),
                            text = stringResource(R.string.label_new_message_title),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            style = OlvidTypography.h2.copy(color = colorResource(R.color.almostBlack)),
                        )
                        Spacer(
                            modifier = Modifier.width(112.dp),
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        if (Build.VERSION.SDK_INT < 28) {
            LaunchedEffect(Unit) {
                delay(100)
                keyboardController?.hide()
            }
        }

        if (showHeader) {
            ContactListScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding())
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorResource(R.color.almostWhite)),
                contactListViewModel = contactListViewModel,
                refreshing = false,
                onRefresh = null,
                onClick = { contactOrKeycloakDetails ->
                    val ownedIdentity =
                        AppSingleton.getCurrentIdentityLiveData().value ?: return@ContactListScreen
                    when (contactOrKeycloakDetails.contactType) {
                        CONTACT -> if (contactOrKeycloakDetails.contact?.oneToOne == true) {
                            App.openOneToOneDiscussionActivity(
                                context,
                                ownedIdentity.bytesOwnedIdentity,
                                contactOrKeycloakDetails.contact.bytesContactIdentity,
                                false
                            )
                            dismissParent.invoke()
                        } else {
                            contactListViewModel.contactInvitation = contactOrKeycloakDetails
                        }

                        KEYCLOAK -> if (contactOrKeycloakDetails.keycloakUserDetails != null
                            && ContactCacheSingleton.getContactCacheInfo(
                                contactOrKeycloakDetails.keycloakUserDetails.identity
                            )?.oneToOne != true
                        ) {
                            contactListViewModel.contactInvitation = contactOrKeycloakDetails
                        } else {
                            contactOrKeycloakDetails.keycloakUserDetails?.identity?.let { contactBytes ->
                                App.openOneToOneDiscussionActivity(
                                    context,
                                    ownedIdentity.bytesOwnedIdentity,
                                    contactBytes,
                                    false
                                )
                                dismissParent.invoke()
                            }
                        }

                        KEYCLOAK_MORE_RESULTS -> {}
                    }
                },
                onInvite = null,
                onScrollStart = { keyboardController?.hide() },
                showOthers = false,
                emptyContactTabContent = {
                    MainScreenEmptyList(
                        modifier = Modifier
                            .widthIn(max = 160.dp),
                        bottomPadding = 0.dp,
                        icon = R.drawable.ic_add_member,
                        title = R.string.button_label_add_first_contact,
                        onClick = onNewContact
                    )
                },
                applyHorizontalSafePadding = false,
            )
        } else {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
                contentAlignment = Center
            ) {
                MainScreenEmptyList(
                    modifier = Modifier
                        .widthIn(max = 160.dp),
                    bottomPadding = 0.dp,
                    icon = R.drawable.ic_add_member,
                    title = R.string.button_label_add_first_contact,
                    onClick = onNewContact
                )
            }
        }
        contactListViewModel.contactInvitation?.let { contactOrKeycloakDetails ->
            ContactInvitationPopup(
                contactOrKeycloakDetails = contactOrKeycloakDetails,
                onDismiss = { contactListViewModel.contactInvitation = null },
                onInvite = { contact ->
                    runCatching {
                        if (contact.hasChannelOrPreKey()) {
                            AppSingleton.getEngine().startOneToOneInvitationProtocol(
                                contact.bytesOwnedIdentity,
                                contact.bytesContactIdentity
                            )
                        }
                        if (contact.keycloakManaged) {
                            runCatching {
                                val jsonIdentityDetails = contact.getIdentityDetails()
                                if (jsonIdentityDetails != null && jsonIdentityDetails.signedUserDetails != null) {
                                    AppSingleton.getEngine().addKeycloakContact(
                                        contact.bytesOwnedIdentity,
                                        contact.bytesContactIdentity,
                                        jsonIdentityDetails.signedUserDetails
                                    )
                                }
                            }
                        }
                    }
                },
                onOpenDetails = { contact ->
                    App.openContactDetailsActivity(
                        context,
                        contact.bytesOwnedIdentity,
                        contact.bytesContactIdentity
                    )
                    dismissParent.invoke()
                },
                onOpenDiscussion = { bytesOwnedIdentity, bytesContactIdentity ->
                    App.openOneToOneDiscussionActivity(
                        context,
                        bytesOwnedIdentity,
                        bytesContactIdentity,
                        true,
                    )
                    dismissParent.invoke()
                })
        }
    }
}


@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun NewMessageScreenPreview() {
    val contactListViewModel = viewModel<ContactListViewModel>()
    NewMessageScreen(
        contactListViewModel = contactListViewModel,
        dismissParent = {},
        onNewContact = {},
        onNewGroup = {},
        onGoToScanScreen = {}
    )
}
