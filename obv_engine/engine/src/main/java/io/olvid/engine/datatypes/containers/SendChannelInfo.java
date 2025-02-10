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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;

public class SendChannelInfo {
    public static final int LOCAL_TYPE = 0;
    public static final int OBLIVIOUS_CHANNEL_TYPE = 1;
    public static final int ASYMMETRIC_CHANNEL_TYPE = 2;
    public static final int ALL_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_ON_SAME_SERVER_TYPE = 3;
    public static final int ASYMMETRIC_BROADCAST_CHANNEL_TYPE = 4;
    public static final int USER_INTERFACE_TYPE = 5;
    public static final int SERVER_QUERY_TYPE = 6;
    public static final int ALL_OWNED_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_TYPE = 7;
    public static final int OBLIVIOUS_CHANNEL_OR_PRE_KEY_TYPE = 8;

    private final int channelType;
    private final Identity toIdentity; // only null if toIdentities is non null
    private final Identity fromIdentity; // never null
    private final UID[] remoteDeviceUids; // if toIdentities is non-null, this corresponds to 1 device per toIdentity. If an UID is null, send to all devices, otherwise send to the given deviceUid
    private final Boolean necessarilyConfirmed;
    private final DialogType dialogType;
    private final UUID dialogUuid;
    private final ServerQuery.Type serverQueryType;
    private final Identity[] toIdentities; // only null if toIdentity is non null



    private SendChannelInfo(int channelType, Identity toIdentity, Identity fromIdentity) {
        this(channelType, toIdentity, fromIdentity, null, null, null, null, null, null);
    }

    private SendChannelInfo(int channelType, Identity toIdentity, Identity fromIdentity, UID[] remoteDeviceUids, Boolean necessarilyConfirmed, DialogType dialogType, UUID dialogUuid, ServerQuery.Type serverQueryType, Identity[] toIdentities) {
        this.channelType = channelType;
        this.toIdentity = toIdentity;
        this.fromIdentity = fromIdentity;
        this.remoteDeviceUids = remoteDeviceUids;
        this.necessarilyConfirmed = necessarilyConfirmed;
        this.dialogType = dialogType;
        this.dialogUuid = dialogUuid;
        this.serverQueryType = serverQueryType;
        this.toIdentities = toIdentities;
    }

    public static SendChannelInfo createLocalChannelInfo(Identity ownedIdentity) {
        if (ownedIdentity == null) {
            return null;
        }
        return new SendChannelInfo(LOCAL_TYPE, ownedIdentity, ownedIdentity);
    }

    public static SendChannelInfo createObliviousChannelInfo(Identity toIdentity, Identity fromIdentity, UID[] remoteDeviceUids, Boolean necessarilyConfirmed) {
        if (toIdentity == null || fromIdentity == null || remoteDeviceUids == null || necessarilyConfirmed == null) {
            return null;
        }
        return new SendChannelInfo(OBLIVIOUS_CHANNEL_TYPE, toIdentity, fromIdentity, remoteDeviceUids, necessarilyConfirmed, null, null, null, null);
    }

    public static SendChannelInfo createObliviousChannelOrPreKeyInfo(Identity toIdentity, Identity fromIdentity, UID[] remoteDeviceUids, Boolean necessarilyConfirmed) {
        if (toIdentity == null || fromIdentity == null || remoteDeviceUids == null || necessarilyConfirmed == null) {
            return null;
        }
        return new SendChannelInfo(OBLIVIOUS_CHANNEL_OR_PRE_KEY_TYPE, toIdentity, fromIdentity, remoteDeviceUids, necessarilyConfirmed, null, null, null, null);
    }

    public static SendChannelInfo createAsymmetricChannelInfo(Identity toIdentity, Identity fromIdentity, UID[] remoteDeviceUids) {
        if (toIdentity == null || fromIdentity == null || remoteDeviceUids == null) {
            return null;
        }
        return new SendChannelInfo(ASYMMETRIC_CHANNEL_TYPE, toIdentity, fromIdentity, remoteDeviceUids, null, null, null, null, null);
    }

    public static SendChannelInfo createAllConfirmedObliviousChannelsOrPreKeysInfo(Identity toIdentity, Identity fromIdentity) {
        if (toIdentity == null || fromIdentity == null) {
            return null;
        }
        return new SendChannelInfo(ALL_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_ON_SAME_SERVER_TYPE, null, fromIdentity, new UID[1], null, null, null, null, new Identity[]{toIdentity});
    }


