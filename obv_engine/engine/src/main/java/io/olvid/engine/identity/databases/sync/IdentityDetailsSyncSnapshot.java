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

package io.olvid.engine.identity.databases.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.sync.ObvSyncDiff;
import io.olvid.engine.engine.types.sync.ObvSyncSnapshotNode;
import io.olvid.engine.identity.databases.OwnedIdentityDetails;
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


    public static IdentityDetailsSyncSnapshot of(IdentityManagerSession identityManagerSession, OwnedIdentityDetails publishedDetails) {
        IdentityDetailsSyncSnapshot identityDetailsSyncSnapshot = new IdentityDetailsSyncSnapshot();
        identityDetailsSyncSnapshot.version = publishedDetails.getVersion();
        identityDetailsSyncSnapshot.serialized_details = publishedDetails.getSerializedJsonDetails();
        identityDetailsSyncSnapshot.photo_server_label = publishedDetails.getPhotoServerLabel().getBytes();
        identityDetailsSyncSnapshot.photo_server_key = Encoded.of(publishedDetails.getPhotoServerKey()).getBytes();
        identityDetailsSyncSnapshot.domain = DEFAULT_DOMAIN;
        return identityDetailsSyncSnapshot;
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
        // TODO
        return null;
    }
}
