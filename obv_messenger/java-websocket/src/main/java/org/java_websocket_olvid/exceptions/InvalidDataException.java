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

/**
 * exception which indicates that a invalid data was received
 */
public class InvalidDataException extends Exception {

  /**
   * Serializable
   */
  private static final long serialVersionUID = 3731842424390998726L;

  /**
   * attribute which closecode will be returned
   */
  private final int closecode;

  /**
   * constructor for a InvalidDataException
   *
   * @param closecode the closecode which will be returned
   */
  public InvalidDataException(int closecode) {
    this.closecode = closecode;
  }

  /**
   * constructor for a InvalidDataException.
   *
   * @param closecode the closecode which will be returned.
   * @param s         the detail message.
   */
  public InvalidDataException(int closecode, String s) {
    super(s);
    this.closecode = closecode;
  }

  /**
   * constructor for a InvalidDataException.
   *
   * @param closecode the closecode which will be returned.
   * @param t         the throwable causing this exception.
   */
  public InvalidDataException(int closecode, Throwable t) {
    super(t);
    this.closecode = closecode;
  }

  /**
   * constructor for a InvalidDataException.
   *
   * @param closecode the closecode which will be returned.
   * @param s         the detail message.
   * @param t         the throwable causing this exception.
   */
  public InvalidDataException(int closecode, String s, Throwable t) {
    super(s, t);
    this.closecode = closecode;
  }

  /**
   * Getter closecode
   *
   * @return the closecode
   */
  public int getCloseCode() {
    return closecode;
  }

}
