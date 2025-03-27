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

package io.olvid.messenger.customClasses;


import android.os.Build;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.KnownCertificate;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.settings.SettingsActivity;

public class CustomSSLSocketFactory extends SSLSocketFactory implements HandshakeCompletedListener {
    private final static HashMap<Long, Long> lastUntrustedCertificateNotification = new HashMap<>();
    private static final long NOTIFICATION_MIN_INTERVAL_MILLIS = 1_800_000; // only notify the user every 30 minutes

    public static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n";
    public static final String END_CERTIFICATE = "-----END CERTIFICATE-----\n";


    private final SSLSocketFactory sslSocketFactory;
    private final HashMap<String, List<KnownCertificate>> knownCertificatesByDomainCache;
    private boolean cacheInitialized;

    public CustomSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
        this.knownCertificatesByDomainCache = new HashMap<>();
        this.cacheInitialized = false;
    }

    public void loadKnownCertificates() {
        App.runThread(() -> {
            synchronized (knownCertificatesByDomainCache) {
                List<KnownCertificate> knownCertificates = AppDatabase.getInstance().knownCertificateDao().getAll();
                for (KnownCertificate knownCertificate : knownCertificates) {
                    List<KnownCertificate> cachedList = knownCertificatesByDomainCache.get(knownCertificate.domainName);
                    if (cachedList == null) {
                        cachedList = new ArrayList<>();
                        knownCertificatesByDomainCache.put(knownCertificate.domainName, cachedList);
                    }
                    cachedList.add(knownCertificate);
                }
                for (List<KnownCertificate> cachedList : knownCertificatesByDomainCache.values()) {
                    sortCertificateList(cachedList);
                }
                this.cacheInitialized = true;
            }
        });
    }

    private void sortCertificateList(List<KnownCertificate> list) {
        Collections.sort(list, (cert1, cert2) -> {
            if (cert1.trustTimestamp == null && cert2.trustTimestamp == null) {
                return 0;
            } else if (cert1.trustTimestamp == null) {
                return -1;
            } else if (cert2.trustTimestamp == null) {
                return 1;
            } else {
                return Long.compare(cert2.trustTimestamp, cert1.trustTimestamp);
            }
        });
    }



    @Override
    public String[] getDefaultCipherSuites() {
        return sslSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return sslSocketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return configureSocket(sslSocketFactory.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return configureSocket(sslSocketFactory.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return configureSocket(sslSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return configureSocket(sslSocketFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return configureSocket(sslSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return configureSocket(sslSocketFactory.createSocket(address, port, localAddress, localPort));
    }

    private Socket configureSocket(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) socket;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                sslSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});

                sslSocket.setEnabledCipherSuites(new String[]{
                        "TLS_AES_256_GCM_SHA384",
                        "TLS_AES_128_GCM_SHA256",
                        "TLS_CHACHA20_POLY1305_SHA256",
                        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                });
            } else {
                sslSocket.setEnabledProtocols(new String[]{"TLSv1.2"});

                sslSocket.setEnabledCipherSuites(new String[]{
                        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                });
            }
            sslSocket.addHandshakeCompletedListener(this);
        }
        return socket;
    }

    @Override
    public void handshakeCompleted(HandshakeCompletedEvent event) {
        try {
            final String hostname = event.getSession().getPeerHost();
            if (hostname == null) {
                return;
            }
            Logger.d("Connected to " + hostname + " using cipher suite " + event.getCipherSuite());
            final Certificate[] certificates = event.getPeerCertificates();

            synchronized (knownCertificatesByDomainCache) {
                if (!cacheInitialized || !verifySllCertificateAndAllowConnection(hostname, certificates)) {
                    Logger.e("Connection to " + hostname + " was blocked");
                    try {
                        event.getSocket().shutdownOutput();
                        event.getSocket().shutdownInput();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean verifySllCertificateAndAllowConnection(String domainName, Certificate[] certificates) {
        try {
            if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate)) {
                Logger.w("SSL handshake finished with no certificates or a non-X.509 certificate. Aborting user certificate validation.");
                return true;
            }
            synchronized (knownCertificatesByDomainCache) {
                List<KnownCertificate> knownCertificates = knownCertificatesByDomainCache.get(domainName);

                if (knownCertificates == null || knownCertificates.size() == 0) {
                    // no known certificate, automatically trust this new certificate (trust on first use)
                    KnownCertificate newKnownCertificate = getKnownCertificateForDb(domainName, certificates, true);
                    if (newKnownCertificate != null) {
                        App.runThread(() -> insertCertificateInDb(domainName, newKnownCertificate));
                    }
                    return true;
                } else {
                    // check all known certificates and find a matching one
                    X509Certificate domainCert = (X509Certificate) certificates[0];
                    byte[] certificateBytes = domainCert.getEncoded();

                    boolean domainCertificateTrusted = false;
                    KnownCertificate domainCertificate = null;
                    KnownCertificate lastTrustedCertificate = null;

                    // this loop assumes that knownCertificates are properly sorted: first untrusted certificates, then trusted certificates (sorted by trust date descending)
                    for (KnownCertificate knownCertificate : knownCertificates) {
                        if (Arrays.equals(certificateBytes, knownCertificate.certificateBytes)) {
                            domainCertificate = knownCertificate;

                            if (knownCertificate.isTrusted()) {
                                domainCertificateTrusted = true;
                                break;
                            } else if (lastTrustedCertificate != null) {
                                break;
                            }
                        } else if (lastTrustedCertificate == null && knownCertificate.isTrusted()) {
                            lastTrustedCertificate = knownCertificate;
                            if (domainCertificate != null) {
                                break;
                            }
                        }
                    }

                    if (domainCertificate == null) {
                        // we have never seen this certificate yet
                        if (SettingsActivity.notifyCertificateChange()) {
                            KnownCertificate newKnownCertificate = getKnownCertificateForDb(domainName, certificates, false);
                            if (newKnownCertificate != null) {
                                boolean allowConnection = shouldAllowConnection(newKnownCertificate, lastTrustedCertificate);

                                Long lastTrustedCertificateId = (lastTrustedCertificate == null) ? null : lastTrustedCertificate.id;
                                App.runThread(() -> {
                                    insertCertificateInDb(domainName, newKnownCertificate);
                                    notifyUser(newKnownCertificate.id, lastTrustedCertificateId, !allowConnection);
                                });
                                return allowConnection;
                            } else {
                                // block if we were not able to getKnownCertificateForDb(), unless set to never block
                                return SettingsActivity.getBlockUntrustedCertificate() == SettingsActivity.BlockUntrustedCertificate.NEVER;
                            }
                        } else {
                            App.runThread(() -> {
                                KnownCertificate newKnownCertificate = getKnownCertificateForDb(domainName, certificates, true);
                                if (newKnownCertificate != null) {
                                    expireCertificatesInDb(domainName, System.currentTimeMillis());
                                    insertCertificateInDb(domainName, newKnownCertificate);
                                }
                            });
                        }
                    } else if (!domainCertificateTrusted) {
                        // we have seen this certificate, but we do not trust it yet
                        if (SettingsActivity.notifyCertificateChange()) {
                            boolean allowConnection = shouldAllowConnection(domainCertificate, lastTrustedCertificate);
                            notifyUser(domainCertificate.id, (lastTrustedCertificate == null) ? null : lastTrustedCertificate.id, !allowConnection);
                            return allowConnection;
                        } else {
                            // trust the certificate automatically
                            KnownCertificate finalDomainCertificate = domainCertificate;
                            App.runThread(() -> trustCertificateInDb(finalDomainCertificate));
                        }
                    }

                    // we already trust this certificate (or we don't care!)
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // block if there was an exception, unless set to never block
            return !SettingsActivity.notifyCertificateChange()
                    || SettingsActivity.getBlockUntrustedCertificate() == SettingsActivity.BlockUntrustedCertificate.NEVER;
        }
    }

    private void expireCertificatesInDb(String domainName, long timestamp) {
        AppDatabase.getInstance().knownCertificateDao().deleteExpired(domainName, timestamp);
        synchronized (knownCertificatesByDomainCache) {
            List<KnownCertificate> list = knownCertificatesByDomainCache.get(domainName);
            if (list != null) {
                List<KnownCertificate> newList = new ArrayList<>();
                for (KnownCertificate knownCertificate : list) {
                    if (knownCertificate.expirationTimestamp >= timestamp) {
                        newList.add(knownCertificate);
                    }
                }
                knownCertificatesByDomainCache.put(domainName, newList);
            }
        }
    }

    private void insertCertificateInDb(String domainName, KnownCertificate certificate) {
        try {
            certificate.id = AppDatabase.getInstance().knownCertificateDao().insert(certificate);
            synchronized (knownCertificatesByDomainCache) {
                List<KnownCertificate> list = knownCertificatesByDomainCache.get(domainName);
                if (list == null) {
                    list = new ArrayList<>();
                    knownCertificatesByDomainCache.put(domainName, list);
                }
                list.add(certificate);
                sortCertificateList(list);
            }
        } catch (Exception e) {
            Logger.e("Exception while inserting KnownCertificate certificate in DB");
            e.printStackTrace();
        }
    }

    public void trustCertificateInDb(KnownCertificate certificate) {
        synchronized (knownCertificatesByDomainCache) {
            String domainName = certificate.domainName;
            long timestamp = System.currentTimeMillis();
            List<KnownCertificate> list = knownCertificatesByDomainCache.get(domainName);
            if (list != null) {
                for (KnownCertificate knownCertificate : list) {
                    if (knownCertificate.id == certificate.id) {
                        knownCertificate.trustTimestamp = timestamp;
                    }
                }
                sortCertificateList(list);
            }
            AppDatabase.getInstance().knownCertificateDao().updateTrustTimestamp(certificate.id, timestamp);
            expireCertificatesInDb(domainName, timestamp);
        }
    }



    private KnownCertificate getKnownCertificateForDb(String domainName, Certificate[] certificates, boolean trustCertificate) {
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

            return new KnownCertificate(
                    domainName,
                    certificateBytes,
                    trustCertificate ? System.currentTimeMillis() : null,
                    domainCertificate.getNotAfter().getTime(),
                    issuersString,
                    sb.toString()
            );
        } catch (CertificateEncodingException | JsonProcessingException e) {
            Logger.e("Error storing SSL certificate in DB.");
        }
        return null;
    }

    private boolean shouldAllowConnection(@NonNull KnownCertificate currentCertificate, @Nullable KnownCertificate lastTrustedCertificate) {
        switch (SettingsActivity.getBlockUntrustedCertificate()) {
            case ALWAYS:
                return false;
            case NEVER:
                return true;
            case ISSUER_CHANGED:
            default:
                if (lastTrustedCertificate == null) {
                    return false;
                }
                return Objects.equals(currentCertificate.issuers, lastTrustedCertificate.issuers);
        }
    }

    private void notifyUser(long untrustedCertificateId, @Nullable Long lastTrustedCertificateId, boolean connectionWasBlocked) {
        Long lastNotificationTimestamp = lastUntrustedCertificateNotification.get(untrustedCertificateId);
        if (lastNotificationTimestamp == null || lastNotificationTimestamp < System.currentTimeMillis() - NOTIFICATION_MIN_INTERVAL_MILLIS) {
            lastUntrustedCertificateNotification.put(untrustedCertificateId, System.currentTimeMillis());

            App.openAppDialogCertificateChanged(untrustedCertificateId, lastTrustedCertificateId);
        }
        if (connectionWasBlocked) {
            AndroidNotificationManager.displayConnectionBlockedNotification(untrustedCertificateId, lastTrustedCertificateId);
        }
    }
}
