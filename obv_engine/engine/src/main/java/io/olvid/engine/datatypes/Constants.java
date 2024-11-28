/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.engine.datatypes;


import java.nio.charset.StandardCharsets;

public abstract class Constants {
    public static final int CURRENT_ENGINE_DB_SCHEMA_VERSION = 42;
    public static final int SERVER_API_VERSION = 18;
    public static final int CURRENT_BACKUP_JSON_VERSION = 0;

    // files / folders
    public static final String ENGINE_DB_FILENAME = "engine_db.sqlite";
    public static final String TMP_ENGINE_ENCRYPTED_DB_FILENAME = "engine_encrypted_db.sqlite";
    public static final String INBOUND_ATTACHMENTS_DIRECTORY = "inbound_attachments";
    public static final String IDENTITY_PHOTOS_DIRECTORY = "identity_photos";
    public static final String DOWNLOADED_USER_DATA_DIRECTORY = "downloaded_user_data";


    // API key statuses
    public static final int API_KEY_STATUS_VALID = 0;
    public static final int API_KEY_STATUS_UNKNOWN = 1;
    public static final int API_KEY_STATUS_LICENSES_EXHAUSTED = 2;
    public static final int API_KEY_STATUS_EXPIRED = 3;
    public static final int API_KEY_STATUS_OPEN_BETA_KEY = 4;
    public static final int API_KEY_STATUS_FREE_TRIAL_KEY = 5;
    public static final int API_KEY_STATUS_AWAITING_PAYMENT_GRACE_PERIOD = 6;
    public static final int API_KEY_STATUS_AWAITING_PAYMENT_ON_HOLD = 7;
    public static final int API_KEY_STATUS_FREE_TRIAL_KEY_EXPIRED = 8;

    // API key permission code (bits of a single permissions long)
    @SuppressWarnings("PointlessBitwiseExpression")
    public static final long API_KEY_PERMISSION_CALL = 1L << 0;
    public static final long API_KEY_PERMISSION_WEB_CLIENT = 1L << 1;
    public static final long API_KEY_PERMISSION_MULTI_DEVICE = 1L << 2;


    // full ratcheting thresholds
    public static final int THRESHOLD_NUMBER_OF_DECRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE = 20;
    public static final long THRESHOLD_TIME_INTERVAL_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE = 86_400_000L; // restart the full ratchet after 24 hours without response
    public static final int THRESHOLD_NUMBER_OF_ENCRYPTED_MESSAGES_PER_FULL_RATCHET = 500; // do a full ratchet after 500 messages
    public static final long FULL_RATCHET_TIME_INTERVAL_VALIDITY = 86_400_000L * 30; // do a full ratchet every month

    public static final int REPROVISIONING_THRESHOLD = 50;
    public static final long PROVISIONED_KEY_MATERIAL_EXPIRATION_DELAY = 86_400_000L * 2; // expire old ProvisionedKeyMaterial after 2 days

    public static final long OUTBOX_MESSAGE_MAX_SEND_DELAY = 86_400_000L * 30; // after 30 days without being able to upload a message, delete it
    public static final long PROTOCOL_RECEIVED_MESSAGE_EXPIRATION_DELAY = 86_400_000L * 15; // expire ReceivedMessage after 15 days
    public static final long SERVER_QUERY_EXPIRATION_DELAY = 86_400_000L * 15; // expire PendingServerQuery after 15 days

    public static final long USER_DATA_REFRESH_INTERVAL = 86_400_000L * 7; // 7 days
    public static final long GET_USER_DATA_LOCAL_FILE_LIFESPAN = 86_400_000L * 7; // 7 days
    public static final long WELL_KNOWN_REFRESH_INTERVAL = 3_600_000L * 6; // 6 hours

    // download message
    public static final long RELIST_DELAY = 10_000; // 10 seconds
    public static final long MINIMUM_URL_REFRESH_INTERVAL = 3_600_000L; // 1 hour

    // backups
    public static final long AUTOBACKUP_MAX_INTERVAL = 86_400_000L; // 1 day
    public static final long AUTOBACKUP_START_DELAY = 60_000L * 2; // 2 minutes
    // not used for now
//    public static final long PERIODIC_OWNED_DEVICE_SYNC_INTERVAL = 86_400_000L; // 1 day

