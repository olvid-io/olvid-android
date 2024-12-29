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
package io.olvid.messenger.owneddetails

import android.Manifest.permission
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.android.material.textfield.TextInputLayout
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonKeycloakUserDetails
import io.olvid.messenger.App
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.fragments.dialog.HiddenProfilePasswordCreationDialogFragment
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.InitialViewContent
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.INVALID
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.Companion.computePINHash
import io.olvid.messenger.settings.SettingsActivity.Companion.useKeyboardIncognitoMode
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OwnedIdentityDetailsFragment : Fragment() {
    private val viewModel: OwnedIdentityDetailsViewModel by activityViewModels()

    private var firstNameLayout: TextInputLayout? = null
    private var lastNameLayout: TextInputLayout? = null
    private var errorTextView: TextView? = null
    private var initialView: InitialView? = null
    private var hiddenProfileCheckbox: CheckBox? = null

    private var useDialogBackground = false
    private var lockedUserDetails: JsonKeycloakUserDetails? = null
    private var lockPicture = false
    private var showNicknameAndHidden = false
    private var disableHidden = false

    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult<String, Boolean>(
            RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                takePicture()
            } else {
                App.toast(
                    R.string.toast_message_camera_permission_denied,
                    Toast.LENGTH_SHORT
                )
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_owned_identity_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firstNameLayout = view.findViewById(R.id.identity_details_first_name_layout)
        lastNameLayout = view.findViewById(R.id.identity_details_last_name_layout)
        errorTextView = view.findViewById(R.id.identity_details_error)
        val firstNameEditText = view.findViewById<EditText>(R.id.identity_details_first_name)
        val lastNameEditText = view.findViewById<EditText>(R.id.identity_details_last_name)
        val companyEditText = view.findViewById<EditText>(R.id.identity_details_company)
        val positionEditText = view.findViewById<EditText>(R.id.identity_details_position)
        val nicknameEditText = view.findViewById<EditText>(R.id.identity_details_nickname)
        hiddenProfileCheckbox = view.findViewById(R.id.hidden_profile_checkbox)
        val cameraIcon = view.findViewById<ImageView>(R.id.camera_icon)

        if (useDialogBackground) {
            cameraIcon.setImageResource(R.drawable.ic_camera_bordered_dialog)
        }
        lockedUserDetails?.let { lockedUserDetails ->
            viewModel.pictureLocked = lockPicture
            viewModel.detailsLocked = true
            viewModel.firstName = lockedUserDetails.firstName
            viewModel.lastName = lockedUserDetails.lastName
            viewModel.company = lockedUserDetails.company
            viewModel.position = lockedUserDetails.position
        } ?: run {
            // do not setDetailsLock(false) as details may be locked voluntarily (typically when editing keycloak managed details)
            viewModel.pictureLocked = false
        }
        if (showNicknameAndHidden) {
            nicknameEditText.visibility = View.VISIBLE
            hiddenProfileCheckbox?.visibility = View.VISIBLE
        } else {
            nicknameEditText.visibility = View.GONE
            hiddenProfileCheckbox?.visibility = View.GONE
        }
        if (disableHidden) {
            hiddenProfileCheckbox?.setOnClickListener {
                if (!viewModel.isProfileHidden) {
                    App.toast(
                        R.string.toast_message_must_have_one_visible_profile,
                        Toast.LENGTH_SHORT,
                        Gravity.CENTER
                    )
                }
                hiddenProfileCheckbox?.isChecked = false
                viewModel.isProfileHidden = false
            }
        } else {
            hiddenProfileCheckbox?.setOnClickListener {
                this.hiddenCheckboxClicked()
            }
        }

        if (viewModel.pictureLocked) {
            cameraIcon.visibility = View.GONE
        } else {
            cameraIcon.visibility = View.VISIBLE
        }
        if (viewModel.detailsLocked) {
            firstNameEditText.isEnabled = false
            lastNameEditText.isEnabled = false
            companyEditText.isEnabled = false
            positionEditText.isEnabled = false
        } else {
            firstNameEditText.isEnabled = true
            lastNameEditText.isEnabled = true
            companyEditText.isEnabled = true
            positionEditText.isEnabled = true
        }
        initialView = view.findViewById(R.id.identity_details_initial_view)
        initialView?.setKeycloakCertified(viewModel.detailsLocked)
        initialView?.setInactive(viewModel.isIdentityInactive)

        if (useKeyboardIncognitoMode()) {
            firstNameEditText.imeOptions =
                firstNameEditText.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            lastNameEditText.imeOptions =
                lastNameEditText.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            companyEditText.imeOptions =
                companyEditText.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            positionEditText.imeOptions =
                positionEditText.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            nicknameEditText.imeOptions =
                positionEditText.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        }

        viewModel.valid.observe(viewLifecycleOwner, object : Observer<ValidStatus?> {
            var first: Boolean = true

            override fun onChanged(value: ValidStatus?) {
                if (first) {
                    first = false
                    return
                }
                if (value == null || value == INVALID) {
                    firstNameLayout?.setError(" ")
                    lastNameLayout?.setError(" ")
                    errorTextView?.setText(R.string.message_error_first_or_last_name_needed)
                } else {
                    firstNameLayout?.setError(null)
                    lastNameLayout?.setError(null)
                    errorTextView?.text = null
                }
            }
        })

        firstNameEditText.setText(viewModel.firstName)
        if (viewModel.detailsLocked && (viewModel.firstName.isNullOrEmpty())) {
            firstNameEditText.setText(" ")
        }
        firstNameEditText.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                viewModel.firstName = s.toString()
            }
        })

        lastNameEditText.setText(viewModel.lastName)
        if (viewModel.detailsLocked && (viewModel.lastName.isNullOrEmpty())) {
            lastNameEditText.setText(" ")
        }
        lastNameEditText.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                viewModel.lastName = s.toString()
            }
        })

        companyEditText.setText(viewModel.company)
        if (viewModel.detailsLocked && (viewModel.company.isNullOrEmpty())) {
            companyEditText.setText(" ")
        }
        companyEditText.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                viewModel.company = s.toString()
            }
        })

        positionEditText.setText(viewModel.position)
        if (viewModel.detailsLocked && (viewModel.position.isNullOrEmpty())) {
            positionEditText.setText(" ")
        }
        positionEditText.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                viewModel.position = s.toString()
            }
        })

        nicknameEditText.setText(viewModel.nickname)
        nicknameEditText.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                viewModel.nickname = s.toString()
            }
        })
        hiddenProfileCheckbox?.isChecked = viewModel.isProfileHidden

        viewModel.initialViewContent.observe(
            viewLifecycleOwner
        ) { initialViewContent: InitialViewContent? ->
            if (initialViewContent == null) {
                return@observe
            }
            if (initialViewContent.absolutePhotoUrl != null) {
                initialView?.setAbsolutePhotoUrl(
                    initialViewContent.bytesOwnedIdentity,
                    initialViewContent.absolutePhotoUrl
                )
            } else {
                initialView?.setInitial(
                    initialViewContent.bytesOwnedIdentity,
                    initialViewContent.initial
                )
            }
        }

        initialView?.setOnClickListener { v: View ->
            if (viewModel.pictureLocked) {
                return@setOnClickListener
            }
            val popup = PopupMenu(v.context, initialView!!)
            if (viewModel.absolutePhotoUrl != null) {
                popup.inflate(R.menu.popup_details_photo_with_clear)
            } else {
                popup.inflate(R.menu.popup_details_photo)
            }
            popup.setOnMenuItemClickListener { item: MenuItem ->
                val itemId = item.itemId
                when (itemId) {
                    R.id.popup_action_remove_image -> {
                        viewModel.absolutePhotoUrl = null
                    }

                    R.id.popup_action_choose_image -> {
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                            .setType("image/*")
                            .addCategory(Intent.CATEGORY_OPENABLE)
                        App.startActivityForResult(
                            this,
                            intent,
                            REQUEST_CODE_CHOOSE_IMAGE
                        )
                    }

                    R.id.popup_action_take_picture -> {
                        try {
                            if (v.context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                                if (ContextCompat.checkSelfPermission(
                                        requireActivity(),
                                        permission.CAMERA
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestPermissionLauncher.launch(permission.CAMERA)
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

    private fun hiddenCheckboxClicked() {
        if (viewModel.isProfileHidden) {
            viewModel.isProfileHidden = false
            hiddenProfileCheckbox?.isChecked = false
        } else {
            hiddenProfileCheckbox?.isChecked = false
            val hiddenProfilePasswordCreationDialogFragment =
                HiddenProfilePasswordCreationDialogFragment.newInstance()
            hiddenProfilePasswordCreationDialogFragment.setOnPasswordSetCallback { password: String? ->
                val salt = ByteArray(SettingsActivity.PIN_SALT_LENGTH)
                SecureRandom().nextBytes(salt)
                val hash = computePINHash(password, salt)
                if (hash != null) {
                    viewModel.setPasswordAndSalt(hash, salt)
                    viewModel.isProfileHidden = true
                    hiddenProfileCheckbox?.isChecked = true
                    App.toast(
                        R.string.toast_message_hidden_profile_password_set,
                        Toast.LENGTH_SHORT,
                        Gravity.CENTER
                    )
                } else {
                    viewModel.isProfileHidden = false
                    hiddenProfileCheckbox?.isChecked = false
                    App.toast(
                        R.string.toast_message_hidden_profile_password_failed,
                        Toast.LENGTH_SHORT,
                        Gravity.CENTER
                    )
                }
            }
            hiddenProfilePasswordCreationDialogFragment.show(childFragmentManager, "dialog")
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_CHOOSE_IMAGE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (StringUtils.validateUri(data.data)) {
                        startActivityForResult(
                            Intent(
                                null, data.data, App.getContext(),
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
                            null, viewModel.takePictureUri, App.getContext(),
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
                            viewModel.absolutePhotoUrl = absolutePhotoUrl
                        }
                    }
                }
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun takePicture() {
        if (viewModel.pictureLocked) {
            return
        }
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        viewModel.takePictureUri = null

        if (takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
            val photoDir = File(requireActivity().cacheDir, App.CAMERA_PICTURE_FOLDER)
            val photoFile = File(
                photoDir, SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH).format(
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
                App.startActivityForResult(this, takePictureIntent, REQUEST_CODE_TAKE_PICTURE)
            } catch (e: IOException) {
                Logger.w("Error creating photo capture file $photoFile")
            }
        }
    }

    fun setUseDialogBackground(useDialogBackground: Boolean) {
        this.useDialogBackground = useDialogBackground
    }

    fun setLockedUserDetails(lockedUserDetails: JsonKeycloakUserDetails?, lockPicture: Boolean) {
        this.lockedUserDetails = lockedUserDetails
        this.lockPicture = lockPicture
    }

    fun setShowNicknameAndHidden(showNicknameAndHidden: Boolean) {
        this.showNicknameAndHidden = showNicknameAndHidden
    }

    fun setDisableHidden(disableHidden: Boolean) {
        this.disableHidden = disableHidden
    }

    companion object {
        private const val REQUEST_CODE_CHOOSE_IMAGE = 7596
        private const val REQUEST_CODE_TAKE_PICTURE = 7597
        private const val REQUEST_CODE_SELECT_ZONE = 7598
    }
}