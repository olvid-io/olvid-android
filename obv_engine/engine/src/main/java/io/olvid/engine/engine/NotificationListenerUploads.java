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

package io.olvid.engine.engine;

import java.util.HashMap;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.NotificationListener;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.notifications.UploadNotifications;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.notification.NotificationManager;

public class NotificationListenerUploads implements NotificationListener {
    private final Engine engine;

    public NotificationListenerUploads(Engine engine) {
        this.engine = engine;
    }

    void registerToNotifications(NotificationManager notificationManager) {
        for (String notificationName : new String[]{
                UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS,
                UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED,
                UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED,
                UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED,
                UploadNotifications.NOTIFICATION_MESSAGE_UPLOAD_FAILED
        }) {
            notificationManager.addListener(notificationName, this);
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        switch (notificationName) {
            case UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS: {
                Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY);
                float progress = (float) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY);
                Float speed = (Float) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_SPEED_BPS_KEY);
                Integer eta = (Integer) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_ETA_SECONDS_KEY);
                if (ownedIdentity == null || messageUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY, attachmentNumber);
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY, progress);
                if (speed != null && eta != null) {
                    engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_SPEED_BPS_KEY, speed);
                    engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS_ETA_SECONDS_KEY, eta);
                }

                engine.postEngineNotification(EngineNotifications.ATTACHMENT_UPLOAD_PROGRESS, engineInfo);
                break;
            }
            case UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED: {
                Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_ATTACHMENT_NUMBER_KEY);
                if (ownedIdentity == null || messageUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOADED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOADED_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOADED_ATTACHMENT_NUMBER_KEY, attachmentNumber);

                engine.postEngineNotification(EngineNotifications.ATTACHMENT_UPLOADED, engineInfo);
                break;
            }
            case UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED: {
                Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_MESSAGE_UID_KEY);
                int attachmentNumber = (int) userInfo.get(UploadNotifications.NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_ATTACHMENT_NUMBER_KEY);
                if (ownedIdentity == null || messageUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_MESSAGE_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED_ATTACHMENT_NUMBER_KEY, attachmentNumber);

                engine.postEngineNotification(EngineNotifications.ATTACHMENT_UPLOAD_CANCELLED, engineInfo);
                break;
            }
            case UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED: {
                Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED_UID_KEY);
                Long timestampFromServer = (Long) userInfo.get(UploadNotifications.NOTIFICATION_MESSAGE_UPLOADED_TIMESTAMP_FROM_SERVER);
                if (ownedIdentity == null || messageUid == null || timestampFromServer == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.MESSAGE_UPLOADED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.MESSAGE_UPLOADED_IDENTIFIER_KEY, messageUid.getBytes());
                engineInfo.put(EngineNotifications.MESSAGE_UPLOADED_TIMESTAMP_FROM_SERVER, timestampFromServer);

                engine.postEngineNotification(EngineNotifications.MESSAGE_UPLOADED, engineInfo);
                break;
            }
            case UploadNotifications.NOTIFICATION_MESSAGE_UPLOAD_FAILED: {
                Identity ownedIdentity = (Identity) userInfo.get(UploadNotifications.NOTIFICATION_MESSAGE_UPLOAD_FAILED_OWNED_IDENTITY_KEY);
                UID messageUid = (UID) userInfo.get(UploadNotifications.NOTIFICATION_MESSAGE_UPLOAD_FAILED_UID_KEY);
                if (ownedIdentity == null || messageUid == null) {
                    break;
                }

                HashMap<String, Object> engineInfo = new HashMap<>();
                engineInfo.put(EngineNotifications.MESSAGE_UPLOAD_FAILED_BYTES_OWNED_IDENTITY_KEY, ownedIdentity.getBytes());
                engineInfo.put(EngineNotifications.MESSAGE_UPLOAD_FAILED_IDENTIFIER_KEY, messageUid.getBytes());

                engine.postEngineNotification(EngineNotifications.MESSAGE_UPLOAD_FAILED, engineInfo);
                break;
            }
            default:
                Logger.w("Received notification " + notificationName + " but no handler is set.");
        }
    }
}
