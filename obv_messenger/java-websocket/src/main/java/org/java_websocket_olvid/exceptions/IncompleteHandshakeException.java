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
 * exception which indicates that a incomplete handshake was received
 */
public class IncompleteHandshakeException extends RuntimeException {

  /**
   * Serializable
   */
  private static final long serialVersionUID = 7906596804233893092L;

  /**
   * attribute which size of handshake would have been preferred
   */
  private final int preferredSize;

  /**
   * constructor for a IncompleteHandshakeException
   * <p>
   *
   * @param preferredSize the preferred size
   */
  public IncompleteHandshakeException(int preferredSize) {
    this.preferredSize = preferredSize;
  }

  /**
   * constructor for a IncompleteHandshakeException
   * <p>
   * preferredSize will be 0
   */
  public IncompleteHandshakeException() {
    this.preferredSize = 0;
  }

  /**
   * Getter preferredSize
   *
   * @return the preferredSize
   */
  public int getPreferredSize() {
    return preferredSize;
  }

}
