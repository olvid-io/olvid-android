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

package io.olvid.engine.datatypes.notifications;


public abstract class IdentityNotifications {
    public static final String NOTIFICATION_OWNED_IDENTITY_LIST_UPDATED = "identity_manager_notification_owned_identity_list_updated";

    public static final String NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED = "identity_manager_notification_owned_identity_published_details_updated";
    public static final String NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_OWNED_IDENTITY_PUBLISHED_DETAILS_UPDATED_IDENTITY_DETAILS_KEY = "identity_details"; // JsonIdentityDetailsWithVersionAndPhoto

    public static final String NOTIFICATION_NEW_CONTACT_IDENTITY = "identity_manager_notification_new_contact_identity";
    public static final String NOTIFICATION_NEW_CONTACT_IDENTITY_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_NEW_CONTACT_IDENTITY_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_NEW_CONTACT_IDENTITY_KEYCLOAK_MANAGED_KEY = "keycloak_managed"; // boolean
    public static final String NOTIFICATION_NEW_CONTACT_IDENTITY_ACTIVE_KEY = "active"; // boolean
    public static final String NOTIFICATION_NEW_CONTACT_IDENTITY_ONE_TO_ONE_KEY = "one_to_one"; // boolean
    public static final String NOTIFICATION_NEW_CONTACT_IDENTITY_TRUST_LEVEL_KEY = "trust_level"; // int

    public static final String NOTIFICATION_CONTACT_IDENTITY_DELETED = "identity_manager_notification_contact_identity_deleted";
    public static final String NOTIFICATION_CONTACT_IDENTITY_DELETED_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_CONTACT_IDENTITY_DELETED_OWNED_IDENTITY_KEY = "owned_identity";

    public static final String NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED = "identity_manager_notification_contact_trust_level_increased";
    public static final String NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED_CONTACT_IDENTITY_KEY = "contact_identity"; // Identity
    public static final String NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_CONTACT_TRUST_LEVEL_INCREASED_TRUST_LEVEL_KEY = "trust_level"; // TrustLevel

    public static final String NOTIFICATION_NEW_CONTACT_DEVICE = "identity_manager_notification_new_contact_device";
    public static final String NOTIFICATION_NEW_CONTACT_DEVICE_CONTACT_DEVICE_UID_KEY = "contact_device_uid";
    public static final String NOTIFICATION_NEW_CONTACT_DEVICE_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_NEW_CONTACT_DEVICE_OWNED_IDENTITY_KEY = "owned_identity";

    public static final String NOTIFICATION_GROUP_MEMBER_ADDED = "identity_manager_notification_group_member_added";
    public static final String NOTIFICATION_GROUP_MEMBER_ADDED_GROUP_UID_KEY = "group_uid";
    public static final String NOTIFICATION_GROUP_MEMBER_ADDED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY = "contact_identity";

    public static final String NOTIFICATION_GROUP_MEMBER_REMOVED = "identity_manager_notification_group_member_removed";
    public static final String NOTIFICATION_GROUP_MEMBER_REMOVED_GROUP_UID_KEY = "group_uid";
    public static final String NOTIFICATION_GROUP_MEMBER_REMOVED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY = "contact_identity";

    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_ADDED = "identity_manager_notification_pending_group_member_added";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_GROUP_UID_KEY = "group_uid";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_ADDED_CONTACT_SERIALIZED_DETAILS_KEY = "contact_serialized_details";

    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED = "identity_manager_notification_pending_group_member_removed";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_GROUP_UID_KEY = "group_uid";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_REMOVED_CONTACT_SERIALIZED_DETAILS_KEY = "contact_serialized_details";

    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED = "identity_manager_notification_pending_group_member_declined_toggled";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_GROUP_UID_KEY = "group_uid";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_PENDING_GROUP_MEMBER_DECLINED_TOGGLED_DECLINED_KEY = "declined";

    public static final String NOTIFICATION_GROUP_CREATED = "identity_manager_notification_group_created";
    public static final String NOTIFICATION_GROUP_CREATED_GROUP_OWNER_AND_UID_KEY = "group_uid";
    public static final String NOTIFICATION_GROUP_CREATED_OWNED_IDENTITY_KEY = "owned_identity";

