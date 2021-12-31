/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

package io.olvid.engine.metamanager;


import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.GroupMembersChangedCallback;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.Group;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.GroupWithDetails;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.containers.UserData;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;

public interface IdentityDelegate {
    boolean isOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    boolean isActiveOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    Identity generateOwnedIdentity(Session session, String server, JsonIdentityDetails jsonIdentityDetails, UUID apiKey, ObvKeycloakState keycloakState, PRNGService prng) throws SQLException;
    void deleteOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    Identity[] getOwnedIdentities(Session session) throws SQLException;
    void updateLatestIdentityDetails(Session session, Identity ownedIdentity, JsonIdentityDetails jsonIdentityDetails) throws Exception;
    void updateOwnedIdentityPhoto(Session session, Identity ownedIdentity, String absolutePhotoUrl) throws Exception;
    void setOwnedDetailsDownloadedPhoto(Session session, Identity ownedIdentity, int version, byte[] decryptedPhoto) throws Exception;
    void setOwnedIdentityDetailsServerLabelAndKey(Session session, Identity ownedIdentity, int version, UID photoServerLabel, AuthEncKey photoServerKey) throws Exception;
    int publishLatestIdentityDetails(Session session, Identity ownedIdentity) throws SQLException;
    void discardLatestIdentityDetails(Session session, Identity ownedIdentity) throws SQLException;
    JsonIdentityDetailsWithVersionAndPhoto[] getOwnedIdentityPublishedAndLatestDetails(Session session, Identity ownedIdentity) throws SQLException;
    String getSerializedPublishedDetailsOfOwnedIdentity(Session session, Identity ownedIdentity);
    JsonIdentityDetailsWithVersionAndPhoto getOwnedIdentityPublishedDetails(Session session, Identity ownedIdentity) throws SQLException;
    boolean isOwnedIdentityKeycloakManaged(Session session, Identity ownedIdentity) throws SQLException;
    List<ObvIdentity> getOwnedIdentitiesWithKeycloakPushTopic(Session session, String pushTopic) throws SQLException;
    ObvKeycloakState getOwnedIdentityKeycloakState(Session session, Identity ownedIdentity) throws SQLException;
    JsonWebKeySet getOwnedIdentityKeycloakJwks(Session session, Identity ownedIdentity) throws SQLException;
    JsonWebKey getOwnedIdentityKeycloakSignatureKey(Session session, Identity ownedIdentity) throws SQLException;
    void setOwnedIdentityKeycloakSignatureKey(Session session, Identity ownedIdentity, JsonWebKey signatureKey) throws SQLException;
    void setKeycloakLatestRevocationListTimestamp(Session session, Identity ownedIdentity, long latestRevocationListTimestamp) throws SQLException;
    void unCertifyExpiredSignedContactDetails(Session session, Identity ownedIdentity, long latestRevocationListTimestamp);
    JsonWebKeySet getTrustedKeycloakJwks(Session session, Identity ownedIdentity, String keycloakServerUrl) throws SQLException;
    JsonWebKey getTrustedKeycloakSignatureKey(Session session, Identity ownedIdentity, String keycloakServerUrl) throws SQLException;
    List<String> getKeycloakPushTopics(Session session, Identity ownedIdentity) throws SQLException;
    void verifyAndAddRevocationList(Session session, Identity ownedIdentity, List<String> signedRevocations) throws Exception;
    JsonKeycloakUserDetails verifyKeycloakSignature(Session session, Identity ownedIdentity, String signature);

