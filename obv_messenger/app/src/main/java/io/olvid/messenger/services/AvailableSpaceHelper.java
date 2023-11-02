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

package io.olvid.messenger.services;

import android.os.storage.StorageManager;
import android.text.format.Formatter;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.settings.SettingsActivity;

public class AvailableSpaceHelper {
    public final static long AVAILABLE_SPACE_WARNING_THRESHOLD = 50_000_000L;
    private final static long MIN_REFRESH_INTERVAL_MILLIS = 600_000L; // 10 minutes
    private final static long MIN_WARNING_INTERVAL_MILLIS = 7_200_000L; // 2 hours

    private static Long availableSpace = null;
    private static long nextRefresh = 0L;

    public static void refreshAvailableSpace(boolean forceRefreshNow) {
        if (!forceRefreshNow && System.currentTimeMillis() < nextRefresh) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                StorageManager storageManager = App.getContext().getSystemService(StorageManager.class);
                UUID appDir = storageManager.getUuidForPath(new File(App.absolutePathFromRelative("")));
                availableSpace = storageManager.getAllocatableBytes(appDir);
                nextRefresh = System.currentTimeMillis() + MIN_REFRESH_INTERVAL_MILLIS;

                if (availableSpace < AVAILABLE_SPACE_WARNING_THRESHOLD
                        && System.currentTimeMillis() > SettingsActivity.getLastAvailableSpaceWarningTimestamp() + MIN_WARNING_INTERVAL_MILLIS) {
                    App.openAppDialogLowStorageSpace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            availableSpace = new File(App.absolutePathFromRelative("")).getUsableSpace();
            nextRefresh = System.currentTimeMillis() + MIN_REFRESH_INTERVAL_MILLIS;

            if (availableSpace < AVAILABLE_SPACE_WARNING_THRESHOLD
                    && System.currentTimeMillis() > SettingsActivity.getLastAvailableSpaceWarningTimestamp() + MIN_WARNING_INTERVAL_MILLIS) {
                App.openAppDialogLowStorageSpace();
            }
        }
        Logger.d("Measured available space on device: " + Formatter.formatShortFileSize(App.getContext(), availableSpace));
    }

    @Nullable
    public static Long getAvailableSpace() {
        return availableSpace;
    }

    public static void acknowledgeWarning() {
        SettingsActivity.setLastAvailableSpaceWarningTimestamp(System.currentTimeMillis());
    }
}