    public static final String NOTIFICATION_GROUP_DELETED = "identity_manager_notification_group_deleted";
    public static final String NOTIFICATION_GROUP_DELETED_GROUP_OWNER_AND_UID_KEY = "group_uid";
    public static final String NOTIFICATION_GROUP_DELETED_OWNED_IDENTITY_KEY = "owned_identity";

    public static final String NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS = "identity_manager_notification_new_contact_published_details";
    public static final String NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_NEW_CONTACT_PUBLISHED_DETAILS_OWNED_IDENTITY_KEY = "owned_identity";

    public static final String NOTIFICATION_CONTACT_PHOTO_SET = "identity_manager_notification_contact_photo_set";
    public static final String NOTIFICATION_CONTACT_PHOTO_SET_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_CONTACT_PHOTO_SET_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_CONTACT_PHOTO_SET_VERSION_KEY = "version";
    public static final String NOTIFICATION_CONTACT_PHOTO_SET_IS_TRUSTED_KEY = "is_trusted";

    public static final String NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED = "identity_manager_notification_contact_trusted_details_updated";
    public static final String NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_CONTACT_PUBLISHED_DETAILS_TRUSTED_IDENTITY_DETAILS_KEY = "identity_details";

    public static final String NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED = "identity_manager_notification_contact_keycloak_managed_changed";
    public static final String NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_CONTACT_KEYCLOAK_MANAGED_CHANGED_KEYCLOAK_MANAGED_KEY = "keycloak_managed";

    public static final String NOTIFICATION_CONTACT_ACTIVE_CHANGED = "identity_manager_notification_contact_active_changed";
    public static final String NOTIFICATION_CONTACT_ACTIVE_CHANGED_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_CONTACT_ACTIVE_CHANGED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_CONTACT_ACTIVE_CHANGED_ACTIVE_KEY = "active";

    public static final String NOTIFICATION_CONTACT_REVOKED = "identity_manager_notification_contact_revoked";
    public static final String NOTIFICATION_CONTACT_REVOKED_CONTACT_IDENTITY_KEY = "contact_identity";
    public static final String NOTIFICATION_CONTACT_REVOKED_OWNED_IDENTITY_KEY = "owned_identity";

    public static final String NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS = "identity_manager_notification_new_group_published_details";
    public static final String NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS_GROUP_OWNER_AND_UID_KEY = "group_uid";
    public static final String NOTIFICATION_NEW_GROUP_PUBLISHED_DETAILS_OWNED_IDENTITY_KEY = "owned_identity";

    public static final String NOTIFICATION_GROUP_PHOTO_SET = "identity_manager_notification_group_photo_set";
    public static final String NOTIFICATION_GROUP_PHOTO_SET_GROUP_OWNER_AND_UID_KEY = "group_uid";
    public static final String NOTIFICATION_GROUP_PHOTO_SET_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_GROUP_PHOTO_SET_VERSION_KEY = "version";
    public static final String NOTIFICATION_GROUP_PHOTO_SET_IS_TRUSTED_KEY = "is_trusted";

    public static final String NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED = "identity_manager_notification_group_trusted_details_updated";
    public static final String NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_OWNER_AND_UID_KEY = "group_uid";
    public static final String NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_DETAILS_KEY = "group_details";

    public static final String NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED = "identity_manager_notification_group_published_details_updated";
    public static final String NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_OWNER_AND_UID_KEY = "group_uid"; // byte[]
    public static final String NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_DETAILS_KEY = "group_name"; // JsonGroupDetailsWithVersionAndPhoto

    public static final String NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED = "identity_manager_notification_latest_owned_identity_details_updated";
    public static final String NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_LATEST_OWNED_IDENTITY_DETAILS_UPDATED_HAS_UNPUBLISHED_KEY = "has_unpublished";  // boolean

    public static final String NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS = "identity_manager_notification_owned_identity_changed_active_status";
    public static final String NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_OWNED_IDENTITY_CHANGED_ACTIVE_STATUS_ACTIVE_KEY = "active"; // boolean

    public static final String NOTIFICATION_DATABASE_CONTENT_CHANGED = "identity_manager_notification_database_content_changed";

