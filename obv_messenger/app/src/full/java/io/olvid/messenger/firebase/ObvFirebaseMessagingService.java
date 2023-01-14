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

package io.olvid.messenger.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.settings.SettingsActivity;

public class ObvFirebaseMessagingService extends FirebaseMessagingService {
    private static Long lastPushNotificationTimestamp = null;

    public static Long getLastPushNotificationTimestamp() {
        return lastPushNotificationTimestamp;
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        lastPushNotificationTimestamp = System.currentTimeMillis();
        Logger.d("FIREBASE Message received");
        Map<String, String> data = remoteMessage.getData();
        String identityString = data.get("identity");
        String topic = data.get("topic");
        if (identityString != null) {
            Logger.d("For identity mask: " + identityString);
            AppSingleton.getEngine().processAndroidPushNotification(identityString);
        } else if (topic != null) {
            Logger.d("For push topic: " + topic);
            KeycloakManager.processPushTopicNotification(topic);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        AppSingleton.storeFirebaseToken(token);

        if (SettingsActivity.disablePushNotifications()) {
            token = null;
        }

        // we try to register up to 5 times (here, registering is only storing something in the engine database)
        for (int i=0; i<5; i++) {
            try {
                Engine engine = AppSingleton.getEngine();
                ObvIdentity[] ownedIdentities = engine.getOwnedIdentities();
                for (ObvIdentity ownedIdentity : ownedIdentities) {
                    engine.registerToPushNotification(ownedIdentity.getBytesIdentity(), token, false, false);
                }
                break;
            } catch (Exception e) {
                Logger.e("An error occurred while updating the push notifications with a new firebase token");
                e.printStackTrace();
            }
        }
    }
}