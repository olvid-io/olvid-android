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

package io.olvid.engine.engine.types;

public abstract class EngineNotifications {
    public static final String UI_DIALOG_DELETED = "engine_notification_ui_dialog_deleted";
    public static final String UI_DIALOG_DELETED_UUID_KEY = "uuid"; // UUID

    public static final String UI_DIALOG = "engine_notification_ui_dialog";
    public static final String UI_DIALOG_UUID_KEY = "uuid"; // UUID
    public static final String UI_DIALOG_DIALOG_KEY = "dialog"; // ObvDialog
    public static final String UI_DIALOG_CREATION_TIMESTAMP_KEY = "creation_timestamp"; // long

    public static final String SERVER_POLL_REQUESTED = "engine_notification_server_poll_requested";
    public static final String SERVER_POLL_REQUESTED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String SERVER_POLL_REQUESTED_USER_INITIATED_KEY = "user_initiated"; // boolean

    public static final String SERVER_POLLED = "engine_notification_server_polled";
    public static final String SERVER_POLLED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String SERVER_POLLED_SUCCESS_KEY = "success"; // boolean
    public static final String SERVER_POLLED_TRUNCATED_KEY = "truncated"; // boolean --> if success == true, this indicates whether there are still some messages to list on the server

    public static final String NEW_MESSAGE_RECEIVED = "engine_notification_new_message_received";
    public static final String NEW_MESSAGE_RECEIVED_MESSAGE_KEY = "message"; // ObvMessage

    public static final String MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED = "engine_notification_message_extended_payload_downloaded";
    public static final String MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_MESSAGE_IDENTIFIER_KEY = "message_identifier"; // byte[]
    public static final String MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_EXTENDED_PAYLOAD_KEY = "extended_payload"; // byte[]

    public static final String ATTACHMENT_DOWNLOAD_PROGRESS = "engine_notification_download_attachment_progress";
    public static final String ATTACHMENT_DOWNLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY = "message_identifier"; // byte[]
    public static final String ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY = "attachment_number"; // int
    public static final String ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY = "progress"; // float
    public static final String ATTACHMENT_DOWNLOAD_PROGRESS_SPEED_BPS_KEY = "speed"; // float
    public static final String ATTACHMENT_DOWNLOAD_PROGRESS_ETA_SECONDS_KEY = "eta"; // int

    public static final String ATTACHMENT_DOWNLOADED = "engine_notification_attachment_downloaded";
    public static final String ATTACHMENT_DOWNLOADED_ATTACHMENT_KEY = "attachment"; // ObvAttachment

    public static final String ATTACHMENT_UPLOAD_PROGRESS = "engine_notification_upload_attachment_progress";
    public static final String ATTACHMENT_UPLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY = "message_identifier"; // byte[]
    public static final String ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY = "attachment_number"; // int
    public static final String ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY = "progress"; // float
    public static final String ATTACHMENT_UPLOAD_PROGRESS_SPEED_BPS_KEY = "speed"; // float
    public static final String ATTACHMENT_UPLOAD_PROGRESS_ETA_SECONDS_KEY = "eta"; // int

    public static final String ATTACHMENT_UPLOADED = "engine_notification_attachment_uploaded";
    public static final String ATTACHMENT_UPLOADED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String ATTACHMENT_UPLOADED_MESSAGE_IDENTIFIER_KEY = "message_identifier"; // byte[] (message UID)
    public static final String ATTACHMENT_UPLOADED_ATTACHMENT_NUMBER_KEY = "attachment_number"; // int

    public static final String ATTACHMENT_UPLOAD_CANCELLED = "engine_notification_attachment_upload_cancelled";
    public static final String ATTACHMENT_UPLOAD_CANCELLED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String ATTACHMENT_UPLOAD_CANCELLED_MESSAGE_IDENTIFIER_KEY = "message_identifier"; // byte[] (message UID)
    public static final String ATTACHMENT_UPLOAD_CANCELLED_ATTACHMENT_NUMBER_KEY = "attachment_number"; // int

    public static final String ATTACHMENT_DOWNLOAD_FAILED = "engine_notification_attachment_failed";
    public static final String ATTACHMENT_DOWNLOAD_FAILED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String ATTACHMENT_DOWNLOAD_FAILED_MESSAGE_IDENTIFIER_KEY = "message_identifier"; // byte[] (message UID)
    public static final String ATTACHMENT_DOWNLOAD_FAILED_ATTACHMENT_NUMBER_KEY = "attachment_number"; // int

