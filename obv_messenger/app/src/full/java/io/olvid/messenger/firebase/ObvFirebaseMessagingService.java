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

package io.olvid.messenger.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.ObvPushNotificationType;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.settings.SettingsActivity;

public class ObvFirebaseMessagingService extends FirebaseMessagingService {
    private static Long lastPushNotificationTimestamp = null;
    private static int deprioritizedMessageCount = 0;
    private static int highPriorityMessageCount = 0;

    public static Long getLastPushNotificationTimestamp() {
        return lastPushNotificationTimestamp;
    }

    public static int getDeprioritizedMessageCount() {
        return deprioritizedMessageCount;
    }

    public static int getHighPriorityMessageCount() {
        return highPriorityMessageCount;
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        lastPushNotificationTimestamp = System.currentTimeMillis();
        Logger.d("FIREBASE Message received. Priority: " + ((remoteMessage.getPriority() == RemoteMessage.PRIORITY_HIGH) ? "HIGH" : "NORMAL"));
        if (remoteMessage.getOriginalPriority() == RemoteMessage.PRIORITY_HIGH) {
            highPriorityMessageCount++;
            if (remoteMessage.getPriority() != remoteMessage.getOriginalPriority()) {
                deprioritizedMessageCount++;
                Logger.e("message was deprioritized!");
            }
        }
        Map<String, String> data = remoteMessage.getData();
        String identityString = data.get("identity");
        String topic = data.get("topic");
        String keycloak = data.get("keycloak");
        String ownedDevices = data.get("ownedDevices");
        if (identityString != null) {
            Logger.d("For identity mask: " + identityString);
            if (keycloak != null) {
                Logger.d("Is keycloak notification");
                KeycloakManager.forceSyncManagedIdentity(AppSingleton.getEngine().getOwnedIdentityFromMaskingUid(identityString));
            } else if (ownedDevices != null) {
                Logger.d("Is ownedDevices notification");
                AppSingleton.getEngine().refreshOwnedDeviceList(AppSingleton.getEngine().getOwnedIdentityFromMaskingUid(identityString));
            } else {
                AppSingleton.getEngine().processAndroidPushNotification(identityString);
            }
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

        try {
            Engine engine = AppSingleton.getEngine();
            for (ObvIdentity ownedIdentity : engine.getOwnedIdentities()) {
                if (ownedIdentity.isActive()) {
                    engine.registerToPushNotification(ownedIdentity.getBytesIdentity(), ObvPushNotificationType.createAndroid(token), false, null);
                }
            }
        } catch (Exception e) {
            Logger.e("An error occurred while updating the push notifications with a new firebase token");
            e.printStackTrace();
        }
    }
}