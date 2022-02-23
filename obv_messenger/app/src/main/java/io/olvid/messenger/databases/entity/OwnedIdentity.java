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

package io.olvid.messenger.databases.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.settings.SettingsActivity;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = OwnedIdentity.TABLE_NAME
)
public class OwnedIdentity {
    public static final String TABLE_NAME = "identity_table";

    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String DISPLAY_NAME = "display_name";
    public static final String IDENTITY_DETAILS = "identity_details";
    public static final String API_KEY_STATUS = "api_key_status";
    public static final String UNPUBLISHED_DETAILS = "unpublished_details";
    public static final String PHOTO_URL = "photo_url";
    public static final String API_KEY_PERMISSIONS = "api_key_permissions";
    public static final String API_KEY_EXPIRATION_TIMESTAMP = "api_key_expiration_timestamp";
    public static final String KEYCLOAK_MANAGED = "keycloak_managed";
    public static final String ACTIVE = "active";
    public static final String CUSTOM_DISPLAY_NAME = "custom_display_name";
    public static final String UNLOCK_PASSWORD = "unlock_password";
    public static final String UNLOCK_SALT = "unlock_salt";
    public static final String PREF_MUTE_NOTIFICATIONS = "pref_mute_notifications";
    public static final String PREF_MUTE_NOTIFICATIONS_TIMESTAMP = "pref_mute_notifications_timestamp"; // when to stop muting notifications, null if unlimited
    public static final String PREF_SHOW_NEUTRAL_NOTIFICATION_WHEN_HIDDEN = "pref_show_neutral_notification_when_hidden"; // if true, even when the profile is hidden you will get an "Olvid requires your attention" notification
    public static final String CAPABILITY_WEBRTC_CONTINUOUS_ICE = "capability_webrtc_continuous_ice";
    public static final String CAPABILITY_GROUPS_V2 = "capability_groups_v2";

    public static final int UNPUBLISHED_DETAILS_NOTHING_NEW = 0;
    public static final int UNPUBLISHED_DETAILS_EXIST = 1;

    public static final int API_KEY_STATUS_UNKNOWN = 0;
    public static final int API_KEY_STATUS_VALID = 1;
    public static final int API_KEY_STATUS_EXPIRED = 2;
    public static final int API_KEY_STATUS_LICENSE_EXHAUSTED = 3;
    public static final int API_KEY_STATUS_OPEN_BETA_KEY = 4;
    public static final int API_KEY_STATUS_FREE_TRIAL_KEY = 5;
    public static final int API_KEY_STATUS_AWAITING_PAYMENT_GRACE_PERIOD = 6;
    public static final int API_KEY_STATUS_AWAITING_PAYMENT_ON_HOLD = 7;
    public static final int API_KEY_STATUS_FREE_TRIAL_KEY_EXPIRED = 8;

    @SuppressWarnings("PointlessBitwiseExpression")
    public static final long API_KEY_PERMISSION_CALL = 1L<<0;
    public static final long API_KEY_PERMISSION_WEB_CLIENT = 1L<<1;

    @PrimaryKey
    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @ColumnInfo(name = DISPLAY_NAME)
    @NonNull
    public String displayName;

    @ColumnInfo(name = IDENTITY_DETAILS)
    @Nullable
    public String identityDetails;

    @ColumnInfo(name = API_KEY_STATUS)
    public int apiKeyStatus;

    @ColumnInfo(name = UNPUBLISHED_DETAILS)
    public int unpublishedDetails;

    @ColumnInfo(name = PHOTO_URL)
    @Nullable
    public String photoUrl;

    @ColumnInfo(name = API_KEY_PERMISSIONS)
    public long apiKeyPermissions;

    @ColumnInfo(name = API_KEY_EXPIRATION_TIMESTAMP)
    @Nullable
    public Long apiKeyExpirationTimestamp;

    @ColumnInfo(name = KEYCLOAK_MANAGED)
    public boolean keycloakManaged;

    @ColumnInfo(name = ACTIVE)
    public boolean active;

    @ColumnInfo(name = CUSTOM_DISPLAY_NAME)
    public String customDisplayName;

    @ColumnInfo(name = UNLOCK_PASSWORD)
    public byte[] unlockPassword;

    @ColumnInfo(name = UNLOCK_SALT)
    public byte[] unlockSalt;

    @ColumnInfo(name = PREF_MUTE_NOTIFICATIONS)
    public boolean prefMuteNotifications;

    @ColumnInfo(name = PREF_MUTE_NOTIFICATIONS_TIMESTAMP)
    @Nullable
    public Long prefMuteNotificationsTimestamp;

