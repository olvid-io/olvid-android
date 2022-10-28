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

import static org.junit.Assert.assertTrue;

import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Test;

public class Issue621Test {

  CountDownLatch countDownLatch = new CountDownLatch(1);
  CountDownLatch countServerDownLatch = new CountDownLatch(1);

  boolean wasError = false;

  class TestPrintStream extends PrintStream {

    public TestPrintStream(OutputStream out) {
      super(out);
    }

    @Override
    public void println(Object o) {
      wasError = true;
      super.println(o);
    }
  }

  @Test
  public void testIssue() throws Exception {
    System.setErr(new TestPrintStream(System.err));
    int port = SocketUtil.getAvailablePort();
    WebSocketClient webSocket = new WebSocketClient(new URI("ws://localhost:" + port)) {
      @Override
      public void onOpen(ServerHandshake handshakedata) {

      }

      @Override
      public void onMessage(String message) {

      }

      @Override
      public void onClose(int code, String reason, boolean remote) {
        countDownLatch.countDown();
      }

      @Override
      public void onError(Exception ex) {

      }
    };
    WebSocketServer server = new WebSocketServer(new InetSocketAddress(port)) {
      @Override
      public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conn.close();
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
    server.start();
    countServerDownLatch.await();
    webSocket.connectBlocking();
    countDownLatch.await();
    assertTrue("There was an error using System.err", !wasError);
    server.stop();
  }
}
