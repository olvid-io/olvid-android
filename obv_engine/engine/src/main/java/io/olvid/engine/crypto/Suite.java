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

package io.olvid.engine.crypto;

import java.security.InvalidParameterException;

import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.key.CryptographicKey;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesCurve25519KeyPair;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionEciesMDCKeyPair;
import io.olvid.engine.datatypes.key.asymmetric.EncryptionPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.KeyPair;
import io.olvid.engine.datatypes.key.asymmetric.PrivateKey;
import io.olvid.engine.datatypes.key.asymmetric.PublicKey;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaCurve25519KeyPair;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationECSdsaMDCKeyPair;
import io.olvid.engine.datatypes.key.asymmetric.ServerAuthenticationPublicKey;
import io.olvid.engine.datatypes.key.asymmetric.SignaturePublicKey;
import io.olvid.engine.datatypes.key.symmetric.AuthEncKey;
import io.olvid.engine.datatypes.key.symmetric.MACKey;
import io.olvid.engine.datatypes.key.symmetric.SymmetricKey;

public class Suite {
    public static final int LATEST_VERSION = 0;
    public static final int MINIMUM_ACCEPTABLE_VERSION = 0;

    public static AuthEnc getAuthEnc(String authEncName) {
        switch (authEncName) {
            case AuthEnc.CTR_AES256_THEN_HMAC_SHA256:
            default:
                return new AuthEncAES256ThenSHA256();
        }
    }

    public static AuthEnc getDefaultAuthEnc(int obliviousEngineVersion) {
        return getAuthEnc(AuthEnc.CTR_AES256_THEN_HMAC_SHA256);
    }

    public static Hash getHash(String hashName) {
        switch (hashName) {
            case Hash.SHA512:
                return new HashSHA512();
            case Hash.SHA256:
            default:
                return new HashSHA256();
        }
    }

    public static PRNG getPRNG(String prngName, Seed seed) throws InvalidParameterException {
        switch (prngName) {
            case PRNG.PRNG_HMAC_SHA256:
            default:
                return new PRNGHmacSHA256(seed);
        }
    }

    public static PRNG getDefaultPRNG(int obliviousEngineVersion, Seed seed) {
        return getPRNG(PRNG.PRNG_HMAC_SHA256, seed);
    }

    public static PRNGService getPRNGService(String prngName) {
        switch (prngName) {
            case PRNG.PRNG_HMAC_SHA256:
            default:
                return PRNGServiceHmacSHA256.getInstance();
        }
    }

    public static EdwardCurve getCurve(String curveName){
        switch (curveName) {
            case EdwardCurve.CURVE_25519:
                return Curve25519.getInstance();
            case EdwardCurve.MDC:
            default:
                return MDC.getInstance();
        }
    }

    public static KDF getKDF(String kdfName) {
        switch (kdfName) {
            case KDF.KDF_SHA256:
            default:
                return new KDFSha256();
        }
    }

    public static PublicKeyEncryption getPublicKeyEncryption(CryptographicKey key) {
        if (!(key instanceof PublicKey) && !(key instanceof PrivateKey)) {
            return null;
        }
        if (key.getAlgorithmClass() != CryptographicKey.ALGO_CLASS_PUBLIC_KEY_ENCRYPTION) {
            return null;
        }
        switch (key.getAlgorithmImplementation()) {
            case EncryptionPublicKey.ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return new PublicKeyEncryptionEciesMDC();
            case EncryptionPublicKey.ALGO_IMPL_KEM_ECIES_CURVE25519_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return new PublicKeyEncryptionEciesCurve25519();
            default:
                return null;
        }
    }

    public static KeyPair generateServerAuthenticationKeyPair(Byte serverAuthenticationAlgoImplByte, PRNGService prng) {
        if (serverAuthenticationAlgoImplByte == null) {
            serverAuthenticationAlgoImplByte = getDefaultServerAuthenticationAlgoImplByte(LATEST_VERSION);
        }
        switch (serverAuthenticationAlgoImplByte) {
            case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC:
                return ServerAuthenticationECSdsaMDCKeyPair.generate(prng);
            case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519:
                return ServerAuthenticationECSdsaCurve25519KeyPair.generate(prng);
        }
        return null;
    }

