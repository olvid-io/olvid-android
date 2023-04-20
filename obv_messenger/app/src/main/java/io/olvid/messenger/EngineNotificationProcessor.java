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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.backup.BackupAppDataForEngineTask;
import io.olvid.messenger.main.invitations.InvitationListViewModelKt;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.services.BackupCloudProviderService;
import io.olvid.messenger.settings.SettingsActivity;

public class EngineNotificationProcessor implements EngineNotificationListener {
    private final Engine engine;
    private final AppDatabase db;
    private Long registrationNumber;

    EngineNotificationProcessor(Engine engine) {
        this.engine = engine;
        this.db = AppDatabase.getInstance();

        registrationNumber = null;
        for (String notificationName : new String[]{
                EngineNotifications.BACKUP_FINISHED,
                EngineNotifications.APP_BACKUP_REQUESTED,
                EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS,
                EngineNotifications.WEBSOCKET_CONNECTION_STATE_CHANGED,
                EngineNotifications.PUSH_TOPIC_NOTIFIED,
                EngineNotifications.KEYCLOAK_UPDATE_REQUIRED,

                EngineNotifications.UI_DIALOG,
                EngineNotifications.UI_DIALOG_DELETED,

                EngineNotifications.API_KEY_ACCEPTED,
//                EngineNotifications.API_KEY_REJECTED,
                EngineNotifications.OWNED_IDENTITY_LIST_UPDATED,
                EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED,
                EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED,
                EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED,
                EngineNotifications.OWN_CAPABILITIES_UPDATED,
        }) {
            engine.addNotificationListener(notificationName, this);
        }
    }


    @Override
    public void callback(String notificationName, final HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case EngineNotifications.BACKUP_FINISHED: {
                byte[] backupKeyUid = (byte[]) userInfo.get(EngineNotifications.BACKUP_FINISHED_BYTES_BACKUP_KEY_UID_KEY);
                Integer version = (Integer) userInfo.get(EngineNotifications.BACKUP_FINISHED_VERSION_KEY);
                byte[] encryptedContent = (byte[]) userInfo.get(EngineNotifications.BACKUP_FINISHED_ENCRYPTED_CONTENT_KEY);

                if (backupKeyUid != null && version != null && encryptedContent != null
                        && SettingsActivity.useAutomaticBackup()) {
                    BackupCloudProviderService.CloudProviderConfiguration configuration = SettingsActivity.getAutomaticBackupConfiguration();
                    if (configuration == null) {
                        App.openAppDialogBackupRequiresSignIn();
                    } else {
                        BackupCloudProviderService.uploadBackup(configuration, encryptedContent, new BackupCloudProviderService.OnBackupsUploadCallback() {
                            @Override
                            public void onUploadSuccess() {
                                engine.markBackupUploaded(backupKeyUid, version);
                            }

                            @Override
                            public void onUploadFailure(int error) {
                                switch (error) {
                                    case BackupCloudProviderService.ERROR_AUTHENTICATION_ERROR:
                                    case BackupCloudProviderService.ERROR_SIGN_IN_REQUIRED:
                                    case BackupCloudProviderService.ERROR_TEN_RETRIES_FAILED:
                                        App.openAppDialogBackupRequiresSignIn();
                                        break;
                                    case BackupCloudProviderService.ERROR_NETWORK_ERROR:
                                    default:
                                        BackupCloudProviderService.rescheduleBackupUpload(configuration, encryptedContent, this);
                                        break;
                                }
                            }
                        });
                    }
                }
                break;
            }
            case EngineNotifications.APP_BACKUP_REQUESTED: {
                byte[] backupKeyUid = (byte[]) userInfo.get(EngineNotifications.APP_BACKUP_REQUESTED_BYTES_BACKUP_KEY_UID_KEY);
                Integer version = (Integer) userInfo.get(EngineNotifications.APP_BACKUP_REQUESTED_VERSION_KEY);

                if (backupKeyUid != null && version != null) {
                    App.runThread(new BackupAppDataForEngineTask(backupKeyUid, version));
                }
                break;
            }
            case EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS: {
                //noinspection unchecked
                Map<String, Integer> appInfo = (Map<String, Integer>) userInfo.get(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY);
                //noinspection ConstantConditions
                boolean updated = (boolean) userInfo.get(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_UPDATED_KEY);

                if (appInfo != null) {
                    if (updated) {
                        Integer latest = appInfo.get("latest_android");
                        if (latest != null && latest > BuildConfig.VERSION_CODE) {
                            App.openAppDialogNewVersionAvailable();
                        }
                    } else {
                        Integer min = appInfo.get("min_android");
                        if (min != null && min > BuildConfig.VERSION_CODE) {
                            App.openAppDialogOutdatedVersion();
                        }
                    }
                }
                break;
            }
            case EngineNotifications.WEBSOCKET_CONNECTION_STATE_CHANGED: {
                Integer state = (Integer) userInfo.get(EngineNotifications.WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY);
                if (state == null) {
                    break;
                }

                AppSingleton.setWebsocketConnectivityState(state);
                break;
            }
            case EngineNotifications.PUSH_TOPIC_NOTIFIED: {
                String topic = (String) userInfo.get(EngineNotifications.PUSH_TOPIC_NOTIFIED_TOPIC_KEY);
                if (topic == null) {
                    break;
                }

                KeycloakManager.processPushTopicNotification(topic);
                break;
            }
            case EngineNotifications.KEYCLOAK_UPDATE_REQUIRED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.KEYCLOAK_UPDATE_REQUIRED_BYTES_OWNED_IDENTITY_KEY);
                if (bytesOwnedIdentity == null) {
                    break;
                }

