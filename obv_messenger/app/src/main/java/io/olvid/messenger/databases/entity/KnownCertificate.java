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

package io.olvid.messenger.databases.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = KnownCertificate.TABLE_NAME,
        indices = {
                @Index(KnownCertificate.DOMAIN_NAME),
        }
)
public class KnownCertificate {
    public static final String TABLE_NAME = "known_certificate";

    public static final String DOMAIN_NAME = "domain_name";
    public static final String CERTIFICATE_BYTES = "certificate_bytes";
    public static final String EXPIRATION_TIMESTAMP = "expiration_timestamp";
    public static final String TRUST_TIMESTAMP = "trust_timestamp";
    public static final String ISSUERS = "issuers"; // json list of String
    public static final String ENCODED_FULL_CHAIN = "encoded_full_chain"; // pem encoded full chain

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = DOMAIN_NAME)
    @NonNull
    public String domainName;

    @ColumnInfo(name = CERTIFICATE_BYTES)
    @NonNull
    public byte[] certificateBytes;

    @ColumnInfo(name = TRUST_TIMESTAMP)
    @Nullable
    public Long trustTimestamp;

    @ColumnInfo(name = EXPIRATION_TIMESTAMP)
    public long expirationTimestamp;

    @ColumnInfo(name = ISSUERS)
    @NonNull
    public String issuers;

    @ColumnInfo(name = ENCODED_FULL_CHAIN)
    @NonNull
    public String encodedFullChain;

    // default constructor used by room
    public KnownCertificate(@NonNull String domainName, @NonNull byte[] certificateBytes, @Nullable Long trustTimestamp, long expirationTimestamp, @NonNull String issuers, @NonNull String encodedFullChain) {
        this.domainName = domainName;
        this.certificateBytes = certificateBytes;
        this.trustTimestamp = trustTimestamp;
        this.expirationTimestamp = expirationTimestamp;
        this.issuers = issuers;
        this.encodedFullChain = encodedFullChain;
    }

    public boolean isTrusted() {
        return trustTimestamp != null;
    }
}
