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


public class ObvContactInfo {
    public final byte[] bytesOwnedIdentity;
    public final byte[] bytesContactIdentity;
    public final JsonIdentityDetails identityDetails;
    public final String photoUrl;
    public final boolean keycloakManaged;
    public final boolean active;
    public final boolean oneToOne;
    public final int trustLevel;
    public final boolean recentlyOnline;
    public final ObvContactDeviceCount contactDeviceCount;

    public ObvContactInfo(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, JsonIdentityDetails identityDetails, boolean keycloakManaged, boolean oneToOne, String photoUrl, boolean active, boolean recentlyOnline, int trustLevel, ObvContactDeviceCount contactDeviceCount) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
        this.identityDetails = identityDetails;
        this.keycloakManaged = keycloakManaged;
        this.oneToOne = oneToOne;
        this.photoUrl = photoUrl;
        this.active = active;
        this.recentlyOnline = recentlyOnline;
        this.trustLevel = trustLevel;
        this.contactDeviceCount = contactDeviceCount;
    }
}
