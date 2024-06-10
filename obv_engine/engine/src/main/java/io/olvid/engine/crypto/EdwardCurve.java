/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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


import java.math.BigInteger;
import java.util.ArrayList;

import io.olvid.engine.datatypes.EdwardCurvePoint;

public abstract class EdwardCurve {
    public static final String MDC = "MDC";
    public static final String CURVE_25519 = "Curve_25519";

    public BigInteger p = null;
    public BigInteger q = null;
    public BigInteger d = null;
    public BigInteger cardinality = null;
    public BigInteger nu = null;
    public BigInteger nuInv = null;
    public EdwardCurvePoint G = null;
    public BigInteger tonelliNonQR = null;
    public BigInteger tonelliT = null;
    public int tonelliS = 0;
    public int byteLength = 0;

    // NOTE: this method only returns one of two possible x coordinates, the other one is (p-x)
    public BigInteger xCoordinateFromY(BigInteger Y) {
        BigInteger Y2 = Y.multiply(Y).mod(p);
        BigInteger X2 = BigInteger.ONE.subtract(d.multiply(Y2)).modInverse(p).multiply(BigInteger.ONE.subtract(Y2)).mod(p);
        return modSqrt(X2);
    }

    // NOTE: this method only returns one possible sqrt of x, the other one is (p-x)
    private BigInteger modSqrt(BigInteger x) {
        BigInteger pMinusOneDiv2 = p.shiftRight(1);
        if (!x.modPow(pMinusOneDiv2, p).equals(BigInteger.ONE)) {
            return null;
        }
        if (p.testBit(1)) {
            return x.modPow(p.add(BigInteger.ONE).shiftRight(2), p);
        } else {
            BigInteger TWO = BigInteger.valueOf(2);
            BigInteger e = BigInteger.ZERO;
            for (int i=1; i<tonelliS; i++) {
                if (!tonelliNonQR.modPow(e, p).multiply(x).modPow(pMinusOneDiv2.shiftRight(i),p).equals(BigInteger.ONE)) {
                    e = e.add(TWO.pow(i));
                }
            }
            return tonelliNonQR.modPow(tonelliT.multiply(e).shiftRight(1), p).multiply(x.modPow(tonelliT.add(BigInteger.ONE).shiftRight(1), p)).mod(p);
        }
    }

    public BigInteger scalarMultiplication(BigInteger n, BigInteger Y) {
        if (n.equals(BigInteger.ZERO) || (Y.equals(BigInteger.ONE))) {
            return BigInteger.ONE;
        }
        if (Y.equals(p.subtract(BigInteger.ONE))) {
            if (n.testBit(0)) {
                return p.subtract(BigInteger.ONE);
            } else {
                return BigInteger.ONE;
            }
        }
        BigInteger TWO = BigInteger.valueOf(2);

        BigInteger c = BigInteger.ONE.subtract(d).modInverse(p);
        BigInteger uP = Y.add(BigInteger.ONE).mod(p);
        BigInteger wP = BigInteger.ONE.subtract(Y).mod(p);
        BigInteger uQ = BigInteger.ONE;
        BigInteger wQ = BigInteger.ZERO;
        BigInteger uR = uP;
        BigInteger wR = wP;

        // reduce n mod cardinality so we can loop on cardinality.bitLength()
        n = n.mod(cardinality);
        for (int i=cardinality.bitLength()-1; i>=0; i--) {
            BigInteger t1 = uQ.subtract(wQ).multiply(uR.add(wR)).mod(p);
            BigInteger t2 = uQ.add(wQ).multiply(uR.subtract(wR)).mod(p);
            BigInteger uQplusR = wP.multiply(t1.add(t2).modPow(TWO, p)).mod(p);
            BigInteger wQplusR = uP.multiply(t1.subtract(t2).modPow(TWO, p)).mod(p);
            if (n.testBit(i)) {
                BigInteger t3 = uR.add(wR).modPow(TWO, p);
                BigInteger t4 = uR.subtract(wR).modPow(TWO, p);
                BigInteger t5 = t3.subtract(t4).mod(p);
                BigInteger u2R = t3.multiply(t4).mod(p);
                BigInteger w2R = t5.multiply(t4.add(c.multiply(t5))).mod(p);
                uQ = uQplusR;
                wQ = wQplusR;
                uR = u2R;
                wR = w2R;
            } else {
                BigInteger t3 = uQ.add(wQ).modPow(TWO, p);
                BigInteger t4 = uQ.subtract(wQ).modPow(TWO, p);
                BigInteger t5 = t3.subtract(t4).mod(p);
                BigInteger u2Q = t3.multiply(t4).mod(p);
                BigInteger w2Q = t5.multiply(t4.add(c.multiply(t5))).mod(p);
                uQ = u2Q;
                wQ = w2Q;
                uR = uQplusR;
                wR = wQplusR;
            }
        }

        return uQ.subtract(wQ).multiply(uQ.add(wQ).modInverse(p)).mod(p);
    }

