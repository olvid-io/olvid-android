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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.ServiceListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import io.olvid.engine.Logger
import io.olvid.messenger.webrtc.BluetoothHeadsetManager.State.AUDIO_CONNECTED
import io.olvid.messenger.webrtc.BluetoothHeadsetManager.State.AUDIO_CONNECTING
import io.olvid.messenger.webrtc.BluetoothHeadsetManager.State.AUDIO_DISCONNECTING
import io.olvid.messenger.webrtc.BluetoothHeadsetManager.State.HEADSET_AVAILABLE
import io.olvid.messenger.webrtc.BluetoothHeadsetManager.State.HEADSET_UNAVAILABLE

internal class BluetoothHeadsetManager(private val webrtcCallService: WebrtcCallService) {
    private val audioManager: AudioManager? = webrtcCallService.audioManager
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothServiceListener: BluetoothServiceListener
    private val headsetBroadcastReceiver: BluetoothHeadsetBroadcastReceiver
    private var bluetoothHeadset: BluetoothHeadset? = null
    var state = HEADSET_UNAVAILABLE

    internal enum class State {
        HEADSET_UNAVAILABLE,
        HEADSET_AVAILABLE,
        AUDIO_CONNECTED,
        AUDIO_CONNECTING,
        AUDIO_DISCONNECTING
    }

    init {
        bluetoothServiceListener = BluetoothServiceListener()
        headsetBroadcastReceiver = BluetoothHeadsetBroadcastReceiver()
        Logger.d("Started bluetooth")
    }

    fun start() {
        if (bluetoothAdapter == null) {
            return
        }
        if (audioManager != null) {
            if (!audioManager.isBluetoothScoAvailableOffCall) {
                Logger.e("☎ Device does not support off call bluetooth SCO")
                return
            }
        }
        bluetoothAdapter.getProfileProxy(
            webrtcCallService,
            bluetoothServiceListener,
            BluetoothProfile.HEADSET
        )
        val headsetFilter = IntentFilter()
        headsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        headsetFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        webrtcCallService.registerReceiver(headsetBroadcastReceiver, headsetFilter)
    }

    fun stop() {
        if (bluetoothAdapter == null) {
            return
        }
        try {
            webrtcCallService.unregisterReceiver(headsetBroadcastReceiver)
        } catch (e: Exception) {
            // do nothing
        }
        bluetoothHeadset?.let {
            val bluetoothHeadsetRef: BluetoothHeadset = it
            bluetoothHeadset = null
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadsetRef)
        }
    }

    private fun updateBluetoothDevices() {
        if (bluetoothHeadset == null) {
            return
        }
        try {
            val devices = bluetoothHeadset!!.connectedDevices
            if (devices.isEmpty()) {
                state = HEADSET_UNAVAILABLE
            } else {
                if (state == HEADSET_UNAVAILABLE) {
                    state = HEADSET_AVAILABLE
                }
            }
        } catch (e: SecurityException) {
            state = HEADSET_UNAVAILABLE
        }
        webrtcCallService.updateAvailableAudioOutputsList()
    }

    fun connectAudio() {
        if (state != HEADSET_AVAILABLE && state != AUDIO_DISCONNECTING || audioManager == null) {
            return
        }
        state = AUDIO_CONNECTING
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
    }

    fun disconnectAudio() {
        if (state != AUDIO_CONNECTING && state != AUDIO_CONNECTED) {
            return
        }
        state = AUDIO_DISCONNECTING
        audioManager?.stopBluetoothSco()
        audioManager?.isBluetoothScoOn = false
    }

    internal inner class BluetoothServiceListener : ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                updateBluetoothDevices()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset?.let {
                    val bluetoothHeadsetRef: BluetoothHeadset = it
                    bluetoothHeadset = null
                    bluetoothAdapter?.closeProfileProxy(
                        BluetoothProfile.HEADSET,
                        bluetoothHeadsetRef
                    )
                }
                state = HEADSET_UNAVAILABLE
            }
        }
    }

    private inner class BluetoothHeadsetBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            when (action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    updateBluetoothDevices()
                }

                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                    val audioState = intent.getIntExtra(
                        BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED
                    )
                    when (audioState) {
                        BluetoothHeadset.STATE_AUDIO_CONNECTED -> {
                            state = AUDIO_CONNECTED
                        }

                        BluetoothHeadset.STATE_AUDIO_DISCONNECTED -> {
                            updateBluetoothDevices()
                            webrtcCallService.bluetoothDisconnected()
                        }
                    }
                }
            }
        }
    }
}
