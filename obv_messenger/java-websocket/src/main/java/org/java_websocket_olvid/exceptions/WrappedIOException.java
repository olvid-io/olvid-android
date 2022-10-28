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

import java.io.IOException;
import org.java_websocket_olvid.WebSocket;

/**
 * Exception to wrap an IOException and include information about the websocket which had the
 * exception
 *
 * @since 1.4.1
 */
public class WrappedIOException extends Exception {

  /**
   * The websocket where the IOException happened
   */
  private final transient WebSocket connection;

  /**
   * The IOException
   */
  private final IOException ioException;

  /**
   * Wrapp an IOException and include the websocket
   *
   * @param connection  the websocket where the IOException happened
   * @param ioException the IOException
   */
  public WrappedIOException(WebSocket connection, IOException ioException) {
    this.connection = connection;
    this.ioException = ioException;
  }

  /**
   * The websocket where the IOException happened
   *
   * @return the websocket for the wrapped IOException
   */
  public WebSocket getConnection() {
    return connection;
  }

  /**
   * The wrapped IOException
   *
   * @return IOException which is wrapped
   */
  public IOException getIOException() {
    return ioException;
  }
}
