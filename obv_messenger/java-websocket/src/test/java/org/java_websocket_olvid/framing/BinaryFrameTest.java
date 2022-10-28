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

import org.java_websocket_olvid.enums.Opcode;
import org.java_websocket_olvid.exceptions.InvalidDataException;
import org.junit.Test;

/**
 * JUnit Test for the BinaryFrame class
 */
public class BinaryFrameTest {

  @Test
  public void testConstructor() {
    BinaryFrame frame = new BinaryFrame();
    assertEquals("Opcode must be equal", Opcode.BINARY, frame.getOpcode());
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
    BinaryFrame frame = new BinaryFrame();
    assertEquals("Frame must extend dataframe", true, frame instanceof DataFrame);
  }

  @Test
  public void testIsValid() {
    BinaryFrame frame = new BinaryFrame();
    try {
      frame.isValid();
    } catch (InvalidDataException e) {
      fail("InvalidDataException should not be thrown");
    }
  }
}
