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

package io.olvid.engine.metamanager;


import org.jose4j.jwk.JsonWebKey;

import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.GroupMembersChangedCallback;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.PreKeyBlobOnServer;
import io.olvid.engine.datatypes.containers.EncodedOwnedPreKey;
import io.olvid.engine.datatypes.containers.OwnedDeviceAndPreKey;
import io.olvid.engine.datatypes.containers.PreKey;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.TrustLevel;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.Group;
import io.olvid.engine.datatypes.containers.GroupInformation;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.containers.GroupWithDetails;
import io.olvid.engine.datatypes.containers.IdentityWithSerializedDetails;
import io.olvid.engine.datatypes.containers.KeycloakGroupV2UpdateOutput;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.datatypes.containers.UidAndPreKey;
import io.olvid.engine.datatypes.containers.UserData;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonGroupDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto;
import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.engine.types.identities.ObvOwnedDevice;
import io.olvid.engine.engine.types.sync.ObvBackupAndSyncDelegate;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.engine.identity.databases.sync.IdentityManagerSyncSnapshot;
import io.olvid.engine.identity.datatypes.KeycloakGroupBlob;

public interface IdentityDelegate {
    boolean isOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    boolean isActiveOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    Identity generateOwnedIdentity(Session session, String server, JsonIdentityDetails jsonIdentityDetails, ObvKeycloakState keycloakState, String deviceDisplayName, PRNGService prng) throws SQLException;
    void deleteOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    Identity[] getOwnedIdentities(Session session) throws SQLException;
    void updateLatestIdentityDetails(Session session, Identity ownedIdentity, JsonIdentityDetails jsonIdentityDetails) throws Exception;
    void updateOwnedIdentityPhoto(Session session, Identity ownedIdentity, String absolutePhotoUrl) throws Exception;
    void setOwnedDetailsDownloadedPhoto(Session session, Identity ownedIdentity, int version, byte[] decryptedPhoto) throws Exception;
    void setOwnedIdentityDetailsServerLabelAndKey(Session session, Identity ownedIdentity, int version, UID photoServerLabel, AuthEncKey photoServerKey) throws Exception;
    void createOwnedIdentityServerUserData(Session session, Identity ownedIdentity, UID photoServerLabel) throws SQLException;
    int publishLatestIdentityDetails(Session session, Identity ownedIdentity) throws SQLException;
    void discardLatestIdentityDetails(Session session, Identity ownedIdentity) throws SQLException;
    boolean setOwnedIdentityDetailsFromOtherDevice(Session session, Identity ownedIdentity, JsonIdentityDetailsWithVersionAndPhoto ownDetailsWithVersionAndPhoto) throws SQLException;
    JsonIdentityDetailsWithVersionAndPhoto[] getOwnedIdentityPublishedAndLatestDetails(Session session, Identity ownedIdentity) throws SQLException;
    String getSerializedPublishedDetailsOfOwnedIdentity(Session session, Identity ownedIdentity);
    JsonIdentityDetailsWithVersionAndPhoto getOwnedIdentityPublishedDetails(Session session, Identity ownedIdentity) throws SQLException;
    boolean isOwnedIdentityKeycloakManaged(Session session, Identity ownedIdentity) throws SQLException;
    Collection<ObvIdentity> getOwnedIdentitiesWithKeycloakPushTopic(Session session, String pushTopic) throws SQLException;
    ObvKeycloakState getOwnedIdentityKeycloakState(Session session, Identity ownedIdentity) throws SQLException;
    JsonWebKey getOwnedIdentityKeycloakSignatureKey(Session session, Identity ownedIdentity) throws SQLException;
    void setOwnedIdentityKeycloakSignatureKey(Session session, Identity ownedIdentity, JsonWebKey signatureKey) throws SQLException;
    void setKeycloakLatestRevocationListTimestamp(Session session, Identity ownedIdentity, long latestRevocationListTimestamp) throws SQLException;
    void unCertifyExpiredSignedContactDetails(Session session, Identity ownedIdentity, long latestRevocationListTimestamp);
    List<String> getKeycloakPushTopics(Session session, Identity ownedIdentity) throws SQLException;
    void verifyAndAddRevocationList(Session session, Identity ownedIdentity, List<String> signedRevocations) throws Exception;
    JsonKeycloakUserDetails verifyKeycloakSignature(Session session, Identity ownedIdentity, String signature);

