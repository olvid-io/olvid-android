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
import io.olvid.engine.encoder.Encoded;

public class ProtocolReceivedServerResponse {
    private final Encoded serverResponse;
    private final Identity toIdentity;
    private final Encoded encodedElements;
    private final ReceptionChannelInfo receptionChannelInfo;

    public ProtocolReceivedServerResponse(Encoded serverResponse, Identity toIdentity, Encoded encodedElements, ReceptionChannelInfo receptionChannelInfo) {
        this.serverResponse = serverResponse;
        this.toIdentity = toIdentity;
        this.encodedElements = encodedElements;
        this.receptionChannelInfo = receptionChannelInfo;
    }

    public Encoded getServerResponse() {
        return serverResponse;
    }

    public Identity getToIdentity() {
        return toIdentity;
    }

    public Encoded getEncodedElements() {
        return encodedElements;
    }

    public ReceptionChannelInfo getReceptionChannelInfo() {
        return receptionChannelInfo;
    }
}