                KeycloakManager.forceSyncManagedIdentity(bytesOwnedIdentity);
                break;
            }
            case EngineNotifications.UI_DIALOG: {
                AtomicLong discussionId = new AtomicLong();
                UUID dialogUuid = (UUID) userInfo.get(EngineNotifications.UI_DIALOG_UUID_KEY);
                ObvDialog dialog = (ObvDialog) userInfo.get(EngineNotifications.UI_DIALOG_DIALOG_KEY);
                Long creationTimestamp = (Long) userInfo.get(EngineNotifications.UI_DIALOG_CREATION_TIMESTAMP_KEY);
                if (dialog == null || creationTimestamp == null) {
                    break;
                }

                Invitation existingInvitation = db.invitationDao().getByDialogUuid(dialogUuid);

                if (dialog.getCategory().getObvGroupV2() != null) {
                    // groupV2
                    String groupName;
                    try {
                        JsonGroupDetails trustedGroupDetails = AppSingleton.getJsonObjectMapper().readValue(dialog.getCategory().getObvGroupV2().detailsAndPhotos.serializedGroupDetails, JsonGroupDetails.class);
                        groupName = trustedGroupDetails.getName();
                    } catch (Exception ex) {
                        groupName = null;
                    }
                    if (groupName == null) {
                        groupName = InvitationListViewModelKt.getReadableMembers(dialog.getCategory().getObvGroupV2());
                    }

                    Discussion discussion = db.discussionDao().getByGroupIdentifierWithAnyStatus(dialog.getBytesOwnedIdentity(), dialog.getCategory().getObvGroupV2().groupIdentifier.getBytes());

                    if (discussion != null) {
                        discussionId.set(discussion.id);
                    } else {
                        discussionId.set(db.discussionDao().insert(new Discussion(
                                groupName != null ? groupName : "",
                                dialog.getBytesOwnedIdentity(),
                                Discussion.TYPE_GROUP_V2,
                                dialog.getCategory().getObvGroupV2().groupIdentifier.getBytes(),
                                UUID.randomUUID(),
                                0,
                                System.currentTimeMillis(),
                                null,
                                false,
                                false,
                                false,
                                true,
                                -1,
                                Discussion.STATUS_PRE_DISCUSSION
                        )));
                    }
                } else if (dialog.getCategory().getPendingGroupMemberIdentities() != null && dialog.getCategory().getBytesGroupOwnerAndUid() != null) {
                    // groupV1
                    String name = "";
                    try {
                        name = AppSingleton.getJsonObjectMapper().readValue(
                                dialog.getCategory().getSerializedGroupDetails(),
                                JsonGroupDetails.class).getName();
                    } catch (Exception ex) {
                        // empty
                    }
                    Discussion discussion = db.discussionDao().getByGroupOwnerAndUidWithAnyStatus(dialog.getBytesOwnedIdentity(), dialog.getCategory().getBytesGroupOwnerAndUid());
                    if (discussion != null) {
                        discussionId.set(discussion.id);
                    } else {
                        discussionId.set(db.discussionDao().insert(new Discussion(
                                name,
                                dialog.getBytesOwnedIdentity(),
                                Discussion.TYPE_GROUP,
                                dialog.getCategory().getBytesGroupOwnerAndUid(),
                                UUID.randomUUID(),
                                0,
                                System.currentTimeMillis(),
                                null,
                                false,
                                false,
                                false,
                                true,
                                -1,
                                Discussion.STATUS_PRE_DISCUSSION
                        )));
                    }
                } else if (dialog.getCategory().getBytesContactIdentity() != null) {
                    // contact
                    String displayName;
                    try {
                        displayName = AppSingleton.getJsonObjectMapper().readValue(
                                dialog.getCategory().getContactDisplayNameOrSerializedDetails(),
                                JsonIdentityDetails.class).formatDisplayName(
                                JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                                SettingsActivity.getUppercaseLastName());
                    } catch (Exception ex) {
                        displayName = dialog.getCategory().getContactDisplayNameOrSerializedDetails();
                    }
                    if (displayName == null) {
                        displayName = AppSingleton.getContactCustomDisplayName(dialog.getCategory().getBytesContactIdentity());
                    }

                    Discussion discussion = null;
                    if (dialog.getCategory().getId() == ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY) {
                        Contact mediator = db.contactDao().get(dialog.getBytesOwnedIdentity(), dialog.getCategory()
                                .getBytesMediatorOrGroupOwnerIdentity());
                        if (mediator != null) {
                            discussion = db.discussionDao().getByContactWithAnyStatus(dialog.getBytesOwnedIdentity(), mediator.bytesContactIdentity);
                        }
                    } else {
                        discussion = db.discussionDao().getByContactWithAnyStatus(dialog.getBytesOwnedIdentity(), dialog.getCategory().getBytesContactIdentity());
                    }

                    if (discussion != null) {
                        discussionId.set(discussion.id);
                    } else {
                        discussionId.set(db.discussionDao().insert(new Discussion(
                                displayName,
                                dialog.getBytesOwnedIdentity(),
                                Discussion.TYPE_CONTACT,
                                dialog.getCategory().getBytesContactIdentity(),
                                UUID.randomUUID(),
                                0,
                                System.currentTimeMillis(),
                                null,
                                false,
                                false,
                                false,
                                true,
                                -1,
                                Discussion.STATUS_PRE_DISCUSSION
                        )));
                    }
                }

                switch (dialog.getCategory().getId()) {
                    case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY:
                    case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY:
                    case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY:
                    case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                    case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY: {
                        Invitation invitation = new Invitation(dialog, creationTimestamp, discussionId.get());
                        db.invitationDao().insert(invitation);
                        db.discussionDao().updateLastMessageTimestamp(discussionId.get(), InvitationListViewModelKt.getTimestamp(invitation));
                        break;
                    }
                    case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                    case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY:
                    case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                    case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY:
                    case ObvDialog.Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY: {
                        Invitation invitation = new Invitation(dialog, creationTimestamp, discussionId.get());
                        db.invitationDao().insert(invitation);
                        db.discussionDao().updateLastMessageTimestamp(discussionId.get(), InvitationListViewModelKt.getTimestamp(invitation));
                        // only notify if the invitation is different from the previous notification
                        if ((existingInvitation == null) || (existingInvitation.associatedDialog.getCategory().getId() != invitation.associatedDialog.getCategory().getId())) {
                            AndroidNotificationManager.displayInvitationNotification(invitation);
                        }
                        break;
                    }
                    case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                    case ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY: {
                        SettingsActivity.AutoJoinGroupsCategory autoJoinGroups = SettingsActivity.getAutoJoinGroups();
                        if (autoJoinGroups == SettingsActivity.AutoJoinGroupsCategory.EVERYONE) {
                            try {
                                dialog.setResponseToAcceptGroupInvite(true);
                                AppSingleton.getEngine().respondToDialog(dialog);
                                break;
                            } catch (Exception ignored) {
                            }
                        } else if (autoJoinGroups == SettingsActivity.AutoJoinGroupsCategory.CONTACTS) {
                            Contact groupOwner = db.contactDao().get(dialog.getBytesOwnedIdentity(), dialog.getCategory().getBytesMediatorOrGroupOwnerIdentity());
                            if (groupOwner != null && groupOwner.oneToOne) {
                                try {
                                    dialog.setResponseToAcceptGroupInvite(true);
                                    AppSingleton.getEngine().respondToDialog(dialog);
                                    break;
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        // fallback case, or if an exception occurred during auto-accept
                        Invitation invitation = new Invitation(dialog, creationTimestamp, discussionId.get());
                        db.invitationDao().insert(invitation);
                        db.discussionDao().updateLastMessageTimestamp(discussionId.get(), InvitationListViewModelKt.getTimestamp(invitation));
                        // only notify if the invitation is different from the previous notification
                        if ((existingInvitation == null) || (existingInvitation.associatedDialog.getCategory().getId() != invitation.associatedDialog.getCategory().getId())) {
                            AndroidNotificationManager.displayInvitationNotification(invitation);
                        }
                        break;
                    }
                }
                break;
            }
            case EngineNotifications.UI_DIALOG_DELETED: {
                App.runThread(() -> {
                    UUID dialogUuid = (UUID) userInfo.get(EngineNotifications.UI_DIALOG_DELETED_UUID_KEY);
                    Invitation existingInvitation = db.invitationDao().getByDialogUuid(dialogUuid);
                    if (existingInvitation != null) {
                        db.invitationDao().delete(existingInvitation);
                        // delete pre discussion
                        if (existingInvitation.discussionId != null) {
                            Discussion discussion = db.discussionDao().getById(existingInvitation.discussionId);
                            if (discussion != null && discussion.isPreDiscussion() && !db.invitationDao().discussionHasInvitations(discussion.id)) {
                                db.discussionDao().delete(discussion);
                            }
                        }
                    }
                });
                break;
            }


            case EngineNotifications.API_KEY_ACCEPTED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.API_KEY_ACCEPTED_OWNED_IDENTITY_KEY);
                EngineAPI.ApiKeyStatus apiKeyStatus = (EngineAPI.ApiKeyStatus) userInfo.get(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY);
                @SuppressWarnings("unchecked")
                List<EngineAPI.ApiKeyPermission> apiKeyPermissions = (List<EngineAPI.ApiKeyPermission>) userInfo.get(EngineNotifications.API_KEY_ACCEPTED_PERMISSIONS_KEY);
                Long apiKeyExpirationTimestamp = (Long) userInfo.get(EngineNotifications.API_KEY_ACCEPTED_API_KEY_EXPIRATION_TIMESTAMP_KEY);
                if (bytesOwnedIdentity != null && apiKeyPermissions != null && apiKeyStatus != null) {
                    OwnedIdentity identity = db.ownedIdentityDao().get(bytesOwnedIdentity);
                    if (identity != null) {
                        boolean changed = false;
                        if (identity.getApiKeyStatus() != apiKeyStatus) {
                            changed = true;
                            identity.setApiKeyStatus(apiKeyStatus);
                        }
                        if (!Objects.equals(identity.apiKeyExpirationTimestamp, apiKeyExpirationTimestamp)) {
                            changed = true;
                            identity.apiKeyExpirationTimestamp = apiKeyExpirationTimestamp;
                        }
                        long oldPermissions = identity.apiKeyPermissions;
                        identity.setApiKeyPermissions(apiKeyPermissions);
                        if (oldPermissions != identity.apiKeyPermissions) {
                            changed = true;
                        }
                        if (changed) {
                            db.ownedIdentityDao().updateApiKey(identity.bytesOwnedIdentity, identity.apiKeyStatus, identity.apiKeyPermissions, identity.apiKeyExpirationTimestamp);
                            App.openAppDialogApiKeyPermissionsUpdated(identity);
                        }
                    }
                }
                break;
            }
            case EngineNotifications.OWNED_IDENTITY_LIST_UPDATED: {
                App.runThread(() -> {
                    // only check for ownedIdentityManaged status change, do not handle insertions or deletions here
                    try {
                        ObvIdentity[] engineOwnedIdentities = AppSingleton.getEngine().getOwnedIdentities();
                        Map<BytesKey, ObvIdentity> engineOwnedIdentitiesMap = new HashMap<>();
                        for (ObvIdentity obvIdentity : engineOwnedIdentities) {
                            engineOwnedIdentitiesMap.put(new BytesKey(obvIdentity.getBytesIdentity()), obvIdentity);
                        }


                        List<OwnedIdentity> appOwnedIdentities = AppDatabase.getInstance().ownedIdentityDao().getAll();
                        for (OwnedIdentity appOwnedIdentity : appOwnedIdentities) {
                            ObvIdentity obvIdentity = engineOwnedIdentitiesMap.get(new BytesKey(appOwnedIdentity.bytesOwnedIdentity));
                            if (obvIdentity != null) {
                                // check whether managed status is the same
                                if (obvIdentity.isKeycloakManaged() != appOwnedIdentity.keycloakManaged) {
                                    appOwnedIdentity.keycloakManaged = obvIdentity.isKeycloakManaged();
                                    AppDatabase.getInstance().ownedIdentityDao().updateKeycloakManaged(appOwnedIdentity.bytesOwnedIdentity, appOwnedIdentity.keycloakManaged);

                                    if (Arrays.equals(appOwnedIdentity.bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                                        AppSingleton.updateCachedKeycloakManaged(appOwnedIdentity.bytesOwnedIdentity, appOwnedIdentity.keycloakManaged);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // do nothing --> will be done at next restart
                    }
                });
                break;
            }
            case EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                JsonIdentityDetails identityDetails = (JsonIdentityDetails) userInfo.get(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_IDENTITY_DETAILS_KEY);
                String photoUrl = (String) userInfo.get(EngineNotifications.OWNED_IDENTITY_DETAILS_CHANGED_PHOTO_URL_KEY);

                OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(bytesOwnedIdentity);
                if (ownedIdentity != null && identityDetails != null) {
                    try {
                        ownedIdentity.setIdentityDetails(identityDetails);
                        ownedIdentity.photoUrl = photoUrl;
                        db.ownedIdentityDao().updateIdentityDetailsAndPhoto(ownedIdentity.bytesOwnedIdentity, ownedIdentity.identityDetails, ownedIdentity.displayName, ownedIdentity.photoUrl);

                        if (Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                            AppSingleton.updateCachedPhotoUrl(bytesOwnedIdentity, photoUrl);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // failed, but we do nothing, this will be done again at next app startup
                    }
                }
                break;
            }
            case EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED_BYTES_OWNED_IDENTITY_KEY);
                Boolean hasUnpublished = (Boolean) userInfo.get(EngineNotifications.OWNED_IDENTITY_LATEST_DETAILS_UPDATED_HAS_UNPUBLISHED_KEY);

                OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(bytesOwnedIdentity);
                if (ownedIdentity != null && hasUnpublished != null) {
                    if (hasUnpublished && ownedIdentity.unpublishedDetails == OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW) {
                        ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_EXIST;
                        db.ownedIdentityDao().updateUnpublishedDetails(ownedIdentity.bytesOwnedIdentity, ownedIdentity.unpublishedDetails);
                    } else if (!hasUnpublished && ownedIdentity.unpublishedDetails == OwnedIdentity.UNPUBLISHED_DETAILS_EXIST) {
                        ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_NOTHING_NEW;
                        db.ownedIdentityDao().updateUnpublishedDetails(ownedIdentity.bytesOwnedIdentity, ownedIdentity.unpublishedDetails);
                    }
                }
                break;
            }
            case EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_BYTES_OWNED_IDENTITY_KEY);
                Boolean active = (Boolean) userInfo.get(EngineNotifications.OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_ACTIVE_KEY);

                OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(bytesOwnedIdentity);
                if (ownedIdentity != null && active != null) {
                    ownedIdentity.active = active;
                    db.ownedIdentityDao().updateActive(ownedIdentity.bytesOwnedIdentity, ownedIdentity.active);

                    AppSingleton.markIdentityActive(ownedIdentity, active);
                }
                break;
            }
            case EngineNotifications.OWN_CAPABILITIES_UPDATED: {
                byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.OWN_CAPABILITIES_UPDATED_BYTES_OWNED_IDENTITY_KEY);
                //noinspection unchecked
                List<ObvCapability> capabilities = (List<ObvCapability>) userInfo.get(EngineNotifications.OWN_CAPABILITIES_UPDATED_CAPABILITIES);

                if (bytesOwnedIdentity == null || capabilities == null) {
                    break;
                }

                OwnedIdentity ownedIdentity = db.ownedIdentityDao().get(bytesOwnedIdentity);
                if (ownedIdentity != null) {
                    for (ObvCapability obvCapability : ObvCapability.values()) {
                        boolean capable = capabilities.contains(obvCapability);

                        switch (obvCapability) {
                            case WEBRTC_CONTINUOUS_ICE:
                                if (capable != ownedIdentity.capabilityWebrtcContinuousIce) {
                                    db.ownedIdentityDao().updateCapabilityWebrtcContinuousIce(ownedIdentity.bytesOwnedIdentity, capable);
                                }
                                break;
                            case ONE_TO_ONE_CONTACTS:
                                if (capable != ownedIdentity.capabilityOneToOneContacts) {
                                    db.ownedIdentityDao().updateCapabilityOneToOneContacts(ownedIdentity.bytesOwnedIdentity, capable);
                                }
                                break;
                            case GROUPS_V2:
                                if (capable != ownedIdentity.capabilityGroupsV2) {
                                    db.ownedIdentityDao().updateCapabilityGroupsV2(ownedIdentity.bytesOwnedIdentity, capable);
                                }
                                break;
                        }
                    }
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