    public EdwardCurvePoint pointAddition(EdwardCurvePoint P, EdwardCurvePoint Q) {
        BigInteger t = d.multiply(P.getX()).mod(p).multiply(Q.getX()).mod(p).multiply(P.getY()).mod(p).multiply(Q.getY()).mod(p);
        BigInteger z = t.add(BigInteger.ONE).modInverse(p);
        BigInteger X = z.multiply(P.getX().multiply(Q.getY()).add(P.getY().multiply(Q.getX()))).mod(p);
        z = BigInteger.ONE.subtract(t).modInverse(p);
        BigInteger Y = z.multiply(P.getY().multiply(Q.getY()).subtract(P.getX().multiply(Q.getX()))).mod(p);
        return EdwardCurvePoint.noCheckFactory(X, Y, this);
    }


    public EdwardCurvePoint scalarMultiplicationWithX(BigInteger n, EdwardCurvePoint P) {
        if (n.equals(BigInteger.ZERO) || P.getY().equals(BigInteger.ONE)) {
            return EdwardCurvePoint.noCheckFactory(BigInteger.ZERO, BigInteger.ONE, this);
        }
        if (P.getY().equals(p.subtract(BigInteger.ONE))) {
            if (n.testBit(0)) {
                return EdwardCurvePoint.noCheckFactory(BigInteger.ZERO, p.subtract(BigInteger.ONE), this);
            } else {
                return EdwardCurvePoint.noCheckFactory(BigInteger.ZERO, BigInteger.ONE, this);
            }
        }
        EdwardCurvePoint Q = EdwardCurvePoint.noCheckFactory(P.getX(), P.getY(), this);
        EdwardCurvePoint R = EdwardCurvePoint.noCheckFactory(BigInteger.ZERO, BigInteger.ONE, this);

        // reduce n mod cardinality so we can loop on cardinality.bitLength()
        n = n.mod(cardinality);
        for (int i=cardinality.bitLength()-1; i>=0; i--) {
            if (n.testBit(i)) {
                R = pointAddition(R, Q);
                Q = pointAddition(Q, Q);
            } else {
                Q = pointAddition(R, Q);
                R = pointAddition(R, R);
            }
        }
        return R;
    }


    public EdwardCurvePoint[] mulAdd(BigInteger a, EdwardCurvePoint P1, BigInteger b, EdwardCurvePoint P2) {
        EdwardCurvePoint P3 = scalarMultiplicationWithX(a, P1);
        ArrayList<EdwardCurvePoint> list = new ArrayList<>();
        if (P2.getX() != null) {
            EdwardCurvePoint P4 = scalarMultiplicationWithX(b, P2);
            list.add(pointAddition(P3, P4));
        } else {
            BigInteger Y4 = scalarMultiplication(b, P2.getY());
            BigInteger X4 = xCoordinateFromY(Y4);
            EdwardCurvePoint P4 = EdwardCurvePoint.noCheckFactory(X4, Y4, this);
            list.add(pointAddition(P3, P4));
            P4 = EdwardCurvePoint.noCheckFactory(p.subtract(X4), Y4, this);
            list.add(pointAddition(P3, P4));
        }
        return list.toArray(new EdwardCurvePoint[0]);
    }

