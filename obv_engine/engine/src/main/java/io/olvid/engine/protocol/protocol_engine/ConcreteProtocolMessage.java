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

package io.olvid.engine.protocol.protocol_engine;


import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ChannelDialogMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelProtocolMessageToSend;
import io.olvid.engine.datatypes.containers.ChannelServerQueryMessageToSend;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.GenericProtocolMessageToSend;

public abstract class ConcreteProtocolMessage {
    private final CoreProtocolMessage coreProtocolMessage;

    protected ConcreteProtocolMessage(CoreProtocolMessage coreProtocolMessage) {
        this.coreProtocolMessage = coreProtocolMessage;
    }

    public abstract int getProtocolMessageId();
    public abstract Encoded[] getInputs();

    public int getProtocolId() {
        return coreProtocolMessage.getProtocolId();
    }

    public ReceptionChannelInfo getReceptionChannelInfo() {
        return coreProtocolMessage.getReceptionChannelInfo();
    }

    public Identity getToIdentity() {
        return coreProtocolMessage.getToIdentity();
    }

    public long getServerTimestamp() {
        return coreProtocolMessage.getServerTimestamp();
    }

    public UID getProtocolInstanceUid() {
        return coreProtocolMessage.getProtocolInstanceUid();
    }

    public GenericProtocolMessageToSend generateGenericProtocolMessageToSend() {
        if (coreProtocolMessage.getSendChannelInfo() == null) {
            return null;
        }
        return new GenericProtocolMessageToSend(coreProtocolMessage.getSendChannelInfo(),
                coreProtocolMessage.getProtocolId(),
                coreProtocolMessage.getProtocolInstanceUid(),
                getProtocolMessageId(),
                getInputs(),
                coreProtocolMessage.isPartOfFullRatchetProtocolOfTheSendSeed(),
                coreProtocolMessage.hasUserContent());
    }

    public ChannelProtocolMessageToSend generateChannelProtocolMessageToSend() {
        return generateGenericProtocolMessageToSend().generateChannelProtocolMessageToSend();
    }

    public ChannelDialogMessageToSend generateChannelDialogMessageToSend() {
        return generateGenericProtocolMessageToSend().generateChannelDialogMessageToSend();
    }

    public ChannelServerQueryMessageToSend generateChannelServerQueryMessageToSend() {
        return generateGenericProtocolMessageToSend().generateChannelServerQueryMessageToSend();
    }

}
