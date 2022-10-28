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

package org.java_websocket_olvid.server;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import org.java_websocket_olvid.WebSocketAdapter;
import org.java_websocket_olvid.WebSocketImpl;
import org.java_websocket_olvid.WebSocketServerFactory;
import org.java_websocket_olvid.drafts.Draft;

public class DefaultWebSocketServerFactory implements WebSocketServerFactory {

  @Override
  public WebSocketImpl createWebSocket(WebSocketAdapter a, Draft d) {
    return new WebSocketImpl(a, d);
  }

  @Override
  public WebSocketImpl createWebSocket(WebSocketAdapter a, List<Draft> d) {
    return new WebSocketImpl(a, d);
  }

  @Override
  public SocketChannel wrapChannel(SocketChannel channel, SelectionKey key) {
    return channel;
  }

  @Override
  public void close() {
    //Nothing to do for a normal ws factory
  }
}