    public static final String NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED = "identity_manager_notification_server_user_data_can_be_deleted";
    public static final String NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_SERVER_USER_DATA_CAN_BE_DELETED_LABEL_KEY = "label"; // UID

    public static final String NOTIFICATION_CONTACT_CAPABILITIES_UPDATED = "identity_manager_notification_contact_capabilities_updated";
    public static final String NOTIFICATION_CONTACT_CAPABILITIES_UPDATED_CONTACT_IDENTITY_KEY = "contact_identity"; // Identity
    public static final String NOTIFICATION_CONTACT_CAPABILITIES_UPDATED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity

    public static final String NOTIFICATION_OWN_CAPABILITIES_UPDATED = "identity_manager_notification_own_capabilities_updated";
    public static final String NOTIFICATION_OWN_CAPABILITIES_UPDATED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity

    public static final String NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED = "identity_manager_notification_contact_one_to_one_changed";
    public static final String NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_CONTACT_IDENTITY_KEY = "contact_identity"; // Identity
    public static final String NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_CONTACT_ONE_TO_ONE_CHANGED_ONE_TO_ONE_KEY = "one_to_one"; // boolean

    public static final String NOTIFICATION_GROUP_V2_CREATED = "identity_manager_notification_group_v2_created";
    public static final String NOTIFICATION_GROUP_V2_CREATED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_GROUP_V2_CREATED_GROUP_IDENTIFIER_KEY = "group_identifier"; // GroupV2.Identifier
    public static final String NOTIFICATION_GROUP_V2_CREATED_NEW_GROUP_KEY = "new_group"; // boolean

    public static final String NOTIFICATION_GROUP_V2_DELETED = "identity_manager_notification_group_v2_deleted";
    public static final String NOTIFICATION_GROUP_V2_DELETED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_GROUP_V2_DELETED_GROUP_IDENTIFIER_KEY = "group_identifier"; // GroupV2.Identifier

    public static final String NOTIFICATION_GROUP_V2_FROZEN_CHANGED = "identity_manager_notification_group_v2_frozen_changed";
    public static final String NOTIFICATION_GROUP_V2_FROZEN_CHANGED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_GROUP_V2_FROZEN_CHANGED_GROUP_IDENTIFIER_KEY = "group_identifier"; // GroupV2.Identifier
    public static final String NOTIFICATION_GROUP_V2_FROZEN_CHANGED_FROZEN_KEY = "frozen"; // boolean
    public static final String NOTIFICATION_GROUP_V2_FROZEN_CHANGED_NEW_GROUP_KEY = "new_group"; // boolean

    public static final String NOTIFICATION_GROUP_V2_UPDATED = "identity_manager_notification_group_v2_updated";
    public static final String NOTIFICATION_GROUP_V2_UPDATED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_GROUP_V2_UPDATED_GROUP_IDENTIFIER_KEY = "group_identifier"; // GroupV2.Identifier
    public static final String NOTIFICATION_GROUP_V2_UPDATED_BY_ME_KEY = "by_me"; // boolean

    public static final String NOTIFICATION_GROUP_V2_PHOTO_UPDATED = "identity_manager_notification_group_v2_photo_updated";
    public static final String NOTIFICATION_GROUP_V2_PHOTO_UPDATED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_GROUP_V2_PHOTO_UPDATED_GROUP_IDENTIFIER_KEY = "group_identifier"; // GroupV2.Identifier

    public static final String NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS = "identity_manager_notification_keycloak_group_v_2_shared_settings";
    public static final String NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_GROUP_IDENTIFIER_KEY = "group_identifier"; // GroupV2.Identifier
    public static final String NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_SERIALIZED_SHARED_SETTINGS_KEY = "serialized_shared_settings"; // String, serialized JsonSharedSettings (may be null when removing shared settings)
    public static final String NOTIFICATION_KEYCLOAK_GROUP_V2_SHARED_SETTINGS_MODIFICATION_TIMESTAMP_KEY = "timestamp"; // long

    public static final String NOTIFICATION_NEW_KEYCLOAK_GROUP_V2_PUSH_TOPIC = "identity_manager_notification_new_keycloak_group_v2_push_topic";
    public static final String NOTIFICATION_NEW_KEYCLOAK_GROUP_V2_PUSH_TOPIC_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
}
