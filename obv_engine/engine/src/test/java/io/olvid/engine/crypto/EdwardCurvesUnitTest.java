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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.math.BigInteger;
import java.net.URL;

import io.olvid.engine.datatypes.EdwardCurvePoint;


public class EdwardCurvesUnitTest {
    static class TestVector {
        String x;
        String x2;
        String x3;
        String x4;
        String x5;
        String y;
        String y2;
        String y3;
        String y4;
        String y5;
        String n;
        String ny;
        String a;
        String b;

        public void setX4(String x4) {
            this.x4 = x4;
        }

        public void setX5(String x5) {
            this.x5 = x5;
        }

        public void setY4(String y4) {
            this.y4 = y4;
        }

        public void setY5(String y5) {
            this.y5 = y5;
        }

        public void setA(String a) {
            this.a = a;
        }

        public void setB(String b) {
            this.b = b;
        }

        public String getX4() {
            return x4;
        }

        public String getX5() {
            return x5;
        }

        public String getY4() {
            return y4;
        }

        public String getY5() {
            return y5;
        }

        public String getA() {
            return a;
        }

        public String getB() {
            return b;
        }

        public void setX3(String x3) {
            this.x3 = x3;
        }

        public void setY2(String y2) {
            this.y2 = y2;
        }

        public void setY3(String y3) {
            this.y3 = y3;
        }

        public String getX3() {
            return x3;
        }

        public String getY2() {
            return y2;
        }

        public String getY3() {
            return y3;
        }

        public void setNy(String ny) {
            this.ny = ny;
        }

        public String getNy() {
            return ny;
        }

        public void setN(String n) {
            this.n = n;
        }

        public String getN() {

            return n;
        }

        public String getX() {
            return x;
        }

        public String getY() {
            return y;
        }

        public String getX2() {
            return x2;
        }

        public void setX(String x) {
            this.x = x;
        }

        public void setX2(String x2) {
            this.x2 = x2;
        }

        public void setY(String y) {
            this.y = y;
        }
    }


    EdwardCurve mdc = Suite.getCurve(EdwardCurve.MDC);
    EdwardCurve curve25519 = Suite.getCurve(EdwardCurve.CURVE_25519);

