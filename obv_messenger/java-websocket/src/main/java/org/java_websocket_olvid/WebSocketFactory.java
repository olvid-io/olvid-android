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

package org.java_websocket_olvid;

import java.util.List;
import org.java_websocket_olvid.drafts.Draft;

public interface WebSocketFactory {

  /**
   * Create a new Websocket with the provided listener, drafts and socket
   *
   * @param a The Listener for the WebsocketImpl
   * @param d The draft which should be used
   * @return A WebsocketImpl
   */
  WebSocket createWebSocket(WebSocketAdapter a, Draft d);

  /**
   * Create a new Websocket with the provided listener, drafts and socket
   *
   * @param a      The Listener for the WebsocketImpl
   * @param drafts The drafts which should be used
   * @return A WebsocketImpl
   */
  WebSocket createWebSocket(WebSocketAdapter a, List<Draft> drafts);

}
