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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import javax.net.SocketFactory;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Assert;
import org.junit.Test;

public class Issue962Test {

  private static class TestSocketFactory extends SocketFactory {

    private final SocketFactory socketFactory = SocketFactory.getDefault();
    private final String bindingAddress;

    public TestSocketFactory(String bindingAddress) {
      this.bindingAddress = bindingAddress;
    }

    @Override
    public Socket createSocket() throws IOException {
      Socket socket = socketFactory.createSocket();
      socket.bind(new InetSocketAddress(bindingAddress, 0));
      return socket;
    }

    @Override
    public Socket createSocket(String string, int i) throws IOException, UnknownHostException {
      Socket socket = socketFactory.createSocket(string, i);
      socket.bind(new InetSocketAddress(bindingAddress, 0));
      return socket;
    }

    @Override
    public Socket createSocket(String string, int i, InetAddress ia, int i1)
        throws IOException, UnknownHostException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(InetAddress ia, int i) throws IOException {
      Socket socket = socketFactory.createSocket(ia, i);
      socket.bind(new InetSocketAddress(bindingAddress, 0));
      return socket;
    }

    @Override
    public Socket createSocket(InetAddress ia, int i, InetAddress ia1, int i1) throws IOException {
      throw new UnsupportedOperationException();
    }

  }

  @Test(timeout = 2000)
  public void testIssue() throws IOException, URISyntaxException, InterruptedException {
    int port = SocketUtil.getAvailablePort();
    WebSocketClient client = new WebSocketClient(new URI("ws://127.0.0.1:" + port)) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {
      }

      @Override
      public void onMessage(String message) {
      }

      @Override
      public void onClose(int code, String reason, boolean remote) {
      }

      @Override
      public void onError(Exception ex) {
        Assert.fail(ex.toString() + " should not occur");
      }
    };

    String bindingAddress = "127.0.0.1";

    client.setSocketFactory(new TestSocketFactory(bindingAddress));

    WebSocketServer server = new WebSocketServer(new InetSocketAddress(port)) {
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
    };

    server.start();
    client.connectBlocking();
    Assert.assertEquals(bindingAddress, client.getSocket().getLocalAddress().getHostAddress());
    Assert.assertNotEquals(0, client.getSocket().getLocalPort());
    Assert.assertTrue(client.getSocket().isConnected());
  }

}
