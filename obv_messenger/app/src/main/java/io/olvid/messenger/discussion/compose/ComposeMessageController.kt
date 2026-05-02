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

package io.olvid.messenger.discussion.compose

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.DiscussionInputEditText
import io.olvid.messenger.customClasses.LocationIntegrationSelectorDialog
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils2.Companion.normalize
import io.olvid.messenger.databases.tasks.AddFyleToDraftFromUriTask
import io.olvid.messenger.databases.tasks.PostMessageInDiscussionTask
import io.olvid.messenger.databases.tasks.UpdateMessageBodyTask
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.Utils
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.location.LocationActivity
import io.olvid.messenger.discussion.mention.MentionViewModel
import io.olvid.messenger.discussion.poll.PollCreationActivity
import io.olvid.messenger.fragments.dialog.ContactIntroductionDialogFragment
import io.olvid.messenger.services.UnifiedForegroundService.LocationSharingSubService
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.BASIC
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.CUSTOM_OSM
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.MAPS
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.OSM
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Stable
class ComposeMessageController(
    val voiceMessageRecorder: VoiceMessageRecorder,
    private val context: Context,
    private val activity: FragmentActivity?,
    private val discussionViewModel: DiscussionViewModel,
    private val composeMessageViewModel: ComposeMessageViewModel,
    private val linkPreviewViewModel: LinkPreviewViewModel,
    private val mentionViewModel: MentionViewModel,
    private val ephemeralViewModel: EphemeralViewModel,
    private val imm: InputMethodManager?,
    private val attachFileLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    private val takePictureImpl: () -> Unit,
    private val takeVideoImpl: () -> Unit,
    private val requestPermissionForPictureLauncher: ManagedActivityResultLauncher<String, Boolean>,
    private val requestPermissionForVideoLauncher: ManagedActivityResultLauncher<String, Boolean>,
    private val requestPermissionForAudioLauncher: ManagedActivityResultLauncher<String, Boolean>,
    private val requestPermissionForAudioAfterRationaleLauncher: ManagedActivityResultLauncher<String, Boolean>,
) {

    fun onAttachImage() {
        discussionViewModel.doNotMarkAsReadOnPause()
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        activity?.let { App.prepareForStartActivityForResult(it) }
        attachFileLauncher.launch(intent)
    }

    fun onAttachFile() {
        discussionViewModel.doNotMarkAsReadOnPause()
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        activity?.let { App.prepareForStartActivityForResult(it) }
        attachFileLauncher.launch(intent)
    }

    fun onAttachVideo(hasCamera: Boolean) {
        if (hasCamera) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionForVideoLauncher.launch(
                    Manifest.permission.CAMERA
                )
            } else {
                takeVideo()
            }
        } else {
            App.toast(
                R.string.toast_message_device_has_no_camera,
                Toast.LENGTH_SHORT
            )
        }
    }

    fun onAttachCamera(hasCamera: Boolean) {
        if (hasCamera) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionForPictureLauncher.launch(
                    Manifest.permission.CAMERA
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
    }

    fun onAttachPoll() {
        context.startActivity(
            Intent(context, PollCreationActivity::class.java).apply {
                putExtra(
                    PollCreationActivity.DISCUSSION_ID_INTENT_EXTRA,
                    discussionViewModel.discussionId
                )
            }
        )
    }

    fun onAttachTimer() {
        composeMessageViewModel.openEphemeralSettings =
            !composeMessageViewModel.openEphemeralSettings
        if (composeMessageViewModel.openEphemeralSettings) {
            ephemeralViewModel.setDiscussionId(
                discussionViewModel.discussionId,
                false
            )
        }
    }

    fun onEmojiToggled(inputEditText: DiscussionInputEditText?) {
        composeMessageViewModel.emojiExpanded = !composeMessageViewModel.emojiExpanded
        if (composeMessageViewModel.emojiExpanded) {
            imm?.hideSoftInputFromWindow(inputEditText?.windowToken, 0)
        }
    }

    fun onAttachEmoji(inputEditText: DiscussionInputEditText?) {
        composeMessageViewModel.emojiExpanded = true
        imm?.hideSoftInputFromWindow(inputEditText?.windowToken, 0)
    }

    fun onAttachIntroduce() {
        discussionViewModel.discussionContacts.value?.firstOrNull()
            ?.let { contact ->
                if (contact.hasChannelOrPreKey()) {
                    val contactIntroductionDialogFragment =
                        ContactIntroductionDialogFragment.newInstance(
                            contact.bytesOwnedIdentity,
                            contact.bytesContactIdentity,
                            contact.getCustomDisplayName()
                        )
                    if (activity != null) {
                        contactIntroductionDialogFragment.show(
                            activity.supportFragmentManager,
                            "introductionDialog"
                        )
                    }
                } else {
                    App.toast(
                        R.string
                            .toast_message_established_channel_required_for_introduction,
                        Toast.LENGTH_LONG
                    )
                }
            }
    }

    fun onAttachLocation() {
        discussionViewModel.discussionId?.let { discussionId ->
            // if currently sharing location: stop sharing location
            if (LocationSharingSubService.isDiscussionSharingLocation(discussionId)) {
                SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                    .setTitle(R.string.title_stop_sharing_location)
                    .setMessage(R.string.label_stop_sharing_location)
                    .setPositiveButton(R.string.button_label_stop) { _, _ ->
                        LocationSharingSubService.stopSharingInDiscussion(
                            discussionViewModel.discussionId!!,
                            false
                        )
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .create()
                    .show()
                return@let
            }
            if (activity != null) {
                when (SettingsActivity.locationIntegration) {
                    OSM,
                    MAPS,
                    CUSTOM_OSM -> {
                        LocationActivity.startSendLocation(
                            context,
                            discussionId,
                            SettingsActivity.locationIntegration
                        )
                    }

                    BASIC -> {
                        LocationActivity.startSendLocationBasic(
                            activity,
                            discussionId,
                            BASIC
                        )
                    }

                    SettingsActivity.LocationIntegrationEnum.NONE -> {
                        LocationIntegrationSelectorDialog(
                            context,
                            object :
                                LocationIntegrationSelectorDialog.OnIntegrationSelectedListener {
                                override fun onIntegrationSelected(
                                    integration:
                                    SettingsActivity.LocationIntegrationEnum,
                                    customOsmServerUrl: String?
                                ) {
                                    SettingsActivity.setLocationIntegration(
                                        integration.string,
                                        customOsmServerUrl
                                    )
                                    // re-run if something was selected
                                    if (integration in listOf(OSM, MAPS, BASIC, CUSTOM_OSM)) {
                                        onAttachLocation()
                                    }
                                }
                            }
                        )
                            .show()
                    }
                }
            }
        }
    }

    fun takePicture() {
        takePictureImpl()
    }

    fun takeVideo() {
        takeVideoImpl()
    }

    fun onVoiceRecordingStart() {
        if (activity == null) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            voiceMessageRecorder.setRecordPermission(true)
            voiceMessageRecorder.startRecord()
        } else {
            if (activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                val builder =
                    SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_voice_message_explanation)
                        .setMessage(R.string.dialog_message_voice_message_explanation)
                        .setPositiveButton(R.string.button_label_ok, null)
                        .setOnDismissListener {
                            requestPermissionForAudioAfterRationaleLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }
                builder.create().show()
            } else {
                requestPermissionForAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun onVoiceRecordingStop(discard: Boolean) {
        voiceMessageRecorder.stopRecord(discard = discard)
    }

    fun onSendMessage(
        sending: Boolean,
        isEditMode: Boolean,
        inputEditText: DiscussionInputEditText?
    ) {
        if (sending) return
        if (isEditMode) {
            composeMessageViewModel.getMessageBeingEdited().value?.let { draftEdit ->
                if (composeMessageViewModel.trimmedNewMessageText != null) {
                    val trimAndMentions =
                        Utils.removeProtectionFEFFsAndTrim(
                            composeMessageViewModel.rawNewMessageText,
                            mentionViewModel.mentions
                        )
                    App.runThread(
                        UpdateMessageBodyTask(
                            draftEdit.id,
                            trimAndMentions.first,
                            trimAndMentions.second
                        )
                    )
                }
                inputEditText?.setText("")
                mentionViewModel.updateMentions(null)
                composeMessageViewModel.clearMessageBeingEdited()
                linkPreviewViewModel.reset()
            }
        } else {
            discussionViewModel.discussionId?.let { discussionId ->
                if (composeMessageViewModel.trimmedNewMessageText != null
                    || composeMessageViewModel.hasAttachments()
                    || voiceMessageRecorder.isOpened
                ) {
                    composeMessageViewModel.sending = true
//                    if (voiceMessageRecorder.isRecording || voiceMessageRecorder.isPaused) {
//                        voiceMessageRecorder.stoppedForSend = true
//                    }
                    discussionViewModel.markMessagesRead()
                    val trimAndMentions =
                        Utils.removeProtectionFEFFsAndTrim(
                            composeMessageViewModel.rawNewMessageText,
                            mentionViewModel.mentions
                        )
                    mentionViewModel.updateMentions(null)
                    linkPreviewViewModel.waitForPreview {
                        App.runThread {
                            runCatching {
                                if (voiceMessageRecorder.isRecording || voiceMessageRecorder.isPaused) {
                                    voiceMessageRecorder.stopRecord(
                                        discard = false,
                                        async = false
                                    )
                                }
                                PostMessageInDiscussionTask(
                                    trimAndMentions.first,
                                    discussionId,
                                    true,
                                    linkPreviewViewModel.openGraph.value,
                                    trimAndMentions.second,
                                    null
                                )
                                    .run()
                            }.onFailure {
                                Logger.x(it)
                            }

                            Handler(Looper.getMainLooper()).post {
                                discussionViewModel.messageWasSent()
                                linkPreviewViewModel.reset()
                                inputEditText?.setText("")
                                composeMessageViewModel.sending = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberComposeMessageController(
    discussionViewModel: DiscussionViewModel,
    composeMessageViewModel: ComposeMessageViewModel,
    ephemeralViewModel: EphemeralViewModel,
    linkPreviewViewModel: LinkPreviewViewModel,
    mentionViewModel: MentionViewModel,
): ComposeMessageController {
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val scope = rememberCoroutineScope()
    val imm =
        remember(context) { context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager }

    val attachFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { data ->
                    if (data.clipData != null) {
                        val clipData = data.clipData!!
                        val uris = (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
                        val discussionId = discussionViewModel.discussionId!!
                        App.runThread {
                            // Sort URIs alphabetically by display name before inserting to preserve order
                            val sortedUris = uris.sortedBy { uri ->
                                context.contentResolver.query(
                                    uri,
                                    arrayOf(OpenableColumns.DISPLAY_NAME),
                                    null, null, null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst())
                                        cursor.getString(0)?.normalize()
                                    else
                                        null
                                } ?: uri.lastPathSegment?.normalize() ?: ""
                            }
                            for (uri in sortedUris) {
                                AddFyleToDraftFromUriTask(uri, null, null, discussionId).run()
                            }
                        }
                    } else if (data.data != null) {
                        App.runThread(
                            AddFyleToDraftFromUriTask(
                                data.data!!,
                                null as String?,
                                null as String?,
                                discussionViewModel.discussionId!!
                            )
                        )
                    }
                }
            }
        }

    val takePictureLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                composeMessageViewModel.photoOrVideoUri?.let { uri ->
                    App.runThread(
                        AddFyleToDraftFromUriTask(
                            uri,
                            composeMessageViewModel.photoOrVideoFile?.name,
                            "image/jpeg",
                            discussionViewModel.discussionId!!
                        )
                    )
                }
            }
        }

    val takeVideoLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                composeMessageViewModel.photoOrVideoUri?.let { uri ->
                    App.runThread(
                        AddFyleToDraftFromUriTask(
                            uri,
                            composeMessageViewModel.photoOrVideoFile?.name,
                            "video/mp4",
                            discussionViewModel.discussionId!!
                        )
                    )
                }
            }
        }

    val voiceMessageRecorder = remember {
        VoiceMessageRecorder(
            activity = activity!!,
            composeMessageViewModel = composeMessageViewModel
        )
    }

    val takePictureImpl =
        remember(context, activity, composeMessageViewModel, discussionViewModel) {
            {
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                composeMessageViewModel.photoOrVideoUri = null
                composeMessageViewModel.photoOrVideoFile = null
                if (context.packageManager.let { takePictureIntent.resolveActivity(it) } != null) {
                    val photoDir = File(context.cacheDir, App.CAMERA_PICTURE_FOLDER)
                    val photoFile =
                        File(
                            photoDir,
                            SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH)
                                .format(Date()) + ".jpg"
                        )
                    runCatching {
                        photoDir.mkdirs()
                        if (photoFile.createNewFile()) {
                            composeMessageViewModel.photoOrVideoFile = photoFile
                            val photoUri =
                                FileProvider.getUriForFile(
                                    context,
                                    BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                                    photoFile
                                )
                            composeMessageViewModel.photoOrVideoUri = photoUri
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                            discussionViewModel.doNotMarkAsReadOnPause()
                            if (activity != null) {
                                App.prepareForStartActivityForResult(activity)
                            }
                            takePictureLauncher.launch(takePictureIntent)
                        }
                    }.onFailure {
                        Logger.w("Error creating photo capture file $photoFile")
                    }
                }
            }
        }

    val takeVideoImpl = remember(context, activity, composeMessageViewModel, discussionViewModel) {
        {
            val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            composeMessageViewModel.photoOrVideoUri = null
            composeMessageViewModel.photoOrVideoFile = null
            if (context.packageManager.let { takeVideoIntent.resolveActivity(it) } != null) {
                val videoDir = File(context.cacheDir, App.CAMERA_PICTURE_FOLDER)
                val videoFile =
                    File(
                        videoDir,
                        SimpleDateFormat(App.TIMESTAMP_FILE_NAME_FORMAT, Locale.ENGLISH)
                            .format(Date()) + ".mp4"
                    )
                runCatching {
                    videoDir.mkdirs()
                    if (videoFile.createNewFile()) {
                        composeMessageViewModel.photoOrVideoFile = videoFile
                        val photoUri =
                            FileProvider.getUriForFile(
                                context,
                                BuildConfig.APPLICATION_ID + ".PICTURE_FILES_PROVIDER",
                                videoFile
                            )
                        composeMessageViewModel.photoOrVideoUri = photoUri
                        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                        discussionViewModel.doNotMarkAsReadOnPause()
                        if (activity != null) {
                            App.prepareForStartActivityForResult(activity)
                        }
                        takeVideoLauncher.launch(takeVideoIntent)
                    }
                }.onFailure {
                    Logger.w("Error creating video capture file $videoFile")
                }
            }
        }
    }

    val requestPermissionForPictureLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                takePictureImpl()
            } else {
                App.toast(R.string.toast_message_device_has_no_camera, Toast.LENGTH_SHORT)
            }
        }

    val requestPermissionForVideoLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                takeVideoImpl()
            } else {
                App.toast(R.string.toast_message_device_has_no_camera, Toast.LENGTH_SHORT)
            }
        }

    val requestPermissionForAudioLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                voiceMessageRecorder.setRecordPermission(true)
                voiceMessageRecorder.startRecord()
            } else {
                val builder =
                    SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_voice_message_explanation)
                        .setMessage(
                            R.string
                                .dialog_message_voice_message_explanation_blocked
                        )
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setNeutralButton(R.string.button_label_app_settings) { _, _ ->
                            val intent = Intent()
                            intent.action =
                                android.provider.Settings
                                    .ACTION_APPLICATION_DETAILS_SETTINGS
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            val uri =
                                Uri.fromParts("package", context.packageName, null)
                            intent.data = uri
                            context.startActivity(intent)
                        }
                builder.create().show()
            }
        }

    val requestPermissionForAudioAfterRationaleLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                voiceMessageRecorder.setRecordPermission(true)
                voiceMessageRecorder.startRecord()
            } else {
                App.toast(R.string.toast_message_audio_permission_denied, Toast.LENGTH_SHORT)
            }
        }

    return remember(
        discussionViewModel,
        composeMessageViewModel,
        ephemeralViewModel,
        linkPreviewViewModel,
        mentionViewModel,
        voiceMessageRecorder,
        context,
        activity,
        scope,
        imm,
        attachFileLauncher,
        takePictureLauncher,
        takeVideoLauncher,
        requestPermissionForPictureLauncher,
        requestPermissionForVideoLauncher,
        requestPermissionForAudioLauncher,
        requestPermissionForAudioAfterRationaleLauncher
    ) {
        ComposeMessageController(
            voiceMessageRecorder = voiceMessageRecorder,
            context = context,
            activity = activity,
            discussionViewModel = discussionViewModel,
            composeMessageViewModel = composeMessageViewModel,
            linkPreviewViewModel = linkPreviewViewModel,
            mentionViewModel = mentionViewModel,
            ephemeralViewModel = ephemeralViewModel,
            imm = imm,
            attachFileLauncher = attachFileLauncher,
            takePictureImpl = takePictureImpl,
            takeVideoImpl = takeVideoImpl,
            requestPermissionForPictureLauncher = requestPermissionForPictureLauncher,
            requestPermissionForVideoLauncher = requestPermissionForVideoLauncher,
            requestPermissionForAudioLauncher = requestPermissionForAudioLauncher,
            requestPermissionForAudioAfterRationaleLauncher = requestPermissionForAudioAfterRationaleLauncher,
        )
    }
}