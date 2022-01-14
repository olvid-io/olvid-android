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

package io.olvid.engine.datatypes.notifications;


public abstract class DownloadNotifications {
    public static final String NOTIFICATION_MESSAGE_DECRYPTED = "network_fetch_notification_message_payload_set";
    public static final String NOTIFICATION_MESSAGE_DECRYPTED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_MESSAGE_DECRYPTED_UID_KEY = "uid"; // UID

    public static final String NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED = "network_fetch_notification_message_extended_payload_downloaded";
    public static final String NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_MESSAGE_UID_KEY = "message_uid"; // UID
    public static final String NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_EXTENDED_PAYLOAD_KEY = "extended_payload"; // byte[]

    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS = "network_fetch_notification_attachment_download_progress";
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_UID_KEY = "messageUid";
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY = "attachmentNumber";
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY = "progress";

    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED = "network_fetch_notification_attachment_download_finished";
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_MESSAGE_UID_KEY = "messageUid"; // UID
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_ATTACHMENT_NUMBER_KEY = "attachmentNumber";

    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED = "network_fetch_notification_attachment_download_was_paused";
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED_MESSAGE_UID_KEY = "messageUid"; // UID
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED_ATTACHMENT_NUMBER = "attachmentNumber";

    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED = "network_fetch_notification_attachment_download_failed";
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_MESSAGE_UID_KEY = "messageUid"; // UID
    public static final String NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_ATTACHMENT_NUMBER_KEY = "attachmentNumber";

    public static final String NOTIFICATION_SERVER_SESSION_CREATED = "network_fetch_notification_server_session_created";
    public static final String NOTIFICATION_SERVER_SESSION_CREATED_IDENTITY_KEY = "identity";
    public static final String NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_STATUS_KEY = "api_key_status"; // ServerSession.ApiKeyStatus
    public static final String NOTIFICATION_SERVER_SESSION_CREATED_PERMISSIONS_KEY = "permissions"; // List<ServerSession.Permission>
    public static final String NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_EXPIRATION_TIMESTAMP_KEY = "api_key_expiration_timestamp"; // long -> 0 means no expiration

    public static final String NOTIFICATION_API_KEY_REJECTED_BY_SERVER = "network_fetch_notification_api_key_rejected_by_server";
    public static final String NOTIFICATION_API_KEY_REJECTED_BY_SERVER_IDENTITY_KEY = "identity";

    public static final String NOTIFICATION_SERVER_POLLED = "network_fetch_notification_server_polled";
    public static final String NOTIFICATION_SERVER_POLLED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_SERVER_POLLED_SUCCESS_KEY = "success";
    
    public static final String NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED = "network_fetch_notification_signed_url_refreshed";
    public static final String NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED_MESSAGE_UID_KEY = "message_uid";
    public static final String NOTIFICATION_INBOX_ATTACHMENT_SIGNED_URL_REFRESHED_ATTACHMENT_NUMBER_KEY = "attachment_number";

    public static final String NOTIFICATION_RETURN_RECEIPT_RECEIVED = "network_fetch_notification_return_receipt_received";
    public static final String NOTIFICATION_RETURN_RECEIPT_RECEIVED_OWNED_IDENTITY_KEY = "bytes_owned_identity"; // Identity
    public static final String NOTIFICATION_RETURN_RECEIPT_RECEIVED_SERVER_UID_KEY = "server_uid"; // byte[]
    public static final String NOTIFICATION_RETURN_RECEIPT_RECEIVED_NONCE_KEY = "nonce"; // byte[]
    public static final String NOTIFICATION_RETURN_RECEIPT_RECEIVED_ENCRYPTED_PAYLOAD_KEY = "encrypted_payload"; // byte[]
    public static final String NOTIFICATION_RETURN_RECEIPT_RECEIVED_TIMESTAMP_KEY = "timestamp"; // long

    public static final String NOTIFICATION_PUSH_NOTIFICATION_REGISTERED = "network_fetch_notification_push_notification_registered";
    public static final String NOTIFICATION_PUSH_NOTIFICATION_REGISTERED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity

