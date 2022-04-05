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

import android.content.Context;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;

import java.util.concurrent.Executor;

public class PhoneCallStateListener {
    final WebrtcCallService service;
    final TelephonyManager telephonyManager;
    final Listener listener;
    final Callback callback;
    boolean unmuteOnIdle = false;

    public PhoneCallStateListener(WebrtcCallService service, Executor executor) {
        this.service = service;
        this.telephonyManager = (TelephonyManager) service.getSystemService(Context.TELEPHONY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listener = null;
            callback = new Callback();
            telephonyManager.registerTelephonyCallback(executor, callback);
        } else {
            listener = new Listener();
            callback = null;
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
        }
    }

    public void unregister() {
        if (telephonyManager != null) {
            if (listener != null) {
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && callback != null) {
                telephonyManager.unregisterTelephonyCallback(callback);
            }
        }
    }


    private void handleStateChange(int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE: {
                if (unmuteOnIdle) {
                    unmuteOnIdle = false;
                    service.toggleMuteMicrophone();
                }
                // nothing to do, the phone is idle
                break;
            }
            case TelephonyManager.CALL_STATE_RINGING: {
                // nothing to do, the phone is ringing
                break;
            }
            case TelephonyManager.CALL_STATE_OFFHOOK: {
                Boolean microphoneMuted = service.getMicrophoneMuted().getValue();
                if (microphoneMuted == null || !microphoneMuted) {
                    unmuteOnIdle = true;
                    service.toggleMuteMicrophone();
                }
            }
        }
    }


    class Listener extends PhoneStateListener {
        public Listener() {
            super();
        }

        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            super.onCallStateChanged(state, phoneNumber);
            handleStateChange(state);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class Callback extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            handleStateChange(state);
        }
    }
}
