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

package io.olvid.engine.engine.types.identities;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.containers.TrustOrigin;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.metamanager.IdentityDelegate;


public class ObvTrustOrigin {
    public enum TYPE {
        DIRECT,
        INTRODUCTION,
        GROUP,
        KEYCLOAK,
        SERVER_GROUP_V2,
    }

    private final TYPE type;
    private final long timestamp;
    private final ObvIdentity mediatorOrGroupOwner;
    private final String keycloakServer;
    private final byte[] bytesGroupIdentifier;

    public TYPE getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ObvIdentity getMediatorOrGroupOwner() {
        return mediatorOrGroupOwner;
    }

    public String getKeycloakServer() {
        return keycloakServer;
    }

    public byte[] getBytesGroupIdentifier() {
        return bytesGroupIdentifier;
    }

    public ObvTrustOrigin(Session session, IdentityDelegate identityDelegate, TrustOrigin trustOrigin, Identity ownedIdentity) throws Exception {
        switch (trustOrigin.getType()) {
            case DIRECT:
                this.type = TYPE.DIRECT;
                break;
            case INTRODUCTION:
                this.type = TYPE.INTRODUCTION;
                break;
            case GROUP:
                this.type = TYPE.GROUP;
                break;
            case KEYCLOAK:
                this.type = TYPE.KEYCLOAK;
                break;
            case SERVER_GROUP_V2:
                this.type = TYPE.SERVER_GROUP_V2;
                break;
            default:
                throw new Exception("Unknown TrustOrigin type " + trustOrigin.getType());
        }

        this.timestamp = trustOrigin.getTimestamp();
        ObvIdentity mediatorOrGroupOwner = null;
        if (trustOrigin.getMediatorOrGroupOwnerIdentity() != null) {
            try {
                mediatorOrGroupOwner = new ObvIdentity(session, identityDelegate, trustOrigin.getMediatorOrGroupOwnerIdentity(), ownedIdentity);
            } catch (Exception e) {
                mediatorOrGroupOwner = new ObvIdentity(trustOrigin.getMediatorOrGroupOwnerIdentity(), new JsonIdentityDetails(), false, true);
            }
        }
        this.mediatorOrGroupOwner = mediatorOrGroupOwner;
        this.keycloakServer = trustOrigin.getKeycloakServer();
        this.bytesGroupIdentifier = trustOrigin.getGroupIdentifier() == null ? null : trustOrigin.getGroupIdentifier().getBytes();
    }
}
