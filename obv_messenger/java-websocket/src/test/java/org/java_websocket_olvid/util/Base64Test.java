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

package org.java_websocket_olvid.util;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class Base64Test {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testEncodeBytes() throws IOException {
    Assert.assertEquals("", Base64.encodeBytes(new byte[0]));
    Assert.assertEquals("QHE=",
        Base64.encodeBytes(new byte[]{49, 121, 64, 113, -63, 43, -24, 62, 4, 48}, 2, 2, 0));
    Assert.assertEquals("H4sIAAAAAAAAADMEALfv3IMBAAAA",
        Base64.encodeBytes(new byte[]{49, 121, 64, 113, -63, 43, -24, 62, 4, 48}, 0, 1, 6));
    Assert.assertEquals("H4sIAAAAAAAAAHMoBABQHKKWAgAAAA==",
        Base64.encodeBytes(new byte[]{49, 121, 64, 113, -63, 43, -24, 62, 4, 48}, 2, 2, 18));
    Assert.assertEquals("F63=",
        Base64.encodeBytes(new byte[]{49, 121, 64, 113, 63, 43, -24, 62, 4, 48}, 2, 2, 32));
    Assert.assertEquals("6sg7---------6Bc0-0F699L-V----==",
        Base64.encodeBytes(new byte[]{49, 121, 64, 113, 63, 43, -24, 62, 4, 48}, 2, 2, 34));
  }

  @Test
  public void testEncodeBytes2() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    Base64.encodeBytes(new byte[0], -2, -2, -56);
  }

  @Test
  public void testEncodeBytes3() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    Base64.encodeBytes(new byte[]{64, -128, 32, 18, 16, 16, 0, 18, 16},
        2064072977, -2064007440, 10);
  }

  @Test
  public void testEncodeBytes4() {
    thrown.expect(NullPointerException.class);
    Base64.encodeBytes(null);
  }

  @Test
  public void testEncodeBytes5() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    Base64.encodeBytes(null, 32766, 0, 8);
  }

  @Test
  public void testEncodeBytesToBytes1() throws IOException {
    Assert.assertArrayEquals(new byte[]{95, 68, 111, 78, 55, 45, 61, 61},
        Base64.encodeBytesToBytes(new byte[]{-108, -19, 24, 32}, 0, 4, 32));
    Assert.assertArrayEquals(new byte[]{95, 68, 111, 78, 55, 67, 111, 61},
        Base64.encodeBytesToBytes(new byte[]{-108, -19, 24, 32, -35}, 0, 5, 40));
    Assert.assertArrayEquals(new byte[]{95, 68, 111, 78, 55, 67, 111, 61},
        Base64.encodeBytesToBytes(new byte[]{-108, -19, 24, 32, -35}, 0, 5, 32));
    Assert.assertArrayEquals(new byte[]{87, 50, 77, 61},
        Base64.encodeBytesToBytes(new byte[]{115, 42, 123, 99, 10, -33, 75, 30, 91, 99}, 8, 2, 48));
    Assert.assertArrayEquals(new byte[]{87, 50, 77, 61},
        Base64.encodeBytesToBytes(new byte[]{115, 42, 123, 99, 10, -33, 75, 30, 91, 99}, 8, 2, 56));
    Assert.assertArrayEquals(new byte[]{76, 53, 66, 61},
        Base64.encodeBytesToBytes(new byte[]{113, 42, 123, 99, 10, -33, 75, 30, 88, 99}, 8, 2, 36));
    Assert.assertArrayEquals(new byte[]{87, 71, 77, 61},
        Base64.encodeBytesToBytes(new byte[]{113, 42, 123, 99, 10, -33, 75, 30, 88, 99}, 8, 2, 4));
  }

  @Test
  public void testEncodeBytesToBytes2() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    Base64.encodeBytesToBytes(new byte[]{83, 10, 91, 67, 42, -1, 107, 62, 91, 67}, 8, 6, 26);
  }

  @Test
  public void testEncodeBytesToBytes3() throws IOException {
    byte[] src = new byte[]{
        113, 42, 123, 99, 10, -33, 75, 30, 88, 99,
        113, 42, 123, 99, 10, -33, 75, 31, 88, 99,
        113, 42, 123, 99, 10, -33, 75, 32, 88, 99,
        113, 42, 123, 99, 10, -33, 75, 33, 88, 99,
        113, 42, 123, 99, 10, -33, 75, 34, 88, 99,
        113, 42, 123, 99, 10, -33, 75, 35, 88, 99,
        55, 60
    };
    byte[] excepted = new byte[]{
        99, 83, 112, 55, 89, 119, 114, 102, 83, 120,
        53, 89, 89, 51, 69, 113, 101, 50, 77, 75, 51,
        48, 115, 102, 87, 71, 78, 120, 75, 110, 116, 106,
        67, 116, 57, 76, 73, 70, 104, 106, 99, 83, 112,
        55, 89, 119, 114, 102, 83, 121, 70, 89, 89,
        51, 69, 113, 101, 50, 77, 75, 51, 48, 115,
        105, 87, 71, 78, 120, 75, 110, 116, 106, 67,
        116, 57, 76, 10, 73, 49, 104, 106, 78, 122,
        119, 61
    };

    Assert.assertArrayEquals(excepted, Base64.encodeBytesToBytes(src, 0, 62, 8));
  }
}