    String getOwnedIdentityKeycloakServerUrl(Session session, Identity ownedIdentity) throws SQLException;
    void saveKeycloakAuthState(Session session, Identity ownedIdentity, String serializedAuthState) throws SQLException;
    void saveKeycloakJwks(Session session, Identity ownedIdentity, String serializedJwks) throws SQLException;
    void saveKeycloakApiKey(Session session, Identity ownedIdentity, String apiKey) throws SQLException;
    String getOwnedIdentityKeycloakUserId(Session session, Identity ownedIdentity) throws SQLException;
    void setOwnedIdentityKeycloakUserId(Session session, Identity ownedIdentity, String userId) throws SQLException;
    void bindOwnedIdentityToKeycloak(Session session, Identity ownedIdentity, String keycloakUserId, ObvKeycloakState keycloakState) throws Exception;
    int unbindOwnedIdentityFromKeycloak(Session session, Identity ownedIdentity) throws Exception; // return the version of the new details to publish
    boolean updateKeycloakPushTopicsIfNeeded(Session session, Identity ownedIdentity, String serverUrl, List<String> pushTopics) throws SQLException;
    void setOwnedIdentityKeycloakSelfRevocationTestNonce(Session session, Identity ownedIdentity, String serverUrl, String nonce) throws SQLException;
    String getOwnedIdentityKeycloakSelfRevocationTestNonce(Session session, Identity ownedIdentity, String serverUrl) throws SQLException;
    void updateKeycloakGroups(Session session, Identity ownedIdentity, List<String> signedGroupBlobs, List<String> signedGroupDeletions, List<String> signedGroupKicks, long keycloakCurrentTimestamp) throws Exception;
    void reactivateOwnedIdentityIfNeeded(Session session, Identity ownedIdentity) throws SQLException;
    void deactivateOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    void markOwnedIdentityForDeletion(Session session, Identity ownedIdentity) throws SQLException;


    UID[] getDeviceUidsOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    UID[] getOtherDeviceUidsOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    UID getCurrentDeviceUidOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    Identity getOwnedIdentityForCurrentDeviceUid(Session session, UID currentDeviceUid) throws SQLException;
    void addDeviceForOwnedIdentity(Session session, Identity ownedIdentity, UID deviceUid, String displayName, Long expirationTimestamp, Long lastRegistrationTimestamp, PreKeyBlobOnServer preKeyBlob, boolean channelCreationAlreadyInProgress) throws SQLException;
    void updateOwnedDevice(Session session, Identity ownedIdentity, UID deviceUid, String displayName, Long expirationTimestamp, Long lastRegistrationTimestamp, PreKeyBlobOnServer preKeyBlob) throws SQLException;
    void removeDeviceForOwnedIdentity(Session session, Identity ownedIdentity, UID deviceUid) throws SQLException;
    List<ObvOwnedDevice> getDevicesOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    List<OwnedDeviceAndPreKey> getDevicesAndPreKeysOfOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    String getCurrentDeviceDisplayName(Session session, Identity ownedIdentity) throws SQLException;
    EncodedOwnedPreKey getLatestPreKeyForOwnedIdentity(Session session, Identity ownedIdentity) throws SQLException;
    Encoded generateNewPreKey(Session session, Identity ownedIdentity, long expirationTimestamp) throws SQLException;
    void expireContactAndOwnedPreKeys(Session session, Identity ownedIdentity, String server, long serverTimestamp) throws SQLException;
    void expireCurrentDeviceOwnedPreKeys(Session session, Identity ownedIdentity, long currentServerTimestamp) throws SQLException;
    long getLatestChannelCreationPingTimestampForOwnedDevice(Session session, Identity ownedIdentity, UID ownedDeviceUid) throws SQLException;
    void setLatestChannelCreationPingTimestampForOwnedDevice(Session session, Identity ownedIdentity, UID ownedDeviceUid, long timestamp) throws Exception;


