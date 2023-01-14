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

package io.olvid.engine.datatypes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPublicKey;
import io.olvid.engine.encoder.DecodingException;

public class Identity implements Comparable<Identity> {
    private final String server;
    private final ServerAuthenticationPublicKey serverAuthenticationPublicKey;
    private final EncryptionPublicKey encryptionPublicKey;
    private byte[] identityBytes;

    public Identity(String server, ServerAuthenticationPublicKey serverAuthenticationPublicKey, EncryptionPublicKey encryptionPublicKey) {
        this.server = server;
        this.serverAuthenticationPublicKey = serverAuthenticationPublicKey;
        this.encryptionPublicKey = encryptionPublicKey;
        this.identityBytes = null;
    }

    public Identity(String server, ServerAuthenticationPublicKey serverAuthenticationPublicKey, EncryptionPublicKey encryptionPublicKey, byte[] identityBytes) {
        this.server = server;
        this.serverAuthenticationPublicKey = serverAuthenticationPublicKey;
        this.encryptionPublicKey = encryptionPublicKey;
        this.identityBytes = identityBytes;
    }

    public static Identity of(byte[] identityBytes) throws DecodingException {
        int pos = -1;
        String server;
        ServerAuthenticationPublicKey serverAuthenticationPublicKey;
        EncryptionPublicKey encryptionPublicKey;
        for (int i=0; i<identityBytes.length; i++) {
            if (identityBytes[i] == 0) {
                pos = i;
                break;
            }
        }
        if (pos == -1) {
            throw new DecodingException();
        }
        server = new String(Arrays.copyOfRange(identityBytes, 0, pos), StandardCharsets.UTF_8);

        pos = pos + 1;
        int serverPkLength = ServerAuthenticationPublicKey.getCompactKeyLength(identityBytes[pos]);
        if (serverPkLength < 0) {
            throw new DecodingException();
        }
        serverAuthenticationPublicKey = ServerAuthenticationPublicKey.of(Arrays.copyOfRange(identityBytes, pos, pos+serverPkLength));

        pos = pos + serverPkLength;
        int anonAuthPkLength = EncryptionPublicKey.getCompactKeyLength(identityBytes[pos]);
        if (anonAuthPkLength < 0) {
            throw new DecodingException();
        }
        encryptionPublicKey = EncryptionPublicKey.of(Arrays.copyOfRange(identityBytes, pos, pos+anonAuthPkLength));

        return new Identity(server, serverAuthenticationPublicKey, encryptionPublicKey, identityBytes);
    }

    public byte[] getBytes() {
        if (identityBytes == null) {
            byte[] serverBytes = server.getBytes(StandardCharsets.UTF_8);
            byte[] serverAuthenticationPublicKeyBytes = serverAuthenticationPublicKey.getCompactKey();
            byte[] anonAuthPublicKeyBytes = encryptionPublicKey.getCompactKey();
            identityBytes = new byte[serverBytes.length + 1 + serverAuthenticationPublicKeyBytes.length + anonAuthPublicKeyBytes.length];
            System.arraycopy(serverBytes, 0, identityBytes, 0, serverBytes.length);
            identityBytes[serverBytes.length] = (byte) 0x00;
            System.arraycopy(serverAuthenticationPublicKeyBytes, 0, identityBytes, serverBytes.length + 1, serverAuthenticationPublicKeyBytes.length);
            System.arraycopy(anonAuthPublicKeyBytes, 0, identityBytes, serverBytes.length + 1 + serverAuthenticationPublicKeyBytes.length, anonAuthPublicKeyBytes.length);
        }
        return identityBytes;
    }

    public String getServer() {
        return server;
    }

    public ServerAuthenticationPublicKey getServerAuthenticationPublicKey() {
        return serverAuthenticationPublicKey;
    }

    public EncryptionPublicKey getEncryptionPublicKey() {
        return encryptionPublicKey;
    }

    public UID computeUniqueUid() {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        return new UID(sha256.digest(getBytes()));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getBytes());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Identity)) {
            return false;
        }
        return Arrays.equals(getBytes(), ((Identity) other).getBytes());
    }


    @Override
    public String toString() {
        return server + "@" + Logger.toHexString(serverAuthenticationPublicKey.getCompactKey()) + "-" + Logger.toHexString(encryptionPublicKey.getCompactKey());
    }

    @Override
    public int compareTo(Identity o) {
        byte[] me = getBytes();
        byte[] other = o.getBytes();

        if (me.length != other.length) {
            return me.length - other.length;
        }
        for (int i=0; i<me.length; i++) {
            if (me[i] != other[i]) {
                return (me[i]&0xff) - (other[i]&0xff);
            }
        }
        return 0;
    }
}
