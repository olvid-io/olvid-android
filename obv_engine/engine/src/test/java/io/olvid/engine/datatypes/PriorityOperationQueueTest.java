/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.olvid.engine.Logger;

public class PriorityOperationQueueTest {
    @Test
    public void testPriorityOperation() {
        SleepOperation opLong = new SleepOperation(100);
        SleepOperation opShort = new SleepOperation(10);

        PriorityOperationQueue queue = new PriorityOperationQueue();
        queue.queue(opShort);
        queue.queue(opLong);
        queue.execute(1);

        queue.queue(new SleepOperation(150));
        queue.queue(new SleepOperation(250));
        queue.queue(new SleepOperation(1000));
        queue.queue(new SleepOperation(200));

        Logger.setOutputLogLevel(Logger.DEBUG);

        try {
            Thread.sleep(10);
            SleepOperation op = (SleepOperation) queue.getExecutingOperationThatShouldBeCancelledWhenQueueingWithHigherPriority();
            assertEquals(op, opShort);

            Thread.sleep(100);
            assertTrue(opShort.isFinished());
            op = (SleepOperation) queue.getExecutingOperationThatShouldBeCancelledWhenQueueingWithHigherPriority();
            assertEquals(op, opLong);

            opShort = new SleepOperation(10);
            queue.queue(opShort);
            opLong.cancel(null);

            Thread.sleep(200);
            assertTrue(opShort.isFinished());
            assertTrue(opLong.isCancelled());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    class SleepOperation extends PriorityOperation {
        private int sleedDuration;

        public SleepOperation(int sleepDuration) {
            super(null, null, null);
            this.sleedDuration = sleepDuration;
        }

        @Override
        public long getPriority() {
            return sleedDuration;
        }

        @Override
        public void doCancel() {
            // nothing to do here
        }

        @Override
        public void doExecute() {
            while (sleedDuration > 0) {
                try {
                    Thread.sleep(10);
                    sleedDuration-=10;
                } catch (InterruptedException e) {}
                if (cancelWasRequested()) {
                    break;
                }
            }
            processCancel();
            setFinished();
        }
    }
}