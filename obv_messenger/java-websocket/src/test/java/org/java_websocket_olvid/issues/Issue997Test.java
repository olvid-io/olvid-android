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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import org.java_websocket_olvid.WebSocket;
import org.java_websocket_olvid.client.WebSocketClient;
import org.java_websocket_olvid.handshake.ClientHandshake;
import org.java_websocket_olvid.handshake.ServerHandshake;
import org.java_websocket_olvid.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket_olvid.server.WebSocketServer;
import org.java_websocket_olvid.util.SSLContextUtil;
import org.java_websocket_olvid.util.SocketUtil;
import org.junit.Test;

public class Issue997Test {

  @Test(timeout = 2000)
  public void test_localServer_ServerLocalhost_Client127_CheckActive()
      throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException, URISyntaxException, InterruptedException {
    SSLWebSocketClient client = testIssueWithLocalServer("127.0.0.1", SocketUtil.getAvailablePort(),
        SSLContextUtil.getLocalhostOnlyContext(), SSLContextUtil.getLocalhostOnlyContext(),
        "HTTPS");
    assertFalse(client.onOpen);
    assertTrue(client.onSSLError);
  }

  @Test(timeout = 2000)
  public void test_localServer_ServerLocalhost_Client127_CheckInactive()
      throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException, URISyntaxException, InterruptedException {
    SSLWebSocketClient client = testIssueWithLocalServer("127.0.0.1", SocketUtil.getAvailablePort(),
        SSLContextUtil.getLocalhostOnlyContext(), SSLContextUtil.getLocalhostOnlyContext(), "");
    assertTrue(client.onOpen);
    assertFalse(client.onSSLError);
  }

  @Test(timeout = 2000)
  public void test_localServer_ServerLocalhost_Client127_CheckDefault()
      throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException, URISyntaxException, InterruptedException {
    SSLWebSocketClient client = testIssueWithLocalServer("127.0.0.1", SocketUtil.getAvailablePort(),
        SSLContextUtil.getLocalhostOnlyContext(), SSLContextUtil.getLocalhostOnlyContext(), null);
    assertFalse(client.onOpen);
    assertTrue(client.onSSLError);
  }

  @Test(timeout = 2000)
  public void test_localServer_ServerLocalhost_ClientLocalhost_CheckActive()
      throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException, URISyntaxException, InterruptedException {
    SSLWebSocketClient client = testIssueWithLocalServer("localhost", SocketUtil.getAvailablePort(),
        SSLContextUtil.getLocalhostOnlyContext(), SSLContextUtil.getLocalhostOnlyContext(),
        "HTTPS");
    assertTrue(client.onOpen);
    assertFalse(client.onSSLError);
  }

  @Test(timeout = 2000)
  public void test_localServer_ServerLocalhost_ClientLocalhost_CheckInactive()
      throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException, URISyntaxException, InterruptedException {
    SSLWebSocketClient client = testIssueWithLocalServer("localhost", SocketUtil.getAvailablePort(),
        SSLContextUtil.getLocalhostOnlyContext(), SSLContextUtil.getLocalhostOnlyContext(), "");
    assertTrue(client.onOpen);
    assertFalse(client.onSSLError);
  }

  @Test(timeout = 2000)
  public void test_localServer_ServerLocalhost_ClientLocalhost_CheckDefault()
      throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException, URISyntaxException, InterruptedException {
    SSLWebSocketClient client = testIssueWithLocalServer("localhost", SocketUtil.getAvailablePort(),
        SSLContextUtil.getLocalhostOnlyContext(), SSLContextUtil.getLocalhostOnlyContext(), null);
    assertTrue(client.onOpen);
    assertFalse(client.onSSLError);
  }


  public SSLWebSocketClient testIssueWithLocalServer(String address, int port,
      SSLContext serverContext, SSLContext clientContext, String endpointIdentificationAlgorithm)
      throws IOException, URISyntaxException, InterruptedException {
    CountDownLatch countServerDownLatch = new CountDownLatch(1);
    SSLWebSocketClient client = new SSLWebSocketClient(address, port,
        endpointIdentificationAlgorithm);
    WebSocketServer server = new SSLWebSocketServer(port, countServerDownLatch);

    server.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(serverContext));
    if (clientContext != null) {
      client.setSocketFactory(clientContext.getSocketFactory());
    }
    server.start();
    countServerDownLatch.await();
    client.connectBlocking(1, TimeUnit.SECONDS);
    return client;
  }


  private static class SSLWebSocketClient extends WebSocketClient {

    private final String endpointIdentificationAlgorithm;
    public boolean onSSLError = false;
    public boolean onOpen = false;

    public SSLWebSocketClient(String address, int port, String endpointIdentificationAlgorithm)
        throws URISyntaxException {
      super(new URI("wss://" + address + ':' + port));
      this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
      this.onOpen = true;
    }

    @Override
    public void onMessage(String message) {
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Exception ex) {
      if (ex instanceof SSLHandshakeException) {
        this.onSSLError = true;
      }
    }

    @Override
    protected void onSetSSLParameters(SSLParameters sslParameters) {
      // Always call super to ensure hostname validation is active by default
      super.onSetSSLParameters(sslParameters);
      if (endpointIdentificationAlgorithm != null) {
        sslParameters.setEndpointIdentificationAlgorithm(endpointIdentificationAlgorithm);
      }
    }

  }


  private static class SSLWebSocketServer extends WebSocketServer {

    private final CountDownLatch countServerDownLatch;


    public SSLWebSocketServer(int port, CountDownLatch countServerDownLatch) {
      super(new InetSocketAddress(port));
      this.countServerDownLatch = countServerDownLatch;
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
      countServerDownLatch.countDown();
    }
  }
}
