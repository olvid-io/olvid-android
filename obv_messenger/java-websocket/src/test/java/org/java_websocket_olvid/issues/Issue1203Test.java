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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Assert;
import org.junit.Test;

public class Issue1203Test {
  private final CountDownLatch countServerDownLatch = new CountDownLatch(1);
  private final CountDownLatch countClientDownLatch = new CountDownLatch(1);
  boolean isClosedCalled = false;
  @Test(timeout = 50000)
  public void testIssue() throws Exception {
    int port = SocketUtil.getAvailablePort();
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
        countServerDownLatch.countDown();
      }
    };
    final WebSocketClient client = new WebSocketClient(new URI("ws://localhost:" + port)) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {
        countClientDownLatch.countDown();
      }

      @Override
      public void onMessage(String message) {

      }

      @Override
      public void onClose(int code, String reason, boolean remote) {
        isClosedCalled = true;
      }

      @Override
      public void onError(Exception ex) {

      }
    };

    server.setConnectionLostTimeout(10);
    server.start();
    countServerDownLatch.await();

    client.setConnectionLostTimeout(10);
    Timer timer = new Timer();
    TimerTask task = new TimerTask() {
      @Override
      public void run() {
        try {
          client.connectBlocking();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    };
    timer.schedule(task, 15000);
    countClientDownLatch.await();
    Thread.sleep(30000);
    Assert.assertFalse(isClosedCalled);
    client.closeBlocking();
    server.stop();
  }
}
