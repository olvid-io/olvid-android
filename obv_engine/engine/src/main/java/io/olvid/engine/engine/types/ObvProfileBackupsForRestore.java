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


import java.util.List;
import java.util.Map;

import io.olvid.engine.engine.types.sync.ObvSyncSnapshot;


public class ObvProfileBackupsForRestore {
    public final Status status;
    public final List<ObvProfileBackupForRestore> snapshots; // null in case of error
    public final ObvDeviceList deviceList; // null in case of error or if snapshots is empty

    public ObvProfileBackupsForRestore(Status status, List<ObvProfileBackupForRestore> snapshots, ObvDeviceList deviceList) {
        this.status = status;
        this.snapshots = snapshots;
        this.deviceList = deviceList;
    }

    public static class ObvProfileBackupForRestore {
        public byte[] bytesBackupThreadId;
        public long version;
        public long timestamp;
        public boolean fromThisDevice;
        public Map<String, String> additionalInfo;
        public int contactCount;
        public int groupCount;
        public KeycloakStatus keycloakStatus;
        public String keycloakServerUrl;
        public String keycloakClientId;
        public String keycloakClientSecret;
        public ObvSyncSnapshot snapshot;
    }


    public enum Status {
        SUCCESS,
        NETWORK_ERROR,
        PERMANENT_ERROR, // unknown key, or invalid snapshot
        ERROR,
        TRUNCATED,
    }

    public enum KeycloakStatus {
        UNMANAGED,
        MANAGED,
        TRANSFER_RESTRICTED,
    }
}
