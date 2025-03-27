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

package io.olvid.engine.datatypes.containers;


import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.engine.types.identities.ObvOwnedDevice;

public class OwnedDeviceAndPreKey {
    public final Identity ownedIdentity;
    public final UID deviceUid;
    public final boolean currentDevice;
    public final PreKey preKey;
    public final ObvOwnedDevice.ServerDeviceInfo serverDeviceInfo;

    public OwnedDeviceAndPreKey(Identity ownedIdentity, UID deviceUid, boolean currentDevice, PreKey preKey, ObvOwnedDevice.ServerDeviceInfo serverDeviceInfo) {
        this.ownedIdentity = ownedIdentity;
        this.deviceUid = deviceUid;
        this.currentDevice = currentDevice;
        this.preKey = preKey;
        this.serverDeviceInfo = serverDeviceInfo;
    }
}
