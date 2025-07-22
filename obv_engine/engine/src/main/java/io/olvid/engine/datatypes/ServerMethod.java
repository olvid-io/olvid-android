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

package io.olvid.engine.datatypes;


import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public abstract class ServerMethod {
    public static final byte OK = 0x00;
//    public static final byte PROOF_OF_WORK_REQUIRED = 0x01;
//    public static final byte QUOTA_EXCEEDED = 0x02;
//    public static final byte EXCEEDING_EXPECTED_BYTE_LENGTH = 0x03;
    public static final byte INVALID_SESSION = 0x04;
//    public static final byte NOT_YET_AVAILABLE = 0x05;
//    public static final byte MESSAGE_NOT_COMPLETE_YET = 0x06;
//    public static final byte UNKNOWN_API_KEY = 0x07;
//    public static final byte API_KEY_LICENSES_EXHAUSTED = 0x08;
    public static final byte DELETED_FROM_SERVER = 0x09;
    public static final byte ANOTHER_DEVICE_IS_ALREADY_REGISTERED = 0x0a;
    public static final byte DEVICE_IS_NOT_REGISTERED = 0x0b;
    public static final byte INVALID_NONCE = 0x0c;
//    public static final byte UPLOAD_CANCELLED = 0x0d;
    public static final byte PERMISSION_DENIED = 0x0e;
    public static final byte FREE_TRIAL_ALREADY_USED = 0x0f;
//    public static final byte STATUS_RECEIPT_IS_EXPIRED = 0x10; // used on iOS only
    public static final byte EXTENDED_PAYLOAD_UNAVAILABLE = 0x11;
    public static final byte GROUP_UID_ALREADY_USED = 0x12;
    public static final byte GROUP_IS_LOCKED = 0x13;
    public static final byte INVALID_SIGNATURE = 0x14;
    public static final byte GROUP_NOT_LOCKED = 0x15;
    public static final byte INVALID_API_KEY = 0x16;
    public static final byte LISTING_TRUNCATED = 0x17;
    public static final byte PAYLOAD_TOO_LARGE = (byte) 0x18;
    public static final byte BACKUP_UID_ALREADY_USED = (byte) 0x19;
    public static final byte BACKUP_VERSION_TOO_SMALL = (byte) 0x1a;
    public static final byte UNKNOWN_BACKUP_UID = (byte) 0x1b;
    public static final byte UNKNOWN_BACKUP_THREAD_ID = (byte) 0x1c;
    public static final byte UNKNOWN_BACKUP_VERSION = (byte) 0x1d;


    public static final byte PARSING_ERROR = (byte) 0xfe;
    public static final byte GENERAL_ERROR = (byte) 0xff;

    public static final byte MALFORMED_URL = (byte) 0x80;
    public static final byte SERVER_CONNECTION_ERROR = (byte) 0x81;
    public static final byte MALFORMED_SERVER_RESPONSE = (byte) 0x82;
    public static final byte OK_WITH_MALFORMED_SERVER_RESPONSE = (byte) 0x83;
    public static final byte IDENTITY_IS_NOT_ACTIVE = (byte) 0x8e;


    protected abstract String getServer();
    protected abstract String getServerMethod();
    protected abstract byte[] getDataToSend();
    protected abstract void parseReceivedData(Encoded[] receivedData);
    protected abstract boolean isActiveIdentityRequired();

    protected byte returnStatus;

    private SSLSocketFactory sslSocketFactory = null;

    public void setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    public byte execute(boolean ownedIdentityIsActive) {
        if (isActiveIdentityRequired() && !ownedIdentityIsActive) {
            returnStatus = IDENTITY_IS_NOT_ACTIVE;
            return returnStatus;
        }
        String server = getServer();
        String[] parts = server.split("://");
        String proto = "https";
        if (parts.length == 2) {
            proto = parts[0];
            server = parts[1];
        } else {
            server = parts[0];
        }
        String pathPrefix = null;
        int pathPos = server.indexOf('/');
        if (pathPos != -1) {
            pathPrefix = server.substring(pathPos);
            server = server.substring(0, pathPos);

            // remove any trailing / from pathPrefix
            while (pathPrefix.endsWith("/")) {
                pathPrefix = pathPrefix.substring(0, pathPrefix.length() - 1);
            }
        }
        int port = -1;
        int portPos = server.indexOf(':');
        if (portPos != -1) {
            port = Integer.parseInt(server.substring(portPos+1));
            server = server.substring(0, portPos);
        }
        String path = getServerMethod();
        if (pathPrefix != null && !pathPrefix.isEmpty()) {
            path = pathPrefix + path;
        }
        byte[] dataToSend = getDataToSend();

        try {
            URL requestUrl = new URL(proto, server, port, path);
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
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(dataToSend.length);
                connection.setRequestProperty("Cache-Control", "no-store");
                connection.setRequestProperty("Content-Type", "application/bytes");
                connection.setRequestProperty("Olvid-API-Version", "" + Constants.SERVER_API_VERSION);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(dataToSend);
                    int serverResponse = connection.getResponseCode();

                    switch (serverResponse) {
                        case 200: {
                            try (InputStream is = connection.getInputStream();
                                 BufferedInputStream bis = new BufferedInputStream(is);
                                 ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                                int numberOfBytesRead;
                                byte[] buffer = new byte[8192];

                                while ((numberOfBytesRead = bis.read(buffer)) != -1) {
                                    byteArrayOutputStream.write(buffer, 0, numberOfBytesRead);
                                }
                                byteArrayOutputStream.flush();

                                byte[] responseData = byteArrayOutputStream.toByteArray();

                                Encoded encodedResponse = new Encoded(responseData);

                                Encoded[] responseList = encodedResponse.decodeList();
                                if (responseList.length == 0) {
                                    throw new DecodingException();
                                }
                                byte[] returnStatusBytes = responseList[0].decodeBytes();
                                if (returnStatusBytes.length != 1) {
                                    throw new DecodingException();
                                }

                                // Parse the received data and return the server status code
                                returnStatus = returnStatusBytes[0];

                                parseReceivedData(Arrays.copyOfRange(responseList, 1, responseList.length));
                            }
                            break;
                        }
                        case 413: { // payload too large
                            returnStatus = PAYLOAD_TOO_LARGE;
                            break;
                        }
                        default: { // unknown server response
                            Logger.w("Unexpected HTTP response code: " + serverResponse + " for query " + path);
                            returnStatus = SERVER_CONNECTION_ERROR;
                        }
                    }
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
        } catch (DecodingException e) {
            Logger.x(e);
            returnStatus = MALFORMED_SERVER_RESPONSE;
        }
        return returnStatus;
    }
}

