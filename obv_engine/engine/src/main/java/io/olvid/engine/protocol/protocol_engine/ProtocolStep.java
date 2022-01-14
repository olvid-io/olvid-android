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

package io.olvid.engine.protocol.protocol_engine;


import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.containers.SendChannelInfo;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;
import io.olvid.engine.protocol.datatypes.ProtocolManagerSession;

public abstract class ProtocolStep extends Operation {
    protected final ConcreteProtocol protocol;
    private ConcreteProtocolState endState;

    public ConcreteProtocolState getEndState() {
        return endState;
    }

    public ProtocolStep(Identity expectedToIdentity, ReceptionChannelInfo expectedReceptionChannelInfo, ConcreteProtocolMessage receivedMessage, ConcreteProtocol protocol) throws Exception {
        if (!receivedMessage.getToIdentity().equals(expectedToIdentity)) {
            Logger.d("Protocol expected toIdentity mismatch.");
            throw new Exception();
        }
        if (expectedReceptionChannelInfo.getChannelType() == ReceptionChannelInfo.ANY_OBLIVIOUS_CHANNEL_WITH_OWNED_DEVICE_TYPE) {
            if ((receivedMessage.getReceptionChannelInfo().getChannelType() != ReceptionChannelInfo.OBLIVIOUS_CHANNEL_TYPE) ||
                    (receivedMessage.getReceptionChannelInfo().getRemoteIdentity() != expectedToIdentity)) {
                Logger.d("Protocol expected ReceptionChannelInfo mismatch.");
                throw new Exception();
            }
        } else if (expectedReceptionChannelInfo.getChannelType() == ReceptionChannelInfo.ANY_OBLIVIOUS_CHANNEL_TYPE) {
            if (receivedMessage.getReceptionChannelInfo().getChannelType() != ReceptionChannelInfo.OBLIVIOUS_CHANNEL_TYPE) {
                Logger.d("Protocol expected ReceptionChannelInfo mismatch.");
                throw new Exception();
            }
        } else if (!receivedMessage.getReceptionChannelInfo().equals(expectedReceptionChannelInfo)) {
                Logger.d("Protocol expected ReceptionChannelInfo mismatch.");
                throw new Exception();
        }
        this.protocol = protocol;
    }

    @Override
    public void doCancel() {
        // Nothing special to do
    }

    @Override
    public void doExecute() {
        try {
            endState = executeStep();
            setFinished();
        } catch (Exception e) {
            cancel(null);
            e.printStackTrace();
        }
    }

    public Identity getOwnedIdentity() {
        return protocol.getOwnedIdentity();
    }

    public ProtocolManagerSession getProtocolManagerSession() {
        return protocol.getProtocolManagerSession();
    }

    public PRNGService getPrng() {
        return protocol.getPrng();
    }

    public UID getProtocolInstanceUid() {
        return protocol.getProtocolInstanceUid();
    }

    public int getProtocolId() {
        return protocol.getProtocolId();
    }

    public abstract ConcreteProtocolState executeStep() throws Exception;

    protected CoreProtocolMessage buildCoreProtocolMessage(SendChannelInfo sendChannelInfo) {
        return new CoreProtocolMessage(sendChannelInfo, getProtocolId(), getProtocolInstanceUid(), false);
    }

}