    public EdwardCurve.ScalarAndPoint generateRandomScalarAndPoint(PRNGService prng) {
        BigInteger a;
        do {
            a = prng.bigInt(q);
        } while (a.equals(BigInteger.ONE) || a.equals(BigInteger.ZERO));
        EdwardCurvePoint aG = scalarMultiplicationWithX(a, G);
        return new ScalarAndPoint(a, aG);
    }

    public boolean isLowOrderPoint(BigInteger Ay) {
        return scalarMultiplication(nu, Ay).equals(BigInteger.ONE);
    }

    static class ScalarAndPoint {
        private final BigInteger scalar;
        private final EdwardCurvePoint point;

        public BigInteger getScalar() {
            return scalar;
        }

        public EdwardCurvePoint getPoint() {
            return point;
        }

        ScalarAndPoint(BigInteger scalar, EdwardCurvePoint point) {
            this.point = point;
            this.scalar = scalar;
        }
    }
}




class MDC extends EdwardCurve {
    private static final MDC instance = new MDC();

    private MDC() {
        p = new BigInteger(1, new byte[]{(byte) 0xf1, (byte) 0x3b, (byte) 0x68, (byte) 0xb9, (byte) 0xd4, (byte) 0x56, (byte) 0xaf, (byte) 0xb4, (byte) 0x53, (byte) 0x2f, (byte) 0x92, (byte) 0xfd, (byte) 0xd7, (byte) 0xa5, (byte) 0xfd, (byte) 0x4f, (byte) 0x08, (byte) 0x6a, (byte) 0x90, (byte) 0x37, (byte) 0xef, (byte) 0x07, (byte) 0xaf, (byte) 0x9e, (byte) 0xc1, (byte) 0x37, (byte) 0x10, (byte) 0x40, (byte) 0x57, (byte) 0x79, (byte) 0xec, (byte) 0x13});
        G = EdwardCurvePoint.noCheckFactory(
                new BigInteger(1, new byte[]{(byte) 0xb6, (byte) 0x81, (byte) 0x88, (byte) 0x6a, (byte) 0x7f, (byte) 0x90, (byte) 0x3b, (byte) 0x83, (byte) 0xd8, (byte) 0x5b, (byte) 0x42, (byte) 0x1e, (byte) 0x03, (byte) 0xcb, (byte) 0xcf, (byte) 0x63, (byte) 0x50, (byte) 0xd7, (byte) 0x2a, (byte) 0xbb, (byte) 0x8d, (byte) 0x27, (byte) 0x13, (byte) 0xe2, (byte) 0x23, (byte) 0x2c, (byte) 0x25, (byte) 0xbf, (byte) 0xee, (byte) 0x68, (byte) 0x36, (byte) 0x3b}),
                new BigInteger(1, new byte[]{(byte) 0xca, (byte) 0x67, (byte) 0x34, (byte) 0xe1, (byte) 0xb5, (byte) 0x9c, (byte) 0x0b, (byte) 0x03, (byte) 0x59, (byte) 0x81, (byte) 0x4d, (byte) 0xcf, (byte) 0x65, (byte) 0x63, (byte) 0xda, (byte) 0x42, (byte) 0x1d, (byte) 0xa8, (byte) 0xbc, (byte) 0x3d, (byte) 0x81, (byte) 0xa9, (byte) 0x3a, (byte) 0x3a, (byte) 0x7e, (byte) 0x73, (byte) 0xc3, (byte) 0x55, (byte) 0xbd, (byte) 0x28, (byte) 0x64, (byte) 0xb5}),
                this
        );
        q = new BigInteger(1, new byte[]{(byte) 0x3c, (byte) 0x4e, (byte) 0xda, (byte) 0x2e, (byte) 0x75, (byte) 0x15, (byte) 0xab, (byte) 0xed, (byte) 0x14, (byte) 0xcb, (byte) 0xe4, (byte) 0xbf, (byte) 0x75, (byte) 0xe9, (byte) 0x7f, (byte) 0x53, (byte) 0x4f, (byte) 0xb3, (byte) 0x89, (byte) 0x75, (byte) 0xfa, (byte) 0xf9, (byte) 0x74, (byte) 0xbb, (byte) 0x58, (byte) 0x85, (byte) 0x52, (byte) 0xf4, (byte) 0x21, (byte) 0xb0, (byte) 0xf7, (byte) 0xfb});
        d = new BigInteger(1, new byte[]{(byte) 0x57, (byte) 0x13, (byte) 0x04, (byte) 0x52, (byte) 0x19, (byte) 0x65, (byte) 0xb6, (byte) 0x8a, (byte) 0x7c, (byte) 0xdf, (byte) 0xbf, (byte) 0xcc, (byte) 0xfb, (byte) 0x0c, (byte) 0xb9, (byte) 0x62, (byte) 0x5f, (byte) 0x12, (byte) 0x70, (byte) 0xf6, (byte) 0x3f, (byte) 0x21, (byte) 0xf0, (byte) 0x41, (byte) 0xee, (byte) 0x93, (byte) 0x09, (byte) 0x25, (byte) 0x03, (byte) 0x00, (byte) 0xcf, (byte) 0x89});
        nu = BigInteger.valueOf(4);
        nuInv = nu.modInverse(q);
        cardinality = q.multiply(nu);
        tonelliNonQR = BigInteger.valueOf(2);
        tonelliT = p.shiftRight(1);
        tonelliS = 1;
        byteLength = 32;
    }

