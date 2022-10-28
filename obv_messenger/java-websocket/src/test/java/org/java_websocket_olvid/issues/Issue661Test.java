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

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.java_websocket_olvid.util.ThreadCheck;
import org.junit.Rule;
import org.junit.Test;

public class Issue661Test {

  @Rule
  public ThreadCheck zombies = new ThreadCheck();

  private CountDownLatch countServerDownLatch = new CountDownLatch(1);

  private boolean wasError = false;
  private boolean wasBindException = false;

  @Test(timeout = 2000)
  public void testIssue() throws Exception {
    int port = SocketUtil.getAvailablePort();
    WebSocketServer server0 = new WebSocketServer(new InetSocketAddress(port)) {
      @Override
      public void onOpen(WebSocket conn, ClientHandshake handshake) {
        fail("There should be no onOpen");
      }

      @Override
      public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        fail("There should be no onClose");
      }

      @Override
      public void onMessage(WebSocket conn, String message) {
        fail("There should be no onMessage");
      }

      @Override
      public void onError(WebSocket conn, Exception ex) {
        fail("There should be no onError!");
      }

      @Override
      public void onStart() {
        countServerDownLatch.countDown();
      }
    };
    server0.start();
    try {
      countServerDownLatch.await();
    } catch (InterruptedException e) {
      //
    }
    WebSocketServer server1 = new WebSocketServer(new InetSocketAddress(port)) {
      @Override
      public void onOpen(WebSocket conn, ClientHandshake handshake) {
        fail("There should be no onOpen");
      }

      @Override
      public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        fail("There should be no onClose");
      }

      @Override
      public void onMessage(WebSocket conn, String message) {
        fail("There should be no onMessage");
      }

      @Override
      public void onError(WebSocket conn, Exception ex) {
        if (ex instanceof BindException) {
          wasBindException = true;
        } else {
          wasError = true;
        }
      }

      @Override
      public void onStart() {
        fail("There should be no onStart!");
      }
    };
    server1.start();
    Thread.sleep(1000);
    server1.stop();
    server0.stop();
    Thread.sleep(100);
    assertFalse("There was an unexpected exception!", wasError);
    assertTrue("There was no bind exception!", wasBindException);
  }
}
