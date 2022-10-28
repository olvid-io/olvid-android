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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.WebSocketAdapter;
import org.java_websocket_olvid.WebSocketImpl;
import org.java_websocket_olvid.drafts.Draft;
import org.java_websocket_olvid.drafts.Draft_6455;
import org.java_websocket_olvid.handshake.Handshakedata;
import org.junit.Test;

public class CustomSSLWebSocketServerFactoryTest {

  final String[] emptyArray = new String[0];

  @Test
  public void testConstructor() throws NoSuchAlgorithmException {
    try {
      new CustomSSLWebSocketServerFactory(null, null, null);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Good
    }
    try {
      new CustomSSLWebSocketServerFactory(null, null, null, null);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
      // Good
    }
    try {
      new CustomSSLWebSocketServerFactory(SSLContext.getDefault(), null, null, null);
      fail("IllegalArgumentException should be thrown");
    } catch (IllegalArgumentException e) {
    }
    try {
      new CustomSSLWebSocketServerFactory(SSLContext.getDefault(), null, null);
    } catch (IllegalArgumentException e) {
      fail("IllegalArgumentException should not be thrown");
    }
    try {
      new CustomSSLWebSocketServerFactory(SSLContext.getDefault(), Executors.newCachedThreadPool(),
          null, null);
    } catch (IllegalArgumentException e) {
      fail("IllegalArgumentException should not be thrown");
    }
    try {
      new CustomSSLWebSocketServerFactory(SSLContext.getDefault(), Executors.newCachedThreadPool(),
          emptyArray, null);
    } catch (IllegalArgumentException e) {
      fail("IllegalArgumentException should not be thrown");
    }
    try {
      new CustomSSLWebSocketServerFactory(SSLContext.getDefault(), Executors.newCachedThreadPool(),
          null, emptyArray);
    } catch (IllegalArgumentException e) {
      fail("IllegalArgumentException should not be thrown");
    }
    try {
      new CustomSSLWebSocketServerFactory(SSLContext.getDefault(), Executors.newCachedThreadPool(),
          emptyArray, emptyArray);
    } catch (IllegalArgumentException e) {
      fail("IllegalArgumentException should not be thrown");
    }
  }

  @Test
  public void testCreateWebSocket() throws NoSuchAlgorithmException {
    CustomSSLWebSocketServerFactory webSocketServerFactory = new CustomSSLWebSocketServerFactory(
        SSLContext.getDefault(), null, null);
    CustomWebSocketAdapter webSocketAdapter = new CustomWebSocketAdapter();
    WebSocketImpl webSocketImpl = webSocketServerFactory
        .createWebSocket(webSocketAdapter, new Draft_6455());
    assertNotNull("webSocketImpl != null", webSocketImpl);
    webSocketImpl = webSocketServerFactory
        .createWebSocket(webSocketAdapter, Collections.<Draft>singletonList(new Draft_6455()));
    assertNotNull("webSocketImpl != null", webSocketImpl);
  }

  @Test
  public void testWrapChannel() throws IOException, NoSuchAlgorithmException {
    CustomSSLWebSocketServerFactory webSocketServerFactory = new CustomSSLWebSocketServerFactory(
        SSLContext.getDefault(), null, null);
    SocketChannel channel = SocketChannel.open();
    try {
      ByteChannel result = webSocketServerFactory.wrapChannel(channel, null);
    } catch (NotYetConnectedException e) {
      //We do not really connect
    }
    channel.close();
    webSocketServerFactory = new CustomSSLWebSocketServerFactory(SSLContext.getDefault(),
        new String[]{"TLSv1.2"},
        new String[]{"TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA"});
    channel = SocketChannel.open();
    try {
      ByteChannel result = webSocketServerFactory.wrapChannel(channel, null);
    } catch (NotYetConnectedException e) {
      //We do not really connect
    }
    channel.close();
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
