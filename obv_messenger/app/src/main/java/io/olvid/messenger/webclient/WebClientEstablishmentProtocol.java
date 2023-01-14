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

package io.olvid.messenger.webclient;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.security.InvalidKeyException;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Commitment;
import io.olvid.engine.crypto.PRNGService;
import io.olvid.engine.crypto.PublicKeyEncryption;
import io.olvid.engine.crypto.SAS;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.containers.CiphertextAndKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesCurve25519KeyPair;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesCurve25519PublicKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncAES256ThenSHA256Key;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.encoder.DecodingException;
import io.olvid.messenger.webclient.protobuf.ConnectionAppDecommitmentOuterClass.ConnectionAppDecommitment;
import io.olvid.messenger.webclient.protobuf.ConnectionAppIdentifierPkKemCommitSeedOuterClass.ConnectionAppIdentifierPkKemCommitSeed;
import io.olvid.messenger.webclient.protobuf.ConnectionBrowserKemSeedOuterClass.ConnectionBrowserKemSeed;
import io.olvid.messenger.webclient.protobuf.ConnectionColissimoOuterClass;
import io.olvid.messenger.webclient.protobuf.ConnectionColissimoOuterClass.ConnectionColissimo;


public class WebClientEstablishmentProtocol {
    final private PRNGService prng;
    private AuthEncAES256ThenSHA256Key appKey;
    private AuthEncAES256ThenSHA256Key webKey;
    private final Seed appSeed;
    private Seed webSeed;
    private byte[] decommitment;
    private EncryptionEciesCurve25519PublicKey webPublicKey;
    final private EncryptionEciesCurve25519KeyPair appKeyPair;


    public WebClientEstablishmentProtocol() {
        prng = Suite.getDefaultPRNGService(0);

        // generate key pair
        this.appKeyPair = EncryptionEciesCurve25519KeyPair.generate(this.prng);

         // generate app seed
        this.appSeed = new Seed(this.prng);
    }

    // protocol first step
    public ConnectionColissimo prepareConnectionAppIdentifierPkKemCommitSeed(byte[] compactWebPublicKey, String appConnectionIdentifier) {
        CiphertextAndKey ciphertextAndKey;
        Commitment.CommitmentOutput commitmentOutput;
        ConnectionAppIdentifierPkKemCommitSeed connectionAppIdentifierPkKemCommitSeed;
        ConnectionColissimo connectionColissimo;

        try {
            this.webPublicKey = (EncryptionEciesCurve25519PublicKey)EncryptionPublicKey.of(compactWebPublicKey);
        } catch (DecodingException e) {
            Logger.e("Unable to decode web raw public key");
            e.printStackTrace();
            return (null);
        }

        // create kem from web public key
        try {
            PublicKeyEncryption publicKeyEncryption;
            publicKeyEncryption = Suite.getPublicKeyEncryption(this.webPublicKey);
            if (publicKeyEncryption == null) {
                Logger.e("Unable to find public key encryption");
                return (null);
            }
            else {
                ciphertextAndKey = Suite.getPublicKeyEncryption(this.webPublicKey).kemEncrypt(this.webPublicKey, this.prng);
            }
        } catch (InvalidKeyException e) {
            Logger.e("Invalid web public key for kem");
            e.printStackTrace();
            return (null);
        }
        this.appKey = (AuthEncAES256ThenSHA256Key) ciphertextAndKey.getKey();

        // create commit with appCipher||appSeed and appPublicKey as tag
        byte[] cipherText = ciphertextAndKey.getCiphertext().getBytes();
        byte[] commitmentValue = new byte[cipherText.length + this.appSeed.length];
        System.arraycopy(cipherText, 0, commitmentValue, 0, cipherText.length);
        System.arraycopy(this.appSeed.getBytes(), 0, commitmentValue, cipherText.length, this.appSeed.length);
        commitmentOutput = Suite.getDefaultCommitment(0).commit(this.appKeyPair.getPublicKey().getCompactKey(), commitmentValue, this.prng);
        this.decommitment = commitmentOutput.decommitment;

        connectionAppIdentifierPkKemCommitSeed = ConnectionAppIdentifierPkKemCommitSeed.newBuilder()
                .setIdentifier(appConnectionIdentifier)
                .setPublicKey(ByteString.copyFrom(this.appKeyPair.getPublicKey().getCompactKey()))
                .setKemCipher(ByteString.copyFrom(ciphertextAndKey.getCiphertext().getBytes()))
                .setCommit(ByteString.copyFrom(commitmentOutput.commitment))
                .build();

        connectionColissimo = ConnectionColissimo.newBuilder()
                .setType(ConnectionColissimoOuterClass.ConnectionColissimoType.CONNECTION_APP_IDENTIFIER_PK_KEM_COMMIT_SEED)
                .setConnectionAppIdentifierPkKemCommitSeed(connectionAppIdentifierPkKemCommitSeed)
                .build();

        return (connectionColissimo);
    }

