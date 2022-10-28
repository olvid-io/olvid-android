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

package org.java_websocket_olvid.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.drafts.Draft;
import org.java_websocket_olvid.drafts.Draft_6455;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Test;

public class WebSocketServerTest {

  @Test
  public void testConstructor() {
    List<Draft> draftCollection = Collections.<Draft>singletonList(new Draft_6455());
    Collection<WebSocket> webSocketCollection = new HashSet<WebSocket>();
    InetSocketAddress inetAddress = new InetSocketAddress(1337);

    try {
      WebSocketServer server = new MyWebSocketServer(null, 1, draftCollection, webSocketCollection);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      //OK
    }
    try {
      WebSocketServer server = new MyWebSocketServer(inetAddress, 0, draftCollection,
          webSocketCollection);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      //OK
    }
    try {
      WebSocketServer server = new MyWebSocketServer(inetAddress, -1, draftCollection,
          webSocketCollection);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      //OK
    }
    try {
      WebSocketServer server = new MyWebSocketServer(inetAddress, Integer.MIN_VALUE,
          draftCollection, webSocketCollection);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      //OK
    }
    try {
      WebSocketServer server = new MyWebSocketServer(inetAddress, Integer.MIN_VALUE,
          draftCollection, webSocketCollection);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      //OK
    }
    try {
      WebSocketServer server = new MyWebSocketServer(inetAddress, 1, draftCollection, null);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      WebSocketServer server = new MyWebSocketServer(inetAddress, 1, draftCollection,
          webSocketCollection);
      // OK
    } catch (IllegalArgumentException e) {
      fail("Should not fail");
    }
    try {
      WebSocketServer server = new MyWebSocketServer(inetAddress, 1, null, webSocketCollection);
      // OK
    } catch (IllegalArgumentException e) {
      fail("Should not fail");
    }
  }


  @Test
  public void testGetAddress() throws IOException {
    int port = SocketUtil.getAvailablePort();
    InetSocketAddress inetSocketAddress = new InetSocketAddress(port);
    MyWebSocketServer server = new MyWebSocketServer(port);
    assertEquals(inetSocketAddress, server.getAddress());
  }

  @Test
  public void testGetDrafts() {
    List<Draft> draftCollection = Collections.<Draft>singletonList(new Draft_6455());
    Collection<WebSocket> webSocketCollection = new HashSet<WebSocket>();
    InetSocketAddress inetAddress = new InetSocketAddress(1337);
    MyWebSocketServer server = new MyWebSocketServer(inetAddress, 1, draftCollection,
        webSocketCollection);
    assertEquals(1, server.getDraft().size());
    assertEquals(draftCollection.get(0), server.getDraft().get(0));
  }

  @Test
  public void testGetPort() throws IOException, InterruptedException {
    int port = SocketUtil.getAvailablePort();
    CountDownLatch countServerDownLatch = new CountDownLatch(1);
    MyWebSocketServer server = new MyWebSocketServer(port);
    assertEquals(port, server.getPort());
    server = new MyWebSocketServer(0, countServerDownLatch);
    assertEquals(0, server.getPort());
    server.start();
    countServerDownLatch.await();
    assertNotEquals(0, server.getPort());
  }

  @Test
  public void testMaxPendingConnections() {
    MyWebSocketServer server = new MyWebSocketServer(1337);
    assertEquals(server.getMaxPendingConnections(), -1);
    server.setMaxPendingConnections(10);
    assertEquals(server.getMaxPendingConnections(), 10);
  }

  @Test
  public void testBroadcast() {
    MyWebSocketServer server = new MyWebSocketServer(1337);
    try {
      server.broadcast((byte[]) null, Collections.<WebSocket>emptyList());
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      server.broadcast((ByteBuffer) null, Collections.<WebSocket>emptyList());
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      server.broadcast((String) null, Collections.<WebSocket>emptyList());
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      server.broadcast(new byte[]{(byte) 0xD0}, null);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      server.broadcast(ByteBuffer.wrap(new byte[]{(byte) 0xD0}), null);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      server.broadcast("", null);
      fail("Should fail");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      server.broadcast("", Collections.<WebSocket>emptyList());
      // OK
    } catch (IllegalArgumentException e) {
      fail("Should not fail");
    }
  }

  private static class MyWebSocketServer extends WebSocketServer {

    private CountDownLatch serverLatch = null;

    public MyWebSocketServer(InetSocketAddress address, int decodercount, List<Draft> drafts,
        Collection<WebSocket> connectionscontainer) {
      super(address, decodercount, drafts, connectionscontainer);
    }

    public MyWebSocketServer(int port, CountDownLatch serverLatch) {
      super(new InetSocketAddress(port));
      this.serverLatch = serverLatch;
    }

    public MyWebSocketServer(int port) {
      this(port, null);
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
      if (serverLatch != null) {
        serverLatch.countDown();
      }
    }
  }
}

