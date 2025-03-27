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

package io.olvid.engine.engine.types.identities;

import java.util.Objects;

public class ObvOwnedDevice {
    public final byte[] bytesOwnedIdentity;
    public final byte[] bytesDeviceUid;
    public final ServerDeviceInfo serverDeviceInfo;
    public final boolean currentDevice;
    public final boolean channelConfirmed;
    public final boolean hasPreKey;

    public ObvOwnedDevice(byte[] bytesOwnedIdentity, byte[] bytesDeviceUid, ServerDeviceInfo serverDeviceInfo, boolean currentDevice, boolean channelConfirmed, boolean hasPreKey) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesDeviceUid = bytesDeviceUid;
        this.serverDeviceInfo = serverDeviceInfo;
        this.currentDevice = currentDevice;
        this.channelConfirmed = channelConfirmed;
        this.hasPreKey = hasPreKey;
    }

    public static class ServerDeviceInfo {
        public final String displayName;
        public final Long expirationTimestamp;
        public final Long lastRegistrationTimestamp;

        public ServerDeviceInfo(String displayName, Long expirationTimestamp, Long lastRegistrationTimestamp) {
            this.displayName = displayName;
            this.expirationTimestamp = expirationTimestamp;
            this.lastRegistrationTimestamp = lastRegistrationTimestamp;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ServerDeviceInfo) {
                ServerDeviceInfo other = (ServerDeviceInfo) obj;
                return Objects.equals(displayName, other.displayName)
                        && Objects.equals(expirationTimestamp, other.expirationTimestamp)
                        && Objects.equals(lastRegistrationTimestamp, other.lastRegistrationTimestamp);
            }
            return false;
        }
    }

}
