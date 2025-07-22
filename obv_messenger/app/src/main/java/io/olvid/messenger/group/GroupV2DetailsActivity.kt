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
package io.olvid.messenger.group

import android.Manifest.permission
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.containers.GroupV2.Identifier
import io.olvid.engine.encoder.DecodingException
import io.olvid.engine.engine.types.JsonGroupType
import io.olvid.engine.engine.types.identities.ObvGroupV2.ObvGroupV2ChangeSet
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.onBackPressed
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.tasks.GroupCloningTasks
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.fragments.FullScreenImageFragment
import io.olvid.messenger.fragments.dialog.EditNameAndPhotoDialogFragment
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment
import io.olvid.messenger.group.components.GroupUpdatesListener
import io.olvid.messenger.group.components.Routes
import io.olvid.messenger.group.components.addMembers
import io.olvid.messenger.group.components.editAdmins
import io.olvid.messenger.group.components.editGroupDetails
import io.olvid.messenger.group.components.editGroupMembers
import io.olvid.messenger.group.components.fullGroupMembers
import io.olvid.messenger.group.components.groupDetails
import io.olvid.messenger.group.components.groupType
import io.olvid.messenger.group.components.removeMembers
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.owneddetails.SelectDetailsPhotoActivity
import io.olvid.messenger.webrtc.WebrtcCallService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupV2DetailsActivity : LockableActivity() {
    private val groupDetailsViewModel: GroupV2DetailsViewModel by viewModels()
    private val ownedGroupDetailsViewModel: OwnedGroupDetailsViewModel by viewModels()
    private var groupAdmin by mutableStateOf(false)
    private var keycloakGroup by mutableStateOf(false)

    data class MenuItem(
        val action: Action,
        @StringRes val label: Int,
        @DrawableRes val icon: Int? = null,
        val onClick: () -> Unit = {}
    ) {
        enum class Action { CLONE, SYNC, DEBUG, LEAVE, DISBAND }
    }

    private fun getGroupMenu() = buildList {
        add(MenuItem(MenuItem.Action.CLONE, R.string.menu_action_clone_group) { cloneGroup() })
        add(MenuItem(MenuItem.Action.SYNC, R.string.menu_action_sync_group) { syncGroup() })
        add(
            MenuItem(
                MenuItem.Action.DEBUG,
                R.string.menu_action_debug_information
            ) { debugInformation() })
        if (!keycloakGroup) {
            add(MenuItem(MenuItem.Action.LEAVE, R.string.menu_action_leave_group) { leaveGroup() })
        }
        if (groupAdmin) {
            add(
                MenuItem(
                    MenuItem.Action.DISBAND,
                    R.string.menu_action_disband_group
                ) { disbandGroup() })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        super.onCreate(savedInstanceState)
        onBackPressed {
            val fullScreenImageFragment = supportFragmentManager.findFragmentByTag(
                FULL_SCREEN_IMAGE_FRAGMENT_TAG
            )
            if (fullScreenImageFragment != null) {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(0, R.anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit()
            } else {
                finish()
            }
        }
        handleIntent(intent)

        setContentView(R.layout.activity_group_v2_details)
        findViewById<ComposeView>(R.id.compose_view)?.setContent {
            GroupUpdatesListener(
                groupDetailsViewModel = groupDetailsViewModel,
                onUpdate = { detailsAndPhoto ->
                    detailsAndPhoto?.let {
                        groupDetailsViewModel.detailsAndPhotos = detailsAndPhoto
                    } ?: run {
                        groupDetailsViewModel.fetchEngineGroupCards()
                    }
                }
            )
            LaunchedEffect(groupDetailsViewModel.initialGroupType) {
                val initialGroupType =
                    groupDetailsViewModel.initialGroupType ?: groupDetailsViewModel.inferGroupType(
                        groupDetailsViewModel.groupMembers.value ?: emptyList()
                    )
                groupDetailsViewModel.groupType = initialGroupType.clone()
                groupDetailsViewModel.startEditingMembers()
            }
            val navController = rememberNavController()
            val currentDestination by navController.currentBackStackEntryAsState()
            Scaffold(
                containerColor = colorResource(R.color.almostWhite),
                contentColor = colorResource(R.color.almostBlack),
                topBar = {
                    OlvidTopAppBar(
                        titleText = when (currentDestination?.destination?.route) {
                            Routes.GROUP_DETAILS -> stringResource(R.string.activity_title_group_details)
                            Routes.FULL_GROUP_MEMBERS -> stringResource(R.string.label_group_members)
                            Routes.EDIT_GROUP_DETAILS -> stringResource(R.string.dialog_title_edit_group_details)
                            Routes.EDIT_GROUP_MEMBERS -> stringResource(R.string.button_label_edit_group_members)
                            Routes.ADD_GROUP_MEMBERS -> stringResource(R.string.button_label_add_members)
                            Routes.REMOVE_GROUP_MEMBERS -> stringResource(R.string.button_label_remove_members)
                            Routes.EDIT_ADMINS -> stringResource(R.string.label_group_choose_admins)
                            Routes.GROUP_TYPE -> stringResource(R.string.label_group_type)
                            else -> stringResource(R.string.activity_title_group_details)
                        },
                        onBackPressed = onBackPressedDispatcher::onBackPressed,
                        actions = {
                            if (currentDestination?.destination?.route == Routes.GROUP_DETAILS) {
                                IconButton(onClick = {
                                    if (groupAdmin) {
                                        navController.navigate(Routes.EDIT_GROUP_DETAILS)
                                    } else {
                                        groupDetailsViewModel.group.value?.let {
                                            val editNameAndPhotoDialogFragment =
                                                EditNameAndPhotoDialogFragment.newInstance(this@GroupV2DetailsActivity, it)
                                            editNameAndPhotoDialogFragment.show(supportFragmentManager, "dialog")
                                        }
                                    }
                                }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pencil),
                                        tint = colorResource(id = R.color.alwaysWhite),
                                        contentDescription = stringResource(R.string.menu_action_edit_group_details)
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
                                        tint = colorResource(id = R.color.alwaysWhite),
                                        contentDescription = "menu"
                                    )
                                    OlvidDropdownMenu(
                                        expanded = mainMenuOpened,
                                        onDismissRequest = { mainMenuOpened = false }
                                    ) {
                                        getGroupMenu().forEach { menuItem ->
                                            OlvidDropdownMenuItem(
                                                text = stringResource(menuItem.label),
                                                onClick = {
                                                    mainMenuOpened = false
                                                    menuItem.onClick()
                                                },
                                                textColor = if (menuItem.action in listOf(
                                                        MenuItem.Action.LEAVE,
                                                        MenuItem.Action.DISBAND))
                                                    colorResource(R.color.red)
                                                else
                                                    null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }) { contentPadding ->
                Box(modifier = Modifier
                    .padding(top = contentPadding.calculateTopPadding())
                    .cutoutHorizontalPadding()
                    .systemBarsHorizontalPadding()
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.GROUP_DETAILS
                    ) {
                        groupDetails(
                            groupV2DetailsViewModel = groupDetailsViewModel,
                            call = ::call,
                            imageClick = ::onPhotoClicked,
                            onFullMembersList = {
//                                if (groupDetailsViewModel.group.value?.ownPermissionAdmin == true) {
//                                    navController.navigate(Routes.EDIT_GROUP_MEMBERS)
//                                } else {
                                    navController.navigate(Routes.FULL_GROUP_MEMBERS)
//                                }
                            },
                            inviteAllMembers = ::inviteAllMembers,
                            onEditMembers = { navController.navigate(Routes.EDIT_GROUP_MEMBERS) },
                            onGroupType = { navController.navigate(Routes.GROUP_TYPE) },
                            onEditAdmins = {
                                navController.navigate(Routes.EDIT_ADMINS)
                            }
                        )
                        fullGroupMembers(
                            groupV2DetailsViewModel = groupDetailsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                        editGroupDetails(
                            groupV2DetailsViewModel = groupDetailsViewModel,
                            editGroupDetailsViewModel = ownedGroupDetailsViewModel,
                            onTakePicture = ::onTakePicture,
                            onValidate = {
                                ownedGroupDetailsViewModel.publish()
                                navController.popBackStack()
                            }
                        )
                        editGroupMembers(
                            groupV2DetailsViewModel = groupDetailsViewModel,
                            onAddMembers = { navController.navigate(Routes.ADD_GROUP_MEMBERS) },
                            onRemoveMembers = { navController.navigate(Routes.REMOVE_GROUP_MEMBERS) },
                        )
                        addMembers(
                            groupV2DetailsViewModel = groupDetailsViewModel,
                            onValidate = { selectedContacts ->
                                groupDetailsViewModel.startEditingMembers()
                                groupDetailsViewModel.membersAdded(selectedContacts.mapNotNull { it.contact })
                                groupDetailsViewModel.publishGroupEdits()
                                navController.popBackStack(
                                    route = Routes.GROUP_DETAILS,
                                    inclusive = false
                                )
                            }
                        )
                        removeMembers(
                            groupV2DetailsViewModel = groupDetailsViewModel,
                            onValidate = {
                                groupDetailsViewModel.publishGroupEdits()
                                navController.popBackStack(
                                    route = Routes.GROUP_DETAILS,
                                    inclusive = false
                                )
                            }
                        )
                        editAdmins(
                            groupV2DetailsViewModel = groupDetailsViewModel,
                            onValidate = {
                                groupDetailsViewModel.publishGroupEdits()
                                navController.popBackStack()
                            }
                        )
                        groupType(
                            groupV2DetailsViewModel = groupDetailsViewModel,
                            validationLabel = getString(R.string.button_label_publish),
                            onValidate = {
                                publishGroupTypeChanged()
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun publishGroupTypeChanged() {
        groupDetailsViewModel.viewModelScope.launch(Dispatchers.IO) {
            var changed = false
            val obvChangeSet: ObvGroupV2ChangeSet
            if (groupDetailsViewModel.groupTypeChanged()) {
                groupDetailsViewModel.createGroupeTypeChangeSet(groupDetailsViewModel.groupType.toJsonGroupType())
                obvChangeSet = groupDetailsViewModel.getObvChangeSet()
                changed = true
            } else {
                obvChangeSet = ObvGroupV2ChangeSet()
            }
            if (changed) {
                try {
                    AppSingleton.getEngine().initiateGroupV2Update(
                        groupDetailsViewModel.bytesOwnedIdentity,
                        groupDetailsViewModel.bytesGroupIdentifier,
                        obvChangeSet
                    )
                } catch (_: Exception) {
                    App.toast(R.string.toast_message_error_retry, Toast.LENGTH_SHORT)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val group = groupDetailsViewModel.group.value
        App.runThread {
            if (group != null && group.newPublishedDetails == Group2.PUBLISHED_DETAILS_NEW_UNSEEN) {
                group.newPublishedDetails = Group2.PUBLISHED_DETAILS_NEW_SEEN
                AppDatabase.getInstance().group2Dao().updateNewPublishedDetails(
                    group.bytesOwnedIdentity,
                    group.bytesGroupIdentifier,
                    group.newPublishedDetails
                )
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
        val bytesGroupIdentifier = intent.getByteArrayExtra(BYTES_GROUP_IDENTIFIER_INTENT_EXTRA)
        if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
            finish()
            Logger.w("GroupV2DetailsActivity Missing owned identity or group identifier in intent.")
            return
        }
        groupDetailsViewModel.setGroup(bytesOwnedIdentity, bytesGroupIdentifier)
        groupDetailsViewModel.group.observe(this) { group: Group2? -> displayGroup(group) }
        groupDetailsViewModel.fetchEngineGroupCards()
    }

    private fun displayGroup(group: Group2?) {
        if (group == null) {
            finish()
            return
        }
        // everytime the group is updated refresh its initial group type from the engine
        groupDetailsViewModel.initialGroupType = try {
            AppSingleton.getJsonObjectMapper().readValue(
                AppSingleton.getEngine()
                    .getGroupV2JsonType(group.bytesOwnedIdentity, group.bytesGroupIdentifier),
                JsonGroupType::class.java
            ).toGroupCreationModel()
        } catch (_: Exception) {
            null
        }
        groupAdmin = group.ownPermissionAdmin
        keycloakGroup = group.keycloakManaged
    }

    fun call(group: Group2) {
        App.runThread {
            val contacts = AppDatabase.getInstance().group2MemberDao()
                .getGroupMemberContactsSync(
                    group.bytesOwnedIdentity,
                    group.bytesGroupIdentifier
                )
            if (contacts != null) {
                val bytesContactIdentities: ArrayList<BytesKey>?
                if (contacts.size > WebrtcCallService.MAX_GROUP_SIZE_TO_SELECT_ALL_BY_DEFAULT) {
                    bytesContactIdentities = null
                } else {
                    bytesContactIdentities = ArrayList(contacts.size)
                    for (contact in contacts) {
                        bytesContactIdentities.add(BytesKey(contact.bytesContactIdentity))
                    }
                }
                val multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(
                    group.bytesOwnedIdentity,
                    group.bytesGroupIdentifier,
                    bytesContactIdentities
                )
                Handler(Looper.getMainLooper()).post {
                    multiCallStartDialogFragment.show(
                        supportFragmentManager, "dialog"
                    )
                }
            }
        }
    }


    fun onPhotoClicked(photoUrl: String?) {
        photoUrl?.takeIf { it.isNotEmpty() }?.let {
            val fullScreenImageFragment =
                FullScreenImageFragment.newInstance(App.absolutePathFromRelative(photoUrl))
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in, 0)
                .replace(R.id.overlay, fullScreenImageFragment, FULL_SCREEN_IMAGE_FRAGMENT_TAG)
                .commit()
        }
    }

    private fun cloneGroup() {
        groupDetailsViewModel.group.value?.let {
            App.runThread {
                val clonabilityOutput = GroupCloningTasks.getClonability(it)
                Handler(Looper.getMainLooper()).post {
                    GroupCloningTasks.initiateGroupCloningOrWarnUser(
                        this,
                        clonabilityOutput
                    )
                }
            }
        }
    }

    private fun disbandGroup() {
        groupDetailsViewModel.group.value?.let { group ->
            if (group.ownPermissionAdmin) {
                val groupName: String = group.getCustomName().ifEmpty {
                    getString(R.string.text_unnamed_group)
                }
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_disband_group)
                    .setMessage(getString(R.string.dialog_message_disband_group, groupName))
                    .setPositiveButton(R.string.button_label_ok) { _, _ ->
                        if (groupDetailsViewModel.groupMembers.value.isNullOrEmpty()) {
                            // group is empty, just delete it
                            try {
                                AppSingleton.getEngine().disbandGroupV2(
                                    group.bytesOwnedIdentity,
                                    group.bytesGroupIdentifier
                                )
                                App.toast(
                                    R.string.toast_message_group_disbanded,
                                    Toast.LENGTH_SHORT
                                )
                                onBackPressedDispatcher.onBackPressed()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            // group is not empty, second confirmation
                            val confirmationBuilder =
                                SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_disband_group)
                                    .setMessage(
                                        getString(
                                            R.string.dialog_message_disband_non_empty_group_v2_confirmation,
                                            groupName,
                                            groupDetailsViewModel.groupMembers.value?.size ?: 0
                                        )
                                    )
                                    .setPositiveButton(R.string.button_label_ok) { _, _ ->
                                        // disband group
                                        try {
                                            AppSingleton.getEngine().disbandGroupV2(
                                                group.bytesOwnedIdentity,
                                                group.bytesGroupIdentifier
                                            )
                                            App.toast(
                                                R.string.toast_message_group_disbanded,
                                                Toast.LENGTH_SHORT
                                            )
                                            onBackPressedDispatcher.onBackPressed()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    .setNegativeButton(R.string.button_label_cancel, null)
                            confirmationBuilder.create().show()
                        }
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                builder.create().show()
            }
        }
    }

    private fun leaveGroup() {
        groupDetailsViewModel.group.value?.let { group ->
            App.runThread {
                if (group.ownPermissionAdmin && group.updateInProgress != Group2.UPDATE_SYNCING) {
                    // check you are not the only admin (among members, pending members could decline)
                    // only do this check if group update is not in progress: sometimes you can get locked in update and there is no way to leave/disband the group
                    var otherAdmin = false
                    val group2Members = AppDatabase.getInstance().group2MemberDao()
                        .getGroupMembers(group.bytesOwnedIdentity, group.bytesGroupIdentifier)
                    for (group2Member in group2Members) {
                        if (group2Member.permissionAdmin) {
                            otherAdmin = true
                            break
                        }
                    }
                    if (!otherAdmin) {
                        // you are the only admin --> cannot leave the group
                        // check if there is a pending admin to change the error message
                        var pendingAdmin = false
                        val group2PendingMembers =
                            AppDatabase.getInstance().group2PendingMemberDao()
                                .getGroupPendingMembers(
                                    group.bytesOwnedIdentity,
                                    group.bytesGroupIdentifier
                                )
                        for (group2Member in group2PendingMembers) {
                            if (group2Member.permissionAdmin) {
                                pendingAdmin = true
                                break
                            }
                        }
                        val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_unable_to_leave_group)
                            .setPositiveButton(R.string.button_label_ok, null)
                        if (pendingAdmin) {
                            builder.setMessage(R.string.dialog_message_unable_to_leave_group_pending_admin)
                        } else {
                            builder.setMessage(R.string.dialog_message_unable_to_leave_group)
                        }
                        runOnUiThread { builder.create().show() }
                        return@runThread
                    }
                }
                val groupName: String = group.getCustomName().ifEmpty {
                    getString(R.string.text_unnamed_group)
                }
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_leave_group)
                    .setMessage(getString(R.string.dialog_message_leave_group, groupName))
                    .setPositiveButton(R.string.button_label_ok) { _, _ ->
                        try {
                            AppSingleton.getEngine()
                                .leaveGroupV2(
                                    group.bytesOwnedIdentity,
                                    group.bytesGroupIdentifier
                                )
                            App.toast(
                                R.string.toast_message_leaving_group_v2,
                                Toast.LENGTH_SHORT
                            )
                            onBackPressedDispatcher.onBackPressed()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                runOnUiThread { builder.create().show() }
            }
        }
    }

    private fun syncGroup() {
        groupDetailsViewModel.group.value?.let { group ->
            runCatching {
                if (group.keycloakManaged) {
                    KeycloakManager.forceSyncManagedIdentity(group.bytesOwnedIdentity)
                } else {
                    AppSingleton.getEngine()
                        .reDownloadGroupV2(group.bytesOwnedIdentity, group.bytesGroupIdentifier)
                }
            }
        }
    }

    private fun debugInformation() {
        groupDetailsViewModel.group.value?.let { group2 ->
            val sb = StringBuilder()
            sb.append(getString(R.string.debug_label_number_of_members_and_invited)).append(" ")
            sb.append(groupDetailsViewModel.membersCount).append("/")
                .append(groupDetailsViewModel.membersAndPendingCount).append("\n")
            try {
                val version = AppSingleton.getEngine()
                    .getGroupV2Version(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier)
                sb.append(getString(R.string.debug_label_group_version)).append(" ")
                    .append(version).append("\n\n")
            } catch (_: Exception) {  }
            try {
                val groupIdentifier = Identifier.of(group2.bytesGroupIdentifier)
                when (groupIdentifier.category) {
                    Identifier.CATEGORY_SERVER -> {
                        sb.append(getString(R.string.debug_label_group_type)).append(" ")
                            .append(
                                getString(
                                    R.string.debug_label_group_type_user_managed
                                )
                            ).append("\n")
                    }

                    Identifier.CATEGORY_KEYCLOAK -> {
                        sb.append(getString(R.string.debug_label_group_type)).append(" ")
                            .append(
                                getString(
                                    R.string.debug_label_group_type_keycloak
                                )
                            ).append("\n")
                    }
                }
                sb.append(getString(R.string.debug_label_server)).append(" ")
                sb.append(groupIdentifier.serverUrl).append("\n\n")
            } catch (_: DecodingException) { }
            val textView = TextView(this)
            val sixteenDp = (16 * resources.displayMetrics.density).toInt()
            textView.setPadding(sixteenDp, sixteenDp, sixteenDp, sixteenDp)
            textView.setTextIsSelectable(true)
            textView.autoLinkMask = Linkify.WEB_URLS
            textView.movementMethod = LinkMovementMethod.getInstance()
            textView.text = sb
            val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle(R.string.menu_action_debug_information)
                .setView(textView)
                .setPositiveButton(R.string.button_label_ok, null)
            builder.create().show()
        }
    }

    private fun inviteAllMembers() {
        val group = groupDetailsViewModel.group.value ?: return
        if (group.updateInProgress == Group2.UPDATE_NONE) {
            groupDetailsViewModel.groupMembers.value?.mapNotNull { group2MemberOrPending ->
                group2MemberOrPending.contact?.let { contact ->
                    if ((contact.hasChannelOrPreKey() || contact.keycloakManaged) && contact.active && contact.oneToOne.not()) {
                        contact
                    } else {
                        null
                    }
                }
            }?.let { contacts ->
                if (contacts.isEmpty()) {
                    App.toast(R.string.toast_message_no_member_can_be_invited, Toast.LENGTH_SHORT)
                } else {
                    val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_invite_all_group_members)
                        .setMessage(
                            resources.getQuantityString(
                                R.plurals.dialog_message_invite_all_group_members,
                                contacts.size,
                                contacts.size
                            )
                        )
                        .setPositiveButton(R.string.button_label_proceed) { _, _ ->
                            contacts.forEach { contact ->
                                groupDetailsViewModel.invite(contact)
                            }
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)
                    builder.create().show()
                }
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val fullScreenImageFragment = supportFragmentManager.findFragmentByTag(
                FULL_SCREEN_IMAGE_FRAGMENT_TAG
            )
            if (fullScreenImageFragment != null) {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(0, R.anim.fade_out)
                    .remove(fullScreenImageFragment)
                    .commit()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION_CAMERA) {
            try {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePicture(this, ownedGroupDetailsViewModel)
                } else {
                    App.toast(
                        R.string.toast_message_camera_permission_denied,
                        Toast.LENGTH_SHORT
                    )
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_CHOOSE_IMAGE -> {
                if (resultCode == RESULT_OK && data != null) {
                    if (StringUtils.validateUri(data.data)) {
                        startActivityForResult(
                            Intent(
                                null,
                                data.data,
                                App.getContext(),
                                SelectDetailsPhotoActivity::class.java
                            ), REQUEST_CODE_SELECT_ZONE
                        )
                    }
                }
            }

            REQUEST_CODE_TAKE_PICTURE -> {
                if (resultCode == RESULT_OK && ownedGroupDetailsViewModel.takePictureUri != null) {
                    startActivityForResult(
                        Intent(
                            null,
                            ownedGroupDetailsViewModel.takePictureUri,
                            App.getContext(),
                            SelectDetailsPhotoActivity::class.java
                        ), REQUEST_CODE_SELECT_ZONE
                    )
                }
            }

            REQUEST_CODE_SELECT_ZONE -> {
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        val absolutePhotoUrl =
                            data.getStringExtra(SelectDetailsPhotoActivity.CROPPED_JPEG_RETURN_INTENT_EXTRA)
                        if (absolutePhotoUrl != null) {
                            ownedGroupDetailsViewModel.setAbsolutePhotoUrl(absolutePhotoUrl)
                        }
                    }
                }
            }
        }
    }

    private fun onTakePicture() {
        runCatching {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(permission.CAMERA),
                        REQUEST_CODE_PERMISSION_CAMERA
                    )
                } else {
                    takePicture(
                        activity = this@GroupV2DetailsActivity,
                        viewModel = ownedGroupDetailsViewModel
                    )
                }
            } else {
                App.toast(
                    R.string.toast_message_device_has_no_camera,
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun takePicture(activity: AppCompatActivity, viewModel: OwnedGroupDetailsViewModel) {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        viewModel.takePictureUri = null
        if (takePictureIntent.resolveActivity(activity.packageManager) != null) {
            val photoDir = File(activity.cacheDir, App.CAMERA_PICTURE_FOLDER)
            val photoFile = File(
                photoDir,
                SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(
                    Date()
                ) + ".jpg"
            )
            try {
                photoDir.mkdirs()
                if (!photoFile.createNewFile()) {
                    return
                }
                val photoUri = FileProvider.getUriForFile(
                    activity,
                    BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                    photoFile
                )
                viewModel.takePictureUri = photoUri
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                App.startActivityForResult(
                    activity,
                    takePictureIntent,
                    REQUEST_CODE_TAKE_PICTURE
                )
            } catch (_: IOException) {
                Logger.w("Error creating photo capture file $photoFile")
            }
        }
    }

    companion object {
        const val BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity"
        const val BYTES_GROUP_IDENTIFIER_INTENT_EXTRA = "group_identifier"
        const val FULL_SCREEN_IMAGE_FRAGMENT_TAG = "full_screen_image"
        const val REQUEST_CODE_PERMISSION_CAMERA = 8511
        const val REQUEST_CODE_CHOOSE_IMAGE = 8596
        const val REQUEST_CODE_TAKE_PICTURE = 8597
        const val REQUEST_CODE_SELECT_ZONE = 8598
    }
}

