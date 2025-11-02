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
package io.olvid.messenger.webrtc

import android.Manifest.permission
import android.app.KeyguardManager
import android.app.KeyguardManager.KeyguardDismissCallback
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Point
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.media.session.MediaSession
import android.media.session.MediaSession.Callback
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.main.contacts.ContactListViewModel
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.LOUDSPEAKER
import io.olvid.messenger.webrtc.WebrtcCallService.WakeLock.PROXIMITY
import io.olvid.messenger.webrtc.WebrtcCallService.WebrtcCallServiceBinder
import io.olvid.messenger.webrtc.components.CallAction.AddParticipant
import io.olvid.messenger.webrtc.components.CallAction.EndCall
import io.olvid.messenger.webrtc.components.CallAction.FlipCamera
import io.olvid.messenger.webrtc.components.CallAction.GoToDiscussion
import io.olvid.messenger.webrtc.components.CallAction.ShareScreen
import io.olvid.messenger.webrtc.components.CallAction.ToggleCamera
import io.olvid.messenger.webrtc.components.CallAction.ToggleMicrophone
import io.olvid.messenger.webrtc.components.CallAction.ToggleSpeaker
import io.olvid.messenger.webrtc.components.CallScreen
import io.olvid.messenger.webrtc.components.enterPictureInPicture

private const val REQUEST_MEDIA_PROJECTION = 314

class WebrtcCallActivity : AppCompatActivity() {
    private val contactListViewModel by viewModels<ContactListViewModel>()
    private var webrtcServiceConnection: WebrtcServiceConnection? = null
    private var webrtcCallService by mutableStateOf<WebrtcCallService?>(null)
    private var permissionDialogToShow by mutableStateOf<PermissionDialog?>(null)
    private var mediaSession: MediaSession? = null
    var foreground = false
    private var outputDialogOpen = false
    private var addParticipantDialogOpen = false
    private var loudSpeakerOn = false

