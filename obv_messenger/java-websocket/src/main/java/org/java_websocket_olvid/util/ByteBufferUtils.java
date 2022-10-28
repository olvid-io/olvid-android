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

package org.java_websocket_olvid.util;

import java.nio.ByteBuffer;

/**
 * Utility class for ByteBuffers
 */
public class ByteBufferUtils {

  /**
   * Private constructor for static class
   */
  private ByteBufferUtils() {
  }

  /**
   * Transfer from one ByteBuffer to another ByteBuffer
   *
   * @param source the ByteBuffer to copy from
   * @param dest   the ByteBuffer to copy to
   * @return the number of transferred bytes
   */
  public static int transferByteBuffer(ByteBuffer source, ByteBuffer dest) {
    if (source == null || dest == null) {
      throw new IllegalArgumentException();
    }
    int fremain = source.remaining();
    int toremain = dest.remaining();
    if (fremain > toremain) {
      int limit = Math.min(fremain, toremain);
      source.limit(limit);
      dest.put(source);
      return limit;
    } else {
      dest.put(source);
      return fremain;
    }
  }

  /**
   * Get a ByteBuffer with zero capacity
   *
   * @return empty ByteBuffer
   */
  public static ByteBuffer getEmptyByteBuffer() {
    return ByteBuffer.allocate(0);
  }
}
