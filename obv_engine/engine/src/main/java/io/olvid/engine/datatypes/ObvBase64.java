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

package io.olvid.engine.datatypes;

import java.util.Arrays;
import java.util.regex.Pattern;

public class ObvBase64 {

    private static final char[] base64Map = new char[64];
    private static final int[] invBase64Map = new int[128];
    static {
        Arrays.fill(invBase64Map, -1);
        int i=0;
        for (char c='A'; c<='Z'; c++) {
            invBase64Map[c] = i;
            base64Map[i++] = c;
        }
        for (char c='a'; c<='z'; c++) {
            invBase64Map[c] = i;
            base64Map[i++] = c;
        }
        for (char c='0'; c<='9'; c++) {
            invBase64Map[c] = i;
            base64Map[i++] = c;
        }
        invBase64Map['-'] = i;
        base64Map[i++] = '-';
        invBase64Map['_'] = i;
        base64Map[i] = '_';
    }

    private static final Pattern pattern = Pattern.compile("^[-_a-zA-Z0-9]*$");

    public static String encode(byte[] bytes) {
        int base64len = 1 + (bytes.length*4-1)/3;
        char[] chars = new char[base64len];
        int srcPos = 0;
        int outPos = 0;
        while (srcPos < bytes.length-2) {
            int buffer = ((bytes[srcPos] & 0xff)<<16) ^ ((bytes[srcPos+1] & 0xff)<<8) ^ (bytes[srcPos+2] & 0xff);
            srcPos += 3;
            chars[outPos++] = base64Map[buffer >> 18];
            chars[outPos++] = base64Map[(buffer >> 12)&63];
            chars[outPos++] = base64Map[(buffer >> 6)&63];
            chars[outPos++] = base64Map[buffer & 63];
        }
        if (srcPos == bytes.length - 1) {
            int buffer =  bytes[srcPos] & 0xff;
            chars[outPos++] = base64Map[(buffer >> 2)&63];
            chars[outPos++] = base64Map[(buffer << 4)&63];
        }
        if (srcPos == bytes.length - 2) {
            int buffer =  ((bytes[srcPos] & 0xff)<<8) ^ (bytes[srcPos+1] & 0xff);
            chars[outPos++] = base64Map[(buffer >> 10)&63];
            chars[outPos++] = base64Map[(buffer >> 4)&63];
            chars[outPos] = base64Map[(buffer << 2)&63];
        }
        return new String(chars);
    }

    public static byte[] decode(String base64) throws Exception {
        base64 = base64.replaceAll("=+$", ""); // this removes potential Base 64 padding
        char[] chars = base64.toCharArray();
        if ((chars.length & 3) == 1) {
            throw new Exception();
        }
        if (!pattern.matcher(base64).matches()) {
            throw new Exception();
        }
        int bytelen = (chars.length*3)/4;
        byte[] bytes = new byte[bytelen];
        int srcPos = 0;
        int outPos = 0;
        while (srcPos < chars.length-3) {
            int buffer;
            buffer = invBase64Map[chars[srcPos++]] << 18;
            buffer ^= invBase64Map[chars[srcPos++]] << 12;
            buffer ^= invBase64Map[chars[srcPos++]] << 6;
            buffer ^= invBase64Map[chars[srcPos++]];

            bytes[outPos++] = (byte) (buffer >> 16);
            bytes[outPos++] = (byte) (buffer >> 8);
            bytes[outPos++] = (byte) buffer;
        }
        if (srcPos == chars.length - 2) {
            int buffer;
            buffer = invBase64Map[chars[srcPos++]] << 18;
            buffer ^= invBase64Map[chars[srcPos++]] << 12;

            bytes[outPos++] = (byte) (buffer >> 16);
        }
        if (srcPos == chars.length - 3) {
            int buffer;
            buffer = invBase64Map[chars[srcPos++]] << 18;
            buffer ^= invBase64Map[chars[srcPos++]] << 12;
            buffer ^= invBase64Map[chars[srcPos]] << 6;

            bytes[outPos++] = (byte) (buffer >> 16);
            bytes[outPos] = (byte) (buffer >> 8);
        }
        return bytes;
    }

}
