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

package io.olvid.messenger.contact

import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.Identity
import io.olvid.engine.encoder.DecodingException
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.lock_screen.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.onBackPressed
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.tasks.PromptToDeleteContactTask
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding

import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment
import io.olvid.messenger.main.MainActivity

class ContactDetailsActivity : LockableActivity() {
    private val contactDetailsViewModel: ContactDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                ContextCompat.getColor(this, R.color.blackOverlay)
            )
        )

        super.onCreate(savedInstanceState)

        onBackPressed {
            if (contactDetailsViewModel.fullScreenPhotoUrl != null) {
                contactDetailsViewModel.fullScreenPhotoUrl = null
                return@onBackPressed
            }
            if (isTaskRoot) {
                App.showMainActivityTab(this, MainActivity.CONTACTS_TAB)
            }
            finish()
        }

        setContent {
            ContactUpdatesListener(
                contactDetailsViewModel = contactDetailsViewModel,
                onUpdate = {
                    contactDetailsViewModel.refreshPublishedAndTrustedDetails(
                        it?.contact?.bytesOwnedIdentity,
                        it?.contact?.bytesContactIdentity,
                    )
                }
            )
            val contactAndInvitation =
                contactDetailsViewModel.contactAndInvitation?.observeAsState()
            val navController = rememberNavController()
            val currentDestination by navController.currentBackStackEntryAsState()
            SharedTransitionLayout {
                Scaffold(
                    containerColor = colorResource(R.color.almostWhite),
                    contentColor = colorResource(R.color.almostBlack),
                    topBar = {
                        OlvidTopAppBar(
                            titleText = when (currentDestination?.destination?.route) {
                                Routes.CONTACT_DETAILS -> if (contactAndInvitation?.value?.contact?.oneToOne == true) {
                                    stringResource(R.string.activity_title_contact_details)
                                } else {
                                    stringResource(R.string.activity_title_user_details)
                                }

                                Routes.FULL_GROUPS_LIST -> stringResource(R.string.label_groups_common)
                                Routes.TRUST_ORIGINS -> stringResource(R.string.label_trust_origins)
                                Routes.CONTACT_INTRODUCTION -> stringResource(
                                    R.string.dialog_title_introduce_contact,
                                    contactAndInvitation?.value?.contact?.getCustomDisplayName()
                                        .orEmpty()
                                )

                                else -> stringResource(R.string.activity_title_contact_details)
                            },
                            onBackPressed = onBackPressedDispatcher::onBackPressed,
                            actions = {
                                when (currentDestination?.destination?.route) {
                                    Routes.CONTACT_DETAILS -> if (contactAndInvitation?.value != null) {
                                        IconButton(onClick = {
                                            contactDetailsViewModel.contactAndInvitation?.value?.contact?.let { contact ->
                                                val intent = Intent(Intent.ACTION_SEND)
                                                intent.type = "text/plain"
                                                val identityUrl = ObvUrlIdentity(
                                                    contact.bytesContactIdentity,
                                                    contactDetailsViewModel.publishedAndTrustedDetails.firstOrNull()?.identityDetails?.formatDisplayName(
                                                        JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                                        false
                                                    )
                                                ).getUrlRepresentation(false)
                                                    ?: return@let
                                                intent.putExtra(Intent.EXTRA_TEXT, identityUrl)
                                                startActivity(
                                                    Intent.createChooser(
                                                        intent,
                                                        getString(R.string.title_sharing_chooser)
                                                    )
                                                )
                                            }
                                        }) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_share),
                                                tint = colorResource(R.color.almostBlack),
                                                contentDescription = stringResource(R.string.button_label_share_contact_id)
                                            )
                                        }
                                        IconButton(onClick = {
                                            contactDetailsViewModel.contactAndInvitation?.value?.contact?.let { contact ->
                                                EditNameAndPhotoDialogFragment.newInstance(
                                                    this@ContactDetailsActivity,
                                                    contact
                                                )
                                                    .show(supportFragmentManager, "dialog")
                                            }
                                        }) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_pencil),
                                                tint = colorResource(R.color.almostBlack),
                                                contentDescription = stringResource(R.string.dialog_title_rename_contact)
                                            )
                                        }
                                        var mainMenuOpened by remember {
                                            mutableStateOf(false)
                                        }
                                        IconButton(onClick = {
                                            mainMenuOpened = true
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                tint = colorResource(R.color.almostBlack),
                                                contentDescription = "menu"
                                            )
                                            OlvidDropdownMenu(
                                                expanded = mainMenuOpened,
                                                onDismissRequest = { mainMenuOpened = false }
                                            ) {
                                                getContactMenu().forEach { menuItem ->
                                                    OlvidDropdownMenuItem(
                                                        text = stringResource(menuItem.label),
                                                        onClick = {
                                                            mainMenuOpened = false
                                                            menuItem.onClick()
                                                        },
                                                        textColor = if (menuItem.action in listOf(
                                                                MenuItem.Action.DELETE
                                                            )
                                                        )
                                                            colorResource(R.color.red)
                                                        else
                                                            null
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                ) { contentPadding ->

                    Box(
                        modifier = Modifier
                            .padding(top = contentPadding.calculateTopPadding())
                            .cutoutHorizontalPadding()
                            .systemBarsHorizontalPadding()
                            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.CONTACT_DETAILS
                        ) {
                            contactDetails(
                                contactDetailsViewModel = contactDetailsViewModel,
                                imageClick = ::onPhotoClicked,
                                onIntroduce = {
                                    contactAndInvitation?.value?.contact?.let { contact ->
                                        if (contact.hasChannelOrPreKey()) {
                                            navController.navigate(Routes.CONTACT_INTRODUCTION)
                                        } else {
                                            App.toast(
                                                R.string.toast_message_established_channel_required_for_introduction,
                                                Toast.LENGTH_LONG
                                            )
                                        }
                                    }
                                },
                                onFullGroupsList = {
                                    navController.navigate(Routes.FULL_GROUPS_LIST)
                                },
                                onTrustOrigins = {
                                    navController.navigate(Routes.TRUST_ORIGINS)
                                },
                                sharedTransitionScope = this@SharedTransitionLayout
                            )
                            fullGroupsList(
                                contactDetailsViewModel = contactDetailsViewModel,
                            )
                            trustOrigins(
                                contactDetailsViewModel = contactDetailsViewModel,
                                onBack = {
                                    navController.navigateUp()
                                })
                            contactIntroduction(
                                contactDetailsViewModel = contactDetailsViewModel,
                                onDone = {
                                    navController.navigateUp()
                                })
                        }
                    }
                }
                AnimatedVisibility(
                    visible = contactDetailsViewModel.fullScreenPhotoUrl != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorResource(R.color.blackDarkOverlay))
                            .clickable(
                                indication = null,
                                interactionSource = null,
                            ) { contactDetailsViewModel.fullScreenPhotoUrl = null },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = contactDetailsViewModel.fullScreenPhotoUrl?.let {
                                App.absolutePathFromRelative(
                                    it
                                )
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .sharedElement(
                                    rememberSharedContentState(key = "profile-photo"),
                                    animatedVisibilityScope = this@AnimatedVisibility
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        val contact = contactDetailsViewModel.contactAndInvitation?.value?.contact ?: return
        App.runThread {
            if (contact.newPublishedDetails == Contact.PUBLISHED_DETAILS_NEW_UNSEEN) {
                contact.newPublishedDetails =
                    Contact.PUBLISHED_DETAILS_NEW_SEEN
                AppDatabase.getInstance().contactDao().updatePublishedDetailsStatus(
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity,
                    contact.newPublishedDetails
                )
            }
        }
    }

    fun onPhotoClicked(photoUrl: String?) {
        photoUrl?.takeIf { it.isNotEmpty() }?.let {
            contactDetailsViewModel.fullScreenPhotoUrl = it
        }
    }

    private fun handleIntent(intent: Intent) {
        if (!intent.hasExtra(CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA) || !intent.hasExtra(
                CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA
            )
        ) {
            finish()
            Logger.w("Missing contact identity in intent.")
            return
        }

        val contactBytesIdentity = intent.getByteArrayExtra(
            CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA
        )
        val contactBytesOwnedIdentity = intent.getByteArrayExtra(
            CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA
        )

        contactDetailsViewModel.contactAndInvitation?.removeObservers(this)
        contactDetailsViewModel.groupDiscussions?.removeObservers(this)
        contactDetailsViewModel.setContactBytes(this,contactBytesOwnedIdentity!!, contactBytesIdentity!!)
    }

    data class MenuItem(
        val action: Action,
        @StringRes val label: Int,
        @DrawableRes val icon: Int? = null,
        val onClick: () -> Unit = {}
    ) {
        enum class Action { RECREATE, REFRESH, DEBUG, DELETE }
    }

    private fun getContactMenu() = buildList {
        val contact = contactDetailsViewModel.contactAndInvitation?.value?.contact ?: return@buildList
        if (contact.active) {
            add(MenuItem(MenuItem.Action.RECREATE, R.string.menu_action_recreate_channels) {
                val contactAndInvitation = contactDetailsViewModel.contactAndInvitation?.value
                if (contactAndInvitation != null) {
                    val builder = SecureAlertDialogBuilder(
                        this@ContactDetailsActivity,
                        R.style.CustomAlertDialog
                    )
                        .setTitle(R.string.dialog_title_recreate_channels)
                        .setMessage(R.string.dialog_message_recreate_channels)
                        .setPositiveButton(
                            R.string.button_label_ok
                        ) { _: DialogInterface?, _: Int ->
                            try {
                                AppSingleton.getEngine().recreateAllChannels(
                                    contactAndInvitation.contact.bytesOwnedIdentity,
                                    contactAndInvitation.contact.bytesContactIdentity
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)
                    builder.create().show()
                }
            })
        }
        add(MenuItem(MenuItem.Action.REFRESH, R.string.menu_action_refresh_status) {
            contactDetailsViewModel.contactAndInvitation?.value?.contact?.let { contact ->
                AppSingleton.getEngine().forceContactDeviceDiscovery(
                    contact.bytesOwnedIdentity,
                    contact.bytesContactIdentity
                )
            }
        })
        add(
            MenuItem(
                MenuItem.Action.DEBUG,
                R.string.menu_action_debug_information
            ) {
                val contactAndInvitation = contactDetailsViewModel.contactAndInvitation?.value
                if (contactAndInvitation != null) {
                    val contact = contactAndInvitation.contact

                    val link = ObvUrlIdentity(
                        contact.bytesContactIdentity,
                        contact.displayName
                    ).getUrlRepresentation(false)

                    val sb = StringBuilder()
                    sb.append(getString(R.string.debug_label_number_of_devices)).append(" ")
                        .append(contact.deviceCount).append("\n")
                    sb.append("- ")
                        .append(getString(R.string.debug_label_number_of_devices_no_channel))
                        .append(" ")
                        .append(contact.deviceCount - contact.establishedChannelCount - contact.preKeyCount)
                        .append("\n")
                    sb.append("- ")
                        .append(getString(R.string.debug_label_number_of_devices_pre_key))
                        .append(" ").append(contact.preKeyCount).append("\n")
                    sb.append("- ")
                        .append(getString(R.string.debug_label_number_of_devices_oblivious_channel))
                        .append(" ").append(contact.establishedChannelCount).append("\n\n")
                    try {
                        val contactIdentity = Identity.of(contact.bytesContactIdentity)
                        sb.append(getString(R.string.debug_label_server)).append(" ")
                        sb.append(contactIdentity.server).append("\n\n")
                    } catch (_: DecodingException) {
                    }
                    sb.append(getString(R.string.debug_label_identity_link)).append("\n")
                    sb.append(link).append("\n\n")
                    sb.append(getString(R.string.debug_label_capabilities)).append("\n")
                    sb.append(getString(R.string.bullet)).append(" ").append(
                        getString(
                            R.string.debug_label_capability_continuous_gathering,
                            contact.capabilityWebrtcContinuousIce
                        )
                    ).append("\n")
                    sb.append(getString(R.string.bullet)).append(" ").append(
                        getString(
                            R.string.debug_label_capability_one_to_one_contacts,
                            contact.capabilityOneToOneContacts
                        )
                    ).append("\n")
                    sb.append(getString(R.string.bullet)).append(" ").append(
                        getString(
                            R.string.debug_label_capability_groups_v2,
                            contact.capabilityGroupsV2
                        )
                    ).append("\n")

                    val textView = TextView(this@ContactDetailsActivity)
                    val sixteenDp = (16 * resources.displayMetrics.density).toInt()
                    textView.setPadding(sixteenDp, sixteenDp, sixteenDp, sixteenDp)
                    textView.setTextIsSelectable(true)
                    textView.autoLinkMask = Linkify.WEB_URLS
                    textView.movementMethod = LinkMovementMethod.getInstance()
                    textView.text = sb

                    val builder = SecureAlertDialogBuilder(
                        this@ContactDetailsActivity,
                        R.style.CustomAlertDialog
                    )
                        .setTitle(R.string.menu_action_debug_information)
                        .setView(textView)
                        .setNeutralButton(R.string.button_label_copy) { _, _ ->
                            val clipboardManager: ClipboardManager? =
                                getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText(link, link))
                            App.toast(R.string.toast_message_clipboard_copied, Toast.LENGTH_SHORT)
                        }
                        .setPositiveButton(R.string.button_label_ok, null)
                    builder.create().show()
                }
            }
        )
        add(
            MenuItem(
                action = MenuItem.Action.DELETE,
                label = if (contact.oneToOne) R.string.menu_action_delete_contact else R.string.menu_action_delete_user
            ) {
                val contactAndInvitation = contactDetailsViewModel.contactAndInvitation?.value
                if (contactAndInvitation != null) {
                    App.runThread(
                        PromptToDeleteContactTask(
                            this@ContactDetailsActivity,
                            contactAndInvitation.contact.bytesOwnedIdentity,
                            contactAndInvitation.contact.bytesContactIdentity
                        ) { onBackPressedDispatcher.onBackPressed() })
                }
            })
    }

    companion object {
        const val CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA: String = "contact_bytes_identity"
        const val CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA: String = "contact_bytes_owned_identity"
    }
}
