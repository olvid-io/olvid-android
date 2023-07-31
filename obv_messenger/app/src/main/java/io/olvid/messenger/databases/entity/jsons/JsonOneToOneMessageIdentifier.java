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

package io.olvid.messenger.databases.entity.jsons;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Arrays;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class JsonOneToOneMessageIdentifier {
    public byte[] bytesIdentityA;
    public byte[] bytesIdentityB;

    public JsonOneToOneMessageIdentifier() {}

    public JsonOneToOneMessageIdentifier(byte[] bytesIdentityA, byte[] bytesIdentityB) {
        this.bytesIdentityA = bytesIdentityA;
        this.bytesIdentityB = bytesIdentityB;
    }

    @JsonIgnore
    @Nullable
    public byte[] getBytesContactIdentity(@NonNull byte[] bytesOwnedIdentity) {
        if (Arrays.equals(bytesOwnedIdentity, bytesIdentityA)) {
            return bytesIdentityB;
        } else if (Arrays.equals(bytesOwnedIdentity, bytesIdentityB)) {
            return bytesIdentityA;
        } else {
            return null;
        }
    }
}
