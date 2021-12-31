/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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

package io.olvid.messenger.databases.tasks;


import android.util.Base64;

import androidx.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.KnownCertificate;
import io.olvid.messenger.settings.SettingsActivity;

public class VerifySSLConnectionCertificatesTask implements Runnable {
    private final static HashMap<Long, Long> lastUntrustedCertificateNotification = new HashMap<>();
    private static final long NOTIFICATION_MIN_INTERVAL_MILLIS = 1_800_000; // only notify the user every 30 minutes

    public static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n";
    public static final String END_CERTIFICATE = "-----END CERTIFICATE-----\n";

    private final String domainName;
    private final Certificate[] certificates;

    public VerifySSLConnectionCertificatesTask(String domainName, Certificate[] certificates) {
        this.domainName = domainName;
        this.certificates = certificates;
    }

    @Override
    public void run() {
        try {
            AppDatabase db = AppDatabase.getInstance();

            if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate)) {
                Logger.w("SSL handshake finished with no certificates or a non-X.509 certificate. Aborting user certificate validation.");
                return;
            }

            List<KnownCertificate> knownCertificates = db.knownCertificateDao().getAllForDomain(domainName);

            if (knownCertificates.size() == 0) {
                // no known certificate, automatically trust this new certificate (trust on first use)
                addCertificatesToDb(true);
            } else {
                // check all known certificates and find a matching one
                X509Certificate domainCertificate = (X509Certificate) certificates[0];
                byte[] certificateBytes = domainCertificate.getEncoded();

                boolean domainCertificateTrusted = false;
                Long domainCertificateId = null;
                Long lastTrustedCertificateId = null;

                for (KnownCertificate knownCertificate : knownCertificates) {
                    if (Arrays.equals(certificateBytes, knownCertificate.certificateBytes)) {
                        domainCertificateId = knownCertificate.id;

                        if (knownCertificate.isTrusted()) {
                            domainCertificateTrusted = true;
                            break;
                        } else if (lastTrustedCertificateId != null) {
                            break;
                        }
                    } else if (lastTrustedCertificateId == null && knownCertificate.isTrusted()) {
                        lastTrustedCertificateId = knownCertificate.id;
                        if (domainCertificateId != null) {
                            break;
                        }
                    }
                }

                if (domainCertificateId == null) {
                    if (SettingsActivity.notifyCertificateChange()) {
                        KnownCertificate newKnownCertificate = addCertificatesToDb(false);
                        if (newKnownCertificate != null) {
                            notifyUser(newKnownCertificate.id, lastTrustedCertificateId);
                        }
                    } else {
                        AppDatabase.getInstance().knownCertificateDao().deleteExpired(domainName, System.currentTimeMillis());
                        addCertificatesToDb(true);
                    }
                } else if (!domainCertificateTrusted) {
                    if (SettingsActivity.notifyCertificateChange()) {
                        notifyUser(domainCertificateId, lastTrustedCertificateId);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private KnownCertificate addCertificatesToDb(boolean autoTrust) {
        try {
            X509Certificate domainCertificate = (X509Certificate) certificates[0];
            byte[] certificateBytes = domainCertificate.getEncoded();

            StringBuilder sb = new StringBuilder();

            sb.append(BEGIN_CERTIFICATE);
            sb.append(Base64.encodeToString(domainCertificate.getEncoded(), Base64.DEFAULT));
            sb.append(END_CERTIFICATE);

            String[] issuers = new String[certificates.length - 1];
            for (int i=0; i<issuers.length; i++) {
                X509Certificate issuerCertificate = (X509Certificate) certificates[i+1];
                issuers[i] = issuerCertificate.getIssuerDN().getName();
                sb.append(BEGIN_CERTIFICATE);
                sb.append(Base64.encodeToString(issuerCertificate.getEncoded(), Base64.DEFAULT));
                sb.append(END_CERTIFICATE);
            }

            String issuersString = AppSingleton.getJsonObjectMapper().writeValueAsString(issuers);

            KnownCertificate newKnownCertificate = new KnownCertificate(
                    domainName,
                    certificateBytes,
                    autoTrust ? System.currentTimeMillis() : null,
                    domainCertificate.getNotAfter().getTime(),
                    issuersString,
                    sb.toString()
            );

            newKnownCertificate.id = AppDatabase.getInstance().knownCertificateDao().insert(newKnownCertificate);
            return newKnownCertificate;
        } catch (CertificateEncodingException | JsonProcessingException e) {
            Logger.e("Error storing SSL certificate in DB.");
        } catch (Exception e) {
            Logger.e("Unexpected exception while storing SSL certificate in DB");
            e.printStackTrace();
        }
        return null;
    }

    private void notifyUser(long untrustedCertificateId, @Nullable Long lastTrustedCertificateId) {
        Long lastNotificationTimestamp = lastUntrustedCertificateNotification.get(untrustedCertificateId);
        if (lastNotificationTimestamp == null || lastNotificationTimestamp < System.currentTimeMillis() - NOTIFICATION_MIN_INTERVAL_MILLIS) {
            lastUntrustedCertificateNotification.put(untrustedCertificateId, System.currentTimeMillis());

            App.openAppDialogCertificateChanged(untrustedCertificateId, lastTrustedCertificateId);
        }
    }
}
