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
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.ContactIdentityDetails;
import io.olvid.engine.identity.databases.OwnedIdentityDetails;
import io.olvid.engine.identity.databases.ServerUserData;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;

// This class is used for both owned identity and contacts
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityDetailsSyncSnapshot implements ObvSyncSnapshotNode {
    public static final String VERSION = "version";
    public static final String SERIALIZED_DETAILS = "serialized_details";
    public static final String PHOTO_SERVER_LABEL = "photo_server_label";
    public static final String PHOTO_SERVER_KEY = "photo_server_key";
    static HashSet<String> DEFAULT_DOMAIN = new HashSet<>(Arrays.asList(VERSION, SERIALIZED_DETAILS, PHOTO_SERVER_LABEL, PHOTO_SERVER_KEY));


    public Integer version;
    public String serialized_details;
    public byte[] photo_server_label;
    public byte[] photo_server_key;
    public HashSet<String> domain;


    public static IdentityDetailsSyncSnapshot of(IdentityManagerSession identityManagerSession, OwnedIdentityDetails ownedIdentityDetails) {
        IdentityDetailsSyncSnapshot identityDetailsSyncSnapshot = new IdentityDetailsSyncSnapshot();
        identityDetailsSyncSnapshot.version = ownedIdentityDetails.getVersion();
        identityDetailsSyncSnapshot.serialized_details = ownedIdentityDetails.getSerializedJsonDetails();
        if (ownedIdentityDetails.getPhotoServerLabel() != null && ownedIdentityDetails.getPhotoServerKey() != null) {
            identityDetailsSyncSnapshot.photo_server_label = ownedIdentityDetails.getPhotoServerLabel().getBytes();
            identityDetailsSyncSnapshot.photo_server_key = Encoded.of(ownedIdentityDetails.getPhotoServerKey()).getBytes();
        }
        identityDetailsSyncSnapshot.domain = DEFAULT_DOMAIN;
        return identityDetailsSyncSnapshot;
    }

    public static IdentityDetailsSyncSnapshot of(IdentityManagerSession identityManagerSession, ContactIdentityDetails contactIdentityDetails) {
        IdentityDetailsSyncSnapshot identityDetailsSyncSnapshot = new IdentityDetailsSyncSnapshot();
        identityDetailsSyncSnapshot.version = contactIdentityDetails.getVersion();
        identityDetailsSyncSnapshot.serialized_details = contactIdentityDetails.getSerializedJsonDetails();
        if (contactIdentityDetails.getPhotoServerLabel() != null && contactIdentityDetails.getPhotoServerKey() != null) {
            identityDetailsSyncSnapshot.photo_server_label = contactIdentityDetails.getPhotoServerLabel().getBytes();
            identityDetailsSyncSnapshot.photo_server_key = Encoded.of(contactIdentityDetails.getPhotoServerKey()).getBytes();
        }
        identityDetailsSyncSnapshot.domain = DEFAULT_DOMAIN;
        return identityDetailsSyncSnapshot;
    }

    @JsonIgnore
    public OwnedIdentityDetails restoreOwned(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws Exception {
        if (!domain.contains(VERSION) || !domain.contains(SERIALIZED_DETAILS)) {
            Logger.e("Trying to restore an incomplete IdentityDetailsSyncSnapshot. Domain: " + domain);
            throw new Exception();
        }

        UID photoServerLabel;
        AuthEncKey photoServerKey;
        if (domain.contains(PHOTO_SERVER_LABEL) && domain.contains(PHOTO_SERVER_KEY) && photo_server_key != null && photo_server_label != null) {
            try {
                photoServerLabel = new UID(photo_server_label);
                photoServerKey = (AuthEncKey) new Encoded(photo_server_key).decodeSymmetricKey();
            } catch (Exception e) {
                Logger.x(e);
                photoServerLabel = null;
                photoServerKey = null;
            }
        } else {
            photoServerLabel = null;
            photoServerKey = null;
        }
        OwnedIdentityDetails ownedIdentityDetails = new OwnedIdentityDetails(identityManagerSession, ownedIdentity, version, serialized_details, null, photoServerLabel, photoServerKey);
        ownedIdentityDetails.insert();
        if (photoServerLabel != null && photoServerKey != null) {
            ServerUserData.createForOwnedIdentityDetails(identityManagerSession, ownedIdentity, photoServerLabel);
        }
        return ownedIdentityDetails;
    }

    @JsonIgnore
    public ContactIdentityDetails restoreContact(IdentityManagerSession identityManagerSession, Identity ownedIdentity, Identity contactIdentity) throws Exception {
        if (!domain.contains(VERSION) || !domain.contains(SERIALIZED_DETAILS)) {
            Logger.e("Trying to restore an incomplete IdentityDetailsSyncSnapshot. Domain: " + domain);
            throw new Exception();
        }

        UID photoServerLabel;
        AuthEncKey photoServerKey;
        if (domain.contains(PHOTO_SERVER_LABEL) && domain.contains(PHOTO_SERVER_KEY) && photo_server_key != null && photo_server_label != null) {
            try {
                photoServerLabel = new UID(photo_server_label);
                photoServerKey = (AuthEncKey) new Encoded(photo_server_key).decodeSymmetricKey();
            } catch (Exception e) {
                Logger.x(e);
                photoServerLabel = null;
                photoServerKey = null;
            }
        } else {
            photoServerLabel = null;
            photoServerKey = null;
        }
        ContactIdentityDetails contactIdentityDetails = new ContactIdentityDetails(identityManagerSession,contactIdentity, ownedIdentity, version, serialized_details, null, photoServerLabel, photoServerKey);
        contactIdentityDetails.insert();
        return contactIdentityDetails;
    }

    @Override
    public boolean areContentsTheSame(ObvSyncSnapshotNode otherSnapshotNode) {
        if (!(otherSnapshotNode instanceof IdentityDetailsSyncSnapshot)) {
            return false;
        }

        IdentityDetailsSyncSnapshot other = (IdentityDetailsSyncSnapshot) otherSnapshotNode;
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
