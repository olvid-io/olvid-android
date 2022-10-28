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
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Test;

public class Issue811Test {

  private CountDownLatch countServerDownLatch = new CountDownLatch(1);

  @Test(timeout = 2000)
  public void testSetConnectionLostTimeout() throws IOException, InterruptedException {
    final MyWebSocketServer server = new MyWebSocketServer(
        new InetSocketAddress(SocketUtil.getAvailablePort()));
    server.start();
    new Thread(new Runnable() {
      @Override
      public void run() {
        while (server.getConnectionLostTimeout() == 60) {
          // do nothing
        }
        // will never reach this statement
        countServerDownLatch.countDown();
      }
    }).start();
    Thread.sleep(1000);
    server.setConnectionLostTimeout(20);
    countServerDownLatch.await();
  }

  private static class MyWebSocketServer extends WebSocketServer {

    public MyWebSocketServer(InetSocketAddress inetSocketAddress) {
      super(inetSocketAddress);
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

    }

    @Override
    public void onStart() {

    }
  }
}
