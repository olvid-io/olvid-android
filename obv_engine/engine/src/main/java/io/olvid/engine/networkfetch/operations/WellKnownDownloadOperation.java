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

package io.olvid.engine.networkfetch.operations;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.networkfetch.coordinators.WellKnownCoordinator;
import io.olvid.engine.networkfetch.databases.CachedWellKnown;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSession;
import io.olvid.engine.networkfetch.datatypes.FetchManagerSessionFactory;

public class WellKnownDownloadOperation extends Operation {
    public static final int RFC_NOT_FOUND = 1;
    public static final int RFC_MALFORMED_WELL_KNOWN = 2;

    public static final String WELL_KNOWN_PATH = "/.well-known/server-config.json";

    private final FetchManagerSessionFactory fetchManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final String server;
    private final ObjectMapper objectMapper;

    private boolean updated;
    private WellKnownCoordinator.JsonWellKnown downloadedWellKnown;

    public String getServer() {
        return server;
    }

    public boolean isUpdated() {
        return updated;
    }

    public WellKnownCoordinator.JsonWellKnown getDownloadedWellKnown() {
        return downloadedWellKnown;
    }



    public WellKnownDownloadOperation(FetchManagerSessionFactory fetchManagerSessionFactory, SSLSocketFactory sslSocketFactory, String server, ObjectMapper objectMapper, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(computeUniqueUid(server), onFinishCallback, onCancelCallback);
        this.fetchManagerSessionFactory = fetchManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.server = server;
        this.objectMapper = objectMapper;
    }

    public static UID computeUniqueUid(String server) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        return new UID(sha256.digest(server.getBytes(StandardCharsets.UTF_8)));
    }



    @Override
    public void doCancel() {
        // do nothing
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        try (FetchManagerSession fetchManagerSession = fetchManagerSessionFactory.getSession()) {
            try {
                CachedWellKnown cachedWellKnown = CachedWellKnown.get(fetchManagerSession, server);

                URL url = new URL(server + WELL_KNOWN_PATH);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                if (connection instanceof HttpsURLConnection && sslSocketFactory != null) {
                    ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
                }
                String userAgentProperty = System.getProperty("http.agent");
                if (userAgentProperty != null) {
                    connection.setRequestProperty("User-Agent", userAgentProperty);
                }
                try {
                    // Timeout after 5 seconds
                    connection.setConnectTimeout(5000);
                    connection.setRequestProperty("Cache-Control", "no-store");
                    connection.setRequestMethod("GET");
                    connection.setDoOutput(false);

                    int serverResponse = connection.getResponseCode();

                    if (serverResponse == 200) {
                        byte[] responseData;
                        try (InputStream is = connection.getInputStream();
                             BufferedInputStream bis = new BufferedInputStream(is);
                             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                            int numberOfBytesRead;
                            byte[] buffer = new byte[32_768];

                            while ((numberOfBytesRead = bis.read(buffer)) != -1) {
                                byteArrayOutputStream.write(buffer, 0, numberOfBytesRead);
                            }
                            byteArrayOutputStream.flush();

                            responseData = byteArrayOutputStream.toByteArray();
                        }

                        try {
                            downloadedWellKnown = objectMapper.readValue(responseData, WellKnownCoordinator.JsonWellKnown.class);
                        } catch (Exception e) {
                            e.printStackTrace();
                            cancel(RFC_MALFORMED_WELL_KNOWN);
                            return;
                        }

                        String newSerializedWellKnown = new String(responseData, StandardCharsets.UTF_8);

                        // check if something changed
                        if (cachedWellKnown == null) {
                            CachedWellKnown.create(fetchManagerSession, server, newSerializedWellKnown);
                            updated = true;
                        } else if (!Objects.equals(cachedWellKnown.getSerializedWellKnown(), newSerializedWellKnown)) {
                            cachedWellKnown.update(newSerializedWellKnown);
                            updated = true;
                        } else {
                            updated = false;
                        }
                    } else {
                        cancel(RFC_NOT_FOUND);
                        return;
                    }
                } finally {
                    connection.disconnect();
                }

                finished = true;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (finished) {
                    fetchManagerSession.session.commit();
                    setFinished();
                } else {
                    if (hasNoReasonForCancel()) {
                        cancel(null);
                    }
                    processCancel();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
