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

package org.java_websocket_olvid.issues;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.WebSocketAdapter;
import org.java_websocket_olvid.WebSocketImpl;
import org.java_websocket_olvid.WebSocketServerFactory;
import org.java_websocket_olvid.drafts.Draft;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.junit.Assert;
import org.junit.Test;

public class Issue765Test {

  boolean isClosedCalled = false;

  @Test
  public void testIssue() {
    WebSocketServer webSocketServer = new MyWebSocketServer();
    webSocketServer.setWebSocketFactory(new LocalWebSocketFactory());
    Assert.assertFalse("Close should not have been called yet", isClosedCalled);
    webSocketServer.setWebSocketFactory(new LocalWebSocketFactory());
    Assert.assertTrue("Close has been called", isClosedCalled);
  }

  private static class MyWebSocketServer extends WebSocketServer {

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {

    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {

    }

    @Override
    public void onMessage(WebSocket conn, String message) {

    }

    @Override
    public void onError(WebSocket conn, Exception ex) {

    }

    @Override
    public void onStart() {

    }
  }

  private class LocalWebSocketFactory implements WebSocketServerFactory {

    @Override
    public WebSocketImpl createWebSocket(WebSocketAdapter a, Draft d) {
      return null;
    }

    @Override
    public WebSocketImpl createWebSocket(WebSocketAdapter a, List<Draft> drafts) {
      return null;
    }

    @Override
    public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
      return null;
    }

    @Override
    public void close() {
      isClosedCalled = true;
    }
  }
}
