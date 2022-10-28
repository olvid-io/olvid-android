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
import static org.junit.Assert.fail;

import java.io.UnsupportedEncodingException;
import org.junit.Test;

/**
 * JUnit Test for the InvalidEncodingException class
 */
public class InvalidEncodingExceptionTest {

  @Test
  public void testConstructor() {
    UnsupportedEncodingException unsupportedEncodingException = new UnsupportedEncodingException();
    InvalidEncodingException invalidEncodingException = new InvalidEncodingException(
        unsupportedEncodingException);
    assertEquals("The argument has to be the provided exception", unsupportedEncodingException,
        invalidEncodingException.getEncodingException());
    try {
      invalidEncodingException = new InvalidEncodingException(null);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      //Null is not allowed
    }
  }
}
