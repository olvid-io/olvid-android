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

public class Issue825Test {


  @Test(timeout = 15000)
  public void testIssue()
      throws IOException, URISyntaxException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, CertificateException, InterruptedException {
    final CountDownLatch countClientOpenLatch = new CountDownLatch(3);
    final CountDownLatch countClientMessageLatch = new CountDownLatch(3);
    final CountDownLatch countServerDownLatch = new CountDownLatch(1);
    int port = SocketUtil.getAvailablePort();
    final WebSocketClient webSocket = new WebSocketClient(new URI("wss://localhost:" + port)) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {
        countClientOpenLatch.countDown();
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
    WebSocketServer server = new MyWebSocketServer(port, countServerDownLatch,
        countClientMessageLatch);

    // load up the key store
    SSLContext sslContext = SSLContextUtil.getContext();

    server.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
    webSocket.setSocketFactory(sslContext.getSocketFactory());
    server.start();
    countServerDownLatch.await();
    webSocket.connectBlocking();
    webSocket.send("hi");
    Thread.sleep(10);
    webSocket.closeBlocking();

    //Disconnect manually and reconnect blocking
    webSocket.reconnectBlocking();
    webSocket.send("it's");
    Thread.sleep(10000);
    webSocket.closeBlocking();
    //Disconnect manually and reconnect
    webSocket.reconnect();
    countClientOpenLatch.await();
    webSocket.send("me");
    Thread.sleep(100);
    webSocket.closeBlocking();
    countClientMessageLatch.await();
  }


  private static class MyWebSocketServer extends WebSocketServer {

    private final CountDownLatch countServerLatch;
    private final CountDownLatch countClientMessageLatch;


    public MyWebSocketServer(int port, CountDownLatch serverDownLatch,
        CountDownLatch countClientMessageLatch) {
      super(new InetSocketAddress(port));
      this.countServerLatch = serverDownLatch;
      this.countClientMessageLatch = countClientMessageLatch;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
      countClientMessageLatch.countDown();
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