    public static final String NOTIFICATION_TURN_CREDENTIALS_RECEIVED = "network_fetch_notification_turn_credentials_recieved";
    public static final String NOTIFICATION_TURN_CREDENTIALS_RECEIVED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_TURN_CREDENTIALS_RECEIVED_CALL_UUID_KEY = "call_uuid"; // Uuid
    public static final String NOTIFICATION_TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY = "username1"; // String
    public static final String NOTIFICATION_TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY = "username2"; // String
    public static final String NOTIFICATION_TURN_CREDENTIALS_RECEIVED_USERNAME_2_KEY = "password1"; // String
    public static final String NOTIFICATION_TURN_CREDENTIALS_RECEIVED_PASSWORD_2_KEY = "password2"; // String
    public static final String NOTIFICATION_TURN_CREDENTIALS_RECEIVED_SERVERS_KEY = "servers"; // List<String>

    public static final String NOTIFICATION_TURN_CREDENTIALS_FAILED = "network_fetch_notification_turn_credentials_failed";
    public static final String NOTIFICATION_TURN_CREDENTIALS_FAILED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_TURN_CREDENTIALS_FAILED_CALL_UUID_KEY = "call_uuid"; // UUID
    public static final String NOTIFICATION_TURN_CREDENTIALS_FAILED_REASON_KEY = "reason"; // TurnCredentialsFailedReason

    public static final String NOTIFICATION_PING_LOST = "network_fetch_notification_ping_lost";
    public static final String NOTIFICATION_PING_RECEIVED = "network_fetch_notification_ping_received";
    public static final String NOTIFICATION_PING_RECEIVED_DELAY_KEY = "delay"; // long

    public enum TurnCredentialsFailedReason {
        PERMISSION_DENIED,
        BAD_SERVER_SESSION,
        CALLS_NOT_SUPPORTED_ON_SERVER,
        UNABLE_TO_CONTACT_SERVER
    }

    public static final String NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS = "network_fetch_notification_api_key_status_query_success";
    public static final String NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY = "api_key"; // UUID
    public static final String NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY = "api_key_status"; // ServerSession.ApiKeyStatus
    public static final String NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY = "permissions"; // List<ServerSession.Permission>
    public static final String NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY = "api_key_expiration_timestamp"; // long

    public static final String NOTIFICATION_API_KEY_STATUS_QUERY_FAILED = "network_fetch_notification_api_key_status_query_failed";
    public static final String NOTIFICATION_API_KEY_STATUS_QUERY_FAILED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY = "api_key"; // UUID

    public static final String NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS = "network_fetch_notification_free_trial_query_success";
    public static final String NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY = "available"; // boolean

    public static final String NOTIFICATION_FREE_TRIAL_QUERY_FAILED = "network_fetch_notification_free_trial_query_failed";
    public static final String NOTIFICATION_FREE_TRIAL_QUERY_FAILED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity

    public static final String NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS = "network_fetch_notification_free_trial_retrieve_success";
    public static final String NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS_OWNED_IDENTITY_KEY = "owned_identity"; // Identity

    public static final String NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED = "network_fetch_notification_free_trial_retrieve_failed";
    public static final String NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED_OWNED_IDENTITY_KEY = "owned_identity"; // Identity

    public static final String NOTIFICATION_VERIFY_RECEIPT_SUCCESS = "network_fetch_notification_verify_receipt_success";
    public static final String NOTIFICATION_VERIFY_RECEIPT_SUCCESS_OWNED_IDENTITY_KEY = "owned_identity"; // Identity
    public static final String NOTIFICATION_VERIFY_RECEIPT_SUCCESS_STORE_TOKEN_KEY = "store_token"; // String

    public static final String NOTIFICATION_WELL_KNOWN_CACHE_INITIALIZED = "network_fetch_notification_well_known_cache_initialized";

    public static final String NOTIFICATION_WELL_KNOWN_UPDATED = "network_fetch_notification_well_known_updated";
    public static final String NOTIFICATION_WELL_KNOWN_UPDATED_SERVER_KEY = "server"; // String
    public static final String NOTIFICATION_WELL_KNOWN_UPDATED_SERVER_CONFIG_KEY = "server_config"; // JsonWellKnownServerConfig
    public static final String NOTIFICATION_WELL_KNOWN_UPDATED_APP_INFO_KEY = "app_info"; // Map<String, Integer>

    public static final String NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS = "network_fetch_notification_well_known_download_success";
    public static final String NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY = "server"; // String
    public static final String NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY = "app_info"; // Map<String, Integer>

    public static final String NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED = "network_fetch_notification_well_known_download_failed";
    public static final String NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY = "server"; // String
}
