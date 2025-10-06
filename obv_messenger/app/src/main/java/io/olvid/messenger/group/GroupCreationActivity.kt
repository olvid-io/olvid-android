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
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.olvid.engine.Logger
import io.olvid.engine.datatypes.containers.GroupV2.Permission
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.JsonGroupDetails
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto
import io.olvid.engine.engine.types.JsonGroupType
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.engine.engine.types.identities.ObvGroup
import io.olvid.engine.engine.types.identities.ObvGroupV2
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.onBackPressed
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.group.GroupTypeModel.GroupType
import io.olvid.messenger.group.GroupV2DetailsActivity.Companion.REQUEST_CODE_CHOOSE_IMAGE
import io.olvid.messenger.group.GroupV2DetailsActivity.Companion.REQUEST_CODE_PERMISSION_CAMERA
import io.olvid.messenger.group.GroupV2DetailsActivity.Companion.REQUEST_CODE_SELECT_ZONE
import io.olvid.messenger.group.GroupV2DetailsActivity.Companion.REQUEST_CODE_TAKE_PICTURE
import io.olvid.messenger.group.components.MembersRow
import io.olvid.messenger.group.components.Routes
import io.olvid.messenger.group.components.addMembers
import io.olvid.messenger.group.components.chooseNewGroupAdmins
import io.olvid.messenger.group.components.editGroupDetails
import io.olvid.messenger.group.components.groupType
import io.olvid.messenger.group.components.toGroupMember
import io.olvid.messenger.owneddetails.SelectDetailsPhotoActivity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupCreationActivity : LockableActivity() {
    private val groupCreationViewModel: GroupCreationViewModel by viewModels()
    private val ownedGroupDetailsViewModel: OwnedGroupDetailsViewModel by viewModels()
    private val groupV2DetailsViewModel: GroupV2DetailsViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            ownedGroupDetailsViewModel.setBytesGroupOwnerAndUidOrIdentifier(ByteArray(0))

            // only look at intent when first creating the activity
            val intent = intent
            ownedGroupDetailsViewModel.setGroupV2(CREATE_GROUPS_AS_V2)
            if (intent.hasExtra(SERIALIZED_GROUP_DETAILS_INTENT_EXTRA)) {
                ownedGroupDetailsViewModel.cloning.value = true
                try {
                    val groupDetails = AppSingleton.getJsonObjectMapper().readValue(
                        intent.getStringExtra(SERIALIZED_GROUP_DETAILS_INTENT_EXTRA),
                        JsonGroupDetails::class.java
                    )
                    if (groupDetails.name.isNullOrEmpty().not()) {
                        ownedGroupDetailsViewModel.setGroupName(getString(R.string.text_copy_of_prefix) + groupDetails.name)
                    }
                    ownedGroupDetailsViewModel.groupDescription = groupDetails.description
                } catch (_: Exception) { }
            }
            if (intent.hasExtra(SERIALIZED_GROUP_TYPE_INTENT_EXTRA)) {
                try {
                    val groupType = AppSingleton.getJsonObjectMapper().readValue(
                        intent.getStringExtra(SERIALIZED_GROUP_TYPE_INTENT_EXTRA),
                        JsonGroupType::class.java
                    )
                    if (groupType != null) {
                        groupV2DetailsViewModel.groupType = groupType.toGroupCreationModel()
                    }
                } catch (_: Exception) { }
            }
            if (intent.hasExtra(ABSOLUTE_PHOTO_URL_INTENT_EXTRA)) {
                ownedGroupDetailsViewModel.cloning.value = true
                ownedGroupDetailsViewModel.setAbsolutePhotoUrl(
                    intent.getStringExtra(
                        ABSOLUTE_PHOTO_URL_INTENT_EXTRA
                    )
                )
            }
            groupCreationViewModel.admins.value =
                GroupClone.preselectedGroupAdminMembers.toHashSet()
            groupCreationViewModel.selectedContacts.value =
                GroupClone.preselectedGroupMembers + GroupClone.preselectedGroupAdminMembers
            GroupClone.clear()
        }

        onBackPressed {
            finish()
        }

        setContent {
            val navController = rememberNavController()
            val currentDestination by navController.currentBackStackEntryAsState()
            Scaffold(
                containerColor = colorResource(R.color.almostWhite),
                contentColor = colorResource(R.color.almostBlack),
                topBar = {
                    OlvidTopAppBar(
                        titleText = when (currentDestination?.destination?.route) {
                            Routes.EDIT_GROUP_DETAILS -> stringResource(R.string.dialog_title_edit_group_details)
                            Routes.ADD_GROUP_MEMBERS -> stringResource(R.string.button_label_new_group)
                            Routes.CHOOSE_NEW_GROUP_ADMINS -> stringResource(R.string.label_group_choose_admins)
                            Routes.GROUP_TYPE -> stringResource(R.string.label_group_type)
                            else -> stringResource(R.string.activity_title_group_details)
                        },
                        onBackPressed = onBackPressedDispatcher::onBackPressed
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
                        startDestination = Routes.ADD_GROUP_MEMBERS
                    ) {
                        addMembers(
                            groupV2DetailsViewModel = groupV2DetailsViewModel,
                            groupCreationViewModel = groupCreationViewModel,
                            onValidate = {
                                if (it.isEmpty()) {
                                    groupCreationViewModel.selectedContacts.value = emptyList()
                                    navController.navigate(
                                        route = Routes.EDIT_GROUP_DETAILS
                                    )
                                } else {
                                    groupCreationViewModel.selectedContacts.value =
                                        it.mapNotNull { it.contact }
                                    navController.navigate(
                                        route = Routes.GROUP_TYPE
                                    )
                                }
                            })
                        groupType(
                            groupV2DetailsViewModel = groupV2DetailsViewModel,
                            content = {
                                groupCreationViewModel.selectedContacts.value.takeIf { it.isNotEmpty() }?.let {
                                    Column {
                                        Text(
                                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                                            text = pluralStringResource(
                                                R.plurals.other_members_count,
                                                it.size,
                                                it.size
                                            ),
                                            style = OlvidTypography.h3.copy(color = colorResource(R.color.almostBlack)),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        MembersRow(members = it.map { it.toGroupMember() })
                                    }
                                }
                            },
                            showTitle = true,
                            isGroupCreation = true,
                            validationLabel = getString(R.string.button_label_next),
                            onBack = { navController.popBackStack() },
                            onValidate = {
                                if (groupV2DetailsViewModel.groupType.type != GroupType.SIMPLE) {
                                   navController.navigate(
                                        route = Routes.CHOOSE_NEW_GROUP_ADMINS,
                                    )
                                } else {
                                    navController.navigate(
                                        route = Routes.EDIT_GROUP_DETAILS
                                    )
                                }
                            })
                        chooseNewGroupAdmins(
                            groupCreationViewModel = groupCreationViewModel,
                            onValidate = {
                                navController.navigate(
                                    route = Routes.EDIT_GROUP_DETAILS
                                )
                            })
                        editGroupDetails(
                            groupV2DetailsViewModel = groupV2DetailsViewModel,
                            editGroupDetailsViewModel = ownedGroupDetailsViewModel,
                            onTakePicture = ::onTakePicture,
                            isGroupCreation = true,
                            isGroupV2 = CREATE_GROUPS_AS_V2,
                            content = {
                                groupCreationViewModel.selectedContacts.value.takeIf { it.isNotEmpty() }?.let {
                                    Column {
                                        Text(
                                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                                            text = pluralStringResource(
                                                R.plurals.other_members_count,
                                                it.size,
                                                it.size
                                            ),
                                            style = OlvidTypography.h3.copy(color = colorResource(R.color.almostBlack)),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        MembersRow(members = it.map { it.toGroupMember() })
                                    }
                                }
                            },
                            onBack = { navController.popBackStack() },
                            onValidate = {
                                onValidateGroupCreation()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun onValidateGroupCreation() {
        if (groupCreationViewModel.selectedContacts.value.isEmpty()) {
            val builder = SecureAlertDialogBuilder(this@GroupCreationActivity, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_create_empty_group)
                .setMessage(R.string.dialog_message_create_empty_group)
                .setPositiveButton(R.string.button_label_ok) { _, _ ->
                    initiateGroupCreationProtocol()
                }
                .setNegativeButton(R.string.button_label_cancel, null)
            builder.create().show()
        } else {
            initiateGroupCreationProtocol()
        }
    }

    private fun initiateGroupCreationProtocol() {
        // uncomment to create a V1 group
        // ownedGroupDetailsViewModel.setGroupV2(false)
        if (ownedGroupDetailsViewModel.isGroupV2()) {
            val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity() ?: return

            val groupAbsolutePhotoUrl = ownedGroupDetailsViewModel.getAbsolutePhotoUrl()
            val groupName = ownedGroupDetailsViewModel.groupName
            val groupDescription = ownedGroupDetailsViewModel.groupDescription?.trim()

            val jsonGroupDetails = JsonGroupDetails(groupName.value?.trim(), groupDescription)
            val groupType = groupV2DetailsViewModel.groupType
            val otherGroupMembers = HashMap<ObvBytesKey, HashSet<Permission>>()
            for (contact in groupCreationViewModel.selectedContacts.value) {
                val permissions = groupV2DetailsViewModel.getPermissions(
                    groupType,
                    groupCreationViewModel.admins.value?.contains(contact) == true
                )
                otherGroupMembers[ObvBytesKey(contact.bytesContactIdentity)] = permissions
            }
            try {
                val serializedGroupDetails =
                    AppSingleton.getJsonObjectMapper().writeValueAsString(jsonGroupDetails)
                val serializedGroupType = AppSingleton.getJsonObjectMapper()
                    .writeValueAsString((groupType).toJsonGroupType())

                val ownPermissions = Permission.DEFAULT_ADMIN_PERMISSIONS.toHashSet().apply {
                    if (groupType is CustomGroup && groupType.remoteDeleteSetting != GroupTypeModel.RemoteDeleteSetting.NOBODY)
                        this.add(Permission.REMOTE_DELETE_ANYTHING)
                }
                AppSingleton.getEngine().startGroupV2CreationProtocol(
                    serializedGroupDetails,
                    groupAbsolutePhotoUrl,
                    bytesOwnedIdentity,
                    ownPermissions,
                    otherGroupMembers,
                    serializedGroupType
                )
                AppSingleton.getEngine().addNotificationListener(
                    EngineNotifications.GROUP_V2_CREATED_OR_UPDATED,
                    object : EngineNotificationListener {
                        private var registrationNumber: Long? = null
                        private val engineNotificationListener: EngineNotificationListener = this

                        init {
                            Thread {
                                try {
                                    Thread.sleep(3000)
                                } catch (_: InterruptedException) {
                                    Logger.i("Group creation listener timer interrupted")
                                }
                                AppSingleton.getEngine().removeNotificationListener(
                                    EngineNotifications.GROUP_CREATED,
                                    engineNotificationListener
                                )
                            }.start()
                        }

                        override fun callback(
                            notificationName: String,
                            userInfo: HashMap<String, Any>
                        ) {
                            AppSingleton.getEngine().removeNotificationListener(
                                EngineNotifications.GROUP_V2_CREATED_OR_UPDATED,
                                this
                            )
                            val group =
                                userInfo[EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY] as ObvGroupV2?

                            group?.let {
                                runOnUiThread {
                                    App.openGroupV2DetailsActivity(
                                        this@GroupCreationActivity,
                                        group.bytesOwnedIdentity,
                                        group.groupIdentifier.bytes
                                    )
                                    finish()
                                }
                            }
                        }

                        override fun setEngineNotificationListenerRegistrationNumber(
                            registrationNumber: Long
                        ) {
                            this.registrationNumber = registrationNumber
                        }

                        override fun getEngineNotificationListenerRegistrationNumber(): Long {
                            return registrationNumber ?: 0
                        }

                        override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
                            return registrationNumber != null
                        }
                    })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity() ?: return
            val selectedContacts = groupCreationViewModel.selectedContacts.value

            val groupAbsolutePhotoUrl = ownedGroupDetailsViewModel.getAbsolutePhotoUrl()
            val groupName = ownedGroupDetailsViewModel.groupName
            val groupDescription = ownedGroupDetailsViewModel.groupDescription?.trim()
            if (groupName.value?.trim().isNullOrEmpty()) {
                return
            }

            val jsonGroupDetailsWithVersionAndPhoto = JsonGroupDetailsWithVersionAndPhoto()
            jsonGroupDetailsWithVersionAndPhoto.version = 0
            jsonGroupDetailsWithVersionAndPhoto.groupDetails =
                JsonGroupDetails(groupName.value?.trim(), groupDescription)
            val bytesContactIdentities = arrayOfNulls<ByteArray>(selectedContacts.size)
            for ((i, contact) in selectedContacts.withIndex()) {
                bytesContactIdentities[i] = contact.bytesContactIdentity
            }
            try {
                val serializedGroupDetailsWithVersionAndPhoto =
                    AppSingleton.getJsonObjectMapper()
                        .writeValueAsString(jsonGroupDetailsWithVersionAndPhoto)
                AppSingleton.getEngine().startGroupCreationProtocol(
                    serializedGroupDetailsWithVersionAndPhoto,
                    groupAbsolutePhotoUrl,
                    bytesOwnedIdentity,
                    bytesContactIdentities
                )
                AppSingleton.getEngine().addNotificationListener(
                    EngineNotifications.GROUP_CREATED,
                    object : EngineNotificationListener {
                        private var registrationNumber: Long? = null
                        private val engineNotificationListener: EngineNotificationListener =
                            this

                        init {
                            Thread {
                                try {
                                    Thread.sleep(3000)
                                } catch (_: InterruptedException) {
                                    Logger.i("Group creation listener timer interrupted")
                                }
                                AppSingleton.getEngine().removeNotificationListener(
                                    EngineNotifications.GROUP_CREATED,
                                    engineNotificationListener
                                )
                            }.start()
                        }

                        override fun callback(
                            notificationName: String,
                            userInfo: HashMap<String, Any>
                        ) {
                            AppSingleton.getEngine()
                                .removeNotificationListener(
                                    EngineNotifications.GROUP_CREATED,
                                    this
                                )
                            val group =
                                userInfo[EngineNotifications.GROUP_CREATED_GROUP_KEY] as ObvGroup?
                            if (group != null) {
                                runOnUiThread {
                                    App.openGroupDetailsActivity(
                                        this@GroupCreationActivity,
                                        group.bytesOwnedIdentity,
                                        group.bytesGroupOwnerAndUid
                                    )
                                    finish()
                                }
                            }
                        }

                        override fun setEngineNotificationListenerRegistrationNumber(
                            registrationNumber: Long
                        ) {
                            this.registrationNumber = registrationNumber
                        }

                        override fun getEngineNotificationListenerRegistrationNumber(): Long {
                            return registrationNumber ?: 0
                        }

                        override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
                            return registrationNumber != null
                        }
                    })
            } catch (e: Exception) {
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
                        activity = this@GroupCreationActivity,
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
        // this boolean controls whether groups are created in v2 format or not
        const val CREATE_GROUPS_AS_V2 = true
        const val ABSOLUTE_PHOTO_URL_INTENT_EXTRA =
            "photo_url" // String with absolute path to photo
        const val SERIALIZED_GROUP_DETAILS_INTENT_EXTRA =
            "serialized_group_details" // String with serialized JsonGroupDetails
        const val SERIALIZED_GROUP_TYPE_INTENT_EXTRA =
            "serialized_group_type" // String with serialized JsonGroupType
    }
}