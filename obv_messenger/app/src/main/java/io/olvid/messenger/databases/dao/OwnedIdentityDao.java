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

package io.olvid.messenger.databases.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.olvid.messenger.databases.entity.OwnedIdentity;

@Dao
public interface OwnedIdentityDao {
    @Insert
    void insert(@NonNull OwnedIdentity ownedIdentity);

    @Delete
    void delete(@NonNull OwnedIdentity ownedIdentity);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.KEYCLOAK_MANAGED + " = :keycloakManaged " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateKeycloakManaged(@NonNull byte[] bytesOwnedIdentity, boolean keycloakManaged);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.ACTIVE + " = :active " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateActive(@NonNull byte[] bytesOwnedIdentity, boolean active);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.IDENTITY_DETAILS + " = :identityDetails, " +
            OwnedIdentity.DISPLAY_NAME + " = :displayName " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateIdentityDetailsAndDisplayName(@NonNull byte[] bytesOwnedIdentity, @Nullable String identityDetails, @NonNull String displayName);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.API_KEY_STATUS + " = :apiKeyStatus, " +
            OwnedIdentity.API_KEY_PERMISSIONS + " = :apiKeyPermissions, " +
            OwnedIdentity.API_KEY_EXPIRATION_TIMESTAMP + " = :apiKeyExpirationTimestamp " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateApiKey(@NonNull byte[] bytesOwnedIdentity, int apiKeyStatus, long apiKeyPermissions, @Nullable Long apiKeyExpirationTimestamp);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.IDENTITY_DETAILS + " = :identityDetails, " +
            OwnedIdentity.DISPLAY_NAME + " = :displayName, " +
            OwnedIdentity.PHOTO_URL + " = :photoUrl " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateIdentityDetailsAndPhoto(@NonNull byte[] bytesOwnedIdentity, @Nullable String identityDetails, @NonNull String displayName, @Nullable String photoUrl);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.UNPUBLISHED_DETAILS + " = :unpublishedDetails " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateUnpublishedDetails(@NonNull byte[] bytesOwnedIdentity, int unpublishedDetails);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.CUSTOM_DISPLAY_NAME + " = :customDisplayName " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateCustomDisplayName(@NonNull byte[] bytesOwnedIdentity, @Nullable String customDisplayName);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.UNLOCK_PASSWORD + " = :unlockPassword, " +
            OwnedIdentity.UNLOCK_SALT + " = :unlockSalt " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateUnlockPasswordAndSalt(@NonNull byte[] bytesOwnedIdentity, @Nullable byte[] unlockPassword, @Nullable byte[] unlockSalt);

    // set start timestamp when transitioning into mute; clear it when leaving mute; preserve original start when extending an active mute
    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS + " = 1, " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + " = :prefMuteNotificationsTimestamp, " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_EXCEPT_MENTIONED + " = :prefMuteNotificationsExceptMentioned, " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_START_TIMESTAMP +
            " = CASE WHEN " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS_START_TIMESTAMP + " IS NULL THEN :nowTimestamp" +
            "        ELSE " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS_START_TIMESTAMP +
            " END " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateMuteNotifications(@NonNull byte[] bytesOwnedIdentity, @Nullable Long prefMuteNotificationsTimestamp, boolean prefMuteNotificationsExceptMentioned, long nowTimestamp);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS + " = 0, " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + " = NULL, " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_START_TIMESTAMP + " = NULL " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void clearMuteNotifications(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS + " = 1" +
            " AND " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + " IS NOT NULL")
    @NonNull
    List<OwnedIdentity> getAllWithFiniteMute();

    @Query("SELECT MIN(" + OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + ") FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS + " = 1" +
            " AND " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + " IS NOT NULL" +
            " AND " + OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP + " > :nowTimestamp")
    @Nullable
    Long getNextMuteExpirationAfter(long nowTimestamp);


    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.PREF_SHOW_NEUTRAL_NOTIFICATION_WHEN_HIDDEN + " = :prefShowNeutralNotificationWhenHidden " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateShowNeutralNotificationWhenHidden(@NonNull byte[] bytesOwnedIdentity, boolean prefShowNeutralNotificationWhenHidden);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.CAPABILITY_WEBRTC_CONTINUOUS_ICE + " = :capable " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateCapabilityWebrtcContinuousIce(@NonNull byte[] bytesOwnedIdentity, boolean capable);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.CAPABILITY_GROUPS_V2 + " = :capable " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateCapabilityGroupsV2(@NonNull byte[] bytesOwnedIdentity, boolean capable);

