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

public abstract class UploadNotifications {
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS = "network_send_notification_attachment_upload_progress";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_OWNED_IDENTITY_KEY = "ownedIdentity";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_MESSAGE_UID_KEY = "messageUid";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_ATTACHMENT_NUMBER_KEY = "attachmentNumber";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_PROGRESS_PROGRESS_KEY = "progress";

    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED = "network_send_notification_attachment_upload_finished";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_OWNED_IDENTITY_KEY = "ownedIdentity";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_MESSAGE_UID_KEY = "messageUid";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_FINISHED_ATTACHMENT_NUMBER_KEY = "attachmentNumber";

    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED = "network_send_notification_attachment_upload_cancelled";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_OWNED_IDENTITY_KEY = "ownedIdentity";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_MESSAGE_UID_KEY = "messageUid";
    public static final String NOTIFICATION_ATTACHMENT_UPLOAD_CANCELLED_ATTACHMENT_NUMBER_KEY = "attachmentNumber";

    public static final String NOTIFICATION_MESSAGE_UPLOADED = "network_send_notification_message_uploaded";
    public static final String NOTIFICATION_MESSAGE_UPLOADED_OWNED_IDENTITY_KEY = "ownedIdentity";
    public static final String NOTIFICATION_MESSAGE_UPLOADED_UID_KEY = "uid";
    public static final String NOTIFICATION_MESSAGE_UPLOADED_TIMESTAMP_FROM_SERVER = "timestamp_from_server";

    public static final String NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED = "netword_send_notification_signed_url_refreshed";
    public static final String NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED_OWNED_IDENTITY_KEY = "owned_identity";
    public static final String NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED_MESSAGE_UID_KEY = "message_uid";
    public static final String NOTIFICATION_OUTBOX_ATTACHMENT_SIGNED_URL_REFRESHED_ATTACHMENT_NUMBER_KEY = "attachment_number";

    public static final String NOTIFICATION_MESSAGE_UPLOAD_FAILED = "network_send_notification_message_upload_failed";
    public static final String NOTIFICATION_MESSAGE_UPLOAD_FAILED_OWNED_IDENTITY_KEY = "ownedIdentity"; // Identity
    public static final String NOTIFICATION_MESSAGE_UPLOAD_FAILED_UID_KEY = "uid"; // UID
}
