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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.WebSocketImpl;
import org.java_websocket_olvid.WrappedByteChannel;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Test;

public class Issue900Test {

  CountDownLatch serverStartLatch = new CountDownLatch(1);
  CountDownLatch closeCalledLatch = new CountDownLatch(1);

  @Test(timeout = 2000)
  public void testIssue() throws Exception {
    int port = SocketUtil.getAvailablePort();
    final WebSocketClient client = new WebSocketClient(new URI("ws://localhost:" + port)) {
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
        closeCalledLatch.countDown();
      }

      @Override
      public void onMessage(WebSocket conn, String message) {

      }

      @Override
      public void onError(WebSocket conn, Exception ex) {

      }

      @Override
      public void onStart() {
        serverStartLatch.countDown();
      }
    };
    new Thread(server).start();
    serverStartLatch.await();
    client.connectBlocking();
    WebSocketImpl websocketImpl = (WebSocketImpl) new ArrayList<WebSocket>(server.getConnections())
        .get(0);
    websocketImpl.setChannel(new ExceptionThrowingByteChannel());
    server.broadcast("test");
    closeCalledLatch.await();
  }

  class ExceptionThrowingByteChannel implements WrappedByteChannel {

    @Override
    public boolean isNeedWrite() {
      return true;
    }

    @Override
    public void writeMore() throws IOException {
      throw new IOException();
    }

    @Override
    public boolean isNeedRead() {
      return true;
    }

    @Override
    public int readMore(ByteBuffer dst) throws IOException {
      throw new IOException();
    }

    @Override
    public boolean isBlocking() {
      return false;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      throw new IOException();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
      throw new IOException();
    }

    @Override
    public boolean isOpen() {
      return false;
    }

    @Override
    public void close() throws IOException {
      throw new IOException();
    }
  }
}
