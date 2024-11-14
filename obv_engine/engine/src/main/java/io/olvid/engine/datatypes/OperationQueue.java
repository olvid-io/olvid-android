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


import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.olvid.engine.Logger;

public class OperationQueue {
    protected static final int MILLISECONDS_TO_WAIT_BETWEEN_TWO_OPERATION_EXECUTIONS = 20;

    private final Queue<Operation> operations;
    private final Lock lockOnCount;
    private final boolean persistent;
    private int count;
    private final Object notifier;

    private boolean executing = false;

    public OperationQueue() {
        this(false);
    }

    public OperationQueue(boolean persistent) {
        this.persistent = persistent;
        this.operations = new ConcurrentLinkedQueue<>();
        this.lockOnCount = new ReentrantLock();
        this.count = 0;
        this.notifier = new Object();
    }

    private void addOperation(Operation op) {
        lockOnCount.lock();
        count++;
        operations.add(op);
        lockOnCount.unlock();
        synchronized (notifier) {
            notifier.notifyAll();
        }
    }

    public void queue(Operation op) {
        op.setPending();
        addOperation(op);
    }


    // this method waits for the queue to be empty.
    // If the queue is non-persistent, a join only returns once all threads are dead.
    // If the queue is persistent, additional operations can still be added later on.
    public void join() {
        lockOnCount.lock();
        boolean queueIsEmpty = count == 0;
        lockOnCount.unlock();
        while (!queueIsEmpty) {
            synchronized (notifier) {
                try {
                    notifier.wait(500);
                } catch (InterruptedException e) {
                    Logger.x(e);
                }
            }
            lockOnCount.lock();
            queueIsEmpty = count == 0;
            lockOnCount.unlock();
        }
    }

    public void execute(int numberOfThreads) {
        execute(numberOfThreads, null);
    }

    public void execute(int numberOfThreads, String tag) {
        if (persistent) {
            if (executing) {
                Logger.e("You can only call execute once on a persistent OperationQueue.");
                return;
            }
            executing = true;
        }
        for (int i=0; i<numberOfThreads; i++) {
            new OperationQueueThread(i, tag).start();
        }
    }


    class OperationQueueThread extends Thread {
        public final int threadNumber;

        public OperationQueueThread(int i, String tag) {
            super();
            threadNumber = i;
            if (tag != null) {
                setName(tag + "-" + threadNumber);
            }
        }

        @Override
        public void run() {
            while (true) {
                Operation op = operations.poll();
                if (op == null) {
                    if (persistent) {
                        synchronized (notifier) {
                            try {
                                notifier.wait(30000);
                            } catch (InterruptedException e) {
                                Logger.x(e);
                            }
                        }
                        continue;
                    } else {
                        break;
                    }
                }

                op.updateReadiness();
                op.processCancel();

                if (op.getTimestampOfLastExecution() != 0) {
                    long timeToWait = op.getTimestampOfLastExecution() - System.currentTimeMillis() + MILLISECONDS_TO_WAIT_BETWEEN_TWO_OPERATION_EXECUTIONS;
                    if (timeToWait > 0) {
                        try {
                            sleep(timeToWait);
                        } catch (InterruptedException ignored) {}
                    }
                }
                op.setTimestampOfLastExecution(System.currentTimeMillis());

                if (op.isPending()) {
                    addOperation(op);
                }
                if (op.isReady()) {
                    if (op.areConditionsFulfilled()) {
                        try {
                            op.execute();
                        } catch (Exception e) {
                            Logger.e("Exception in operation that could have killed a queue!");
                            Logger.x(e);
                        }
                } else {
                        addOperation(op);
                    }
                }


                lockOnCount.lock();
                count--;
                synchronized (notifier) {
                    notifier.notifyAll();
                }
                lockOnCount.unlock();
            }
        }
    }
}
