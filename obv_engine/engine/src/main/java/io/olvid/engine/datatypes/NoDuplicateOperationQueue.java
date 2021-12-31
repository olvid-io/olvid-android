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


import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.olvid.engine.Logger;

public class NoDuplicateOperationQueue {

    private final BlockingQueue<Operation> operations;
    private final Lock lockOnQueuedOperationUids;
    private final Set<UID> queuedOperationUids;

    private boolean executing = false;

    public NoDuplicateOperationQueue() {
        queuedOperationUids = new HashSet<>();
        lockOnQueuedOperationUids = new ReentrantLock();
        operations = new LinkedBlockingQueue<>();
    }

    public void queue(Operation op) {
        if (op.getDependencies().size() > 0) {
            Logger.e("Cannot queue an operation with dependencies into a NoDuplicateOperationQueue.");
            return;
        }
        UID uid = op.getUid();
        if (uid != null) {
            lockOnQueuedOperationUids.lock();
            if (queuedOperationUids.contains(uid)) {
                Logger.d("NoDuplicateOperationQueue already contains an operation of type " + op.getClass().toString() + " with UID " + uid);
                Logger.d("queue size: " + operations.size() + "  set size: " + queuedOperationUids.size());
                lockOnQueuedOperationUids.unlock();
                return;
            }
            queuedOperationUids.add(uid);
            lockOnQueuedOperationUids.unlock();
        }
        op.setPending();
        operations.add(op);
    }

    public void execute(int numberOfThreads) {
        execute(numberOfThreads, null);
    }

    public void execute(int numberOfThreads, String tag) {
        if (executing) {
            Logger.e("You can only call execute once on a NoDuplicateOperationQueue.");
            return;
        }
        executing = true;
        for (int i=0; i<numberOfThreads; i++) {
            new NoDuplicateOperationQueueThread(i, tag).start();
        }
    }

    class NoDuplicateOperationQueueThread extends Thread {
        final int threadNumber;
        NoDuplicateOperationQueueThread(int i, String tag) {
            super();
            threadNumber = i;
            if (tag != null) {
                setName(tag + "-" + threadNumber);
            }
        }

        @Override
        public void run() {
            //noinspection InfiniteLoopStatement
            while (true) {
                Operation op;
                try {
                    op = operations.take();
                } catch (InterruptedException e) {
                    continue;
                }

                op.updateReadiness();
                op.processCancel();

                if (op.getTimestampOfLastExecution() != 0) {
                    long timeToWait = op.getTimestampOfLastExecution() - System.currentTimeMillis() + OperationQueue.MILLISECONDS_TO_WAIT_BETWEEN_TWO_OPERATION_EXECUTIONS;
                    if (timeToWait > 0) {
                        try {
                            sleep(timeToWait);
                        } catch (InterruptedException ignored) {}
                    }
                }
                op.setTimestampOfLastExecution(System.currentTimeMillis());

                if (op.isReady()) {
                    if (op.areConditionsFulfilled()) {
                        if (op.getUid() != null) {
                            lockOnQueuedOperationUids.lock();
                            queuedOperationUids.remove(op.getUid());
                            lockOnQueuedOperationUids.unlock();
                        }
                        op.execute();
                    } else {
                        operations.add(op);
                    }
                }
            }
        }
    }



}
