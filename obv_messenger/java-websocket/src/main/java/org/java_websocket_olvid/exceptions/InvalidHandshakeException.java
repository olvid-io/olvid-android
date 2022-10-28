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

import org.java_websocket_olvid.framing.CloseFrame;

/**
 * exception which indicates that a invalid handshake was received (CloseFrame.PROTOCOL_ERROR)
 */
public class InvalidHandshakeException extends InvalidDataException {

  /**
   * Serializable
   */
  private static final long serialVersionUID = -1426533877490484964L;

  /**
   * constructor for a InvalidHandshakeException
   * <p>
   * calling InvalidDataException with closecode PROTOCOL_ERROR
   */
  public InvalidHandshakeException() {
    super(CloseFrame.PROTOCOL_ERROR);
  }

  /**
   * constructor for a InvalidHandshakeException
   * <p>
   * calling InvalidDataException with closecode PROTOCOL_ERROR
   *
   * @param s the detail message.
   * @param t the throwable causing this exception.
   */
  public InvalidHandshakeException(String s, Throwable t) {
    super(CloseFrame.PROTOCOL_ERROR, s, t);
  }

  /**
   * constructor for a InvalidHandshakeException
   * <p>
   * calling InvalidDataException with closecode PROTOCOL_ERROR
   *
   * @param s the detail message.
   */
  public InvalidHandshakeException(String s) {
    super(CloseFrame.PROTOCOL_ERROR, s);
  }

  /**
   * constructor for a InvalidHandshakeException
   * <p>
   * calling InvalidDataException with closecode PROTOCOL_ERROR
   *
   * @param t the throwable causing this exception.
   */
  public InvalidHandshakeException(Throwable t) {
    super(CloseFrame.PROTOCOL_ERROR, t);
  }

}
