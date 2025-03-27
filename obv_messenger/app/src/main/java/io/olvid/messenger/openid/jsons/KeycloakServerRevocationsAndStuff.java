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

package io.olvid.messenger.openid.jsons;

import java.util.List;
import java.util.Map;

public class KeycloakServerRevocationsAndStuff {
    public final boolean revocationAllowed;
    public final boolean transferRestricted;
    public final long currentServerTimestamp;
    public final List<String> signedRevocations;
    public final Map<String, Integer> minimumBuildVersions;

    public KeycloakServerRevocationsAndStuff(boolean revocationAllowed, boolean transferRestricted, long currentServerTimestamp, List<String> signedRevocations, Map<String, Integer> minimumBuildVersions) {
        this.revocationAllowed = revocationAllowed;
        this.transferRestricted = transferRestricted;
        this.currentServerTimestamp = currentServerTimestamp;
        this.signedRevocations = signedRevocations;
        this.minimumBuildVersions = minimumBuildVersions;
    }
}
