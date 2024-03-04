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

package io.olvid.messenger.customClasses;


import android.content.Context;

import io.olvid.messenger.R;

public enum LocationShareQuality {
    QUALITY_PRECISE(1),
    QUALITY_BALANCED(2),
    QUALITY_POWER_SAVE(3);

    public final int value;

    LocationShareQuality(int value) {
        this.value = value;
    }

    public static LocationShareQuality fromValue(int value) {
        switch (value) {
            case 3:
                return QUALITY_POWER_SAVE;
            case 2:
                return QUALITY_BALANCED;
            case 1:
            default:
                return QUALITY_PRECISE;
        }
    }

    public CharSequence getFullString(Context context) {
        switch (this) {
            case QUALITY_BALANCED:
                return context.getText(R.string.location_sharing_quality_balanced_full_string);
            case QUALITY_POWER_SAVE:
                return context.getText(R.string.location_sharing_quality_power_save_full_string);
            case QUALITY_PRECISE:
            default:
                return context.getText(R.string.location_sharing_quality_precise_full_string);
        }
    }

    public long getMinUpdateFrequencyMs() {
        switch (this) {
            case QUALITY_BALANCED:
                return 12_000;
            case QUALITY_POWER_SAVE:
                return 60_000;
            case QUALITY_PRECISE:
            default:
                return 3_000;
        }
    }

    // for this, we pick slightly smaller durations to have some leeway when checking if this was elapsed
    public long getDefaultUpdateFrequencyMs() {
        switch (this) {
            case QUALITY_BALANCED:
                return 110_000; // 10 min - 10s
            case QUALITY_POWER_SAVE:
                return 1_750_000; // 30 min - 50s
            case QUALITY_PRECISE:
            default:
                return 28_000; // 30s - 2s
        }
    }
    public int getMinUpdateDistanceMeters() {
        switch (this) {
            case QUALITY_BALANCED:
                return 20;
            case QUALITY_POWER_SAVE:
                return 100;
            case QUALITY_PRECISE:
            default:
                return 5;
        }
    }
}
