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

package io.olvid.engine.datatypes.containers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.KDF;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPrivateKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.metamanager.IdentityDelegate;

public class GroupV2 {
    public enum Permission {
        GROUP_ADMIN, // allows changing members and their permissions
        REMOTE_DELETE_ANYTHING, // allows to remote-delete any message or the whole discussion
        EDIT_OR_REMOTE_DELETE_OWN_MESSAGES, // allows to edit your messages and remote delete them
        CHANGE_SETTINGS,
        SEND_MESSAGE;

        public static final Permission[] DEFAULT_MEMBER_PERMISSIONS = {REMOTE_DELETE_ANYTHING, EDIT_OR_REMOTE_DELETE_OWN_MESSAGES, SEND_MESSAGE};
        public static final Permission[] DEFAULT_ADMIN_PERMISSIONS = {GROUP_ADMIN, REMOTE_DELETE_ANYTHING, EDIT_OR_REMOTE_DELETE_OWN_MESSAGES, CHANGE_SETTINGS, SEND_MESSAGE};

        public String getString() {
            switch (this) {
                case GROUP_ADMIN:
                    return "ga";
                case REMOTE_DELETE_ANYTHING:
                    return "rd";
                case EDIT_OR_REMOTE_DELETE_OWN_MESSAGES:
                    return "eo";
                case CHANGE_SETTINGS:
                    return "cs";
                case SEND_MESSAGE:
                    return "sm";
            }
            return "";
        }

        private static final Map<String, Permission> valueMap = new HashMap<>();
        static {
            valueMap.put("ga", GROUP_ADMIN);
            valueMap.put("rd", REMOTE_DELETE_ANYTHING);
            valueMap.put("eo", EDIT_OR_REMOTE_DELETE_OWN_MESSAGES);
            valueMap.put("cs", CHANGE_SETTINGS);
            valueMap.put("sm", SEND_MESSAGE);
        }
        public static Permission fromString(String value) {
            return valueMap.get(value);
        }

        public static HashSet<Permission> fromStrings(Collection<String> permissionStrings) {
            HashSet<Permission> res = new HashSet<>();
            for (String permissionString : permissionStrings) {
                Permission perm = Permission.fromString(permissionString);
                if (perm != null) {
                    res.add(perm);
                }
            }
            return res;
        }


        public static List<String> deserializePermissions(byte[] serializedPermissions) {
            List<String> permissionStrings = new ArrayList<>();
            int startPos = 0;
            for (int i=0; i<serializedPermissions.length; i++) {
                if (serializedPermissions[i] == 0x00) {
                    permissionStrings.add(new String(Arrays.copyOfRange(serializedPermissions, startPos, i), StandardCharsets.UTF_8));
                    startPos = i+1;
                }
            }
            if (startPos != serializedPermissions.length) {
                permissionStrings.add(new String(Arrays.copyOfRange(serializedPermissions, startPos, serializedPermissions.length), StandardCharsets.UTF_8));
            }
            return permissionStrings;
        }

        public static HashSet<Permission> deserializeKnownPermissions(byte[] serializedPermissions) {
            List<String> permissionStrings = deserializePermissions(serializedPermissions);
            HashSet<Permission> permissions = new HashSet<>();
            for (String permissionString : permissionStrings) {
                Permission permission = Permission.fromString(permissionString);
                if (permission != null) {
                    permissions.add(permission);
                }
            }
            return permissions;
        }

        public static byte[] serializePermissionStrings(Collection<String> permissionStrings) {
            if (permissionStrings.size() == 0) {
                return new byte[0];
            } else {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    for (String permissionString : permissionStrings) {
                        if (baos.size() > 0) {
                            baos.write(new byte[]{0});
                        }
                        baos.write(permissionString.getBytes(StandardCharsets.UTF_8));
                    }
                    return baos.toByteArray();
                } catch (IOException e) {
                    return null;
                }
            }
        }

