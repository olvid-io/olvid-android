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


import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;

public class AuthEncKeyAndChannelInfo {
    private final AuthEncKey authEncKey;
    private final ReceptionChannelInfo receptionChannelInfo;

    public AuthEncKeyAndChannelInfo(AuthEncKey authEncKey, ReceptionChannelInfo receptionChannelInfo) {
        this.authEncKey = authEncKey;
        this.receptionChannelInfo = receptionChannelInfo;
    }

    public AuthEncKey getAuthEncKey() {
        return authEncKey;
    }

    public ReceptionChannelInfo getReceptionChannelInfo() {
        return receptionChannelInfo;
    }
}
