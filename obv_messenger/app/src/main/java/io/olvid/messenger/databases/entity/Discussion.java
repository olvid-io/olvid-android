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

package io.olvid.messenger.databases.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.databases.AppDatabase;

@SuppressWarnings("CanBeFinal")
@Entity(tableName = Discussion.TABLE_NAME,
        foreignKeys = {
                @ForeignKey(entity = OwnedIdentity.class,
                        parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                        childColumns = Discussion.BYTES_OWNED_IDENTITY,
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Contact.class,
                        parentColumns = {Contact.BYTES_CONTACT_IDENTITY, Contact.BYTES_OWNED_IDENTITY},
                        childColumns = {Discussion.BYTES_CONTACT_IDENTITY, Discussion.BYTES_OWNED_IDENTITY},
                        onDelete = ForeignKey.NO_ACTION),
                @ForeignKey(entity = Group.class,
                        parentColumns = {Group.BYTES_GROUP_OWNER_AND_UID, Group.BYTES_OWNED_IDENTITY},
                        childColumns = {Discussion.BYTES_GROUP_OWNER_AND_UID, Discussion.BYTES_OWNED_IDENTITY},
                        onDelete = ForeignKey.NO_ACTION),
        },
        indices = {
                @Index(Discussion.BYTES_OWNED_IDENTITY),
                @Index(value = {Discussion.BYTES_CONTACT_IDENTITY, Discussion.BYTES_OWNED_IDENTITY}, unique = true),
                @Index(value = {Discussion.BYTES_GROUP_OWNER_AND_UID, Discussion.BYTES_OWNED_IDENTITY}, unique = true),
                @Index(Discussion.TITLE),
        }
)
public class Discussion {
    public static final String TABLE_NAME = "discussion_table";

