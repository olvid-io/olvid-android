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

import org.junit.Test;

/**
 * JUnit Test for the InvalidDataException class
 */
public class InvalidDataExceptionTest {

  @Test
  public void testConstructor() {
    InvalidDataException invalidDataException = new InvalidDataException(42);
    assertEquals("The close code has to be the argument", 42, invalidDataException.getCloseCode());
    invalidDataException = new InvalidDataException(42, "Message");
    assertEquals("The close code has to be the argument", 42, invalidDataException.getCloseCode());
    assertEquals("The message has to be the argument", "Message",
        invalidDataException.getMessage());
    Exception e = new Exception();
    invalidDataException = new InvalidDataException(42, "Message", e);
    assertEquals("The close code has to be the argument", 42, invalidDataException.getCloseCode());
    assertEquals("The message has to be the argument", "Message",
        invalidDataException.getMessage());
    assertEquals("The throwable has to be the argument", e, invalidDataException.getCause());
    invalidDataException = new InvalidDataException(42, e);
    assertEquals("The close code has to be the argument", 42, invalidDataException.getCloseCode());
    assertEquals("The throwable has to be the argument", e, invalidDataException.getCause());
  }
}
