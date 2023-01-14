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

package io.olvid.messenger.databases.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;

@Dao
public interface OwnedIdentityDao {
    @Insert
    void insert(OwnedIdentity contact);

    @Delete
    void delete(OwnedIdentity ownedIdentity);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.KEYCLOAK_MANAGED + " = :keycloakManaged " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateKeycloakManaged(byte[] bytesOwnedIdentity, boolean keycloakManaged);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.ACTIVE + " = :active " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateActive(byte[] bytesOwnedIdentity, boolean active);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.IDENTITY_DETAILS + " = :identityDetails, " +
            OwnedIdentity.DISPLAY_NAME + " = :displayName " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateIdentityDetailsAndDisplayName(byte[] bytesOwnedIdentity, String identityDetails, String displayName);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.API_KEY_STATUS + " = :apiKeyStatus, " +
            OwnedIdentity.API_KEY_PERMISSIONS + " = :apiKeyPermissions, " +
            OwnedIdentity.API_KEY_EXPIRATION_TIMESTAMP + " = :apiKeyExpirationTimestamp " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateApiKey(byte[] bytesOwnedIdentity, int apiKeyStatus, long apiKeyPermissions, Long apiKeyExpirationTimestamp);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.IDENTITY_DETAILS + " = :identityDetails, " +
            OwnedIdentity.DISPLAY_NAME + " = :displayName, " +
            OwnedIdentity.PHOTO_URL + " = :photoUrl " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateIdentityDetailsAndPhoto(byte[] bytesOwnedIdentity, String identityDetails, String displayName, String photoUrl);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.UNPUBLISHED_DETAILS + " = :unpublishedDetails " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateUnpublishedDetails(byte[] bytesOwnedIdentity, int unpublishedDetails);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.CUSTOM_DISPLAY_NAME + " = :customDisplayName " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateCustomDisplayName(byte[] bytesOwnedIdentity, String customDisplayName);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.UNLOCK_PASSWORD + " = :unlockPassword, " +
            OwnedIdentity.UNLOCK_SALT + " = :unlockSalt " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateUnlockPasswordAndSalt(byte[] bytesOwnedIdentity, byte[] unlockPassword, byte[] unlockSalt);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS + " = :prefMuteNotifications, " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + " = :prefMuteNotificationsTimestamp " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateMuteNotifications(byte[] bytesOwnedIdentity, boolean prefMuteNotifications, Long prefMuteNotificationsTimestamp);


    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.PREF_SHOW_NEUTRAL_NOTIFICATION_WHEN_HIDDEN + " = :prefShowNeutralNotificationWhenHidden " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateShowNeutralNotificationWhenHidden(byte[] bytesOwnedIdentity, boolean prefShowNeutralNotificationWhenHidden);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.CAPABILITY_WEBRTC_CONTINUOUS_ICE + " = :capable " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateCapabilityWebrtcContinuousIce(byte[] bytesOwnedIdentity, boolean capable);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.CAPABILITY_GROUPS_V2 + " = :capable " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateCapabilityGroupsV2(byte[] bytesOwnedIdentity, boolean capable);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.CAPABILITY_ONE_TO_ONE_CONTACTS + " = :capable " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateCapabilityOneToOneContacts(byte[] bytesOwnedIdentity, boolean capable);