    public static final String TITLE = "title";
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String SENDER_THREAD_IDENTIFIER = "sender_thread_identifier";
    public static final String LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER = "last_outbound_message_sequence_number";
    public static final String LAST_MESSAGE_TIMESTAMP = "last_message_timestamp";
    // If both groupUid and bytesContactIdentity are null, the discussion is locked
    public static final String BYTES_CONTACT_IDENTITY = "bytes_contact_identity";
    public static final String BYTES_GROUP_OWNER_AND_UID = "bytes_group_owner_and_uid";
    public static final String PHOTO_URL = "photo_url";
    public static final String KEYCLOAK_MANAGED = "keycloak_managed";
    public static final String UNREAD = "unread"; // specify if discussion as been manually marked as unread
    public static final String ACTIVE = "active";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = TITLE)
    public String title;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = SENDER_THREAD_IDENTIFIER)
    @NonNull
    public UUID senderThreadIdentifier;

    @ColumnInfo(name = LAST_OUTBOUND_MESSAGE_SEQUENCE_NUMBER)
    public long lastOutboundMessageSequenceNumber;

    @ColumnInfo(name = LAST_MESSAGE_TIMESTAMP)
    public long lastMessageTimestamp;

    @ColumnInfo(name = BYTES_GROUP_OWNER_AND_UID)
    @Nullable
    public byte[] bytesGroupOwnerAndUid;

    @ColumnInfo(name = BYTES_CONTACT_IDENTITY)
    @Nullable
    public byte[] bytesContactIdentity;

    @ColumnInfo(name = PHOTO_URL)
    @Nullable
    public String photoUrl;

    @ColumnInfo(name = KEYCLOAK_MANAGED)
    public boolean keycloakManaged;

    // specify if discussion as been manually marked as unread
    @ColumnInfo(name = UNREAD)
    public boolean unread;

    @ColumnInfo(name = ACTIVE)
    public boolean active;



    // default constructor required by Room

    public Discussion(String title, @NonNull byte[] bytesOwnedIdentity, @NonNull UUID senderThreadIdentifier, long lastOutboundMessageSequenceNumber, long lastMessageTimestamp, @Nullable byte[] bytesGroupOwnerAndUid, @Nullable byte[] bytesContactIdentity, @Nullable String photoUrl, boolean keycloakManaged, boolean unread, boolean active) {
        this.title = title;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.senderThreadIdentifier = senderThreadIdentifier;
        this.lastOutboundMessageSequenceNumber = lastOutboundMessageSequenceNumber;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
        this.bytesContactIdentity = bytesContactIdentity;
        this.photoUrl = photoUrl;
        this.keycloakManaged = keycloakManaged;
        this.unread = unread;
        this.active = active;
    }


    @Ignore
    public static Discussion createOneToOneDiscussion(@NonNull String title, @Nullable String photoUrl, @NonNull byte[] bytesOwnedIdentity, @NonNull byte[] contactBytesIdentity, boolean keycloakManaged, boolean active) {
        return new Discussion(
                title,
                bytesOwnedIdentity,
                UUID.randomUUID(),
                0,
                System.currentTimeMillis(),
        null,
                contactBytesIdentity,
                photoUrl,
                keycloakManaged,
                false,
                active
        );
    }

    @Ignore
    public static Discussion createGroupDiscussion(@NonNull String title, @Nullable String photoUrl, @NonNull byte[] bytesOwnedIdentity, @NonNull byte[] groupId) {
        return new Discussion(
                title,
                bytesOwnedIdentity,
                UUID.randomUUID(),
                0,
                System.currentTimeMillis(),
                groupId,
                null,
                photoUrl,
                false,
                false,
                true
        );
    }

    public boolean isLocked() {
        return bytesGroupOwnerAndUid == null && bytesContactIdentity == null;
    }

    public boolean updateLastMessageTimestamp(long lastMessageTimestamp) {
        if (lastMessageTimestamp > this.lastMessageTimestamp) {
            this.lastMessageTimestamp = lastMessageTimestamp;
            return true;
        }
        return false;
    }

    public void lockWithMessage(AppDatabase db) {
        if (! db.inTransaction()) {
            Logger.e("ERROR: running discussion lockWithMessage outside a transaction");
        }

        if (isLocked()) {
            Logger.w("Locking a discussion which is already locked");
            return;
        }

        // delete draft messages
        db.messageDao().deleteDiscussionDraftMessage(id);
        db.messageDao().deleteAllDiscussionNewPublishedDetailsMessages(id);

        if (db.messageDao().countMessagesInDiscussion(id) == 0) {
            // discussion is empty and will be locked --> delete it
            db.discussionDao().delete(this);
        } else {
            // the discussion has messages, do not delete it and insert a discussion left message
            if (bytesContactIdentity != null) {
                Message contactDeletedMessage = Message.createContactDeletedMessage(id, bytesContactIdentity);
                db.messageDao().insert(contactDeletedMessage);
            } else if (bytesGroupOwnerAndUid != null) {
                Message groupLeftMessage = Message.createLeftGroupMessage(id, bytesOwnedIdentity);
                db.messageDao().insert(groupLeftMessage);
            }

            // copy the discussion photoUrl to customPhotos folder, unless it is already in there :)
            if (photoUrl != null && !photoUrl.startsWith(AppSingleton.CUSTOM_PHOTOS_DIRECTORY)) {
                try {
                    int i = 0;
                    String relativeOutputPath;
                    do {
                        relativeOutputPath = AppSingleton.CUSTOM_PHOTOS_DIRECTORY + File.separator + UUID.randomUUID().toString();
                        i++;
                    } while (i < 10 && new File(App.absolutePathFromRelative(relativeOutputPath)).exists());

                    // move or copy file
                    File oldFile = new File(App.absolutePathFromRelative(photoUrl));
                    File newFile = new File(App.absolutePathFromRelative(relativeOutputPath));
                    if (!oldFile.renameTo(newFile)) {
                        // rename failed --> maybe on 2 different partitions
                        // fallback to a copy.
                        try (FileInputStream fileInputStream = new FileInputStream(oldFile); FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                            byte[] buffer = new byte[262_144];
                            int c;
                            while ((c = fileInputStream.read(buffer)) != -1) {
                                fileOutputStream.write(buffer, 0, c);
                            }
                        }

                        //noinspection ResultOfMethodCallIgnored
                        oldFile.delete();
                    }
                    photoUrl = relativeOutputPath;
                } catch (Exception e) {
                    e.printStackTrace();
                    photoUrl = null;
                }
                db.discussionDao().updateTitleAndPhotoUrl(id, title, photoUrl);
            }

            // lock the discussion
            bytesContactIdentity = null;
            bytesGroupOwnerAndUid = null;
            keycloakManaged = false;
            db.discussionDao().updateAsLocked(id);

            ShortcutActivity.updateShortcut(this);
        }
    }
}
