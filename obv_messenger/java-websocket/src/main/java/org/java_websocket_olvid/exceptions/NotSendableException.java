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
 * exception which indicates the frame payload is not sendable
 */
public class NotSendableException extends RuntimeException {

  /**
   * Serializable
   */
  private static final long serialVersionUID = -6468967874576651628L;

  /**
   * constructor for a NotSendableException
   *
   * @param s the detail message.
   */
  public NotSendableException(String s) {
    super(s);
  }

  /**
   * constructor for a NotSendableException
   *
   * @param t the throwable causing this exception.
   */
  public NotSendableException(Throwable t) {
    super(t);
  }

  /**
   * constructor for a NotSendableException
   *
   * @param s the detail message.
   * @param t the throwable causing this exception.
   */
  public NotSendableException(String s, Throwable t) {
    super(s, t);
  }

}
