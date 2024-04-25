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

package io.olvid.engine.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.net.URL;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.BackupSeed;

public class BackupManagerTest {

    @Test
    public void testEquivString() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        URL jsonURL = getClass().getClassLoader().getResource("TestVectorsEquivalentBackupSeedString.json");
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        EquivString[] equivStrings = mapper.readValue(jsonURL, new TypeReference<EquivString[]>(){});
        for (EquivString equivString: equivStrings) {
            assertEquals(new BackupSeed(equivString.backupSeedString1), new BackupSeed(equivString.backupSeedString2));
        }
    }

    @Test
    public void testStringAndSeed() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        URL jsonURL = getClass().getClassLoader().getResource("TestVectorsBackupSeedFromString.json");
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        SeedAndString[] seedAndStrings = mapper.readValue(jsonURL, new TypeReference<SeedAndString[]>(){});
        for (SeedAndString seedAndString: seedAndStrings) {
            assertArrayEquals(new BackupSeed(seedAndString.backupSeedString).getBackupSeedBytes(), Logger.fromHexString(seedAndString.backupSeed));
        }
    }

    static class SeedAndString {
        String backupSeed;
        String backupSeedString;

        public SeedAndString() {
        }

        public String getBackupSeed() {
            return backupSeed;
        }

        public void setBackupSeed(String backupSeed) {
            this.backupSeed = backupSeed;
        }

        public String getBackupSeedString() {
            return backupSeedString;
        }

        public void setBackupSeedString(String backupSeedString) {
            this.backupSeedString = backupSeedString;
        }
    }

    static class EquivString {
        String backupSeedString1;
        String backupSeedString2;

        public EquivString() {
        }

        public String getBackupSeedString1() {
            return backupSeedString1;
        }

        public void setBackupSeedString1(String backupSeedString1) {
            this.backupSeedString1 = backupSeedString1;
        }

        public String getBackupSeedString2() {
            return backupSeedString2;
        }

        public void setBackupSeedString2(String backupSeedString2) {
            this.backupSeedString2 = backupSeedString2;
        }
    }
}
