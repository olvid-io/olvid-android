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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.SSLContext;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SSLContextUtil;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Test;

public class Issue1142Test {



  @Test(timeout = 4000)
  public void testWithoutSSLSession()
      throws IOException, URISyntaxException, InterruptedException {
    int port = SocketUtil.getAvailablePort();
    final CountDownLatch countServerDownLatch = new CountDownLatch(1);
    final WebSocketClient webSocket = new WebSocketClient(new URI("ws://localhost:" + port)) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {
        countServerDownLatch.countDown();
      }

      @Override
      public void onMessage(String message) {
      }

      @Override
      public void onClose(int code, String reason, boolean remote) {
      }

      @Override
      public void onError(Exception ex) {
      }
    };
    WebSocketServer server = new MyWebSocketServer(port, countServerDownLatch);
    server.start();
    countServerDownLatch.await();
    webSocket.connectBlocking();
    assertFalse(webSocket.hasSSLSupport());
    try {
      webSocket.getSSLSession();
      assertFalse(false);
    } catch (IllegalArgumentException e) {
      // Fine
    }
    server.stop();
  }

  @Test(timeout = 4000)
  public void testWithSSLSession()
      throws IOException, URISyntaxException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, CertificateException, InterruptedException {
    int port = SocketUtil.getAvailablePort();
    final CountDownLatch countServerDownLatch = new CountDownLatch(1);
    final WebSocketClient webSocket = new WebSocketClient(new URI("wss://localhost:" + port)) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {
        countServerDownLatch.countDown();
      }

      @Override
      public void onMessage(String message) {
      }

      @Override
      public void onClose(int code, String reason, boolean remote) {
      }

      @Override
      public void onError(Exception ex) {
      }
    };
    WebSocketServer server = new MyWebSocketServer(port, countServerDownLatch);
    SSLContext sslContext = SSLContextUtil.getContext();

    server.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
    webSocket.setSocketFactory(sslContext.getSocketFactory());
    server.start();
    countServerDownLatch.await();
    webSocket.connectBlocking();
    assertTrue(webSocket.hasSSLSupport());
    assertNotNull(webSocket.getSSLSession());
    server.stop();
  }

  private static class MyWebSocketServer extends WebSocketServer {

    private final CountDownLatch countServerLatch;


    public MyWebSocketServer(int port, CountDownLatch serverDownLatch) {
      super(new InetSocketAddress(port));
      this.countServerLatch = serverDownLatch;
    }

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
      ex.printStackTrace();
    }

    @Override
    public void onStart() {
      countServerLatch.countDown();
    }
  }
}
