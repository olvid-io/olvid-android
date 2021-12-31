/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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


public abstract class Constants {
    public static final int CURRENT_ENGINE_DB_SCHEMA_VERSION = 26;
    public static final int SERVER_API_VERSION = 13;
    public static final int CURRENT_BACKUP_JSON_VERSION = 0;

    // files / folders
    public static final String ENGINE_DB_FILENAME = "engine_db.sqlite";
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


    // full ratcheting thresholds
    public static final int THRESHOLD_NUMBER_OF_DECRYPTED_MESSAGES_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE = 20;
    public static final long THRESHOLD_TIME_INTERVAL_SINCE_LAST_FULL_RATCHET_SENT_MESSAGE = 7_200_000L; // restart the full ratchet after 2 hours without response
    public static final int THRESHOLD_NUMBER_OF_ENCRYPTED_MESSAGES_PER_FULL_RATCHET = 100; // do a full ratchet after 100 messages
    public static final long FULL_RATCHET_TIME_INTERVAL_VALIDITY = 86_400_000L * 7; // do a full ratchet every week

    public static final int REPROVISIONING_THRESHOLD = 50;
    public static final long PROVISIONED_KEY_MATERIAL_EXPIRATION_DELAY = 86_400_000L * 2; // expire old ProvisionedKeyMaterial after 2 days

    public static final long PROTOCOL_RECEIVED_MESSAGE_EXPIRATION_DELAY = 86_400_000L * 15; // expire ReceivedMessage after 15 days

    public static final long USER_DATA_REFRESH_INTERVAL = 86_400_000L * 7;
    public static final long GET_USER_DATA_LOCAL_FILE_LIFESPAN = 86_400_000L * 7;

    // backups
    public static final long AUTOBACKUP_MAX_INTERVAL = 86_400_000L; // 1 day
    public static final long AUTOBACKUP_START_DELAY = 120_000L; // 2 minutes

    public static final int SERVER_SESSION_NONCE_LENGTH = 32;
    public static final int SERVER_SESSION_CHALLENGE_LENGTH = 32;
    public static final int SERVER_SESSION_TOKEN_LENGTH = 32;

    public static final int RETURN_RECEIPT_NONCE_LENGTH = 16;

    public static final byte[] MUTUAL_SCAN_SIGNATURE_CHALLENGE_PREFIX = "mutualScan".getBytes();

    public static final int DEFAULT_ATTACHMENT_CHUNK_LENGTH = 4*2048*1024;
    public static final int MAX_MESSAGE_EXTENDED_CONTENT_LENGTH = 50 * 1024;

    public static final UID BROADCAST_UID = new UID(new byte[]{(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff});

    public static final byte[] ANDROID_STORE_ID = new byte[]{0x01};

    public static final int DEFAULT_NUMBER_OF_DIGITS_FOR_SAS = 4;

    // TrustLevel threshold
    public static final TrustLevel AUTO_ACCEPT_TRUST_LEVEL_THRESHOLD = new TrustLevel(3,0);
    public static final TrustLevel USER_CONFIRMATION_TRUST_LEVEL_THRESHOLD = new TrustLevel(0, 0);

    public static final long BASE_RESCHEDULING_TIME = 250L;

    // Keycloak
    public static final long KEYCLOAK_SIGNATURE_VALIDITY_MILLIS = 60 * 86_400_000L;
}
