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
 * JUnit Test for the NotSendableException class
 */
public class NotSendableExceptionTest {

  @Test
  public void testConstructor() {
    NotSendableException notSendableException = new NotSendableException("Message");
    assertEquals("The message has to be the argument", "Message",
        notSendableException.getMessage());
    Exception e = new Exception();
    notSendableException = new NotSendableException(e);
    assertEquals("The throwable has to be the argument", e, notSendableException.getCause());
    notSendableException = new NotSendableException("Message", e);
    assertEquals("The message has to be the argument", "Message",
        notSendableException.getMessage());
    assertEquals("The throwable has to be the argument", e, notSendableException.getCause());
  }
}
