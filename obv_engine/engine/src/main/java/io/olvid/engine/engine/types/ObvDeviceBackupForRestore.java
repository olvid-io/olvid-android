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

import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;


public class ObvDeviceBackupForRestore {
    public final Status status;
    public final List<ObvDeviceBackupProfile> profiles; // null in case of error
    public final ObvSyncSnapshotNode appDeviceBackupSnapshot; // null in case of error

    public ObvDeviceBackupForRestore(Status status, List<ObvDeviceBackupProfile> profiles, ObvSyncSnapshotNode appDeviceBackupSnapshot) {
        this.status = status;
        this.profiles = profiles;
        this.appDeviceBackupSnapshot = appDeviceBackupSnapshot;
    }

    public static class ObvDeviceBackupProfile {
        public byte[] bytesProfileIdentity;
        public JsonIdentityDetailsWithVersionAndPhoto identityDetails;
        public boolean keycloakManaged;
        public String profileBackupSeed;
    }


    public enum Status {
        SUCCESS,
        NETWORK_ERROR,
        PERMANENT_ERROR, // unknown key, or invalid snapshot
        ERROR,
    }
}
