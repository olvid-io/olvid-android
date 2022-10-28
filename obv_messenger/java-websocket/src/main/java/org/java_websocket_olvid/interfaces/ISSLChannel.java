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

package org.java_websocket_olvid.interfaces;

import javax.net.ssl.SSLEngine;

/**
 * Interface which specifies all required methods a SSLSocketChannel has to make public.
 *
 * @since 1.4.1
 */
public interface ISSLChannel {

  /**
   * Get the ssl engine used for the de- and encryption of the communication.
   *
   * @return the ssl engine of this channel
   */
  SSLEngine getSSLEngine();
}
