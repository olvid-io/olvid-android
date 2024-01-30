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



public abstract class PriorityOperation extends Operation implements Comparable<PriorityOperation> {
    public PriorityOperation(UID uid, Operation.OnFinishCallback onFinishCallback, Operation.OnCancelCallback onCancelCallback) {
        super(uid, onFinishCallback, onCancelCallback);
    }

    @Override
    public int compareTo(PriorityOperation priorityOperation) {
        if (this.getPriority() < priorityOperation.getPriority()) {
            return -1;
        } else if (this.getPriority() == priorityOperation.getPriority()) {
            return 0;
        }
        return 1;
    }

    public abstract long getPriority();
}
