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

package io.olvid.messenger.main;


import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.google_services.GoogleServicesUtils;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;

public class Utils {
    public static boolean dialogsLoaded = false;
    static boolean dialogShowing = false;

    public static String getUptime(Context context) {
        int uptimeSeconds = (int) ((System.currentTimeMillis() - App.appStartTimestamp) / 1000);
        final String uptime;
        if (uptimeSeconds > 86400) {
            uptime = context.getResources().getQuantityString(R.plurals.text_app_uptime_days, uptimeSeconds / 86400, uptimeSeconds / 86400, (uptimeSeconds % 86400) / 3600, (uptimeSeconds % 3600) / 60, uptimeSeconds % 60);
        } else if (uptimeSeconds > 3600) {
            uptime = context.getString(R.string.text_app_uptime_hours, uptimeSeconds / 3600, (uptimeSeconds % 3600) / 60, uptimeSeconds % 60);
        } else {
            uptime = context.getString(R.string.text_app_uptime, uptimeSeconds / 60, uptimeSeconds % 60);
        }
        return uptime;
    }

    // region Websocket latency ping
    static Timer pingTimer = null;
    static boolean doPing = false;

    public static void startPinging() {
        doPing = true;
        if (pingTimer == null) {
            pingTimer = new Timer("MainActivity-websocketLatencyPingTimer");
            pingTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (doPing) {
                        byte[] bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
                        if (bytesOwnedIdentity != null) {
                            AppSingleton.getEngine().pingWebsocket(bytesOwnedIdentity);
                        }
                    } else {
                        pingTimer.cancel();
                        pingTimer = null;
                    }
                }
            }, 0, 10_000);
        } else {
            // even if a ping is already schedule, immediately ping
            byte[] bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity();
            if (bytesOwnedIdentity != null) {
                AppSingleton.getEngine().pingWebsocket(bytesOwnedIdentity);
            }
        }
    }

    public static void stopPinging() {
        doPing = false;
    }

    // endregion
}
