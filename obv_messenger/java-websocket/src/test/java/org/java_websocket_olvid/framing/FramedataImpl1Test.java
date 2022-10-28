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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import org.java_websocket_olvid.enums.Opcode;
import org.junit.Test;

/**
 * JUnit Test for the FramedataImpl1 class
 */
public class FramedataImpl1Test {

  @Test
  public void testDefaultValues() {
    FramedataImpl1 binary = FramedataImpl1.get(Opcode.BINARY);
    assertEquals("Opcode must be equal", Opcode.BINARY, binary.getOpcode());
    assertEquals("Fin must be set", true, binary.isFin());
    assertEquals("TransferedMask must not be set", false, binary.getTransfereMasked());
    assertEquals("Payload must be empty", 0, binary.getPayloadData().capacity());
    assertEquals("RSV1 must be false", false, binary.isRSV1());
    assertEquals("RSV2 must be false", false, binary.isRSV2());
    assertEquals("RSV3 must be false", false, binary.isRSV3());
  }

  @Test
  public void testGet() {
    FramedataImpl1 binary = FramedataImpl1.get(Opcode.BINARY);
    assertEquals("Frame must be binary", true, binary instanceof BinaryFrame);
    FramedataImpl1 text = FramedataImpl1.get(Opcode.TEXT);
    assertEquals("Frame must be text", true, text instanceof TextFrame);
    FramedataImpl1 closing = FramedataImpl1.get(Opcode.CLOSING);
    assertEquals("Frame must be closing", true, closing instanceof CloseFrame);
    FramedataImpl1 continuous = FramedataImpl1.get(Opcode.CONTINUOUS);
    assertEquals("Frame must be continuous", true, continuous instanceof ContinuousFrame);
    FramedataImpl1 ping = FramedataImpl1.get(Opcode.PING);
    assertEquals("Frame must be ping", true, ping instanceof PingFrame);
    FramedataImpl1 pong = FramedataImpl1.get(Opcode.PONG);
    assertEquals("Frame must be pong", true, pong instanceof PongFrame);
    try {
      FramedataImpl1.get(null);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      //Fine
    }
  }

  @Test
  public void testSetters() {
    FramedataImpl1 frame = FramedataImpl1.get(Opcode.BINARY);
    frame.setFin(false);
    assertEquals("Fin must not be set", false, frame.isFin());
    frame.setTransferemasked(true);
    assertEquals("TransferedMask must be set", true, frame.getTransfereMasked());
    ByteBuffer buffer = ByteBuffer.allocate(100);
    frame.setPayload(buffer);
    assertEquals("Payload must be of size 100", 100, frame.getPayloadData().capacity());
    frame.setRSV1(true);
    assertEquals("RSV1 must be true", true, frame.isRSV1());
    frame.setRSV2(true);
    assertEquals("RSV2 must be true", true, frame.isRSV2());
    frame.setRSV3(true);
    assertEquals("RSV3 must be true", true, frame.isRSV3());
  }

  @Test
  public void testAppend() {
    FramedataImpl1 frame0 = FramedataImpl1.get(Opcode.BINARY);
    frame0.setFin(false);
    frame0.setPayload(ByteBuffer.wrap("first".getBytes()));
    FramedataImpl1 frame1 = FramedataImpl1.get(Opcode.BINARY);
    frame1.setPayload(ByteBuffer.wrap("second".getBytes()));
    frame0.append(frame1);
    assertEquals("Fin must be set", true, frame0.isFin());
    assertArrayEquals("Payload must be equal", "firstsecond".getBytes(),
        frame0.getPayloadData().array());
  }
}
