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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.DecryptedApplicationMessage;
import io.olvid.engine.datatypes.containers.OwnedIdentitySynchronizationStatus;
import io.olvid.engine.datatypes.containers.ReceivedAttachment;
import io.olvid.engine.datatypes.notifications.DownloadNotifications;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvAttachment;
import io.olvid.engine.engine.types.ObvMessage;
import io.olvid.engine.engine.types.ObvTurnCredentialsFailedReason;
import io.olvid.engine.networkfetch.databases.ServerSession;
import io.olvid.engine.notification.NotificationManager;

public class NotificationListenerDownloads implements NotificationListener {
    private final Engine engine;
    private long latestNetworkRestart = System.currentTimeMillis();

    public NotificationListenerDownloads(Engine engine) {
        this.engine = engine;
    }

    void registerToNotifications(NotificationManager notificationManager) {
        for (String notificationName : new String[]{
                DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED,
                DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED,
                DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED,
                DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED,
                DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS,
                DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED,
                DownloadNotifications.NOTIFICATION_SERVER_SESSION_EXISTS,
                DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED,
//                DownloadNotifications.NOTIFICATION_API_KEY_REJECTED_BY_SERVER,
                DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED,
                DownloadNotifications.NOTIFICATION_SERVER_POLLED,
                DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED,
                DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED,
                DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED,
                DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS,
                DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED,
                DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS,
                DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_FAILED,
                DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS,
                DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED,
                DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS,
                DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED,
                DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS,
                DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED,
                DownloadNotifications.NOTIFICATION_PING_LOST,
                DownloadNotifications.NOTIFICATION_PING_RECEIVED,
                DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED,
                DownloadNotifications.NOTIFICATION_WEBSOCKET_DETECTED_SOME_NETWORK,
                DownloadNotifications.NOTIFICATION_PUSH_TOPIC_NOTIFIED,
                DownloadNotifications.NOTIFICATION_PUSH_KEYCLOAK_UPDATE_REQUIRED,
                DownloadNotifications.NOTIFICATION_PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE,
                DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER,
        }) {
            notificationManager.addListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, Map<String, Object> userInfo) {
        switch (notificationName) {
            case DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED: {
                DecryptedApplicationMessage decryptedMessage = (DecryptedApplicationMessage) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED_MESSAGE_KEY);
                ReceivedAttachment[] receivedAttachments = (ReceivedAttachment[]) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_DECRYPTED_ATTACHMENTS_KEY);
                if (decryptedMessage == null || receivedAttachments == null) {
                    break;
                }

                ObvMessage message = new ObvMessage(decryptedMessage, receivedAttachments);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_MESSAGE_RECEIVED_MESSAGE_KEY, message);

                engine.postEngineNotification(EngineNotifications.NEW_MESSAGE_RECEIVED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_MESSAGE_UID_KEY);
                byte[] extendedPayload = (byte[]) userInfo.get(DownloadNotifications.NOTIFICATION_MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_EXTENDED_PAYLOAD_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED_EXTENDED_PAYLOAD_KEY, extendedPayload);
                engine.postEngineNotification(EngineNotifications.MESSAGE_EXTENDED_PAYLOAD_DOWNLOADED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_WAS_PAUSED:
                // nothing to do, attachment status is already updated in app
                break;
            case DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FAILED_ATTACHMENT_NUMBER_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED_ATTACHMENT_NUMBER_KEY, attachmentNumber);

                engine.postEngineNotification(EngineNotifications.ATTACHMENT_DOWNLOAD_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_FINISHED_ATTACHMENT_NUMBER_KEY);

                ObvAttachment attachment = ObvAttachment.create(engine.fetchManager, ownedIdentity, messageUid, attachmentNumber);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOADED_ATTACHMENT_KEY, attachment);

