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

package io.olvid.engine.engine.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonKeycloakRevocation {
    byte[] bytesRevokedIdentity;
    long revocationTimestamp;
    int revocationType;

    public JsonKeycloakRevocation() { }

    @JsonProperty("identity")
    public byte[] getBytesRevokedIdentity() {
        return bytesRevokedIdentity;
    }

    @JsonProperty("identity")
    public void setBytesRevokedIdentity(byte[] bytesRevokedIdentity) {
        this.bytesRevokedIdentity = bytesRevokedIdentity;
    }

    @JsonProperty("timestamp")
    public long getRevocationTimestamp() {
        return revocationTimestamp;
    }

    @JsonProperty("timestamp")
    public void setRevocationTimestamp(long revocationTimestamp) {
        this.revocationTimestamp = revocationTimestamp;
    }

    @JsonProperty("type")
    public int getRevocationType() {
        return revocationType;
    }

    @JsonProperty("type")
    public void setRevocationType(int revocationType) {
        this.revocationType = revocationType;
    }
}