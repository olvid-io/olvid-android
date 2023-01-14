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

import org.junit.Test;

import static org.junit.Assert.*;

public class OperationsUnitTest {
    Integer j;

    @Test
    public void testOperationQueue() {
        j = 0;
        OperationQueue queue = new OperationQueue();
        for (int o=0; o<2; o++) {
            Operation op = new Operation() {
                @Override
                public void doCancel() {

                }

                @Override
                public void doExecute() {
                    for (int i = 0; i < 10000; i++) {
                        synchronized (j) {
                            j += i;
                        }
                    }
                    setFinished();
                }
            };
            queue.queue(op);
        }
        for (int o=0; o<3; o++) {
            Operation op = new Operation() {
                @Override
                public void doCancel() {

                }

                @Override
                public void doExecute() {
                    for (int i = 0; i < 20000; i++) {
                        synchronized (j) {
                            j += i;
                        }
                    }
                    setFinished();
                }
            };
            queue.queue(op);
        }
        queue.execute(1);
        queue.join();
        assertEquals(j.intValue(), 2*5000*9999 + 3*10000*19999);
    }
}
