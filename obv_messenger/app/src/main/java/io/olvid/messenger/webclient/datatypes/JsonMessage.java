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

package io.olvid.messenger.webclient.datatypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonMessage {
    private String action;
    public JsonMessage() {}
    public JsonMessage(String action) { this.action = action; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegisterConnection extends JsonMessage {
        private String identifier;
        private byte[] bytesIdentity;
        private byte[] token;

        public RegisterConnection(String identifier, byte[] bytesOwnedIdentity, byte[] token) {
            super("registerConnection");
            this.identifier = identifier;
            this.bytesIdentity = bytesOwnedIdentity;
            this.token = token;
        }

        public String getIdentifier() {
            return identifier;
        }
        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }
        public byte[] getBytesIdentity() {
            return bytesIdentity;
        }
        public void setBytesIdentity(byte[] bytesIdentity) {
            this.bytesIdentity = bytesIdentity;
        }
        public byte[] getToken() {
            return token;
        }
        public void setToken(byte[] token) {
            this.token = token;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegisterCorresponding extends JsonMessage {
        private String identifier;
        private int version;

        public RegisterCorresponding(String identifier, int version) {
            super("registerCorresponding");
            this.identifier = identifier;
            this.version = version;
        }

        public String getIdentifier() {
            return identifier;
        }
        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Relay extends JsonMessage {
        private byte[] colissimo;

        public Relay() {
        }
        public Relay(byte[] encryptedColissimo) {
            super("relay");
            this.colissimo = encryptedColissimo;
            //Logger.e(Base64.encodeToString(colissimo, Base64.DEFAULT));
        }

        public byte[] getColissimo() {
            return colissimo;
        }
        public void setColissimo(byte[] colissimo) {
            this.colissimo = colissimo;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Connection extends JsonMessage {
        private byte[] colissimo;

        public Connection() {
        }
        public Connection(byte[] connectionColissimo) {
            super("connection");
            this.colissimo = connectionColissimo;
        }

        public byte[] getColissimo() {
            return colissimo;
        }
        public void setColissimo(byte[] colissimo) {
            this.colissimo = colissimo;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewCorresponding extends JsonMessage {
        private String identifier;
        private int version;

        public NewCorresponding() {}

        public String getIdentifier() {
            return identifier;
        }
        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorMessage extends JsonMessage {
        private int error;

        public ErrorMessage() {}

        public int getError() { return this.error; }

        public void setError(int error) {
            this.error = error;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CorrespondingDisconnected extends JsonMessage {

    }

}