        public static byte[] serializePermissions(Collection<Permission> permissions) {
            if (permissions.size() == 0) {
                return new byte[0];
            } else {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    for (Permission permission : permissions) {
                        if (baos.size() > 0) {
                            baos.write(new byte[]{0});
                        }
                        baos.write(permission.getString().getBytes(StandardCharsets.UTF_8));
                    }
                    return baos.toByteArray();
                } catch (IOException e) {
                    return null;
                }
            }
        }
    }

    public static AuthEncKey getSharedBlobSecretKey(Seed blobMainSeed, Seed blobVersionSeed) {
        return (AuthEncKey) Suite.getKDF(KDF.KDF_SHA256).gen(new Seed(blobMainSeed, blobVersionSeed), Suite.getDefaultAuthEnc(0).getKDFDelegate())[0];
    }


    public static class Identifier {
        public static final int CATEGORY_SERVER = 0;
        public static final int CATEGORY_KEYCLOAK = 1;

        public final UID groupUid;
        public final String serverUrl;
        public final int category;

        public Identifier(UID groupUid, String serverUrl, int category) {
            this.groupUid = groupUid;
            this.serverUrl = serverUrl;
            this.category = category;
        }

        public byte[] getBytes() {
            return encode().getBytes();
        }

        public Encoded encode() {
            return Encoded.of(new Encoded[] {
                    Encoded.of(groupUid),
                    Encoded.of(serverUrl),
                    Encoded.of(category),
            });
        }

        public static Identifier of(byte[] bytesGroupIdentifier) throws DecodingException {
            return of(new Encoded(bytesGroupIdentifier));
        }

        public static Identifier of(Encoded encoded) throws DecodingException {
            Encoded[] encodeds = encoded.decodeList();
            if (encodeds.length != 3) {
                throw new DecodingException();
            }
            switch ((int) encodeds[2].decodeLong()) {
                case CATEGORY_SERVER:
                    return new Identifier(encodeds[0].decodeUid(), encodeds[1].decodeString(), CATEGORY_SERVER);
                case CATEGORY_KEYCLOAK:
                    return new Identifier(encodeds[0].decodeUid(), encodeds[1].decodeString(), CATEGORY_KEYCLOAK);
                default:
                    throw new DecodingException();
            }
        }

        public UID computeProtocolInstanceUid() {
            Seed prngSeed = new Seed(this.getBytes());
            PRNG seededPRNG = Suite.getDefaultPRNG(0, prngSeed);
            return new UID(seededPRNG);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Identifier)) {
                return false;
            }
            Identifier other = (Identifier) obj;
            return category == other.category && Objects.equals(serverUrl, other.serverUrl) && Objects.equals(groupUid, other.groupUid);
        }

        @Override
        public int hashCode() {
            return serverUrl.hashCode() + 31 * groupUid.hashCode() + category;
        }
    }


    public static class AdministratorsChain {

        public final UID groupUid;
        public final Block[] blocks;
        public boolean integrityWasChecked;

        public static AdministratorsChain startNewChain(Session session, IdentityDelegate identityDelegate, Identity ownedIdentity, Identity[] otherAdministratorIdentities, PRNGService prng) throws Exception {
            Block firstBlock = new Block(session, identityDelegate, ownedIdentity, otherAdministratorIdentities, prng);
            return new AdministratorsChain(new UID(firstBlock.computeSha256()), new Block[]{firstBlock}, true);
        }

        public AdministratorsChain withCheckedIntegrity(UID expectedGroupUid, Identity latestUpdateAdministratorIdentity) throws Exception {
            if (latestUpdateAdministratorIdentity != null) {
                // first check the administrator is indeed part of the last block list of admins
                boolean found = false;
                for (Identity identity : blocks[blocks.length - 1].innerData.administratorIdentities) {
                    if (identity.equals(latestUpdateAdministratorIdentity)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    throw new Exception("Administrator is not a valid administrator for this chain");
                }
            }

            if (!Objects.equals(expectedGroupUid, groupUid)) {
                throw new Exception("GroupUid of chain does not match expected groupUid");
            }

            if (integrityWasChecked) {
                return this;
            }

            {
                // verify the groupUID
                if (!groupUid.equals(new UID(blocks[0].computeSha256()))) {
                    throw new Exception("Invalid groupUid");
                }

                // check the first block's signature
                if (!blocks[0].isSignatureValid(blocks[0].innerData.administratorIdentities)) {
                    throw new Exception("Invalid block signature at block 0");
                }

                // check following blocks
                for (int i=1; i< blocks.length; i++) {
                    if (!Arrays.equals(blocks[i].innerData.previousBlockHash, blocks[i-1].computeSha256())) {
                        throw new Exception("Invalid block hash chaining at block " + i);
                    }
                    if (!blocks[i].isSignatureValid(blocks[i-1].innerData.administratorIdentities)) {
                        throw new Exception("Invalid block signature at block " + i);
                    }
                }
            }

            integrityWasChecked = true;
            return this;
        }

        public boolean isPrefixedBy(AdministratorsChain prefix) {
            if (!Objects.equals(prefix.groupUid, groupUid)) {
                return false;
            }
            if (prefix.blocks.length > blocks.length) {
                return false;
            }
            for (int i=0; i<prefix.blocks.length; i++) {
                if (!Objects.equals(blocks[i].encodedInnerData, prefix.blocks[i].encodedInnerData)) {
                    return false;
                }
            }
            return true;
        }

        // no longer used, but could be useful one day!
//        public boolean isChainCreatedBy(Identity identity) {
//            return blocks.length > 0 && blocks[0].isSignatureValid(new Identity[]{identity});
//        }

        private AdministratorsChain(UID groupUid, Block[] blocks, boolean integrityWasChecked) {
            this.groupUid = groupUid;
            this.blocks = blocks;
            this.integrityWasChecked = integrityWasChecked;
        }

        public static AdministratorsChain of(Encoded encoded) throws DecodingException {
            Encoded[] encodeds = encoded.decodeList();
            if (encodeds.length == 0) {
                throw new DecodingException();
            }
            Block[] blocks = new Block[encodeds.length];
            for (int i = 0; i<blocks.length; i++) {
                blocks[i] = Block.of(encodeds[i]);
            }
            return new AdministratorsChain(new UID(blocks[0].computeSha256()), blocks, false);
        }

        public Encoded encode() {
            Encoded[] encodeds = new Encoded[blocks.length];
            for (int i = 0; i<blocks.length; i++) {
                encodeds[i] = blocks[i].encode();
            }
            return Encoded.of(encodeds);
        }

        public HashSet<Identity> getAdminIdentities() {
            if (blocks.length == 0) {
                return new HashSet<>();
            }
            return new HashSet<>(Arrays.asList(blocks[blocks.length - 1].innerData.administratorIdentities));
        }

        public AdministratorsChain buildNewChainByAppendingABlock(Session session, IdentityDelegate identityDelegate, Identity ownedIdentity, Identity[] otherAdministratorIdentities, PRNGService prng) throws Exception {
            if (blocks.length == 0) {
                return null;
            }
            if (!Arrays.asList(blocks[blocks.length - 1].innerData.administratorIdentities).contains(ownedIdentity)) {
                Logger.e("Trying to append block to AdministratorsChain using an identity not in the last block!");
                throw new Exception();
            }
            Block[] newBlocks = new Block[blocks.length + 1];
            System.arraycopy(blocks, 0, newBlocks, 0, blocks.length);
            newBlocks[newBlocks.length - 1] = new Block(session, identityDelegate, blocks[blocks.length - 1], ownedIdentity, otherAdministratorIdentities, prng);
            return new AdministratorsChain(
                    groupUid,
                    newBlocks,
                    true
            );
        }

        public static class Block {

            public final Encoded encodedInnerData;
            public final InnerData innerData;
            public final byte[] signature;

            private Block(Session session, IdentityDelegate identityDelegate, Identity ownedIdentity, Identity[] otherAdministratorIdentities, PRNGService prng) throws Exception {
                this.innerData = new InnerData(ownedIdentity, otherAdministratorIdentities, prng);
                this.encodedInnerData = innerData.encode();
                this.signature = identityDelegate.signBlock(session, Constants.SignatureContext.GROUP_ADMINISTRATORS_CHAIN, encodedInnerData.getBytes(), ownedIdentity, prng);
            }

            private Block(Session session, IdentityDelegate identityDelegate, Block previousBlock, Identity ownedIdentity, Identity[] otherAdministratorIdentities, PRNGService prng) throws Exception {
                byte[] previousBlockHash = previousBlock.computeSha256();
                Identity[] administratorIdentities = new Identity[otherAdministratorIdentities.length + 1];
                administratorIdentities[0] = ownedIdentity;
                System.arraycopy(otherAdministratorIdentities, 0, administratorIdentities, 1, otherAdministratorIdentities.length);
                this.innerData = new InnerData(previousBlockHash, administratorIdentities);
                this.encodedInnerData = innerData.encode();
                this.signature = identityDelegate.signBlock(session, Constants.SignatureContext.GROUP_ADMINISTRATORS_CHAIN, encodedInnerData.getBytes(), ownedIdentity, prng);
            }

            private Block(Encoded encodedInnerData, InnerData innerData, byte[] signature) {
                this.encodedInnerData = encodedInnerData;
                this.innerData = innerData;
                this.signature = signature;
            }

            static Block of(Encoded encoded) throws DecodingException{
                Encoded[] encodeds = encoded.decodeList();
                if (encodeds.length != 2) {
                    throw new DecodingException();
                }
                return new Block(encodeds[0], InnerData.of(encodeds[0]), encodeds[1].decodeBytes());
            }

            Encoded encode() {
                return Encoded.of(new Encoded[]{
                        encodedInnerData,
                        Encoded.of(signature),
                });
            }

            byte[] computeSha256() {
                return Suite.getHash(Hash.SHA256).digest(encode().getBytes());
            }

            @SuppressWarnings("BooleanMethodIsAlwaysInverted")
            boolean isSignatureValid(Identity[] previousBlockAdministratorIdentities) {
                for (Identity administratorIdentity : previousBlockAdministratorIdentities) {
                    try {
                        if (Signature.verify(Constants.SignatureContext.GROUP_ADMINISTRATORS_CHAIN, encodedInnerData.getBytes(), administratorIdentity, signature)) {
                            return true;
                        }
                    } catch (Exception ignored) { }
                }
                return false;
            }

            public static class InnerData {
                public final byte[] previousBlockHash;
                public final Identity[] administratorIdentities;

                private InnerData(byte[] previousBlockHash, Identity[] administratorIdentities) {
                    this.previousBlockHash = previousBlockHash;
                    this.administratorIdentities = administratorIdentities;
                }

                // create the first block InnerData, with no chaining
                InnerData(Identity ownedIdentity, Identity[] otherAdministratorIdentities, PRNGService prng) {
                    this.previousBlockHash = prng.bytes(Suite.getHash(Hash.SHA256).outputLength());
                    this.administratorIdentities = new Identity[otherAdministratorIdentities.length + 1];
                    this.administratorIdentities[0] = ownedIdentity;
                    System.arraycopy(otherAdministratorIdentities, 0, administratorIdentities, 1, otherAdministratorIdentities.length);
                }

                Encoded encode() {
                    return Encoded.of(new Encoded[] {
                            Encoded.of(previousBlockHash),
                            Encoded.of(administratorIdentities),
                    });
                }

                static InnerData of(Encoded encoded) throws DecodingException {
                    Encoded[] encodeds = encoded.decodeList();
                    if (encodeds.length != 2) {
                        throw new DecodingException();
                    }
                    return new InnerData(encodeds[0].decodeBytes(), encodeds[1].decodeIdentityArray());
                }
            }
        }
    }

    public static class ServerBlob {
        public static final String KEY_ADMINISTRATORS_CHAIN = "ac";
        public static final String KEY_GROUP_MEMBER_IDENTITY_AND_PERMISSIONS_AND_DETAILS_LIST = "mem";
        public static final String KEY_VERSION = "v";
        public static final String KEY_SERIALIZED_GROUP_DETAILS = "det";
        public static final String KEY_SERVER_PHOTO_INFO = "ph";
        public static final String KEY_SERIALIZED_GROUP_TYPE = "t";

        public final AdministratorsChain administratorsChain;
        public final HashSet<IdentityAndPermissionsAndDetails> groupMemberIdentityAndPermissionsAndDetailsList;
        public final int version;
        public final String serializedGroupDetails;
        public final ServerPhotoInfo serverPhotoInfo; // null if the group does not have a photo
        public final String serializedGroupType;

        public ServerBlob(AdministratorsChain administratorsChain, HashSet<IdentityAndPermissionsAndDetails> groupMemberIdentityAndPermissionsAndDetailsList, int version, String serializedGroupDetails, ServerPhotoInfo serverPhotoInfo, String serializedGroupType) {
            this.administratorsChain = administratorsChain;
            this.groupMemberIdentityAndPermissionsAndDetailsList = groupMemberIdentityAndPermissionsAndDetailsList;
            this.version = version;
            this.serializedGroupDetails = serializedGroupDetails;
            this.serverPhotoInfo = serverPhotoInfo;
            this.serializedGroupType = serializedGroupType;
        }

        public Encoded encode() {
            HashMap<DictionaryKey, Encoded> map = new HashMap<>();
            map.put(new DictionaryKey(KEY_ADMINISTRATORS_CHAIN), administratorsChain.encode());
            Encoded[] encodedGroupMembers = new Encoded[groupMemberIdentityAndPermissionsAndDetailsList.size()];
            int i=0;
            for (IdentityAndPermissionsAndDetails identityAndPermissionsAndDetails : groupMemberIdentityAndPermissionsAndDetailsList) {
                encodedGroupMembers[i] = identityAndPermissionsAndDetails.encode();
                i++;
            }
            map.put(new DictionaryKey(KEY_GROUP_MEMBER_IDENTITY_AND_PERMISSIONS_AND_DETAILS_LIST), Encoded.of(encodedGroupMembers));
            map.put(new DictionaryKey(KEY_VERSION), Encoded.of(version));
            map.put(new DictionaryKey(KEY_SERIALIZED_GROUP_DETAILS), Encoded.of(serializedGroupDetails));
            if (serverPhotoInfo != null) {
                map.put(new DictionaryKey(KEY_SERVER_PHOTO_INFO), serverPhotoInfo.encode());
            }
            if (serializedGroupType != null) {
                map.put(new DictionaryKey(KEY_SERIALIZED_GROUP_TYPE), Encoded.of(serializedGroupType));
            }
            return Encoded.of(map);
        }

        public static ServerBlob of(Encoded encoded) throws DecodingException {
            HashMap<DictionaryKey, Encoded> map = encoded.decodeDictionary();
            Encoded value;
            value = map.get(new DictionaryKey(KEY_ADMINISTRATORS_CHAIN));
            if (value == null) {
                throw new DecodingException();
            }
            AdministratorsChain administratorsChain = AdministratorsChain.of(value);

            value = map.get(new DictionaryKey(KEY_GROUP_MEMBER_IDENTITY_AND_PERMISSIONS_AND_DETAILS_LIST));
            if (value == null) {
                throw new DecodingException();
            }
            Encoded[] encodedGroupMembers = value.decodeList();
            HashSet<IdentityAndPermissionsAndDetails> groupMemberIdentityAndPermissionsAndDetailsList = new HashSet<>();
            for (Encoded encodedGroupMember: encodedGroupMembers) {
                groupMemberIdentityAndPermissionsAndDetailsList.add(IdentityAndPermissionsAndDetails.of(encodedGroupMember));
            }

            value = map.get(new DictionaryKey(KEY_VERSION));
            if (value == null) {
                throw new DecodingException();
            }
            int version = (int) value.decodeLong();

            value = map.get(new DictionaryKey(KEY_SERIALIZED_GROUP_DETAILS));
            if (value == null) {
                throw new DecodingException();
            }
            String serializedGroupDetails = value.decodeString();

            value = map.get(new DictionaryKey(KEY_SERVER_PHOTO_INFO));
            ServerPhotoInfo serverPhotoInfo = value == null ? null : ServerPhotoInfo.of(value);

            value = map.get(new DictionaryKey(KEY_SERIALIZED_GROUP_TYPE));
            String serializedGroupType = value == null ? null : value.decodeString();

            return new ServerBlob(administratorsChain, groupMemberIdentityAndPermissionsAndDetailsList, version, serializedGroupDetails, serverPhotoInfo, serializedGroupType);
        }

        public void consolidateWithLogEntries(GroupV2.Identifier groupIdentifier, List<byte[]> logEntries) {
            HashSet<IdentityAndPermissionsAndDetails> leavers = new HashSet<>();
            for (byte[] logEntry : logEntries) {
                for (IdentityAndPermissionsAndDetails groupMember : groupMemberIdentityAndPermissionsAndDetailsList) {
                    try {
                        if (Signature.verify(Constants.SignatureContext.GROUP_LEAVE_NONCE, groupIdentifier, groupMember.groupInvitationNonce, null, groupMember.identity, logEntry)) {
                            leavers.add(groupMember);
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }

            groupMemberIdentityAndPermissionsAndDetailsList.removeAll(leavers);
        }
    }

    public static class ServerPhotoInfo {
        public final Identity serverPhotoIdentity; // null for keycloak group photo info
        public final UID serverPhotoLabel;
        public final AuthEncKey serverPhotoKey;

        public ServerPhotoInfo(Identity serverPhotoIdentity, UID serverPhotoLabel, AuthEncKey serverPhotoKey) {
            this.serverPhotoIdentity = serverPhotoIdentity;
            this.serverPhotoLabel = serverPhotoLabel;
            this.serverPhotoKey = serverPhotoKey;
        }

        public Encoded encode() {
            if (serverPhotoIdentity == null) {
                return Encoded.of(new Encoded[]{
                        Encoded.of(serverPhotoLabel),
                        Encoded.of(serverPhotoKey),
                });
            } else {
                return Encoded.of(new Encoded[]{
                        Encoded.of(serverPhotoIdentity),
                        Encoded.of(serverPhotoLabel),
                        Encoded.of(serverPhotoKey),
                });
            }
        }

        public static ServerPhotoInfo of(Encoded encoded) throws DecodingException {
            Encoded[] encodeds = encoded.decodeList();
            if (encodeds.length == 2) {
                return new ServerPhotoInfo(
                        null,
                        encodeds[0].decodeUid(),
                        (AuthEncKey) encodeds[1].decodeSymmetricKey());
            } else if (encodeds.length == 3) {
                return new ServerPhotoInfo(
                        encodeds[0].decodeIdentity(),
                        encodeds[1].decodeUid(),
                        (AuthEncKey) encodeds[2].decodeSymmetricKey());
            }
            throw new DecodingException();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ServerPhotoInfo)) {
                return false;
            }
            ServerPhotoInfo other = (ServerPhotoInfo) obj;
            return Objects.equals(serverPhotoIdentity, other.serverPhotoIdentity) && Objects.equals(serverPhotoLabel, other.serverPhotoLabel) && Objects.equals(serverPhotoKey, other.serverPhotoKey);
        }
    }

    public static class BlobKeys {
        public static final String KEY_MAIN_SEED = "ms";
        public static final String KEY_VERSION_SEED = "vs";
        public static final String KEY_GROUP_ADMIN_PRIVATE_KEY = "ga";


        public final Seed blobMainSeed; // may be null when sent through an asymmetric channel
        public final Seed blobVersionSeed; // not null
        public final ServerAuthenticationPrivateKey groupAdminServerAuthenticationPrivateKey; // may be null when you are not admin/they are not the admin

        public BlobKeys(Seed blobMainSeed, Seed blobVersionSeed, ServerAuthenticationPrivateKey groupAdminServerAuthenticationPrivateKey) {
            this.blobMainSeed = blobMainSeed;
            this.blobVersionSeed = blobVersionSeed;
            this.groupAdminServerAuthenticationPrivateKey = groupAdminServerAuthenticationPrivateKey;
        }

        public Encoded encode() {
            HashMap<DictionaryKey, Encoded> map = new HashMap<>();
            if (blobMainSeed != null) {
                map.put(new DictionaryKey(KEY_MAIN_SEED), Encoded.of(blobMainSeed));
            }
            map.put(new DictionaryKey(KEY_VERSION_SEED), Encoded.of(blobVersionSeed));
            if (groupAdminServerAuthenticationPrivateKey != null) {
                map.put(new DictionaryKey(KEY_GROUP_ADMIN_PRIVATE_KEY), Encoded.of(groupAdminServerAuthenticationPrivateKey));
            }
            return Encoded.of(map);
        }

        public static BlobKeys of(Encoded encoded) throws DecodingException {
            HashMap<DictionaryKey, Encoded> map = encoded.decodeDictionary();
            Encoded value;
            value = map.get(new DictionaryKey(KEY_MAIN_SEED));
            Seed blobMainSeed = value == null ? null : value.decodeSeed();

            value = map.get(new DictionaryKey(KEY_VERSION_SEED));
            if (value == null) {
                throw new DecodingException();
            }
            Seed blobVersionSeed = value.decodeSeed();

            value = map.get(new DictionaryKey(KEY_GROUP_ADMIN_PRIVATE_KEY));
            ServerAuthenticationPrivateKey groupAdminServerAuthenticationPrivateKey = value == null ? null : (ServerAuthenticationPrivateKey) value.decodePrivateKey();

            return new BlobKeys(blobMainSeed, blobVersionSeed, groupAdminServerAuthenticationPrivateKey);
        }
    }

    public static class InvitationCollectedData {
        public static final String KEY_INVITER_IDENTITY_AND_MAIN_SEED = "ms";
        public static final String KEY_VERSION_SEED = "vs";
        public static final String KEY_GROUP_ADMIN_PRIVATE_KEY = "ga";

        public final HashMap<Identity, Seed> inviterIdentityAndBlobMainSeedCandidates; // non null
        public final HashSet<Seed> blobVersionSeedCandidates; // non null
        public final HashSet<ServerAuthenticationPrivateKey> groupAdminServerAuthenticationPrivateKeyCandidates; // non null

        public InvitationCollectedData(HashMap<Identity, Seed> inviterIdentityAndBlobMainSeedCandidates, HashSet<Seed> blobVersionSeedCandidates, HashSet<ServerAuthenticationPrivateKey> groupAdminServerAuthenticationPrivateKeyCandidates) {
            this.inviterIdentityAndBlobMainSeedCandidates = inviterIdentityAndBlobMainSeedCandidates;
            this.blobVersionSeedCandidates = blobVersionSeedCandidates;
            this.groupAdminServerAuthenticationPrivateKeyCandidates = groupAdminServerAuthenticationPrivateKeyCandidates;
        }

        public InvitationCollectedData() {
            this.inviterIdentityAndBlobMainSeedCandidates = new HashMap<>();
            this.blobVersionSeedCandidates = new HashSet<>();
            this.groupAdminServerAuthenticationPrivateKeyCandidates = new HashSet<>();
        }

        public Encoded encode() {
            HashMap<DictionaryKey, Encoded> map = new HashMap<>();
            List<Encoded> encodeds = new ArrayList<>();
            for (Map.Entry<Identity, Seed> entry : inviterIdentityAndBlobMainSeedCandidates.entrySet()) {
                encodeds.add(Encoded.of(new Encoded[]{
                        Encoded.of(entry.getKey()),
                        Encoded.of(entry.getValue()),
                }));
            }
            map.put(new DictionaryKey(KEY_INVITER_IDENTITY_AND_MAIN_SEED), Encoded.of(encodeds.toArray(new Encoded[0])));
            encodeds = new ArrayList<>();
            for (Seed seed : blobVersionSeedCandidates) {
                encodeds.add(Encoded.of(seed));
            }
            map.put(new DictionaryKey(KEY_VERSION_SEED), Encoded.of(encodeds.toArray(new Encoded[0])));
            encodeds = new ArrayList<>();
            for (ServerAuthenticationPrivateKey key : groupAdminServerAuthenticationPrivateKeyCandidates) {
                encodeds.add(Encoded.of(key));
            }
            map.put(new DictionaryKey(KEY_GROUP_ADMIN_PRIVATE_KEY), Encoded.of(encodeds.toArray(new Encoded[0])));
            return Encoded.of(map);
        }

        public static InvitationCollectedData of(Encoded encoded) throws DecodingException {
            HashMap<DictionaryKey, Encoded> map = encoded.decodeDictionary();
            Encoded value;

            HashMap<Identity, Seed> inviterIdentityAndBlobMainSeedCandidates = new HashMap<>();
            value = map.get(new DictionaryKey(KEY_INVITER_IDENTITY_AND_MAIN_SEED));
            if (value == null) {
                throw new DecodingException();
            }
            for (Encoded enc : value.decodeList()) {
                Encoded[] list = enc.decodeList();
                inviterIdentityAndBlobMainSeedCandidates.put(list[0].decodeIdentity(), list[1].decodeSeed());
            }

            HashSet<Seed> blobVersionSeedCandidates = new HashSet<>();
            value = map.get(new DictionaryKey(KEY_VERSION_SEED));
            if (value == null) {
                throw new DecodingException();
            }
            for (Encoded enc : value.decodeList()) {
                blobVersionSeedCandidates.add(enc.decodeSeed());
            }

            HashSet<ServerAuthenticationPrivateKey> groupAdminServerAuthenticationPrivateKeyCandidates = new HashSet<>();
            value = map.get(new DictionaryKey(KEY_GROUP_ADMIN_PRIVATE_KEY));
            if (value == null) {
                throw new DecodingException();
            }
            for (Encoded enc : value.decodeList()) {
                groupAdminServerAuthenticationPrivateKeyCandidates.add((ServerAuthenticationPrivateKey) enc.decodePrivateKey());
            }

            return new InvitationCollectedData(inviterIdentityAndBlobMainSeedCandidates, blobVersionSeedCandidates, groupAdminServerAuthenticationPrivateKeyCandidates);
        }

        public void addBlobKeysCandidates(Identity inviterIdentity, BlobKeys blobKeys) {
            if (inviterIdentity != null && blobKeys.blobMainSeed != null) {
                inviterIdentityAndBlobMainSeedCandidates.put(inviterIdentity, blobKeys.blobMainSeed);
            }
            if (blobKeys.blobVersionSeed != null) {
                blobVersionSeedCandidates.add(blobKeys.blobVersionSeed);
            }
            if (blobKeys.groupAdminServerAuthenticationPrivateKey != null) {
                groupAdminServerAuthenticationPrivateKeyCandidates.add(blobKeys.groupAdminServerAuthenticationPrivateKey);
            }
        }
    }

    // used when creating a group
    public static class IdentityAndPermissions {
        public final Identity identity;
        public final HashSet<GroupV2.Permission> permissions;

        public IdentityAndPermissions(Identity identity, HashSet<Permission> permissions) {
            this.identity = identity;
            this.permissions = permissions;
        }

        public static IdentityAndPermissions of(Encoded encoded) throws DecodingException {
            Encoded[] encodeds = encoded.decodeList();
            if (encodeds.length != 2) {
                throw new DecodingException();
            }
            Identity identity = encodeds[0].decodeIdentity();
            HashSet<GroupV2.Permission> permissions = new HashSet<>();
            for (Encoded encodedPermission : encodeds[1].decodeList()) {
                GroupV2.Permission permission = GroupV2.Permission.fromString(encodedPermission.decodeString());
                if (permission != null) {
                    permissions.add(permission);
                }
            }

            return new IdentityAndPermissions(identity, permissions);
        }

        public Encoded encode() {
            List<Encoded> encodedPermissions = new ArrayList<>();
            for (GroupV2.Permission permission : permissions) {
                encodedPermissions.add(Encoded.of(permission.getString()));
            }

            return Encoded.of(new Encoded[] {
                    Encoded.of(identity),
                    Encoded.of(encodedPermissions.toArray(new Encoded[0])),
            });
        }

        public boolean isAdmin() {
            return permissions.contains(GroupV2.Permission.GROUP_ADMIN);
        }

        // hashcode only uses the Identity to avoid duplicate group members when building sets of IdentityAndGroupPermissions
        @Override
        public int hashCode() {
            return identity.hashCode();
        }

        // equals only matches the Identity to avoid duplicate group members when building sets of IdentityAndGroupPermissions
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof IdentityAndPermissions)) {
                return false;
            }
            return identity.equals(((IdentityAndPermissions) obj).identity);
        }
    }


    // stored in the blob on the server
    public static class IdentityAndPermissionsAndDetails {
        public final Identity identity;
        public final List<String> permissionStrings;
        public final String serializedIdentityDetails;
        public final byte[] groupInvitationNonce;

        public IdentityAndPermissionsAndDetails(Identity identity, List<String> permissionStrings, String serializedIdentityDetails, byte[] groupInvitationNonce) {
            this.identity = identity;
            this.permissionStrings = permissionStrings;
            this.serializedIdentityDetails = serializedIdentityDetails;
            this.groupInvitationNonce = groupInvitationNonce;
        }

        public static IdentityAndPermissionsAndDetails of(Encoded encoded) throws DecodingException {
            Encoded[] encodeds = encoded.decodeList();
            if (encodeds.length != 4) {
                throw new DecodingException();
            }
            Identity identity = encodeds[0].decodeIdentity();
            List<String> permissionStrings = new ArrayList<>();
            for (Encoded encodedPermission : encodeds[1].decodeList()) {
               permissionStrings.add(encodedPermission.decodeString());
            }
            String serializedIdentityDetails = encodeds[2].decodeString();
            byte[] groupInvitationNonce = encodeds[3].decodeBytes();

            return new IdentityAndPermissionsAndDetails(identity, permissionStrings, serializedIdentityDetails, groupInvitationNonce);
        }

        public Encoded encode() {
            List<Encoded> encodedPermissions = new ArrayList<>();
            for (String permissionString : permissionStrings) {
                encodedPermissions.add(Encoded.of(permissionString));
            }

            return Encoded.of(new Encoded[] {
                    Encoded.of(identity),
                    Encoded.of(encodedPermissions.toArray(new Encoded[0])),
                    Encoded.of(serializedIdentityDetails),
                    Encoded.of(groupInvitationNonce),
            });
        }

        // hashcode only uses the Identity to avoid duplicate group members when building sets of IdentityAndGroupPermissions
        @Override
        public int hashCode() {
            return identity.hashCode();
        }

        // equals only matches the Identity to avoid duplicate group members when building sets of IdentityAndGroupPermissions
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof IdentityAndPermissionsAndDetails)) {
                return false;
            }
            return identity.equals(((IdentityAndPermissionsAndDetails) obj).identity);
        }
    }

    public static class IdentifierVersionAndKeys {
        public final Identifier groupIdentifier;
        public final int groupVersion;
        public final BlobKeys blobKeys;

        public IdentifierVersionAndKeys(Identifier groupIdentifier, int groupVersion, BlobKeys blobKeys) {
            this.groupIdentifier = groupIdentifier;
            this.groupVersion = groupVersion;
            this.blobKeys = blobKeys;
        }

        public IdentifierVersionAndKeys(Encoded encoded) throws Exception {
            Encoded[] list = encoded.decodeList();
            if (list.length != 3) {
                throw new Exception();
            }
            this.groupIdentifier = Identifier.of(list[0]);
            this.groupVersion = (int) list[1].decodeLong();
            this.blobKeys = BlobKeys.of(list[2]);
        }

        public Encoded encode() {
            return Encoded.of(new Encoded[]{
                    groupIdentifier.encode(),
                    Encoded.of(groupVersion),
                    blobKeys.encode(),
            });
        }
    }
    public static class IdentifierAndAdminStatus {
        public final Identifier groupIdentifier;
        public final boolean iAmAdmin;

        public IdentifierAndAdminStatus(Identifier groupIdentifier, boolean iAmAdmin) {
            this.groupIdentifier = groupIdentifier;
            this.iAmAdmin = iAmAdmin;
        }
    }
}
