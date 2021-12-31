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

package io.olvid.messenger.services;


import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.PreferenceManager;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.settings.SettingsActivity;

public class AlarmPermissionStateChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // access to alarm was restored
        // - restart the UnifiedForeground
        // - reschedule message expiration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Logger.w("Permission to set exact alarms was granted!");
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                // reset hidden dialog flag
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove(SettingsActivity.USER_DIALOG_HIDE_ALARM_SCHEDULING);
                editor.apply();

                // restart unified foreground service (in case app was still running)
                context.startService(new Intent(context, UnifiedForegroundService.class));
                UnifiedForegroundService.onAppBackground(context);

                // reschedule expiration
                App.runThread(MessageExpirationService::scheduleNextExpiration);
            }
        }
    }
}
