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

import java.util.HashMap;
import java.util.Map;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.notifications.BackupNotifications;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.notification.NotificationManager;

public class NotificationListenerBackups implements NotificationListener {
    private final Engine engine;

    public NotificationListenerBackups(Engine engine) {
        this.engine = engine;
    }

    void registerToNotifications(NotificationManager notificationManager) {
        for (String notificationName : new String[]{
                BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED,
                BackupNotifications.NOTIFICATION_BACKUP_SEED_GENERATION_FAILED,
                BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED,
                BackupNotifications.NOTIFICATION_BACKUP_FINISHED,
                BackupNotifications.NOTIFICATION_BACKUP_VERIFICATION_SUCCESSFUL,
                BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FAILED,
                BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST,
                BackupNotifications.NOTIFICATION_BACKUP_RESTORATION_FINISHED,
        }) {
            notificationManager.addListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, Map<String, Object> userInfo) {
        switch (notificationName) {
            case BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED: {
                String seed = (String) userInfo.get(BackupNotifications.NOTIFICATION_NEW_BACKUP_SEED_GENERATED_SEED_KEY);
                if (seed == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.NEW_BACKUP_SEED_GENERATED_SEED_KEY, seed);

                engine.postEngineNotification(EngineNotifications.NEW_BACKUP_SEED_GENERATED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_SEED_GENERATION_FAILED: {
                engine.postEngineNotification(EngineNotifications.BACKUP_SEED_GENERATION_FAILED, new HashMap<>());
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED: {
                UID backupKeyUid = (UID) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_BACKUP_KEY_UID_KEY);
                int version = (int) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_VERSION_KEY);
                byte[] encryptedContent = (byte[]) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_ENCRYPTED_CONTENT_KEY);

                if (backupKeyUid == null || encryptedContent == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_BYTES_BACKUP_KEY_UID_KEY, backupKeyUid.getBytes());
                engineInfo.put(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_VERSION_KEY, version);
                engineInfo.put(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED_ENCRYPTED_CONTENT_KEY, encryptedContent);

                engine.postEngineNotification(EngineNotifications.BACKUP_FOR_EXPORT_FINISHED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_FINISHED: {
                UID backupKeyUid = (UID) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FINISHED_BACKUP_KEY_UID_KEY);
                int version = (int) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FINISHED_VERSION_KEY);
                byte[] encryptedContent = (byte[]) userInfo.get(BackupNotifications.NOTIFICATION_BACKUP_FINISHED_ENCRYPTED_CONTENT_KEY);

                if (backupKeyUid == null || encryptedContent == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.BACKUP_FINISHED_BYTES_BACKUP_KEY_UID_KEY, backupKeyUid.getBytes());
                engineInfo.put(EngineNotifications.BACKUP_FINISHED_VERSION_KEY, version);
                engineInfo.put(EngineNotifications.BACKUP_FINISHED_ENCRYPTED_CONTENT_KEY, encryptedContent);

                engine.postEngineNotification(EngineNotifications.BACKUP_FINISHED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_VERIFICATION_SUCCESSFUL: {
                HashMap<String, Object> engineInfo = new HashMap<>();
                engine.postEngineNotification(EngineNotifications.BACKUP_KEY_VERIFICATION_SUCCESSFUL, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_FOR_EXPORT_FAILED: {
                HashMap<String, Object> engineInfo = new HashMap<>();
                engine.postEngineNotification(EngineNotifications.BACKUP_FOR_EXPORT_FAILED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST: {
                UID backupKeyUid = (UID) userInfo.get(BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST_BACKUP_KEY_UID_KEY);
                int version = (int) userInfo.get(BackupNotifications.NOTIFICATION_APP_BACKUP_INITIATION_REQUEST_VERSION_KEY);
                if (backupKeyUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.APP_BACKUP_REQUESTED_BYTES_BACKUP_KEY_UID_KEY, backupKeyUid.getBytes());
                engineInfo.put(EngineNotifications.APP_BACKUP_REQUESTED_VERSION_KEY, version);

                engine.postEngineNotification(EngineNotifications.APP_BACKUP_REQUESTED, engineInfo);
                break;
            }
            case BackupNotifications.NOTIFICATION_BACKUP_RESTORATION_FINISHED: {
                engine.postEngineNotification(EngineNotifications.ENGINE_BACKUP_RESTORATION_FINISHED, new HashMap<>());
                break;
            }
            default:
                Logger.w("Received notification " + notificationName + " but no handler is set.");
        }
    }
}