    // pre keys
    public static final long PRE_KEY_VALIDITY_DURATION = 60 * 86_400_000L; // validity duration of newly generated pre-keys: 60 days
    public static final long PRE_KEY_RENEWAL_INTERVAL = 7 * 86_400_000L; // how frequently to refresh pre-keys on the server: 7 days
    public static final long PRE_KEY_CONSERVATION_DURATION = 60 * 86_400_000L; // how long to keep a pre-key after it expires: 60 days
    public static final long PRE_KEY_INBOX_NO_CONTACT_DURATION = 15 * 86_400_000L; // how long to keep a message in the inbox if it can be decrypted with a pre-key, but the sender is not a contact: 15 days

    // device discovery
    public static final long NO_DEVICE_CONTACT_DEVICE_DISCOVERY_INTERVAL = 3 * 86_400_000L;
    public static final long CONTACT_DEVICE_DISCOVERY_INTERVAL = 7 * 86_400_000L;
    public static final long OWNED_DEVICE_DISCOVERY_INTERVAL = 86_400_000L;
    public static final long CHANNEL_CREATION_PING_INTERVAL = 3 * 86_400_000L;

    public static final int SERVER_SESSION_NONCE_LENGTH = 32;
    public static final int SERVER_SESSION_CHALLENGE_LENGTH = 32;
    public static final int SERVER_SESSION_TOKEN_LENGTH = 32;

    public static final int RETURN_RECEIPT_NONCE_LENGTH = 16;

    public static final int GROUP_V2_INVITATION_NONCE_LENGTH = 16;
    public static final int GROUP_V2_LOCK_NONCE_LENGTH = 32;

    public static final int DEFAULT_ATTACHMENT_CHUNK_LENGTH = 4*2048*1024;
    public static final int MAX_MESSAGE_EXTENDED_CONTENT_LENGTH = 50 * 1024;
    public static final int MAX_UPLOAD_MESSAGE_BATCH_SIZE = 50;
    public static final int MAX_UPLOAD_RETURN_RECEIPT_BATCH_SIZE = 50;
    public static final int MAX_DELETE_MESSAGE_ON_SERVER_BATCH_SIZE = 50;

