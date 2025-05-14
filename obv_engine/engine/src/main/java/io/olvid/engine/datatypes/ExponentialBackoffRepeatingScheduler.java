/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.olvid.engine.Logger;

public class ExponentialBackoffRepeatingScheduler<T> {
    private final ScheduledExecutorService scheduler;
    private final HashMap<T, Integer> failedAttemptCounts;
    private final HashMap<T, Runnable> pendingRunnables;
    private final Object lock = new Object();
    private final Random random = new Random();


    public ExponentialBackoffRepeatingScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.failedAttemptCounts = new HashMap<>();
        this.pendingRunnables = new HashMap<>();
    }

    public void schedule(T key, Runnable runnable) {
        schedule(key, runnable, null);
    }

    public void schedule(T key, Runnable runnable, String tag) {
        synchronized (lock) {
            Runnable oldRunnable = pendingRunnables.get(key);
            if (oldRunnable != null) {
                return;
            }
            pendingRunnables.put(key, runnable);

            Integer failedCount = failedAttemptCounts.get(key);
            if (failedCount == null) {
                failedCount = 1;
            } else {
                failedCount++;
            }
            failedAttemptCounts.put(key, failedCount);
            long delay = computeReschedulingDelay(failedCount);
            if (tag != null) {
                Logger.i("Scheduling a " + tag + " for " + key.toString() + " in " + delay + "ms.");
            }
            scheduler.schedule(() -> {
                Runnable runnab;
                synchronized (lock) {
                    runnab = pendingRunnables.get(key);
                    if (runnab != null) {
                        pendingRunnables.remove(key);
                    }
                }
                if (runnab != null) {
                    runnab.run();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    // used to schedule a task with a given delay, independently of failed attempts
    public void schedule(T key, Runnable runnable, String tag, long delay) {
        synchronized (lock) {
            if (tag != null) {
                Logger.i("Scheduling a " + tag + " for " + key.toString() + " in " + delay + "ms.");
            }
            scheduler.schedule(runnable, delay, TimeUnit.MILLISECONDS);
        }
    }


    public void clearFailedCount(T key) {
        failedAttemptCounts.remove(key);
    }

    public void retryScheduledRunnables() {
        List<Runnable> runnables;
        synchronized (lock) {
            runnables = new ArrayList<>(pendingRunnables.values());
            pendingRunnables.clear();
            failedAttemptCounts.clear();
        }
        scheduler.execute(() -> {
            for(Runnable runnable: runnables) {
                runnable.run();
            }
        });
    }

    // for polling only
    protected long computeReschedulingDelay(int failedAttemptCount) {
        long base = Constants.BASE_RESCHEDULING_TIME << Math.min(failedAttemptCount, 32);
        return (long) (base * (1 + random.nextFloat()));
    }
}
