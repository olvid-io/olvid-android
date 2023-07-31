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

package io.olvid.messenger.databases.entity.sync;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import junit.framework.TestCase;

import java.util.HashMap;

import io.olvid.engine.engine.types.ObvBytesKey;
import io.olvid.engine.engine.types.ObvGroupOwnerAndUidKey;

public class OwnedIdentitySyncSnapshotTest extends TestCase {
    public void testSerialization() {
        OwnedIdentitySyncSnapshot ownedIdentitySyncSnapshot = new OwnedIdentitySyncSnapshot();
        ownedIdentitySyncSnapshot.custom_name = "Toto";
        ownedIdentitySyncSnapshot.contacts = new HashMap<>();
        ownedIdentitySyncSnapshot.groups = new HashMap<>();

        ContactSyncSnapshot contactSyncSnapshot = new ContactSyncSnapshot();
        ownedIdentitySyncSnapshot.contacts.put(new ObvBytesKey(new byte[]{0x12, 0x13, 0x15, 0x78}), contactSyncSnapshot);

        GroupV1SyncSnapshot groupV1SyncSnapshot = new GroupV1SyncSnapshot();
        ownedIdentitySyncSnapshot.groups.put(new ObvGroupOwnerAndUidKey(new byte[]{0x12, 0x13, 0x15, 0x78}, new byte[]{0x15, (byte) 0x96, 0x67}), groupV1SyncSnapshot);


        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try {
            String serialized = objectMapper.writeValueAsString(ownedIdentitySyncSnapshot);

            System.out.println(serialized);

            OwnedIdentitySyncSnapshot deserialized = objectMapper.readValue(serialized, OwnedIdentitySyncSnapshot.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}