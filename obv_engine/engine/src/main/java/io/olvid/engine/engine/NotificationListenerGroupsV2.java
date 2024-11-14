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

package io.olvid.engine.engine;

import java.util.HashMap;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.engine.datatypes.EngineSession;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.notification.NotificationManager;

public class NotificationListenerGroupsV2 implements NotificationListener {
    private final Engine engine;

    public NotificationListenerGroupsV2(Engine engine) {
        this.engine = engine;
    }

    void registerToNotifications(NotificationManager notificationManager) {
        for (String notificationName : new String[]{
                IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED,
                IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED,
                IdentityNotifications.NOTIFICATION_GROUP_V2_PHOTO_UPDATED,
                IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED,
                IdentityNotifications.NOTIFICATION_GROUP_V2_DELETED,
                IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS,
                IdentityNotifications.NOTIFICATION_NEW_KEYCLOAK_GROUP_V2_PUSH_TOPIC,
        }) {
            notificationManager.addListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED: {
                try (EngineSession engineSession = engine.getSession()) {
                    Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED_OWNED_IDENTITY_KEY);
                    GroupV2.Identifier groupIdentifier = (GroupV2.Identifier) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED_GROUP_IDENTIFIER_KEY);
                    Boolean createdByMe = (Boolean) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED_CREATED_BY_ME_KEY);
                    Boolean createdOnOtherDevice = (Boolean) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_CREATED_ON_OTHER_DEVICE_KEY);
                    if (ownedIdentity == null || groupIdentifier == null || createdByMe == null || createdOnOtherDevice == null) {
                        break;
                    }

                    ObvGroupV2 obvGroupV2 = engine.identityManager.getObvGroupV2(engineSession.session, ownedIdentity, groupIdentifier);
                    if (obvGroupV2 == null) {
                        break;
                    }

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    engineInfo.put(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY, obvGroupV2);
                    engineInfo.put(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_NEW_GROUP_KEY, createdByMe);
                    engineInfo.put(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_BY_ME_KEY, createdByMe);
                    engineInfo.put(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_CREATED_ON_OTHER_DEVICE, createdOnOtherDevice);
                    engine.postEngineNotification(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED, engineInfo);
                } catch (Exception e) {
                    Logger.x(e);
                }
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED: {
                try (EngineSession engineSession = engine.getSession()) {
                    Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED_OWNED_IDENTITY_KEY);
                    GroupV2.Identifier groupIdentifier = (GroupV2.Identifier) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED_GROUP_IDENTIFIER_KEY);
                    Boolean updatedByMe = (Boolean) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_UPDATED_BY_ME_KEY);
                    if (ownedIdentity == null || groupIdentifier == null || updatedByMe == null) {
                        break;
                    }

                    ObvGroupV2 obvGroupV2 = engine.identityManager.getObvGroupV2(engineSession.session, ownedIdentity, groupIdentifier);
                    if (obvGroupV2 == null) {
                        break;
                    }

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    engineInfo.put(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY, obvGroupV2);
                    engineInfo.put(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_NEW_GROUP_KEY, false);
                    engineInfo.put(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_BY_ME_KEY, updatedByMe);
                    engineInfo.put(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_CREATED_ON_OTHER_DEVICE, false);
                    engine.postEngineNotification(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED, engineInfo);
                } catch (Exception e) {
                    Logger.x(e);
                }
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_V2_PHOTO_UPDATED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_PHOTO_UPDATED_OWNED_IDENTITY_KEY);
                GroupV2.Identifier groupIdentifier = (GroupV2.Identifier) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_PHOTO_UPDATED_GROUP_IDENTIFIER_KEY);
                if (ownedIdentity == null || groupIdentifier == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_GROUP_IDENTIFIER_KEY, groupIdentifier.getBytes());
                engine.postEngineNotification(EngineNotifications.GROUP_V2_PHOTO_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED_OWNED_IDENTITY_KEY);
                GroupV2.Identifier groupIdentifier = (GroupV2.Identifier) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED_GROUP_IDENTIFIER_KEY);
                Boolean frozen = (Boolean) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED_FROZEN_KEY);
                Boolean newGroup = (Boolean) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_FROZEN_CHANGED_NEW_GROUP_KEY);
                if ((ownedIdentity == null) || (groupIdentifier == null) || (frozen == null) || (newGroup == null)) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_GROUP_IDENTIFIER_KEY, groupIdentifier.getBytes());
                engineInfo.put(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_UPDATING_KEY, frozen);
                engineInfo.put(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_CREATING_KEY, newGroup);
                engine.postEngineNotification(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_V2_DELETED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_DELETED_OWNED_IDENTITY_KEY);
                GroupV2.Identifier groupIdentifier = (GroupV2.Identifier) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_V2_DELETED_GROUP_IDENTIFIER_KEY);
                if ((ownedIdentity == null) || (groupIdentifier == null)) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_V2_DELETED_BYTES_OWNED_IDENTITY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_V2_DELETED_BYTES_GROUP_IDENTIFIER_KEY, groupIdentifier.getBytes());
                engine.postEngineNotification(EngineNotifications.GROUP_V2_DELETED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_OWNED_IDENTITY_KEY);
                GroupV2.Identifier groupIdentifier = (GroupV2.Identifier) userInfo.get(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_GROUP_IDENTIFIER_KEY);
                String serializedSharedSettings = (String) userInfo.get(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_SERIALIZED_SHARED_SETTINGS_KEY);
                Long latestModificationTimestamp = (Long) userInfo.get(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_MODIFICATION_TIMESTAMP_KEY);

                if (ownedIdentity == null || groupIdentifier == null || latestModificationTimestamp == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_BYTES_GROUP_IDENTIFIER_KEY, groupIdentifier.getBytes());
                engineInfo.put(EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_SHARED_SETTINGS_KEY, serializedSharedSettings);
                engineInfo.put(EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_MODIFICATION_TIMESTAMP_KEY, latestModificationTimestamp);

                engine.postEngineNotification(EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_NEW_KEYCLOAK_GROUP_V2_PUSH_TOPIC: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                engine.fetchManager.forceRegisterPushNotification(ownedIdentity,false);
                break;
            }
            default:
                Logger.w("Received notification " + notificationName + " but no handler is set.");
        }
    }
}