    @ColumnInfo(name = PREF_SHOW_NEUTRAL_NOTIFICATION_WHEN_HIDDEN)
    public boolean prefShowNeutralNotificationWhenHidden;

    @ColumnInfo(name = CAPABILITY_WEBRTC_CONTINUOUS_ICE)
    public boolean capabilityWebrtcContinuousIce;

    @ColumnInfo(name = CAPABILITY_GROUPS_V2)
    public boolean capabilityGroupsV2;

    // Constructor required by Room
    public OwnedIdentity(@NonNull byte[] bytesOwnedIdentity, @NonNull String displayName, @Nullable String identityDetails, int apiKeyStatus, int unpublishedDetails, @Nullable String photoUrl, long apiKeyPermissions, @Nullable Long apiKeyExpirationTimestamp, boolean keycloakManaged, boolean active, String customDisplayName, byte[] unlockPassword, byte[] unlockSalt, boolean prefMuteNotifications, @Nullable Long prefMuteNotificationsTimestamp, boolean prefShowNeutralNotificationWhenHidden, boolean capabilityWebrtcContinuousIce, boolean capabilityGroupsV2) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.displayName = displayName;
        this.identityDetails = identityDetails;
        this.apiKeyStatus = apiKeyStatus;
        this.unpublishedDetails = unpublishedDetails;
        this.photoUrl = photoUrl;
        this.apiKeyPermissions = apiKeyPermissions;
        this.apiKeyExpirationTimestamp = apiKeyExpirationTimestamp;
        this.keycloakManaged = keycloakManaged;
        this.active = active;
        this.customDisplayName = customDisplayName;
        this.unlockPassword = unlockPassword;
        this.unlockSalt = unlockSalt;
        this.prefMuteNotifications = prefMuteNotifications;
        this.prefMuteNotificationsTimestamp = prefMuteNotificationsTimestamp;
        this.prefShowNeutralNotificationWhenHidden = prefShowNeutralNotificationWhenHidden;
        this.capabilityWebrtcContinuousIce = capabilityWebrtcContinuousIce;
        this.capabilityGroupsV2 = capabilityGroupsV2;
    }



    // Constructor used when inserting a new OwnedIdentity
    @Ignore
    public OwnedIdentity(@NonNull ObvIdentity obvOwnedIdentity, int apiKeyStatus) throws Exception {
        this.bytesOwnedIdentity = obvOwnedIdentity.getBytesIdentity();
        this.setIdentityDetails(obvOwnedIdentity.getIdentityDetails());
        this.apiKeyStatus = apiKeyStatus;
        this.unpublishedDetails = UNPUBLISHED_DETAILS_NOTHING_NEW;
        this.apiKeyPermissions = 0;
        this.apiKeyExpirationTimestamp = null;
        this.keycloakManaged = obvOwnedIdentity.isKeycloakManaged();
        this.active = obvOwnedIdentity.isActive();
        this.customDisplayName = null;
        this.unlockPassword = null;
        this.unlockSalt = null;
        this.prefMuteNotifications = false;
        this.prefMuteNotificationsTimestamp = null;
        this.prefShowNeutralNotificationWhenHidden = false;
        this.capabilityWebrtcContinuousIce = false;
        this.capabilityGroupsV2 = false;
    }

    public void setIdentityDetails(@Nullable JsonIdentityDetails jsonIdentityDetails) throws Exception {
        if (jsonIdentityDetails == null) {
            this.identityDetails = null;
            this.displayName = "";
        } else {
            this.identityDetails = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonIdentityDetails);
            this.displayName = jsonIdentityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
        }
    }

    @Nullable
    public JsonIdentityDetails getIdentityDetails() {
        if (identityDetails == null) {
            return null;
        }
        try {
            return AppSingleton.getJsonObjectMapper().readValue(identityDetails, JsonIdentityDetails.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getCustomDisplayName() {
        if (customDisplayName == null) {
            return displayName;
        }
        return customDisplayName;
    }

    public boolean shouldMuteNotifications() {
        // for hidden identities, never show a notification if the identity is not currently open
        if (isHidden() && !Arrays.equals(AppSingleton.getBytesCurrentIdentity(), bytesOwnedIdentity)) {
            return true;
        }
        if (prefMuteNotificationsTimestamp == null || prefMuteNotificationsTimestamp > System.currentTimeMillis()) {
            return prefMuteNotifications;
        }
        return false;
    }

    // calling this method only makes sense after getting true from shouldMuteNotifications. Otherwise a full notification should be displayed anyway
    public boolean shouldShowNeutralNotification() {
        if (prefMuteNotifications && (prefMuteNotificationsTimestamp == null || prefMuteNotificationsTimestamp > System.currentTimeMillis())) {
            return false;
        }
        return prefShowNeutralNotificationWhenHidden;
    }

    public boolean isHidden() {
        return unlockPassword != null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OwnedIdentity)) {
            return false;
        }
        OwnedIdentity other = (OwnedIdentity) obj;
        return Arrays.equals(bytesOwnedIdentity, other.bytesOwnedIdentity);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytesOwnedIdentity);
    }

    public List<EngineAPI.ApiKeyPermission> getApiKeyPermissions() {
        return deserializeApiKeyPermissions(apiKeyPermissions);
    }

    public static List<EngineAPI.ApiKeyPermission> deserializeApiKeyPermissions(long apiKeyPermissions) {
        List<EngineAPI.ApiKeyPermission> list = new ArrayList<>();
        if ((apiKeyPermissions & API_KEY_PERMISSION_CALL) != 0) {
            list.add(EngineAPI.ApiKeyPermission.CALL);
        }
        if ((apiKeyPermissions & API_KEY_PERMISSION_WEB_CLIENT) != 0) {
            list.add(EngineAPI.ApiKeyPermission.WEB_CLIENT);
        }
        return list;
    }

    public void setApiKeyPermissions(List<EngineAPI.ApiKeyPermission> permissions) {
        this.apiKeyPermissions = serializeApiKeyPermissions(permissions);
    }

    public static long serializeApiKeyPermissions(List<EngineAPI.ApiKeyPermission> permissions) {
        long apiKeyPermissions = 0L;
        for (EngineAPI.ApiKeyPermission permission : permissions) {
            switch (permission) {
                case CALL:
                    apiKeyPermissions |= API_KEY_PERMISSION_CALL;
                    break;
                case WEB_CLIENT:
                    apiKeyPermissions |= API_KEY_PERMISSION_WEB_CLIENT;
                    break;
            }
        }
        return apiKeyPermissions;
    }

    public EngineAPI.ApiKeyStatus getApiKeyStatus() {
        return deserializeApiKeyStatus(apiKeyStatus);
    }

    public static EngineAPI.ApiKeyStatus deserializeApiKeyStatus(int apiKeyStatus) {
        switch (apiKeyStatus) {
            case API_KEY_STATUS_VALID:
                return EngineAPI.ApiKeyStatus.VALID;
            case API_KEY_STATUS_EXPIRED:
                return EngineAPI.ApiKeyStatus.EXPIRED;
            case API_KEY_STATUS_LICENSE_EXHAUSTED:
                return EngineAPI.ApiKeyStatus.LICENSES_EXHAUSTED;
            case API_KEY_STATUS_OPEN_BETA_KEY:
                return EngineAPI.ApiKeyStatus.OPEN_BETA_KEY;
            case API_KEY_STATUS_FREE_TRIAL_KEY:
                return EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY;
            case API_KEY_STATUS_AWAITING_PAYMENT_GRACE_PERIOD:
                return EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_GRACE_PERIOD;
            case API_KEY_STATUS_AWAITING_PAYMENT_ON_HOLD:
                return EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD;
            case API_KEY_STATUS_FREE_TRIAL_KEY_EXPIRED:
                return EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY_EXPIRED;
            case API_KEY_STATUS_UNKNOWN:
            default:
                return EngineAPI.ApiKeyStatus.UNKNOWN;
        }
    }

    public void setApiKeyStatus(EngineAPI.ApiKeyStatus status) {
        apiKeyStatus = serializeApiKeyStatus(status);
    }

    public static int serializeApiKeyStatus(EngineAPI.ApiKeyStatus status) {
        switch (status) {
            case UNKNOWN:
                return API_KEY_STATUS_UNKNOWN;
            case VALID:
                return API_KEY_STATUS_VALID;
            case LICENSES_EXHAUSTED:
                return API_KEY_STATUS_LICENSE_EXHAUSTED;
            case EXPIRED:
                return API_KEY_STATUS_EXPIRED;
            case OPEN_BETA_KEY:
                return API_KEY_STATUS_OPEN_BETA_KEY;
            case FREE_TRIAL_KEY:
                return API_KEY_STATUS_FREE_TRIAL_KEY;
            case AWAITING_PAYMENT_GRACE_PERIOD:
                return API_KEY_STATUS_AWAITING_PAYMENT_GRACE_PERIOD;
            case AWAITING_PAYMENT_ON_HOLD:
                return API_KEY_STATUS_AWAITING_PAYMENT_ON_HOLD;
            case FREE_TRIAL_KEY_EXPIRED:
                return API_KEY_STATUS_FREE_TRIAL_KEY_EXPIRED;
        }
        return -1;
    }
}
