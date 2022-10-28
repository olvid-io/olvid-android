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

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Issue879Test {

  private static final int NUMBER_OF_TESTS = 20;

  @Parameterized.Parameter
  public int numberOfConnections;


  @Test(timeout = 10000)
  public void QuickStopTest() throws IOException, InterruptedException, URISyntaxException {
    final boolean[] wasBindException = {false};
    final boolean[] wasConcurrentException = new boolean[1];
    final CountDownLatch countDownLatch = new CountDownLatch(1);

    class SimpleServer extends WebSocketServer {

      public SimpleServer(InetSocketAddress address) {
        super(address);
      }

      @Override
      public void onOpen(WebSocket conn, ClientHandshake handshake) {
        broadcast("new connection: " + handshake
            .getResourceDescriptor()); //This method sends a message to all clients connected
      }

      @Override
      public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      }

      @Override
      public void onMessage(WebSocket conn, String message) {
      }

      @Override
      public void onMessage(WebSocket conn, ByteBuffer message) {
      }

      @Override
      public void onError(WebSocket conn, Exception ex) {
        if (ex instanceof BindException) {
          wasBindException[0] = true;
        }
        if (ex instanceof ConcurrentModificationException) {
          wasConcurrentException[0] = true;
        }
      }

      @Override
      public void onStart() {
        countDownLatch.countDown();
      }
    }
    int port = SocketUtil.getAvailablePort();
    SimpleServer serverA = new SimpleServer(new InetSocketAddress(port));
    SimpleServer serverB = new SimpleServer(new InetSocketAddress(port));
    serverA.start();
    countDownLatch.await();
    List<WebSocketClient> clients = startNewConnections(numberOfConnections, port);
    Thread.sleep(100);
    int numberOfConnected = 0;
    for (WebSocketClient client : clients) {
      if (client.isOpen()) {
        numberOfConnected++;
      }
    }
    // Number will differ since we use connect instead of connectBlocking
    // System.out.println(numberOfConnected + " " + numberOfConnections);

    serverA.stop();
    serverB.start();
    clients.clear();
    assertFalse("There was a BindException", wasBindException[0]);
    assertFalse("There was a ConcurrentModificationException", wasConcurrentException[0]);
  }

  @Parameterized.Parameters
  public static Collection<Integer[]> data() {
    List<Integer[]> ret = new ArrayList<Integer[]>(NUMBER_OF_TESTS);
    for (int i = 0; i < NUMBER_OF_TESTS; i++) {
      ret.add(new Integer[]{25 + i * 25});
    }
    return ret;
  }

  private List<WebSocketClient> startNewConnections(int numberOfConnections, int port)
      throws URISyntaxException, InterruptedException {
    List<WebSocketClient> clients = new ArrayList<WebSocketClient>(numberOfConnections);
    for (int i = 0; i < numberOfConnections; i++) {
      WebSocketClient client = new SimpleClient(new URI("ws://localhost:" + port));
      client.connect();
      Thread.sleep(1);
      clients.add(client);
    }
    return clients;
  }

  class SimpleClient extends WebSocketClient {

    public SimpleClient(URI serverUri) {
      super(serverUri);
    }

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
  }
}
