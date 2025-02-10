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

package io.olvid.engine.protocol.protocol_engine;

import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.databases.ReceivedMessage;
import io.olvid.engine.protocol.datatypes.CoreProtocolMessage;

public abstract class EmptyProtocolMessage extends ConcreteProtocolMessage {
    protected EmptyProtocolMessage(CoreProtocolMessage coreProtocolMessage) {
        super(coreProtocolMessage);
    }

    @SuppressWarnings("unused")
    public EmptyProtocolMessage(ReceivedMessage receivedMessage) throws Exception {
        super(new CoreProtocolMessage(receivedMessage));
        if (receivedMessage.getInputs().length != 0) {
            throw new Exception();
        }
    }


    @Override
    public Encoded[] getInputs() {
        return new Encoded[0];
    }
}
