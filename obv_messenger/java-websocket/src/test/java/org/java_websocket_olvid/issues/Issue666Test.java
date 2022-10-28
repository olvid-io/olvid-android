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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.java_websocket_olvid.util.ThreadCheck;
import org.junit.Assert;
import org.junit.Test;

public class Issue666Test {

  private CountDownLatch countServerDownLatch = new CountDownLatch(1);

  @Test
  public void testServer() throws Exception {
    Map<Long, Thread> mapBefore = ThreadCheck.getThreadMap();
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
    server.start();
    countServerDownLatch.await();
    Map<Long, Thread> mapAfter = ThreadCheck.getThreadMap();
    for (long key : mapBefore.keySet()) {
      mapAfter.remove(key);
    }
    for (Thread thread : mapAfter.values()) {
      String name = thread.getName();
      if (!name.startsWith("WebSocketSelector-") && !name.startsWith("WebSocketWorker-") && !name
          .startsWith("connectionLostChecker-")) {
        Assert.fail("Thread not correctly named! Is: " + name);
      }
    }
    server.stop();
  }

  @Test
  public void testClient() throws Exception {
    int port = SocketUtil.getAvailablePort();
    WebSocketClient client = new WebSocketClient(new URI("ws://localhost:" + port)) {
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

      }
    };
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
    server.start();
    countServerDownLatch.await();
    Map<Long, Thread> mapBefore = ThreadCheck.getThreadMap();
    client.connectBlocking();
    Map<Long, Thread> mapAfter = ThreadCheck.getThreadMap();
    for (long key : mapBefore.keySet()) {
      mapAfter.remove(key);
    }
    for (Thread thread : mapAfter.values()) {
      String name = thread.getName();
      if (!name.startsWith("connectionLostChecker-") && !name.startsWith("WebSocketWriteThread-")
          && !name.startsWith("WebSocketConnectReadThread-")) {
        Assert.fail("Thread not correctly named! Is: " + name);
      }
    }
    client.close();
    server.stop();
  }
}
