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

package io.olvid.engine.engine.types;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import io.olvid.engine.crypto.PublicKeyEncryption;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.PrivateKey;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.identities.ObvOwnedDevice;

public class ObvDeviceList {
    public final Boolean multiDevice; // null if the server is not able to determine if the user has multi-device permission
    public final HashMap<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> deviceUidsAndServerInfo;

    public ObvDeviceList(Boolean multiDevice, HashMap<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> deviceUidsAndServerInfo) {
        this.deviceUidsAndServerInfo = deviceUidsAndServerInfo;
        this.multiDevice = multiDevice;
    }

    public static ObvDeviceList of(EncryptedBytes encryptedPayload, EncryptionPrivateKey privateKey) throws Exception {
        PublicKeyEncryption publicKeyEncryption = Suite.getPublicKeyEncryption(privateKey);

        // decrypt the received device list
        byte[] decryptedPayload = publicKeyEncryption.decrypt(privateKey, encryptedPayload);

        HashMap<DictionaryKey, Encoded> map = new Encoded(decryptedPayload).decodeDictionary();

        // check for multi-device (is null if server could not determine if multi-device is available)
        Encoded encodedMulti = map.get(new DictionaryKey("multi"));
        Boolean multiDevice;
        if (encodedMulti != null) {
            multiDevice = encodedMulti.decodeBoolean();
        } else {
            multiDevice = null;
        }

        // now get the actual device list
        HashMap<ObvBytesKey, ObvOwnedDevice.ServerDeviceInfo> deviceUidsAndServerInfo = new HashMap<>();

        Encoded[] encodedDevices = map.get(new DictionaryKey("dev")).decodeList();
        for (Encoded encodedDevice : encodedDevices) {
            HashMap<DictionaryKey, Encoded> deviceMap = encodedDevice.decodeDictionary();
            UID deviceUid = deviceMap.get(new DictionaryKey("uid")).decodeUid();

            Encoded encodedExpiration = deviceMap.get(new DictionaryKey("exp"));
            Long expirationTimestamp = encodedExpiration == null ? null : encodedExpiration.decodeLong();

            Encoded encodedRegistration = deviceMap.get(new DictionaryKey("reg"));
            Long lastRegistrationTimestamp = encodedRegistration == null ? null : encodedRegistration.decodeLong();

            Encoded encodedName = deviceMap.get(new DictionaryKey("name"));
            String deviceName = null;
            if (encodedName != null) {
                try {
                    byte[] plaintext = publicKeyEncryption.decrypt(privateKey, encodedName.decodeEncryptedData());
                    byte[] bytesDeviceName = new Encoded(plaintext).decodeListWithPadding()[0].decodeBytes();
                    if (bytesDeviceName.length != 0) {
                        deviceName = new String(bytesDeviceName, StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) {}
            }

            deviceUidsAndServerInfo.put(new ObvBytesKey(deviceUid.getBytes()), new ObvOwnedDevice.ServerDeviceInfo(deviceName, expirationTimestamp, lastRegistrationTimestamp));
        }

        return new ObvDeviceList(multiDevice, deviceUidsAndServerInfo);
    }
}