    // protocol second step: message parsing (handler called by websocket message handler)
    public boolean handleBrowserKemSeed(byte[] bytesConnectionColissimo) {
        ConnectionColissimo connectionColissimo;
        ConnectionBrowserKemSeed connectionBrowserKemSeed;

        try {
            connectionColissimo = ConnectionColissimo.parseFrom(bytesConnectionColissimo);
        } catch (InvalidProtocolBufferException e) {
            Logger.e("Unable to parse connection colissimo, ignoring");
            e.printStackTrace();
            return (false);
        }
        if (connectionColissimo.getType() != ConnectionColissimoOuterClass.ConnectionColissimoType.CONNECTION_BROWSER_KEM_SEED) {
            Logger.e("Invalid connection message type received, ignoring");
            return (false);
        }

        connectionBrowserKemSeed = connectionColissimo.getConnectionBrowserKemSeed();

        // store webSeed
        this.webSeed = new Seed(connectionBrowserKemSeed.getSeed().toByteArray());

        // decrypt web kem and store key
        try {
            EncryptedBytes encryptedCipher = new EncryptedBytes(connectionBrowserKemSeed.getKemCipher().toByteArray());
            this.webKey = (AuthEncAES256ThenSHA256Key) Suite.getPublicKeyEncryption(this.appKeyPair.getPublicKey()).kemDecrypt(this.appKeyPair.getPrivateKey(), encryptedCipher);
        } catch (Exception e) {
            Logger.e("Unable to decrypt web kem");
            e.printStackTrace();
            return (false);
        }
        return (true);
    }

    // protocol third step, send next message
    public ConnectionColissimo prepareDecommitment() {
        ConnectionColissimo connectionColissimo;
        ConnectionAppDecommitment connectionAppDecommitment;

        connectionAppDecommitment = ConnectionAppDecommitment
                .newBuilder()
                .setDecommitment(ByteString.copyFrom(this.decommitment))
                .build();

        connectionColissimo = ConnectionColissimo
                .newBuilder()
                .setType(ConnectionColissimoOuterClass.ConnectionColissimoType.CONNECTION_APP_DECOMMITMENT)
                .setConnectionAppDecommitment(connectionAppDecommitment)
                .build();

        return (connectionColissimo);
    }

    public String calculateSasCode() {
        return (SAS.computeSimple(this.appSeed, this.webSeed, this.webPublicKey.getCompactKey(), 4));
    }

    public AuthEncKey derivateAuthEncKey() {
        Seed finalSeed;
        try {
            finalSeed = Seed.of(this.appKey, this.webKey);
        } catch (Exception e) {
            Logger.e("Unable to derivate final AuthEncKey");
            e.printStackTrace();
            return (null);
        }
        return (Suite.getDefaultAuthEnc(0).generateKey(Suite.getDefaultPRNG(0, finalSeed)));
    }
}
