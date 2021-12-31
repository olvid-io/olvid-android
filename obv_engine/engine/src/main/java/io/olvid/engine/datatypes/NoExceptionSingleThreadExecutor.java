/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

package io.olvid.engine.datatypes;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoExceptionSingleThreadExecutor implements Executor {
    private final String name;
    private final ExecutorService executor;

    public NoExceptionSingleThreadExecutor(String name) {
        this.name = name;
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, name));
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void execute(Runnable r) {
        try {
            executor.execute(r);
        } catch (Exception e) {
            // do nothing, this is sometimes normal
        }
    }

    public void shutdownNow() {
        try {
            executor.shutdownNow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
