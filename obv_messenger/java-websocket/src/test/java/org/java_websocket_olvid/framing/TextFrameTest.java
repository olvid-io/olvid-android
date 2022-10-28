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

package org.java_websocket_olvid.framing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import org.java_websocket_olvid.enums.Opcode;
import org.java_websocket_olvid.exceptions.InvalidDataException;
import org.junit.Test;

/**
 * JUnit Test for the TextFrame class
 */
public class TextFrameTest {

  @Test
  public void testConstructor() {
    TextFrame frame = new TextFrame();
    assertEquals("Opcode must be equal", Opcode.TEXT, frame.getOpcode());
    assertEquals("Fin must be set", true, frame.isFin());
    assertEquals("TransferedMask must not be set", false, frame.getTransfereMasked());
    assertEquals("Payload must be empty", 0, frame.getPayloadData().capacity());
    assertEquals("RSV1 must be false", false, frame.isRSV1());
    assertEquals("RSV2 must be false", false, frame.isRSV2());
    assertEquals("RSV3 must be false", false, frame.isRSV3());
    try {
      frame.isValid();
    } catch (InvalidDataException e) {
      fail("InvalidDataException should not be thrown");
    }
  }

  @Test
  public void testExtends() {
    TextFrame frame = new TextFrame();
    assertEquals("Frame must extend dataframe", true, frame instanceof DataFrame);
  }

  @Test
  public void testIsValid() {
    TextFrame frame = new TextFrame();
    try {
      frame.isValid();
    } catch (InvalidDataException e) {
      fail("InvalidDataException should not be thrown");
    }

    frame = new TextFrame();
    frame.setPayload(ByteBuffer.wrap(new byte[]{
        (byte) 0xD0, (byte) 0x9F, // 'П'
        (byte) 0xD1, (byte) 0x80, // 'р'
        (byte) 0xD0,              // corrupted UTF-8, was 'и'
        (byte) 0xD0, (byte) 0xB2, // 'в'
        (byte) 0xD0, (byte) 0xB5, // 'е'
        (byte) 0xD1, (byte) 0x82  // 'т'
    }));
    try {
      frame.isValid();
      fail("InvalidDataException should be thrown");
    } catch (InvalidDataException e) {
      //Utf8 Check should work
    }
  }
}
