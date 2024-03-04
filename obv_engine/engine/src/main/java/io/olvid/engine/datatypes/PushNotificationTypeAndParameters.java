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

package io.olvid.engine.datatypes;

import java.util.Arrays;

public class PushNotificationTypeAndParameters {
    public static final byte PUSH_NOTIFICATION_TYPE_ANDROID = 0x01;
    public static final byte PUSH_NOTIFICATION_TYPE_WEBSOCKET_ANDROID = 0x10;
    public static final byte PUSH_NOTIFICATION_TYPE_WEBSOCKET_WINDOWS = 0x11;
    public static final byte PUSH_NOTIFICATION_TYPE_WEBSOCKET_LINUX = 0x12;
    public static final byte PUSH_NOTIFICATION_TYPE_WEBSOCKET_DAEMON = 0x13;

    //    public static final byte PUSH_NOTIFICATION_TYPE_ANDROID_EXPERIMENT = (byte) (0x80);
    //    public static final byte PUSH_NOTIFICATION_TYPE_NONE = (byte) 0xff;

    public final byte pushNotificationType;
    public final byte[] token;
    public UID identityMaskingUid; // not taken into account when comparing, this is supposed to always be random. Not final so we can sometimes copy old maskingUid
    public boolean reactivateCurrentDevice; // not taken into account when comparing. Not final so we can sometimes preserve the previous kickMode
    public UID deviceUidToReplace; // deviceUID of the device to deactivate when reactivateCurrentDevice is set.

    public PushNotificationTypeAndParameters(byte pushNotificationType, byte[] token, UID identityMaskingUid, boolean reactivateCurrentDevice, UID deviceUidToReplace) {
        this.pushNotificationType = pushNotificationType;
        this.token = token;
        this.identityMaskingUid = identityMaskingUid;
        this.reactivateCurrentDevice = reactivateCurrentDevice;
        this.deviceUidToReplace = deviceUidToReplace;
    }

    public static PushNotificationTypeAndParameters createWebsocketOnlyAndroid(boolean reactivateCurrentDevice, UID deviceUidToReplace) {
        return new PushNotificationTypeAndParameters(PUSH_NOTIFICATION_TYPE_WEBSOCKET_ANDROID, null, null, reactivateCurrentDevice, deviceUidToReplace);
    }

    public static PushNotificationTypeAndParameters createWindows(boolean reactivateCurrentDevice, UID deviceUidToReplace) {
        return new PushNotificationTypeAndParameters(PUSH_NOTIFICATION_TYPE_WEBSOCKET_WINDOWS, null, null, reactivateCurrentDevice, deviceUidToReplace);
    }

    public static PushNotificationTypeAndParameters createLinux(boolean reactivateCurrentDevice, UID deviceUidToReplace) {
        return new PushNotificationTypeAndParameters(PUSH_NOTIFICATION_TYPE_WEBSOCKET_LINUX, null, null, reactivateCurrentDevice, deviceUidToReplace);
    }

    public static PushNotificationTypeAndParameters createDaemon(boolean reactivateCurrentDevice, UID deviceUidToReplace) {
        return new PushNotificationTypeAndParameters(PUSH_NOTIFICATION_TYPE_WEBSOCKET_DAEMON, null, null, reactivateCurrentDevice, deviceUidToReplace);
    }

    public static PushNotificationTypeAndParameters createFirebaseAndroid(byte[] token, UID identityMaskingUid, boolean reactivateCurrentDevice, UID deviceUidToReplace) {
        return new PushNotificationTypeAndParameters(PUSH_NOTIFICATION_TYPE_ANDROID, token, identityMaskingUid, reactivateCurrentDevice, deviceUidToReplace);
    }

    public boolean sameTypeAndToken(PushNotificationTypeAndParameters other) {
        if (pushNotificationType != other.pushNotificationType) {
            return false;
        }
        switch (pushNotificationType) {
            case PUSH_NOTIFICATION_TYPE_WEBSOCKET_ANDROID:
            case PUSH_NOTIFICATION_TYPE_WEBSOCKET_WINDOWS:
            case PUSH_NOTIFICATION_TYPE_WEBSOCKET_LINUX:
            case PUSH_NOTIFICATION_TYPE_WEBSOCKET_DAEMON:
                return true;
            case PUSH_NOTIFICATION_TYPE_ANDROID:
                return Arrays.equals(token, other.token);
            default:
                return false;
        }
    }
}
