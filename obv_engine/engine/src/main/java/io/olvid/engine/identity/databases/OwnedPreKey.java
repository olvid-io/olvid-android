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

package io.olvid.engine.identity.databases;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.Signature;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.datatypes.DictionaryKey;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.KeyId;
import io.olvid.engine.datatypes.ObvDatabase;
import io.olvid.engine.datatypes.PrivateIdentity;
import io.olvid.engine.datatypes.Session;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.KeyPair;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.ObvCapability;
import io.olvid.engine.identity.datatypes.IdentityManagerSession;


@SuppressWarnings("FieldMayBeFinal")
public class OwnedPreKey implements ObvDatabase {
    static final String TABLE_NAME = "owned_pre_key";

    private final IdentityManagerSession identityManagerSession;

    private KeyId keyId;
    static final String KEY_ID = "key_id";
    private Identity ownedIdentity;
    static final String OWNED_IDENTITY = "owned_identity";
    private Long expirationTimestamp;
    static final String EXPIRATION_TIMESTAMP = "expiration_timestamp";
    private EncryptionPrivateKey encryptionPrivateKey;
    static final String ENCRYPTION_PRIVATE_KEY = "encryption_private_key";
    private Encoded encodedSignedPreKey;
    static final String ENCODED_SIGNED_PRE_KEY = "encoded_signed_pre_key";

    public KeyId getKeyId() {
        return keyId;
    }

    public Long getExpirationTimestamp() {
        return expirationTimestamp;
    }

