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

package io.olvid.engine.datatypes.containers;

import java.util.Objects;

import io.olvid.engine.datatypes.Identity;

public class TrustOrigin {
    public enum TYPE {
        DIRECT,
        INTRODUCTION,
        GROUP,
        KEYCLOAK,
        SERVER_GROUP_V2,
    }

    private final TYPE type;
    private final long timestamp;
    private final Identity mediatorOrGroupOwnerIdentity;
    private final String keycloakServer;
    private final GroupV2.Identifier groupIdentifier;

    public TYPE getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Identity getMediatorOrGroupOwnerIdentity() {
        return mediatorOrGroupOwnerIdentity;
    }

    public String getKeycloakServer() {
        return keycloakServer;
    }

    public GroupV2.Identifier getGroupIdentifier() {
        return groupIdentifier;
    }

    public TrustOrigin(TYPE type, long timestamp, Identity mediatorOrGroupOwnerIdentity, String keycloakServer, GroupV2.Identifier groupIdentifier) {
        this.type = type;
        this.timestamp = timestamp;
        this.mediatorOrGroupOwnerIdentity = mediatorOrGroupOwnerIdentity;
        this.keycloakServer = keycloakServer;
        this.groupIdentifier = groupIdentifier;
    }

    public static TrustOrigin createDirectTrustOrigin(long timestamp) {
        return new TrustOrigin(
                TYPE.DIRECT,
                timestamp,
                null,
                null,
                null);
    }

    public static TrustOrigin createIntroductionTrustOrigin(long timestamp, Identity mediatorIdentity) {
        return new TrustOrigin(
                TYPE.INTRODUCTION,
                timestamp,
                mediatorIdentity,
                null,
                null);
    }

    public static TrustOrigin createGroupTrustOrigin(long timestamp, Identity groupOwner) {
        return new TrustOrigin(
                TYPE.GROUP,
                timestamp,
                groupOwner,
                null,
                null);
    }

    public static TrustOrigin createKeycloakTrustOrigin(long timestamp, String keycloakServer) {
        return new TrustOrigin(
                TYPE.KEYCLOAK,
                timestamp,
                null,
                keycloakServer,
                null);
    }

    public static TrustOrigin createServerGroupV2TrustOrigin(long timestamp, GroupV2.Identifier groupIdentifier) {
        return new TrustOrigin(
                TYPE.SERVER_GROUP_V2,
                timestamp,
                null,
                null,
                groupIdentifier);
    }


    // Note that equals does not check the timestamp
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TrustOrigin)) {
            return false;
        }
        TrustOrigin castedOther = (TrustOrigin) other;
        if (castedOther.type != type) {
            return false;
        }
        switch (type) {
            case INTRODUCTION:
            case GROUP:
                return Objects.equals(castedOther.mediatorOrGroupOwnerIdentity, mediatorOrGroupOwnerIdentity);
            case KEYCLOAK:
                return Objects.equals(castedOther.keycloakServer, keycloakServer);
            case SERVER_GROUP_V2:
                return Objects.equals(castedOther.groupIdentifier, groupIdentifier);
            case DIRECT:
            default:
                return true;
        }
    }
}
