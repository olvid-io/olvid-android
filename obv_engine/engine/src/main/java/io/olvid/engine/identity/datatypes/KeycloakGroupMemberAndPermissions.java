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

package io.olvid.engine.identity.datatypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakGroupMemberAndPermissions {
    public String keycloakUserId;
    public byte[] identity;
    public String signedUserDetails;
    public List<String> permissions;
    public byte[] groupInvitationNonce;

    @JsonProperty("id")
    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    @JsonProperty("id")
    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    @JsonProperty("identity")
    public byte[] getIdentity() {
        return identity;
    }

    @JsonProperty("identity")
    public void setIdentity(byte[] identity) {
        this.identity = identity;
    }

    @JsonProperty("signature")
    public String getSignedUserDetails() {
        return signedUserDetails;
    }

    @JsonProperty("signature")
    public void setSignedUserDetails(String signedUserDetails) {
        this.signedUserDetails = signedUserDetails;
    }

    @JsonProperty("permissions")
    public List<String> getPermissions() {
        return permissions;
    }

    @JsonProperty("permissions")
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    @JsonProperty("nonce")
    public byte[] getGroupInvitationNonce() {
        return groupInvitationNonce;
    }

    @JsonProperty("nonce")
    public void setGroupInvitationNonce(byte[] groupInvitationNonce) {
        this.groupInvitationNonce = groupInvitationNonce;
    }

    public int hashCode() {
        return keycloakUserId.hashCode();
    }

    // equals only matches the keycloakUserId to avoid duplicate group members when building sets of GroupMember
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof KeycloakGroupMemberAndPermissions)) {
            return false;
        }
        return keycloakUserId.equals(((KeycloakGroupMemberAndPermissions) obj).keycloakUserId);
    }
}
