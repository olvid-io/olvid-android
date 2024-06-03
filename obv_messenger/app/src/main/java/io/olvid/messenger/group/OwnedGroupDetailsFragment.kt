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

package io.olvid.messenger.group

import android.Manifest.permission
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import com.google.android.material.textfield.TextInputLayout
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonGroupType
import io.olvid.messenger.App
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.group.GroupTypeModel.CustomGroup
import io.olvid.messenger.group.GroupTypeModel.GroupType.CUSTOM
import io.olvid.messenger.group.GroupTypeModel.PrivateGroup
import io.olvid.messenger.group.GroupTypeModel.ReadOnlyGroup
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting
import io.olvid.messenger.group.GroupTypeModel.SimpleGroup
import io.olvid.messenger.group.OwnedGroupDetailsViewModel.InitialViewContent
import io.olvid.messenger.group.components.GroupTypeSelection
import io.olvid.messenger.owneddetails.SelectDetailsPhotoActivity
import io.olvid.messenger.settings.SettingsActivity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OwnedGroupDetailsFragment : Fragment() {

    private val viewModel: OwnedGroupDetailsViewModel by activityViewModels()
    private val groupV2DetailsViewModel: GroupV2DetailsViewModel by activityViewModels()
    private val groupCreationViewModel: GroupCreationViewModel by activityViewModels()
    private lateinit var groupNameLayout: TextInputLayout
    private lateinit var initialView: InitialView
    private lateinit var groupTypeView: ComposeView

    private var useDialogBackground = false
    private var hidePersonalNote = false
    private lateinit var initialGroupType : GroupTypeModel
    private lateinit var initialCustomGroup : CustomGroup
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_group_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        groupNameLayout = view.findViewById(R.id.group_details_group_name_layout)
        groupNameLayout.isErrorEnabled = !viewModel.isGroupV2()

        val groupNameEditText = view.findViewById<EditText>(R.id.group_details_group_name)
        val groupDescriptionEditText =
            view.findViewById<EditText>(R.id.group_details_group_description)
        val personalNoteEditText = view.findViewById<EditText>(R.id.personal_note_edit_text)
        val cameraIcon = view.findViewById<ImageView>(R.id.camera_icon)
        if (useDialogBackground) {
            cameraIcon.setImageResource(R.drawable.ic_camera_bordered_dialog)
        }
        if (hidePersonalNote) {
            view.findViewById<View>(R.id.personal_note_group).visibility = View.GONE
        }
        initialView = view.findViewById(R.id.group_details_initial_view)
        if (SettingsActivity.useKeyboardIncognitoMode()) {
            groupNameEditText.imeOptions =
                groupNameEditText.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            groupDescriptionEditText.imeOptions =
                groupDescriptionEditText.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            personalNoteEditText.imeOptions =
                groupDescriptionEditText.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        viewModel.valid.observe(viewLifecycleOwner, object : Observer<Boolean> {
            var first = true
            override fun onChanged(value: Boolean) {
                if (first) {
                    first = false
                    return
                }
                if (!value) {
                    groupNameLayout.error = getString(R.string.message_error_group_name_empty)
                } else {
                    groupNameLayout.error = null
                }
            }
        })
        groupNameEditText.setText(viewModel.getGroupName())
        groupNameEditText.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                viewModel.setGroupName(s.toString())
            }
        })
        groupDescriptionEditText.setText(viewModel.groupDescription)
        groupDescriptionEditText.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                viewModel.groupDescription = s.toString()
            }
        })
        personalNoteEditText.setText(viewModel.personalNote)
        personalNoteEditText.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                viewModel.personalNote = s.toString()
            }
        })

        if (viewModel.isGroupV2()) {
            groupV2DetailsViewModel.getGroupTypeLiveData().value ifNull {
                groupV2DetailsViewModel.setGroupType(initialGroupType)
            }

            groupTypeView = view.findViewById(R.id.group_type)
            groupTypeView.setContent {
                val selectedGroupType by groupV2DetailsViewModel.getGroupTypeLiveData().observeAsState()
                AppCompatTheme {
                    GroupTypeSelection(
                            groupTypes = listOf(SimpleGroup, PrivateGroup, ReadOnlyGroup, initialCustomGroup),
                            selectedGroupType = selectedGroupType
                                    ?: initialGroupType, // the selectedGroupType should always be non null as we set it just before
                            selectGroupType = {
                                groupV2DetailsViewModel.setGroupType(it)
                                groupCreationViewModel.setIsCustomGroup(it.type == CUSTOM)
                            },
                            isEditingCustomSettings = if (hidePersonalNote.not() && selectedGroupType is CustomGroup) {
                                { isEditing ->
                                    groupV2DetailsViewModel.isEditingGroupCustomSettingsLiveData.postValue(isEditing)
                                    (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(groupTypeView.windowToken, 0)
                                }
                            } else null)
                }
            }
        }

        viewModel.getInitialViewContent()
            .observe(viewLifecycleOwner) { initialViewContent: InitialViewContent? ->
                initialViewContent?.let {
                    if (initialViewContent.absolutePhotoUrl != null) {
                        initialView.setAbsolutePhotoUrl(
                            initialViewContent.bytesGroupOwnerAndUid,
                            initialViewContent.absolutePhotoUrl
                        )
                    } else {
                        initialView.setGroup(initialViewContent.bytesGroupOwnerAndUid)
                    }
                }
            }
        initialView.setOnClickListener { v: View ->
            val popup = PopupMenu(initialView.context, initialView)
            if (viewModel.getAbsolutePhotoUrl() != null) {
                popup.inflate(R.menu.popup_details_photo_with_clear)
            } else {
                popup.inflate(R.menu.popup_details_photo)
            }
            popup.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.popup_action_remove_image -> {
                        viewModel.setAbsolutePhotoUrl(null)
                    }

                    R.id.popup_action_choose_image -> {
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                            .setType("image/*")
                            .addCategory(Intent.CATEGORY_OPENABLE)
                        App.startActivityForResult(this, intent, REQUEST_CODE_CHOOSE_IMAGE)
                    }

                    R.id.popup_action_take_picture -> {
                        try {
                            if (v.context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                                if (ContextCompat.checkSelfPermission(
                                        requireActivity(),
                                        permission.CAMERA
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestPermissions(
                                        arrayOf(permission.CAMERA),
                                        REQUEST_CODE_PERMISSION_CAMERA
                                    )
                                } else {
                                    takePicture()
                                }
                            } else {
                                App.toast(
                                    R.string.toast_message_device_has_no_camera,
                                    Toast.LENGTH_SHORT
                                )
                            }
                        } catch (e: IllegalStateException) {
                            e.printStackTrace()
                        }
                    }
                }
                true
            }
            popup.show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSION_CAMERA) {
            try {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePicture()
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
        when (requestCode) {
            REQUEST_CODE_CHOOSE_IMAGE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
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
                if (resultCode == Activity.RESULT_OK && viewModel.takePictureUri != null) {
                    startActivityForResult(
                        Intent(
                            null,
                            viewModel.takePictureUri,
                            App.getContext(),
                            SelectDetailsPhotoActivity::class.java
                        ), REQUEST_CODE_SELECT_ZONE
                    )
                }
            }

            REQUEST_CODE_SELECT_ZONE -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        val absolutePhotoUrl =
                            data.getStringExtra(SelectDetailsPhotoActivity.CROPPED_JPEG_RETURN_INTENT_EXTRA)
                        if (absolutePhotoUrl != null) {
                            viewModel.setAbsolutePhotoUrl(absolutePhotoUrl)
                        }
                    }
                }
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun takePicture() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        viewModel.takePictureUri = null
        if (takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
            val photoDir = File(requireActivity().cacheDir, App.CAMERA_PICTURE_FOLDER)
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
                    requireActivity(),
                    BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                    photoFile
                )
                viewModel.takePictureUri = photoUri
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                App.startActivityForResult(
                    this,
                    takePictureIntent,
                    REQUEST_CODE_TAKE_PICTURE
                )
            } catch (e: IOException) {
                Logger.w("Error creating photo capture file $photoFile")
            }
        }
    }

    fun setUseDialogBackground(useDialogBackground: Boolean) {
        this.useDialogBackground = useDialogBackground
    }

    fun setInitialGroupType(initialGroupType : GroupTypeModel) {
        this.initialGroupType = initialGroupType
        this.initialCustomGroup = when(initialGroupType) {
            is CustomGroup -> initialGroupType.clone() as CustomGroup
            ReadOnlyGroup -> CustomGroup(readOnlySetting = true)
            else -> CustomGroup()
        }
    }

    fun setHidePersonalNote(hidePersonalNote: Boolean) {
        this.hidePersonalNote = hidePersonalNote
    }

    companion object {
        private const val REQUEST_CODE_PERMISSION_CAMERA = 8511
        private const val REQUEST_CODE_CHOOSE_IMAGE = 8596
        private const val REQUEST_CODE_TAKE_PICTURE = 8597
        private const val REQUEST_CODE_SELECT_ZONE = 8598
    }
}

fun JsonGroupType.toGroupCreationModel(): GroupTypeModel {
    return when (type) {
        JsonGroupType.TYPE_SIMPLE -> SimpleGroup
        JsonGroupType.TYPE_PRIVATE -> PrivateGroup
        JsonGroupType.TYPE_READ_ONLY -> ReadOnlyGroup
        JsonGroupType.TYPE_CUSTOM -> CustomGroup(
            readOnlySetting = readOnly ?: false,
            remoteDeleteSetting = when(remoteDelete) {
                JsonGroupType.REMOTE_DELETE_NOBODY -> RemoteDeleteSetting.NOBODY
                JsonGroupType.REMOTE_DELETE_ADMINS -> RemoteDeleteSetting.ADMINS
                JsonGroupType.REMOTE_DELETE_EVERYONE -> RemoteDeleteSetting.EVERYONE
                else -> RemoteDeleteSetting.NOBODY
            })
        else -> SimpleGroup
    }
}