    public Encoded getEncodedSignedPreKey() {
        return encodedSignedPreKey;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public EncryptionPrivateKey getEncryptionPrivateKey() {
        return encryptionPrivateKey;
    }

    public static OwnedPreKey create(IdentityManagerSession identityManagerSession, Identity ownedIdentity, PrivateIdentity privateIdentity, UID currentDeviceUid, long expirationTimestamp, PRNGService prng) {
        if (ownedIdentity == null || privateIdentity == null || currentDeviceUid == null) {
            return null;
        }
        // generate a key pair
        KeyId keyId = new KeyId(prng.bytes(KeyId.KEYID_LENGTH));
        KeyPair encryptionKeyPair = Suite.generateEncryptionKeyPair(null, prng);
        if (encryptionKeyPair == null) {
            return null;
        }
        String[] rawDeviceCapabilities = ObvCapability.capabilityListToStringArray(ObvCapability.currentCapabilities);
        // encode the public part to sign it for server upload
        Encoded encodedPreKey = Encoded.of(new Encoded[]{
                Encoded.of(keyId.getBytes()),
                Encoded.of(((EncryptionPublicKey) encryptionKeyPair.getPublicKey()).getCompactKey()),
                Encoded.of(currentDeviceUid),
                Encoded.of(expirationTimestamp),
        });
        HashMap<DictionaryKey, Encoded> dict = new HashMap<>();
        dict.put(new DictionaryKey("prk"), encodedPreKey);
        dict.put(new DictionaryKey("cap"), Encoded.of(rawDeviceCapabilities));
        Encoded encodedDict = Encoded.of(dict);
        byte[] signature;
        try {
            signature = Signature.sign(Constants.SignatureContext.DEVICE_PRE_KEY, encodedDict.getBytes(), privateIdentity.getServerAuthenticationPrivateKey().getSignaturePrivateKey(), prng);
            if (signature == null) {
                return null;
            }
        } catch (Exception e) {
            Logger.x(e);
            return null;
        }

        try {
            OwnedPreKey ownedPreKey = new OwnedPreKey(
                    identityManagerSession,
                    keyId,
                    ownedIdentity,
                    expirationTimestamp,
                    (EncryptionPrivateKey) encryptionKeyPair.getPrivateKey(),
                    Encoded.of(new Encoded[]{
                            encodedDict,
                            Encoded.of(signature),
                    })
            );
            ownedPreKey.insert();
            return ownedPreKey;
        } catch (SQLException e) {
            return null;
        }
    }

    public OwnedPreKey(IdentityManagerSession identityManagerSession, KeyId keyId, Identity ownedIdentity, Long expirationTimestamp, EncryptionPrivateKey encryptionPrivateKey, Encoded encodedSignedPreKey) {
        this.identityManagerSession = identityManagerSession;
        this.keyId = keyId;
        this.ownedIdentity = ownedIdentity;
        this.expirationTimestamp = expirationTimestamp;
        this.encryptionPrivateKey = encryptionPrivateKey;
        this.encodedSignedPreKey = encodedSignedPreKey;
    }


    private OwnedPreKey(IdentityManagerSession identityManagerSession, ResultSet res) throws SQLException {
        this.identityManagerSession = identityManagerSession;
        this.keyId = new KeyId(res.getBytes(KEY_ID));
        try {
            this.ownedIdentity = Identity.of(res.getBytes(OWNED_IDENTITY));
        } catch (DecodingException e) {
            throw new SQLException();
        }
        this.expirationTimestamp = res.getLong(EXPIRATION_TIMESTAMP);
        byte[] encryptionPrivateKeyBytes = res.getBytes(ENCRYPTION_PRIVATE_KEY);
        try {
            this.encryptionPrivateKey = (EncryptionPrivateKey) new Encoded(encryptionPrivateKeyBytes).decodePrivateKey();
        } catch (DecodingException ignored) {}
        this.encodedSignedPreKey = new Encoded(res.getBytes(ENCODED_SIGNED_PRE_KEY));
    }


    public static void createTable(Session session) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    KEY_ID + " BLOB PRIMARY KEY, " +
                    OWNED_IDENTITY + " BLOB NOT NULL, " +
                    EXPIRATION_TIMESTAMP + " INTEGER NOT NULL, " +
                    ENCRYPTION_PRIVATE_KEY + " BLOB NOT NULL, " +
                    ENCODED_SIGNED_PRE_KEY + " BLOB NOT NULL, " +
                    "FOREIGN KEY (" + OWNED_IDENTITY + ") REFERENCES " + OwnedIdentity.TABLE_NAME + " (" + OwnedIdentity.OWNED_IDENTITY + ") ON DELETE CASCADE);");
        }
    }

    public static void upgradeTable(Session session, int oldVersion, int newVersion) throws SQLException {
        if (oldVersion < 41 && newVersion >= 41) {
            Logger.d("CREATING `owned_pre_key` DATABASE FOR VERSION 41");
            try (Statement statement = session.createStatement()) {
                statement.execute("CREATE TABLE `owned_pre_key` (" +
                        " key_id BLOB PRIMARY KEY, " +
                        " owned_identity BLOB NOT NULL, " +
                        " expiration_timestamp INTEGER NOT NULL, " +
                        " encryption_private_key BLOB NOT NULL, " +
                        " encoded_signed_pre_key BLOB NOT NULL, " +
                        " FOREIGN KEY (owned_identity) REFERENCES owned_identity(identity) ON DELETE CASCADE);");
            }
            oldVersion = 41;
        }
    }

    @Override
    public void insert() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?);")) {
            statement.setBytes(1, keyId.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            statement.setLong(3, expirationTimestamp);
            statement.setBytes(4, Encoded.of(encryptionPrivateKey).getBytes());
            statement.setBytes(5, encodedSignedPreKey.getBytes());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete() throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + KEY_ID + " = ?;")) {
            statement.setBytes(1, keyId.getBytes());
            statement.executeUpdate();
        }
    }


    public static OwnedPreKey get(IdentityManagerSession identityManagerSession, Identity ownedIdentity, KeyId keyId) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + KEY_ID + " = ? " +
                " AND " + OWNED_IDENTITY + " = ?;")) {
            statement.setBytes(1, keyId.getBytes());
            statement.setBytes(2, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new OwnedPreKey(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }


    public static OwnedPreKey getLatest(IdentityManagerSession identityManagerSession, Identity ownedIdentity) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("SELECT * FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " ORDER BY " + EXPIRATION_TIMESTAMP + " DESC LIMIT 1;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return new OwnedPreKey(identityManagerSession, res);
                } else {
                    return null;
                }
            }
        }
    }

    public static void deleteExpired(IdentityManagerSession identityManagerSession, Identity ownedIdentity, long timestamp) throws SQLException {
        try (PreparedStatement statement = identityManagerSession.session.prepareStatement("DELETE FROM " + TABLE_NAME +
                " WHERE " + OWNED_IDENTITY + " = ? " +
                " AND " + EXPIRATION_TIMESTAMP + " < ?;")) {
            statement.setBytes(1, ownedIdentity.getBytes());
            statement.setLong(2, timestamp);
            statement.executeUpdate();
        }
    }


//    private long commitHookBits = 0;

    @Override
    public void wasCommitted() {
//        commitHookBits = 0;
    }
}
