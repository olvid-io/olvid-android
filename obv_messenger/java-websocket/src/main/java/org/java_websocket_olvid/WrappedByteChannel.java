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

package org.java_websocket_olvid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

public interface WrappedByteChannel extends ByteChannel {

  /**
   * returns whether writeMore should be called write additional data.
   *
   * @return is a additional write needed
   */
  boolean isNeedWrite();

  /**
   * Gets called when {@link #isNeedWrite()} ()} requires a additional rite
   *
   * @throws IOException may be thrown due to an error while writing
   */
  void writeMore() throws IOException;

  /**
   * returns whether readMore should be called to fetch data which has been decoded but not yet been
   * returned.
   *
   * @return is a additional read needed
   * @see #read(ByteBuffer)
   * @see #readMore(ByteBuffer)
   **/
  boolean isNeedRead();

  /**
   * This function does not read data from the underlying channel at all. It is just a way to fetch
   * data which has already be received or decoded but was but was not yet returned to the user.
   * This could be the case when the decoded data did not fit into the buffer the user passed to
   * {@link #read(ByteBuffer)}.
   *
   * @param dst the destiny of the read
   * @return the amount of remaining data
   * @throws IOException when a error occurred during unwrapping
   **/
  int readMore(ByteBuffer dst) throws IOException;

  /**
   * This function returns the blocking state of the channel
   *
   * @return is the channel blocking
   */
  boolean isBlocking();
}
