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

package io.olvid.engine.channel.datatypes;


import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.crypto.exceptions.DecryptionException;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.NetworkReceivedMessage;
import io.olvid.engine.datatypes.containers.ReceptionChannelInfo;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.Encoded;

public class ChannelReceivedMessage {
    private final int messageType;
    private final Encoded encodedElements;
    private final AuthEncKey extendedPayloadKey;
    private final ReceptionChannelInfo receptionChannelInfo;
    private final NetworkReceivedMessage message;

    public ChannelReceivedMessage(NetworkReceivedMessage message, AuthEncKey messageKey, ReceptionChannelInfo receptionChannelInfo) throws Exception {
        try {
            // decrypt
            AuthEnc authEnc = Suite.getAuthEnc(messageKey);
            Encoded decryptedMessage = new Encoded(authEnc.decrypt(messageKey, message.getEncryptedContent()));

            // if needed, compute the extended payload key
            if (message.hasExtendedPayload()) {
                PRNG extendedPayloadPRNG = Suite.getDefaultPRNG(0, Seed.of(messageKey));
                extendedPayloadKey = authEnc.generateKey(extendedPayloadPRNG);
            } else {
                extendedPayloadKey = null;
            }

            // parse
            Encoded[] listOfEncoded = decryptedMessage.decodeListWithPadding();
            if (listOfEncoded.length != 2) {
                throw new Exception();
            }
            this.messageType = (int) listOfEncoded[0].decodeLong();
            this.encodedElements = listOfEncoded[1];

            this.receptionChannelInfo = receptionChannelInfo;
            this.message = message;
        } catch (DecryptionException e) {
            throw new Exception("Undecipherable message.");
        }
    }


    public int getMessageType() {
        return messageType;
    }

    public Encoded getEncodedElements() {
        return encodedElements;
    }

    public ReceptionChannelInfo getReceptionChannelInfo() {
        return receptionChannelInfo;
    }

    public NetworkReceivedMessage getMessage() {
        return message;
    }

    public Identity getOwnedIdentity() {
        return message.getOwnedIdentity();
    }

    public UID getMessageUid() {
        return message.getMessageUid();
    }

    public AuthEncKey getExtendedPayloadKey() {
        return extendedPayloadKey;
    }
}
