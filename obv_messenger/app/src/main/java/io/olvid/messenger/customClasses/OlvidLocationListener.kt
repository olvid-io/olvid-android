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

package io.olvid.messenger.customClasses

import android.location.Location
import androidx.core.location.LocationListenerCompat
import io.olvid.messenger.services.GpsDebugLogger


interface OlvidLocationListener : LocationListenerCompat {
    override fun onLocationChanged(locations: MutableList<Location?>) {
        GpsDebugLogger.logGpsEvent("Received a batch of ${locations.size} locations --> keeping best location")
        locations.maxWith(OlvidLocationListener::isBetterLocation)?.let {
            onLocationChanged(it)
        }
    }

    companion object {
        const val TIME_THRESHOLD_MS = 20_000L
        const val ACCURACY_RATIO = 2f

        // ==============================================================
        // | accuracy     time => |      older       |     newer        |
        // |   V                  | much old | a bit | a bit | much new |
        // |============================================================|
        // |            much less |    -1    |  -1   |  -1   |     1    |
        // | less acc. -------------------------------------------------|
        // |           a bit less |    -1    |  -1   |   1   |     1    |
        // |============================================================|
        // |           a bit more |    -1    |  -1   |   1   |     1    |
        // | more acc. -------------------------------------------------|
        // |            much more |    -1    |   1   |   1   |     1    |
        // |============================================================|

        private fun isBetterLocation(newLocation: Location?, referenceLocation: Location?) : Int {
            if (newLocation == null) {
                return if (referenceLocation == null) 0 else -1
            } else {
                if (referenceLocation == null) {
                    return 1
                } else {
                    // both locations are non-null
                    val isNewer = newLocation.time > referenceLocation.time
                    val isMuchNewer = newLocation.time - TIME_THRESHOLD_MS > referenceLocation.time
                    val isMuchOlder = newLocation.time  < referenceLocation.time - TIME_THRESHOLD_MS
                    // accuracy is a distance in meters, so smaller accuracy means more accurate location
                    val isMoreAccurate = newLocation.accuracy < referenceLocation.accuracy
                    val isMuchMoreAccurate = if (
                        referenceLocation.hasAccuracy()
                        && newLocation.hasAccuracy()
                        && newLocation.accuracy > 0.1)
                        referenceLocation.accuracy / newLocation.accuracy > ACCURACY_RATIO
                    else
                        false
                    val isMuchLessAccurate = if (
                        referenceLocation.hasAccuracy()
                        && newLocation.hasAccuracy()
                        && referenceLocation.accuracy > 0.1)
                        newLocation.accuracy / referenceLocation.accuracy  > ACCURACY_RATIO
                    else
                        false

                    return if (isNewer && isMoreAccurate) {
                        1
                    } else if (!isNewer && !isMoreAccurate) {
                        -1
                    } else if (isMuchNewer && !isMuchLessAccurate){
                        1
                    } else if (isMuchMoreAccurate && !isMuchOlder) {
                        1
                    } else if (isMuchOlder && !isMuchMoreAccurate) {
                        -1
                    } else if (isMuchLessAccurate && !isMuchNewer) {
                        -1
                    } else {
                        if (isNewer) 1 else -1
                    }
                }
            }
        }
    }
}