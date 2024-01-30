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

package io.olvid.messenger.webrtc;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

import java.io.IOException;

import io.olvid.engine.Logger;
import io.olvid.messenger.R;

public class OutgoingCallRinger {
    private final Context context;

    private MediaPlayer mediaPlayer;

    private AssetFileDescriptor afd;

    public OutgoingCallRinger(Context context) {
        this.context = context;
    }

    public enum Type {
        RING,
        BUSY
    }

    public void ring(Type ringType) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        try {
            mediaPlayer = new MediaPlayer();
            int resourceId = 0;
            switch (ringType) {
                case RING:
                    resourceId = R.raw.ringing;
                    break;
                case BUSY:
                    resourceId = R.raw.busy;
                    break;
            }
            if (afd != null) {
                afd.close();
                afd = null;
            }
            afd = context.getResources().openRawResourceFd(resourceId);
            if (afd == null) {
                mediaPlayer = null;
                return;
            }
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mediaPlayer.setLooping(true);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                    .build());
        } catch (IOException e) {
            e.printStackTrace();
            Logger.w("☎ Error initializing media player for outgoing call ringing");
            mediaPlayer = null;
        }

        if (mediaPlayer != null) {
            try {
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                }
            } catch (IllegalStateException | IOException e) {
                e.printStackTrace();
                mediaPlayer = null;
            }
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (afd != null) {
            try {
                afd.close();
            } catch (Exception ignored) { }
            afd = null;
        }
    }
}
