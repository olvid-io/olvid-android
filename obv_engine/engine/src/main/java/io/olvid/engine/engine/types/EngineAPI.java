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

package io.olvid.engine.engine.types;

import org.jose4j.jwk.JsonWebKey;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvGroupV2;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl;
import io.olvid.engine.engine.types.identities.ObvOwnedDevice;
import io.olvid.engine.engine.types.identities.ObvTrustOrigin;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;

public interface EngineAPI {
    enum ApiKeyPermission {
        CALL,
        WEB_CLIENT,
        MULTI_DEVICE,
    }

    enum ApiKeyStatus {
        UNKNOWN,
        VALID,
        LICENSES_EXHAUSTED,
        EXPIRED,
        OPEN_BETA_KEY,
        FREE_TRIAL_KEY,
        AWAITING_PAYMENT_GRACE_PERIOD,
        AWAITING_PAYMENT_ON_HOLD,
        FREE_TRIAL_KEY_EXPIRED,
    }

    // Engine notifications
    void addNotificationListener(String notificationName, EngineNotificationListener engineNotificationListener);
    void removeNotificationListener(String notificationName, EngineNotificationListener engineNotificationListener);
    void startSendingNotifications();
    void stopSendingNotifications();


    // ObvOwnedIdentity
    String getServerOfIdentity(byte[] bytesIdentity);
    ObvIdentity[] getOwnedIdentities() throws Exception;
    ObvIdentity getOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception;
    ObvIdentity generateOwnedIdentity(String server, JsonIdentityDetails jsonIdentityDetails, ObvKeycloakState keycloakState, String deviceDisplayName);
    RegisterApiKeyResult registerOwnedIdentityApiKeyOnServer(byte[] bytesOwnedIdentity, UUID apiKey);
    void recreateServerSession(byte[] bytesOwnedIdentity);
    void deleteOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception;
    JsonIdentityDetailsWithVersionAndPhoto[] getOwnedIdentityPublishedAndLatestDetails(byte[] bytesOwnedOdentity) throws Exception;
    ObvKeycloakState getOwnedIdentityKeycloakState(byte[] bytesOwnedIdentity) throws Exception;
    void saveKeycloakAuthState(byte[] bytesOwnedIdentity, String serializedAuthState) throws Exception;
    void saveKeycloakJwks(byte[] bytesOwnedIdentity, String serializedJwks) throws Exception;
    void saveKeycloakApiKey(byte[] bytesOwnedIdentity, String apiKey) throws Exception;
    Collection<ObvIdentity> getOwnedIdentitiesWithKeycloakPushTopic(String pushTopic) throws Exception;
    String getOwnedIdentityKeycloakUserId(byte[] bytesOwnedIdentity) throws Exception;
    void setOwnedIdentityKeycloakUserId(byte[] bytesOwnedIdentity, String id) throws Exception;
    JsonWebKey getOwnedIdentityKeycloakSignatureKey(byte[] bytesOwnedIdentity) throws Exception;
    void setOwnedIdentityKeycloakSignatureKey(byte[] bytesOwnedIdentity, JsonWebKey signatureKey) throws Exception;
    ObvIdentity bindOwnedIdentityToKeycloak(byte[] bytesOwnedIdentity, ObvKeycloakState keycloakState, String keycloakUserId);
    void unbindOwnedIdentityFromKeycloak(byte[] bytesOwnedIdentity);
    void updateKeycloakPushTopicsIfNeeded(byte[] bytesOwnedIdentity, String serverUrl, List<String> pushTopics);
    void updateKeycloakRevocationList(byte[] bytesOwnedIdentity, long latestRevocationListTimestamp, List<String> signedRevocations);
    void setOwnedIdentityKeycloakSelfRevocationTestNonce(byte[] bytesOwnedIdentity, String serverUrl, String nonce);
    String getOwnedIdentityKeycloakSelfRevocationTestNonce(byte[] bytesOwnedIdentity, String serverUrl);
    boolean updateKeycloakGroups(byte[] bytesOwnedIdentity, List<String> signedGroupBlobs, List<String> signedGroupDeletions, List<String> signedGroupKicks, long keycloakCurrentTimestamp);

    void registerToPushNotification(byte[] bytesOwnedIdentity, ObvPushNotificationType pushNotificationType, boolean reactivateCurrentDevice, byte[] bytesDeviceUidToReplace) throws Exception;
    void processAndroidPushNotification(String maskingUidString);
    byte[] getOwnedIdentityFromMaskingUid(String maskingUidString);
    void processDeviceManagementRequest(byte[] bytesOwnedIdentity, ObvDeviceManagementRequest deviceManagementRequest) throws Exception;

    void updateLatestIdentityDetails(byte[] bytesOwnedIdentity, JsonIdentityDetails jsonIdentityDetails) throws Exception;
    void discardLatestIdentityDetails(byte[] bytesOwnedIdentity);
    void publishLatestIdentityDetails(byte[] bytesOwnedIdentity);
    void updateOwnedIdentityPhoto(byte[] bytesOwnedIdentity, String absolutePhotoUrl) throws Exception;

