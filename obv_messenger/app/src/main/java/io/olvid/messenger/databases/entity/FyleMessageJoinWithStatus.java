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

package io.olvid.messenger.databases.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.encoder.Encoded;
import io.olvid.messenger.App;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.discussion.linkpreview.OpenGraph;
import io.olvid.messenger.settings.SettingsActivity;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = FyleMessageJoinWithStatus.TABLE_NAME,
        primaryKeys = {FyleMessageJoinWithStatus.FYLE_ID, FyleMessageJoinWithStatus.MESSAGE_ID},
        foreignKeys = {
                @ForeignKey(entity = Fyle.class,
                        parentColumns = "id",
                        childColumns = FyleMessageJoinWithStatus.FYLE_ID,
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Message.class,
                        parentColumns = "id",
                        childColumns = FyleMessageJoinWithStatus.MESSAGE_ID,
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = OwnedIdentity.class,
                        parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                        childColumns = FyleMessageJoinWithStatus.BYTES_OWNED_IDENTITY),
        },
        indices = {
                @Index(FyleMessageJoinWithStatus.FYLE_ID),
                @Index(FyleMessageJoinWithStatus.MESSAGE_ID),
                @Index({FyleMessageJoinWithStatus.ENGINE_MESSAGE_IDENTIFIER, FyleMessageJoinWithStatus.ENGINE_NUMBER}),
                @Index({FyleMessageJoinWithStatus.MESSAGE_ID, FyleMessageJoinWithStatus.ENGINE_NUMBER}),
                @Index({FyleMessageJoinWithStatus.BYTES_OWNED_IDENTITY}),
        }
)
public class FyleMessageJoinWithStatus {
    public static final String TABLE_NAME = "fyle_message_join_with_status";
    public static final String FTS_TABLE_NAME = "fyle_message_join_with_status_fts";
    public static final String FYLE_ID = "fyle_id";
    public static final String MESSAGE_ID = "message_id";
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String FILE_PATH = "file_path"; // should almost always be a path relative to the getNoBackupFilesDir folder, but may be an absolute path when referring to a file in the cache dir (during a copy typically)
    public static final String FILE_NAME = "file_name";
    public static final String TEXT_EXTRACTED = "text_extracted";
    public static final String TEXT_CONTENT = "text_content";
    public static final String MIME_TYPE = "file_type";
    public static final String STATUS = "status";
    public static final String SIZE = "size";
    public static final String PROGRESS = "progress";
    public static final String ENGINE_MESSAGE_IDENTIFIER = "engine_message_identifier";
    public static final String ENGINE_NUMBER = "engine_number";
    public static final String IMAGE_RESOLUTION = "image_resolution"; // null = not computed yet, "" = not a preview-able image, "750x1203" = image resolution, "a750x1203" = animated image, "v360x240" = video resolution
    public static final String MINI_PREVIEW = "mini_preview";
    public static final String WAS_OPENED = "audio_played"; // this name is legacy, but actually corresponds to the attachment having been opened (used for return receipts)
    public static final String RECEPTION_STATUS = "reception_status";

    public static final int STATUS_DOWNLOADABLE = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_DRAFT = 2;
    public static final int STATUS_UPLOADING = 3;
    public static final int STATUS_COMPLETE = 4;
    public static final int STATUS_COPYING = 5;
    public static final int STATUS_FAILED = 6;

    public static final int RECEPTION_STATUS_NONE = 0;
    public static final int RECEPTION_STATUS_DELIVERED = 1;
    public static final int RECEPTION_STATUS_DELIVERED_AND_READ = 2;

    @ColumnInfo(name = FYLE_ID)
    public final long fyleId;