    public static MDC getInstance() {
        return instance;
    }
}

class Curve25519 extends EdwardCurve {
    private static final Curve25519 instance = new Curve25519();

    private Curve25519() {
        p = new BigInteger(1, new byte[]{(byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xed});
        G = EdwardCurvePoint.noCheckFactory(
                new BigInteger(1, new byte[]{(byte) 0x15, (byte) 0x9a, (byte) 0x68, (byte) 0x49, (byte) 0xe4, (byte) 0x4c, (byte) 0x3c, (byte) 0x7f, (byte) 0x06, (byte) 0x1b, (byte) 0x3d, (byte) 0x57, (byte) 0x0f, (byte) 0xc4, (byte) 0xed, (byte) 0x5b, (byte) 0x5d, (byte) 0x14, (byte) 0xc8, (byte) 0xba, (byte) 0x42, (byte) 0x53, (byte) 0xdf, (byte) 0x49, (byte) 0xcc, (byte) 0x7e, (byte) 0xdf, (byte) 0x80, (byte) 0xf5, (byte) 0x33, (byte) 0xad, (byte) 0x9b}),
                new BigInteger(1, new byte[]{(byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x66, (byte) 0x58}),
                this
        );
        q = new BigInteger(1, new byte[]{(byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x14, (byte) 0xde, (byte) 0xf9, (byte) 0xde, (byte) 0xa2, (byte) 0xf7, (byte) 0x9c, (byte) 0xd6, (byte) 0x58, (byte) 0x12, (byte) 0x63, (byte) 0x1a, (byte) 0x5c, (byte) 0xf5, (byte) 0xd3, (byte) 0xed});
        d = new BigInteger(1, new byte[]{(byte) 0x2d, (byte) 0xfc, (byte) 0x93, (byte) 0x11, (byte) 0xd4, (byte) 0x90, (byte) 0x01, (byte) 0x8c, (byte) 0x73, (byte) 0x38, (byte) 0xbf, (byte) 0x86, (byte) 0x88, (byte) 0x86, (byte) 0x17, (byte) 0x67, (byte) 0xff, (byte) 0x8f, (byte) 0xf5, (byte) 0xb2, (byte) 0xbe, (byte) 0xbe, (byte) 0x27, (byte) 0x54, (byte) 0x8a, (byte) 0x14, (byte) 0xb2, (byte) 0x35, (byte) 0xec, (byte) 0xa6, (byte) 0x87, (byte) 0x4a});
        nu = BigInteger.valueOf(8);
        nuInv = nu.modInverse(q);
        cardinality = q.multiply(nu);
        tonelliNonQR = BigInteger.valueOf(2);
        tonelliT = p.shiftRight(2);
        tonelliS = 2;
        byteLength = 32;
    }

    public static Curve25519 getInstance() {
        return instance;
    }
}

