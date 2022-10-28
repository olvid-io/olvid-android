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

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {

  private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
  private final AtomicInteger threadNumber = new AtomicInteger(1);
  private final String threadPrefix;

  public NamedThreadFactory(String threadPrefix) {
    this.threadPrefix = threadPrefix;
  }

  @Override
  public Thread newThread(Runnable runnable) {
    Thread thread = defaultThreadFactory.newThread(runnable);
    thread.setName(threadPrefix + "-" + threadNumber);
    return thread;
  }
}
