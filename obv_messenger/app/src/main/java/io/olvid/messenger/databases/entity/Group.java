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

import java.util.List;

import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.messenger.customClasses.StringUtils;

@Entity(tableName = Group.TABLE_NAME,
        primaryKeys = {Group.BYTES_GROUP_OWNER_AND_UID, Group.BYTES_OWNED_IDENTITY},
        foreignKeys = {
                @ForeignKey(entity = OwnedIdentity.class,
                        parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                        childColumns = Group.BYTES_OWNED_IDENTITY,
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Contact.class,
                        parentColumns = {Contact.BYTES_CONTACT_IDENTITY, Contact.BYTES_OWNED_IDENTITY},
                        childColumns = {Group.BYTES_GROUP_OWNER_IDENTITY, Group.BYTES_OWNED_IDENTITY},
                        onDelete = ForeignKey.NO_ACTION),
        },
        indices = {
                @Index(Group.NAME),
                @Index(Group.BYTES_OWNED_IDENTITY),
                @Index({Group.BYTES_GROUP_OWNER_IDENTITY, Group.BYTES_OWNED_IDENTITY}),
        }
)
public class Group {
    public static final String TABLE_NAME = "group_table";

    public static final String BYTES_GROUP_OWNER_AND_UID = "bytes_group_owner_and_uid";
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String CUSTOM_NAME = "custom_name";
    public static final String NAME = "name";
    public static final String NEW_PUBLISHED_DETAILS = "new_published_details";
    public static final String BYTES_GROUP_OWNER_IDENTITY = "bytes_group_owner_identity"; // null for groups you own
    public static final String PHOTO_URL = "photo_url";
    public static final String GROUP_MEMBERS_NAMES = "group_members_names";
    public static final String CUSTOM_PHOTO_URL = "custom_photo_url"; // set to "" to remove the default photo_url, null to use the default
    public static final String PERSONAL_NOTE = "personal_note";
    public static final String FULL_SEARCH_FIELD = "full_search_field";

    public static final int PUBLISHED_DETAILS_NOTHING_NEW = 0;
    public static final int PUBLISHED_DETAILS_NEW_UNSEEN = 1;
    public static final int PUBLISHED_DETAILS_NEW_SEEN = 2;
    public static final int PUBLISHED_DETAILS_UNPUBLISHED_NEW = 3;

    @ColumnInfo(name = BYTES_GROUP_OWNER_AND_UID)
    @NonNull
    public byte[] bytesGroupOwnerAndUid;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = CUSTOM_NAME)
    @Nullable
    public String customName;

    @ColumnInfo(name = NAME)
    @NonNull
    public String name;

    @ColumnInfo(name = NEW_PUBLISHED_DETAILS)
    public int newPublishedDetails;

    @ColumnInfo(name = BYTES_GROUP_OWNER_IDENTITY)
    @Nullable
    public byte[] bytesGroupOwnerIdentity;

    @ColumnInfo(name = PHOTO_URL)
    @Nullable
    public String photoUrl;

    @ColumnInfo(name = GROUP_MEMBERS_NAMES)
    @NonNull
    public String groupMembersNames;


    @ColumnInfo(name = CUSTOM_PHOTO_URL)
    @Nullable
    public String customPhotoUrl;

    @ColumnInfo(name = PERSONAL_NOTE)
    @Nullable
    public String personalNote;

    @ColumnInfo(name = FULL_SEARCH_FIELD)
    @NonNull
    public String fullSearchField;

    public String getCustomName() {
        if (customName == null) {
            return name;
        }
        return customName;
    }

    public String getCustomPhotoUrl() {
        if (customPhotoUrl == null) {
            return photoUrl;
        } else if (customPhotoUrl.isEmpty()) {
            return null;
        }
        return customPhotoUrl;
    }

    // default constructor required by Room
    public Group(@NonNull byte[] bytesGroupOwnerAndUid, @NonNull byte[] bytesOwnedIdentity, @Nullable String customName, @NonNull String name, int newPublishedDetails, @Nullable byte[] bytesGroupOwnerIdentity, @Nullable String photoUrl, @NonNull String groupMembersNames, @Nullable String customPhotoUrl, @Nullable String personalNote, @NonNull String fullSearchField) {
        this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.customName = customName;
        this.name = name;
        this.newPublishedDetails = newPublishedDetails;
        this.bytesGroupOwnerIdentity = bytesGroupOwnerIdentity;
        this.photoUrl = photoUrl;
        this.groupMembersNames = groupMembersNames;
        this.customPhotoUrl = customPhotoUrl;
        this.personalNote = personalNote;
        this.fullSearchField = fullSearchField;
    }


    @Ignore
    public Group(@NonNull byte[] bytesGroupOwnerAndUid, @NonNull byte[] bytesOwnedIdentity, @NonNull JsonGroupDetails groupDetails, @Nullable String photoUrl, @Nullable byte[] bytesGroupOwnerIdentity, boolean hasMultipleDetails) {
        this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.customName = null;
        this.name = groupDetails.getName();
        this.customPhotoUrl = null;
        this.photoUrl = photoUrl;
        if (hasMultipleDetails) {
            if (bytesGroupOwnerIdentity == null) {
                this.newPublishedDetails = PUBLISHED_DETAILS_UNPUBLISHED_NEW;
            } else {
                this.newPublishedDetails = PUBLISHED_DETAILS_NEW_UNSEEN;
            }
        } else {
            this.newPublishedDetails = PUBLISHED_DETAILS_NOTHING_NEW;
        }
        this.bytesGroupOwnerIdentity = bytesGroupOwnerIdentity;
        this.groupMembersNames = "";
        this.personalNote = null;
        this.fullSearchField = "";
    }

    @NonNull
    public String computeFullSearch(@NonNull List<String> membersFullSearch) {
        String suffix = "";
        if (customName != null) {
            suffix += " " + customName;
        }
        if (personalNote != null) {
            suffix += " " + personalNote;
        }
        if (name != null) {
            suffix += " " + name;
        }
        return StringUtils.unAccent(String.join(" ", membersFullSearch) + suffix);
    }
}
