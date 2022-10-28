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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Test;

public class Issue713Test {

  CountDownLatch countDownLatchString = new CountDownLatch(10);
  CountDownLatch countDownLatchConnect = new CountDownLatch(10);
  CountDownLatch countDownLatchBytebuffer = new CountDownLatch(10);

  @Test
  public void testIllegalArgument() throws IOException {
    WebSocketServer server = new WebSocketServer(
        new InetSocketAddress(SocketUtil.getAvailablePort())) {
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
    try {
      server.broadcast((byte[]) null, null);
      fail("IllegalArgumentException should be thrown");
    } catch (Exception e) {
      // OK
    }
    try {
      server.broadcast((String) null, null);
      fail("IllegalArgumentException should be thrown");
    } catch (Exception e) {
      // OK
    }
  }

  @Test(timeout = 2000)
  public void testIssue() throws Exception {
    final int port = SocketUtil.getAvailablePort();
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
        try {
          for (int i = 0; i < 10; i++) {
            TestWebSocket tw = new TestWebSocket(port);
            tw.connect();
          }
        } catch (Exception e) {
          fail("Exception during connect!");
        }
      }
    };
    server.start();
    countDownLatchConnect.await();
    server.broadcast("Hello world!");
    countDownLatchString.await();
    server.broadcast("Hello world".getBytes());
    countDownLatchBytebuffer.await();
    countDownLatchBytebuffer = new CountDownLatch(10);
    server.broadcast(ByteBuffer.wrap("Hello world".getBytes()));
    countDownLatchBytebuffer.await();

    countDownLatchString = new CountDownLatch(5);
    ArrayList<WebSocket> specialList = new ArrayList<WebSocket>(server.getConnections());
    specialList.remove(8);
    specialList.remove(6);
    specialList.remove(4);
    specialList.remove(2);
    specialList.remove(0);
    server.broadcast("Hello world", specialList);
    countDownLatchString.await();

    countDownLatchBytebuffer = new CountDownLatch(5);
    server.broadcast("Hello world".getBytes());
    countDownLatchBytebuffer.await();
  }


  class TestWebSocket extends WebSocketClient {

    TestWebSocket(int port) throws URISyntaxException {
      super(new URI("ws://localhost:" + port));
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
      countDownLatchConnect.countDown();
    }

    @Override
    public void onMessage(String message) {
      countDownLatchString.countDown();
    }

    @Override
    public void onMessage(ByteBuffer message) {
      countDownLatchBytebuffer.countDown();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

    }

    @Override
    public void onError(Exception ex) {

    }
  }
}
