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
import java.util.Set;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Assert;
import org.junit.Test;

public class Issue834Test {

  @Test(timeout = 1000)
  public void testNoNewThreads() throws IOException {

    Set<Thread> threadSet1 = Thread.getAllStackTraces().keySet();

    new WebSocketServer(new InetSocketAddress(SocketUtil.getAvailablePort())) {
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
    };

    Set<Thread> threadSet2 = Thread.getAllStackTraces().keySet();

    //checks that no threads are started in the constructor
    Assert.assertEquals(threadSet1, threadSet2);

  }

}
