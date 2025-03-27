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

package io.olvid.engine.engine.types;

import java.util.HashMap;

import io.olvid.engine.engine.types.identities.ObvOwnedDevice;

public class ObvDeviceList {
    public final Boolean multiDevice; // null if the server is not able to determine if the user has multi-device permission
    public final HashMap<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> deviceUidsAndServerInfo;

    public ObvDeviceList(Boolean multiDevice, HashMap<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> deviceUidsAndServerInfo) {
        this.deviceUidsAndServerInfo = deviceUidsAndServerInfo;
        this.multiDevice = multiDevice;
    }
}
