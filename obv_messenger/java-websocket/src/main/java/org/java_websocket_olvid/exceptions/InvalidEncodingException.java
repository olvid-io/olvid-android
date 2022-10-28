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

import java.io.UnsupportedEncodingException;

/**
 * The Character Encoding is not supported.
 *
 * @since 1.4.0
 */
public class InvalidEncodingException extends RuntimeException {

  /**
   * attribute for the encoding exception
   */
  private final UnsupportedEncodingException encodingException;

  /**
   * constructor for InvalidEncodingException
   *
   * @param encodingException the cause for this exception
   */
  public InvalidEncodingException(UnsupportedEncodingException encodingException) {
    if (encodingException == null) {
      throw new IllegalArgumentException();
    }
    this.encodingException = encodingException;
  }

  /**
   * Get the exception which includes more information on the unsupported encoding
   *
   * @return an UnsupportedEncodingException
   */
  public UnsupportedEncodingException getEncodingException() {
    return encodingException;
  }
}
