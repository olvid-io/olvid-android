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

public class InitialProtocolState extends ConcreteProtocolState {
    public InitialProtocolState(Encoded encodedState) throws Exception {
        super(ConcreteProtocol.INITIAL_STATE_ID);
        Encoded[] list = encodedState.decodeList();
        if (list.length != 0) {
            throw new Exception();
        }
    }

    public InitialProtocolState() {
        super(ConcreteProtocol.INITIAL_STATE_ID);
    }

    @Override
    public Encoded encode() {
        return Encoded.of(new Encoded[0]);
    }
}
