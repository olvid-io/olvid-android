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

package io.olvid.engine.datatypes.containers;


import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class ReceptionChannelInfo {
    public static final int LOCAL_TYPE = 0;
    public static final int OBLIVIOUS_CHANNEL_TYPE = 1;
    public static final int ASYMMETRIC_CHANNEL_TYPE = 2;
    public static final int PRE_KEY_CHANNEL_TYPE = 5;
    public static final int ANY_OBLIVIOUS_CHANNEL_OR_PRE_KEY_WITH_OWNED_DEVICE_TYPE = 3; // dummy type, never serialized
    public static final int ANY_OBLIVIOUS_OR_PRE_KEY_CHANNEL_TYPE = 4; // dummy type, never serialized
    public static final int ANY_OBLIVIOUS_CHANNEL_TYPE = 6; // dummy type, never serialized

    private final int channelType;
    private final UID remoteDeviceUid;
    private final Identity remoteIdentity;


    private ReceptionChannelInfo(int channelType) {
        this(channelType, null, null);
    }

    private ReceptionChannelInfo(int channelType, UID remoteDeviceUid, Identity remoteIdentity) {
        this.channelType = channelType;
        this.remoteDeviceUid = remoteDeviceUid;
        this.remoteIdentity = remoteIdentity;
    }

    public static ReceptionChannelInfo createLocalChannelInfo() {
        return new ReceptionChannelInfo(LOCAL_TYPE);
    }

    public static ReceptionChannelInfo createObliviousChannelInfo(UID remoteDeviceUid, Identity remoteIdentity) {
        return new ReceptionChannelInfo(OBLIVIOUS_CHANNEL_TYPE, remoteDeviceUid, remoteIdentity);
    }

    public static ReceptionChannelInfo createAsymmetricChannelInfo() {
        return new ReceptionChannelInfo(ASYMMETRIC_CHANNEL_TYPE);
    }

    public static ReceptionChannelInfo createPreKeyChannelInfo(UID remoteDeviceUid, Identity remoteIdentity) {
        return new ReceptionChannelInfo(PRE_KEY_CHANNEL_TYPE, remoteDeviceUid, remoteIdentity);
    }


    public static ReceptionChannelInfo createAnyObliviousChannelOrPreKeyWithOwnedDeviceInfo() {
        return new ReceptionChannelInfo(ANY_OBLIVIOUS_CHANNEL_OR_PRE_KEY_WITH_OWNED_DEVICE_TYPE);
    }

    public static ReceptionChannelInfo createAnyObliviousChannelOrPreKeyInfo() {
        return new ReceptionChannelInfo(ANY_OBLIVIOUS_OR_PRE_KEY_CHANNEL_TYPE);
    }

    public static ReceptionChannelInfo createAnyObliviousChannelInfo() {
        return new ReceptionChannelInfo(ANY_OBLIVIOUS_CHANNEL_TYPE);
    }

    public static ReceptionChannelInfo of(Encoded encodedChannelInfo) throws DecodingException {
        Encoded[] listOfEncoded = encodedChannelInfo.decodeList();
        if (listOfEncoded.length == 0) {
            throw new DecodingException();
        }
        int type = (int) listOfEncoded[0].decodeLong();
        switch (type) {
            case LOCAL_TYPE:
                if (listOfEncoded.length != 1) {
                    throw new DecodingException();
                }
                return createLocalChannelInfo();
            case OBLIVIOUS_CHANNEL_TYPE:
                if (listOfEncoded.length != 4 && listOfEncoded.length != 3) { // 4 is here for legacy compatibility
                    throw new DecodingException();
                }
                return createObliviousChannelInfo(listOfEncoded[1].decodeUid(), listOfEncoded[2].decodeIdentity());
            case ASYMMETRIC_CHANNEL_TYPE:
                if (listOfEncoded.length != 1) {
                    throw new DecodingException();
                }
                return createAsymmetricChannelInfo();
            case PRE_KEY_CHANNEL_TYPE:
                if (listOfEncoded.length != 3) {
                    throw new DecodingException();
                }
                return createPreKeyChannelInfo(listOfEncoded[1].decodeUid(), listOfEncoded[2].decodeIdentity());
            default:
                throw new DecodingException("Unknown reception channel type " + type);
        }
    }

    public Encoded encode() {
        if (channelType == OBLIVIOUS_CHANNEL_TYPE || channelType == PRE_KEY_CHANNEL_TYPE) {
            return Encoded.of(new Encoded[]{
                    Encoded.of(channelType),
                    Encoded.of(remoteDeviceUid),
                    Encoded.of(remoteIdentity),
            });
        }
        return Encoded.of(new Encoded[]{Encoded.of(channelType)});
    }

    public int getChannelType() {
        return channelType;
    }

    public UID getRemoteDeviceUid() {
        return remoteDeviceUid;
    }

    public Identity getRemoteIdentity() {
        return remoteIdentity;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ReceptionChannelInfo)) {
            return false;
        }
        //noinspection PatternVariableCanBeUsed
        ReceptionChannelInfo castedOther = (ReceptionChannelInfo) other;
        if (castedOther.getChannelType() != getChannelType()) {
            return false;
        }
        switch (getChannelType()) {
            case OBLIVIOUS_CHANNEL_TYPE:
            case PRE_KEY_CHANNEL_TYPE:
                return castedOther.remoteDeviceUid.equals(remoteDeviceUid) && castedOther.remoteIdentity.equals(remoteIdentity);
            default:
                return true;
        }
    }

}
