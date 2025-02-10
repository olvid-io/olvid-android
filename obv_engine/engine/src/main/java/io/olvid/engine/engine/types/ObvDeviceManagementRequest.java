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

import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class ObvDeviceManagementRequest {
    public static final int ACTION_SET_NICKNAME = 0;
    public static final int ACTION_DEACTIVATE_DEVICE = 1;
    public static final int ACTION_SET_UNEXPIRING_DEVICE = 2;

    public final int action;
    public final byte[] bytesDeviceUid;
    public final String nickname;

    public static ObvDeviceManagementRequest createSetNicknameRequest(byte[] bytesDeviceUid, String nickname) {
        if (nickname == null) {
            nickname = "";
        }
        return new ObvDeviceManagementRequest(ACTION_SET_NICKNAME, bytesDeviceUid, nickname);
    }

    public static ObvDeviceManagementRequest createDeactivateDeviceRequest(byte[] bytesDeviceUid) {
        return new ObvDeviceManagementRequest(ACTION_DEACTIVATE_DEVICE, bytesDeviceUid, null);
    }

    public static ObvDeviceManagementRequest createSetUnexpiringDeviceRequest(byte[] bytesDeviceUid) {
        return new ObvDeviceManagementRequest(ACTION_SET_UNEXPIRING_DEVICE, bytesDeviceUid, null);
    }


    private ObvDeviceManagementRequest(int action, byte[] bytesDeviceUid, String nickname) {
        this.action = action;
        this.bytesDeviceUid = bytesDeviceUid;
        this.nickname = nickname;
    }

    public UID getDeviceUid() {
        if (bytesDeviceUid == null) {
            return null;
        }
        return new UID(bytesDeviceUid);
    }


    public Encoded encode() {
        switch (action) {
            case ACTION_SET_NICKNAME: {
                return Encoded.of(new Encoded[]{
                        Encoded.of(ACTION_SET_NICKNAME),
                        Encoded.of(bytesDeviceUid),
                        Encoded.of(nickname),
                });
            }
            case ACTION_DEACTIVATE_DEVICE: {
                return Encoded.of(new Encoded[]{
                        Encoded.of(ACTION_DEACTIVATE_DEVICE),
                        Encoded.of(bytesDeviceUid),
                });
            }
            case ACTION_SET_UNEXPIRING_DEVICE: {
                return Encoded.of(new Encoded[]{
                        Encoded.of(ACTION_SET_UNEXPIRING_DEVICE),
                        Encoded.of(bytesDeviceUid),
                });
            }
            default: {
                return null;
            }
        }
    }

    public static ObvDeviceManagementRequest of(Encoded encoded) throws DecodingException {
        Encoded[] encodeds = encoded.decodeList();
        int action = (int) encodeds[0].decodeLong();
        switch (action) {
            case ACTION_SET_NICKNAME: {
                if (encodeds.length != 3) {
                    throw new DecodingException();
                }
                UID deviceUid = encodeds[1].decodeUid();
                String nickname = encodeds[2].decodeString();
                return new ObvDeviceManagementRequest(ACTION_SET_NICKNAME, deviceUid.getBytes(), nickname);
            }
            case ACTION_DEACTIVATE_DEVICE: {
                if (encodeds.length != 2) {
                    throw new DecodingException();
                }
                UID deviceUid = encodeds[1].decodeUid();
                return new ObvDeviceManagementRequest(ACTION_DEACTIVATE_DEVICE, deviceUid.getBytes(), null);
            }
            case ACTION_SET_UNEXPIRING_DEVICE: {
                if (encodeds.length != 2) {
                    throw new DecodingException();
                }
                UID deviceUid = encodeds[1].decodeUid();
                return new ObvDeviceManagementRequest(ACTION_SET_UNEXPIRING_DEVICE, deviceUid.getBytes(), null);
            }
            default: {
                throw new DecodingException();
            }
        }
    }
}
