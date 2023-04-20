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

package io.olvid.engine.identity.datatypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakGroupMemberKickedData {
    public byte[] groupUid;
    public byte[] identity;
    public long timestamp;

    public KeycloakGroupMemberKickedData() {
    }

    public KeycloakGroupMemberKickedData(byte[] groupUid, byte[] identity, long timestamp) {
        this.groupUid = groupUid;
        this.identity = identity;
        this.timestamp = timestamp;
    }

    public byte[] getGroupUid() {
        return groupUid;
    }

    public void setGroupUid(byte[] groupUid) {
        this.groupUid = groupUid;
    }

    public byte[] getIdentity() {
        return identity;
    }

    public void setIdentity(byte[] identity) {
        this.identity = identity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}