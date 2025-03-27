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

package io.olvid.messenger.databases.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.settings.SettingsActivity;

@SuppressWarnings("CanBeFinal")
@Entity(tableName = Invitation.TABLE_NAME,
        foreignKeys = @ForeignKey(entity = OwnedIdentity.class,
                parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                childColumns = Invitation.BYTES_OWNED_IDENTITY,
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(Invitation.BYTES_OWNED_IDENTITY),
        }
)
public class Invitation {
    public static final String TABLE_NAME = "invitation_table";
    public static final String DIALOG_UUID = "dialog_uuid";
    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String BYTES_CONTACT_IDENTITY = "bytes_contact_identity";
    public static final String ASSOCIATED_DIALOG = "associated_dialog";
    public static final String INVITATION_TIMESTAMP = "invitation_timestamp";
    public static final String CATEGORY_ID = "category_id";
    public static final String DISCUSSION_ID = "discussion_id";

    @PrimaryKey
    @ColumnInfo(name = DIALOG_UUID)
    @NonNull
    public UUID dialogUuid;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = BYTES_CONTACT_IDENTITY)
    @Nullable
    public byte[] bytesContactIdentity;

    @ColumnInfo(name = ASSOCIATED_DIALOG)
    @NonNull
    public ObvDialog associatedDialog;

    @ColumnInfo(name = INVITATION_TIMESTAMP)
    public long invitationTimestamp;

    @ColumnInfo(name = CATEGORY_ID)
    public int categoryId;

    @ColumnInfo(name = DISCUSSION_ID)
    @Nullable
    public Long discussionId;

    // Required by room, but not used
    public Invitation(@NonNull UUID dialogUuid, @NonNull byte[] bytesOwnedIdentity, @Nullable byte[] bytesContactIdentity, @NonNull ObvDialog associatedDialog, long invitationTimestamp, int categoryId, @Nullable Long discussionId) {
        this.dialogUuid = dialogUuid;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
        this.associatedDialog = associatedDialog;
        this.invitationTimestamp = invitationTimestamp;
        this.categoryId = categoryId;
        this.discussionId = discussionId;
    }

    @Ignore
    public Invitation(ObvDialog associatedDialog, long invitationTimestamp, long discussionId) {
        this.dialogUuid = associatedDialog.getUuid();
        this.bytesOwnedIdentity = associatedDialog.getBytesOwnedIdentity();
        this.bytesContactIdentity = associatedDialog.getCategory().getBytesContactIdentity();
        this.associatedDialog = associatedDialog;
        this.invitationTimestamp = invitationTimestamp;
        this.categoryId = associatedDialog.getCategory().getId();
        this.categoryId = associatedDialog.getCategory().getId();
        this.discussionId = discussionId;
    }


    @NonNull
    public String getStatusText() {
        switch (associatedDialog.getCategory().getId()) {
            case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY: {
                String shortName = StringUtils.removeCompanyFromDisplayName(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails());
                return App.getContext().getString(R.string.invitation_status_invite_sent, shortName);
            }
            case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                try {
                    String displayName = AppSingleton.getJsonObjectMapper()
                            .readValue(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class)
                            .formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                    return App.getContext().getString(R.string.invitation_status_accept_invite_from, displayName);
                } catch (Exception ignored) {
                    return App.getContext().getString(R.string.invitation_status_accept_invite);
                }
            case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY:
            case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY:
                try {
                    String displayName = AppSingleton.getJsonObjectMapper()
                            .readValue(associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class)
                            .formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
                    return App.getContext().getString(R.string.invitation_status_exchange_sas_with, displayName);
                } catch (Exception ignored) {
                    return App.getContext().getString(R.string.invitation_status_exchange_sas);
                }
            case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_invite_accepted);
            case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_mediator_invite);
            case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_mediator_invite_accepted);
            case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
            case ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY:
            case ObvDialog.Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_group_invite);
            case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY:
            case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY:
                return App.getContext().getString(R.string.invitation_status_one_to_one_invitation);
            default:
                return "";
        }
    }

    public boolean requiresAction() {
        switch (associatedDialog.getCategory().getId()) {
            case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
            case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY:
            case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
            case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
            case ObvDialog.Category.GROUP_V2_INVITATION_DIALOG_CATEGORY:
            case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY:
                return true;
            case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY:
            case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY:
            case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY:
            case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
            case ObvDialog.Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY:
            case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY:
            default:
                return false;
        }
    }
}
