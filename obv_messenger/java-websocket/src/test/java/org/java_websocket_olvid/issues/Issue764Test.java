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

public class Issue764Test {

  private CountDownLatch countClientDownLatch = new CountDownLatch(2);
  private CountDownLatch countServerDownLatch = new CountDownLatch(1);

  @Test(timeout = 2000)
  public void testIssue()
      throws IOException, URISyntaxException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, CertificateException, InterruptedException {
    int port = SocketUtil.getAvailablePort();
    final WebSocketClient webSocket = new WebSocketClient(new URI("wss://localhost:" + port)) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {
        countClientDownLatch.countDown();
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
    WebSocketServer server = new MyWebSocketServer(port, webSocket, countServerDownLatch);

    SSLContext sslContext = SSLContextUtil.getContext();

    server.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
    webSocket.setSocketFactory(sslContext.getSocketFactory());
    server.start();
    countServerDownLatch.await();
    webSocket.connectBlocking();
    webSocket.reconnectBlocking();
    countClientDownLatch.await();
  }


  private static class MyWebSocketServer extends WebSocketServer {

    private final WebSocketClient webSocket;
    private final CountDownLatch countServerDownLatch;


    public MyWebSocketServer(int port, WebSocketClient webSocket,
        CountDownLatch countServerDownLatch) {
      super(new InetSocketAddress(port));
      this.webSocket = webSocket;
      this.countServerDownLatch = countServerDownLatch;
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
      countServerDownLatch.countDown();
    }
  }
}