    byte[] getServerAuthenticationToken(byte[] bytesOwnedIdentity);

    List<ObvCapability> getOwnCapabilities(byte[] bytesOwnedIdentity); // returns null in case of error, empty list if there are no capabilities
    List<ObvOwnedDevice> getOwnedDevices(byte[] bytesOwnedIdentity);
    ObvDeviceList queryRegisteredOwnedDevicesFromServer(byte[] bytesOwnedIdentity);
    void refreshOwnedDeviceList(byte[] bytesOwnedIdentity);
    void recreateOwnedDeviceChannel(byte[] bytesOwnedIdentity, byte[] bytesDeviceUid);
//    void resynchronizeAllOwnedDevices(byte[] bytesOwnedIdentity);


    // ObvContactIdentity
    ObvIdentity[] getContactsOfOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception;
    EnumSet<ObvContactActiveOrInactiveReason> getContactActiveOrInactiveReasons(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);
    boolean forcefullyUnblockContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);
    boolean reBlockForcefullyUnblockedContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);
    boolean isContactOneToOne(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    
    int getContactDeviceCount(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    int getContactEstablishedChannelsCount(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    String getContactTrustedDetailsPhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    JsonIdentityDetailsWithVersionAndPhoto[] getContactPublishedAndTrustedDetails(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void trustPublishedContactDetails(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);
    ObvTrustOrigin[] getContactTrustOrigins(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    int getContactTrustLevel(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    List<ObvCapability> getContactCapabilities(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity); // returns null in case of error, empty list if there are no capabilities


    // ObvGroup
    ObvGroup[] getGroupsOfOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception;
    JsonGroupDetailsWithVersionAndPhoto[] getGroupPublishedAndLatestOrTrustedDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception;
    String getGroupTrustedDetailsPhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception;
    void trustPublishedGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);
    void updateLatestGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, JsonGroupDetails jsonGroupDetails) throws Exception;
    void discardLatestGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);
    void publishLatestGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);
    void updateOwnedGroupPhoto(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, String photoUrl) throws Exception;

    // Group V2
    List<ObvGroupV2> getGroupsV2OfOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception;
    void trustGroupV2PublishedDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception;
    ObvGroupV2.ObvGroupV2DetailsAndPhotos getGroupV2DetailsAndPhotos(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);
    String getGroupV2JsonType(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier);
    void initiateGroupV2Update(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, ObvGroupV2.ObvGroupV2ChangeSet changeSet) throws Exception;
    void leaveGroupV2(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception;
    void disbandGroupV2(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception;
    void reDownloadGroupV2(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception;
    Integer getGroupV2Version(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier) throws Exception;
    boolean isGroupV2UpdateInProgress(byte[] bytesOwnedIdentity, GroupV2.Identifier groupIdentifier) throws Exception;



    // ObvDialog
    void deletePersistedDialog(UUID uuid) throws Exception;
    Set<UUID> getAllPersistedDialogUuids() throws Exception;
    void resendAllPersistedDialogs() throws Exception;
    void respondToDialog(ObvDialog dialog) throws Exception;
    void abortProtocol(ObvDialog dialog) throws Exception;

    // Start protocols
    void startTrustEstablishmentProtocol(byte[] bytesRemoteIdentity, String contactDisplayName, byte[] bytesOwnedIdentity) throws Exception;
    ObvMutualScanUrl computeMutualScanSignedNonceUrl(byte[] bytesRemoteIdentity, byte[] bytesOwnedIdentity, String ownDisplayName) throws Exception;
    boolean verifyMutualScanSignedNonceUrl(byte[] bytesOwnedIdentity, ObvMutualScanUrl mutualScanUrl);
    void startMutualScanTrustEstablishmentProtocol(byte[] bytesOwnedIdentity, byte[] bytesRemoteIdentity, byte[] signature) throws Exception;
    void startContactMutualIntroductionProtocol(byte[] bytesOwnedIdentity, byte[] bytesContactIdentityA, byte[][] bytesContactIdentities) throws Exception;
    void startGroupCreationProtocol(String serializedGroupDetailsWithVersionAndPhoto, String absolutePhotoUrl, byte[] bytesOwnedIdentity, byte[][] bytesRemoteIdentities) throws Exception;
    void startGroupV2CreationProtocol(String serializedGroupDetails, String absolutePhotoUrl, byte[] bytesOwnedIdentity, HashSet<GroupV2.Permission> ownPermissions, HashMap<ObvBytesKey, HashSet<GroupV2.Permission>> otherGroupMembers, String serializedGroupType) throws Exception;
    void restartAllOngoingChannelEstablishmentProtocols(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void recreateAllChannels(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void inviteContactsToGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, byte[][] bytesNewMemberIdentities) throws Exception;
    void removeContactsFromGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, byte[][] bytesRemovedMemberIdentities) throws Exception;
    void reinvitePendingToGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, byte[] bytesPendingMemberIdentity) throws Exception;
    void leaveGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception;
    void disbandGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception;
    void deleteContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void downgradeOneToOneContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void startOneToOneInvitationProtocol(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void deleteOwnedIdentityAndNotifyContacts(byte[] bytesOwnedIdentity, boolean deleteEverywhere) throws Exception;
    void queryGroupOwnerForLatestGroupMembers(byte[] bytesGroupOwnerAndUid, byte[] bytesOwnedIdentity) throws Exception;
    void addKeycloakContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, String signedContactDetails) throws Exception;
    void initiateOwnedIdentityTransferProtocolOnSourceDevice(byte[] bytesOwnedIdentity) throws Exception;
    void initiateOwnedIdentityTransferProtocolOnTargetDevice(String deviceName) throws Exception;


    // Post/receive messages
    byte[] getReturnReceiptNonce();
    byte[] getReturnReceiptKey();
    void deleteReturnReceipt(byte[] bytesOwnedIdentity, byte[] serverUid);
    ObvReturnReceipt decryptReturnReceipt(byte[] returnReceiptKey, byte[] encryptedPayload);
    ObvPostMessageOutput post(byte[] messagePayload, byte[] extendedMessagePayload, ObvOutboundAttachment[] attachments, List<byte[]> bytesContactIdentities, byte[] bytesOwnedIdentity, boolean hasUserContent, boolean isVoipMessage);
    void sendReturnReceipt(byte[] bytesOwnedIdentity, byte[] senderIdentifier, int status, byte[] returnReceiptNonce, byte[] returnReceiptKey, Integer attachmentNumber);
    boolean isOutboxAttachmentSent(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier, int engineNumber);
    boolean isOutboxMessageSent(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier);
    void cancelMessageSending(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier);

    boolean isInboxAttachmentReceived(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier, int engineNumber);
    void downloadMessages(byte[] bytesOwnedIdentity);
    void downloadSmallAttachment(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void downloadLargeAttachment(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void pauseAttachmentDownload(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void markAttachmentForDeletion(ObvAttachment attachment);
    void markAttachmentForDeletion(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void deleteMessageAndAttachments(byte[] bytesOwnedIdentity, byte[] messageIdentifier);
    void markMessageForDeletion(byte[] bytesOwnedIdentity, byte[] messageIdentifier);
    void cancelAttachmentUpload(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void resendAllAttachmentNotifications() throws Exception;
    void connectWebsocket(boolean relyOnWebsocketForNetworkDetection, String os, String osVersion, int appBuild, String appVersion);
    void disconnectWebsocket();
    void pingWebsocket(byte[] bytesOwnedIdentity);
    void retryScheduledNetworkTasks();

    // Backups
    void initiateBackup(boolean forExport);
    ObvBackupKeyInformation getBackupKeyInformation() throws Exception;
    void generateBackupKey();
    void setAutoBackupEnabled(boolean enabled, boolean initiateBackupNowIfNeeded);
    void markBackupExported(byte[] backupKeyUid, int version);
    void markBackupUploaded(byte[] backupKeyUid, int version);
    void discardBackup(byte[] backupKeyUid, int version);
    ObvBackupKeyVerificationOutput validateBackupSeed(String backupSeed, byte[] backupContent);
    ObvBackupKeyVerificationOutput verifyBackupSeed(String backupSeed);
    ObvIdentity[] restoreOwnedIdentitiesFromBackup(String backupSeed, byte[] backupContent, String deviceDisplayName);
    void restoreContactsAndGroupsFromBackup(String backupSeed, byte[] backupContent,  ObvIdentity[] restoredOwnedIdentities);
    String decryptAppDataBackup(String backupSeed, byte[] backupContent);
    void appBackupSuccess(byte[] bytesBackupKeyUid, int version, String appBackupContent);
    void appBackupFailed(byte[] bytesBackupKeyUid, int version);


    void getTurnCredentials(byte[] bytesOwnedIdentity, UUID callUuid, String callerUsername, String recipientUsername);
    void queryApiKeyStatus(byte[] bytesOwnedIdentity, UUID apiKey);
    void queryApiKeyStatus(String server, UUID apiKey);
    void queryFreeTrial(byte[] bytesOwnedIdentity);
    void startFreeTrial(byte[] bytesOwnedIdentity);
    void verifyReceipt(byte[] bytesOwnedIdentity, String storeToken);
    void queryServerWellKnown(String server);
    String getOsmServerUrl(byte[] bytesOwnedIdentity);
    String getAddressServerUrl(byte[] bytesOwnedIdentity);

    void propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(ObvSyncAtom obvSyncAtom) throws Exception;
    void propagateAppSyncAtomToOtherDevicesIfNeeded(byte[] bytesOwnedIdentity, ObvSyncAtom obvSyncAtom) throws Exception;

    // Run once after you upgrade from a version not handling Contact and ContactGroup UserData (profile photos) to a version able to do so
    void downloadAllUserData() throws Exception;
    void setAllOwnedDeviceNames(String deviceName);

    void vacuumDatabase() throws Exception;
}
