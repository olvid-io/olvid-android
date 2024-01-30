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

package io.olvid.engine.identity.databases.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.GroupV2;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.ContactGroupDetails;
import io.olvid.engine.identity.databases.ContactGroupV2Details;
import io.olvid.engine.identity.databases.ServerUserData;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;

// This class is used for both owned identity and contacts
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupDetailsSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String VERSION = "version";
    public static final String SERIALIZED_DETAILS = "serialized_details";
    public static final String PHOTO_SERVER_IDENTITY = "photo_server_identity";
    public static final String PHOTO_SERVER_LABEL = "photo_server_label";
    public static final String PHOTO_SERVER_KEY = "photo_server_key";
    static HashSet<String> DEFAULT_V1_DOMAIN = new HashSet<>(Arrays.asList(VERSION, SERIALIZED_DETAILS, PHOTO_SERVER_LABEL, PHOTO_SERVER_KEY));
    static HashSet<String> DEFAULT_V2_DOMAIN = new HashSet<>(Arrays.asList(SERIALIZED_DETAILS, PHOTO_SERVER_IDENTITY, PHOTO_SERVER_LABEL, PHOTO_SERVER_KEY));


    public Integer version;
    public String serialized_details;
    public byte[] photo_server_identity; // non-null only for group v2 (as we don't know which admin hosts the photo)
    public byte[] photo_server_label;
    public byte[] photo_server_key;
    public HashSet<String> domain;


    public static GroupDetailsSyncSnapshot of(IdentityManagerSession identityManagerSession, ContactGroupDetails contactGroupDetails) {
        GroupDetailsSyncSnapshot groupDetailsSyncSnapshot = new GroupDetailsSyncSnapshot();
        groupDetailsSyncSnapshot.version = contactGroupDetails.getVersion();
        groupDetailsSyncSnapshot.serialized_details = contactGroupDetails.getSerializedJsonDetails();
        if (contactGroupDetails.getPhotoServerLabel() != null && contactGroupDetails.getPhotoServerKey() != null) {
            groupDetailsSyncSnapshot.photo_server_label = contactGroupDetails.getPhotoServerLabel().getBytes();
            groupDetailsSyncSnapshot.photo_server_key = Encoded.of(contactGroupDetails.getPhotoServerKey()).getBytes();
        }
        groupDetailsSyncSnapshot.domain = DEFAULT_V1_DOMAIN;
        return groupDetailsSyncSnapshot;
    }

    public static GroupDetailsSyncSnapshot of(IdentityManagerSession identityManagerSession, ContactGroupV2Details contactGroupV2Details) {
        GroupDetailsSyncSnapshot groupDetailsSyncSnapshot = new GroupDetailsSyncSnapshot();
        groupDetailsSyncSnapshot.serialized_details = contactGroupV2Details.getSerializedJsonDetails();
        if (contactGroupV2Details.getPhotoServerLabel() != null && contactGroupV2Details.getPhotoServerKey() != null) {
            groupDetailsSyncSnapshot.photo_server_identity = contactGroupV2Details.getPhotoServerIdentity() == null ? null : contactGroupV2Details.getPhotoServerIdentity().getBytes();
            groupDetailsSyncSnapshot.photo_server_label = contactGroupV2Details.getPhotoServerLabel().getBytes();
            groupDetailsSyncSnapshot.photo_server_key = Encoded.of(contactGroupV2Details.getPhotoServerKey()).getBytes();
        }
        groupDetailsSyncSnapshot.domain = DEFAULT_V2_DOMAIN;
        return groupDetailsSyncSnapshot;
    }


    @JsonIgnore
    public ContactGroupDetails restoreGroup(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity groupOwnerIdentity, byte[] groupOwnerAndUid) throws Exception {
        if (!domain.contains(VERSION) || !domain.contains(SERIALIZED_DETAILS)) {
            Logger.e("Trying to restoreGroup an incomplete GroupDetailsSyncSnapshot. Domain: " + domain);
            throw new Exception();
        }

        UID photoServerLabel;
        AuthEncKey photoServerKey;
        if (domain.contains(PHOTO_SERVER_LABEL) && domain.contains(PHOTO_SERVER_KEY) && photo_server_key != null && photo_server_label != null) {
            try {
                photoServerLabel = new UID(photo_server_label);
                photoServerKey = (AuthEncKey) new Encoded(photo_server_key).decodeSymmetricKey();
            } catch (Exception e) {
                e.printStackTrace();
                photoServerLabel = null;
                photoServerKey = null;
            }
        } else {
            photoServerLabel = null;
            photoServerKey = null;
        }

        ContactGroupDetails contactGroupDetails = new ContactGroupDetails(identityManagerSession, groupOwnerAndUid, ownedIdentity, version, serialized_details, null, photoServerLabel, photoServerKey);
        contactGroupDetails.insert();
        if (groupOwnerIdentity.equals(ownedIdentity) && photoServerLabel != null && photoServerKey != null) {
            ServerUserData.createForOwnedGroupDetails(identityManagerSession, ownedIdentity, photoServerLabel, groupOwnerAndUid);
        }
        return contactGroupDetails;
    }

    @JsonIgnore
    public ContactGroupV2Details restoreGroupV2(IdentityManagerSession identityManagerSession, Identity ownedIdentity, GroupV2.Identifier groupIdentifier, int version) throws Exception {
        if (!domain.contains(SERIALIZED_DETAILS)) {
            Logger.e("Trying to restoreGroupV2 an incomplete GroupDetailsSyncSnapshot. Domain: " + domain);
            throw new Exception();
        }

        Identity photoServerIdentity;
        UID photoServerLabel;
        AuthEncKey photoServerKey;
        if (domain.contains(PHOTO_SERVER_IDENTITY) && domain.contains(PHOTO_SERVER_LABEL) && domain.contains(PHOTO_SERVER_KEY) && photo_server_key != null && photo_server_label != null) {
            try {
                photoServerIdentity = photo_server_identity == null ? null : Identity.of(photo_server_identity);
                photoServerLabel = new UID(photo_server_label);
                photoServerKey = (AuthEncKey) new Encoded(photo_server_key).decodeSymmetricKey();
            } catch (Exception e) {
                e.printStackTrace();
                photoServerIdentity = null;
                photoServerLabel = null;
                photoServerKey = null;
            }
        } else {
            photoServerIdentity = null;
            photoServerLabel = null;
            photoServerKey = null;
        }

        ContactGroupV2Details contactGroupV2Details = new ContactGroupV2Details(identityManagerSession, groupIdentifier.groupUid, groupIdentifier.serverUrl, groupIdentifier.category, ownedIdentity, version, serialized_details, null, photoServerIdentity, photoServerLabel, photoServerKey);
        contactGroupV2Details.insert();
        if (ownedIdentity.equals(photoServerIdentity)) {
            ServerUserData.createForGroupV2(identityManagerSession, ownedIdentity, photoServerLabel, groupIdentifier.getBytes());
        }
        return contactGroupV2Details;
    }

    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof GroupDetailsSyncSnapshot)) {
            return false;
        }

        GroupDetailsSyncSnapshot other = (GroupDetailsSyncSnapshot) otherSnapshotNode;
        HashSet<String> domainIntersection = new HashSet<>(domain);
        domainIntersection.retainAll(other.domain);

        for (String item : domainIntersection) {
            switch (item) {
                case VERSION: {
                    if (!Objects.equals(version, other.version)) {
                        return false;
                    }
                    break;
                }
                case SERIALIZED_DETAILS: {
                    // TODO: we need to deserialize here for the comparison
                    if (!Objects.equals(serialized_details, other.serialized_details)) {
                        return false;
                    }
                    break;
                }
                case PHOTO_SERVER_IDENTITY: {
                    if (!Arrays.equals(photo_server_identity, other.photo_server_identity)) {
                        return false;
                    }
                    break;
                }
                case PHOTO_SERVER_LABEL: {
                    if (!Arrays.equals(photo_server_label, other.photo_server_label)) {
                        return false;
                    }
                    break;
                }
                case PHOTO_SERVER_KEY: {
                    try {
                        if ((photo_server_key == null && other.photo_server_key != null)
                                || (photo_server_key != null && other.photo_server_key == null)
                                || (photo_server_key != null && !Objects.equals(new Encoded(photo_server_key).decodeSymmetricKey(), new Encoded(other.photo_server_key).decodeSymmetricKey()))) {
                            return false;
                        }
                    } catch (DecodingException e) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public List<ObvSyncDiff> computeDiff(ObvSyncSnapshotNode otherSnapshotNode) throws Exception {
        // TODO computeDiff
        return null;
    }
}
