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

package io.olvid.messenger.databases.tasks;

import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.Group2Dao;
import io.olvid.messenger.databases.dao.Group2MemberDao;
import io.olvid.messenger.databases.dao.PendingGroupMemberDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Group2;
import io.olvid.messenger.settings.SettingsActivity;

public class GroupCloningTasks {
    @Nullable
    public static ClonabilityOutput getClonability(@NonNull Group2Dao.GroupOrGroup2 groupOrGroup2) {
        if (groupOrGroup2.group != null) {
            return getClonability(groupOrGroup2.group);
        } else if (groupOrGroup2.group2 != null) {
            return getClonability(groupOrGroup2.group2);
        } else {
            return null;
        }
    }

    public static ClonabilityOutput getClonability(@NonNull Group group) {
        String groupDisplayName = group.getCustomName();
        String serializedGroupDetails = null;
        String absolutePhotoUrl = App.absolutePathFromRelative(group.getCustomPhotoUrl());


        try {
            JsonGroupDetailsWithVersionAndPhoto[] jsons = AppSingleton.getEngine().getGroupPublishedAndLatestOrTrustedDetails(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
            JsonGroupDetails groupDetails;
            if (jsons.length == 2) {
                groupDetails = jsons[1].getGroupDetails();
            } else {
                groupDetails = jsons[0].getGroupDetails();
            }
            if (groupDetails != null) {
                if (group.customName != null) {
                    groupDetails.setName(group.customName);
                }
                serializedGroupDetails = AppSingleton.getJsonObjectMapper().writeValueAsString(groupDetails);
            }
        } catch (Exception ignored) { }

        List<Contact> clonableGroupContacts = new ArrayList<>();
        List<Contact> nonGroupV2CapableContacts = new ArrayList<>();
        List<String> nonContactOrNonChanelSerializedDetails = new ArrayList<>();

        AppDatabase db = AppDatabase.getInstance();
        List<Contact> groupMembers = db.contactGroupJoinDao().getGroupContactsSync(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);
        List<PendingGroupMemberDao.PendingGroupMemberAndContact> pendingGroupMembers = db.pendingGroupMemberDao().getGroupPendingMemberAndContacts(group.bytesOwnedIdentity, group.bytesGroupOwnerAndUid);

        for (Contact groupMember : groupMembers) {
            if (groupMember.establishedChannelCount == 0) {
                if (groupMember.identityDetails != null) {
                    nonContactOrNonChanelSerializedDetails.add(groupMember.identityDetails);
                }
            } else if (!groupMember.capabilityGroupsV2) {
                nonGroupV2CapableContacts.add(groupMember);
            } else {
                // we have a channel and they have groupV2 capability --> clonable
                clonableGroupContacts.add(groupMember);
            }
        }
        for (PendingGroupMemberDao.PendingGroupMemberAndContact pendingGroupMember : pendingGroupMembers) {
            if (pendingGroupMember.pendingGroupMember.declined) {
                // As a group owner, we do not re-invite people who declined the group invitation
                continue;
            }
            if (pendingGroupMember.contact == null || pendingGroupMember.contact.establishedChannelCount == 0) {
                JsonIdentityDetails jsonIdentityDetails = new JsonIdentityDetails(pendingGroupMember.pendingGroupMember.displayName, null, null, null);
                try {
                    nonContactOrNonChanelSerializedDetails.add(AppSingleton.getJsonObjectMapper().writeValueAsString(jsonIdentityDetails));
                } catch (Exception ignored) { }
            } else if (!pendingGroupMember.contact.capabilityGroupsV2) {
                nonGroupV2CapableContacts.add(pendingGroupMember.contact);
            } else {
                clonableGroupContacts.add(pendingGroupMember.contact);
            }
        }

        return new ClonabilityOutput(
                groupDisplayName,
                serializedGroupDetails,
                null,
                absolutePhotoUrl,
                clonableGroupContacts,
                new ArrayList<>(),
                nonGroupV2CapableContacts,
                nonContactOrNonChanelSerializedDetails
        );
    }

    public static ClonabilityOutput getClonability(@NonNull Group2 group2) {
        ObvGroupV2.ObvGroupV2DetailsAndPhotos groupV2DetailsAndPhotos = AppSingleton.getEngine().getGroupV2DetailsAndPhotos(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);
        String groupDisplayName = group2.getCustomName();
        String absolutePhotoUrl = App.absolutePathFromRelative(group2.getCustomPhotoUrl());
        String serializedGroupDetails = groupV2DetailsAndPhotos.serializedGroupDetails;
        String serializedGroupType = AppSingleton.getEngine().getGroupV2JsonType(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);

        if (group2.customName != null) {
            try {
                JsonGroupDetails groupDetails = AppSingleton.getJsonObjectMapper().readValue(groupV2DetailsAndPhotos.serializedGroupDetails, JsonGroupDetails.class);
                groupDetails.setName(group2.customName);
                serializedGroupDetails = AppSingleton.getJsonObjectMapper().writeValueAsString(groupDetails);
            } catch (Exception ignored) { }
        }

        List<Contact> clonableGroupContacts = new ArrayList<>();
        List<Contact> clonableGroupAdminContacts = new ArrayList<>();
        List<String> nonContactOrNonChanelSerializedDetails = new ArrayList<>();


        AppDatabase db = AppDatabase.getInstance();

        List<Group2MemberDao.Group2MemberOrPending> groupMembers = db.group2MemberDao().getGroupMembersAndPendingSync(group2.bytesOwnedIdentity, group2.bytesGroupIdentifier);

        for (Group2MemberDao.Group2MemberOrPending group2Member : groupMembers) {
            if (group2Member.contact == null || !group2Member.contact.capabilityGroupsV2 || group2Member.contact.establishedChannelCount == 0) {
                nonContactOrNonChanelSerializedDetails.add(group2Member.identityDetails);
            } else {
                if (group2Member.permissionAdmin) {
                    clonableGroupAdminContacts.add(group2Member.contact);
                } else {
                    clonableGroupContacts.add(group2Member.contact);
                }
            }
        }

        return new ClonabilityOutput(
                groupDisplayName,
                serializedGroupDetails,
                serializedGroupType,
                absolutePhotoUrl,
                clonableGroupContacts,
                clonableGroupAdminContacts,
                new ArrayList<>(),
                nonContactOrNonChanelSerializedDetails
        );
    }


    public static class ClonabilityOutput {
        public final String groupDisplayName; // only used in dialogs displayed before the cloning: may be members names for unnamed groups v2
        public final String serializedGroupDetails; // contains the actual group name (or custom name), never the members names
        public final String serializedGroupType;
        public final String absolutePhotoUrl;
        public final List<Contact> clonableGroupContacts; // list of non admin contacts in the group (or pending) with whom I have a channel and that have group v2 capability
        public final List<Contact> clonableGroupAdminContacts; // list of admins in the group (or pending) with whom I have a channel and that have group v2 capability
        public final List<Contact> nonGroupV2CapableContacts; // contacts without group v2 capability
        public final List<String> nonContactOrNonChanelSerializedDetails; // serializedIdentityDetails of group members without a channel or with whom I am not in contact


        public ClonabilityOutput(String groupDisplayName, String serializedGroupDetails, String serializedGroupType, String absolutePhotoUrl, List<Contact> clonableGroupContacts, List<Contact> clonableGroupAdminContacts, List<Contact> nonGroupV2CapableContacts, List<String> nonContactOrNonChanelSerializedDetails) {
            this.groupDisplayName = groupDisplayName;
            this.serializedGroupDetails = serializedGroupDetails;
            this.serializedGroupType = serializedGroupType;
            this.absolutePhotoUrl = absolutePhotoUrl;
            this.clonableGroupContacts = clonableGroupContacts;
            this.clonableGroupAdminContacts = clonableGroupAdminContacts;
            this.nonGroupV2CapableContacts = nonGroupV2CapableContacts;
            this.nonContactOrNonChanelSerializedDetails = nonContactOrNonChanelSerializedDetails;
        }
    }


    // Should always be run on the main thread
    public static void initiateGroupCloningOrWarnUser(FragmentActivity activity, ClonabilityOutput clonabilityOutput) {
        if (clonabilityOutput == null) {
            return;
        }

        String bullet = "\n" + activity.getString(R.string.bullet) + " ";

        if (!clonabilityOutput.nonGroupV2CapableContacts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Contact contact : clonabilityOutput.nonGroupV2CapableContacts) {
                sb.append(bullet).append(contact.getCustomDisplayName());
            }

            final AlertDialog.Builder confirmationBuilder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_clone_group_error_missing_capability)
                    .setMessage(activity.getString(R.string.dialog_message_clone_group_error_missing_capability,
                            clonabilityOutput.groupDisplayName,
                            sb.toString()))
                    .setPositiveButton(R.string.button_label_ok, null);
            confirmationBuilder.create().show();
        } else if (!clonabilityOutput.nonContactOrNonChanelSerializedDetails.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String serializedContactDetails : clonabilityOutput.nonContactOrNonChanelSerializedDetails) {
                try {
                    JsonIdentityDetails details = AppSingleton.getJsonObjectMapper().readValue(serializedContactDetails, JsonIdentityDetails.class);
                    sb.append(bullet).append(details.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));
                } catch (Exception ignored) { }
            }

            final AlertDialog.Builder confirmationBuilder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_clone_group_warning_missing_members)
                    .setMessage(activity.getString(R.string.dialog_message_clone_group_warning_missing_members,
                            clonabilityOutput.groupDisplayName,
                            sb.toString()))
                    .setPositiveButton(R.string.button_label_proceed, ((DialogInterface dialog, int which) -> App.openGroupCreationActivityForCloning(activity, clonabilityOutput.absolutePhotoUrl, clonabilityOutput.serializedGroupDetails, clonabilityOutput.serializedGroupType, clonabilityOutput.clonableGroupContacts, clonabilityOutput.clonableGroupAdminContacts)))
                    .setNegativeButton(R.string.button_label_cancel, null);
            confirmationBuilder.create().show();
        } else {
            App.openGroupCreationActivityForCloning(activity, clonabilityOutput.absolutePhotoUrl, clonabilityOutput.serializedGroupDetails, clonabilityOutput.serializedGroupType, clonabilityOutput.clonableGroupContacts, clonabilityOutput.clonableGroupAdminContacts);
        }
    }
}
