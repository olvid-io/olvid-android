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

package org.java_websocket_olvid.client;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Users may implement this interface to override the default DNS lookup offered by the OS.
 *
 * @since 1.4.1
 */
public interface DnsResolver {

  /**
   * Resolves the IP address for the given URI.
   * <p>
   * This method should never return null. If it's not able to resolve the IP address then it should
   * throw an UnknownHostException
   *
   * @param uri The URI to be resolved
   * @return The resolved IP address
   * @throws UnknownHostException if no IP address for the <code>uri</code> could be found.
   */
  InetAddress resolve(URI uri) throws UnknownHostException;

}