    String getOwnedIdentityKeycloakServerUrl(Session session, Identity ownedIdentity) throws SQLException;
    void saveKeycloakAuthState(Session session, Identity ownedIdentity, String serializedAuthState) throws SQLException;
    void saveKeycloakJwks(Session session, Identity ownedIdentity, String serializedJwks) throws SQLException;
    String getOwnedIdentityKeycloakUserId(Session session, Identity ownedIdentity) throws SQLException;
    void setOwnedIdentityKeycloakUserId(Session session, Identity ownedIdentity, String userId) throws SQLException;
    void bindOwnedIdentityToKeycloak(Session session, Identity ownedIdentity, String keycloakUserId, ObvKeycloakState keycloakState) throws Exception;
    int unbindOwnedIdentityFromKeycloak(Session session, Identity ownedIdentity) throws Exception; // return the version of the new details to publish
    void updateApiKeyOfOwnedIdentity(Session session, Identity ownedIdentity, UUID newApiKey) throws SQLException;
    boolean updateKeycloakPushTopicsIfNeeded(Session session, Identity ownedIdentity, String serverUrl, List<String> pushTopics) throws SQLException;
    void setOwnedIdentityKeycloakSelfRevocationTestNonce(Session session, Identity ownedIdentity, String serverUrl, String nonce) throws SQLException;
    String getOwnedIdentityKeycloakSelfRevocationTestNonce(Session session, Identity ownedIdentity, String serverUrl) throws SQLException;
    void reactivateOwnedIdentityIfNeeded(Session session, Identity ownedIdentity) throws SQLException;
    void deactivateOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;


    UID[] getDeviceUidsOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    UID[] getOtherDeviceUidsOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    UID getCurrentDeviceUidOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    Identity getOwnedIdentityForDeviceUid(Session session, UID currentDeviceUid) throws SQLException;
    void addDeviceForOwnedIdentity(Session session, UID deviceUid, Identity ownedIdentity) throws SQLException;
    boolean isRemoteDeviceUidOfOwnedIdentity(Session session, UID deviceUid, Identity ownedIdentity) throws SQLException;


