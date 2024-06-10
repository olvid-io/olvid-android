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

package io.olvid.engine.datatypes;

import java.math.BigInteger;

import io.olvid.engine.crypto.EdwardCurve;
import io.olvid.engine.crypto.exceptions.PointNotOnCurveException;


public class EdwardCurvePoint {
    final BigInteger X;
    final BigInteger Y;
    final EdwardCurve curve;

    public BigInteger getX() {
        return X;
    }

    public BigInteger getY() {
        return Y;
    }

    public EdwardCurve getCurve() {
        return curve;
    }

    public EdwardCurvePoint(BigInteger X, BigInteger Y, EdwardCurve curve) throws PointNotOnCurveException {
        this.X = X;
        this.Y = Y;
        this.curve = curve;
        BigInteger X2 = X.multiply(X).mod(curve.p);
        BigInteger Y2 = Y.multiply(Y).mod(curve.p);
        if (!X2.add(Y2).mod(curve.p).equals(BigInteger.ONE.add(curve.d.multiply(X2).multiply(Y2)).mod(curve.p))) {
            throw new PointNotOnCurveException();
        }
    }

    private EdwardCurvePoint(BigInteger X, BigInteger Y, EdwardCurve curve, boolean noCheck) {
        this.X = X;
        this.Y = Y;
        this.curve = curve;
    }


    public static EdwardCurvePoint noCheckFactory(BigInteger X, BigInteger Y, EdwardCurve curve) {
        return new EdwardCurvePoint(X, Y, curve, true);
    }

    public boolean isLowOrderPoint() {
        if (X != null) {
            return curve.scalarMultiplicationWithX(curve.nu, this).getY().equals(BigInteger.ONE);
        } else {
            return curve.scalarMultiplication(curve.nu, Y).equals(BigInteger.ONE);
        }
    }

    public boolean equals(Object o) {
        if (o instanceof EdwardCurvePoint) {
            EdwardCurvePoint Q = (EdwardCurvePoint) o;
            return X.equals(Q.getX()) && Y.equals(Q.getY());
        }
        return false;
    }
}
