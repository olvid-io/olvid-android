/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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

package io.olvid.messenger.webclient.listeners;

import java.util.Arrays;

import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.webclient.WebClientManager;
import io.olvid.messenger.webclient.protobuf.ColissimoOuterClass;

public class OwnedIdentityObserver implements androidx.lifecycle.Observer<OwnedIdentity> {
    private final WebClientManager manager;
    private byte[] bytesCurrentOwnedIdentity;

    public OwnedIdentityObserver(WebClientManager manager, byte[] bytesCurrentOwnedIdentity) {
        this.manager = manager;
        this.bytesCurrentOwnedIdentity = bytesCurrentOwnedIdentity;
    }

    @Override
    public void onChanged(OwnedIdentity ownedIdentity) {
        if (!Arrays.equals(bytesCurrentOwnedIdentity, ownedIdentity.bytesOwnedIdentity)) {
            this.bytesCurrentOwnedIdentity = ownedIdentity.bytesOwnedIdentity;
            // send a message to webclient to tell him to start a refresh protocol
            // (do not update owned identity it will be done in refresh protocol to avoid breaking currently working listeners)
            this.manager.sendColissimo(ColissimoOuterClass.Colissimo.newBuilder().setType(ColissimoOuterClass.ColissimoType.REFRESH).build());
        }
    }
}
