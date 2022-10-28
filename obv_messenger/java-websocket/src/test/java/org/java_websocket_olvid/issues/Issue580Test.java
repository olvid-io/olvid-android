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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.java_websocket_olvid.util.ThreadCheck;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Issue580Test {

  private static final int NUMBER_OF_TESTS = 10;

  @Parameterized.Parameter
  public int count;


  @Rule
  public ThreadCheck zombies = new ThreadCheck();

  private void runTestScenario(boolean closeBlocking) throws Exception {
    final CountDownLatch countServerDownLatch = new CountDownLatch(1);
    int port = SocketUtil.getAvailablePort();
    WebSocketServer ws = new WebSocketServer(new InetSocketAddress(port)) {
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
    ws.start();
    countServerDownLatch.await();
    WebSocketClient clt = new WebSocketClient(new URI("ws://localhost:" + port)) {
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
    clt.connectBlocking();
    clt.send("test");
    if (closeBlocking) {
      clt.closeBlocking();
    }
    ws.stop();
    Thread.sleep(100);
  }

  @Parameterized.Parameters
  public static Collection<Integer[]> data() {
    List<Integer[]> ret = new ArrayList<Integer[]>(NUMBER_OF_TESTS);
    for (int i = 0; i < NUMBER_OF_TESTS; i++) {
      ret.add(new Integer[]{i});
    }
    return ret;
  }

  @Test
  public void runNoCloseBlockingTestScenario() throws Exception {
    runTestScenario(false);
  }

  @Test
  public void runCloseBlockingTestScenario() throws Exception {
    runTestScenario(true);
  }
}

