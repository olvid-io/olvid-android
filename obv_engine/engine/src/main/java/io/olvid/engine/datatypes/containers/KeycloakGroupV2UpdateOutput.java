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

package io.olvid.engine.datatypes.containers;

import java.util.List;

import io.olvid.engine.datatypes.Identity;

public class KeycloakGroupV2UpdateOutput {
    public final byte[] ownInvitationNonce;
    public final boolean photoNeedsToBeDownloaded;
    public final List<Identity> membersWithNewInvitationNonce;

    public KeycloakGroupV2UpdateOutput(byte[] ownInvitationNonce, boolean photoNeedsToBeDownloaded, List<Identity> membersWithNewInvitationNonce) {
        this.ownInvitationNonce = ownInvitationNonce;
        this.photoNeedsToBeDownloaded = photoNeedsToBeDownloaded;
        this.membersWithNewInvitationNonce = membersWithNewInvitationNonce;
    }
}
