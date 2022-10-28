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
 * Exception which indicates that the frame is not yet complete
 */
public class IncompleteException extends Exception {

  /**
   * It's Serializable.
   */
  private static final long serialVersionUID = 7330519489840500997L;

  /**
   * The preferred size
   */
  private final int preferredSize;

  /**
   * Constructor for the preferred size of a frame
   *
   * @param preferredSize the preferred size of a frame
   */
  public IncompleteException(int preferredSize) {
    this.preferredSize = preferredSize;
  }

  /**
   * Getter for the preferredSize
   *
   * @return the value of the preferred size
   */
  public int getPreferredSize() {
    return preferredSize;
  }
}
