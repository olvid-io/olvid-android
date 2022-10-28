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
 * exception which indicates that the message limited was exceeded (CloseFrame.TOOBIG)
 */
public class LimitExceededException extends InvalidDataException {

  /**
   * Serializable
   */
  private static final long serialVersionUID = 6908339749836826785L;

  /**
   * A closer indication about the limit
   */
  private final int limit;

  /**
   * constructor for a LimitExceededException
   * <p>
   * calling LimitExceededException with closecode TOOBIG
   */
  public LimitExceededException() {
    this(Integer.MAX_VALUE);
  }

  /**
   * constructor for a LimitExceededException
   * <p>
   * calling InvalidDataException with closecode TOOBIG
   * @param limit the allowed size which was not enough
   */
  public LimitExceededException(int limit) {
    super(CloseFrame.TOOBIG);
    this.limit = limit;
  }

  /**
   * constructor for a LimitExceededException
   * <p>
   * calling InvalidDataException with closecode TOOBIG
   * @param s the detail message.
   * @param limit the allowed size which was not enough
   */
  public LimitExceededException(String s, int limit) {
    super(CloseFrame.TOOBIG, s);
    this.limit = limit;
  }

  /**
   * constructor for a LimitExceededException
   * <p>
   * calling InvalidDataException with closecode TOOBIG
   *
   * @param s the detail message.
   */
  public LimitExceededException(String s) {
    this(s, Integer.MAX_VALUE);
  }

  /**
   * Get the limit which was hit so this exception was caused
   *
   * @return the limit as int
   */
  public int getLimit() {
    return limit;
  }
}
