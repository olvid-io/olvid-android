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

package org.java_websocket_olvid.exceptions;

import static org.junit.Assert.assertEquals;

import org.java_websocket_olvid.framing.CloseFrame;
import org.junit.Test;

/**
 * JUnit Test for the InvalidFrameException class
 */
public class InvalidFrameExceptionTest {

  @Test
  public void testConstructor() {
    InvalidFrameException invalidFrameException = new InvalidFrameException();
    assertEquals("The close code has to be PROTOCOL_ERROR", CloseFrame.PROTOCOL_ERROR,
        invalidFrameException.getCloseCode());
    invalidFrameException = new InvalidFrameException("Message");
    assertEquals("The close code has to be PROTOCOL_ERROR", CloseFrame.PROTOCOL_ERROR,
        invalidFrameException.getCloseCode());
    assertEquals("The message has to be the argument", "Message",
        invalidFrameException.getMessage());
    Exception e = new Exception();
    invalidFrameException = new InvalidFrameException("Message", e);
    assertEquals("The close code has to be PROTOCOL_ERROR", CloseFrame.PROTOCOL_ERROR,
        invalidFrameException.getCloseCode());
    assertEquals("The message has to be the argument", "Message",
        invalidFrameException.getMessage());
    assertEquals("The throwable has to be the argument", e, invalidFrameException.getCause());
    invalidFrameException = new InvalidFrameException(e);
    assertEquals("The close code has to be PROTOCOL_ERROR", CloseFrame.PROTOCOL_ERROR,
        invalidFrameException.getCloseCode());
    assertEquals("The throwable has to be the argument", e, invalidFrameException.getCause());
  }

  @Test
  public void testExtends() {
    InvalidFrameException invalidFrameException = new InvalidFrameException();
    assertEquals("InvalidFrameException must extend InvalidDataException", true,
        invalidFrameException instanceof InvalidDataException);
  }
}
