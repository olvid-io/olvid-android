/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.history_transfer.types

import io.olvid.engine.Logger
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor

// This class is a NoExceptionSingleThreadExecutor that provides an additional executeTracked() method to be able to detect that
class TrackingExecutor(name: String) : NoExceptionSingleThreadExecutor(name) {
    var trackedExecuteCount = 0

    fun executeTracked(onLastTrackedExecuted: Runnable? = null, r: Runnable) {
        trackedExecuteCount++
        super.execute {
            r.run()
            trackedExecuteCount--

            if (trackedExecuteCount == 0) {
                onLastTrackedExecuted?.let {
                    super.execute(it)
                }
            }
        }
    }

    fun hasPendingTrackedExecutes(): Boolean {
        return  trackedExecuteCount > 0
    }
}