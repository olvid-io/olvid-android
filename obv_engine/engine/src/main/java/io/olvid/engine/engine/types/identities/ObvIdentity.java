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

package io.olvid.engine.engine.types.identities;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.metamanager.IdentityDelegate;

public class ObvIdentity implements Comparable<ObvIdentity> {
    protected final Identity identity;
    protected final JsonIdentityDetails identityDetails;
    protected final boolean keycloakManaged;
    protected final boolean active;

    public ObvIdentity(Identity identity, JsonIdentityDetails identityDetails, boolean keycloakManaged, boolean active) {
        this.identity = identity;
        this.identityDetails = identityDetails;
        this.keycloakManaged = keycloakManaged;
        this.active = active;
    }

    public ObvIdentity(Session session, IdentityDelegate identityDelegate, Identity ownedIdentity) throws SQLException {
        this.identity = ownedIdentity;
        this.identityDetails = identityDelegate.getOwnedIdentityPublishedDetails(session, ownedIdentity).getIdentityDetails();
        this.keycloakManaged = identityDelegate.isOwnedIdentityKeycloakManaged(session, ownedIdentity);
        this.active = identityDelegate.isActiveOwnedIdentity(session, ownedIdentity);
    }

    public ObvIdentity(Session session, IdentityDelegate identityDelegate, Identity contactIdentity, Identity ownedIdentity) throws SQLException {
        this.identity = contactIdentity;
        this.identityDetails = identityDelegate.getContactIdentityTrustedDetails(session, ownedIdentity, contactIdentity);
        this.keycloakManaged = identityDelegate.isContactIdentityCertifiedByOwnKeycloak(session, ownedIdentity, contactIdentity);
        this.active = identityDelegate.isIdentityAnActiveContactOfOwnedIdentity(session, ownedIdentity, contactIdentity);
    }

    public byte[] getBytesIdentity() {
        return identity.getBytes();
    }

    public String getServer() { return identity.getServer(); }

    public JsonIdentityDetails getIdentityDetails() {
        return identityDetails;
    }

    public boolean isKeycloakManaged() {
        return keycloakManaged;
    }

    public boolean isActive() {
        return active;
    }

    // region Encoded
    public static ObvIdentity of(Encoded encoded, ObjectMapper jsonObjectMapper) throws Exception {
        Encoded[] list = encoded.decodeList();
        if (list.length != 2) {
            throw new DecodingException();
        }
        return new ObvIdentity(list[0].decodeIdentity(), jsonObjectMapper.readValue(list[1].decodeString(), JsonIdentityDetails.class), false, true);
    }

    public Encoded encode(ObjectMapper jsonObjectMapper) throws Exception {
        return Encoded.of(new Encoded[]{
                Encoded.of(identity),
                Encoded.of(jsonObjectMapper.writeValueAsString(identityDetails)),
        });
    }
    // endregion



    @Override
    public boolean equals(Object other) {
        if (!getClass().equals(other.getClass())) {
            return false;
        }
        return identity.equals(((ObvIdentity) other).identity);
    }

    @Override
    public int compareTo(ObvIdentity o) {
        return identity.computeUniqueUid().compareTo(o.identity.computeUniqueUid());
    }
}
