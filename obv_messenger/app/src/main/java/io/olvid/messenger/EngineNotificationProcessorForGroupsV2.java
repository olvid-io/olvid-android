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

package io.olvid.messenger;

import java.util.HashMap;
import java.util.Objects;

import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.tasks.CreateOrUpdateGroupV2Task;
import io.olvid.messenger.databases.tasks.UpdateGroupV2PhotoFromEngineTask;

public class EngineNotificationProcessorForGroupsV2 implements EngineNotificationListener {
    private final Engine engine;
    private final AppDatabase db;
    private Long registrationNumber;

    EngineNotificationProcessorForGroupsV2(Engine engine) {
        this.engine = engine;
        this.db = AppDatabase.getInstance();

        registrationNumber = null;
        for (String notificationName: new String[] {
                EngineNotifications.GROUP_V2_CREATED_OR_UPDATED,
                EngineNotifications.GROUP_V2_PHOTO_CHANGED,
                EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED,
                EngineNotifications.GROUP_V2_DELETED,
                EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS,
        }) {
            engine.addNotificationListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, final HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.GROUP_V2_CREATED_OR_UPDATED : {
                ObvGroupV2 groupV2 = (ObvGroupV2) userInfo.get(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY);
                Boolean groupWasJustCreatedByMe = (Boolean) userInfo.get(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_NEW_GROUP_KEY);
                Boolean updatedByMe = (Boolean) userInfo.get(EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_BY_ME_KEY);
                if (groupV2 == null || groupWasJustCreatedByMe == null || updatedByMe == null) {
                    break;
                }

                new CreateOrUpdateGroupV2Task(groupV2, groupWasJustCreatedByMe, updatedByMe, false).run();
                break;
            }
            case EngineNotifications.GROUP_V2_PHOTO_CHANGED : {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupIdentifier = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_GROUP_IDENTIFIER_KEY);
                if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
                    break;
                }
                App.runThread(new UpdateGroupV2PhotoFromEngineTask(bytesOwnedIdentity, bytesGroupIdentifier));
                break;
            }
            case EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED : {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupIdentifier = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_GROUP_IDENTIFIER_KEY);
                Boolean updating = (Boolean) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_UPDATING_KEY);
                Boolean creating = (Boolean) userInfo.get(EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_CREATING_KEY);
                if (bytesOwnedIdentity == null || bytesGroupIdentifier == null || updating == null || creating == null) {
                    break;
                }

                db.group2Dao().updateUpdateInProgress(bytesOwnedIdentity, bytesGroupIdentifier, updating ? (creating ? Group2.UPDATE_CREATING : Group2.UPDATE_SYNCING) : Group2.UPDATE_NONE);
                break;
            }
            case EngineNotifications.GROUP_V2_DELETED : {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_DELETED_BYTES_OWNED_IDENTITY);
                byte[] bytesGroupIdentifier = (byte[]) userInfo.get(EngineNotifications.GROUP_V2_DELETED_BYTES_GROUP_IDENTIFIER_KEY);
                if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
                    break;
                }

                Group2 group2 = db.group2Dao().get(bytesOwnedIdentity, bytesGroupIdentifier);
                if (group2 != null) {
                    db.runInTransaction(()-> {
                        Discussion discussion = db.discussionDao().getByGroupIdentifier(bytesOwnedIdentity, bytesGroupIdentifier);
                        if (discussion != null) {
                            discussion.lockWithMessage(db);
                        }
                        db.group2Dao().delete(group2);
                    });
                }
                break;
            }
            case EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.KEYCLOAK_UPDATE_REQUIRED_BYTES_OWNED_IDENTITY_KEY);
                byte[] bytesGroupIdentifier = (byte[]) userInfo.get(EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_BYTES_GROUP_IDENTIFIER_KEY);
                String serializedSharedSettings = (String) userInfo.get(EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_SHARED_SETTINGS_KEY);
                Long latestModificationTimestamp = (Long) userInfo.get(EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_MODIFICATION_TIMESTAMP_KEY);
                if (bytesOwnedIdentity == null || bytesGroupIdentifier == null || latestModificationTimestamp == null) {
                    break;
                }

                try {
                    Message.JsonExpiration jsonExpiration;
                    if (serializedSharedSettings == null) {
                        jsonExpiration = null;
                    } else {
                        jsonExpiration = AppSingleton.getJsonObjectMapper().readValue(serializedSharedSettings, DiscussionCustomization.JsonSharedSettings.class).getJsonExpiration();
                    }

                    if (jsonExpiration != null && jsonExpiration.likeNull()) {
                        jsonExpiration = null;
                    }

                    Discussion discussion = AppDatabase.getInstance().discussionDao().getByGroupIdentifier(bytesOwnedIdentity, bytesGroupIdentifier);
                    if (discussion != null) {
                        DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(discussion.id);
                        if (discussionCustomization == null || !Objects.equals(discussionCustomization.getExpirationJson(), jsonExpiration)) {
                            // we need to update the shared settings
                            if (discussionCustomization == null) {
                                if (jsonExpiration == null) {
                                    // we don't have any customization, but there is no JsonExpiration --> do nothing
                                    break;
                                }
                                discussionCustomization = new DiscussionCustomization(discussion.id);
                                db.discussionCustomizationDao().insert(discussionCustomization);
                            }

                            // always use version 0 for keycloak shared settings
                            discussionCustomization.sharedSettingsVersion = 0;
                            if (jsonExpiration != null) {
                                discussionCustomization.settingReadOnce = jsonExpiration.getReadOnce() != null && jsonExpiration.getReadOnce();
                                discussionCustomization.settingVisibilityDuration = jsonExpiration.getVisibilityDuration();
                                discussionCustomization.settingExistenceDuration = jsonExpiration.getExistenceDuration();
                            } else {
                                discussionCustomization.settingReadOnce = false;
                                discussionCustomization.settingVisibilityDuration = null;
                                discussionCustomization.settingExistenceDuration = null;
                            }

                            Message message = Message.createDiscussionSettingsUpdateMessage(db, discussion.id, discussionCustomization.getSharedSettingsJson(), new byte[0], false, latestModificationTimestamp);
                            if (message != null) {
                                message.id = db.messageDao().insert(message);
                                db.discussionCustomizationDao().update(discussionCustomization);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
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
