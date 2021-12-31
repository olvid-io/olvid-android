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

package io.olvid.messenger.customClasses;


import android.os.Build;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.Certificate;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.databases.tasks.VerifySSLConnectionCertificatesTask;

public class CustomSSLSocketFactory extends SSLSocketFactory implements HandshakeCompletedListener {
    private final SSLSocketFactory sslSocketFactory;

    public CustomSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
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
            Logger.d("Connected to " + event.getSocket().getInetAddress().getHostName() + " using cipher suite " + event.getCipherSuite());
            final String hostname = event.getSocket().getInetAddress().getHostName();
            final Certificate[] certificates = event.getPeerCertificates();

            App.runThread(new VerifySSLConnectionCertificatesTask(hostname, certificates));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
