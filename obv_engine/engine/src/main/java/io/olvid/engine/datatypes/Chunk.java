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

package io.olvid.engine.datatypes;


import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public class Chunk {
    private final int chunkNumber;
    private final byte[] data;

    public int getChunkNumber() {
        return chunkNumber;
    }

    public byte[] getData() {
        return data;
    }

    public Chunk(int chunkNumber, byte[] data) {
        this.chunkNumber = chunkNumber;
        this.data = data;
    }

    public static Chunk of(Encoded encodedChunk) throws DecodingException {
        Encoded[] list = encodedChunk.decodeList();
        return new Chunk((int) list[0].decodeLong(), list[1].decodeBytes());
    }

    public Encoded encode() {
        return Encoded.of(new Encoded[]{Encoded.of(chunkNumber), Encoded.of(data)});
    }

    public static int lengthOfEncodedChunkFromLengthOfInnerData(int length) {
        return length + 3*Encoded.ENCODED_HEADER_LENGTH + Encoded.INT_ENCODING_LENGTH;
    }

    public static int lengthOfInnerDataFromLengthOfEncodedChunk(int length) {
        return length - 3*Encoded.ENCODED_HEADER_LENGTH - Encoded.INT_ENCODING_LENGTH;
    }

}
