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
package io.olvid.messenger.webrtc

import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyCallback.CallStateListener
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

class PhoneCallStateListener(val service: WebrtcCallService, executor: Executor?) {
    private val telephonyManager: TelephonyManager? = service.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
    var listener: Listener? = null
    var callback: Callback? = null
    private var unmuteOnIdle = false

    init {
        if (VERSION.SDK_INT >= VERSION_CODES.S) {
            listener = null
            callback = Callback()
            telephonyManager?.registerTelephonyCallback(executor!!, callback!!)
        } else {
            listener = Listener()
            callback = null
            telephonyManager?.listen(
                listener,
                PhoneStateListener.LISTEN_CALL_STATE or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
            )
        }
    }

    fun unregister() {
        if (telephonyManager != null) {
            listener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
            if (VERSION.SDK_INT >= VERSION_CODES.S) {
                callback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            }
        }
    }

    private fun handleStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                if (unmuteOnIdle) {
                    unmuteOnIdle = false
                    service.toggleMuteMicrophone()
                }
            }

            TelephonyManager.CALL_STATE_RINGING -> {}
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val microphoneMuted = service.getMicrophoneMuted().value
                if (microphoneMuted == null || !microphoneMuted) {
                    unmuteOnIdle = true
                    service.toggleMuteMicrophone()
                }
            }
        }
    }

    inner class Listener : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String) {
            super.onCallStateChanged(state, phoneNumber)
            handleStateChange(state)
        }
    }

    @RequiresApi(api = VERSION_CODES.S)
    inner class Callback : TelephonyCallback(), CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleStateChange(state)
        }
    }
}
