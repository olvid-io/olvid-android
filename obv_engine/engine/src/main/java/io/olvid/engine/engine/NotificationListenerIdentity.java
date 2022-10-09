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

package io.olvid.engine.engine;

import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.engine.datatypes.EngineSession;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.notification.NotificationManager;

public class NotificationListenerIdentity implements NotificationListener {
    private final Engine engine;

    public NotificationListenerIdentity(Engine engine) {
        this.engine = engine;
    }

    void registerToNotifications(NotificationManager notificationManager) {
        for (String notificationName : new String[]{
                IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_LIST_UPDATED,
                IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED,
                IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY,
                IdentityNotifications.NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED,
                IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED,
                IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE,
                IdentityNotifications.NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS,
                IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET,
                IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED,
                IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED,
                IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED,
                IdentityNotifications.NOTIFICATION_CONTACT_REVOKED,
                IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED,
                IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS,
                IdentityNotifications.NOTIFICATION_CONTACT_CAPABILITIES_UPDATED,
                IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED,
                IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED,
        }) {
            notificationManager.addListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY: {
                try (EngineSession engineSession = engine.getSession()) {
                    Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_CONTACT_IDENTITY_KEY);
                    Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_OWNED_IDENTITY_KEY);
                    boolean keycloakManaged = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_KEYCLOAK_MANAGED_KEY);
                    boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_ACTIVE_KEY);
                    boolean oneToOne = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_ONE_TO_ONE_KEY);
                    int trustLevel = (int) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_IDENTITY_TRUST_LEVEL_KEY);
                    if (contactIdentity == null || ownedIdentity == null) {
                        break;
                    }

                    engine.protocolManager.startDeviceDiscoveryProtocol(ownedIdentity, contactIdentity);

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    JsonIdentityDetails contactDetails = engine.identityManager.getContactIdentityTrustedDetails(engineSession.session, ownedIdentity, contactIdentity);

                    engineInfo.put(EngineNotifications.NEW_CONTACT_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.NEW_CONTACT_CONTACT_IDENTITY_KEY, new ObvIdentity(contactIdentity, contactDetails, keycloakManaged, active));
                    engineInfo.put(EngineNotifications.NEW_CONTACT_ONE_TO_ONE_KEY, oneToOne);
                    engineInfo.put(EngineNotifications.NEW_CONTACT_TRUST_LEVEL_KEY, trustLevel);
                    engineInfo.put(EngineNotifications.NEW_CONTACT_HAS_UNTRUSTED_PUBLISHED_DETAILS_KEY, engine.identityManager.contactHasUntrustedPublishedDetails(engineSession.session, ownedIdentity, contactIdentity));
                    engine.postEngineNotification(EngineNotifications.NEW_CONTACT, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED: {
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED_CONTACT_IDENTITY_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED_OWNED_IDENTITY_KEY);
                TrustLevel trustLevel = (TrustLevel) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED_TRUST_LEVEL_KEY);
                if (contactIdentity == null || ownedIdentity == null || trustLevel == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_TRUST_LEVEL_INCREASED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_TRUST_LEVEL_INCREASED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_TRUST_LEVEL_INCREASED_TRUST_LEVEL_KEY, trustLevel.major);

                engine.postEngineNotification(EngineNotifications.CONTACT_TRUST_LEVEL_INCREASED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED: {
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED_CONTACT_IDENTITY_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_IDENTITY_DELETED_OWNED_IDENTITY_KEY);
                if (contactIdentity == null || ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_DELETED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_DELETED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                engine.postEngineNotification(EngineNotifications.CONTACT_DELETED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_CONTACT_IDENTITY_KEY);
                if (contactIdentity == null || ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_CONTACT_DEVICE_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                engine.postEngineNotification(EngineNotifications.NEW_CONTACT_DEVICE, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS_CONTACT_IDENTITY_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                engine.postEngineNotification(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_LIST_UPDATED: {
                engine.postEngineNotification(EngineNotifications.OWNED_IDENTITY_LIST_UPDATED, new HashMap<>());
                break;
            }
            case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED_OWNED_IDENTITY_KEY);
                JsonIdentityDetailsWithVersionAndPhoto identityDetails = (JsonIdentityDetailsWithVersionAndPhoto) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED_IDENTITY_DETAILS_KEY);
                if (ownedIdentity == null || identityDetails == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_IDENTITY_DETAILS_KEY, identityDetails.getIdentityDetails());
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_PHOTO_URL_KEY, identityDetails.getPhotoUrl());

                engine.postEngineNotification(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_CONTACT_IDENTITY_KEY);
                int version = (int) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_VERSION_KEY);
                boolean isTrusted = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PHOTO_SET_IS_TRUSTED_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_CONTACT_PHOTO_VERSION_KEY, version);
                engineInfo.put(EngineNotifications.NEW_CONTACT_PHOTO_IS_TRUSTED_KEY, isTrusted);

                engine.postEngineNotification(EngineNotifications.NEW_CONTACT_PHOTO, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_CONTACT_IDENTITY_KEY);
                JsonIdentityDetailsWithVersionAndPhoto identityDetails = (JsonIdentityDetailsWithVersionAndPhoto) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_IDENTITY_DETAILS_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_IDENTITY_DETAILS_KEY, identityDetails);

                engine.postEngineNotification(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_CONTACT_IDENTITY_KEY);
                boolean keycloakManaged = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_KEYCLOAK_MANAGED_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_KEYCLOAK_MANAGED_KEY, keycloakManaged);

                engine.postEngineNotification(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED_CONTACT_IDENTITY_KEY);
                boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ACTIVE_CHANGED_ACTIVE_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                if (active) {
                    try {
                        engine.protocolManager.startDeviceDiscoveryProtocol(ownedIdentity, contactIdentity);
                    } catch (Exception ignored) {}
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_ACTIVE_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_ACTIVE_CHANGED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_ACTIVE_CHANGED_ACTIVE_KEY, active);

                engine.postEngineNotification(EngineNotifications.CONTACT_ACTIVE_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_REVOKED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_REVOKED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_REVOKED_CONTACT_IDENTITY_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_REVOKED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_REVOKED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engine.postEngineNotification(EngineNotifications.CONTACT_REVOKED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED_OWNED_IDENTITY_KEY);
                boolean hasUnpublished = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED_HAS_UNPUBLISHED_KEY);
                if (ownedIdentity == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED_HAS_UNPUBLISHED_KEY, hasUnpublished);

                engine.postEngineNotification(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY);
                boolean active = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY);
                if (ownedIdentity == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_ACTIVE_KEY, active);

                engine.postEngineNotification(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_CAPABILITIES_UPDATED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_CAPABILITIES_UPDATED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_CAPABILITIES_UPDATED_CONTACT_IDENTITY_KEY);
                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }
                try {
                    List<ObvCapability> capabilities = engine.identityManager.getContactCapabilities(ownedIdentity, contactIdentity);

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    engineInfo.put(EngineNotifications.CONTACT_CAPABILITIES_UPDATED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.CONTACT_CAPABILITIES_UPDATED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                    engineInfo.put(EngineNotifications.CONTACT_CAPABILITIES_UPDATED_CAPABILITIES, capabilities);

                    engine.postEngineNotification(EngineNotifications.CONTACT_CAPABILITIES_UPDATED, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_OWN_CAPABILITIES_UPDATED_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }
                try {
                    List<ObvCapability> capabilities = engine.identityManager.getOwnCapabilities(ownedIdentity);

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    engineInfo.put(EngineNotifications.OWN_CAPABILITIES_UPDATED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                    engineInfo.put(EngineNotifications.OWN_CAPABILITIES_UPDATED_CAPABILITIES, capabilities);

                    engine.postEngineNotification(EngineNotifications.OWN_CAPABILITIES_UPDATED, engineInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            case IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_CONTACT_IDENTITY_KEY);
                boolean oneToOne = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_ONE_TO_ONE_KEY);

                if (ownedIdentity == null || contactIdentity == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.CONTACT_ONE_TO_ONE_CHANGED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_ONE_TO_ONE_CHANGED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.CONTACT_ONE_TO_ONE_CHANGED_ONE_TO_ONE_KEY, oneToOne);

                engine.postEngineNotification(EngineNotifications.CONTACT_ONE_TO_ONE_CHANGED, engineInfo);
                break;
            }
            default:
                Logger.w("Received notification " + notificationName + " but no handler is set.");
        }
    }
}
