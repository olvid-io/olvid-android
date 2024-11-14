/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

// This class is similar to OperationQueue but adds some priority management
// Queued operations cannot have dependencies and must extend PriorityOperation

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.olvid.engine.Logger;

public class PriorityOperationQueue {
    private final PriorityBlockingQueue<PriorityOperation> operations;

    private final List<PriorityOperation> executingOperations;
    private final Lock lockOnExecutingOperations;

    private boolean executing = false;
    private int numberOfThreads;

    public PriorityOperationQueue() {
        this.operations = new PriorityBlockingQueue<>();
        this.executingOperations = new LinkedList<>();
        this.lockOnExecutingOperations = new ReentrantLock();
        this.numberOfThreads = 0;
    }

    public void queue(PriorityOperation op) {
        if (op.getDependencies().size() > 0) {
            Logger.e("Cannot queue an operation with dependencies into a PriorityOperationQueue.");
            return;
        }
        op.setPending();
        operations.add(op);
    }

    public void execute(int numberOfThreads) {
        execute(numberOfThreads, null);
    }

    public void execute(int numberOfThreads, String tag) {
        if (executing) {
            Logger.e("You can only call execute once on a PriorityOperationQueue.");
            return;
        }
        executing = true;
        this.numberOfThreads = numberOfThreads;
        for (int i=0; i<numberOfThreads; i++) {
            new PriorityOperationQueueThread(i, tag).start();
        }
    }

    // NOTE: This method also return null if there is a thread available for the new queued operation
    public PriorityOperation getExecutingOperationThatShouldBeCancelledWhenQueueingWithHigherPriority() {
        PriorityOperation op = null;
        long maxPriority = 0;
        lockOnExecutingOperations.lock();
        if (executingOperations.size() < numberOfThreads) {
            lockOnExecutingOperations.unlock();
            return null;
        }
        for (PriorityOperation operation: executingOperations) {
            long priority = operation.getPriority();
            if ((op == null) || (priority > maxPriority)) {
                op = operation;
                maxPriority = priority;
            }
        }
        lockOnExecutingOperations.unlock();
        return op;
    }

    class PriorityOperationQueueThread extends Thread {
        public final int threadNumber;
        public PriorityOperationQueueThread(int i, String tag) {
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
                PriorityOperation op;
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
                        } catch (InterruptedException e) {
                            // do nothing
                        }
                    }
                }
                op.setTimestampOfLastExecution(System.currentTimeMillis());

                if (op.isReady()) {
                    if (op.areConditionsFulfilled()) {
                        lockOnExecutingOperations.lock();
                        executingOperations.add(op);
                        lockOnExecutingOperations.unlock();

                        try {
                            op.execute();
                        } catch (Exception e) {
                            Logger.e("Exception in operation that could have killed a queue!");
                            Logger.x(e);
                        }

                        lockOnExecutingOperations.lock();
                        executingOperations.remove(op);
                        lockOnExecutingOperations.unlock();
                    } else {
                        operations.add(op);
                    }
                }
            }
        }
    }
}
