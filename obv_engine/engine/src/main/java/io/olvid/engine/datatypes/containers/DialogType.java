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

package io.olvid.engine.datatypes.containers;


import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.engine.types.ObvTransferStep;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.engine.engine.types.identities.ObvGroupV2;

public class DialogType {
    public static final int DELETE_DIALOG_ID = -1;
    public static final int INVITE_SENT_DIALOG_ID = 0;
    public static final int ACCEPT_INVITE_DIALOG_ID = 1;
    public static final int SAS_EXCHANGE_DIALOG_ID = 2;
    public static final int SAS_CONFIRMED_DIALOG_ID = 3;
    //public static final int MUTUAL_TRUST_CONFIRMED_DIALOG_ID = 4;
    public static final int INVITE_ACCEPTED_DIALOG_ID = 5;
    public static final int ACCEPT_MEDIATOR_INVITE_DIALOG_ID = 6;
    public static final int MEDIATOR_INVITE_ACCEPTED_DIALOG_ID = 7;
    public static final int ACCEPT_GROUP_INVITE_DIALOG_ID = 8;
//    public static final int INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_ID = 9;
//    public static final int INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_ID = 10;
//    public static final int AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_ID = 11;
//    public static final int GROUP_JOINED_DIALOG_ID = 12;
    public static final int ONE_TO_ONE_INVITATION_SENT_DIALOG_ID = 13;
    public static final int ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_ID = 14;
    public static final int ACCEPT_GROUP_V2_INVITATION_DIALOG_ID = 15;
    public static final int GROUP_V2_FROZEN_INVITATION_DIALOG_ID = 16;
    public static final int SYNC_ITEM_TO_APPLY_DIALOG_ID = 17;
    public static final int TRANSFER_DIALOG_ID = 18;


    public final int id;
    public final String contactDisplayNameOrSerializedDetails;
    public final Identity contactIdentity;
    public final byte[] sasToDisplay;
    public final byte[] sasEntered;
    public final Identity mediatorOrGroupOwnerIdentity;
    public final String serializedGroupDetails;
    public final UID groupUid;
    public final Identity[] pendingGroupMemberIdentities;
    public final String[] pendingGroupMemberSerializedDetails;
    public final Long serverTimestamp;
    public final ObvGroupV2 obvGroupV2;
    public final ObvSyncAtom obvSyncAtom;
    public final ObvTransferStep obvTransferStep;

    private DialogType(int id, String contactDisplayNameOrSerializedDetails, Identity contactIdentity, byte[] sasToDisplay, byte[] sasEntered, Identity mediatorOrGroupOwnerIdentity, String serializedGroupDetails, UID groupUid, Identity[] pendingGroupMemberIdentities, String[] pendingGroupMemberSerializedDetails, Long serverTimestamp, ObvGroupV2 obvGroupV2, ObvSyncAtom obvSyncAtom, ObvTransferStep obvTransferStep) {
        this.id = id;
        this.contactDisplayNameOrSerializedDetails = contactDisplayNameOrSerializedDetails;
        this.contactIdentity = contactIdentity;
        this.sasToDisplay = sasToDisplay;
        this.sasEntered = sasEntered;
        this.mediatorOrGroupOwnerIdentity = mediatorOrGroupOwnerIdentity;
        this.serializedGroupDetails = serializedGroupDetails;
        this.groupUid = groupUid;
        this.pendingGroupMemberIdentities = pendingGroupMemberIdentities;
        this.pendingGroupMemberSerializedDetails = pendingGroupMemberSerializedDetails;
        this.serverTimestamp = serverTimestamp;
        this.obvGroupV2 = obvGroupV2;
        this.obvSyncAtom = obvSyncAtom;
        this.obvTransferStep = obvTransferStep;
    }

