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

package io.olvid.engine.engine.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum ObvCapability {
    WEBRTC_CONTINUOUS_ICE,
    GROUPS_V2;

    public static List<ObvCapability> currentCapabilities = new ArrayList<>(Arrays.asList(
            // add Engine capabilities here, once there are some
//            GROUPS_V2
    ));

    public static ObvCapability fromString(String stringRepresentation) {
        switch (stringRepresentation) {
            case "webrtc_continuous_ice":
                return WEBRTC_CONTINUOUS_ICE;
            case "groups_v2":
                return GROUPS_V2;
        }
        return null;
    }

    public String toString() {
        switch (this) {
            case WEBRTC_CONTINUOUS_ICE:
                return "webrtc_continuous_ice";
            case GROUPS_V2:
                return "groups_v2";
        }
        return null;
    }

    public static List<ObvCapability> getAll() {
        return Arrays.asList(ObvCapability.values());
    }

//    public static byte[] serializeDeviceCapabilities(List<ObvCapability> capabilities) {
//        if (capabilities == null) {
//            return null;
//        }
//        String[] capabilityStrings = new String[capabilities.size()];
//        int i = 0;
//        for (ObvCapability capability : capabilities) {
//            capabilityStrings[i] = capability.toString();
//            i++;
//        }
//        return serializeDeviceCapabilityStrings(capabilityStrings);
//    }

    public static String[] capabilityListToStringArray(List<ObvCapability> capabilities) {
        String[] capabilityStrings = new String[capabilities.size()];
        int i=0;
        for (ObvCapability capability : capabilities) {
            capabilityStrings[i] = capability.toString();
            i++;
        }
        return capabilityStrings;
    }

    public static byte[] serializeRawDeviceCapabilities(String[] rawDeviceCapabilities) {
        byte[] serializedDeviceCapabilities;
        if (rawDeviceCapabilities == null || rawDeviceCapabilities.length == 0) {
            serializedDeviceCapabilities = null;
        } else {
            // sort the array before serializing to ensure consistent serialization
            Arrays.sort(rawDeviceCapabilities);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                boolean first = true;
                for (String capabilityString : rawDeviceCapabilities) {
                    if (!first) {
                        baos.write(new byte[]{0});
                    }
                    first = false;
                    baos.write(capabilityString.getBytes(StandardCharsets.UTF_8));
                }
                serializedDeviceCapabilities = baos.toByteArray();
            } catch (IOException e) {
                serializedDeviceCapabilities = null;
            }
        }

        return serializedDeviceCapabilities;
    }

    public static List<ObvCapability> deserializeDeviceCapabilities(byte[] serializedDeviceCapabilities) {
        if (serializedDeviceCapabilities == null) {
            return new ArrayList<>(0);
        }

        List<ObvCapability> capabilities = new ArrayList<>();
        int startPos = 0;
        for (int i=0; i<serializedDeviceCapabilities.length; i++) {
            if (serializedDeviceCapabilities[i] == 0) {
                String capabilityString = new String(Arrays.copyOfRange(serializedDeviceCapabilities, startPos, i), StandardCharsets.UTF_8);
                startPos = i+1;

                ObvCapability capability = ObvCapability.fromString(capabilityString);
                if (capability != null) {
                    capabilities.add(capability);
                }
            }
        }
        if (startPos != serializedDeviceCapabilities.length) {
            String capabilityString = new String(Arrays.copyOfRange(serializedDeviceCapabilities, startPos, serializedDeviceCapabilities.length), StandardCharsets.UTF_8);
            ObvCapability capability = ObvCapability.fromString(capabilityString);
            if (capability != null) {
                capabilities.add(capability);
            }
        }
        return capabilities;
    }

    public static String[] deserializeRawDeviceCapabilities(byte[] serializedDeviceCapabilities) {
        if (serializedDeviceCapabilities == null) {
            return new String[0];
        }

        List<String> rawCapabilities = new ArrayList<>();
        int startPos = 0;
        for (int i=0; i<serializedDeviceCapabilities.length; i++) {
            if (serializedDeviceCapabilities[i] == 0) {
                rawCapabilities.add(new String(Arrays.copyOfRange(serializedDeviceCapabilities, startPos, i), StandardCharsets.UTF_8));
                startPos = i+1;
            }
        }
        if (startPos != serializedDeviceCapabilities.length) {
            rawCapabilities.add(new String(Arrays.copyOfRange(serializedDeviceCapabilities, startPos, serializedDeviceCapabilities.length), StandardCharsets.UTF_8));
        }
        return rawCapabilities.toArray(new String[0]);
    }

}