    public static final String MESSAGE_UPLOADED = "engine_notification_message_uploaded";
    public static final String MESSAGE_UPLOADED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String MESSAGE_UPLOADED_IDENTIFIER_KEY = "identifier"; // byte[] (message UID)
    public static final String MESSAGE_UPLOADED_TIMESTAMP_FROM_SERVER = "timestamp_from_server";

    public static final String MESSAGE_UPLOAD_FAILED = "engine_notification_message_upload_failed";
    public static final String MESSAGE_UPLOAD_FAILED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String MESSAGE_UPLOAD_FAILED_IDENTIFIER_KEY = "identifier"; // byte[] (message UID)

    public static final String NEW_CONTACT = "engine_notification_new_contact";
    public static final String NEW_CONTACT_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String NEW_CONTACT_CONTACT_IDENTITY_KEY = "contact_identity"; // ObvIdentity
    public static final String NEW_CONTACT_ONE_TO_ONE_KEY = "one_to_one"; // boolean
    public static final String NEW_CONTACT_TRUST_LEVEL_KEY = "trust_level"; // int
    public static final String NEW_CONTACT_HAS_UNTRUSTED_PUBLISHED_DETAILS_KEY = "has_untrusted_published_details"; // boolean

    public static final String CONTACT_DELETED = "engine_notification_contact_deleted";
    public static final String CONTACT_DELETED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_DELETED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]

    public static final String CONTACT_DEVICES_UPDATED = "engine_notification_contact_devices_updated";
    public static final String CONTACT_DEVICES_UPDATED_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String CONTACT_DEVICES_UPDATED_CONTACT_IDENTITY_KEY = "contact_identity"; // byte[]

    public static final String CHANNEL_CONFIRMED_OR_DELETED = "engine_notification_channel_confirmed_or_deleted";
    public static final String CHANNEL_CONFIRMED_OR_DELETED_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String CHANNEL_CONFIRMED_OR_DELETED_CONTACT_IDENTITY_KEY = "contact_identity"; // byte[]

    public static final String GROUP_CREATED = "engine_notification_group_created";
    public static final String GROUP_CREATED_GROUP_KEY = "group"; // ObvGroup
    public static final String GROUP_CREATED_HAS_MULTIPLE_DETAILS_KEY = "has_multiple_details"; // boolean
    public static final String GROUP_CREATED_PHOTO_URL_KEY = "photo_url"; // String
    public static final String GROUP_CREATED_ON_OTHER_DEVICE_KEY = "on_other_device"; // boolean --> true if I am the group owner and the group was created on another device


    public static final String GROUP_DELETED = "engine_notification_group_deleted";
    public static final String GROUP_DELETED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String GROUP_DELETED_BYTES_GROUP_OWNER_AND_UID_KEY = "bytes_group_owner_and_uid"; // byte[]


    public static final String GROUP_MEMBER_ADDED = "engine_notification_group_member_added";
    public static final String GROUP_MEMBER_ADDED_BYTES_GROUP_UID_KEY = "group_uid"; // byte[]
    public static final String GROUP_MEMBER_ADDED_BYTES_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String GROUP_MEMBER_ADDED_BYTES_CONTACT_IDENTITY_KEY = "contact_identity"; // byte[]

    public static final String GROUP_MEMBER_REMOVED = "engine_notification_group_member_removed";
    public static final String GROUP_MEMBER_REMOVED_BYTES_GROUP_UID_KEY = "bytes_group_uid"; // byte[]
    public static final String GROUP_MEMBER_REMOVED_BYTES_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String GROUP_MEMBER_REMOVED_BYTES_CONTACT_IDENTITY_KEY = "contact_identity"; // byte[]

    public static final String PENDING_GROUP_MEMBER_ADDED = "engine_notification_pending_group_member_added";
    public static final String PENDING_GROUP_MEMBER_ADDED_BYTES_GROUP_UID_KEY = "bytes_group_uid"; // byte[]
    public static final String PENDING_GROUP_MEMBER_ADDED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String PENDING_GROUP_MEMBER_ADDED_CONTACT_IDENTITY_KEY = "contact_identity"; // ObvIdentity

    public static final String PENDING_GROUP_MEMBER_REMOVED = "engine_notification_pending_group_member_removed";
    public static final String PENDING_GROUP_MEMBER_REMOVED_BYTES_GROUP_UID_KEY = "bytes_group_uid"; // byte[]
    public static final String PENDING_GROUP_MEMBER_REMOVED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String PENDING_GROUP_MEMBER_REMOVED_CONTACT_IDENTITY_KEY = "contact_identity"; // ObvIdentity

    public static final String API_KEY_ACCEPTED = "engine_notification_api_key_accepted";
    public static final String API_KEY_ACCEPTED_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String API_KEY_ACCEPTED_API_KEY_STATUS_KEY = "api_key_status"; // EngineApi.ApiKeyStatus
    public static final String API_KEY_ACCEPTED_PERMISSIONS_KEY = "permissions"; // List<EngineApi.Permission>
    public static final String API_KEY_ACCEPTED_API_KEY_EXPIRATION_TIMESTAMP_KEY = "api_key_expiration_timestamp"; // long

    public static final String OWNED_IDENTITY_LIST_UPDATED = "engine_notification_owned_identity_list_updated";

    public static final String OWNED_IDENTITY_DETAILS_CHANGED = "engine_notification_owned_identity_display_name_changed";
    public static final String OWNED_IDENTITY_DETAILS_CHANGED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String OWNED_IDENTITY_DETAILS_CHANGED_IDENTITY_DETAILS_KEY = "display_name"; // JsonIdentityDetails
    public static final String OWNED_IDENTITY_DETAILS_CHANGED_PHOTO_URL_KEY = "photo_url"; // String

    public static final String NEW_CONTACT_PUBLISHED_DETAILS = "engine_notification_new_contact_published_details";
    public static final String NEW_CONTACT_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String NEW_CONTACT_PUBLISHED_DETAILS_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]

    public static final String CONTACT_PUBLISHED_DETAILS_TRUSTED = "engine_notification_contact_published_details_trusted";
    public static final String CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_PUBLISHED_DETAILS_TRUSTED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String CONTACT_PUBLISHED_DETAILS_TRUSTED_IDENTITY_DETAILS_KEY = "identity_details"; // JsonIdentityDetailsWithVersionAndPhoto

    public static final String CONTACT_KEYCLOAK_MANAGED_CHANGED = "engine_notification_contact_keycloak_managed_changed";
    public static final String CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_KEYCLOAK_MANAGED_CHANGED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String CONTACT_KEYCLOAK_MANAGED_CHANGED_KEYCLOAK_MANAGED_KEY = "keycloak_managed"; // boolean

    public static final String CONTACT_ACTIVE_CHANGED = "engine_notification_contact_active_changed";
    public static final String CONTACT_ACTIVE_CHANGED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_ACTIVE_CHANGED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String CONTACT_ACTIVE_CHANGED_ACTIVE_KEY = "active"; // boolean

    public static final String CONTACT_REVOKED = "engine_notification_contact_revoked";
    public static final String CONTACT_REVOKED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_REVOKED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]

    public static final String NEW_CONTACT_PHOTO = "engine_notification_new_contact_photo";
    public static final String NEW_CONTACT_PHOTO_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String NEW_CONTACT_PHOTO_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String NEW_CONTACT_PHOTO_VERSION_KEY = "version"; // int
    public static final String NEW_CONTACT_PHOTO_IS_TRUSTED_KEY = "is_trusted"; // boolean

    public static final String NEW_GROUP_PHOTO = "engine_notification_new_group_photo";
    public static final String NEW_GROUP_PHOTO_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String NEW_GROUP_PHOTO_BYTES_GROUP_OWNER_AND_UID_KEY = "bytes_group_uid"; // byte[]
    public static final String NEW_GROUP_PHOTO_VERSION_KEY = "version"; // int
    public static final String NEW_GROUP_PHOTO_IS_TRUSTED_KEY = "is_trusted"; // boolean

    public static final String GROUP_PUBLISHED_DETAILS_UPDATED = "engine_notification_group_published_details_updated";
    public static final String GROUP_PUBLISHED_DETAILS_UPDATED_BYTES_GROUP_UID_KEY = "bytes_group_uid"; // byte[]
    public static final String GROUP_PUBLISHED_DETAILS_UPDATED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String GROUP_PUBLISHED_DETAILS_UPDATED_GROUP_DETAILS_KEY = "group_details"; // JsonGroupDetailsWithVersionAndPhoto

    public static final String GROUP_PUBLISHED_DETAILS_TRUSTED = "engine_notification_group_published_details_trusted";
    public static final String GROUP_PUBLISHED_DETAILS_TRUSTED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String GROUP_PUBLISHED_DETAILS_TRUSTED_BYTES_GROUP_UID_KEY = "bytes_group_uid"; // byte[]
    public static final String GROUP_PUBLISHED_DETAILS_TRUSTED_GROUP_DETAILS_KEY = "group_details"; // JsonGroupDetailsWithVersionAndPhoto

    public static final String NEW_GROUP_PUBLISHED_DETAILS = "engine_notification_new_group_published_details";
    public static final String NEW_GROUP_PUBLISHED_DETAILS_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String NEW_GROUP_PUBLISHED_DETAILS_BYTES_GROUP_OWNER_AND_UID_KEY = "bytes_group_uid"; // byte[]

    public static final String PENDING_GROUP_MEMBER_DECLINE_TOGGLED = "engine_notification_pending_group_member_decline_toggled";
    public static final String PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_GROUP_UID_KEY = "bytes_group_uid"; // byte[]
    public static final String PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String PENDING_GROUP_MEMBER_DECLINE_TOGGLED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String PENDING_GROUP_MEMBER_DECLINE_TOGGLED_DECLINED_KEY = "declined"; // boolean

    public static final String OWNED_IDENTITY_LATEST_DETAILS_UPDATED = "engine_notification_owned_identity_latest_details_updated";
    public static final String OWNED_IDENTITY_LATEST_DETAILS_UPDATED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String OWNED_IDENTITY_LATEST_DETAILS_UPDATED_HAS_UNPUBLISHED_KEY = "has_unpublished"; // boolean

    public static final String OWNED_IDENTITY_ACTIVE_STATUS_CHANGED = "engine_notification_owned_identity_changed_active_status";
    public static final String OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String OWNED_IDENTITY_ACTIVE_STATUS_CHANGED_ACTIVE_KEY = "active"; // boolean

    public static final String RETURN_RECEIPT_RECEIVED = "engine_notification_return_receipt_received";
    public static final String RETURN_RECEIPT_RECEIVED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String RETURN_RECEIPT_RECEIVED_SERVER_UID_KEY = "server_uid"; // byte[]
    public static final String RETURN_RECEIPT_RECEIVED_NONCE_KEY = "nonce"; // byte[]
    public static final String RETURN_RECEIPT_RECEIVED_ENCRYPTED_PAYLOAD_KEY = "encrypted_payload"; // byte[]
    public static final String RETURN_RECEIPT_RECEIVED_TIMESTAMP_KEY = "timestamp"; // long

    public static final String NEW_BACKUP_SEED_GENERATED = "engine_notification_new_backup_seed_generated";
    public static final String NEW_BACKUP_SEED_GENERATED_SEED_KEY = "seed"; // String

    public static final String BACKUP_SEED_GENERATION_FAILED = "engine_notification_backup_seed_generation_failed";

    public static final String BACKUP_KEY_VERIFICATION_SUCCESSFUL = "engine_notification_backup_key_verification_successful";

    public static final String BACKUP_FOR_EXPORT_FINISHED = "engine_notification_backup_for_export_finished";
    public static final String BACKUP_FOR_EXPORT_FINISHED_BYTES_BACKUP_KEY_UID_KEY = "backup_key_uid"; // byte[]
    public static final String BACKUP_FOR_EXPORT_FINISHED_VERSION_KEY = "version"; // int
    public static final String BACKUP_FOR_EXPORT_FINISHED_ENCRYPTED_CONTENT_KEY = "encrypted_content"; // byte[]

    public static final String BACKUP_FINISHED = "engine_notification_backup_finished";
    public static final String BACKUP_FINISHED_BYTES_BACKUP_KEY_UID_KEY = "backup_key_uid"; // byte[]
    public static final String BACKUP_FINISHED_VERSION_KEY = "version"; // int
    public static final String BACKUP_FINISHED_ENCRYPTED_CONTENT_KEY = "encrypted_content"; // byte[]

    public static final String BACKUP_FOR_EXPORT_FAILED = "engine_notification_backup_for_export_failed";

    public static final String TURN_CREDENTIALS_RECEIVED = "engine_notification_turn_credentials_received";
    public static final String TURN_CREDENTIALS_RECEIVED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String TURN_CREDENTIALS_RECEIVED_CALL_UUID_KEY = "call_uuid"; // Uuid
    public static final String TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY = "username1"; // String
    public static final String TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY = "username2"; // String
    public static final String TURN_CREDENTIALS_RECEIVED_USERNAME_2_KEY = "password1"; // String
    public static final String TURN_CREDENTIALS_RECEIVED_PASSWORD_2_KEY = "password2"; // String
    public static final String TURN_CREDENTIALS_RECEIVED_SERVERS_KEY = "servers"; // List<String>

    public static final String TURN_CREDENTIALS_FAILED = "engine_notification_turn_credentials_failed";
    public static final String TURN_CREDENTIALS_FAILED_OWNED_IDENTITY_KEY = "owned_identity"; // byte[]
    public static final String TURN_CREDENTIALS_FAILED_CALL_UUID_KEY = "call_uuid"; // UUID
    public static final String TURN_CREDENTIALS_FAILED_REASON_KEY = "reason"; // ObvTurnCredentialsFailedReason

    public static final String API_KEY_STATUS_QUERY_SUCCESS = "engine_notification_api_key_status_query_success";
    public static final String API_KEY_STATUS_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY = "api_key"; // UUID
    public static final String API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY = "api_key_status"; // EngineAPI.ApiKeyStatus
    public static final String API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY = "permissions"; // List<EngineAPI.Permission>
    public static final String API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY = "api_key_expiration_timestamp"; // long

    public static final String API_KEY_STATUS_QUERY_FAILED = "engine_notification_api_key_status_query_failed";
    public static final String API_KEY_STATUS_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY = "api_key"; // UUID

    public static final String FREE_TRIAL_QUERY_SUCCESS = "engine_notification_free_trial_query_success";
    public static final String FREE_TRIAL_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY = "available"; // boolean

    public static final String FREE_TRIAL_QUERY_FAILED = "engine_notification_free_trial_query_failed";
    public static final String FREE_TRIAL_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]

    public static final String FREE_TRIAL_RETRIEVE_SUCCESS = "engine_notification_retrieve_query_success";
    public static final String FREE_TRIAL_RETRIEVE_SUCCESS_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]

    public static final String FREE_TRIAL_RETRIEVE_FAILED = "engine_notification_retrieve_query_failed";
    public static final String FREE_TRIAL_RETRIEVE_FAILED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]

    public static final String VERIFY_RECEIPT_SUCCESS = "engine_notification_verify_receipt_success";
    public static final String VERIFY_RECEIPT_SUCCESS_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String VERIFY_RECEIPT_SUCCESS_STORE_TOKEN_KEY = "store_token"; // String

    public static final String WELL_KNOWN_DOWNLOAD_SUCCESS = "engine_notification_well_known_download_success";
    public static final String WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY = "server"; // String
    public static final String WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY = "app_info"; // Map<String, Integer>
    public static final String WELL_KNOWN_DOWNLOAD_SUCCESS_UPDATED_KEY = "updated"; // boolean

    public static final String WELL_KNOWN_DOWNLOAD_FAILED = "engine_notification_well_known_download_failed";
    public static final String WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY = "server"; // String

    public static final String MUTUAL_SCAN_CONTACT_ADDED = "engine_notification_mutual_scan_contact_added";
    public static final String MUTUAL_SCAN_CONTACT_ADDED_BYTES_OWNED_IDENTITIY_KEY = "bytes_owned_identity"; // byte[]
    public static final String MUTUAL_SCAN_CONTACT_ADDED_BYTES_CONTACT_IDENTITIY_KEY = "bytes_contact_identity"; // byte[]
    public static final String MUTUAL_SCAN_CONTACT_ADDED_SIGNATURE_KEY = "signature"; // byte[]

    public static final String APP_BACKUP_REQUESTED = "engine_notification_app_backup_requested";
    public static final String APP_BACKUP_REQUESTED_BYTES_BACKUP_KEY_UID_KEY = "bytes_backup_key_uid"; // byte[]
    public static final String APP_BACKUP_REQUESTED_VERSION_KEY = "version"; // int

    public static final String ENGINE_BACKUP_RESTORATION_FINISHED = "engine_notification_engine_backup_restoration_finished";

    public static final String ENGINE_SNAPSHOT_RESTORATION_FINISHED = "engine_notification_engine_snapshot_restoration_finished";

    public static final String PING_LOST = "engine_notification_ping_lost";

    public static final String PING_RECEIVED = "engine_notification_ping_received";
    public static final String PING_RECEIVED_DELAY_KEY = "delay"; // long (in milliseconds)

    public static final String WEBSOCKET_CONNECTION_STATE_CHANGED = "engine_notification_websocket_connection_state_changed";
    public static final String WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY = "state"; // int

    public static final String WEBSOCKET_DETECTED_SOME_NETWORK = "engine_notification_websocket_detected_some_network";

    public static final String CONTACT_CAPABILITIES_UPDATED = "engine_notification_contact_capabilities_updated"; // List<ObvCapabilities>
    public static final String CONTACT_CAPABILITIES_UPDATED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_CAPABILITIES_UPDATED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String CONTACT_CAPABILITIES_UPDATED_CAPABILITIES = "capabilities"; // List<ObvCapabilities>

    public static final String OWN_CAPABILITIES_UPDATED = "engine_notification_own_capabilities_updated"; // List<ObvCapabilities>
    public static final String OWN_CAPABILITIES_UPDATED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String OWN_CAPABILITIES_UPDATED_CAPABILITIES = "capabilities"; // List<ObvCapabilities>

    public static final String CONTACT_ONE_TO_ONE_CHANGED = "engine_notification_contact_one_to_one_changed";
    public static final String CONTACT_ONE_TO_ONE_CHANGED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_ONE_TO_ONE_CHANGED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String CONTACT_ONE_TO_ONE_CHANGED_ONE_TO_ONE_KEY = "one_to_one"; // boolean

    public static final String CONTACT_RECENTLY_ONLINE_CHANGED = "engine_notification_contact_recently_online_changed";
    public static final String CONTACT_RECENTLY_ONLINE_CHANGED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_RECENTLY_ONLINE_CHANGED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String CONTACT_RECENTLY_ONLINE_CHANGED_RECENTLY_ONLINE_KEY = "recently_online"; // boolean

    public static final String CONTACT_TRUST_LEVEL_INCREASED = "engine_notification_contact_trust_level_increased";
    public static final String CONTACT_TRUST_LEVEL_INCREASED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_TRUST_LEVEL_INCREASED_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String CONTACT_TRUST_LEVEL_INCREASED_TRUST_LEVEL_KEY = "trust_level"; // int

    public static final String GROUP_V2_CREATED_OR_UPDATED = "engine_notification_group_v2_created_or_updated";
    public static final String GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY = "group"; // ObvGroupV2
    public static final String GROUP_V2_CREATED_OR_UPDATED_NEW_GROUP_KEY = "new_group"; // boolean --> if true, the group was created by be (as opposed to joined groups created by someone else)
    public static final String GROUP_V2_CREATED_OR_UPDATED_BY_ME_KEY = "by_me"; // boolean
    public static final String GROUP_V2_CREATED_OR_UPDATED_CREATED_ON_OTHER_DEVICE = "created_on_other_device"; // boolean --> only meaningful for new groups created by be ("new_group" == true). true if created on another device, false if created on this device

    public static final String GROUP_V2_PHOTO_CHANGED = "engine_notification_group_v2_photo_changed";
    public static final String GROUP_V2_PHOTO_CHANGED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String GROUP_V2_PHOTO_CHANGED_BYTES_GROUP_IDENTIFIER_KEY = "bytes_group_identifier"; // byte[]

    public static final String GROUP_V2_UPDATE_IN_PROGRESS_CHANGED = "engine_notification_group_v2_update_in_progress_changed";
    public static final String GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_GROUP_IDENTIFIER_KEY = "bytes_group_identifier"; // byte[]
    public static final String GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_UPDATING_KEY = "updating"; // boolean
    public static final String GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_CREATING_KEY = "creating"; // boolean

    public static final String GROUP_V2_DELETED = "engine_notification_group_v2_deleted";
    public static final String GROUP_V2_DELETED_BYTES_OWNED_IDENTITY = "bytes_owned_identity"; // byte[]
    public static final String GROUP_V2_DELETED_BYTES_GROUP_IDENTIFIER_KEY = "bytes_group_identifier"; // byte[]

    public static final String GROUP_V2_UPDATE_FAILED = "engine_notification_group_v2_update_failed";
    public static final String GROUP_V2_UPDATE_FAILED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String GROUP_V2_UPDATE_FAILED_BYTES_GROUP_IDENTIFIER_KEY = "bytes_group_identifier"; // byte[]
    public static final String GROUP_V2_UPDATE_FAILED_ERROR_KEY = "error"; // boolean

    public static final String PUSH_TOPIC_NOTIFIED = "engine_notification_push_topic_notified";
    public static final String PUSH_TOPIC_NOTIFIED_TOPIC_KEY = "topic"; // String

    public static final String KEYCLOAK_UPDATE_REQUIRED = "engine_notification_keycloak_update_required";
    public static final String KEYCLOAK_UPDATE_REQUIRED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]

    public static final String KEYCLOAK_GROUP_V2_SHARED_SETTINGS = "engine_notification_keycloak_group_v2_shared_settings";
    public static final String KEYCLOAK_GROUP_V2_SHARED_SETTINGS_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String KEYCLOAK_GROUP_V2_SHARED_SETTINGS_BYTES_GROUP_IDENTIFIER_KEY = "bytes_group_identifier"; // byte[]
    public static final String KEYCLOAK_GROUP_V2_SHARED_SETTINGS_SHARED_SETTINGS_KEY = "shared_settings"; // String, serialized JsonSharedSettings
    public static final String KEYCLOAK_GROUP_V2_SHARED_SETTINGS_MODIFICATION_TIMESTAMP_KEY = "timestamp"; // long

    public static final String OWNED_IDENTITY_DELETED_FROM_ANOTHER_DEVICE = "engine_notification_owned_identity_deleted_from_another_device";
    public static final String OWNED_IDENTITY_DELETED_FROM_ANOTHER_DEVICE_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]

    public static final String OWNED_IDENTITY_DEVICE_LIST_CHANGED = "engine_notification_owned_identity_device_list_changed";
    public static final String OWNED_IDENTITY_DEVICE_LIST_CHANGED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]

    public static final String KEYCLOAK_SYNCHRONIZATION_REQUIRED = "engine_notification_keycloak_synchronization_required";
    public static final String KEYCLOAK_SYNCHRONIZATION_REQUIRED_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]

    public static final String CONTACT_INTRODUCTION_INVITATION_SENT = "engine_notification_contact_introduction_invitation_sent";
    public static final String CONTACT_INTRODUCTION_INVITATION_SENT_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_INTRODUCTION_INVITATION_SENT_BYTES_CONTACT_IDENTITY_A_KEY = "bytes_contact_identity_a"; // byte[]
    public static final String CONTACT_INTRODUCTION_INVITATION_SENT_BYTES_CONTACT_IDENTITY_B_KEY = "bytes_contact_identity_b"; // byte[]

    public static final String CONTACT_INTRODUCTION_INVITATION_RESPONSE = "engine_notification_contact_introduction_invitation_response";
    public static final String CONTACT_INTRODUCTION_INVITATION_RESPONSE_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String CONTACT_INTRODUCTION_INVITATION_RESPONSE_BYTES_MEDIATOR_IDENTITY_KEY = "bytes_mediator_identity"; // byte[]
    public static final String CONTACT_INTRODUCTION_INVITATION_RESPONSE_BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity"; // byte[]
    public static final String CONTACT_INTRODUCTION_INVITATION_RESPONSE_CONTACT_SERIALIZED_DETAILS_KEY = "contact_serialized_Details"; // String
    public static final String CONTACT_INTRODUCTION_INVITATION_RESPONSE_ACCEPTED_KEY = "accepted"; // boolean

    public static final String PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE = "engine_notification_push_register_failed_bad_device_uid_to_replace";
    public static final String PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]

    public static final String OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER = "engine_notification_owned_identity_synchronizing_with_server";
    public static final String OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // byte[]
    public static final String OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_STATUS_KEY = "status"; // OwnedIdentitySynchronizationStatus
}
