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

package io.olvid.messenger.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.ObvPushNotificationType;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.troubleshooting.PushDiagnostics;

public class ObvFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // on a cold start, this is what creates the engine (and sets the Logger level)
        Engine engine = AppSingleton.getEngine();

        boolean highPriority = remoteMessage.getPriority() == RemoteMessage.PRIORITY_HIGH;
        boolean sentAsHighPriority = remoteMessage.getOriginalPriority() == RemoteMessage.PRIORITY_HIGH;
        boolean deprioritized = sentAsHighPriority && !highPriority;
        Logger.d("FIREBASE Message received. Priority: " + (highPriority ? "HIGH" : "NORMAL"));
        if (deprioritized) {
            Logger.e("message was deprioritized!");
        }
        Map<String, String> data = remoteMessage.getData();
        String identityString = data.get("identity");
        String topic = data.get("topic");
        String keycloak = data.get("keycloak");
        String ownedDevices = data.get("ownedDevices");

        // get the ownedIdentity from the engine: since engine version 1.3.1 this call can properly wait for the engine to initialize the masking cache
        byte[] maskedOwnedIdentity = (identityString == null) ? null : engine.getOwnedIdentityFromMaskingUid(identityString);
        PushDiagnostics.recordPush(remoteMessage.getSentTime(), sentAsHighPriority, deprioritized, identityString == null || maskedOwnedIdentity != null);
        if (identityString != null) {
            Logger.d("For identity mask: " + identityString);
            if (maskedOwnedIdentity == null) {
                Logger.w("FIREBASE unknown identity mask, targeting all active identities");
            }
            // if we were not able to get an masked owned identity from the identityString, download for all active identities
            List<byte[]> bytesOwnedIdentities = (maskedOwnedIdentity != null) ? Collections.singletonList(maskedOwnedIdentity) : getActiveOwnedIdentities(engine);
            if (keycloak != null) {
                Logger.d("Is keycloak notification");
                for (byte[] bytesOwnedIdentity : bytesOwnedIdentities) {
                    KeycloakManager.forceSyncManagedIdentity(bytesOwnedIdentity);
                }
            } else if (ownedDevices != null) {
                Logger.d("Is ownedDevices notification");
                for (byte[] bytesOwnedIdentity : bytesOwnedIdentities) {
                    engine.refreshOwnedDeviceList(bytesOwnedIdentity);
                }
            } else {
                if (highPriority) {
                    UnifiedForegroundService.PushProcessingSubService.startForPush(bytesOwnedIdentities);
                }
                if (maskedOwnedIdentity != null) {
                    engine.processAndroidPushNotification(identityString);
                } else {
                    for (byte[] bytesOwnedIdentity : bytesOwnedIdentities) {
                        engine.downloadMessages(bytesOwnedIdentity);
                    }
                }
            }
        } else if (topic != null) {
            Logger.d("For push topic: " + topic);
            KeycloakManager.processPushTopicNotification(topic);
        }
    }

    @NonNull
    private static List<byte[]> getActiveOwnedIdentities(@NonNull Engine engine) {
        List<byte[]> bytesOwnedIdentities = new ArrayList<>();
        try {
            for (ObvIdentity ownedIdentity : engine.getOwnedIdentities()) {
                if (ownedIdentity.isActive()) {
                    bytesOwnedIdentities.add(ownedIdentity.getBytesIdentity());
                }
            }
        } catch (Exception e) {
            Logger.x(e);
        }
        return bytesOwnedIdentities;
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
            for (byte[] bytesOwnedIdentity : getActiveOwnedIdentities(engine)) {
                engine.registerToPushNotification(bytesOwnedIdentity, ObvPushNotificationType.createAndroid(token), false, null);
            }
        } catch (Exception e) {
            Logger.e("An error occurred while updating the push notifications with a new firebase token");
            e.printStackTrace();
        }
    }
}