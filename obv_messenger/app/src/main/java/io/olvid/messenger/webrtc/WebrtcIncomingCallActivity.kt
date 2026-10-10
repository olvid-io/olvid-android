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
package io.olvid.messenger.webrtc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager.LayoutParams
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.app.NotificationManagerCompat
import io.olvid.engine.Logger
import io.olvid.messenger.settings.SettingsActivity.Companion.overrideContextScales
import io.olvid.messenger.webrtc.WebrtcCallService.Call
import io.olvid.messenger.webrtc.WebrtcCallService.WebrtcCallServiceBinder


class WebrtcIncomingCallActivity : AppCompatActivity() {
    private var webrtcCallService by mutableStateOf<WebrtcCallService?>(null)
    private val webrtcServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service !is WebrtcCallServiceBinder) {
                Logger.e("☎ Bound to bad service!!!")
                closeActivity()
                return
            }
            webrtcCallService = service.service
        }

        override fun onNullBinding(name: ComponentName?) {
            closeActivity()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            webrtcCallService = null
            closeActivity()
        }
    }

    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(overrideContextScales(baseContext))
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        setContent {
            val currentCallState = webrtcCallService?.getCurrentIncomingCallLiveData()?.observeAsState()
            val callStateSnapshot : MutableState<Call?> = remember { mutableStateOf(null) }
            LaunchedEffect(currentCallState) {
                if (currentCallState?.value != null) {
                    callStateSnapshot.value = currentCallState.value
                }
            }

            CompositionLocalProvider(LocalConfiguration provides Configuration(LocalConfiguration.current).apply {
                uiMode =
                    (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }) {
                IncomingCallScreen(
                    name = callStateSnapshot.value?.callerContact?.displayName.orEmpty(),
                    avatar = callStateSnapshot.value?.callerContact,
                    callRejected = currentCallState?.value == null,
                    personalNote = callStateSnapshot.value?.callerContact?.personalNote.orEmpty(),
                    participantCount = callStateSnapshot.value?.participantCount ?: 0,
                    color = callStateSnapshot.value?.discussionCustomization?.colorJson?.color,
                    onAccept = { callStateSnapshot.value?.let { acceptCall(it) } },
                    onReject = { callStateSnapshot.value?.let { rejectCall(it) } }
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                closeActivity()
            }
        })

        val serviceBindIntent = Intent(this, WebrtcCallService::class.java)
        bindService(serviceBindIntent, webrtcServiceConnection, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(webrtcServiceConnection)
        webrtcCallService = null
    }

    private fun acceptCall(call: Call) {
        val answerCallIntent = Intent(this, WebrtcCallActivity::class.java)
        answerCallIntent.action = WebrtcCallActivity.ANSWER_CALL_ACTION
        answerCallIntent.putExtra(
            WebrtcCallActivity.ANSWER_CALL_EXTRA_CALL_IDENTIFIER,
            Logger.getUuidString(call.callIdentifier)
        )
        answerCallIntent.putExtra(
            WebrtcCallActivity.ANSWER_CALL_EXTRA_BYTES_OWNED_IDENTITY,
            call.bytesOwnedIdentity
        )
        answerCallIntent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(answerCallIntent)
        closeActivity()
    }

    private fun rejectCall(call: Call) {
        webrtcCallService?.recipientRejectCall(
            call.bytesOwnedIdentity,
            call.callIdentifier
        )
        // do not close activity here, the live data will take care of it
    }

    private fun closeActivity() {
        runCatching {
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.cancel(WebrtcCallService.NOT_FOREGROUND_NOTIFICATION_ID)
        }
        finishAndRemoveTask()
    }
}