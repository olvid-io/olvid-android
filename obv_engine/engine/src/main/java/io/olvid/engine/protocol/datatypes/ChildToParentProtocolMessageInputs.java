/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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


import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.protocol.protocol_engine.ConcreteProtocolState;

public class ChildToParentProtocolMessageInputs {
    private final UID childProtocolInstanceUid;
    private final int childProtocolReachedStateId;
    private final Encoded childProtocolEncodedState;

    public ChildToParentProtocolMessageInputs(UID childProtocolInstanceUid, ConcreteProtocolState childProtocolState) {
        this.childProtocolInstanceUid = childProtocolInstanceUid;
        this.childProtocolReachedStateId = childProtocolState.id;
        this.childProtocolEncodedState = childProtocolState.encode();
    }

    public UID getChildProtocolInstanceUid() {
        return childProtocolInstanceUid;
    }

    public int getChildProtocolReachedStateId() {
        return childProtocolReachedStateId;
    }

    public Encoded getChildProtocolEncodedState() {
        return childProtocolEncodedState;
    }

    public ChildToParentProtocolMessageInputs(Encoded[] inputs) throws Exception {
        if (inputs.length != 3) {
            throw new Exception();
        }
        this.childProtocolInstanceUid = inputs[0].decodeUid();
        this.childProtocolReachedStateId = (int) inputs[1].decodeLong();
        this.childProtocolEncodedState = inputs[2];
    }

    public Encoded[] toEncodedInputs() {
        return new Encoded[]{Encoded.of(childProtocolInstanceUid), Encoded.of(childProtocolReachedStateId), childProtocolEncodedState};
    }
}
