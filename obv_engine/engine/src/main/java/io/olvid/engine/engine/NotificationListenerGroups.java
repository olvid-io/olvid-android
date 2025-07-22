/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.containers.GroupWithDetails;
import io.olvid.engine.datatypes.notifications.IdentityNotifications;
import io.olvid.engine.engine.datatypes.EngineSession;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.notification.NotificationManager;

public class NotificationListenerGroups implements NotificationListener {
    private final Engine engine;

    public NotificationListenerGroups(Engine engine) {
        this.engine = engine;
    }

    void registerToNotifications(NotificationManager notificationManager) {
        for (String notificationName : new String[]{
                IdentityNotifications.NOTIFICATION_GROUP_CREATED,
                IdentityNotifications.NOTIFICATION_GROUP_DELETED,
                IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED,
                IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED,
                IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS,
                IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED,
                IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED,
                IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET,
                IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED,
                IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED,
                IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED,
        }) {
            notificationManager.addListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, Map<String, Object> userInfo) {
        switch (notificationName) {
            case IdentityNotifications.NOTIFICATION_GROUP_CREATED:
                try (EngineSession engineSession = engine.getSession()) {
                    byte[] groupOwnerAndUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_CREATED_GROUP_OWNER_AND_UID_KEY);
                    Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_CREATED_OWNED_IDENTITY_KEY);
                    Boolean createdOnOtherDevice = (Boolean) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_CREATED_ON_OTHER_DEVICE_KEY);
                    if (groupOwnerAndUid == null || ownedIdentity == null || createdOnOtherDevice == null) {
                        break;
                    }

                    HashMap<String, Object> engineInfo = new HashMap<>();
                    GroupWithDetails group = engine.identityManager.getGroupWithDetails(engineSession.session, ownedIdentity, groupOwnerAndUid);
                    if (group == null) {
                        break;
                    }

                    byte[][] bytesContactIdentities = new byte[group.getGroupMembers().length][];
                    for (int j = 0; j < bytesContactIdentities.length; j++) {
                        bytesContactIdentities[j] = group.getGroupMembers()[j].getBytes();
                    }
                    ObvIdentity[] pendingMembers = new ObvIdentity[group.getPendingGroupMembers().length];
                    for (int j = 0; j < pendingMembers.length; j++) {
                        try {
                            JsonIdentityDetails identityDetails = engine.identityManager.getJsonObjectMapper().readValue(group.getPendingGroupMembers()[j].serializedDetails, JsonIdentityDetails.class);
                            pendingMembers[j] = new ObvIdentity(group.getPendingGroupMembers()[j].identity, identityDetails, false, true);
                        } catch (IOException e) {
                            pendingMembers[j] = new ObvIdentity(group.getPendingGroupMembers()[j].identity, null, false, true);
                        }
                    }
                    byte[][] bytesDeclinesPendingMembers = new byte[group.getDeclinedPendingMembers().length][];
                    for (int j = 0; j < bytesDeclinesPendingMembers.length; j++) {
                        bytesDeclinesPendingMembers[j] = group.getDeclinedPendingMembers()[j].getBytes();
                    }
                    ObvGroup obvGroup;
                    if (group.getGroupOwner() == null) {
                        obvGroup = new ObvGroup(
                                group.getGroupOwnerAndUid(),
                                group.getPublishedGroupDetails(),
                                ownedIdentity.getBytes(),
                                bytesContactIdentities,
                                pendingMembers,
                                bytesDeclinesPendingMembers,
                                null
                        );
                    } else {
                        obvGroup = new ObvGroup(
                                group.getGroupOwnerAndUid(),
                                group.getLatestOrTrustedGroupDetails(),
                                ownedIdentity.getBytes(),
                                bytesContactIdentities,
                                pendingMembers,
                                bytesDeclinesPendingMembers,
                                group.getGroupOwner().getBytes()
                        );
                    }

                    String photoUrl = engine.identityManager.getGroupPhotoUrl(engineSession.session, ownedIdentity, groupOwnerAndUid);

                    engineInfo.put(EngineNotifications.GROUP_CREATED_GROUP_KEY, obvGroup);
                    engineInfo.put(EngineNotifications.GROUP_CREATED_HAS_MULTIPLE_DETAILS_KEY, group.hasMultipleDetails());
                    engineInfo.put(EngineNotifications.GROUP_CREATED_PHOTO_URL_KEY, photoUrl);
                    engineInfo.put(EngineNotifications.GROUP_CREATED_ON_OTHER_DEVICE_KEY, createdOnOtherDevice);
                    engine.postEngineNotification(EngineNotifications.GROUP_CREATED, engineInfo);
                } catch (Exception e) {
                    Logger.x(e);
                }
                break;
            case IdentityNotifications.NOTIFICATION_GROUP_DELETED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_DELETED_GROUP_OWNER_AND_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_DELETED_OWNED_IDENTITY_KEY);
                if (groupUid == null || ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_DELETED_BYTES_GROUP_OWNER_AND_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.GROUP_DELETED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());

                engine.postEngineNotification(EngineNotifications.GROUP_DELETED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_OWNER_AND_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_OWNED_IDENTITY_KEY);
                JsonGroupDetailsWithVersionAndPhoto groupDetailsWithVersionAndPhoto = (JsonGroupDetailsWithVersionAndPhoto) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_DETAILS_KEY);
                if (groupUid == null || ownedIdentity == null || groupDetailsWithVersionAndPhoto == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_DETAILS_KEY, groupDetailsWithVersionAndPhoto);

                engine.postEngineNotification(EngineNotifications.GROUP_PUBLISHED_DETAILS_TRUSTED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED: {
                byte[] groupOwnerAndUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED_GROUP_OWNER_AND_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY);
                if (groupOwnerAndUid == null || ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_GROUP_UID_KEY, groupOwnerAndUid);
                engineInfo.put(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_MEMBER_ADDED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                engine.postEngineNotification(EngineNotifications.GROUP_MEMBER_ADDED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED_GROUP_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY);
                if (groupUid == null || ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_MEMBER_REMOVED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());

                engine.postEngineNotification(EngineNotifications.GROUP_MEMBER_REMOVED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_OWNER_AND_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_OWNED_IDENTITY_KEY);
                JsonGroupDetailsWithVersionAndPhoto groupDetails = (JsonGroupDetailsWithVersionAndPhoto) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_DETAILS_KEY);
                if (groupUid == null || ownedIdentity == null || groupDetails == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_DETAILS_KEY, groupDetails);
                engine.postEngineNotification(EngineNotifications.GROUP_PUBLISHED_DETAILS_UPDATED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_GROUP_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY);
                String contactSerializedDetails = (String) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_CONTACT_SERIALIZED_DETAILS_KEY);
                if (groupUid == null || ownedIdentity == null || contactIdentity == null || contactSerializedDetails == null) {
                    break;
                }

                JsonIdentityDetails identityDetails;
                try {
                    identityDetails = engine.jsonObjectMapper.readValue(contactSerializedDetails, JsonIdentityDetails.class);
                } catch (Exception e) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_ADDED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_ADDED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY, new ObvIdentity(contactIdentity, identityDetails, false, true));

                engine.postEngineNotification(EngineNotifications.PENDING_GROUP_MEMBER_ADDED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_GROUP_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY);
                String contactSerializedDetails = (String) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_CONTACT_SERIALIZED_DETAILS_KEY);
                if (groupUid == null || ownedIdentity == null || contactIdentity == null || contactSerializedDetails == null) {
                    break;
                }

                JsonIdentityDetails identityDetails;
                try {
                    identityDetails = engine.jsonObjectMapper.readValue(contactSerializedDetails, JsonIdentityDetails.class);
                } catch (Exception e) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY, new ObvIdentity(contactIdentity, identityDetails, false, true));

                engine.postEngineNotification(EngineNotifications.PENDING_GROUP_MEMBER_REMOVED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED: {
                byte[] groupUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_GROUP_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_OWNED_IDENTITY_KEY);
                Identity contactIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_CONTACT_IDENTITY_KEY);
                boolean declined = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_DECLINED_KEY);
                if (groupUid == null || ownedIdentity == null || contactIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_GROUP_UID_KEY, groupUid);
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_CONTACT_IDENTITY_KEY, contactIdentity.getBytes());
                engineInfo.put(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED_DECLINED_KEY, declined);

                engine.postEngineNotification(EngineNotifications.PENDING_GROUP_MEMBER_DECLINE_TOGGLED, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET: {
                byte[] groupOwnerAndUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_GROUP_OWNER_AND_UID_KEY);
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_OWNED_IDENTITY_KEY);
                int version = (int) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_VERSION_KEY);
                boolean isTrusted = (boolean) userInfo.get(IdentityNotifications.NOTIFICATION_GROUP_PHOTO_SET_IS_TRUSTED_KEY);
                if (ownedIdentity == null || groupOwnerAndUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_GROUP_PHOTO_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_GROUP_PHOTO_BYTES_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);
                engineInfo.put(EngineNotifications.NEW_GROUP_PHOTO_VERSION_KEY, version);
                engineInfo.put(EngineNotifications.NEW_GROUP_PHOTO_IS_TRUSTED_KEY, isTrusted);

                engine.postEngineNotification(EngineNotifications.NEW_GROUP_PHOTO, engineInfo);
                break;
            }
            case IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS: {
                Identity ownedIdentity = (Identity) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS_OWNED_IDENTITY_KEY);
                byte[] groupOwnerAndUid = (byte[]) userInfo.get(IdentityNotifications.NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS_GROUP_OWNER_AND_UID_KEY);
                if (ownedIdentity == null || groupOwnerAndUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS_BYTES_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);

                engine.postEngineNotification(EngineNotifications.NEW_GROUP_PUBLISHED_DETAILS, engineInfo);
                break;
            }
            default:
                Logger.w("Received notification " + notificationName + " but no handler is set.");
        }
    }
}