    @Test
    public void test_isOnCurve() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsIsOnCurveMDC.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                EdwardCurvePoint P = new EdwardCurvePoint(new BigInteger(vec.x), new BigInteger(vec.y), mdc);
                try {
                    P = new EdwardCurvePoint(new BigInteger(vec.x2), new BigInteger(vec.y), mdc);
                    assertTrue(false);
                } catch (Exception e) {}
            }
        }
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsIsOnCurveCurve25519.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                EdwardCurvePoint P = new EdwardCurvePoint(new BigInteger(vec.x), new BigInteger(vec.y), curve25519);
                try {
                    P = new EdwardCurvePoint(new BigInteger(vec.x2), new BigInteger(vec.y), curve25519);
                    assertTrue(false);
                } catch (Exception e) {}
            }
        }
    }

    @Test
    public void test_xCoordiateFromY() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsIsOnCurveMDC.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                BigInteger X = new BigInteger(vec.x);
                BigInteger Y = new BigInteger(vec.y);
                BigInteger X2 = mdc.xCoordinateFromY(Y);
                assertTrue(X.equals(X2) || mdc.p.subtract(X).equals(X2));
            }
        }
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsIsOnCurveCurve25519.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                BigInteger X = new BigInteger(vec.x);
                BigInteger Y = new BigInteger(vec.y);
                BigInteger X2 = curve25519.xCoordinateFromY(Y);
                assertTrue(X.equals(X2) || curve25519.p.subtract(X).equals(X2));
            }
        }
    }

    @Test
    public void test_scalarMultiplication() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsScalarMultiplicationMDC.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                BigInteger y = new BigInteger(vec.y);
                BigInteger n = new BigInteger(vec.n);
                BigInteger ny = new BigInteger(vec.ny);
                BigInteger ny2 = mdc.scalarMultiplication(n, y);
                assertEquals(ny, ny2);
            }
        }
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsScalarMultiplicationCurve25519.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                BigInteger y = new BigInteger(vec.y);
                BigInteger n = new BigInteger(vec.n);
                BigInteger ny = new BigInteger(vec.ny);
                BigInteger ny2 = curve25519.scalarMultiplication(n, y);
                assertEquals(ny, ny2);
            }
        }
    }

    @Test
    public void test_pointAddition() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsPointAdditionMDC.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                EdwardCurvePoint P = new EdwardCurvePoint(new BigInteger(vec.x), new BigInteger(vec.y), mdc);
                EdwardCurvePoint Q = new EdwardCurvePoint(new BigInteger(vec.x2), new BigInteger(vec.y2), mdc);
                EdwardCurvePoint R = new EdwardCurvePoint(new BigInteger(vec.x3), new BigInteger(vec.y3), mdc);
                EdwardCurvePoint R2 = mdc.pointAddition(P, Q);
                assertEquals(R, R2);
            }
        }
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsPointAdditionCurve25519.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                EdwardCurvePoint P = new EdwardCurvePoint(new BigInteger(vec.x), new BigInteger(vec.y), curve25519);
                EdwardCurvePoint Q = new EdwardCurvePoint(new BigInteger(vec.x2), new BigInteger(vec.y2), curve25519);
                EdwardCurvePoint R = new EdwardCurvePoint(new BigInteger(vec.x3), new BigInteger(vec.y3), curve25519);
                EdwardCurvePoint R2 = curve25519.pointAddition(P, Q);
                assertEquals(R, R2);
            }
        }
    }

    @Test
    public void test_scalarMultiplicationWithX() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsScalarMultiplicationWithXMDC.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                BigInteger n = new BigInteger(vec.n);
                EdwardCurvePoint P = new EdwardCurvePoint(new BigInteger(vec.x), new BigInteger(vec.y), mdc);
                EdwardCurvePoint Q = new EdwardCurvePoint(new BigInteger(vec.x2), new BigInteger(vec.y2), mdc);
                EdwardCurvePoint Q2 = mdc.scalarMultiplicationWithX(n, P);
                assertEquals(Q, Q2);
            }
        }
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsScalarMultiplicationWithXCurve25519.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                BigInteger n = new BigInteger(vec.n);
                EdwardCurvePoint P = new EdwardCurvePoint(new BigInteger(vec.x), new BigInteger(vec.y), curve25519);
                EdwardCurvePoint Q = new EdwardCurvePoint(new BigInteger(vec.x2), new BigInteger(vec.y2), curve25519);
                EdwardCurvePoint Q2 = curve25519.scalarMultiplicationWithX(n, P);
                assertEquals(Q, Q2);
            }
        }
    }

    @Test
    public void test_mulAdd() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsMulAddMDC.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                BigInteger a = new BigInteger(vec.a);
                BigInteger b = new BigInteger(vec.b);
                EdwardCurvePoint P = new EdwardCurvePoint(new BigInteger(vec.x), new BigInteger(vec.y), mdc);
                EdwardCurvePoint Q = new EdwardCurvePoint(new BigInteger(vec.x2), new BigInteger(vec.y2), mdc);
                EdwardCurvePoint R = new EdwardCurvePoint(new BigInteger(vec.x3), new BigInteger(vec.y3), mdc);
                EdwardCurvePoint[] list = mdc.mulAdd(a, P, b, Q);
                assertEquals(list.length, 1);
                EdwardCurvePoint R2 = list[0];
                assertEquals(R, R2);
                Q = EdwardCurvePoint.noCheckFactory(null, new BigInteger(vec.y2), mdc);
                R = new EdwardCurvePoint(new BigInteger(vec.x4), new BigInteger(vec.y4), mdc);
                R2 = new EdwardCurvePoint(new BigInteger(vec.x5), new BigInteger(vec.y5), mdc);
                list = mdc.mulAdd(a, P, b, Q);
                assertEquals(list.length, 2);
                EdwardCurvePoint R3 = list[0];
                EdwardCurvePoint R4 = list[1];
                assertTrue((R3.equals(R) && R4.equals(R2)) || (R3.equals(R2) && R4.equals(R)));
            }
        }
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsMulAddCurve25519.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                BigInteger a = new BigInteger(vec.a);
                BigInteger b = new BigInteger(vec.b);
                EdwardCurvePoint P = new EdwardCurvePoint(new BigInteger(vec.x), new BigInteger(vec.y), curve25519);
                EdwardCurvePoint Q = new EdwardCurvePoint(new BigInteger(vec.x2), new BigInteger(vec.y2), curve25519);
                EdwardCurvePoint R = new EdwardCurvePoint(new BigInteger(vec.x3), new BigInteger(vec.y3), curve25519);
                EdwardCurvePoint[] list = curve25519.mulAdd(a, P, b, Q);
                assertEquals(list.length, 1);
                EdwardCurvePoint R2 = list[0];
                assertEquals(R, R2);
                Q = EdwardCurvePoint.noCheckFactory(null, new BigInteger(vec.y2), curve25519);
                R = new EdwardCurvePoint(new BigInteger(vec.x4), new BigInteger(vec.y4), curve25519);
                R2 = new EdwardCurvePoint(new BigInteger(vec.x5), new BigInteger(vec.y5), curve25519);
                list = curve25519.mulAdd(a, P, b, Q);
                assertEquals(list.length, 2);
                EdwardCurvePoint R3 = list[0];
                EdwardCurvePoint R4 = list[1];
                assertTrue((R3.equals(R) && R4.equals(R2)) || (R3.equals(R2) && R4.equals(R)));
            }
        }
    }

}
