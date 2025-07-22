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

package io.olvid.messenger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvContactDeviceCount;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.ContactCacheSingleton;
import io.olvid.messenger.databases.dao.MessageRecipientInfoDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings;
import io.olvid.messenger.databases.tasks.InsertContactRevokedMessageTask;
import io.olvid.messenger.databases.tasks.OwnedDevicesSynchronisationWithEngineTask;
import io.olvid.messenger.databases.tasks.UpdateContactActiveTask;
import io.olvid.messenger.databases.tasks.UpdateContactDisplayNameAndPhotoTask;
import io.olvid.messenger.databases.tasks.UpdateContactKeycloakManagedTask;
import io.olvid.messenger.databases.tasks.new_message.ProcessReadyToProcessOnHoldMessagesTask;

public class EngineNotificationProcessorForContacts implements EngineNotificationListener {
    private final Engine engine;
    private final AppDatabase db;
    private Long registrationNumber;

    EngineNotificationProcessorForContacts(Engine engine) {
        this.engine = engine;
        this.db = AppDatabase.getInstance();

        registrationNumber = null;
        for (String notificationName: new String[] {
                EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED,
                EngineNotifications.NEW_CONTACT,
                EngineNotifications.CONTACT_DELETED,
                EngineNotifications.CONTACT_DEVICES_UPDATED,
                EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED,
                EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED,
                EngineNotifications.CONTACT_ACTIVE_CHANGED,
                EngineNotifications.CONTACT_REVOKED,
                EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS,
                EngineNotifications.NEW_CONTACT_PHOTO,
                EngineNotifications.CONTACT_CAPABILITIES_UPDATED,
                EngineNotifications.CONTACT_ONE_TO_ONE_CHANGED,
                EngineNotifications.CONTACT_RECENTLY_ONLINE_CHANGED,
                EngineNotifications.CONTACT_TRUST_LEVEL_INCREASED,
        }) {
            engine.addNotificationListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, final HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CHANNEL_CONFIRMED_OR_DELETED_CONTACT_IDENTITY_KEY);
                if (bytesOwnedIdentity != null && Arrays.equals(bytesOwnedIdentity, bytesContactIdentity)) {
                    new OwnedDevicesSynchronisationWithEngineTask(bytesOwnedIdentity).run();
                } else if (bytesOwnedIdentity != null && bytesContactIdentity != null) {
                    Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                    if (contact != null) {
                        try {
                            ObvContactDeviceCount contactDeviceCount = engine.getContactDeviceCounts(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                            contact.deviceCount = contactDeviceCount.deviceCount;
                            contact.establishedChannelCount = contactDeviceCount.establishedChannelCount;
                            contact.preKeyCount = contactDeviceCount.preKeyCount;
                            db.contactDao().updateCounts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.deviceCount, contact.establishedChannelCount, contact.preKeyCount);

                            if (contact.hasChannelOrPreKey()) {
                                // Search for MessageRecipientInfo indicating a message was not sent to this user
                                List<MessageRecipientInfoDao.MessageRecipientInfoAndMessage> messageRecipientInfoAndMessages = db.messageRecipientInfoDao().getAllUnsentForContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                App.runThread(() -> db.runInTransaction(() -> {
                                    for (MessageRecipientInfoDao.MessageRecipientInfoAndMessage messageRecipientInfoAndMessage : messageRecipientInfoAndMessages) {
                                        messageRecipientInfoAndMessage.message.repost(messageRecipientInfoAndMessage.messageRecipientInfo, null);
                                    }
                                }));

                                // resend all discussion shared ephemeral message settings
                                List<Long> discussionIds = new ArrayList<>();
                                // direct discussion
                                Discussion directDiscussion = db.discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
                                if (directDiscussion != null) {
                                    discussionIds.add(directDiscussion.id);
                                }
                                // owned group discussions
                                discussionIds.addAll(db.contactGroupJoinDao().getAllOwnedGroupDiscussionIdsWithSpecificContact(bytesOwnedIdentity, bytesContactIdentity));

                                // managed group V2 discussions
                                discussionIds.addAll(db.group2MemberDao().getGroupV2DiscussionIdsWithSettingsPermissionWithContact(bytesOwnedIdentity, bytesContactIdentity));

                                for (Long discussionId : discussionIds) {
                                    if (discussionId == null) {
                                        continue;
                                    }
                                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(discussionId);
                                    if (discussionCustomization == null) {
                                        continue;
                                    }
                                    JsonSharedSettings jsonSharedSettings = discussionCustomization.getSharedSettingsJson();
                                    if (jsonSharedSettings != null) {
                                        // send the json to contact
                                        Message message = Message.createDiscussionSettingsUpdateMessage(db, discussionId, jsonSharedSettings, bytesOwnedIdentity, true, null);
                                        if (message != null) {
                                            message.postSettingsMessage(true, null);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            }
            case EngineNotifications.NEW_CONTACT: {
                final byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_OWNED_IDENTITY_KEY);
                final ObvIdentity contactIdentity = (ObvIdentity) userInfo.get(EngineNotifications.NEW_CONTACT_CONTACT_IDENTITY_KEY);
                final Boolean oneToOne = (Boolean) userInfo.get(EngineNotifications.NEW_CONTACT_ONE_TO_ONE_KEY);
                final Integer trustLevel = (Integer) userInfo.get(EngineNotifications.NEW_CONTACT_TRUST_LEVEL_KEY);
                final Boolean hasUntrustedPublishedDetails = (Boolean) userInfo.get(EngineNotifications.NEW_CONTACT_HAS_UNTRUSTED_PUBLISHED_DETAILS_KEY);
                if (bytesOwnedIdentity == null || contactIdentity == null || hasUntrustedPublishedDetails == null || oneToOne == null || trustLevel == null) {
                    break;
                }
                Contact contact = db.contactDao().get(bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                if (contact == null) {
                    try {
                        db.runInTransaction(() -> {
                            Contact createdContact;
                            try {
                                createdContact = new Contact(contactIdentity.getBytesIdentity(), bytesOwnedIdentity, contactIdentity.getIdentityDetails(), hasUntrustedPublishedDetails, null, contactIdentity.isKeycloakManaged(), contactIdentity.isActive(), oneToOne, trustLevel, true);
                                if (Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                                    ContactCacheSingleton.INSTANCE.updateCachedCustomDisplayName(createdContact);
                                    ContactCacheSingleton.INSTANCE.updateContactCachedInfo(createdContact);
                                }
                                db.contactDao().insert(createdContact);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }

                            if (oneToOne) {
                                Discussion discussion = Discussion.createOrReuseOneToOneDiscussion(db, createdContact);

                                if (discussion == null) {
                                    throw new RuntimeException("Unable to create discussion!");
                                }

                                // mark all on hold messages (if any) as ready to process
                                int count = db.onHoldInboxMessageDao().markAsReadyToProcessForContactDiscussion(
                                        createdContact.bytesOwnedIdentity,
                                        createdContact.bytesContactIdentity
                                );
                                if (count > 0) {
                                    Logger.i("⏸️ Marked " + count + " on hold messages as ready to process");
                                }
                            }
                        });

                        if (oneToOne) {
                            // if the transaction finished without an exception, trigger the process of on hold messages that are ready to process
                            AppSingleton.getEngine().runTaskOnEngineNotificationQueue(new ProcessReadyToProcessOnHoldMessagesTask(engine, db, bytesOwnedIdentity));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    contact = db.contactDao().get(bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                    if (contact != null) {
                        try {
                            ObvContactDeviceCount contactDeviceCount = engine.getContactDeviceCounts(bytesOwnedIdentity, contactIdentity.getBytesIdentity());
                            contact.deviceCount = contactDeviceCount.deviceCount;
                            contact.establishedChannelCount = contactDeviceCount.establishedChannelCount;
                            contact.preKeyCount = contactDeviceCount.preKeyCount;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        db.contactDao().updateCounts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.deviceCount, contact.establishedChannelCount, contact.preKeyCount);
                    }
                }
                break;
            }
            case EngineNotifications.CONTACT_DELETED: {
                final byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_DELETED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_DELETED_BYTES_CONTACT_IDENTITY_KEY);
                if (bytesOwnedIdentity != null && bytesContactIdentity != null) {
                    final Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                    if (contact != null) {
                        contact.delete();
                        if (Arrays.equals(contact.bytesContactIdentity, AppSingleton.getBytesCurrentIdentity())) {
                            ContactCacheSingleton.INSTANCE.reloadCachedDisplayNamesAndHues();
                        }
                    }
                }
                break;
            }
            case EngineNotifications.CONTACT_DEVICES_UPDATED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_DEVICES_UPDATED_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_DEVICES_UPDATED_CONTACT_IDENTITY_KEY);
                if (bytesOwnedIdentity != null && bytesContactIdentity != null) {
                    Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                    if (contact != null) {
                        try {
                            ObvContactDeviceCount contactDeviceCount = engine.getContactDeviceCounts(bytesOwnedIdentity, bytesContactIdentity);
                            contact.deviceCount = contactDeviceCount.deviceCount;
                            contact.establishedChannelCount = contactDeviceCount.establishedChannelCount;
                            contact.preKeyCount = contactDeviceCount.preKeyCount;
                            db.contactDao().updateCounts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.deviceCount, contact.establishedChannelCount, contact.preKeyCount);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            }
            case EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_CONTACT_IDENTITY_KEY);
                JsonIdentityDetailsWithVersionAndPhoto identityDetails = (JsonIdentityDetailsWithVersionAndPhoto) userInfo.get(EngineNotifications.CONTACT_PUBLISHED_DETAILS_TRUSTED_IDENTITY_DETAILS_KEY);

                if (identityDetails != null) {
                    App.runThread(new UpdateContactDisplayNameAndPhotoTask(bytesContactIdentity, bytesOwnedIdentity, identityDetails));
                }
                break;
            }
            case EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_CONTACT_IDENTITY_KEY);
                Boolean keycloakManaged = (Boolean) userInfo.get(EngineNotifications.CONTACT_KEYCLOAK_MANAGED_CHANGED_KEYCLOAK_MANAGED_KEY);
                if (bytesOwnedIdentity == null || bytesContactIdentity == null || keycloakManaged == null) {
                    break;
                }

                App.runThread(new UpdateContactKeycloakManagedTask(bytesOwnedIdentity, bytesContactIdentity, keycloakManaged));
                break;
            }
            case EngineNotifications.CONTACT_ACTIVE_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_ACTIVE_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_ACTIVE_CHANGED_BYTES_CONTACT_IDENTITY_KEY);
                Boolean active = (Boolean) userInfo.get(EngineNotifications.CONTACT_ACTIVE_CHANGED_ACTIVE_KEY);
                if (bytesOwnedIdentity == null || bytesContactIdentity == null || active == null) {
                    break;
                }

                App.runThread(new UpdateContactActiveTask(bytesOwnedIdentity, bytesContactIdentity, active));
                break;
            }
            case EngineNotifications.CONTACT_REVOKED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_REVOKED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_REVOKED_BYTES_CONTACT_IDENTITY_KEY);
                if (bytesOwnedIdentity != null && bytesContactIdentity != null) {
                    App.runThread(new InsertContactRevokedMessageTask(bytesOwnedIdentity, bytesContactIdentity));
                }
                break;
            }
            case EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PUBLISHED_DETAILS_BYTES_CONTACT_IDENTITY_KEY);
                if (bytesOwnedIdentity != null && bytesContactIdentity != null) {
                    Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                    if (contact != null) {
                        try {
                            contact.newPublishedDetails = Contact.PUBLISHED_DETAILS_NEW_UNSEEN;
                            db.contactDao().updatePublishedDetailsStatus(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.newPublishedDetails);
                            Discussion discussion = db.discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
                            if (discussion != null) {
                                Message newDetailsMessage = Message.createNewPublishedDetailsMessage(db, discussion.id, bytesContactIdentity);
                                newDetailsMessage.id = db.messageDao().insert(newDetailsMessage);
                                UnreadCountsSingleton.INSTANCE.newUnreadMessage(discussion.id, newDetailsMessage.id, false, newDetailsMessage.timestamp);
                                if (discussion.updateLastMessageTimestamp(newDetailsMessage.timestamp)) {
                                    db.discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            }
            case EngineNotifications.NEW_CONTACT_PHOTO: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_BYTES_CONTACT_IDENTITY_KEY);
                Integer version = (Integer) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_VERSION_KEY);
                Boolean isTrusted = (Boolean) userInfo.get(EngineNotifications.NEW_CONTACT_PHOTO_IS_TRUSTED_KEY);
                if (bytesOwnedIdentity == null || bytesContactIdentity == null || version == null || isTrusted == null) {
                    break;
                }

                if (isTrusted) {
                    try {
                        JsonIdentityDetailsWithVersionAndPhoto[] jsons = engine.getContactPublishedAndTrustedDetails(bytesOwnedIdentity, bytesContactIdentity);
                        if (jsons[jsons.length-1].getVersion() == version) {
                            Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                            if (contact != null) {
                                App.runThread(new UpdateContactDisplayNameAndPhotoTask(bytesContactIdentity, bytesOwnedIdentity, jsons[jsons.length-1]));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case EngineNotifications.CONTACT_CAPABILITIES_UPDATED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_CAPABILITIES_UPDATED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_CAPABILITIES_UPDATED_BYTES_CONTACT_IDENTITY_KEY);
                //noinspection unchecked
                List<ObvCapability> capabilities = (List<ObvCapability>) userInfo.get(EngineNotifications.CONTACT_CAPABILITIES_UPDATED_CAPABILITIES);

                if (bytesOwnedIdentity == null || bytesContactIdentity == null || capabilities == null) {
                    break;
                }

                Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                if (contact != null) {
                    for (ObvCapability obvCapability : ObvCapability.values()) {
                        boolean capable = capabilities.contains(obvCapability);

                        switch (obvCapability) {
                            case WEBRTC_CONTINUOUS_ICE:
                                if (capable != contact.capabilityWebrtcContinuousIce) {
                                    db.contactDao().updateCapabilityWebrtcContinuousIce(contact.bytesOwnedIdentity, contact.bytesContactIdentity, capable);
                                }
                                break;
                            case ONE_TO_ONE_CONTACTS:
                                if (capable != contact.capabilityOneToOneContacts) {
                                    db.contactDao().updateCapabilityOneToOneContacts(contact.bytesOwnedIdentity, contact.bytesContactIdentity, capable);
                                }
                                break;
                            case GROUPS_V2:
                                if (capable != contact.capabilityGroupsV2) {
                                    db.contactDao().updateCapabilityGroupsV2(contact.bytesOwnedIdentity, contact.bytesContactIdentity, capable);
                                }
                                break;
                        }
                    }
                }
                break;
            }
            case EngineNotifications.CONTACT_ONE_TO_ONE_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_ONE_TO_ONE_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_ONE_TO_ONE_CHANGED_BYTES_CONTACT_IDENTITY_KEY);
                Boolean oneToOne = (Boolean) userInfo.get(EngineNotifications.CONTACT_ONE_TO_ONE_CHANGED_ONE_TO_ONE_KEY);

                if (bytesOwnedIdentity == null || bytesContactIdentity == null || oneToOne == null) {
                    break;
                }

                Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                if (contact != null && contact.oneToOne != oneToOne) {
                    contact.oneToOne = oneToOne;
                    db.contactDao().updateOneToOne(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.oneToOne);
                    if (Arrays.equals(contact.bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                        ContactCacheSingleton.INSTANCE.updateContactCachedInfo(contact);
                    }
                    if (oneToOne) {
                        try {
                            db.runInTransaction(() -> {
                                Discussion discussion = Discussion.createOrReuseOneToOneDiscussion(db, contact);
                                if (discussion == null) {
                                    throw new RuntimeException("Unable to create discussion");
                                }

                                // mark all on hold messages (if any) as ready to process
                                int count = db.onHoldInboxMessageDao().markAsReadyToProcessForContactDiscussion(
                                        contact.bytesOwnedIdentity,
                                        contact.bytesContactIdentity
                                );
                                if (count > 0) {
                                    Logger.i("⏸️ Marked " + count + " on hold messages as ready to process");
                                }
                            });

                            // if the transaction finished without an exception, trigger the process of on hold messages that are ready to process
                            AppSingleton.getEngine().runTaskOnEngineNotificationQueue(new ProcessReadyToProcessOnHoldMessagesTask(engine, db, contact.bytesOwnedIdentity));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        db.runInTransaction(() -> {
                            Discussion discussion = db.discussionDao().getByContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                            if (discussion != null) {
                                discussion.lockWithMessage(db);
                            }
                        });
                    }
                }
                break;
            }
            case EngineNotifications.CONTACT_RECENTLY_ONLINE_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_RECENTLY_ONLINE_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_RECENTLY_ONLINE_CHANGED_BYTES_CONTACT_IDENTITY_KEY);
                Boolean recentlyOnline = (Boolean) userInfo.get(EngineNotifications.CONTACT_RECENTLY_ONLINE_CHANGED_RECENTLY_ONLINE_KEY);

                if (bytesOwnedIdentity == null || bytesContactIdentity == null || recentlyOnline == null) {
                    break;
                }

                Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                if (contact != null && contact.recentlyOnline != recentlyOnline) {
                    contact.recentlyOnline = recentlyOnline;
                    db.contactDao().updateRecentlyOnline(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.recentlyOnline);
                    ContactCacheSingleton.INSTANCE.updateContactCachedInfo(contact);
                }
                break;
            }
            case EngineNotifications.CONTACT_TRUST_LEVEL_INCREASED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_TRUST_LEVEL_INCREASED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.CONTACT_TRUST_LEVEL_INCREASED_BYTES_CONTACT_IDENTITY_KEY);
                Integer trustLevel = (Integer) userInfo.get(EngineNotifications.CONTACT_TRUST_LEVEL_INCREASED_TRUST_LEVEL_KEY);

                if (bytesOwnedIdentity == null || bytesContactIdentity == null || trustLevel == null) {
                    break;
                }

                Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
                if (contact != null && contact.trustLevel != trustLevel) {
                    contact.trustLevel = trustLevel;
                    db.contactDao().updateTrustLevel(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.trustLevel);
                    if (Arrays.equals(contact.bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                        ContactCacheSingleton.INSTANCE.updateContactCachedInfo(contact);
                    }

                    if (contact.oneToOne) {
                        db.discussionDao().updateTrustLevel(contact.bytesOwnedIdentity, contact.bytesContactIdentity, contact.trustLevel);
                    }
                }
            }
        }
    }



    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return registrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return registrationNumber != null;
    }
}
