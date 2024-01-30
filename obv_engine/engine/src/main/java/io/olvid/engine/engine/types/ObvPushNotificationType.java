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

package io.olvid.engine.engine.types;

public class ObvPushNotificationType {
    public enum Platform {
        ANDROID,
        WINDOWS,
        LINUX,
        DAEMON,
    }

    public final Platform platform;
    public final String firebaseToken;

    private ObvPushNotificationType(Platform platform, String firebaseToken) {
        this.platform = platform;
        this.firebaseToken = firebaseToken;
    }

    public static ObvPushNotificationType createAndroid(String firebaseToken) {
        return new ObvPushNotificationType(Platform.ANDROID, firebaseToken);
    }

    public static ObvPushNotificationType createWindows() {
        return new ObvPushNotificationType(Platform.WINDOWS, null);
    }

    public static ObvPushNotificationType createLinux() {
        return new ObvPushNotificationType(Platform.LINUX, null);
    }

    public static ObvPushNotificationType createDaemon() {
        return new ObvPushNotificationType(Platform.DAEMON, null);
    }
}
