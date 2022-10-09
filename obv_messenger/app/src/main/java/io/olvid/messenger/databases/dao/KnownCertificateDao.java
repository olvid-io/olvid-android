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

package io.olvid.messenger.databases.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.olvid.messenger.databases.entity.KnownCertificate;

@Dao
public interface KnownCertificateDao {
    @Insert
    long insert(KnownCertificate knownCertificate);

    @Query("UPDATE " + KnownCertificate.TABLE_NAME + " SET " + KnownCertificate.TRUST_TIMESTAMP + " = :trustTimestamp WHERE id = :certificateId")
    void updateTrustTimestamp(long certificateId, long trustTimestamp);

    @Query("SELECT * FROM " + KnownCertificate.TABLE_NAME + " WHERE id = :certificateId")
    KnownCertificate get(long certificateId);

    @Query("SELECT * FROM " + KnownCertificate.TABLE_NAME + " WHERE " + KnownCertificate.DOMAIN_NAME + " = :domainName ORDER BY " + KnownCertificate.TRUST_TIMESTAMP + " DESC ")
    List<KnownCertificate> getAllForDomain(String domainName);

    @Query("DELETE FROM " + KnownCertificate.TABLE_NAME + " WHERE " + KnownCertificate.DOMAIN_NAME + " = :domainName AND " + KnownCertificate.EXPIRATION_TIMESTAMP + " < :currentTimestamp")
    void deleteExpired(String domainName, long currentTimestamp);

    @Query("SELECT * FROM " + KnownCertificate.TABLE_NAME)
    List<KnownCertificate> getAll();
}
