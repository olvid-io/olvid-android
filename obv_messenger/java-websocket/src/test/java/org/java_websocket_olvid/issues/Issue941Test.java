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

import static org.junit.Assert.assertArrayEquals;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.framing.Framedata;
import org.java_websocket_olvid.framing.PingFrame;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Test;

public class Issue941Test {

  private CountDownLatch pingLatch = new CountDownLatch(1);
  private CountDownLatch pongLatch = new CountDownLatch(1);
  private byte[] pingBuffer, receivedPingBuffer, pongBuffer;

  @Test
  public void testIssue() throws Exception {
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

      @Override
      public PingFrame onPreparePing(WebSocket conn) {
        PingFrame frame = new PingFrame();
        pingBuffer = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        frame.setPayload(ByteBuffer.wrap(pingBuffer));
        return frame;
      }

      @Override
      public void onWebsocketPong(WebSocket conn, Framedata f) {
        pongBuffer = f.getPayloadData().array();
        pongLatch.countDown();
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
      }

      @Override
      public void onWebsocketPing(WebSocket conn, Framedata f) {
        receivedPingBuffer = f.getPayloadData().array();
        super.onWebsocketPing(conn, f);
        pingLatch.countDown();
      }
    };

    server.start();
    client.connectBlocking();
    client.setConnectionLostTimeout(1);
    pingLatch.await();
    assertArrayEquals(pingBuffer, receivedPingBuffer);
    pongLatch.await();
    assertArrayEquals(pingBuffer, pongBuffer);
  }
}