    @Query("SELECT oi.*, unread.count AS unreadMessageCount, unreadinv.count AS unreadInvitationCount, unreaddisc.count AS unreadDiscussionCount FROM " + OwnedIdentity.TABLE_NAME + " AS oi " +
            " LEFT JOIN ( " +
            " SELECT COUNT(*) AS count, disc." + Discussion.BYTES_OWNED_IDENTITY + " AS boi FROM " + Discussion.TABLE_NAME + " AS disc " +
            " JOIN " + Message.TABLE_NAME + " AS mess " +
            " ON disc.id = mess." + Message.DISCUSSION_ID +
            " WHERE mess." + Message.STATUS + " = " + Message.STATUS_UNREAD +
            " GROUP BY disc." + Discussion.BYTES_OWNED_IDENTITY +
            " ) AS unread " +
            " ON unread.boi = oi." + OwnedIdentity.BYTES_OWNED_IDENTITY +

            " LEFT JOIN (" +
            " SELECT COUNT(*) AS count, inv." + Invitation.BYTES_OWNED_IDENTITY + " AS boi FROM " + Invitation.TABLE_NAME + " AS inv " +
            " WHERE inv." + Invitation.CATEGORY_ID + " IN ( " +
            ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY +
            ") GROUP BY inv." + Discussion.BYTES_OWNED_IDENTITY +
            " ) as unreadinv " +
            " ON unreadinv.boi = oi." + OwnedIdentity.BYTES_OWNED_IDENTITY +

            " LEFT JOIN ( " +
            " SELECT COUNT(*) AS count, disc." + Discussion.BYTES_OWNED_IDENTITY + " AS boi FROM " + Discussion.TABLE_NAME + " AS disc " +
            " WHERE disc." + Discussion.UNREAD + " = 1 " +
            " GROUP BY disc." + Discussion.BYTES_OWNED_IDENTITY +
            " ) AS unreaddisc " +
            " ON unreaddisc.boi = oi." + OwnedIdentity.BYTES_OWNED_IDENTITY +

            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " AND " + OwnedIdentity.BYTES_OWNED_IDENTITY + " != :byteCurrentIdentity " +
            " ORDER BY CASE WHEN " + OwnedIdentity.CUSTOM_DISPLAY_NAME + " IS NULL THEN " + OwnedIdentity.DISPLAY_NAME + " ELSE " + OwnedIdentity.CUSTOM_DISPLAY_NAME + " END ASC ")
    LiveData<List<OwnedIdentityAndUnreadMessageCount>> getAllNotHiddenWithUnreadMessageCount(byte[] byteCurrentIdentity);

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL ")
    LiveData<List<OwnedIdentity>> getAllNotHiddenLiveData();

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL ")
    List<OwnedIdentity> getAllNotHidden();

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " AND " + OwnedIdentity.BYTES_OWNED_IDENTITY + " != :bytesOwnedIdentity ")
    LiveData<List<OwnedIdentity>> getAllNotHiddenExceptOne(byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " ORDER BY COALESCE(" + OwnedIdentity.CUSTOM_DISPLAY_NAME + ", " + OwnedIdentity.DISPLAY_NAME + ") ASC ")
    List<OwnedIdentity> getAllNotHiddenSortedSync();

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME)
    List<OwnedIdentity> getAll();

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    OwnedIdentity get(byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    LiveData<OwnedIdentity> getLiveData(byte[] bytesOwnedIdentity);

    @Query("SELECT COUNT(*) FROM " + OwnedIdentity.TABLE_NAME)
    int countAll();

    @Query("SELECT COUNT(*) FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL ")
    int countNotHidden();

    @Query("SELECT " + OwnedIdentity.BYTES_OWNED_IDENTITY + ", " + OwnedIdentity.UNLOCK_PASSWORD + ", " + OwnedIdentity.UNLOCK_SALT + " FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NOT NULL " +
            " AND " + OwnedIdentity.UNLOCK_SALT + " IS NOT NULL ")
    List<OwnedIdentityPasswordAndSalt> getHiddenIdentityPasswordsAndSalts();

    @Query("SELECT EXISTS ( " +
            " SELECT 1 FROM " + Message.TABLE_NAME + " AS mess " +
            " JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON disc.id = mess." + Message.DISCUSSION_ID +
            " JOIN " + OwnedIdentity.TABLE_NAME + " AS oi " +
            " ON disc." + Discussion.BYTES_OWNED_IDENTITY + " = oi." + OwnedIdentity.BYTES_OWNED_IDENTITY +
            " WHERE mess." + Message.STATUS + " = " + Message.STATUS_UNREAD +
            " AND oi." + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " AND oi." + OwnedIdentity.BYTES_OWNED_IDENTITY + " != :byteCurrentIdentity " +
            " UNION " +
            " SELECT 1 FROM " + Invitation.TABLE_NAME + " AS inv " +
            " JOIN " + OwnedIdentity.TABLE_NAME + " AS oi " +
            " ON inv." + Invitation.BYTES_OWNED_IDENTITY + " = oi." + OwnedIdentity.BYTES_OWNED_IDENTITY +
            " WHERE inv." + Invitation.CATEGORY_ID + " IN ( " +
            ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY + ", " +
            ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY +
            ") AND oi." + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " AND oi." + OwnedIdentity.BYTES_OWNED_IDENTITY + " != :byteCurrentIdentity " +
            " UNION " +
            " SELECT 1 FROM " + Discussion.TABLE_NAME + " AS disc " +
            " JOIN " + OwnedIdentity.TABLE_NAME + " AS oi " +
            " ON disc." + Discussion.BYTES_OWNED_IDENTITY + " = oi." + OwnedIdentity.BYTES_OWNED_IDENTITY +
            " WHERE disc." + Discussion.UNREAD + " = 1 " +
            " AND oi." + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " AND oi." + OwnedIdentity.BYTES_OWNED_IDENTITY + " != :byteCurrentIdentity " +
            " )")
    LiveData<Boolean> otherNotHiddenOwnedIdentityHasMessageOrInvitation(byte[] byteCurrentIdentity);

    @Query("SELECT oi.*, disc.id AS discussionId FROM " + OwnedIdentity.TABLE_NAME + " AS oi " +
            " JOIN " + Discussion.TABLE_NAME + " AS disc " +
            " ON oi." + OwnedIdentity.BYTES_OWNED_IDENTITY + " = disc." + Discussion.BYTES_OWNED_IDENTITY +
            " WHERE disc." + Discussion.BYTES_DISCUSSION_IDENTIFIER + " = :bytesDiscussionIdentifier " +
            " AND disc." + Discussion.DISCUSSION_TYPE + " = :discussionType " +
            " AND oi." + OwnedIdentity.BYTES_OWNED_IDENTITY + " != :bytesOwnedIdentity " +
            " AND oi." + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " ORDER BY CASE WHEN " + OwnedIdentity.CUSTOM_DISPLAY_NAME + " IS NULL THEN " + OwnedIdentity.DISPLAY_NAME + " ELSE " + OwnedIdentity.CUSTOM_DISPLAY_NAME + " END ASC ")
    LiveData<List<OwnedIdentityAndDiscussionId>> getOtherNonHiddenOwnedIdentitiesForDiscussion(byte[] bytesOwnedIdentity, int discussionType, byte[] bytesDiscussionIdentifier);


    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NOT NULL ")
    List<OwnedIdentity> getAllHidden();


    class OwnedIdentityPasswordAndSalt {
        public byte[] bytes_owned_identity;
        public byte[] unlock_password;
        public byte[] unlock_salt;
    }

    class OwnedIdentityAndUnreadMessageCount {
        @Embedded
        public OwnedIdentity ownedIdentity;
        public long unreadMessageCount;
        public long unreadInvitationCount;
        public long unreadDiscussionCount;
    }

    class OwnedIdentityAndDiscussionId {
        @Embedded
        public OwnedIdentity ownedIdentity;
        public long discussionId;
    }
}