    void addContactIdentity(Session session, Identity contactIdentity, String serializedDetails, Identity ownedIdentity, TrustOrigin trustOrigin) throws Exception;
    void addTrustOriginToContact(Session session, Identity contacIdentity, Identity ownedIdentity, TrustOrigin trustOrigin) throws SQLException;
    Identity[] getContactsOfOwnedIdentity(Session session, Identity ownedIdentity);
    void trustPublishedContactDetails(Session session, Identity contactIdentity, Identity ownedIdentity) throws SQLException;
    void setContactPublishedDetails(Session session, Identity contactIdentity, Identity ownedIdentity, JsonIdentityDetailsWithVersionAndPhoto jsonIdentityDetailsWithVersionAndPhoto, boolean allowDowngrade) throws Exception;
    void setContactDetailsDownloadedPhoto(Session session, Identity contactIdentity, Identity ownedIdentity, int version, byte[] photo) throws Exception;
    String getSerializedPublishedDetailsOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity);
    JsonIdentityDetails getContactIdentityTrustedDetails(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    String getContactTrustedDetailsPhotoUrl(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean contactHasUntrustedPublishedDetails(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    JsonIdentityDetailsWithVersionAndPhoto[] getContactPublishedAndTrustedDetails(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean isContactIdentityCertifiedByOwnKeycloak(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    void unmarkAllCertifiedByOwnKeycloakContacts(Session session, Identity ownedIdentity) throws SQLException;
    void reCheckAllCertifiedByOwnKeycloakContacts(Session session, Identity ownedIdentity) throws SQLException;
    TrustOrigin[] getTrustOriginsOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity);
    void deleteContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, boolean failIfGroup) throws Exception;
    byte[][] getGroupOwnerAndUidsOfGroupsOwnedByContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception;
    boolean isIdentityAnActiveContactOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean isIdentityAContactIdentityOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    TrustLevel getContactIdentityTrustLevel(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    EnumSet<ObvContactActiveOrInactiveReason> getContactActiveOrInactiveReasons(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean forcefullyUnblockContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean reBlockForcefullyUnblockedContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;

    void addDeviceForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, UID deviceUid) throws SQLException;
    void removeDeviceForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, UID deviceUid) throws SQLException;
    void removeAllDevicesForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    UID[] getDeviceUidsOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    Map<Identity, Map<Identity, Set<UID>>> getAllDeviceUidsOfAllContactsOfAllOwnedIdentities(Session session) throws SQLException;

    Seed getDeterministicSeedForOwnedIdentity(Identity ownedIdentity, byte[] diversificationTag) throws Exception;

    byte[] signIdentities(Session session, byte[] prefix, Identity[] identities, Identity ownedIdentity, PRNGService prng) throws Exception;
    boolean verifyIdentitiesSignature(byte[] prefix, Identity[] identities, Identity signerIdentity, byte[] signature) throws Exception;

    void createContactGroup(Session session, Identity ownedIdentity, GroupInformation groupInformation, Identity[] groupMembers, IdentityWithSerializedDetails[] pendingGroupMembers) throws Exception;
    void leaveGroup(Session session, byte[] groupUid, Identity ownedIdentity) throws Exception;
    void addPendingMembersToGroup(Session session, byte[] groupUid, Identity ownedIdentity, Identity[] contactIdentities, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception;
    void removeMembersAndPendingFromGroup(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity[] contactIdentities, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception;
    void addGroupMemberFromPendingMember(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception;
    void demoteGroupMemberToDeclinedPendingMember(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception;
    void setPendingMemberDeclined(Session session, byte[] groupUid, Identity ownedIdentity, Identity contactIdentity, boolean declined) throws Exception;
    void updateGroupMembersAndDetails(Session session, Identity ownedIdentity, GroupInformation groupInformation, HashSet<IdentityWithSerializedDetails> groupMembers, HashSet<IdentityWithSerializedDetails> pendingMembers, long membersVersion) throws Exception;
    void deleteGroup(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception;
    void resetGroupMembersAndPublishedDetailsVersions(Session session, Identity ownedIdentity, GroupInformation groupInformation) throws Exception;

    GroupWithDetails[] getGroupsForOwnedIdentity(Session session, Identity ownedIdentity) throws Exception;
    Group getGroup(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws Exception;
    GroupWithDetails getGroupWithDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws Exception;
    GroupInformation getGroupInformation(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws Exception;
    JsonGroupDetailsWithVersionAndPhoto[] getGroupPublishedAndLatestOrTrustedDetails(Session session, Identity ownedIdentity, byte[] groupUid) throws SQLException;
    String getGroupPhotoUrl(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException;
    void trustPublishedGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException;
    void updateLatestGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, JsonGroupDetails jsonGroupDetails) throws Exception;
    void setOwnedGroupDetailsServerLabelAndKey(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, int version, UID photoServerLabel, AuthEncKey photoServerKey) throws Exception;
    void updateOwnedGroupPhoto(Session session, Identity ownedIdentity,  byte[] groupOwnerAndUid, String absolutePhotoUrl, boolean partOfGroupCreation) throws Exception;
    void setContactGroupDownloadedPhoto(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, int version, byte[] photo) throws Exception;
    int publishLatestGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException;
    void discardLatestGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException;
    byte[][] getGroupOwnerAndUidOfGroupsWhereContactIsPending(Session session, Identity contactIdentity, Identity ownedIdentity);
    void refreshMembersOfGroupsOwnedByGroupOwner(UID currentDeviceUid, Identity remoteIdentity);
    void pushMembersOfOwnedGroupsToContact(UID currentDeviceUid, Identity remoteIdentity);


    void initiateBackup(final BackupDelegate backupDelegate, final String tag, final UID backupKeyUid, final int version);
    ObvIdentity[] restoreOwnedIdentitiesFromBackup(String serializedJsonPojo, PRNGService prng);
    void restoreContactsAndGroupsFromBackup(String serializedJsonPojo, Identity[] restoredOwnedIdentities, long backupTimestamp);

    // userData
    UserData[] getAllUserData(Session session) throws Exception;
    UserData getUserData(Session session, Identity ownedIdentity, UID label) throws Exception;
    void deleteUserData(Session session, Identity ownedIdentity, UID label) throws Exception;
    void updateUserDataNextRefreshTimestamp(Session session, Identity ownedIdentity, UID label) throws Exception;

}
