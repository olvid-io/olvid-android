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

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Collections;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.drafts.Draft;
import org.java_websocket_olvid.drafts.Draft_6455;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket_olvid.server.WebSocketServer;

public class AutobahnSSLServerTest extends WebSocketServer {

  private static int counter = 0;

  public AutobahnSSLServerTest(int port, Draft d) throws UnknownHostException {
    super(new InetSocketAddress(port), Collections.singletonList(d));
  }

  public AutobahnSSLServerTest(InetSocketAddress address, Draft d) {
    super(address, Collections.singletonList(d));
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    counter++;
    System.out.println("///////////Opened connection number" + counter);
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    System.out.println("closed");
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
    int port;
    try {
      port = new Integer(args[0]);
    } catch (Exception e) {
      System.out.println("No port specified. Defaulting to 9003");
      port = 9003;
    }
    AutobahnSSLServerTest test = new AutobahnSSLServerTest(port, new Draft_6455());
    try {
      // load up the key store
      String STORETYPE = "JKS";
      String KEYSTORE = Paths.get("src", "test", "java", "org", "java_websocket", "keystore.jks")
          .toString();
      String STOREPASSWORD = "storepassword";
      String KEYPASSWORD = "keypassword";

      KeyStore ks = KeyStore.getInstance(STORETYPE);
      File kf = new File(KEYSTORE);
      ks.load(new FileInputStream(kf), STOREPASSWORD.toCharArray());

      KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
      kmf.init(ks, KEYPASSWORD.toCharArray());
      TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
      tmf.init(ks);

      SSLContext sslContext = null;
      sslContext = SSLContext.getInstance("TLS");
      sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

      test.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
    } catch (Exception e) {
      e.printStackTrace();
    }
    test.setConnectionLostTimeout(0);
    test.start();
  }

}
