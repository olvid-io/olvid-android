/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.openid.KeycloakManager;

public class NetworkStateMonitorReceiver extends BroadcastReceiver {
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
        AppSingleton.getEngine().retryScheduledNetworkTasks();
        KeycloakManager.syncAllManagedIdentities();

        Intent networkStateChanged = new Intent(MainActivity.NETWORK_CONNECTIVITY_CHANGED_BROADCAST_ACTION);
        networkStateChanged.setPackage(App.getContext().getPackageName());
        LocalBroadcastManager.getInstance(App.getContext()).sendBroadcast(networkStateChanged);
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