    @ColumnInfo(name = MESSAGE_ID)
    public final long messageId;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = FILE_PATH)
    @NonNull
    public String filePath; // should almost always be a path relative to the getNoBackupFilesDir folder, but may be an absolute path when referring to a file in the cache dir (during a copy typically)

    @ColumnInfo(name = FILE_NAME)
    @NonNull
    public String fileName;

    @ColumnInfo(name = TEXT_EXTRACTED)
    public boolean textExtracted;

    @ColumnInfo(name = TEXT_CONTENT)
    public String textContent;

    @ColumnInfo(name = MIME_TYPE)
    public String mimeType;

    @ColumnInfo(name = STATUS)
    public int status;

    @ColumnInfo(name = SIZE)
    public long size;

    @ColumnInfo(name = PROGRESS)
    public float progress;

    @ColumnInfo(name = ENGINE_MESSAGE_IDENTIFIER)
    public byte[] engineMessageIdentifier;

    @ColumnInfo(name = ENGINE_NUMBER)
    public Integer engineNumber;

    @ColumnInfo(name = IMAGE_RESOLUTION)
    @Nullable
    public String imageResolution;

    @ColumnInfo(name = MINI_PREVIEW)
    @Nullable
    public byte[] miniPreview;

    @ColumnInfo(name = WAS_OPENED)
    public boolean wasOpened;

    @ColumnInfo(name = RECEPTION_STATUS)
    public int receptionStatus;

    // default constructor required by Room (do not use internally)
    public FyleMessageJoinWithStatus(long fyleId, long messageId, @NonNull byte[] bytesOwnedIdentity, @NonNull String filePath, @NonNull String fileName, boolean textExtracted, String textContent, String mimeType, int status, long size, float progress, byte[] engineMessageIdentifier, Integer engineNumber, @Nullable String imageResolution, @Nullable byte[] miniPreview, boolean wasOpened, int receptionStatus) {
        this.fyleId = fyleId;
        this.messageId = messageId;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.filePath = filePath;
        this.fileName = fileName;
        this.textExtracted = textExtracted;
        this.textContent = textContent;
        this.mimeType = mimeType;
        this.status = status;
        this.size = size;
        this.progress = progress;
        this.engineMessageIdentifier = engineMessageIdentifier;
        this.engineNumber = engineNumber;
        this.imageResolution = imageResolution;
        this.miniPreview = miniPreview;
        this.wasOpened = wasOpened;
        this.receptionStatus = receptionStatus;
    }

    @Ignore
    public FyleMessageJoinWithStatus(long fyleId, long messageId, @NonNull byte[] bytesOwnedIdentity, @NonNull String filePath, @NonNull String fileName, String mimeType, int status, long size, float progress, byte[] engineMessageIdentifier, Integer engineNumber, @Nullable String imageResolution) {
        this.fyleId = fyleId;
        this.messageId = messageId;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.filePath = filePath;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.status = status;
        this.size = size;
        this.progress = progress;
        this.engineMessageIdentifier = engineMessageIdentifier;
        this.engineNumber = engineNumber;
        this.imageResolution = imageResolution;
        this.miniPreview = null;
        this.wasOpened = false;
    }


    public static FyleMessageJoinWithStatus createDraft(long fyleId, long messageId, @NonNull byte[] bytesOwnedIdentity, String filePath, String fileName, String mimeType, long size) {
        final String imageResolution = PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(mimeType, fileName)) ? null : "";
        return new FyleMessageJoinWithStatus(fyleId,
                messageId,
                bytesOwnedIdentity,
                filePath,
                fileName,
                false,
                null,
                mimeType,
                STATUS_DRAFT,
                size,
                0,
                null,
                null,
                imageResolution,
                null,
                true,
                RECEPTION_STATUS_NONE);
    }

    public static FyleMessageJoinWithStatus createCopying(long fyleId, long messageId, @NonNull byte[] bytesOwnedIdentity, String filePath, String fileName, String mimeType, long size) {
        final String imageResolution = PreviewUtils.mimeTypeIsSupportedImageOrVideo(PreviewUtils.getNonNullMimeType(mimeType, fileName)) ? null : "";
        return new FyleMessageJoinWithStatus(fyleId,
                messageId,
                bytesOwnedIdentity,
                filePath,
                fileName,
                false,
                null,
                mimeType,
                STATUS_COPYING,
                size,
                0,
                null,
                null,
                imageResolution,
                null,
                true,
                RECEPTION_STATUS_NONE);
    }


    @Ignore
    private String cachedNonNullMimeType = null;

    public String getNonNullMimeType() {
        if (mimeType == null || !mimeType.contains("/")) {
            if (cachedNonNullMimeType == null) {
                cachedNonNullMimeType = PreviewUtils.getNonNullMimeType(null, fileName);
            }
            return cachedNonNullMimeType;
        }
        return mimeType;
    }

    public String getAbsoluteFilePath() {
        if (filePath.startsWith("/")) {
            return filePath;
        } else {
            return App.absolutePathFromRelative(filePath);
        }
    }

    public void setProgress(float progress) {
        this.progress = progress;
    }

    public boolean refreshOutboundStatus(byte[] bytesOwnedIdentity) {
        // outbound status only makes sense for outbound messages, which have an engine number
        if (engineNumber == null) {
            return false;
        }

        List<MessageRecipientInfo> messageRecipientInfos = AppDatabase.getInstance().messageRecipientInfoDao().getAllByMessageId(messageId);
        if (messageRecipientInfos.size() == 0) {
            return false;
        }
        // when computing the message status, do not take the recipient info of my other owned devices into account, unless this is the only recipient info (discussion with myself)
        boolean ignoreOwnRecipientInfo = messageRecipientInfos.size() > 1;

        int newStatus = 100000;
        for (MessageRecipientInfo messageRecipientInfo : messageRecipientInfos) {
            if (ignoreOwnRecipientInfo && Arrays.equals(messageRecipientInfo.bytesContactIdentity, bytesOwnedIdentity)) {
                continue;
            }

            if (MessageRecipientInfo.isAttachmentNumberPresent(engineNumber, messageRecipientInfo.undeliveredAttachmentNumbers)) {
                newStatus = RECEPTION_STATUS_NONE;
                break;
            } else if (MessageRecipientInfo.isAttachmentNumberPresent(engineNumber, messageRecipientInfo.unreadAttachmentNumbers)) {
                newStatus = RECEPTION_STATUS_DELIVERED;
            } else {
                newStatus = Math.min(newStatus, RECEPTION_STATUS_DELIVERED_AND_READ);
            }
        }
        if (newStatus != receptionStatus) {
            receptionStatus = newStatus;
            return true;
        }
        return false;
    }

    public void sendReturnReceipt(int receptionStatus, @Nullable Message message) {
        if (engineNumber == null) {
            return;
        }
        AppDatabase db = AppDatabase.getInstance();
        if (message == null) {
            message = db.messageDao().get(messageId);
        }
        if (message != null) {
            Discussion discussion = db.discussionDao().getById(message.discussionId);
            if (discussion != null) {
                if (receptionStatus == RECEPTION_STATUS_DELIVERED && wasOpened) {
                    DiscussionCustomization discussionCustomization = db.discussionCustomizationDao().get(message.discussionId);
                    boolean sendReadReceipt;
                    if (discussionCustomization != null && discussionCustomization.prefSendReadReceipt != null) {
                        sendReadReceipt = discussionCustomization.prefSendReadReceipt;
                    } else {
                        sendReadReceipt = SettingsActivity.getDefaultSendReadReceipt();
                    }
                    if (sendReadReceipt) {
                        receptionStatus = RECEPTION_STATUS_DELIVERED_AND_READ;
                    }
                }
                message.sendAttachmentReturnReceipt(discussion.bytesOwnedIdentity, receptionStatus, engineNumber);
            }
        }
    }

    // should be run on a background thread (accesses the DB)
    public void markAsOpened() {
        if (!wasOpened) {
            App.runThread(() -> {
                Message message = AppDatabase.getInstance().messageDao().get(messageId);
                // only mark opened for inbound messages
                if (message != null
                        && (message.messageType == Message.TYPE_INBOUND_MESSAGE || message.messageType == Message.TYPE_INBOUND_EPHEMERAL_MESSAGE)) {

                    // do not modify the fyleMessageJoin otherwise the MessageAttachment diffutil does not detect the change in db...
                    wasOpened = true;
                    AppDatabase.getInstance().fyleMessageJoinWithStatusDao().update(this);

                    // send notification to the sender if needed
                    DiscussionCustomization discussionCustomization = AppDatabase.getInstance().discussionCustomizationDao().get(message.discussionId);
                    boolean sendReadReceipt;
                    if (discussionCustomization != null && discussionCustomization.prefSendReadReceipt != null) {
                        sendReadReceipt = discussionCustomization.prefSendReadReceipt;
                    } else {
                        sendReadReceipt = SettingsActivity.getDefaultSendReadReceipt();
                    }
                    if (sendReadReceipt) {
                        sendReturnReceipt(RECEPTION_STATUS_DELIVERED_AND_READ, message);
                    }
                }
            });
        }
    }

    // should be run on a background thread (accesses the DB)
    public void computeTextContentForFullTextSearch(AppDatabase db, Fyle fyle) throws IOException {
        if (textExtracted) {
            return;
        }

        if (OpenGraph.MIME_TYPE.equals(mimeType)) {
            try (FileInputStream fileInputStream = new FileInputStream(App.absolutePathFromRelative(fyle.filePath));
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[262_144];
                int c;
                while ((c = fileInputStream.read(buffer)) != -1) {
                    bos.write(buffer, 0, c);
                }
                OpenGraph openGraph = OpenGraph.Companion.of(new Encoded(bos.toByteArray()), null);
                String content = openGraph.getUrl() + " " + (openGraph.getTitle() != null ? openGraph.getTitle() + " " : " ") + (openGraph.getDescription() != null ? openGraph.getDescription() : "").trim();
                textContent = content;
                textExtracted = true;
                db.fyleMessageJoinWithStatusDao().updateTextContent(messageId, fyleId, content);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FyleMessageJoinWithStatus)) return false;
        FyleMessageJoinWithStatus that = (FyleMessageJoinWithStatus) o;
        return fyleId == that.fyleId && messageId == that.messageId && textExtracted == that.textExtracted && status == that.status && size == that.size && Float.compare(progress, that.progress) == 0 && wasOpened == that.wasOpened && receptionStatus == that.receptionStatus && Objects.deepEquals(bytesOwnedIdentity, that.bytesOwnedIdentity) && Objects.equals(filePath, that.filePath) && Objects.equals(fileName, that.fileName) && Objects.equals(textContent, that.textContent) && Objects.equals(mimeType, that.mimeType) && Objects.deepEquals(engineMessageIdentifier, that.engineMessageIdentifier) && Objects.equals(engineNumber, that.engineNumber) && Objects.equals(imageResolution, that.imageResolution) && Objects.deepEquals(miniPreview, that.miniPreview);
    }
}
