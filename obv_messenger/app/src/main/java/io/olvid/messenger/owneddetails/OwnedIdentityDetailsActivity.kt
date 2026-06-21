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

package io.olvid.messenger.owneddetails

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.WindowManager.LayoutParams
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.android.material.textfield.TextInputLayout
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.Identity
import io.olvid.engine.engine.types.ObvDeviceManagementRequest
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.MuteNotificationDialog
import io.olvid.messenger.customClasses.MuteNotificationDialog.MuteType.PROFILE
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.customClasses.onBackPressed
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.databases.tasks.DeleteOwnedIdentityAndEverythingRelatedToItTask
import io.olvid.messenger.databases.tasks.OwnedDevicesSynchronisationWithEngineTask
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.lock_screen.LockableActivity
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.services.MuteExpirationService
import java.util.Locale

class OwnedIdentityDetailsActivity : LockableActivity() {

    private val ownedDetailsViewModel: OwnedIdentityDetailsViewModel by viewModels()
    private var deleteProfileEverywhere = true

    @OptIn(ExperimentalSharedTransitionApi::class)
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
            if (ownedDetailsViewModel.fullScreenPhotoUrl != null) {
                ownedDetailsViewModel.fullScreenPhotoUrl = null
                return@onBackPressed
            }
            finish()
        }

        setContent {
            val navController = rememberNavController()
            val ownedIdentity by AppSingleton.getCurrentIdentityLiveData().observeAsState()

            DeviceDiscoveryListener { ownedDetailsViewModel.hideRefreshSpinner() }

            SharedTransitionLayout {
                Scaffold(
                    containerColor = colorResource(R.color.almostWhite),
                    contentColor = colorResource(R.color.almostBlack),
                    topBar = {
                        var showMenu by remember { mutableStateOf(false) }
                        OlvidTopAppBar(
                            titleText = stringResource(R.string.activity_title_my_details),
                            onBackPressed = onBackPressedDispatcher::onBackPressed,
                            actions = {
                                // Unmute
                                if (ownedIdentity?.prefMuteNotifications == true && ownedIdentity?.shouldMuteNotifications() == true) {
                                    IconButton(onClick = {
                                        unmuteNotifications(ownedIdentity)
                                    }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_notification_muted),
                                            tint = colorResource(R.color.almostBlack),
                                            contentDescription = stringResource(R.string.menu_action_unmute_notifications)
                                        )
                                    }
                                }
                                // Edit
                                IconButton(onClick = {
                                    ownedIdentity?.let { ownedIdentity ->
                                        ownedDetailsViewModel.bytesOwnedIdentity =
                                            ownedIdentity.bytesOwnedIdentity

                                        ownedDetailsViewModel.latestDetails?.let {
                                            ownedDetailsViewModel.setOwnedIdentityDetails(
                                                it,
                                                ownedIdentity.customDisplayName,
                                                ownedIdentity.unlockPassword != null
                                            )
                                        }
                                        ownedDetailsViewModel.detailsLocked =
                                            ownedIdentity.keycloakManaged
                                        ownedDetailsViewModel.isIdentityInactive =
                                            ownedIdentity.active.not()
                                    }
                                    renameIdentity()
                                }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pencil),
                                        tint = colorResource(R.color.almostBlack),
                                        contentDescription = stringResource(R.string.menu_action_edit_my_details)
                                    )
                                }
                                // menu
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, tint = colorResource(R.color.almostBlack), contentDescription = "menu")
                                }
                                OlvidDropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    // Mute notifications
                                    if (ownedIdentity?.prefMuteNotifications == false && ownedIdentity?.shouldMuteNotifications() == false) {
                                        OlvidDropdownMenuItem(
                                            text = stringResource(R.string.menu_action_mute_profile_notifications),
                                            onClick = {
                                                showMenu = false
                                                muteNotifications(ownedIdentity)
                                            }
                                        )
                                    }
                                    // Refresh subscription status
                                    OlvidDropdownMenuItem(
                                        text = stringResource(R.string.menu_action_refresh_subscription_status),
                                        onClick = {
                                            showMenu = false
                                            refreshSubscription(ownedIdentity)
                                        }
                                    )
                                    // Keycloak
                                    if (ownedIdentity?.keycloakManaged == true) {
                                        OlvidDropdownMenuItem(
                                            text = stringResource(R.string.menu_action_unbind_from_keycloak),
                                            onClick = {
                                                showMenu = false
                                                unbindFromKeycloak(ownedIdentity)
                                            }
                                        )
                                    }
                                    // Neutral notification (only for hidden profiles)
                                    if (ownedIdentity?.isHidden == true) {
                                        OlvidDropdownMenuItem(
                                            text = stringResource(R.string.menu_action_neutral_notifications_when_hidden),
                                            onClick = {
                                                showMenu = false
                                                toggleNeutralNotification(ownedIdentity)
                                            },
                                            trailingIcon = if (ownedIdentity?.prefShowNeutralNotificationWhenHidden == true) {
                                                {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_check),
                                                        contentDescription = null,
                                                        tint = colorResource(R.color.darkGrey)
                                                    )
                                                }
                                            } else null
                                        )
                                    }
                                    // Debug information
                                    OlvidDropdownMenuItem(
                                        text = stringResource(R.string.menu_action_debug_information),
                                        onClick = {
                                            showMenu = false
                                            showDebugInformation(ownedIdentity)
                                        }
                                    )
                                    // Delete profile
                                    OlvidDropdownMenuItem(
                                        text = stringResource(R.string.menu_action_delete_profile),
                                        textColor = colorResource(R.color.red),
                                        onClick = {
                                            showMenu = false
                                            confirmDeleteProfile(ownedIdentity)
                                        }
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(top = innerPadding.calculateTopPadding())
                            .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.OWNED_IDENTITY_DETAILS
                        ) {
                            ownedIdentityDetails(
                                ownedDetailsViewModel = ownedDetailsViewModel,
                                imageClick = { url ->
                                    ownedDetailsViewModel.fullScreenPhotoUrl = url
                                },
                                onReactivate = { reactivateIdentity(ownedIdentity) },
                                onPublish = { publishDetails(ownedIdentity) },
                                onDiscard = { discardDraft(ownedIdentity) },
                                onAddDevice = { addDevice() },
                                onTrustDevice = { device -> trustDevice(device) },
                                onRenameDevice = { device -> renameDevice(device) },
                                onRemoveDeviceExpiration = { device -> removeDeviceExpiration(device) },
                                onRefreshDeviceList = { device -> refreshDeviceList(device) },
                                onRecreateDeviceChannel = { device -> recreateDeviceChannel(device) },
                                onRemoveDevice = { device -> removeDevice(device) },
                                sharedTransitionScope = this@SharedTransitionLayout
                            )
                        }
                    }
                }
                // full screen photo overlay
                AnimatedVisibility(
                    visible = ownedDetailsViewModel.fullScreenPhotoUrl != null,
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
                            ) { ownedDetailsViewModel.fullScreenPhotoUrl = null },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ownedDetailsViewModel.fullScreenPhotoUrl?.let {
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
    }

    private fun muteNotifications(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) return
        val muteNotificationDialog = MuteNotificationDialog(
            this,
            { muteExpirationTimestamp: Long?, _: Boolean, exceptMentioned: Boolean ->
                App.runThread {
                    AppDatabase.getInstance().ownedIdentityDao()
                        .updateMuteNotifications(
                            ownedIdentity.bytesOwnedIdentity,
                            muteExpirationTimestamp,
                            exceptMentioned,
                            System.currentTimeMillis()
                        )
                    MuteExpirationService.scheduleNextExpiration()
                }
            }, PROFILE,
            ownedIdentity.prefMuteNotificationsExceptMentioned
        )
        muteNotificationDialog.show()
    }

    private fun unmuteNotifications(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null || !ownedIdentity.shouldMuteNotifications()) return
        val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.dialog_title_unmute_notifications)
            .setPositiveButton(R.string.button_label_unmute_notifications) { _, _ ->
                App.runThread {
                    // atomically claims the mute window, clears the DB row, and emits the recap.
                    // any concurrent claim (e.g. an alarm firing simultaneously) loses the race
                    // and emits nothing, so the user only ever sees one recap.
                    MuteExpirationService.clearAndEmitForManualUnmute(ownedIdentity.bytesOwnedIdentity)
                    MuteExpirationService.scheduleNextExpiration()
                }
            }
            .setNegativeButton(R.string.button_label_cancel, null)

        if (ownedIdentity.prefMuteNotificationsTimestamp == null) {
            builder.setMessage(R.string.dialog_message_unmute_notifications)
        } else {
            builder.setMessage(
                getString(
                    R.string.dialog_message_unmute_notifications_muted_until,
                    StringUtils.getLongNiceDateString(
                        this,
                        ownedIdentity.prefMuteNotificationsTimestamp!!
                    )
                )
            )
        }
        builder.create().show()
    }

    private fun toggleNeutralNotification(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) return
        if (ownedIdentity.prefShowNeutralNotificationWhenHidden) {
            App.runThread {
                AppDatabase.getInstance().ownedIdentityDao()
                    .updateShowNeutralNotificationWhenHidden(
                        ownedIdentity.bytesOwnedIdentity, false
                    )
            }
        } else {
            SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_neutral_notification_when_hidden)
                .setMessage(R.string.dialog_message_neutral_notification_when_hidden)
                .setPositiveButton(R.string.button_label_activate) { _, _ ->
                    App.runThread {
                        AppDatabase.getInstance().ownedIdentityDao()
                            .updateShowNeutralNotificationWhenHidden(
                                ownedIdentity.bytesOwnedIdentity, true
                            )
                    }
                }
                .setNegativeButton(R.string.button_label_cancel, null)
                .show()
        }
    }

    private fun renameIdentity() {
        // TODO convert EditOwnedIdentityDetailsDialogFragment
        val dialogFragment = EditOwnedIdentityDetailsDialogFragment()
        dialogFragment.show(supportFragmentManager, "dialog")
    }

    private fun refreshSubscription(ownedIdentity: OwnedIdentity?) {
        ownedIdentity?.bytesOwnedIdentity?.let {
            AppSingleton.getEngine().recreateServerSession(it)
        }
    }

    private fun unbindFromKeycloak(ownedIdentity: OwnedIdentity?) {
        ownedIdentity?.bytesOwnedIdentity?.let {
            if (KeycloakManager.isOwnedIdentityTransferRestricted(it)) {
                SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_unbind_from_keycloak_restricted)
                    .setMessage(R.string.dialog_message_unbind_from_keycloak_restricted)
                    .setPositiveButton(R.string.button_label_ok, null)
                    .show()
            } else {
                SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_unbind_from_keycloak)
                    .setMessage(R.string.dialog_message_unbind_from_keycloak)
                    .setPositiveButton(R.string.button_label_ok) { _, _ ->
                        KeycloakManager.unregisterKeycloakManagedIdentity(it)
                        AppSingleton.getEngine()
                            .unbindOwnedIdentityFromKeycloak(it)
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .show()
            }
        }
    }

    private fun confirmDeleteProfile(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) return
        App.runThread {
            val otherNotHiddenOwnedIdentityCount = AppDatabase.getInstance().ownedIdentityDao()
                .countNotHidden() - (if (ownedIdentity.isHidden) 0 else 1)
            val hasOtherDevices = AppDatabase.getInstance().ownedDeviceDao()
                .getAllSync(ownedIdentity.bytesOwnedIdentity).size > 1

            val messageRes = if (hasOtherDevices) {
                if (otherNotHiddenOwnedIdentityCount >= 1) R.string.dialog_message_delete_profile_multi else R.string.dialog_message_delete_last_profile_multi
            } else {
                if (otherNotHiddenOwnedIdentityCount >= 1) R.string.dialog_message_delete_profile else R.string.dialog_message_delete_last_profile
            }

            runOnUiThread {
                SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_delete_profile)
                    .setMessage(messageRes)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_next) { _, _ ->
                        showDeleteProfileDialog(
                            ownedIdentity,
                            otherNotHiddenOwnedIdentityCount < 1,
                            hasOtherDevices
                        )
                    }
                    .show()
            }
        }
    }

    private fun showDeleteProfileDialog(
        ownedIdentity: OwnedIdentity,
        deleteAllHiddenOwnedIdentities: Boolean,
        hasOtherDevices: Boolean
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_view_delete_profile, null)
        val cancelButton: Button = dialogView.findViewById(R.id.cancel_button)
        val deleteButton: Button = dialogView.findViewById(R.id.delete_button)
        deleteButton.isEnabled = false
        @SuppressLint("UseSwitchCompatOrMaterialCode") val deleteEverywhereSwitch: Switch =
            dialogView.findViewById(R.id.delete_profile_everywhere_switch)
        deleteEverywhereSwitch.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
            deleteProfileEverywhere = checked
            deleteButton.setText(if (checked) R.string.button_label_delete_everywhere else R.string.button_label_delete)
        }
        val typeDeleteEditText: EditText = dialogView.findViewById(R.id.type_delete_edit_text)
        typeDeleteEditText.hint = getString(
            R.string.hint_type_delete_here_to_proceed,
            getString(R.string.text_delete_capitalized)
        )
        typeDeleteEditText.addTextChangedListener(object : TextChangeListener() {
            val target: String = getString(R.string.text_delete_capitalized)
            override fun afterTextChanged(s: Editable) {
                deleteButton.isEnabled = target == s.toString().uppercase(Locale.getDefault())
            }
        })
        val explanationTextView: TextView =
            dialogView.findViewById(R.id.delete_dialog_confirmation_explanation)
        if (ownedIdentity.active) {
            if (hasOtherDevices) {
                explanationTextView.setText(R.string.explanation_delete_owned_identity_multi)
                deleteEverywhereSwitch.isChecked = false
                deleteProfileEverywhere = false
            } else {
                explanationTextView.setText(R.string.explanation_delete_owned_identity)
                deleteEverywhereSwitch.isChecked = true
                deleteProfileEverywhere = true
            }
            deleteEverywhereSwitch.isEnabled = true
        } else {
            explanationTextView.setText(R.string.explanation_delete_inactive_owned_identity)
            deleteEverywhereSwitch.isChecked = false
            deleteEverywhereSwitch.isEnabled = false
            deleteProfileEverywhere = false
        }

        val dialog = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.dialog_title_delete_profile)
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener { dialog.dismiss() }
        deleteButton.setOnClickListener {
            App.runThread {
                val bytesOwnedIdentities = mutableListOf<ByteArray>()
                bytesOwnedIdentities.add(ownedIdentity.bytesOwnedIdentity)
                if (deleteAllHiddenOwnedIdentities) {
                    val hiddenOwnedIdentities =
                        AppDatabase.getInstance().ownedIdentityDao().allHidden
                    for (hiddenOwnedIdentity in hiddenOwnedIdentities) {
                        bytesOwnedIdentities.add(hiddenOwnedIdentity.bytesOwnedIdentity)
                    }
                }
                try {
                    for (bytesOwnedIdentity in bytesOwnedIdentities) {
                        AppSingleton.getEngine().deleteOwnedIdentityAndNotifyContacts(
                            bytesOwnedIdentity,
                            deleteProfileEverywhere
                        )
                    }
                    runOnUiThread { dialog.dismiss() }
                    for (bytesOwnedIdentity in bytesOwnedIdentities) {
                        App.runThread(
                            DeleteOwnedIdentityAndEverythingRelatedToItTask(
                                bytesOwnedIdentity
                            )
                        )
                    }
                    finish()
                    App.toast(R.string.toast_message_profile_deleted, Toast.LENGTH_SHORT)
                } catch (_: Exception) {
                    App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT)
                }
            }
        }
        dialog.window?.setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        dialog.show()
    }

    private fun showDebugInformation(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) return
        val sb = StringBuilder()
        val identity = try {
            Identity.of(ownedIdentity.bytesOwnedIdentity)
        } catch (_: Exception) {
            null
        }
        identity?.let {
            sb.append(getString(R.string.debug_label_server)).append(" ").append(it.server)
                .append("\n\n")
        }

        if (ownedIdentity.keycloakManaged) {
            AppSingleton.getEngine()
                .getOwnedIdentityKeycloakState(ownedIdentity.bytesOwnedIdentity)?.keycloakServer?.let {
                    sb.append(getString(R.string.debug_label_identity_provider)).append(" ")
                        .append(it)
                        .append("\n\n")
                }
        }
        sb.append(getString(R.string.debug_label_identity_link)).append("\n")
        sb.append(
            ObvUrlIdentity(
                ownedIdentity.bytesOwnedIdentity,
                ownedIdentity.displayName
            ).getUrlRepresentation(false)
        ).append("\n\n")
        sb.append(getString(R.string.debug_label_capabilities)).append("\n")
        sb.append(getString(R.string.bullet)).append(" ").append(
            getString(
                R.string.debug_label_capability_continuous_gathering,
                ownedIdentity.capabilityWebrtcContinuousIce
            )
        ).append("\n")
        sb.append(getString(R.string.bullet)).append(" ").append(
            getString(
                R.string.debug_label_capability_one_to_one_contacts,
                ownedIdentity.capabilityOneToOneContacts
            )
        ).append("\n")
        sb.append(getString(R.string.bullet)).append(" ").append(
            getString(
                R.string.debug_label_capability_groups_v2,
                ownedIdentity.capabilityGroupsV2
            )
        ).append("\n")

        val textView = TextView(this)
        val density = resources.displayMetrics.density
        val sixteenDp = (16 * density).toInt()
        textView.setPadding(sixteenDp, sixteenDp, sixteenDp, sixteenDp)
        textView.setTextIsSelectable(true)
        textView.autoLinkMask = Linkify.ALL
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.text = sb

        SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.menu_action_debug_information)
            .setView(textView)
            .setPositiveButton(R.string.button_label_ok, null)
            .show()
    }

    private fun reactivateIdentity(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) return
        App.openAppDialogIdentityDeactivated(ownedIdentity)
    }

    private fun publishDetails(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) return
        AppSingleton.getEngine().publishLatestIdentityDetails(ownedIdentity.bytesOwnedIdentity)
    }

    private fun discardDraft(ownedIdentity: OwnedIdentity?) {
        if (ownedIdentity == null) return
        AppSingleton.getEngine().discardLatestIdentityDetails(ownedIdentity.bytesOwnedIdentity)
    }

    private fun addDevice() {
        App.startTransferFlowAsSource(this)
    }

    private fun trustDevice(device: OwnedDevice) {
        App.runThread {
            AndroidNotificationManager.clearDeviceTrustNotification(device.bytesDeviceUid)
            AppDatabase.getInstance().ownedDeviceDao()
                .updateTrusted(device.bytesOwnedIdentity, device.bytesDeviceUid, true)
        }
    }

    private fun renameDevice(device: OwnedDevice) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_view_message_and_input, null)
        dialogView.findViewById<TextView>(R.id.dialog_message).apply {
            if (device.currentDevice) {
                setText(R.string.dialog_message_rename_current_device)
            } else {
                setText(R.string.dialog_message_rename_other_device)
            }
        }
        val textInputLayout =
            dialogView.findViewById<TextInputLayout>(R.id.dialog_text_layout)
        textInputLayout.setHint(R.string.hint_device_name)
        val deviceNameEditText: EditText = dialogView.findViewById(R.id.dialog_edittext)
        deviceNameEditText.setText(device.displayName)
        deviceNameEditText.inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
        val dialog = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.dialog_title_rename_device)
            .setView(dialogView)
            .setPositiveButton(R.string.menu_action_rename) { _, _ ->
                val nickname: CharSequence? = deviceNameEditText.text
                if (nickname != null) {
                    runCatching {
                        ownedDetailsViewModel.showRefreshSpinner()
                        AppSingleton.getEngine().processDeviceManagementRequest(
                            device.bytesOwnedIdentity,
                            ObvDeviceManagementRequest.createSetNicknameRequest(
                                device.bytesDeviceUid,
                                nickname.toString()
                            )
                        )
                    }.onFailure { Logger.x(it) }
                }
            }
            .apply {
                if (device.currentDevice) setNeutralButton(
                    R.string.button_label_default,
                    null
                )
            }
            .setNegativeButton(R.string.button_label_cancel, null)
            .create()

        dialog.setOnShowListener {
            val ok = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            ok.isEnabled = deviceNameEditText.text.isNotEmpty()
            deviceNameEditText.addTextChangedListener(object : TextChangeListener() {
                override fun afterTextChanged(s: Editable) {
                    ok.isEnabled = s.isNotEmpty()
                }
            })
            val neutral = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
            neutral.setOnClickListener {
                deviceNameEditText.setText(AppSingleton.DEFAULT_DEVICE_DISPLAY_NAME)
                deviceNameEditText.selectAll()
            }
        }
        dialog.show()
    }

    private fun removeDeviceExpiration(device: OwnedDevice) {
        if (device.expirationTimestamp == null) return
        App.runThread {
            val ownedIdentity =
                AppDatabase.getInstance().ownedIdentityDao()[device.bytesOwnedIdentity]
                    ?: return@runThread
            if (!ownedIdentity.hasMultiDeviceApiKeyPermission()) {
                val ownedDevices = AppDatabase.getInstance().ownedDeviceDao()
                    .getAllSync(ownedIdentity.bytesOwnedIdentity)
                val currentlyNotExpiringDevice =
                    ownedDevices.firstOrNull { it.expirationTimestamp == null }
                if (currentlyNotExpiringDevice != null) {
                    runOnUiThread {
                        SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_set_unexpiring_device)
                            .setMessage(
                                getString(
                                    R.string.dialog_message_set_unexpiring_device,
                                    device.getDisplayNameOrDeviceHexName(this),
                                    currentlyNotExpiringDevice.getDisplayNameOrDeviceHexName(this)
                                )
                            )
                            .setPositiveButton(R.string.button_label_proceed) { _, _ ->
                                setUnexpiringDevice(device)
                            }
                            .setNegativeButton(R.string.button_label_cancel, null)
                            .show()
                    }
                    return@runThread
                }
            }
            setUnexpiringDevice(device)
        }
    }

    private fun setUnexpiringDevice(device: OwnedDevice) {
        App.runThread {
            try {
                ownedDetailsViewModel.showRefreshSpinner()
                AppSingleton.getEngine().processDeviceManagementRequest(
                    device.bytesOwnedIdentity,
                    ObvDeviceManagementRequest.createSetUnexpiringDeviceRequest(device.bytesDeviceUid)
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshDeviceList(device: OwnedDevice) {
        App.runThread(OwnedDevicesSynchronisationWithEngineTask(device.bytesOwnedIdentity))
    }

    private fun recreateDeviceChannel(device: OwnedDevice) {
        App.runThread {
            AppSingleton.getEngine().recreateOwnedDeviceChannel(
                device.bytesOwnedIdentity,
                device.bytesDeviceUid
            )
        }
    }

    private fun removeDevice(device: OwnedDevice) {
        SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.dialog_title_remove_device)
            .setMessage(R.string.dialog_message_remove_device)
            .setPositiveButton(R.string.button_label_remove) { _, _ ->
                runCatching {
                    ownedDetailsViewModel.showRefreshSpinner()
                    AppSingleton.getEngine().processDeviceManagementRequest(
                        device.bytesOwnedIdentity,
                        ObvDeviceManagementRequest.createDeactivateDeviceRequest(device.bytesDeviceUid)
                    )
                }
            }
            .setNegativeButton(R.string.button_label_cancel, null)
            .show()
    }

    fun reloadIdentity() {
        ownedDetailsViewModel.reload()
    }
}
