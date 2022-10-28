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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.java_websocket_olvid.util.ThreadCheck;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class Issue732Test {

  @Rule
  public ThreadCheck zombies = new ThreadCheck();

  private CountDownLatch countServerDownLatch = new CountDownLatch(1);

  @Test(timeout = 2000)
  public void testIssue() throws Exception {
    int port = SocketUtil.getAvailablePort();
    final WebSocketClient webSocket = new WebSocketClient(new URI("ws://localhost:" + port)) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {
        try {
          this.reconnect();
          Assert.fail("Exception should be thrown");
        } catch (IllegalStateException e) {
          //
        }
      }

      @Override
      public void onMessage(String message) {
        try {
          this.reconnect();
          Assert.fail("Exception should be thrown");
        } catch (IllegalStateException e) {
          send("hi");
        }
      }

      @Override
      public void onClose(int code, String reason, boolean remote) {
        try {
          this.reconnect();
          Assert.fail("Exception should be thrown");
        } catch (IllegalStateException e) {
          //
        }
      }

      @Override
      public void onError(Exception ex) {
        try {
          this.reconnect();
          Assert.fail("Exception should be thrown");
        } catch (IllegalStateException e) {
          //
        }
      }
    };
    WebSocketServer server = new WebSocketServer(new InetSocketAddress(port)) {
      @Override
      public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conn.send("hi");
      }

      @Override
      public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        countServerDownLatch.countDown();
      }

      @Override
      public void onMessage(WebSocket conn, String message) {
        conn.close();
      }

      @Override
      public void onError(WebSocket conn, Exception ex) {
        fail("There should be no onError!");
      }

      @Override
      public void onStart() {
        webSocket.connect();
      }
    };
    server.start();
    countServerDownLatch.await();
    server.stop();
  }
}
