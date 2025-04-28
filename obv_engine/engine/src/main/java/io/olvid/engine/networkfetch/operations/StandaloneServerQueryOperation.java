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

package io.olvid.engine.networkfetch.operations;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.containers.ServerQuery;
import io.olvid.engine.encoder.Encoded;

public class StandaloneServerQueryOperation extends Operation {
    public static final int RFC_NETWORK_ERROR = 1;
    public static final int RFC_UNSUPPORTED_SERVER_QUERY_TYPE = 2;
    public static final int RFC_INVALID_SERVER_SESSION = 3;
    public static final int RFC_INVALID_API_KEY = 4;

    public static final int RFC_BACKUP_UID_ALREADY_USED = 5;
    public static final int RFC_BACKUP_VERSION_TOO_SMALL = 6;
    public static final int RFC_UNKNOWN_BACKUP_UID = 7;
    public static final int RFC_UNKNOWN_BACKUP_THREAD_ID = 8;
    public static final int RFC_UNKNOWN_BACKUP_VERSION = 9;
    public static final int RFC_SERVER_PARSING_ERROR = 100;

    private final ServerQuery serverQuery;
    private final SSLSocketFactory sslSocketFactory;
    private Encoded serverResponse; // will be set if the operation finishes normally

    public Encoded getServerResponse() {
        return serverResponse;
    }

