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

package io.olvid.engine.protocol.datatypes;


import java.util.HashSet;
import java.util.List;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;

public interface ProtocolStarterDelegate {
    void startDeviceDiscoveryProtocol(Identity ownedIdentity, Identity contactIdentity) throws Exception;
    void startDeviceDiscoveryProtocolWithinTransaction(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception;
    void startTrustEstablishmentProtocol(Identity ownedIdentity, Identity contactIdentity, String contactDisplayName) throws Exception;
    void startMutualScanTrustEstablishmentProtocol(Identity ownedIdentity, Identity contactIdentity, byte[] signature) throws Exception;
    void startChannelCreationWithContactDeviceProtocol(Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws Exception;
    void startContactMutualIntroductionProtocol(Identity ownedIdentity, Identity contactIdentityA, Identity[] contactIdentities) throws Exception;
    void startGroupCreationProtocol(Identity ownedIdentity, String serializedGroupDetailsWithVersionAndPhoto, String photoUrl, HashSet<IdentityWithSerializedDetails> groupMemberIdentitiesAndDisplayNames) throws Exception;
    void startIdentityDetailsPublicationProtocol(Session session, Identity ownedIdentity, int version) throws Exception;
    void startGroupDetailsPublicationProtocol(Session session, Identity ownedIdentity, byte[] groupUid) throws Exception;
    void startOneToOneInvitationProtocol(Identity ownedIdentity, Identity contactIdentity) throws Exception;

    void deleteContact(Identity ownedIdentity, Identity contactIdentity) throws Exception;
    void downgradeOneToOneContact(Identity ownedIdentity, Identity contactIdentity) throws Exception;
    void addKeycloakContact(Identity ownedIdentity, Identity contactIdentity, String signedContactDetails) throws Exception;
    void startProtocolForBindingOwnedIdentityToKeycloakWithinTransaction(Session session, Identity ownedIdentity, ObvKeycloakState keycloakState, String keycloakUserId) throws Exception;
    void updateCurrentDeviceCapabilitiesForOwnedIdentity(Session session, Identity ownedIdentity, List<ObvCapability> newOwnCapabilities) throws Exception;
    void startProtocolForUnbindingOwnedIdentityFromKeycloak(Identity ownedIdentity) throws Exception;
    void deleteOwnedIdentityAndNotifyContacts(Session session, Identity ownedIdentity) throws Exception;

    void inviteContactsToGroup(byte[] groupOwnerAndUid, Identity ownedIdentity, HashSet<Identity> newMembersIdentity) throws Exception;
    void reinvitePendingToGroup(byte[] groupOwnerAndUid, Identity ownedIdentity, Identity pendingMemberIdentity) throws Exception;
    void removeContactsFromGroup(byte[] groupOwnerAndUid, Identity ownedIdentity, HashSet<Identity> removedMemberIdentities) throws Exception;
    void leaveGroup(byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception;
    void disbandGroup(byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception;
    void queryGroupMembers(byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception;
    void reinviteAndPushMembersToContact(byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity) throws Exception;

    void startDownloadIdentityPhotoProtocolWithinTransaction(Session session, Identity ownedIdentity, Identity contactIdentity, JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto) throws Exception;
    void startDownloadGroupPhotoWithinTransactionProtocol(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, JsonGroupDetailsWithVersionAndPhoto jsonGroupDetailsWithVersionAndPhoto) throws Exception;

}