    public static final UID BROADCAST_UID = new UID(new byte[]{(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff});

    public static final byte[] ANDROID_STORE_ID = new byte[]{0x01};

    public static final int DEFAULT_NUMBER_OF_DIGITS_FOR_SAS = 4;
    public static final String EPHEMERAL_IDENTITY_SERVER = "ephemeral_fake_server";
    public static final String TRANSFER_WS_SERVER_URL = "wss://transfer.olvid.io";
    public static final int TRANSFER_MAX_PAYLOAD_SIZE = 10000;


    public static final long BASE_RESCHEDULING_TIME = 250L;
    public static final long WEBSOCKET_PING_INTERVAL_MILLIS = 20_000L;


    // Keycloak
    public static final long KEYCLOAK_SIGNATURE_VALIDITY_MILLIS = 60 * 86_400_000L;


    // prefixes for various types of signature

    public static final int SIGNATURE_PADDING_LENGTH = 16;

    public enum SignatureContext {
        SERVER_AUTHENTICATION,
        MUTUAL_SCAN,
        MUTUAL_INTRODUCTION,
        CHANNEL_CREATION,
        GROUP_ADMINISTRATORS_CHAIN,
        GROUP_BLOB,
        GROUP_LEAVE_NONCE,
        GROUP_LOCK_ON_SERVER,
        GROUP_DELETE_ON_SERVER,
        GROUP_JOIN_NONCE,
        GROUP_UPDATE_ON_SERVER,
        GROUP_KICK,
        OWNED_IDENTITY_DELETION,
        DEVICE_PRE_KEY,
        ENCRYPTION_WITH_PRE_KEY,
    }

    public static final byte[] SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX = "authentChallenge".getBytes(StandardCharsets.UTF_8);
    public static final byte[] MUTUAL_SCAN_SIGNATURE_CHALLENGE_PREFIX = "mutualScan".getBytes(StandardCharsets.UTF_8);
    public static final byte[] MUTUAL_INTRODUCTION_SIGNATURE_CHALLENGE_PREFIX = "mutualIntroduction".getBytes(StandardCharsets.UTF_8);
    public static final byte[] CHANNEL_CREATION_SIGNATURE_CHALLENGE_PREFIX = "channelCreation".getBytes(StandardCharsets.UTF_8);
    public static final byte[] GROUP_ADMINISTRATORS_CHAIN_SIGNATURE_CHALLENGE_PREFIX = "groupAdministratorsChain".getBytes(StandardCharsets.UTF_8);
    public static final byte[] GROUP_BLOB_SIGNATURE_CHALLENGE_PREFIX = "groupBlob".getBytes(StandardCharsets.UTF_8);
    public static final byte[] GROUP_LEAVE_NONCE_SIGNATURE_CHALLENGE_PREFIX = "groupLeave".getBytes(StandardCharsets.UTF_8);
    public static final byte[] GROUP_LOCK_SIGNATURE_CHALLENGE_PREFIX = "lockNonce".getBytes(StandardCharsets.UTF_8);
    public static final byte[] GROUP_DELETE_ON_SERVER_SIGNATURE_CHALLENGE_PREFIX = "deleteGroup".getBytes(StandardCharsets.UTF_8);
    public static final byte[] GROUP_JOIN_NONCE_SIGNATURE_CHALLENGE_PREFIX = "joinGroup".getBytes(StandardCharsets.UTF_8);
    public static final byte[] GROUP_UPDATE_ON_SERVER_SIGNATURE_CHALLENGE_PREFIX = "updateGroup".getBytes(StandardCharsets.UTF_8);
    public static final byte[] GROUP_KICK_SIGNATURE_CHALLENGE_PREFIX = "groupKick".getBytes(StandardCharsets.UTF_8);
    public static final byte[] OWNED_IDENTITY_DELETION_SIGNATURE_CHALLENGE_PREFIX = "ownedIdentityDeletion".getBytes(StandardCharsets.UTF_8);
    public static final byte[] DEVICE_PRE_KEY_SIGNATURE_CHALLENGE_PREFIX = "devicePreKey".getBytes(StandardCharsets.UTF_8);
    public static final byte[] ENCRYPTION_WITH_PRE_KEY_SIGNATURE_CHALLENGE_PREFIX = "encryptionWithPreKey".getBytes(StandardCharsets.UTF_8);

    public static byte[] getSignatureChallengePrefix(SignatureContext signatureContext) {
        switch (signatureContext) {
            case SERVER_AUTHENTICATION:
                return SERVER_AUTHENTICATION_SIGNATURE_CHALLENGE_PREFIX;
            case MUTUAL_SCAN:
                return MUTUAL_SCAN_SIGNATURE_CHALLENGE_PREFIX;
            case MUTUAL_INTRODUCTION:
                return MUTUAL_INTRODUCTION_SIGNATURE_CHALLENGE_PREFIX;
            case CHANNEL_CREATION:
                return CHANNEL_CREATION_SIGNATURE_CHALLENGE_PREFIX;
            case GROUP_ADMINISTRATORS_CHAIN:
                return GROUP_ADMINISTRATORS_CHAIN_SIGNATURE_CHALLENGE_PREFIX;
            case GROUP_BLOB:
                return GROUP_BLOB_SIGNATURE_CHALLENGE_PREFIX;
            case GROUP_LEAVE_NONCE:
                return GROUP_LEAVE_NONCE_SIGNATURE_CHALLENGE_PREFIX;
            case GROUP_LOCK_ON_SERVER:
                return GROUP_LOCK_SIGNATURE_CHALLENGE_PREFIX;
            case GROUP_DELETE_ON_SERVER:
                return GROUP_DELETE_ON_SERVER_SIGNATURE_CHALLENGE_PREFIX;
            case GROUP_JOIN_NONCE:
                return GROUP_JOIN_NONCE_SIGNATURE_CHALLENGE_PREFIX;
            case GROUP_UPDATE_ON_SERVER:
                return GROUP_UPDATE_ON_SERVER_SIGNATURE_CHALLENGE_PREFIX;
            case GROUP_KICK:
                return GROUP_KICK_SIGNATURE_CHALLENGE_PREFIX;
            case OWNED_IDENTITY_DELETION:
                return OWNED_IDENTITY_DELETION_SIGNATURE_CHALLENGE_PREFIX;
            case DEVICE_PRE_KEY:
                return DEVICE_PRE_KEY_SIGNATURE_CHALLENGE_PREFIX;
            case ENCRYPTION_WITH_PRE_KEY:
                return ENCRYPTION_WITH_PRE_KEY_SIGNATURE_CHALLENGE_PREFIX;
            default:
                return null;
        }
    }
}