    void addContactIdentity(Session session, Identity contactIdentity, String serializedDetails, Identity ownedIdentity, TrustOrigin trustOrigin, boolean oneToOne) throws Exception;
    void addTrustOriginToContact(Session session, Identity contactIdentity, Identity ownedIdentity, TrustOrigin trustOrigin, boolean markContactAsOneToOne) throws SQLException;
    Identity[] getContactsOfOwnedIdentity(Session session, Identity ownedIdentity);
    JsonIdentityDetailsWithVersionAndPhoto trustPublishedContactDetails(Session session, Identity contactIdentity, Identity ownedIdentity) throws SQLException;
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
    TrustLevel getContactTrustLevel(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception;
    void deleteContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, boolean failIfGroup) throws Exception;
    byte[][] getGroupOwnerAndUidsOfGroupsOwnedByContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception;
    boolean isIdentityAnActiveContactOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean isIdentityAContactOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean isIdentityAOneToOneContactOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean isIdentityANotOneToOneContactOfOwnedIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    void setContactOneToOne(Session session, Identity ownedIdentity, Identity contactIdentity, boolean oneToOne) throws SQLException;
//    TrustLevel getContactIdentityTrustLevel(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    EnumSet<ObvContactActiveOrInactiveReason> getContactActiveOrInactiveReasons(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean forcefullyUnblockContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    boolean reBlockForcefullyUnblockedContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    void setContactRecentlyOnline(Session session, Identity ownedIdentity, Identity contactIdentity, boolean recentlyOnline) throws SQLException;

