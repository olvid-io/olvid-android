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

package org.java_websocket_olvid.example;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collections;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.drafts.Draft;
import org.java_websocket_olvid.drafts.Draft_6455;
import org.java_websocket_olvid.extensions.permessage_deflate.PerMessageDeflateExtension;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.server.WebSocketServer;

public class AutobahnServerTest extends WebSocketServer {

  private static int openCounter = 0;
  private static int closeCounter = 0;
  private int limit = Integer.MAX_VALUE;

  public AutobahnServerTest(int port, int limit, Draft d) throws UnknownHostException {
    super(new InetSocketAddress(port), Collections.singletonList(d));
    this.limit = limit;
  }

  public AutobahnServerTest(InetSocketAddress address, Draft d) {
    super(address, Collections.singletonList(d));
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    openCounter++;
    System.out.println("///////////Opened connection number" + openCounter);
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    closeCounter++;
    System.out.println("closed");
    if (closeCounter >= limit) {
      System.exit(0);
    }
  }

  @Override
  public void onError(WebSocket conn, Exception ex) {
    System.out.println("Error:");
    ex.printStackTrace();
  }

  @Override
  public void onStart() {
    System.out.println("Server started!");
  }

  @Override
  public void onMessage(WebSocket conn, String message) {
    conn.send(message);
  }

  @Override
  public void onMessage(WebSocket conn, ByteBuffer blob) {
    conn.send(blob);
  }

  public static void main(String[] args) throws UnknownHostException {
    int port, limit;
    try {
      port = new Integer(args[0]);
    } catch (Exception e) {
      System.out.println("No port specified. Defaulting to 9003");
      port = 9003;
    }
    try {
      limit = new Integer(args[1]);
    } catch (Exception e) {
      System.out.println("No limit specified. Defaulting to MaxInteger");
      limit = Integer.MAX_VALUE;
    }
    PerMessageDeflateExtension perMessageDeflateExtension = new PerMessageDeflateExtension();
    perMessageDeflateExtension.setThreshold(0);
    AutobahnServerTest test = new AutobahnServerTest(port, limit,
        new Draft_6455(perMessageDeflateExtension));
    test.setConnectionLostTimeout(0);
    test.start();
  }

}
