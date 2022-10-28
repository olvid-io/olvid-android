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

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import org.java_websocket_olvid.drafts.Draft;

/**
 * Interface to encapsulate the required methods for a websocket factory
 */
public interface WebSocketServerFactory extends WebSocketFactory {

  @Override
  WebSocketImpl createWebSocket(WebSocketAdapter a, Draft d);

  @Override
  WebSocketImpl createWebSocket(WebSocketAdapter a, List<Draft> drafts);

  /**
   * Allows to wrap the SocketChannel( key.channel() ) to insert a protocol layer( like ssl or proxy
   * authentication) beyond the ws layer.
   *
   * @param channel The SocketChannel to wrap
   * @param key     a SelectionKey of an open SocketChannel.
   * @return The channel on which the read and write operations will be performed.<br>
   * @throws IOException may be thrown while writing on the channel
   */
  ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException;

  /**
   * Allows to shutdown the websocket factory for a clean shutdown
   */
  void close();
}