    @Query("UPDATE " + OwnedIdentity.TABLE_NAME +
            " SET " + OwnedIdentity.CAPABILITY_ONE_TO_ONE_CONTACTS + " = :capable " +
            " WHERE "  + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    void updateCapabilityOneToOneContacts(@NonNull byte[] bytesOwnedIdentity, boolean capable);



    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL ")
    LiveData<List<OwnedIdentity>> getAllNotHiddenLiveData();

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL ")
    List<OwnedIdentity> getAllNotHidden();

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " AND " + OwnedIdentity.BYTES_OWNED_IDENTITY + " != :bytesOwnedIdentity ")
    LiveData<List<OwnedIdentity>> getAllNotHiddenExceptOne(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " AND " + OwnedIdentity.BYTES_OWNED_IDENTITY + " != :bytesOwnedIdentity " +
            " ORDER BY CASE WHEN " + OwnedIdentity.CUSTOM_DISPLAY_NAME + " IS NULL THEN " + OwnedIdentity.DISPLAY_NAME + " ELSE " + OwnedIdentity.CUSTOM_DISPLAY_NAME + " END ASC ")
    LiveData<List<OwnedIdentity>> getAllNotHiddenExceptOneSorted(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL " +
            " ORDER BY COALESCE(" + OwnedIdentity.CUSTOM_DISPLAY_NAME + ", " + OwnedIdentity.DISPLAY_NAME + ") ASC ")
    List<OwnedIdentity> getAllNotHiddenSortedSync();

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME)
    List<OwnedIdentity> getAll();

    @Query("SELECT " + OwnedIdentity.BYTES_OWNED_IDENTITY + ", " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS + ", " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_EXCEPT_MENTIONED + ", " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP +
            " FROM " + OwnedIdentity.TABLE_NAME)
    LiveData<List<MuteStateStub>> getMuteStateStubsLiveData();


    @Query("SELECT " + OwnedIdentity.BYTES_OWNED_IDENTITY + ", " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS + ", " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_EXCEPT_MENTIONED + ", " +
            OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP +
            " FROM " + OwnedIdentity.TABLE_NAME)
    List<MuteStateStub> getMuteStateStubs();

    class MuteStateStub {
        @ColumnInfo(name = OwnedIdentity.BYTES_OWNED_IDENTITY)
        @NonNull
        public byte[] bytesOwnedIdentity;

        @ColumnInfo(name = OwnedIdentity.PREF_MUTE_NOTIFICATIONS)
        public boolean prefMuteNotifications;

        @ColumnInfo(name = OwnedIdentity.PREF_MUTE_NOTIFICATIONS_EXCEPT_MENTIONED)
        public boolean prefMuteNotificationsExceptMentioned;

        @ColumnInfo(name = OwnedIdentity.PREF_MUTE_NOTIFICATIONS_TIMESTAMP)
        @Nullable
        public Long prefMuteNotificationsTimestamp;
    }

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    @Nullable OwnedIdentity get(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT * FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.BYTES_OWNED_IDENTITY + " = :bytesOwnedIdentity")
    LiveData<OwnedIdentity> getLiveData(@NonNull byte[] bytesOwnedIdentity);

    @Query("SELECT COUNT(*) FROM " + OwnedIdentity.TABLE_NAME)
    int countAll();

    @Query("SELECT COUNT(*) FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NULL ")
    int countNotHidden();

    @Query("SELECT " + OwnedIdentity.BYTES_OWNED_IDENTITY + ", " + OwnedIdentity.UNLOCK_PASSWORD + ", " + OwnedIdentity.UNLOCK_SALT + " FROM " + OwnedIdentity.TABLE_NAME +
            " WHERE " + OwnedIdentity.UNLOCK_PASSWORD + " IS NOT NULL " +
            " AND " + OwnedIdentity.UNLOCK_SALT + " IS NOT NULL ")
    List<OwnedIdentityPasswordAndSalt> getHiddenIdentityPasswordsAndSalts();

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
}
