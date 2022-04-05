/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.messenger.webrtc;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

import java.util.List;

import io.olvid.engine.Logger;

class BluetoothHeadsetManager {
    private final WebrtcCallService webrtcCallService;
    private final AudioManager audioManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothServiceListener bluetoothServiceListener;
    private final BluetoothHeadsetBroadcastReceiver headsetBroadcastReceiver;

    private BluetoothHeadset bluetoothHeadset;
    State state = State.HEADSET_UNAVAILABLE;


    enum State {
        HEADSET_UNAVAILABLE,
        HEADSET_AVAILABLE,
        AUDIO_CONNECTED,
        AUDIO_CONNECTING,
        AUDIO_DISCONNECTING,
    }


    public BluetoothHeadsetManager(WebrtcCallService webrtcCallService) {
        this.webrtcCallService = webrtcCallService;
        audioManager = webrtcCallService.audioManager;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothServiceListener = new BluetoothServiceListener();
        headsetBroadcastReceiver = new BluetoothHeadsetBroadcastReceiver();
        Logger.d("Started bluetooth");
    }


    public void start() {
        if (bluetoothAdapter == null) {
            return;
        }

        if (audioManager != null) {
            if (!audioManager.isBluetoothScoAvailableOffCall()) {
                Logger.e("☎ Device does not support off call bluetooth SCO");
                return;
            }
        }

        bluetoothAdapter.getProfileProxy(webrtcCallService, bluetoothServiceListener, BluetoothProfile.HEADSET);

        IntentFilter headsetFilter = new IntentFilter();
        headsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        headsetFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        webrtcCallService.registerReceiver(headsetBroadcastReceiver, headsetFilter);

    }

    public void stop() {
        if (bluetoothAdapter == null) {
            return;
        }
        try {
            webrtcCallService.unregisterReceiver(headsetBroadcastReceiver);
        } catch (Exception e) {
            // do nothing
        }
        if (bluetoothHeadset != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
        }
    }

    private void updateBluetoothDevices() {
        if (bluetoothHeadset == null) {
            return;
        }
        try {
            List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
            if (devices.isEmpty()) {
                state = State.HEADSET_UNAVAILABLE;
            } else {
                if (state == State.HEADSET_UNAVAILABLE) {
                    state = State.HEADSET_AVAILABLE;
                }
            }
        } catch (SecurityException e) {
            state = State.HEADSET_UNAVAILABLE;
        }
        webrtcCallService.updateAvailableAudioOutputsList();
    }

    public void connectAudio() {
        if ((state != State.HEADSET_AVAILABLE && state != State.AUDIO_DISCONNECTING) || audioManager == null) {
            return;
        }

        state = State.AUDIO_CONNECTING;
        audioManager.startBluetoothSco();
        audioManager.setBluetoothScoOn(true);
    }

    public void disconnectAudio() {
        if (state != State.AUDIO_CONNECTING && state != State.AUDIO_CONNECTED) {
            return;
        }
        state = State.AUDIO_DISCONNECTING;
        audioManager.stopBluetoothSco();
        audioManager.setBluetoothScoOn(false);
    }

    class BluetoothServiceListener implements BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = (BluetoothHeadset) proxy;
                updateBluetoothDevices();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET) {
                if (bluetoothHeadset != null) {
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
                    bluetoothHeadset = null;
                }
                state = State.HEADSET_UNAVAILABLE;
            }
        }
    }

    private class BluetoothHeadsetBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED: {
                    updateBluetoothDevices();
                    break;
                }
                case BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED: {
                    int audioState = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                    switch (audioState) {
                        case BluetoothHeadset.STATE_AUDIO_CONNECTED: {
                            state = State.AUDIO_CONNECTED;
                            break;
                        }
                        case BluetoothHeadset.STATE_AUDIO_DISCONNECTED: {
                            updateBluetoothDevices();
                            webrtcCallService.bluetoothDisconnected();
                            break;
                        }
                    }
                    break;
                }
            }
        }
    }
}
