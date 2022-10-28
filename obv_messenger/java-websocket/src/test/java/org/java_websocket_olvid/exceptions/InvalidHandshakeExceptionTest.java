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
 * JUnit Test for the InvalidHandshakeException class
 */
public class InvalidHandshakeExceptionTest {

  @Test
  public void testConstructor() {
    InvalidHandshakeException invalidHandshakeException = new InvalidHandshakeException();
    assertEquals("The close code has to be PROTOCOL_ERROR", CloseFrame.PROTOCOL_ERROR,
        invalidHandshakeException.getCloseCode());
    invalidHandshakeException = new InvalidHandshakeException("Message");
    assertEquals("The close code has to be PROTOCOL_ERROR", CloseFrame.PROTOCOL_ERROR,
        invalidHandshakeException.getCloseCode());
    assertEquals("The message has to be the argument", "Message",
        invalidHandshakeException.getMessage());
    Exception e = new Exception();
    invalidHandshakeException = new InvalidHandshakeException("Message", e);
    assertEquals("The close code has to be PROTOCOL_ERROR", CloseFrame.PROTOCOL_ERROR,
        invalidHandshakeException.getCloseCode());
    assertEquals("The message has to be the argument", "Message",
        invalidHandshakeException.getMessage());
    assertEquals("The throwable has to be the argument", e, invalidHandshakeException.getCause());
    invalidHandshakeException = new InvalidHandshakeException(e);
    assertEquals("The close code has to be PROTOCOL_ERROR", CloseFrame.PROTOCOL_ERROR,
        invalidHandshakeException.getCloseCode());
    assertEquals("The throwable has to be the argument", e, invalidHandshakeException.getCause());

  }

  @Test
  public void testExtends() {
    InvalidHandshakeException invalidHandshakeException = new InvalidHandshakeException();
    assertEquals("InvalidHandshakeException must extend InvalidDataException", true,
        invalidHandshakeException instanceof InvalidDataException);
  }
}
