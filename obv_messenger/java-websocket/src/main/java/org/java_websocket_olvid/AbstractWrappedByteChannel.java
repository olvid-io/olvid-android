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
import java.nio.channels.SocketChannel;

/**
 * @deprecated
 */
@Deprecated
public class AbstractWrappedByteChannel implements WrappedByteChannel {

  private final ByteChannel channel;

  /**
   * @deprecated
   */
  @Deprecated
  public AbstractWrappedByteChannel(ByteChannel towrap) {
    this.channel = towrap;
  }

  /**
   * @deprecated
   */
  @Deprecated
  public AbstractWrappedByteChannel(WrappedByteChannel towrap) {
    this.channel = towrap;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    return channel.read(dst);
  }

  @Override
  public boolean isOpen() {
    return channel.isOpen();
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    return channel.write(src);
  }

  @Override
  public boolean isNeedWrite() {
    return channel instanceof WrappedByteChannel && ((WrappedByteChannel) channel).isNeedWrite();
  }

  @Override
  public void writeMore() throws IOException {
    if (channel instanceof WrappedByteChannel) {
      ((WrappedByteChannel) channel).writeMore();
    }

  }

  @Override
  public boolean isNeedRead() {
    return channel instanceof WrappedByteChannel && ((WrappedByteChannel) channel).isNeedRead();

  }

  @Override
  public int readMore(ByteBuffer dst) throws IOException {
    return channel instanceof WrappedByteChannel ? ((WrappedByteChannel) channel).readMore(dst) : 0;
  }

  @Override
  public boolean isBlocking() {
    if (channel instanceof SocketChannel) {
      return ((SocketChannel) channel).isBlocking();
    } else if (channel instanceof WrappedByteChannel) {
      return ((WrappedByteChannel) channel).isBlocking();
    }
    return false;
  }

}
