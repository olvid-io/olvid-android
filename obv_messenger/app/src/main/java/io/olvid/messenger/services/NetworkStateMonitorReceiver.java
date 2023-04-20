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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.openid.KeycloakManager;

public class NetworkStateMonitorReceiver extends BroadcastReceiver {
    private static long latestNetworkRestart = 0;

    private final static ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            try {
                restartNetworkJobs();
            } catch (Exception | NoClassDefFoundError e) {
                // do nothing
            }
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            monitorNetworkWhileOff(App.getContext());
        }
    };

    private static void restartNetworkJobs() {
        // do not restart within less than 5 seconds
        if (latestNetworkRestart + 5_000 < System.currentTimeMillis()) {
            latestNetworkRestart = System.currentTimeMillis();
            AppSingleton.getEngine().retryScheduledNetworkTasks();
            KeycloakManager.syncAllManagedIdentities();
            BackupCloudProviderService.networkAvailable();
            // try to reconnect websocket in case connection was lost for a long time and exponential backup may take some time top try again
            UnifiedForegroundService.connectOrDisconnectWebSocket();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        restartNetworkJobs();
    }

    
    public static void startMonitoringNetwork(final Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest networkRequest = new NetworkRequest.Builder().build();
        if (connectivityManager != null) {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        }
    }


    public static void monitorNetworkWhileOff(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest networkRequest = new NetworkRequest.Builder().build();
            if (connectivityManager != null) {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, NetworkStateMonitorReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                try {
                    connectivityManager.registerNetworkCallback(networkRequest, pendingIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
