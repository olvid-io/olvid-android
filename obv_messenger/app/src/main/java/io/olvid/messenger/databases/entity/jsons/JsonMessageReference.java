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

package io.olvid.messenger.databases.entity.jsons;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

import io.olvid.messenger.databases.entity.Message;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonMessageReference {
    public long senderSequenceNumber;
    public UUID senderThreadIdentifier;
    public byte[] senderIdentifier;

    public static JsonMessageReference of(Message message) {
        JsonMessageReference jsonMessageReference = new JsonMessageReference();
        jsonMessageReference.senderSequenceNumber = message.senderSequenceNumber;
        jsonMessageReference.senderThreadIdentifier = message.senderThreadIdentifier;
        jsonMessageReference.senderIdentifier = message.senderIdentifier;
        return jsonMessageReference;
    }

    @JsonProperty("ssn")
    public long getSenderSequenceNumber() {
        return senderSequenceNumber;
    }

    @JsonProperty("ssn")
    public void setSenderSequenceNumber(long senderSequenceNumber) {
        this.senderSequenceNumber = senderSequenceNumber;
    }

    @JsonProperty("sti")
    public UUID getSenderThreadIdentifier() {
        return senderThreadIdentifier;
    }

    @JsonProperty("sti")
    public void setSenderThreadIdentifier(UUID senderThreadIdentifier) {
        this.senderThreadIdentifier = senderThreadIdentifier;
    }

    @JsonProperty("si")
    public byte[] getSenderIdentifier() {
        return senderIdentifier;
    }

    @JsonProperty("si")
    public void setSenderIdentifier(byte[] senderIdentifier) {
        this.senderIdentifier = senderIdentifier;
    }
}