    private val mediaProjectionManager by lazy { getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private val screenCaptureIntent by lazy {
        if (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForUserChoice()
            )
        } else {
            mediaProjectionManager.createScreenCaptureIntent()
        }
    }

    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        window.addFlags(
            LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or LayoutParams.FLAG_KEEP_SCREEN_ON
                    or LayoutParams.FLAG_TURN_SCREEN_ON
        )

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )

        super.onCreate(savedInstanceState)

        if (VERSION.SDK_INT >= VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        val intent = intent
        if (CALL_BACK_ACTION == intent.action) {
            val bytesOwnedIdentity = intent.getByteArrayExtra(CALL_BACK_EXTRA_BYTES_OWNED_IDENTITY)
            val bytesContactIdentity = intent.getByteArrayExtra(
                CALL_BACK_EXTRA_BYTES_CONTACT_IDENTITY
            )
            val discussionId = intent.getLongExtra(CALL_BACK_EXTRA_DISCUSSION_ID, -1)
            if (bytesOwnedIdentity != null && bytesContactIdentity != null) {
                val serviceIntent = Intent(this, WebrtcCallService::class.java)
                serviceIntent.action = WebrtcCallService.ACTION_START_CALL
                val bytesContactIdentitiesBundle = Bundle()
                bytesContactIdentitiesBundle.putByteArray(
                    WebrtcCallService.SINGLE_CONTACT_IDENTITY_BUNDLE_KEY,
                    bytesContactIdentity
                )
                serviceIntent.putExtra(
                    WebrtcCallService.CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA,
                    bytesContactIdentitiesBundle
                )
                serviceIntent.putExtra(
                    WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA,
                    bytesOwnedIdentity
                )
                startService(serviceIntent)
            } else {
                closeActivity()
            }
            if (discussionId != -1L) {
                AndroidNotificationManager.clearMissedCallNotification(discussionId)
            }
        } else if (ANSWER_CALL_ACTION == intent.action) {
            val callIdentifier = intent.getStringExtra(ANSWER_CALL_EXTRA_CALL_IDENTIFIER)
            val bytesOwnedIdentity = intent.getByteArrayExtra(ANSWER_CALL_EXTRA_BYTES_OWNED_IDENTITY)

            if (callIdentifier != null && bytesOwnedIdentity != null) {
                startService(
                    Intent(this, WebrtcCallService::class.java)
                        .setAction(WebrtcCallService.ACTION_ANSWER_CALL)
                        .putExtra(WebrtcCallService.CALL_IDENTIFIER_INTENT_EXTRA, callIdentifier)
                        .putExtra(WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity)
                )
            } else {
                closeActivity()
            }
        }
        intent.action = null
        webrtcServiceConnection = WebrtcServiceConnection().apply {
            val serviceBindIntent =
                Intent(this@WebrtcCallActivity, WebrtcCallService::class.java)
            bindService(serviceBindIntent, this, 0)
        }

        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        setContent {
            LaunchedEffect(webrtcCallService?.selectedAudioOutput) {
                webrtcCallService?.selectedAudioOutput?.let {
                    audioOutput ->
                    if (audioOutput == LOUDSPEAKER != loudSpeakerOn) {
                        loudSpeakerOn = audioOutput == LOUDSPEAKER
                        refreshProximityLockStatus()
                    }
                }
            }
            AppCompatTheme {
                var addingParticipant by remember {
                    mutableStateOf(false)
                }
                BackHandler {
                    if (addingParticipant) {
                        addingParticipant = false
                    }
                }
                CallScreen(
                    webrtcCallService = webrtcCallService,
                    contactListViewModel = contactListViewModel,
                    addingParticipant = addingParticipant,
                    onCallAction = { callAction ->
                        when (callAction) {
                            EndCall -> {
                                webrtcCallService?.hangUpCall() ?: run { finishAndRemoveTask() }
                            }

                            FlipCamera -> {
                                webrtcCallService?.flipCamera()
                            }

                            ToggleCamera -> {
                                if ((webrtcCallService?.getCallParticipantsLiveData()?.value?.size ?: 0) > WebrtcPeerConnectionHolder.MAXIMUM_OTHER_PARTICIPANTS_FOR_VIDEO) {
                                    App.toast(getString(R.string.toast_message_video_calls_xxx_participants, WebrtcPeerConnectionHolder.MAXIMUM_OTHER_PARTICIPANTS_FOR_VIDEO + 1), Toast.LENGTH_SHORT, Gravity.BOTTOM)
                                    return@CallScreen
                                }

                                if (
                                    ContextCompat.checkSelfPermission(
                                        this,
                                        permission.CAMERA
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    ActivityCompat.requestPermissions(
                                        this,
                                        arrayOf(permission.CAMERA),
                                        PERMISSIONS_REQUEST_CODE
                                    )
                                } else {
                                    webrtcCallService?.toggleCamera()
                                    refreshProximityLockStatus()
                                }
                            }

                            is ToggleMicrophone -> {
                                webrtcCallService?.toggleMuteMicrophone()
                            }

                            is GoToDiscussion -> {
                                openDiscussion()
                            }

                            ShareScreen -> {
                                if ((webrtcCallService?.getCallParticipantsLiveData()?.value?.size ?: 0) > WebrtcPeerConnectionHolder.MAXIMUM_OTHER_PARTICIPANTS_FOR_VIDEO) {
                                    App.toast(getString(R.string.toast_message_video_calls_xxx_participants, WebrtcPeerConnectionHolder.MAXIMUM_OTHER_PARTICIPANTS_FOR_VIDEO + 1), Toast.LENGTH_SHORT, Gravity.BOTTOM)
                                    return@CallScreen
                                }

                                val width: Int
                                val height: Int
                                if (VERSION.SDK_INT >= VERSION_CODES.R) {
                                    windowManager.currentWindowMetrics.bounds.let {
                                        width = it.width()
                                        height = it.height()
                                    }
                                } else {
                                    val point = Point()
                                    @Suppress("DEPRECATION")
                                    windowManager.defaultDisplay.getRealSize(point)
                                    width = point.x
                                    height = point.y
                                }

                                webrtcCallService?.setScreenSize(width, height)
                                if (webrtcCallService?.screenShareActive == false) {
                                    webrtcCallService?.requestingScreenCast = true
                                    @Suppress("DEPRECATION")
                                    startActivityForResult(
                                        screenCaptureIntent,
                                        REQUEST_MEDIA_PROJECTION
                                    )
                                } else {
                                    webrtcCallService?.toggleScreenShare()
                                }
                            }

                            ToggleSpeaker -> {}
                            is AddParticipant -> {
                                addingParticipant = callAction.open
                            }
                        }
                    }
                )

                // permission request dialog to show if some permissions were denied
                permissionDialogToShow?.let { dialog ->
                    val onDismiss = {
                        dialog.dismissCallback?.invoke()
                        permissionDialogToShow = null
                    }

                    AlertDialog(
                        backgroundColor = colorResource(id = R.color.dialogBackground),
                        contentColor = Color.White,
                        onDismissRequest = onDismiss,
                        buttons = {
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, bottom = 8.dp, end = 16.dp),
                                horizontalArrangement = Arrangement.End) {
                                dialog.additionalButton?.let { button ->
                                    button()
                                    Spacer(modifier = Modifier.weight(1f, true))
                                }
                                TextButton(onClick = onDismiss) {
                                    Text(text = stringResource(id = R.string.button_label_ok))
                                }
                            }
                        },
                        title = {
                            Text(text = stringResource(id = dialog.titleStringResId))
                        },
                        text = {
                            Text(text = stringResource(id = dialog.messageStringResId))
                        })
                }
            }
        }

        mediaSession = MediaSession(this, "Olvid ongoing call")
        mediaSession?.setCallback(object : Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val keyEvent = if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && (keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
                    if (webrtcCallService != null) {
                        webrtcCallService?.toggleMuteMicrophone()
                    }
                    return true
                }
                return false
            }
        }, Handler(Looper.getMainLooper()))
        mediaSession?.isActive = true
        if (!SettingsActivity.wasFirstCallAudioPermissionRequested() && ContextCompat.checkSelfPermission(
                this,
                permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            SettingsActivity.setFirstCallAudioPermissionRequested(true)
            permissionDialogToShow = PermissionDialog(R.string.dialog_title_webrtc_audio_permission, R.string.dialog_message_webrtc_audio_permission) {
                requestPermissionsIfNeeded(true)
            }
        } else {
            requestPermissionsIfNeeded(false)
        }
    }

    private fun requestPermissionsIfNeeded(rationaleWasShown: Boolean) {
        if (ContextCompat.checkSelfPermission(
                this,
                permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(permission.RECORD_AUDIO, permission.READ_PHONE_STATE),
                if (rationaleWasShown) PERMISSIONS_REQUEST_CODE_AFTER_RATIONALE else PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun refreshProximityLockStatus() {
        if (webrtcCallService != null) {
            if (foreground && !outputDialogOpen && !addParticipantDialogOpen && !loudSpeakerOn && webrtcCallService?.cameraEnabled != true) {
                webrtcCallService?.acquireWakeLock(PROXIMITY)
            } else {
                webrtcCallService?.releaseWakeLocks(PROXIMITY)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webrtcCallService?.closeCallActivity = {}
        webrtcServiceConnection?.let { unbindService(it) }
        initWebrtcCallService(null)
        if (mediaSession != null) {
            mediaSession?.release()
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        refreshProximityLockStatus()
    }

    override fun onPause() {
        super.onPause()
        foreground = false
        refreshProximityLockStatus()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (webrtcCallService?.screenShareActive != true
            && webrtcCallService?.requestingScreenCast != true
            && webrtcCallService?.getState()?.value == WebrtcCallService.State.CALL_IN_PROGRESS) {
            enterPictureInPicture(this)
        }
    }

    private fun openDiscussion(oneToOneContact : Contact? = null) {
        if (webrtcCallService != null) {
            val callParticipants = webrtcCallService?.getCallParticipantsLiveData()?.value
            if (webrtcCallService?.bytesOwnedIdentity != null && callParticipants != null) {
                val discussionIntent: Intent
                when (webrtcCallService?.discussionType) {
                    Discussion.TYPE_GROUP -> {
                        if (webrtcCallService?.bytesGroupOwnerAndUidOrIdentifier == null) {
                            return
                        }
                        discussionIntent = Intent(this, MainActivity::class.java)
                        discussionIntent.action = MainActivity.FORWARD_ACTION
                        discussionIntent.putExtra(
                            MainActivity.FORWARD_TO_INTENT_EXTRA,
                            DiscussionActivity::class.java.name
                        )
                        discussionIntent.putExtra(
                            MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA,
                            webrtcCallService?.bytesOwnedIdentity
                        )
                        discussionIntent.putExtra(
                            DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA,
                            webrtcCallService?.bytesOwnedIdentity
                        )
                        discussionIntent.putExtra(
                            DiscussionActivity.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA,
                            webrtcCallService?.bytesGroupOwnerAndUidOrIdentifier
                        )
                    }

                    Discussion.TYPE_GROUP_V2 -> {
                        if (webrtcCallService?.bytesGroupOwnerAndUidOrIdentifier == null) {
                            return
                        }
                        discussionIntent = Intent(this, MainActivity::class.java)
                        discussionIntent.action = MainActivity.FORWARD_ACTION
                        discussionIntent.putExtra(
                            MainActivity.FORWARD_TO_INTENT_EXTRA,
                            DiscussionActivity::class.java.name
                        )
                        discussionIntent.putExtra(
                            MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA,
                            webrtcCallService?.bytesOwnedIdentity
                        )
                        discussionIntent.putExtra(
                            DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA,
                            webrtcCallService?.bytesOwnedIdentity
                        )
                        discussionIntent.putExtra(
                            DiscussionActivity.BYTES_GROUP_IDENTIFIER_INTENT_EXTRA,
                            webrtcCallService?.bytesGroupOwnerAndUidOrIdentifier
                        )
                    }

                    else -> {
                        if (callParticipants.size != 1 && oneToOneContact == null) {
                            return
                        }
                        val contact = oneToOneContact ?: callParticipants[0].contact
                        if (contact == null || !contact.oneToOne) {
                            return
                        }
                        discussionIntent = Intent(this, MainActivity::class.java)
                        discussionIntent.action = MainActivity.FORWARD_ACTION
                        discussionIntent.putExtra(
                            MainActivity.FORWARD_TO_INTENT_EXTRA,
                            DiscussionActivity::class.java.name
                        )
                        discussionIntent.putExtra(
                            MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA,
                            webrtcCallService?.bytesOwnedIdentity
                        )
                        discussionIntent.putExtra(
                            DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA,
                            webrtcCallService?.bytesOwnedIdentity
                        )
                        discussionIntent.putExtra(
                            DiscussionActivity.BYTES_CONTACT_IDENTITY_INTENT_EXTRA,
                            callParticipants[0].bytesContactIdentity
                        )
                    }
                }
                if (VERSION.SDK_INT >= VERSION_CODES.O) {
                    val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager?
                    if (km != null && km.isDeviceLocked) {
                        km.requestDismissKeyguard(this, object : KeyguardDismissCallback() {
                            override fun onDismissSucceeded() {
                                startActivity(discussionIntent)
                            }
                        })
                        return
                    }
                }
                startActivity(discussionIntent)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSIONS_REQUEST_CODE ||
            requestCode == PERMISSIONS_REQUEST_CODE_AFTER_RATIONALE
        ) {
            var audioPermissionGranted = true
            var cameraPermissionGranted = true
            for (i in permissions.indices) {
                if (permission.RECORD_AUDIO == permissions[i]) {
                    audioPermissionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    if (audioPermissionGranted && webrtcCallService != null) {
                        webrtcCallService?.audioPermissionGranted()
                    }
                } else if (permission.CAMERA == permissions[i]) {
                    cameraPermissionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    if (cameraPermissionGranted && webrtcCallService != null) {
                        webrtcCallService?.toggleCamera()
                        refreshProximityLockStatus()
                    }
                } else if (permission.READ_PHONE_STATE == permissions[i]) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED && webrtcCallService != null) {
                        webrtcCallService?.readCallStatePermissionGranted()
                    }
                }
            }
            if (!audioPermissionGranted) {
                if (requestCode == PERMISSIONS_REQUEST_CODE_AFTER_RATIONALE) {
                    // user was prompted for permission, and he denied it --> hangup
                    App.toast(R.string.toast_message_audio_permission_denied, Toast.LENGTH_SHORT)
                    if (webrtcCallService != null) {
                        webrtcCallService?.hangUpCall()
                    }
                } else {
                    // user was not prompted --> show dialog explaining that audio permission was permanently denied
                    permissionDialogToShow = PermissionDialog(
                        titleStringResId = R.string.dialog_title_webrtc_permissions_audio_blocked,
                        messageStringResId = R.string.dialog_message_webrtc_permissions_audio_blocked,
                        additionalButton = {
                            TextButton(onClick = {
                                val settingsIntent = Intent()
                                settingsIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                val uri = Uri.fromParts("package", packageName, null)
                                settingsIntent.data = uri
                                startActivity(settingsIntent)
                            }) {
                                Text(text = stringResource(id = R.string.button_label_app_settings))
                            }
                        }
                    ) {
                        requestPermissionsIfNeeded(true)
                    }
                }
            }
            if (!cameraPermissionGranted) {
                if (requestCode == PERMISSIONS_REQUEST_CODE_AFTER_RATIONALE) {
                    // user was prompted for permission, and he denied it --> hangup
                    App.toast(R.string.toast_message_camera_permission_denied, Toast.LENGTH_SHORT)
                } else {
                    // user was not prompted --> show dialog explaining that audio permission was permanently denied
                    permissionDialogToShow = PermissionDialog(
                        titleStringResId = R.string.dialog_title_webrtc_permissions_camera_blocked,
                        messageStringResId = R.string.dialog_message_webrtc_permissions_camera_blocked,
                        additionalButton = {
                            TextButton(onClick = {
                                val settingsIntent = Intent()
                                settingsIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                val uri = Uri.fromParts("package", packageName, null)
                                settingsIntent.data = uri
                                startActivity(settingsIntent)
                            }) {
                                Text(text = stringResource(id = R.string.button_label_app_settings))
                            }
                        }
                    ) {
                        requestPermissionsIfNeeded(true)
                    }
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            webrtcCallService?.requestingScreenCast = false
            if (resultCode == RESULT_OK) {
                webrtcCallService?.toggleScreenShare(data)
            }
        }
    }

    private fun closeActivity() {
        Handler(Looper.getMainLooper()).postDelayed({ finishAndRemoveTask() }, 0) // TODO: put back the 3000 wait once we remove the black screen
    }

    private fun initWebrtcCallService(webrtcCallService: WebrtcCallService?) {
        if (webrtcCallService != null) {
            this.webrtcCallService = webrtcCallService.apply {
                closeCallActivity = ::closeActivity
            }
            refreshProximityLockStatus()
        } else {
            this.webrtcCallService = null
        }
    }

    inner class WebrtcServiceConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (service !is WebrtcCallServiceBinder) {
                Logger.e("☎ WebrtcCallActivity bound to bad service?!")
                closeActivity()
                return
            }
            initWebrtcCallService(service.service)
        }

        override fun onNullBinding(name: ComponentName) {
            closeActivity()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            initWebrtcCallService(null)
            closeActivity()
        }
    }
    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 632
        private const val PERMISSIONS_REQUEST_CODE_AFTER_RATIONALE = 633
        const val CALL_BACK_ACTION = "call_back"
        const val CALL_BACK_EXTRA_BYTES_OWNED_IDENTITY = "bytes_owned_identity"
        const val CALL_BACK_EXTRA_BYTES_CONTACT_IDENTITY = "bytes_contact_identity"
        const val CALL_BACK_EXTRA_DISCUSSION_ID = "discussion_id"
        const val ANSWER_CALL_ACTION = "answer_call"
        const val ANSWER_CALL_EXTRA_CALL_IDENTIFIER = "call_identifier"
        const val ANSWER_CALL_EXTRA_BYTES_OWNED_IDENTITY = "bytes_owned_identity"
    }

    data class PermissionDialog(
        val titleStringResId: Int,
        val messageStringResId: Int,
        val additionalButton: (@Composable RowScope.() -> Unit)? = null,
        val dismissCallback: (() -> Unit)?
    )
}