    public static SendChannelInfo[] createAllConfirmedObliviousChannelsOrPreKeysInfoForMultipleIdentities(Identity[] toIdentities, Identity fromIdentity) {
        return createAllConfirmedObliviousChannelsOrPreKeysInfoForMultipleIdentities(toIdentities, new UID[toIdentities.length], fromIdentity);
    }

    public static SendChannelInfo[] createAllConfirmedObliviousChannelsOrPreKeysInfoForMultipleIdentities(Identity[] toIdentities, UID[] toDeviceUids, Identity fromIdentity) {
        if (toIdentities == null || toIdentities.length == 0 || fromIdentity == null) {
            return null;
        }
        HashMap<String, List<Identity>> map = new HashMap<>();
        HashMap<Identity, UID> deviceUidsMap = new HashMap<>();
        for (int i = 0; i < toIdentities.length; i++) {
            String server = toIdentities[i].getServer();
            List<Identity> serverIdentityList = map.get(server);
            if (serverIdentityList == null) {
                serverIdentityList = new ArrayList<>();
                map.put(server, serverIdentityList);
            }
            serverIdentityList.add(toIdentities[i]);
            if (toDeviceUids[i] != null) {
                deviceUidsMap.put(toIdentities[i], toDeviceUids[i]);
            }
        }
        SendChannelInfo[] sendChannelInfos = new SendChannelInfo[map.size()];
        int i=0;
        for (String server: map.keySet()) {
            List<Identity> serverIdentityList = map.get(server);
            if (serverIdentityList != null && !serverIdentityList.isEmpty()) {
                Identity[] identities = new Identity[serverIdentityList.size()];
                UID[] deviceUids = new UID[serverIdentityList.size()];
                int j = 0;
                for (Identity identity : serverIdentityList) {
                    identities[j] = identity;
                    deviceUids[j] = deviceUidsMap.get(identity);
                    j++;
                }

                sendChannelInfos[i] = new SendChannelInfo(ALL_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_ON_SAME_SERVER_TYPE, null, fromIdentity, deviceUids, null, null, null, null, identities);
            } else {
                sendChannelInfos[i] = null;
            }
            i++;
        }
        return sendChannelInfos;
    }



    public static SendChannelInfo createAllOwnedConfirmedObliviousChannelsOrPreKeysInfo(Identity ownedIdentity) {
        if (ownedIdentity == null) {
            return null;
        }
        return new SendChannelInfo(ALL_OWNED_CONFIRMED_OBLIVIOUS_CHANNELS_OR_PRE_KEY_TYPE, ownedIdentity, ownedIdentity);
    }

    public static SendChannelInfo createAsymmetricBroadcastChannelInfo(Identity toIdentity, Identity fromIdentity) {
        if (toIdentity == null || fromIdentity == null) {
            return null;
        }
        return new SendChannelInfo(ASYMMETRIC_BROADCAST_CHANNEL_TYPE, toIdentity, fromIdentity);
    }

    public static SendChannelInfo createUserInterfaceChannelInfo(Identity ownedIdentity, DialogType dialogType, UUID dialogUuid) {
        if (ownedIdentity == null || dialogType == null || dialogUuid == null) {
            return null;
        }
        return new SendChannelInfo(USER_INTERFACE_TYPE, ownedIdentity, ownedIdentity, null, null, dialogType, dialogUuid, null, null);
    }

    public static SendChannelInfo createServerQueryChannelInfo(Identity ownedIdentity, ServerQuery.Type serverQueryType) {
        if (ownedIdentity == null || serverQueryType == null) {
            return null;
        }
        return new SendChannelInfo(SERVER_QUERY_TYPE, ownedIdentity, ownedIdentity, null, null, null, null, serverQueryType, null);
    }

    public int getChannelType() {
        return channelType;
    }

    public Identity getToIdentity() {
        return toIdentity;
    }

    public UID[] getRemoteDeviceUids() {
        return remoteDeviceUids;
    }

    public Identity getFromIdentity() {
        return fromIdentity;
    }

    public Boolean getNecessarilyConfirmed() {
        return necessarilyConfirmed;
    }

    public DialogType getDialogType() {
        return dialogType;
    }

    public UUID getDialogUuid() {
        return dialogUuid;
    }

    public ServerQuery.Type getServerQueryType() {
        return serverQueryType;
    }

    public Identity[] getToIdentities() {
        return toIdentities;
    }
}
