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

public abstract class BackupNotifications {
    public static final String NOTIFICATION_NEW_BACKUP_SEED_GENERATED = "backup_notification_new_backup_seed_generated";
    public static final String NOTIFICATION_NEW_BACKUP_SEED_GENERATED_SEED_KEY = "seed";

    public static final String NOTIFICATION_BACKUP_SEED_GENERATION_FAILED = "backup_notification_backup_seed_generation_failed";

    public static final String NOTIFICATION_BACKUP_VERIFICATION_SUCCESSFUL = "backup_notification_backup_verification_successful";

    public static final String NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED = "backup_notification_backup_for_export_finished";
    public static final String NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_BACKUP_KEY_UID_KEY = "backup_key_uid"; // UID
    public static final String NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_VERSION_KEY = "version"; // int
    public static final String NOTIFICATION_BACKUP_FOR_EXPORT_FINISHED_ENCRYPTED_CONTENT_KEY = "encrypted_content"; // byte[]

    public static final String NOTIFICATION_BACKUP_FINISHED = "backup_notification_backup_finished";
    public static final String NOTIFICATION_BACKUP_FINISHED_BACKUP_KEY_UID_KEY = "backup_key_uid"; // UID
    public static final String NOTIFICATION_BACKUP_FINISHED_VERSION_KEY = "version"; // int
    public static final String NOTIFICATION_BACKUP_FINISHED_ENCRYPTED_CONTENT_KEY = "encrypted_content"; // byte[]

    public static final String NOTIFICATION_BACKUP_FOR_EXPORT_FAILED = "backup_notification_backup_for_export_failed";

    public static final String NOTIFICATION_APP_BACKUP_INITIATION_REQUEST = "backup_notification_app_backup_initiation_request";
    public static final String NOTIFICATION_APP_BACKUP_INITIATION_REQUEST_BACKUP_KEY_UID_KEY = "backup_uid";
    public static final String NOTIFICATION_APP_BACKUP_INITIATION_REQUEST_VERSION_KEY = "version";

    public static final String NOTIFICATION_BACKUP_RESTORATION_FINISHED = "backup_notification_backup_restoration_finished";
}
