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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import org.java_websocket_olvid.SSLSocketChannel2;

/**
 * WebSocketFactory that can be configured to only support specific protocols and cipher suites.
 */
public class SSLParametersWebSocketServerFactory extends DefaultSSLWebSocketServerFactory {

  private final SSLParameters sslParameters;

  /**
   * New CustomSSLWebSocketServerFactory configured to only support given protocols and given cipher
   * suites.
   *
   * @param sslContext    - can not be <code>null</code>
   * @param sslParameters - can not be <code>null</code>
   */
  public SSLParametersWebSocketServerFactory(SSLContext sslContext, SSLParameters sslParameters) {
    this(sslContext, Executors.newSingleThreadScheduledExecutor(), sslParameters);
  }

  /**
   * New CustomSSLWebSocketServerFactory configured to only support given protocols and given cipher
   * suites.
   *
   * @param sslContext      - can not be <code>null</code>
   * @param executerService - can not be <code>null</code>
   * @param sslParameters   - can not be <code>null</code>
   */
  public SSLParametersWebSocketServerFactory(SSLContext sslContext, ExecutorService executerService,
      SSLParameters sslParameters) {
    super(sslContext, executerService);
    if (sslParameters == null) {
      throw new IllegalArgumentException();
    }
    this.sslParameters = sslParameters;
  }

  @Override
  public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
    SSLEngine e = sslcontext.createSSLEngine();
    e.setUseClientMode(false);
    e.setSSLParameters(sslParameters);
    return new SSLSocketChannel2(channel, e, exec, key);
  }
}