    public static DialogType createDeleteDialog() {
        return new DialogType(DELETE_DIALOG_ID, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static DialogType createInviteSentDialog(String contactDisplayName, Identity contactIdentity) {
        return new DialogType(INVITE_SENT_DIALOG_ID, contactDisplayName, contactIdentity, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static DialogType createAcceptInviteDialog(String contactSerializedDetails, Identity contactIdentity, long serverTimestamp) {
        return new DialogType(ACCEPT_INVITE_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, null, null, null, null, null, serverTimestamp, null, null, null);
    }

    public static DialogType createSasExchangeDialog(String contactSerializedDetails, Identity contactIdentity, byte[] sasToDisplay, long serverTimestamp) {
        return new DialogType(SAS_EXCHANGE_DIALOG_ID, contactSerializedDetails, contactIdentity, sasToDisplay, null, null, null, null, null, null, serverTimestamp, null, null, null);
    }

    public static DialogType createSasConfirmedDialog(String contactSerializedDetails, Identity contactIdentity, byte[] sasToDisplay, byte[] sasEntered) {
        return new DialogType(SAS_CONFIRMED_DIALOG_ID, contactSerializedDetails, contactIdentity, sasToDisplay, sasEntered, null, null, null, null, null, null, null, null, null);
    }

    public static DialogType createInviteAcceptedDialog(String contactSerializedDetails, Identity contactIdentity) {
        return new DialogType(INVITE_ACCEPTED_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static DialogType createAcceptMediatorInviteDialog(String contactSerializedDetails, Identity contactIdentity, Identity mediatorIdentity, long serverTimestamp) {
        return new DialogType(ACCEPT_MEDIATOR_INVITE_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, mediatorIdentity, null, null, null, null, serverTimestamp, null, null, null);
    }

    public static DialogType createMediatorInviteAcceptedDialog(String contactSerializedDetails, Identity contactIdentity, Identity mediatorIdentity) {
        return new DialogType(MEDIATOR_INVITE_ACCEPTED_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, mediatorIdentity, null, null, null, null, null, null, null, null);
    }

    public static DialogType createAcceptGroupInviteDialog(String serializedGroupDetails, UID groupUid, Identity groupOwnerIdentity, Identity[] pendingGroupMemberIdentities, String[]  pendingGroupMemberSerializedDetails, long serverTimestamp) {
        return new DialogType(ACCEPT_GROUP_INVITE_DIALOG_ID, null, null, null, null, groupOwnerIdentity, serializedGroupDetails, groupUid, pendingGroupMemberIdentities, pendingGroupMemberSerializedDetails, serverTimestamp, null, null, null);
    }

    public static DialogType createOneToOneInvitationSentDialog(Identity contactIdentity) {
        return new DialogType(ONE_TO_ONE_INVITATION_SENT_DIALOG_ID, null, contactIdentity, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static DialogType createAcceptOneToOneInvitationDialog(Identity contactIdentity, long serverTimestamp) {
        return new DialogType(ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_ID, null, contactIdentity, null, null, null, null, null, null, null, serverTimestamp, null, null, null);
    }

    public static DialogType createGroupV2InvitationDialog(Identity inviterIdentity, ObvGroupV2 obvGroupV2) {
        return new DialogType(ACCEPT_GROUP_V2_INVITATION_DIALOG_ID, null, null, null, null, inviterIdentity, null, null, null, null, null, obvGroupV2, null, null);
    }

    public static DialogType createGroupV2FrozenInvitationDialog(Identity inviterIdentity, ObvGroupV2 obvGroupV2) {
        return new DialogType(GROUP_V2_FROZEN_INVITATION_DIALOG_ID, null, null, null, null, inviterIdentity, null, null, null, null, null, obvGroupV2, null, null);
    }

    public static DialogType createSyncItemToApplyDialog(ObvSyncAtom obvSyncAtom) {
        return new DialogType(SYNC_ITEM_TO_APPLY_DIALOG_ID, null, null, null, null, null, null, null, null, null, null, null, obvSyncAtom, null);
    }

    public static DialogType createTransferDialog(ObvTransferStep obvTransferStep) {
        return new DialogType(TRANSFER_DIALOG_ID, null, null, null, null, null, null, null, null, null, null, null, null, obvTransferStep);
    }
}
