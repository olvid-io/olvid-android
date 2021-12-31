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

package io.olvid.messenger.webclient;

import android.util.Base64;

import com.google.protobuf.InvalidProtocolBufferException;

import java.net.URI;
import java.net.URISyntaxException;

import io.olvid.engine.Logger;
import io.olvid.messenger.webclient.protobuf.QrCodeInfoOuterClass;

public class QrCodeParser {
    static class QrCodeParserResult {
        private URI serverUri;
        private String correspondingIdentifier;
        private byte[] rawWebPublicKey;

        public URI getServerUri() { return this.serverUri; }
        public String getCorrespondingIdentifier() { return this.correspondingIdentifier; }
        public byte[] getRawWebPublicKey() { return this.rawWebPublicKey; }
    }

    static QrCodeParserResult parse(String base64QrCodeData) {
        String serverUrl;
        final byte[] bytesQrCodeData;
        final QrCodeInfoOuterClass.QrCodeInfo qrCodeInfo;
        QrCodeParserResult result = new QrCodeParserResult();

        try {
            bytesQrCodeData = Base64.decode(base64QrCodeData, Base64.URL_SAFE);
        }
        catch (IllegalArgumentException e) {
            Logger.e("Unable to decode qrcode data from base64");
            return (null);
        }
        try {
            qrCodeInfo = QrCodeInfoOuterClass.QrCodeInfo.parseFrom(bytesQrCodeData);
        } catch (InvalidProtocolBufferException e) {
            Logger.e("Unable to parse qrcode", e);
            return (null);
        }
        serverUrl = qrCodeInfo.getServerUrl();
        if (serverUrl == null || serverUrl.equals("")) {
            Logger.e("Invalid server URL");
            return (null);
        }
        try {
            result.serverUri = new URI(serverUrl);
        } catch (URISyntaxException e) {
            Logger.e("Unable to parse serverUrl");
            return (null);
        }
        result.correspondingIdentifier = qrCodeInfo.getIdentifier();
        result.rawWebPublicKey = qrCodeInfo.getPublicKey().toByteArray();
        if (result.correspondingIdentifier == null || result.correspondingIdentifier.length() == 0) {
            Logger.e("Invalid corresponding identifier");
            return (null);
        }
        if (result.rawWebPublicKey == null || result.rawWebPublicKey.length == 0) {
            Logger.e("Invalid corresponding web public key");
            return (null);
        }
        return (result);
    }
}
