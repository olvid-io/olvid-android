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

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.java_websocket_olvid.SSLSocketChannel2;
import org.java_websocket_olvid.WebSocketAdapter;
import org.java_websocket_olvid.WebSocketImpl;
import org.java_websocket_olvid.WebSocketServerFactory;
import org.java_websocket_olvid.drafts.Draft;

public class DefaultSSLWebSocketServerFactory implements WebSocketServerFactory {

  protected SSLContext sslcontext;
  protected ExecutorService exec;

  public DefaultSSLWebSocketServerFactory(SSLContext sslContext) {
    this(sslContext, Executors.newSingleThreadScheduledExecutor());
  }

  public DefaultSSLWebSocketServerFactory(SSLContext sslContext, ExecutorService exec) {
    if (sslContext == null || exec == null) {
      throw new IllegalArgumentException();
    }
    this.sslcontext = sslContext;
    this.exec = exec;
  }

  @Override
  public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
    SSLEngine e = sslcontext.createSSLEngine();
    /*
     * See https://github.com/TooTallNate/Java-WebSocket/issues/466
     *
     * We remove TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 from the enabled ciphers since it is just available when you patch your java installation directly.
     * E.g. firefox requests this cipher and this causes some dcs/instable connections
     */
    List<String> ciphers = new ArrayList<>(Arrays.asList(e.getEnabledCipherSuites()));
    ciphers.remove("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
    e.setEnabledCipherSuites(ciphers.toArray(new String[ciphers.size()]));
    e.setUseClientMode(false);
    return new SSLSocketChannel2(channel, e, exec, key);
  }

  @Override
  public WebSocketImpl createWebSocket(WebSocketAdapter a, Draft d) {
    return new WebSocketImpl(a, d);
  }

  @Override
  public WebSocketImpl createWebSocket(WebSocketAdapter a, List<Draft> d) {
    return new WebSocketImpl(a, d);
  }

  @Override
  public void close() {
    exec.shutdown();
  }
}