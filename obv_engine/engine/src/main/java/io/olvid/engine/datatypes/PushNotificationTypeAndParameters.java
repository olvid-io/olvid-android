/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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
    public static final byte PUSH_NOTIFICATION_TYPE_NONE = (byte) 0xff;

    public final byte pushNotificationType;
    public final byte[] token;
    public UID identityMaskingUid; // not taken into account when comparing, this is supposed to always be random. Not final so we can sometimes copy old maskingUid
    public boolean kickOtherDevices; // not taken into account when comparing. Not final so we can sometimes preserve the previous kickMode
    public final boolean useMultiDevice;

    public PushNotificationTypeAndParameters(byte pushNotificationType, byte[] token, UID identityMaskingUid, boolean kickOtherDevices, boolean useMultiDevice) {
        this.pushNotificationType = pushNotificationType;
        this.token = token;
        this.identityMaskingUid = identityMaskingUid;
        this.kickOtherDevices = kickOtherDevices;
        this.useMultiDevice = useMultiDevice;
    }

    public static PushNotificationTypeAndParameters createWebsocketOnly(boolean kickOtherDevices, boolean useMultidevice) {
        return new PushNotificationTypeAndParameters(PUSH_NOTIFICATION_TYPE_NONE, null, null, kickOtherDevices, useMultidevice);
    }

    public static PushNotificationTypeAndParameters createFirebaseAndroid(byte[] token, UID identityMaskingUid, boolean kickOtherDevices, boolean useMultidevice) {
        return new PushNotificationTypeAndParameters(PUSH_NOTIFICATION_TYPE_ANDROID, token, identityMaskingUid, kickOtherDevices, useMultidevice);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PushNotificationTypeAndParameters) {
            PushNotificationTypeAndParameters other = (PushNotificationTypeAndParameters) o;
            if (pushNotificationType != other.pushNotificationType || useMultiDevice != other.useMultiDevice) {
                return false;
            }
            switch (pushNotificationType) {
                case PUSH_NOTIFICATION_TYPE_NONE:
                    return true;
                case PUSH_NOTIFICATION_TYPE_ANDROID:
                    return Arrays.equals(token, other.token);
            }
        }
        return false;
    }
}
