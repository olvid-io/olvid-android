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

package io.olvid.engine.datatypes;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.Assert.*;

public class ObvBase64Test {

    @Test
    public void testRandom() throws Exception {
        SecureRandom random = new SecureRandom();
        for (int i=0; i<100; i++) {

            int len = random.nextInt(357)+2;
            byte[] bytes = new byte[len];
            random.nextBytes(bytes);

            String base64 = ObvBase64.encode(bytes);

            byte[] decoded = Base64.getDecoder().decode(base64.replace('_', '/').replace('-', '+'));

            byte[] decoded2 = ObvBase64.decode(base64);

            assertArrayEquals(bytes, decoded);
            assertArrayEquals(bytes, decoded2);

        }
    }

    @Test
    public void testRandomAgain() throws Exception {
        SecureRandom random = new SecureRandom();
        for (int i=0; i<100; i++) {

            int len = random.nextInt(357)+2;
            byte[] bytes = new byte[len];
            random.nextBytes(bytes);

            String base64 = Base64.getEncoder().withoutPadding().encodeToString(bytes).replace('/', '_').replace('+', '-');
            String base642 = ObvBase64.encode(bytes);

            byte[] decoded = ObvBase64.decode(base64);

            assertEquals(base64, base642);
            assertArrayEquals(bytes, decoded);
        }
    }


}