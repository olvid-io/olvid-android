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

/**
 * The interface for building a handshake for the server
 */
public interface ServerHandshakeBuilder extends HandshakeBuilder, ServerHandshake {

  /**
   * Setter for the http status code
   *
   * @param status the http status code
   */
  void setHttpStatus(short status);

  /**
   * Setter for the http status message
   *
   * @param message the http status message
   */
  void setHttpStatusMessage(String message);
}
