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
import org.java_websocket_olvid.enums.Role;

public class SocketChannelIOHelper {

  private SocketChannelIOHelper() {
    throw new IllegalStateException("Utility class");
  }

  public static boolean read(final ByteBuffer buf, WebSocketImpl ws, ByteChannel channel)
      throws IOException {
    buf.clear();
    int read = channel.read(buf);
    buf.flip();

    if (read == -1) {
      ws.eot();
      return false;
    }
    return read != 0;
  }

  /**
   * @param buf     The ByteBuffer to read from
   * @param ws      The WebSocketImpl associated with the channels
   * @param channel The channel to read from
   * @return returns Whether there is more data left which can be obtained via {@link
   * WrappedByteChannel#readMore(ByteBuffer)}
   * @throws IOException May be thrown by {@link WrappedByteChannel#readMore(ByteBuffer)}#
   * @see WrappedByteChannel#readMore(ByteBuffer)
   **/
  public static boolean readMore(final ByteBuffer buf, WebSocketImpl ws, WrappedByteChannel channel)
      throws IOException {
    buf.clear();
    int read = channel.readMore(buf);
    buf.flip();

    if (read == -1) {
      ws.eot();
      return false;
    }
    return channel.isNeedRead();
  }

  /**
   * Returns whether the whole outQueue has been flushed
   *
   * @param ws          The WebSocketImpl associated with the channels
   * @param sockchannel The channel to write to
   * @return returns Whether there is more data to write
   * @throws IOException May be thrown by {@link WrappedByteChannel#writeMore()}
   */
  public static boolean batch(WebSocketImpl ws, ByteChannel sockchannel) throws IOException {
    if (ws == null) {
      return false;
    }
    ByteBuffer buffer = ws.outQueue.peek();
    WrappedByteChannel c = null;

    if (buffer == null) {
      if (sockchannel instanceof WrappedByteChannel) {
        c = (WrappedByteChannel) sockchannel;
        if (c.isNeedWrite()) {
          c.writeMore();
        }
      }
    } else {
      do {
        // FIXME writing as much as possible is unfair!!
        /*int written = */
        sockchannel.write(buffer);
        if (buffer.remaining() > 0) {
          return false;
        } else {
          ws.outQueue.poll(); // Buffer finished. Remove it.
          buffer = ws.outQueue.peek();
        }
      } while (buffer != null);
    }

    if (ws.outQueue.isEmpty() && ws.isFlushAndClose() && ws.getDraft() != null
        && ws.getDraft().getRole() != null && ws.getDraft().getRole() == Role.SERVER) {
      ws.closeConnection();
    }
    return c == null || !((WrappedByteChannel) sockchannel).isNeedWrite();
  }
}
