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

package io.olvid.engine.protocol.datatypes;


import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.protocol.databases.ReceivedMessage;

public class CoreProtocolMessage {
    private final SendChannelInfo sendChannelInfo;
    private final ReceptionChannelInfo receptionChannelInfo;
    private final Identity toIdentity;
    private final int protocolId;
    private final UID protocolInstanceUid;
    private final boolean partOfFullRatchetProtocolOfTheSendSeed;
    private final boolean hasUserContent;
    private final long serverTimestamp;

    public CoreProtocolMessage(ReceivedMessage message) {
        this.sendChannelInfo = null;
        this.receptionChannelInfo = message.getReceptionChannelInfo();
        this.toIdentity = message.getToIdentity();
        this.protocolId = message.getProtocolId();
        this.protocolInstanceUid = message.getProtocolInstanceUid();
        this.partOfFullRatchetProtocolOfTheSendSeed = false;
        this.hasUserContent = false;
        this.serverTimestamp = message.getServerTimestamp();
    }

    public CoreProtocolMessage(SendChannelInfo sendChannelInfo, int protocolId, UID protocolInstanceUid) {
        this.sendChannelInfo = sendChannelInfo;
        this.receptionChannelInfo = null;
        this.toIdentity = null;
        this.protocolId = protocolId;
        this.protocolInstanceUid = protocolInstanceUid;
        this.partOfFullRatchetProtocolOfTheSendSeed = false;
        this.hasUserContent = false;
        this.serverTimestamp = System.currentTimeMillis();
    }

    public CoreProtocolMessage(SendChannelInfo sendChannelInfo, int protocolId, UID protocolInstanceUid, boolean partOfFullRatchetProtocolOfTheSendSeed, boolean hasUserContent) {
        this.sendChannelInfo = sendChannelInfo;
        this.receptionChannelInfo = null;
        this.toIdentity = null;
        this.protocolId = protocolId;
        this.protocolInstanceUid = protocolInstanceUid;
        this.partOfFullRatchetProtocolOfTheSendSeed = partOfFullRatchetProtocolOfTheSendSeed;
        this.hasUserContent = hasUserContent;
        this.serverTimestamp = System.currentTimeMillis();
    }

    public SendChannelInfo getSendChannelInfo() {
        return sendChannelInfo;
    }

    public ReceptionChannelInfo getReceptionChannelInfo() {
        return receptionChannelInfo;
    }

    public Identity getToIdentity() {
        return toIdentity;
    }

    public int getProtocolId() {
        return protocolId;
    }

    public UID getProtocolInstanceUid() {
        return protocolInstanceUid;
    }

    public boolean isPartOfFullRatchetProtocolOfTheSendSeed() {
        return partOfFullRatchetProtocolOfTheSendSeed;
    }

    public boolean hasUserContent() {
        return hasUserContent;
    }

    public long getServerTimestamp() {
        return serverTimestamp;
    }
}
