/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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


import java.util.UUID;

import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.ProtocolReceivedDialogResponse;
import io.olvid.engine.datatypes.containers.ProtocolReceivedMessage;
import io.olvid.engine.datatypes.containers.ProtocolReceivedServerResponse;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class GenericReceivedProtocolMessage {
    private final Identity toIdentity;
    private final Encoded[] inputs;
    private final UUID userDialogUuid;
    private final Encoded encodedResponse;
    private final UID protocolInstanceUid;
    private final int protocolMessageId;
    private final int protocolId;
    private final ReceptionChannelInfo receptionChannelInfo;
    private final Identity associatedOwnedIdentity;
    private final long serverTimestamp;

    public Identity getToIdentity() {
        return toIdentity;
    }

    public Encoded[] getInputs() {
        return inputs;
    }

    public Encoded getEncodedResponse() {
        return encodedResponse;
    }

    public UID getProtocolInstanceUid() {
        return protocolInstanceUid;
    }

    public int getProtocolMessageId() {
        return protocolMessageId;
    }

    public int getProtocolId() {
        return protocolId;
    }

    public ReceptionChannelInfo getReceptionChannelInfo() {
        return receptionChannelInfo;
    }

    public UUID getUserDialogUuid() {
        return userDialogUuid;
    }

    public Identity getAssociatedOwnedIdentity() {
        return associatedOwnedIdentity;
    }

    public long getServerTimestamp() {
        return serverTimestamp;
    }

    private GenericReceivedProtocolMessage(Identity toIdentity, Encoded[] inputs, UUID userDialogUuid, Encoded encodedResponse, UID protocolInstanceUid,
                                           int protocolMessageId, int protocolId, ReceptionChannelInfo receptionChannelInfo, Identity associatedOwnedIdentity, long serverTimestamp) {
        this.toIdentity = toIdentity;
        this.inputs = inputs;
        this.userDialogUuid = userDialogUuid;
        this.encodedResponse = encodedResponse;
        this.protocolInstanceUid = protocolInstanceUid;
        this.protocolMessageId = protocolMessageId;
        this.protocolId = protocolId;
        this.receptionChannelInfo = receptionChannelInfo;
        this.associatedOwnedIdentity = associatedOwnedIdentity;
        this.serverTimestamp = serverTimestamp;
    }

    public static GenericReceivedProtocolMessage of(ProtocolReceivedMessage protocolReceivedMessage, Identity associatedOwnedIdentity) {
        try {
            Encoded[] listOfEncoded = protocolReceivedMessage.getEncodedElements().decodeList();
            if (listOfEncoded.length != 4) {
                return null;
            }
            int protocolId = (int) listOfEncoded[0].decodeLong();
            UID protocolInstanceUid = listOfEncoded[1].decodeUid();
            int protocolMessageId = (int) listOfEncoded[2].decodeLong();
            Encoded[] inputs = listOfEncoded[3].decodeList();
            return new GenericReceivedProtocolMessage(
                    protocolReceivedMessage.getOwnedIdentity(),
                    inputs,
                    null,
                    null,
                    protocolInstanceUid,
                    protocolMessageId,
                    protocolId,
                    protocolReceivedMessage.getReceptionChannelInfo(),
                    associatedOwnedIdentity,
                    protocolReceivedMessage.getServerTimestamp());
        } catch (DecodingException e) {
            return null;
        }
    }

    public static GenericReceivedProtocolMessage of(ProtocolReceivedDialogResponse protocolReceivedDialogResponse, Identity associatedOwnedIdentity) {
        try {
            Encoded[] listOfEncoded = protocolReceivedDialogResponse.getEncodedElements().decodeList();
            if (listOfEncoded.length != 4) {
                return null;
            }
            int protocolId = (int) listOfEncoded[0].decodeLong();
            UID protocolInstanceUid = listOfEncoded[1].decodeUid();
            int protocolMessageId = (int) listOfEncoded[2].decodeLong();
            Encoded[] inputs = listOfEncoded[3].decodeList();
            return new GenericReceivedProtocolMessage(
                    protocolReceivedDialogResponse.getToIdentity(),
                    inputs,
                    protocolReceivedDialogResponse.getUserDialogUuid(),
                    protocolReceivedDialogResponse.getUserDialogResponse(),
                    protocolInstanceUid,
                    protocolMessageId,
                    protocolId,
                    protocolReceivedDialogResponse.getReceptionChannelInfo(),
                    associatedOwnedIdentity,
                    0);
        } catch (DecodingException e) {
            return null;
        }
    }

    public static GenericReceivedProtocolMessage of(ProtocolReceivedServerResponse protocolReceivedServerResponse, Identity associatedOwnedIdentity) {
        try {
            Encoded[] listOfEncoded = protocolReceivedServerResponse.getEncodedElements().decodeList();
            if (listOfEncoded.length != 4) {
                return null;
            }
            int protocolId = (int) listOfEncoded[0].decodeLong();
            UID protocolInstanceUid = listOfEncoded[1].decodeUid();
            int protocolMessageId = (int) listOfEncoded[2].decodeLong();
            Encoded[] inputs = listOfEncoded[3].decodeList();
            return new GenericReceivedProtocolMessage(
                    protocolReceivedServerResponse.getToIdentity(),
                    inputs,
                    null,
                    protocolReceivedServerResponse.getServerResponse(),
                    protocolInstanceUid,
                    protocolMessageId,
                    protocolId,
                    protocolReceivedServerResponse.getReceptionChannelInfo(),
                    associatedOwnedIdentity,
                    0);
        } catch (DecodingException e) {
            return null;
        }
    }
}
