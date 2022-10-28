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
import org.java_websocket_olvid.SSLSocketChannel2;

/**
 * WebSocketFactory that can be configured to only support specific protocols and cipher suites.
 */
public class CustomSSLWebSocketServerFactory extends DefaultSSLWebSocketServerFactory {

  /**
   * The enabled protocols saved as a String array
   */
  private final String[] enabledProtocols;

  /**
   * The enabled ciphersuites saved as a String array
   */
  private final String[] enabledCiphersuites;

  /**
   * New CustomSSLWebSocketServerFactory configured to only support given protocols and given cipher
   * suites.
   *
   * @param sslContext          - can not be <code>null</code>
   * @param enabledProtocols    - only these protocols are enabled, when <code>null</code> default
   *                            settings will be used.
   * @param enabledCiphersuites - only these cipher suites are enabled, when <code>null</code>
   *                            default settings will be used.
   */
  public CustomSSLWebSocketServerFactory(SSLContext sslContext, String[] enabledProtocols,
      String[] enabledCiphersuites) {
    this(sslContext, Executors.newSingleThreadScheduledExecutor(), enabledProtocols,
        enabledCiphersuites);
  }

  /**
   * New CustomSSLWebSocketServerFactory configured to only support given protocols and given cipher
   * suites.
   *
   * @param sslContext          - can not be <code>null</code>
   * @param executerService     - can not be <code>null</code>
   * @param enabledProtocols    - only these protocols are enabled, when <code>null</code> default
   *                            settings will be used.
   * @param enabledCiphersuites - only these cipher suites are enabled, when <code>null</code>
   *                            default settings will be used.
   */
  public CustomSSLWebSocketServerFactory(SSLContext sslContext, ExecutorService executerService,
      String[] enabledProtocols, String[] enabledCiphersuites) {
    super(sslContext, executerService);
    this.enabledProtocols = enabledProtocols;
    this.enabledCiphersuites = enabledCiphersuites;
  }

  @Override
  public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
    SSLEngine e = sslcontext.createSSLEngine();
    if (enabledProtocols != null) {
      e.setEnabledProtocols(enabledProtocols);
    }
    if (enabledCiphersuites != null) {
      e.setEnabledCipherSuites(enabledCiphersuites);
    }
    e.setUseClientMode(false);
    return new SSLSocketChannel2(channel, e, exec, key);
  }

}