    public StandaloneServerQueryOperation(ServerQuery serverQuery, SSLSocketFactory sslSocketFactory) {
        this.serverQuery = serverQuery;
        this.sslSocketFactory = sslSocketFactory;
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        try {
            ServerQueryServerMethod serverMethod;
            switch (serverQuery.getType().getId()) {
                case OWNED_DEVICE_DISCOVERY_QUERY_ID: {
                    serverMethod = new OwnedDeviceDiscoveryServerMethod(serverQuery.getOwnedIdentity());
                    break;
                }
                case REGISTER_API_KEY_QUERY_ID: {
                    ServerQuery.RegisterApiKeyQuery registerApiKeyQuery = (ServerQuery.RegisterApiKeyQuery) serverQuery.getType();
                    serverMethod = new RegisterApiKeyServerMethod(serverQuery.getOwnedIdentity(), registerApiKeyQuery.serverSessionToken, registerApiKeyQuery.apiKeyString);
                    break;
                }
                case BACKUPS_V2_CREATE_BACKUP_QUERY_ID: {
                    ServerQuery.BackupsV2CreateBackupQuery backupsV2CreateBackupQuery = (ServerQuery.BackupsV2CreateBackupQuery) serverQuery.getType();
                    serverMethod = new BackupsV2CreateBackupServerMethod(backupsV2CreateBackupQuery.server, backupsV2CreateBackupQuery.backupUid, backupsV2CreateBackupQuery.serverAuthenticationPublicKey);
                    break;
                }
                case BACKUPS_V2_UPLOAD_BACKUP_QUERY_ID: {
                    ServerQuery.BackupsV2UploadBackupQuery backupsV2UploadBackupQuery = (ServerQuery.BackupsV2UploadBackupQuery) serverQuery.getType();
                    serverMethod = new BackupsV2UploadBackupsServerMethod(backupsV2UploadBackupQuery.server, backupsV2UploadBackupQuery.backupUid, backupsV2UploadBackupQuery.threadId, backupsV2UploadBackupQuery.version, backupsV2UploadBackupQuery.encryptedBackup, backupsV2UploadBackupQuery.signature);
                    break;
                }
                case BACKUPS_V2_DELETE_BACKUP_QUERY_ID: {
                    ServerQuery.BackupsV2DeleteBackupQuery backupsV2DeleteBackupQuery = (ServerQuery.BackupsV2DeleteBackupQuery) serverQuery.getType();
                    serverMethod = new BackupsV2DeleteBackupServerMethod(backupsV2DeleteBackupQuery.server, backupsV2DeleteBackupQuery.backupUid, backupsV2DeleteBackupQuery.threadId, backupsV2DeleteBackupQuery.version, backupsV2DeleteBackupQuery.signature);
                    break;
                }
                case BACKUPS_V2_LIST_BACKUPS_QUERY_ID: {
                    ServerQuery.BackupsV2ListBackupsQuery backupsV2ListBackupsQuery = (ServerQuery.BackupsV2ListBackupsQuery) serverQuery.getType();
                    serverMethod = new BackupsV2ListBackupsServerMethod(backupsV2ListBackupsQuery.server, backupsV2ListBackupsQuery.backupUid);
                    break;
                }
                case BACKUPS_V2_DOWNLOAD_PROFILE_PICTURE_QUERY_ID: {
                    ServerQuery.BackupsV2DownloadProfilePictureQuery backupsV2DownloadProfilePictureQuery = (ServerQuery.BackupsV2DownloadProfilePictureQuery) serverQuery.getType();
                    serverMethod = new BackupsV2DownloadProfilePictureServerMethod(backupsV2DownloadProfilePictureQuery.identity, backupsV2DownloadProfilePictureQuery.photoLabel, backupsV2DownloadProfilePictureQuery.photoKey);
                    break;
                }

                case DEVICE_DISCOVERY_QUERY_ID:
                case PUT_USER_DATA_QUERY_ID:
                case GET_USER_DATA_QUERY_ID:
                case CHECK_KEYCLOAK_REVOCATION_QUERY_ID:
                case CREATE_GROUP_BLOB_QUERY_ID:
                case GET_GROUP_BLOB_QUERY_ID:
                case LOCK_GROUP_BLOB_QUERY_ID:
                case UPDATE_GROUP_BLOB_QUERY_ID:
                case PUT_GROUP_LOG_QUERY_ID:
                case DELETE_GROUP_BLOB_QUERY_ID:
                case GET_KEYCLOAK_DATA_QUERY_ID:
                case DEVICE_MANAGEMENT_SET_NICKNAME_QUERY_ID:
                case DEVICE_MANAGEMENT_DEACTIVATE_DEVICE_QUERY_ID:
                case DEVICE_MANAGEMENT_SET_UNEXPIRING_DEVICE_QUERY_ID:
                case UPLOAD_PRE_KEY_QUERY_ID:
                case TRANSFER_SOURCE_QUERY_ID:
                case TRANSFER_TARGET_QUERY_ID:
                case TRANSFER_RELAY_QUERY_ID:
                case TRANSFER_WAIT_QUERY_ID:
                case TRANSFER_CLOSE_QUERY_ID:
                default: {
                    cancel(RFC_UNSUPPORTED_SERVER_QUERY_TYPE);
                    return;
                }
            }

            serverMethod.setSslSocketFactory(sslSocketFactory);
            byte returnStatus = serverMethod.execute(true);
            Logger.d("?? Server query return status (after parse): " + returnStatus);

            switch (returnStatus) {
                case ServerMethod.OK: {
                    serverResponse = serverMethod.getServerResponse();
                    finished = true;
                    return;
                }
                case ServerMethod.INVALID_SESSION: {
                    cancel(RFC_INVALID_SERVER_SESSION);
                    return;
                }
                case ServerMethod.INVALID_API_KEY: {
                    cancel(RFC_INVALID_API_KEY);
                    return;
                }
                case ServerMethod.BACKUP_UID_ALREADY_USED: {
                    cancel(RFC_BACKUP_UID_ALREADY_USED);
                    return;
                }
                case ServerMethod.BACKUP_VERSION_TOO_SMALL: {
                    cancel(RFC_BACKUP_VERSION_TOO_SMALL);
                    return;
                }
                case ServerMethod.UNKNOWN_BACKUP_UID: {
                    cancel(RFC_UNKNOWN_BACKUP_UID);
                    return;
                }
                case ServerMethod.UNKNOWN_BACKUP_THREAD_ID: {
                    cancel(RFC_UNKNOWN_BACKUP_THREAD_ID);
                    return;
                }
                case ServerMethod.UNKNOWN_BACKUP_VERSION: {
                    cancel(RFC_UNKNOWN_BACKUP_VERSION);
                    return;
                }
                case ServerMethod.PARSING_ERROR: {
                    cancel(RFC_SERVER_PARSING_ERROR);
                    return;
                }
                default: {
                    cancel(RFC_NETWORK_ERROR);
                    return;
                }
            }
        } catch (Exception e) {
            Logger.x(e);
        } finally {
            if (finished) {
                setFinished();
            } else {
                if (hasNoReasonForCancel()) {
                    cancel(null);
                }
                processCancel();
            }
        }
    }

    @Override
    public void doCancel() {
        // nothing to do here
    }
}