                engine.postEngineNotification(EngineNotifications.ATTACHMENT_DOWNLOADED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                float progress = (float) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY);
                Float speed = (Float) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_SPEED_BPS_KEY);
                Integer eta = (Integer) userInfo.get(DownloadNotifications.NOTIFICATION_ATTACHMENT_DOWNLOAD_PROGRESS_ETA_SECONDS_KEY);
                if (messageUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY, attachmentNumber);
                engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_PROGRESS_KEY, progress);
                if (speed != null && eta != null) {
                    engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_SPEED_BPS_KEY, speed);
                    engineInfo.put(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS_ETA_SECONDS_KEY, eta);
                }

                engine.postEngineNotification(EngineNotifications.ATTACHMENT_DOWNLOAD_PROGRESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_SERVER_SESSION_EXISTS:
            case DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_IDENTITY_KEY);
                ServerSession.ApiKeyStatus apiKeyStatus = (ServerSession.ApiKeyStatus) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_STATUS_KEY);
                //noinspection unchecked
                List<ServerSession.Permission> permissions = (List<ServerSession.Permission>) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_PERMISSIONS_KEY);
                long apiKeyExpirationTimestamp = (long) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_SESSION_CREATED_API_KEY_EXPIRATION_TIMESTAMP_KEY);
                if (ownedIdentity == null || apiKeyStatus == null || permissions == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                switch (apiKeyStatus) {
                    case VALID:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.VALID);
                        break;
                    case UNKNOWN:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.UNKNOWN);
                        break;
                    case LICENSES_EXHAUSTED:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.LICENSES_EXHAUSTED);
                        break;
                    case EXPIRED:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.EXPIRED);
                        break;
                    case OPEN_BETA_KEY:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.OPEN_BETA_KEY);
                        break;
                    case FREE_TRIAL_KEY:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY);
                        break;
                    case AWAITING_PAYMENT_GRACE_PERIOD:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_GRACE_PERIOD);
                        break;
                    case AWAITING_PAYMENT_ON_HOLD:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD);
                        break;
                    case FREE_TRIAL_KEY_EXPIRED:
                        engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY_EXPIRED);
                        break;
                }
                List<EngineAPI.ApiKeyPermission> enginePermissions = new ArrayList<>();
                for (ServerSession.Permission permission: permissions) {
                    switch (permission) {
                        case CALL:
                            enginePermissions.add(EngineAPI.ApiKeyPermission.CALL);
                            break;
                        case WEB_CLIENT:
                            enginePermissions.add(EngineAPI.ApiKeyPermission.WEB_CLIENT);
                            break;
                        case MULTI_DEVICE:
                            enginePermissions.add(EngineAPI.ApiKeyPermission.MULTI_DEVICE);
                            break;
                    }
                }
                engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_PERMISSIONS_KEY, enginePermissions);
                if (apiKeyExpirationTimestamp != 0) {
                    engineInfo.put(EngineNotifications.API_KEY_ACCEPTED_API_KEY_EXPIRATION_TIMESTAMP_KEY, apiKeyExpirationTimestamp);
                }
                engine.postEngineNotification(EngineNotifications.API_KEY_ACCEPTED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED_OWNED_IDENTITY_KEY);
                boolean user_initiated = (boolean) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_POLL_REQUESTED_USER_INITIATED_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.SERVER_POLL_REQUESTED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.SERVER_POLL_REQUESTED_USER_INITIATED_KEY, user_initiated);
                engine.postEngineNotification(EngineNotifications.SERVER_POLL_REQUESTED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_SERVER_POLLED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_POLLED_OWNED_IDENTITY_KEY);
                boolean success = (boolean) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_POLLED_SUCCESS_KEY);
                boolean truncated = (boolean) userInfo.get(DownloadNotifications.NOTIFICATION_SERVER_POLLED_TRUNCATED_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.SERVER_POLLED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.SERVER_POLLED_SUCCESS_KEY, success);
                engineInfo.put(EngineNotifications.SERVER_POLLED_TRUNCATED_KEY, truncated);
                engine.postEngineNotification(EngineNotifications.SERVER_POLLED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_OWNED_IDENTITY_KEY);
                byte[] serverUid = (byte[]) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_SERVER_UID_KEY);
                byte[] nonce = (byte[]) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_NONCE_KEY);
                byte[] encryptedPayload = (byte[]) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_ENCRYPTED_PAYLOAD_KEY);
                long timestamp = (long) userInfo.get(DownloadNotifications.NOTIFICATION_RETURN_RECEIPT_RECEIVED_TIMESTAMP_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_SERVER_UID_KEY, serverUid);
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_NONCE_KEY, nonce);
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_ENCRYPTED_PAYLOAD_KEY, encryptedPayload);
                engineInfo.put(EngineNotifications.RETURN_RECEIPT_RECEIVED_TIMESTAMP_KEY, timestamp);
                engine.postEngineNotification(EngineNotifications.RETURN_RECEIPT_RECEIVED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_OWNED_IDENTITY_KEY);
                UUID callUuid = (UUID) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_CALL_UUID_KEY);
                String username1 = (String) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY);
                String password1 = (String) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY);
                String username2 = (String) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_USERNAME_2_KEY);
                String password2 = (String) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_PASSWORD_2_KEY);
                //noinspection unchecked
                List<String> turnServers = (List<String>) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_RECEIVED_SERVERS_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_CALL_UUID_KEY, callUuid);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY, username1);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY, password1);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_USERNAME_2_KEY, username2);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_PASSWORD_2_KEY, password2);
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_RECEIVED_SERVERS_KEY, turnServers);
                engine.postEngineNotification(EngineNotifications.TURN_CREDENTIALS_RECEIVED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED_OWNED_IDENTITY_KEY);
                UUID callUuid = (UUID) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED_CALL_UUID_KEY);
                DownloadNotifications.TurnCredentialsFailedReason rfc = (DownloadNotifications.TurnCredentialsFailedReason) userInfo.get(DownloadNotifications.NOTIFICATION_TURN_CREDENTIALS_FAILED_REASON_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_CALL_UUID_KEY, callUuid);
                switch (rfc) {
                    case PERMISSION_DENIED:
                        engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY, ObvTurnCredentialsFailedReason.PERMISSION_DENIED);
                        break;
                    case BAD_SERVER_SESSION:
                        engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY, ObvTurnCredentialsFailedReason.BAD_SERVER_SESSION);
                        break;
                    case UNABLE_TO_CONTACT_SERVER:
                        engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY, ObvTurnCredentialsFailedReason.UNABLE_TO_CONTACT_SERVER);
                        break;
                    case CALLS_NOT_SUPPORTED_ON_SERVER:
                        engineInfo.put(EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY, ObvTurnCredentialsFailedReason.CALLS_NOT_SUPPORTED_ON_SERVER);
                        break;
                }
                engine.postEngineNotification(EngineNotifications.TURN_CREDENTIALS_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_OWNED_IDENTITY_KEY);
                UUID apiKey = (UUID) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY);
                ServerSession.ApiKeyStatus apiKeyStatus = (ServerSession.ApiKeyStatus) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY);
                //noinspection unchecked
                List<ServerSession.Permission> permissions = (List<ServerSession.Permission>) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY);
                long apiKeyExpirationTimestamp = (long) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY);
                if (ownedIdentity == null || apiKey == null || apiKeyStatus == null || permissions == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY, apiKey);
                switch (apiKeyStatus) {
                    case VALID:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.VALID);
                        break;
                    case UNKNOWN:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.UNKNOWN);
                        break;
                    case LICENSES_EXHAUSTED:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.LICENSES_EXHAUSTED);
                        break;
                    case EXPIRED:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.EXPIRED);
                        break;
                    case OPEN_BETA_KEY:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.OPEN_BETA_KEY);
                        break;
                    case FREE_TRIAL_KEY:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY);
                        break;
                    case AWAITING_PAYMENT_GRACE_PERIOD:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_GRACE_PERIOD);
                        break;
                    case AWAITING_PAYMENT_ON_HOLD:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD);
                        break;
                    case FREE_TRIAL_KEY_EXPIRED:
                        engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY, EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY_EXPIRED);
                        break;
                }
                List<EngineAPI.ApiKeyPermission> enginePermissions = new ArrayList<>();
                for (ServerSession.Permission permission: permissions) {
                    switch (permission) {
                        case CALL:
                            enginePermissions.add(EngineAPI.ApiKeyPermission.CALL);
                            break;
                        case WEB_CLIENT:
                            enginePermissions.add(EngineAPI.ApiKeyPermission.WEB_CLIENT);
                            break;
                        case MULTI_DEVICE:
                            enginePermissions.add(EngineAPI.ApiKeyPermission.MULTI_DEVICE);
                            break;
                    }
                }
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY, enginePermissions);
                if (apiKeyExpirationTimestamp != 0) {
                    engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY, apiKeyExpirationTimestamp);
                }
                engine.postEngineNotification(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED_OWNED_IDENTITY_KEY);
                UUID apiKey = (UUID) userInfo.get(DownloadNotifications.NOTIFICATION_API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY);
                if (ownedIdentity == null || apiKey == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY, apiKey);
                engine.postEngineNotification(EngineNotifications.API_KEY_STATUS_QUERY_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_OWNED_IDENTITY_KEY);
                boolean available = (boolean) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY, available);
                engine.postEngineNotification(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_QUERY_FAILED_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.FREE_TRIAL_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engine.postEngineNotification(EngineNotifications.FREE_TRIAL_QUERY_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_SUCCESS_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engine.postEngineNotification(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_FREE_TRIAL_RETRIEVE_FAILED_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engine.postEngineNotification(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS_OWNED_IDENTITY_KEY);
                String storeToken = (String) userInfo.get(DownloadNotifications.NOTIFICATION_VERIFY_RECEIPT_SUCCESS_STORE_TOKEN_KEY);
                if (ownedIdentity == null || storeToken == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.VERIFY_RECEIPT_SUCCESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.VERIFY_RECEIPT_SUCCESS_STORE_TOKEN_KEY, storeToken);
                engine.postEngineNotification(EngineNotifications.VERIFY_RECEIPT_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED: {
                String server = (String) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED_SERVER_KEY);
                //noinspection unchecked
                Map<String, Integer> appInfo = (Map<String, Integer>) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_UPDATED_APP_INFO_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY, server);
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY, appInfo);
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_UPDATED_KEY, true);
                engine.postEngineNotification(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS: {
                String server = (String) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY);
                //noinspection unchecked
                Map<String, Integer> appInfo = (Map<String, Integer>) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY, server);
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_APP_INFO_KEY, appInfo);
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_UPDATED_KEY, false);
                engine.postEngineNotification(EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED: {
                String server = (String) userInfo.get(DownloadNotifications.NOTIFICATION_WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY);

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY, server);
                engine.postEngineNotification(EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_PING_LOST: {
                HashMap<String, Object> engineInfo = new HashMap<>();
                engine.postEngineNotification(EngineNotifications.PING_LOST, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_PING_RECEIVED: {
                Long delay = (Long) userInfo.get(DownloadNotifications.NOTIFICATION_PING_RECEIVED_DELAY_KEY);
                if (delay == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PING_RECEIVED_DELAY_KEY, delay);

                engine.postEngineNotification(EngineNotifications.PING_RECEIVED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED: {
                Integer state = (Integer) userInfo.get(DownloadNotifications.NOTIFICATION_WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY);
                if (state == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.WEBSOCKET_CONNECTION_STATE_CHANGED_STATE_KEY, state);

                engine.postEngineNotification(EngineNotifications.WEBSOCKET_CONNECTION_STATE_CHANGED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_WEBSOCKET_DETECTED_SOME_NETWORK: {
                // this notification is only sent if websocket are monitoring network state. In case network is detected --> reschedule all network tasks
                if (latestNetworkRestart + 5_000 < System.currentTimeMillis()) {
                    latestNetworkRestart = System.currentTimeMillis();
                    Logger.i("Network detected (WebSocket connected), retrying all scheduled network jobs");
                    engine.retryScheduledNetworkTasks();
                }
                engine.postEngineNotification(EngineNotifications.WEBSOCKET_DETECTED_SOME_NETWORK, new HashMap<>());
                break;
            }
            case DownloadNotifications.NOTIFICATION_PUSH_TOPIC_NOTIFIED: {
                String topic = (String) userInfo.get(DownloadNotifications.NOTIFICATION_PUSH_TOPIC_NOTIFIED_TOPIC_KEY);
                if (topic == null) {
                    break;
                }
                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PUSH_TOPIC_NOTIFIED_TOPIC_KEY, topic);

                engine.postEngineNotification(EngineNotifications.PUSH_TOPIC_NOTIFIED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_PUSH_KEYCLOAK_UPDATE_REQUIRED: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_PUSH_KEYCLOAK_UPDATE_REQUIRED_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.KEYCLOAK_UPDATE_REQUIRED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());

                engine.postEngineNotification(EngineNotifications.KEYCLOAK_UPDATE_REQUIRED, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE_OWNED_IDENTITY_KEY);
                if (ownedIdentity == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());

                engine.postEngineNotification(EngineNotifications.PUSH_REGISTER_FAILED_BAD_DEVICE_UID_TO_REPLACE, engineInfo);
                break;
            }
            case DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER: {
                Identity ownedIdentity = (Identity) userInfo.get(DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_OWNED_IDENTITY_KEY);
                OwnedIdentitySynchronizationStatus status = (OwnedIdentitySynchronizationStatus) userInfo.get(DownloadNotifications.NOTIFICATION_OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_STATUS_KEY);
                if (ownedIdentity == null || status == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER_STATUS_KEY, status);

                engine.postEngineNotification(EngineNotifications.OWNED_IDENTITY_SYNCHRONIZING_WITH_SERVER, engineInfo);
                break;
            }
            default:
                Logger.w("Received notification " + notificationName + " but no handler is set.");
        }
    }
}
