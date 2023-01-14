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

package io.olvid.engine.datatypes.containers;


import java.util.UUID;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.encoder.Encoded;

public class ProtocolReceivedDialogResponse {
    private final UUID userDialogUuid;
    private final Encoded userDialogResponse;
    private final Identity toIdentity;
    private final Encoded encodedElements;
    private final ReceptionChannelInfo receptionChannelInfo;

    public ProtocolReceivedDialogResponse(UUID userDialogUuid, Encoded userDialogResponse, Identity toIdentity, Encoded encodedElements, ReceptionChannelInfo receptionChannelInfo) {
        this.userDialogUuid = userDialogUuid;
        this.userDialogResponse = userDialogResponse;
        this.toIdentity = toIdentity;
        this.encodedElements = encodedElements;
        this.receptionChannelInfo = receptionChannelInfo;
    }

    public Encoded getUserDialogResponse() {
        return userDialogResponse;
    }

    public UUID getUserDialogUuid() {
        return userDialogUuid;
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