    private static byte getDefaultServerAuthenticationAlgoImplByte(int engineVersion) {
        switch (engineVersion) {
            default:
                return ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC;
        }
    }


    public static KeyPair generateEncryptionKeyPair(Byte encryptionAlgoImplByte, PRNGService prng) {
        if (encryptionAlgoImplByte == null) {
            encryptionAlgoImplByte = getDefaultEncryptionAlgoImplByte(LATEST_VERSION);
        }
        switch (encryptionAlgoImplByte) {
            case EncryptionPublicKey.ALGO_IMPL_KEM_ECIES_MDC_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return EncryptionEciesMDCKeyPair.generate(prng);
            case EncryptionPublicKey.ALGO_IMPL_KEM_ECIES_CURVE25519_AND_DEM_CTR_AES256_THEN_HMAC_SHA256:
                return EncryptionEciesCurve25519KeyPair.generate(prng);
        }
        return null;
    }

    private static byte getDefaultEncryptionAlgoImplByte(int engineVersion) {
        switch (engineVersion) {
            default:
                return EncryptionPublicKey.ALGO_IMPL_KEM_ECIES_CURVE25519_AND_DEM_CTR_AES256_THEN_HMAC_SHA256;
        }
    }

    public static ServerAuthentication getServerAuthentication(CryptographicKey key) {
        if (!(key instanceof PublicKey) && !(key instanceof PrivateKey)) {
            return null;
        }
        if (key.getAlgorithmClass() != CryptographicKey.ALGO_CLASS_SERVER_AUTHENTICATION) {
            return null;
        }
        switch (key.getAlgorithmImplementation()) {
            case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_MDC:
                return new ServerAuthenticationECSdsaMDC();
            case ServerAuthenticationPublicKey.ALGO_IMPL_SIGN_CHALLENGE_EC_SDSA_CURVE25519:
                return new ServerAuthenticationECSdsaCurve25519();
            default:
                return null;
        }
    }

    public static Signature getSignature(CryptographicKey key) {
        if (!(key instanceof PublicKey) && !(key instanceof PrivateKey)) {
            return null;
        }
        if (key.getAlgorithmClass() != CryptographicKey.ALGO_CLASS_SIGNATURE) {
            return null;
        }
        switch (key.getAlgorithmImplementation()) {
            case SignaturePublicKey.ALGO_IMPL_EC_SDSA_MDC:
                return new SignatureECSdsaMDC();
            case SignaturePublicKey.ALGO_IMPL_EC_SDSA_CURVE25519:
                return new SignatureECSdsaCurve25519();
            default:
                return null;
        }
    }

    public static AuthEnc getAuthEnc(CryptographicKey key) {
        if (!(key instanceof SymmetricKey)) {
            return null;
        }
        if (key.getAlgorithmClass() != CryptographicKey.ALGO_CLASS_AUTHENTICATED_SYMMETRIC_ENCRYPTION) {
            return null;
        }
        switch (key.getAlgorithmImplementation()) {
            case AuthEncKey.ALGO_IMPL_AES256_THEN_SHA256:
                return new AuthEncAES256ThenSHA256();
            default:
                return null;
        }
    }

    public static MAC getMAC(CryptographicKey key) {
        if (!(key instanceof SymmetricKey)) {
            return null;
        }
        if (key.getAlgorithmClass() != CryptographicKey.ALGO_CLASS_MAC) {
            return null;
        }
        switch (key.getAlgorithmImplementation()) {
            case MACKey.ALGO_IMPL_HMAC_SHA256:
                return new MACHmacSha256();
            default:
                return null;
        }
    }

    public static MAC getMAC(String macName) {
        switch (macName) {
            case MAC.HMAC_SHA256:
                return new MACHmacSha256();
            default:
                return null;
        }
    }

    public static KDF getDefaultKDF(int obliviousEngineVersion) {
        return new KDFSha256();
    }

    public static PRNGService getDefaultPRNGService(int obliviousEngineVersion) {
        return getPRNGService(PRNG.PRNG_HMAC_SHA256);
    }

    public static Commitment getDefaultCommitment(int obliviousEngineVersion) {
        return new CommitmentWithSHA256();
    }

    public static MAC getDefaultMAC(int obliviousEngineVersion) {
        return new MACHmacSha256();
    }
}
