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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.WebSocketAdapter;
import org.java_websocket_olvid.WebSocketImpl;
import org.java_websocket_olvid.drafts.Draft;
import org.java_websocket_olvid.drafts.Draft_6455;
import org.java_websocket_olvid.handshake.Handshakedata;
import org.junit.Test;

public class DefaultWebSocketServerFactoryTest {

  @Test
  public void testCreateWebSocket() {
    DefaultWebSocketServerFactory webSocketServerFactory = new DefaultWebSocketServerFactory();
    CustomWebSocketAdapter webSocketAdapter = new CustomWebSocketAdapter();
    WebSocketImpl webSocketImpl = webSocketServerFactory
        .createWebSocket(webSocketAdapter, new Draft_6455());
    assertNotNull("webSocketImpl != null", webSocketImpl);
    webSocketImpl = webSocketServerFactory
        .createWebSocket(webSocketAdapter, Collections.<Draft>singletonList(new Draft_6455()));
    assertNotNull("webSocketImpl != null", webSocketImpl);
  }

  @Test
  public void testWrapChannel() {
    DefaultWebSocketServerFactory webSocketServerFactory = new DefaultWebSocketServerFactory();
    SocketChannel channel = (new Socket()).getChannel();
    SocketChannel result = webSocketServerFactory.wrapChannel(channel, null);
    assertSame("channel == result", channel, result);
  }

  @Test
  public void testClose() {
    DefaultWebSocketServerFactory webSocketServerFactory = new DefaultWebSocketServerFactory();
    webSocketServerFactory.close();
  }

  private static class CustomWebSocketAdapter extends WebSocketAdapter {

    @Override
    public void onWebsocketMessage(WebSocket conn, String message) {

    }

    @Override
    public void onWebsocketMessage(WebSocket conn, ByteBuffer blob) {

    }

    @Override
    public void onWebsocketOpen(WebSocket conn, Handshakedata d) {

    }

    @Override
    public void onWebsocketClose(WebSocket ws, int code, String reason, boolean remote) {

    }

    @Override
    public void onWebsocketClosing(WebSocket ws, int code, String reason, boolean remote) {

    }

    @Override
    public void onWebsocketCloseInitiated(WebSocket ws, int code, String reason) {

    }

    @Override
    public void onWebsocketError(WebSocket conn, Exception ex) {

    }

    @Override
    public void onWriteDemand(WebSocket conn) {

    }

    @Override
    public InetSocketAddress getLocalSocketAddress(WebSocket conn) {
      return null;
    }

    @Override
    public InetSocketAddress getRemoteSocketAddress(WebSocket conn) {
      return null;
    }
  }
}
