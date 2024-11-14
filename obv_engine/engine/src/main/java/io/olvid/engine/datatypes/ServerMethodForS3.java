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

package io.olvid.engine.datatypes;


import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;

public abstract class ServerMethodForS3 {
    public static final byte OK = 0x00;
    public static final byte NOT_FOUND = 0x01;
    public static final byte INVALID_SIGNED_URL = 0x02;
    public static final byte GENERAL_ERROR = (byte) 0xff;

    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_GET = "GET";

    public static final byte MALFORMED_URL = (byte) 0x80;
    public static final byte SERVER_CONNECTION_ERROR = (byte) 0x81;
    public static final byte IDENTITY_IS_NOT_ACTIVE = (byte) 0x8e;

    private static final int BLOCK_SIZE = 32_768;

    private SSLSocketFactory sslSocketFactory = null;
    private ServerMethodForS3ProgressListener progressListener = null;
    private long progressListenerIntervalMs;

    protected abstract String getUrl();
    // only called for PUT methods
    protected abstract byte[] getDataToSend();
    // only called for GET methods
    protected abstract void handleReceivedData(byte[] receivedData);
    protected abstract String getMethod();
    protected abstract boolean isActiveIdentityRequired();

    protected byte returnStatus;

    public final void setProgressListener(long intervalMs, ServerMethodForS3ProgressListener progressListener) {
        this.progressListenerIntervalMs = intervalMs;
        this.progressListener = progressListener;
    }

    public void setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    public byte execute(boolean ownedIdentityIsActive) {
        if (isActiveIdentityRequired() && !ownedIdentityIsActive) {
            returnStatus = IDENTITY_IS_NOT_ACTIVE;
            return returnStatus;
        }
        String url = getUrl();
        byte[] dataToSend = getDataToSend();
        String method = getMethod();

        try {
            URL requestUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
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
                connection.setRequestMethod(method);
                byte[] responseData;
                if (METHOD_GET.equals(getMethod())) {
                    connection.setDoOutput(false);
                } else {
                    connection.setDoOutput(true);
                    connection.setFixedLengthStreamingMode(dataToSend.length);
                    try (OutputStream os = connection.getOutputStream()) {
                        if (progressListener != null) {
                            long nextReport = System.currentTimeMillis() + progressListenerIntervalMs;

                            for (int offset = 0; offset < dataToSend.length; offset += BLOCK_SIZE) {
                                if (System.currentTimeMillis() > nextReport) {
                                    progressListener.onProgress(offset);
                                    nextReport = System.currentTimeMillis() + progressListenerIntervalMs;
                                }
                                os.write(dataToSend, offset, Math.min(BLOCK_SIZE, dataToSend.length - offset));
                            }
                            progressListener.onProgress(dataToSend.length);
                        } else {
                            os.write(dataToSend);
                        }
                    }
                }

                int serverResponse = connection.getResponseCode();

                switch (serverResponse) {
                    case 200: {
                        returnStatus = OK;
                        if (METHOD_GET.equals(getMethod())) {
                            try (InputStream is = connection.getInputStream();
                                 BufferedInputStream bis = new BufferedInputStream(is);
                                 ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                                int numberOfBytesRead;
                                byte[] buffer = new byte[BLOCK_SIZE];

                                if (progressListener != null) {
                                    long nextReport = System.currentTimeMillis() + progressListenerIntervalMs;
                                    int progress = 0;
                                    while ((numberOfBytesRead = bis.read(buffer)) != -1) {
                                        byteArrayOutputStream.write(buffer, 0, numberOfBytesRead);
                                        progress += numberOfBytesRead;
                                        if (System.currentTimeMillis() > nextReport) {
                                            progressListener.onProgress(progress);
                                            nextReport = System.currentTimeMillis() + progressListenerIntervalMs;
                                        }
                                    }
                                    progressListener.onProgress(progress);
                                } else {
                                    while ((numberOfBytesRead = bis.read(buffer)) != -1) {
                                        byteArrayOutputStream.write(buffer, 0, numberOfBytesRead);
                                    }
                                }
                                byteArrayOutputStream.flush();

                                responseData = byteArrayOutputStream.toByteArray();
                                handleReceivedData(responseData);
                            }
                        }
                        break;
                    }
                    case 403: {
                        returnStatus = INVALID_SIGNED_URL;
                        break;
                    }
                    case 404: {
                        returnStatus = NOT_FOUND;
                        break;
                    }
                    default:
                        Logger.w("Unexpected HTTP response code: " + serverResponse + " for attachment download");
                        returnStatus = GENERAL_ERROR;
                }

            } finally {
                connection.disconnect();
            }
        } catch (MalformedURLException e) {
            Logger.x(e);
            returnStatus = MALFORMED_URL;
        } catch (IOException e) {
            Logger.x(e);
            returnStatus = SERVER_CONNECTION_ERROR;
        }
        return returnStatus;
    }

    public interface ServerMethodForS3ProgressListener {
        void onProgress(long byteCount);
    }
}
