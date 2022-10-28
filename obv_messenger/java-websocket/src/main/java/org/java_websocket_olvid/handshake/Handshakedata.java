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

package org.java_websocket_olvid.handshake;

import java.util.Iterator;

/**
 * The interface for the data of a handshake
 */
public interface Handshakedata {

  /**
   * Iterator for the http fields
   *
   * @return the http fields
   */
  Iterator<String> iterateHttpFields();

  /**
   * Gets the value of the field
   *
   * @param name The name of the field
   * @return the value of the field or an empty String if not in the handshake
   */
  String getFieldValue(String name);

  /**
   * Checks if this handshake contains a specific field
   *
   * @param name The name of the field
   * @return true, if it contains the field
   */
  boolean hasFieldValue(String name);

  /**
   * Get the content of the handshake
   *
   * @return the content as byte-array
   */
  byte[] getContent();
}