    // return true if a device was indeed added, false if the device already existed, and throws an exception if adding the device failed
    boolean addDeviceForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, UID deviceUid, PreKeyBlobOnServer preKeyBlob, boolean channelCreationAlreadyInProgress) throws SQLException;
    boolean isContactDeviceKnown(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws SQLException;
    void updateContactDevicePreKey(Session session, Identity ownedIdentity, Identity contactIdentity, UID deviceUid, PreKeyBlobOnServer preKeyBlob) throws SQLException;
    void removeDeviceForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity, UID deviceUid) throws SQLException;
    void removeAllDevicesForContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    UID[] getDeviceUidsOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    List<UidAndPreKey> getDeviceUidsAndPreKeysOfContactIdentity(Session session, Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    Map<Identity, Map<Identity, Set<UID>>> getAllDeviceUidsOfAllContactsOfAllOwnedIdentities(Session session) throws SQLException;
    long getLatestChannelCreationPingTimestampForContactDevice(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws SQLException;
    void setLatestChannelCreationPingTimestampForContactDevice(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid, long timestamp) throws Exception;

    List<ObvCapability> getContactCapabilities(Identity ownedIdentity, Identity contactIdentity) throws SQLException;
    String[] getContactDeviceCapabilities(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid) throws SQLException;
    void setContactDeviceCapabilities(Session session, Identity ownedIdentity, Identity contactIdentity, UID contactDeviceUid, String[] rawDeviceCapabilities) throws Exception;
    List<ObvCapability> getOwnCapabilities(Identity ownedIdentity) throws SQLException;
    List<ObvCapability> getCurrentDevicePublishedCapabilities(Session session, Identity ownedIdentity) throws Exception;
    void setCurrentDevicePublishedCapabilities(Session session, Identity ownedIdentity, List<ObvCapability> capabilities) throws Exception;
    String[] getOtherOwnedDeviceCapabilities(Session session, Identity ownedIdentity, UID otherDeviceUid) throws Exception;
    void setOtherOwnedDeviceCapabilities(Session session, Identity ownedIdentity, UID otherOwnedDeviceUID, String[] rawDeviceCapabilities) throws Exception;

    Seed getDeterministicSeedForOwnedIdentity(Identity ownedIdentity, byte[] diversificationTag) throws Exception;

    byte[] signIdentities(Session session, Constants.SignatureContext signatureContext, Identity[] identities, Identity ownedIdentity, PRNGService prng) throws Exception;
    byte[] signChannel(Session session, Constants.SignatureContext signatureContext, Identity contactIdentity, UID contactDeviceUid, Identity ownedIdentity, UID ownedDeviceUid, PRNGService prng) throws Exception;
    byte[] signBlock(Session session, Constants.SignatureContext signatureContext, byte[] block, Identity ownedIdentity, PRNGService prng) throws Exception;
    byte[] signGroupInvitationNonce(Session session, Constants.SignatureContext signatureContext, GroupV2.Identifier groupIdentifier, byte[] nonce, Identity contactIdentity, Identity ownedIdentity, PRNGService prng) throws Exception;

    void createContactGroup(Session session, Identity ownedIdentity, GroupInformation groupInformation, Identity[] groupMembers, IdentityWithSerializedDetails[] pendingGroupMembers, boolean createdByMeOnOtherDevice) throws Exception;
    void leaveGroup(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception;
    void addPendingMembersToGroup(Session session, byte[] groupUid, Identity ownedIdentity, Identity[] contactIdentities, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception;
    void removeMembersAndPendingFromGroup(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity[] contactIdentities, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception;
    void addGroupMemberFromPendingMember(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception;
    void demoteGroupMemberToDeclinedPendingMember(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity, Identity contactIdentity, GroupMembersChangedCallback groupMembersChangedCallback) throws Exception;
    void setPendingMemberDeclined(Session session, byte[] groupUid, Identity ownedIdentity, Identity contactIdentity, boolean declined) throws Exception;
    void updateGroupMembersAndDetails(Session session, Identity ownedIdentity, GroupInformation groupInformation, HashSet<IdentityWithSerializedDetails> groupMembers, HashSet<IdentityWithSerializedDetails> pendingMembers, long membersVersion) throws Exception;
    void deleteGroup(Session session, byte[] groupOwnerAndUid, Identity ownedIdentity) throws Exception;
    void resetGroupMembersAndPublishedDetailsVersions(Session session, Identity ownedIdentity, GroupInformation groupInformation) throws Exception;
    void forcefullyRemoveMemberOrPendingFromJoinedGroup(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, Identity contactIdentity) throws SQLException;
    GroupWithDetails[] getGroupsForOwnedIdentity(Session session, Identity ownedIdentity) throws Exception;
    Group getGroup(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws Exception;
    GroupWithDetails getGroupWithDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws Exception;
    GroupInformation getGroupInformation(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws Exception;
    JsonGroupDetailsWithVersionAndPhoto[] getGroupPublishedAndLatestOrTrustedDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException;
    String getGroupPhotoUrl(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException;
    JsonGroupDetailsWithVersionAndPhoto trustPublishedGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException;
    void updateLatestGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, JsonGroupDetails jsonGroupDetails) throws Exception;
    void setOwnedGroupDetailsServerLabelAndKey(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, int version, UID photoServerLabel, AuthEncKey photoServerKey) throws Exception;
    void createGroupV1ServerUserData(Session session, Identity ownedIdentity, UID photoServerLabel, byte[] groupOwnerAndUid) throws SQLException;
    void updateOwnedGroupPhoto(Session session, Identity ownedIdentity,  byte[] groupOwnerAndUid, String absolutePhotoUrl, boolean partOfGroupCreation) throws Exception;
    void setContactGroupDownloadedPhoto(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid, int version, byte[] photo) throws Exception;
    int publishLatestGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException;
    void discardLatestGroupDetails(Session session, Identity ownedIdentity, byte[] groupOwnerAndUid) throws SQLException;
    byte[][] getGroupOwnerAndUidOfGroupsWhereContactIsPending(Session session, Identity contactIdentity, Identity ownedIdentity);
    byte[][] getGroupOwnerAndUidsOfGroupsContainingContact(Session session, Identity contactIdentity, Identity ownedIdentity) throws SQLException;
    void refreshMembersOfGroupsOwnedByGroupOwner(UID currentDeviceUid, Identity remoteIdentity);
    void pushMembersOfOwnedGroupsToContact(UID currentDeviceUid, Identity remoteIdentity);


    // region groups v2

    void createNewGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, String serializedGroupDetails, String absolutePhotoUrl, GroupV2.ServerPhotoInfo serverPhotoInfo, byte[] verifiedAdministratorsChain, GroupV2.BlobKeys blobKeys, byte[] ownGroupInvitationNonce, List<String> ownPermissionStrings, HashSet<GroupV2.IdentityAndPermissionsAndDetails> otherGroupMembers, String serializedGroupType) throws Exception;
    boolean createJoinedGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, GroupV2.BlobKeys blobKeys, GroupV2.ServerBlob serverBlob, boolean createdByMeOnOtherDevice) throws Exception;
    GroupV2.ServerBlob getGroupV2ServerBlob(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    String getGroupV2PhotoUrl(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    void deleteGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    void freezeGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    void unfreezeGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    Integer getGroupV2Version(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    String getGroupV2JsonGroupType(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    boolean isGroupV2Frozen(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    GroupV2.BlobKeys getGroupV2BlobKeys(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    HashSet<GroupV2.IdentityAndPermissions> getGroupV2OtherMembersAndPermissions(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception;
    boolean getGroupV2HasOtherAdminMember(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception;
    List<Identity> updateGroupV2WithNewBlob(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, GroupV2.ServerBlob serverBlob, GroupV2.BlobKeys blobKeys, boolean updatedByMe) throws SQLException;
    List<Identity> getGroupV2MembersAndPendingMembersFromNonce(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, byte[] groupMemberInvitationNonce) throws Exception;
    byte[] getGroupV2OwnGroupInvitationNonce(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    void moveGroupV2PendingMemberToMembers(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Identity groupMemberIdentity) throws Exception;
    void setGroupV2DownloadedPhoto(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, GroupV2.ServerPhotoInfo serverPhotoInfo, byte[] photov) throws Exception;
    ObvGroupV2 getObvGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception;
    int trustGroupV2PublishedDetails(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    GroupV2.ServerPhotoInfo getGroupV2PublishedServerPhotoInfo(Session session, Identity ownedIdentity, byte[] bytesGroupIdentifier);
    ObvGroupV2.ObvGroupV2DetailsAndPhotos getGroupV2DetailsAndPhotos(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier);
    void setUpdatedGroupV2PhotoUrl(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, int version, String absolutePhotoUrl) throws Exception;
    GroupV2.AdministratorsChain getGroupV2AdministratorsChain(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception;
    boolean getGroupV2AdminStatus(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws Exception;
    List<ObvGroupV2> getObvGroupsV2ForOwnedIdentity(Session session, Identity ownedIdentity) throws Exception;
    GroupV2.IdentifierVersionAndKeys[] getServerGroupsV2IdentifierVersionAndKeysForContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception;
    GroupV2.IdentifierVersionAndKeys[] getAllServerGroupsV2IdentifierVersionAndKeys(Session session, Identity ownedIdentity) throws Exception;
    GroupV2.IdentifierAndAdminStatus[] getServerGroupsV2IdentifierAndMyAdminStatusForContact(Session session, Identity ownedIdentity, Identity contactIdentity) throws Exception;
    void initiateGroupV2BatchKeysResend(UID currentDeviceUid, Identity contactIdentity, UID contactDeviceUid);
    void forcefullyRemoveMemberOrPendingFromNonAdminGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Identity contactIdentity) throws SQLException;
    Long getGroupV2LastModificationTimestamp(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier) throws SQLException;
    byte[] createKeycloakGroupV2(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, KeycloakGroupBlob keycloakGroupBlob);
    KeycloakGroupV2UpdateOutput updateKeycloakGroupV2WithNewBlob(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, KeycloakGroupBlob keycloakGroupBlob) throws Exception;
    void rePingOrDemoteContactFromAllKeycloakGroups(Session session, Identity ownedIdentity, Identity contactIdentity, boolean certifiedByOwnKeycloak, String lastKnownSerializedCertifiedDetails) throws SQLException;
    boolean isIdentityAPendingGroupV2Member(Session session, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, Identity obliviousChannelContactIdentity) throws SQLException;
    // endregion


    void initiateBackup(final BackupDelegate backupDelegate, final String tag, final UID backupKeyUid, final int version);
    ObvIdentity[] restoreOwnedIdentitiesFromBackup(String serializedJsonPojo, String deviceDisplayName, PRNGService prng);
    void restoreContactsAndGroupsFromBackup(String serializedJsonPojo, ObvIdentity[] restoredOwnedIdentities, long backupTimestamp);

    // userData
    UserData[] getAllUserData(Session session) throws Exception;
    UserData getUserData(Session session, Identity ownedIdentity, UID label) throws Exception;
    void deleteUserData(Session session, Identity ownedIdentity, UID label) throws Exception;
    void updateUserDataNextRefreshTimestamp(Session session, Identity ownedIdentity, UID label) throws Exception;

    // device sync
    void processSyncItem(Session session, Identity ownedIdentity, ObvSyncAtom obvSyncAtom) throws Exception;

    ObvBackupAndSyncDelegate getSyncDelegateWithinTransaction(Session session);

    ObvIdentity restoreTransferredOwnedIdentity(Session session, String deviceName, IdentityManagerSyncSnapshot node) throws Exception;

    void downloadAllUserData(Session session) throws Exception;
}
