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

import java.nio.ByteBuffer;
import org.java_websocket_olvid.exceptions.InvalidDataException;
import org.junit.Assert;
import org.junit.Test;

public class CharsetfunctionsTest {

  @Test
  public void testAsciiBytes() {
    Assert.assertArrayEquals(new byte[]{102, 111, 111}, Charsetfunctions.asciiBytes("foo"));
  }

  @Test
  public void testStringUtf8ByteBuffer() throws InvalidDataException {
    Assert.assertEquals("foo",
        Charsetfunctions.stringUtf8(ByteBuffer.wrap(new byte[]{102, 111, 111})));
  }


  @Test
  public void testIsValidUTF8off() {
    Assert.assertFalse(Charsetfunctions.isValidUTF8(ByteBuffer.wrap(new byte[]{100}), 2));
    Assert.assertFalse(Charsetfunctions.isValidUTF8(ByteBuffer.wrap(new byte[]{(byte) 128}), 0));

    Assert.assertTrue(Charsetfunctions.isValidUTF8(ByteBuffer.wrap(new byte[]{100}), 0));
  }

  @Test
  public void testIsValidUTF8() {
    Assert.assertFalse(Charsetfunctions.isValidUTF8(ByteBuffer.wrap(new byte[]{(byte) 128})));

    Assert.assertTrue(Charsetfunctions.isValidUTF8(ByteBuffer.wrap(new byte[]{100})));
  }

  @Test
  public void testStringAscii1() {
    Assert.assertEquals("oBar",
        Charsetfunctions.stringAscii(new byte[]{102, 111, 111, 66, 97, 114}, 2, 4));

  }

  @Test
  public void testStringAscii2() {
    Assert.assertEquals("foo", Charsetfunctions.stringAscii(new byte[]{102, 111, 111}));
  }

  @Test
  public void testUtf8Bytes() {
    Assert.assertArrayEquals(new byte[]{102, 111, 111, 66, 97, 114},
        Charsetfunctions.utf8Bytes("fooBar"));
  }
}
