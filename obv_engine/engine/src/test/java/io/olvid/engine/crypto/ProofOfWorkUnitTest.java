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

package io.olvid.engine.crypto;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.net.URL;

import io.olvid.engine.encoder.Encoded;

import static org.junit.Assert.*;


public class ProofOfWorkUnitTest {
    static class TestVector {
        String challenge;
        String response;

        public void setChallenge(String challenge) {
            this.challenge = challenge;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public String getChallenge() {
            return challenge;
        }

        public String getResponse() {
            return response;
        }
    }
    static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] data = new byte[len/2];
        for(int i = 0; i < len; i+=2){
            data[i/2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    @Test
    public void test_solveChallenge() throws Exception {
        {
            ObjectMapper mapper = new ObjectMapper();
            URL jsonURL = getClass().getClassLoader().getResource("TestVectorsProofOfWork.json");
            JsonParser parser = new JsonFactory().createParser(jsonURL).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            parser.nextToken();
            parser.nextToken();
            MappingIterator iter = mapper.readValues(parser, TestVector.class);
            while (iter.hasNext()) {
                TestVector vec = (TestVector) iter.next();
                Encoded challenge = new Encoded(fromHex(vec.challenge));
                Encoded expectedResponse = new Encoded(fromHex(vec.response));
                Encoded response = ProofOfWorkEngine.solveChallenge(challenge);
                assertEquals(response, expectedResponse);
            }
        }
    }
}
