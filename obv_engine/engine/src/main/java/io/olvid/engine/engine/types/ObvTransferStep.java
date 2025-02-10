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

package io.olvid.engine.engine.types;

import java.util.HashMap;
import java.util.Map;

import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;

public abstract class ObvTransferStep {
    public abstract Step getStep();
    public abstract Encoded[] getEncodedParts();


    public static ObvTransferStep of(Encoded encoded) throws DecodingException {
        Encoded[] list = encoded.decodeList();
        if (list.length != 2) {
            throw new DecodingException();
        }
        int id = (int) list[0].decodeLong();
        Encoded[] encodedParts = list[1].decodeList();
        Step step = Step.fromIntValue(id);
        if (step == null) {
            throw new DecodingException();
        }
        switch (step) {
            case FAIL:
                return new Fail(encodedParts);
            case SOURCE_WAIT_FOR_SESSION_NUMBER:
                return new SourceWaitForSessionNumberStep(encodedParts);
            case SOURCE_DISPLAY_SESSION_NUMBER:
                return new SourceDisplaySessionNumber(encodedParts);
            case TARGET_SESSION_NUMBER_INPUT:
                return new TargetSessionNumberInput(encodedParts);
            case ONGOING_PROTOCOL:
                return new OngoingProtocol(encodedParts);
            case SOURCE_SAS_INPUT:
                return new SourceSasInput(encodedParts);
            case TARGET_SHOW_SAS:
                return new TargetShowSas(encodedParts);
            case SOURCE_SNAPSHOT_SENT:
                return new SourceSnapshotSent(encodedParts);
            case TARGET_SNAPSHOT_RECEIVED:
                return new TargetSnapshotReceived(encodedParts);
            case TARGET_REQUESTS_KEYCLOAK_AUTHENTICATION_PROOF:
                return new TargetRequestsKeycloakAuthenticationProof(encodedParts);
            default:
                throw new DecodingException();
        }
    }

    Encoded encode() {
        return Encoded.of(new Encoded[] {
                Encoded.of(getStep().value),
                Encoded.of(getEncodedParts()),
        });
    }


    public enum Step {
        FAIL(1000),
        SOURCE_WAIT_FOR_SESSION_NUMBER(0),
        SOURCE_DISPLAY_SESSION_NUMBER(1),
        TARGET_SESSION_NUMBER_INPUT(2),
        ONGOING_PROTOCOL(3),
        SOURCE_SAS_INPUT(4),
        TARGET_SHOW_SAS(5),
        SOURCE_SNAPSHOT_SENT(6),
        TARGET_SNAPSHOT_RECEIVED(7),
        TARGET_REQUESTS_KEYCLOAK_AUTHENTICATION_PROOF(8);

        private static final Map<Integer, Step> valueMap = new HashMap<>();
        static {
            for (Step step : values()) {
                valueMap.put(step.value, step);
            }
        }

        final int value;

        Step(int value) {
            this.value = value;
        }

        static Step fromIntValue(int value) {
            return valueMap.get(value);
        }
    }

    public static class SourceWaitForSessionNumberStep extends ObvTransferStep {
        public SourceWaitForSessionNumberStep() {
        }

        public SourceWaitForSessionNumberStep(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 0) {
                throw new DecodingException();
            }
        }

        @Override
        public Step getStep() {
            return Step.SOURCE_WAIT_FOR_SESSION_NUMBER;
        }

        @Override
        public Encoded[] getEncodedParts() {
            return new Encoded[0];
        }
    }

    public static class SourceDisplaySessionNumber extends ObvTransferStep {
        public final long sessionNumber;
        public SourceDisplaySessionNumber(long sessionNumber) {
            this.sessionNumber = sessionNumber;
        }

        public SourceDisplaySessionNumber(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 1) {
                throw new DecodingException();
            }
            this.sessionNumber = encodedParts[0].decodeLong();
        }

        @Override
        public Step getStep() {
            return Step.SOURCE_DISPLAY_SESSION_NUMBER;
        }

        @Override
        public Encoded[] getEncodedParts() {
            return new Encoded[] {
                    Encoded.of(sessionNumber),
            };
        }
    }

    public static class Fail extends ObvTransferStep {
        public static final int FAIL_REASON_NETWORK_ERROR = 1;
        public static final int FAIL_REASON_TRANSFERRED_IDENTITY_ALREADY_EXISTS = 2;
        public static final int FAIL_REASON_INVALID_RESPONSE = 3;
        public final int failReason;
        public Fail(int failReason) {
            this.failReason = failReason;
        }

        public Fail(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 1) {
                throw new DecodingException();
            }
            this.failReason = (int) encodedParts[0].decodeLong();
        }

        @Override
        public Step getStep() {
            return Step.FAIL;
        }

        @Override
        public Encoded[] getEncodedParts() {
            return new Encoded[] {
                    Encoded.of(failReason),
            };
        }
    }

    public static class TargetSessionNumberInput extends ObvTransferStep {
        public TargetSessionNumberInput() {
        }

        public TargetSessionNumberInput(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 0) {
                throw new DecodingException();
            }
        }

        @Override
        public Step getStep() {
            return Step.TARGET_SESSION_NUMBER_INPUT;
        }

        @Override
        public Encoded[] getEncodedParts() {
            return new Encoded[0];
        }
    }

    public static class OngoingProtocol extends ObvTransferStep {
        public OngoingProtocol() {
        }

        public OngoingProtocol(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 0) {
                throw new DecodingException();
            }
        }

        @Override
        public Step getStep() {
            return Step.ONGOING_PROTOCOL;
        }

        @Override
        public Encoded[] getEncodedParts() {
            return new Encoded[0];
        }
    }

    public static class SourceSasInput extends ObvTransferStep {
        public final String correctSas;
        public final String targetDeviceName;

        public SourceSasInput(String correctSas, String targetDeviceName) {
            this.correctSas = correctSas;
            this.targetDeviceName = targetDeviceName;
        }

        public SourceSasInput(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 2) {
                throw new DecodingException();
            }
            this.correctSas = encodedParts[0].decodeString();
            this.targetDeviceName = encodedParts[1].decodeString();
        }

        @Override
        public Step getStep() {
            return Step.SOURCE_SAS_INPUT;
        }

        @Override
        public Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(correctSas),
                    Encoded.of(targetDeviceName),
            };
        }
    }

    public static class TargetShowSas extends ObvTransferStep {
        public final String sas;
        public TargetShowSas(String sas) {
            this.sas = sas;
        }

        public TargetShowSas(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 1) {
                throw new DecodingException();
            }
            this.sas = encodedParts[0].decodeString();
        }

        @Override
        public Step getStep() {
            return Step.TARGET_SHOW_SAS;
        }

        @Override
        public Encoded[] getEncodedParts() {
            return new Encoded[]{
                    Encoded.of(sas),
            };
        }
    }

    public static class SourceSnapshotSent extends ObvTransferStep {
        public SourceSnapshotSent() {
        }

        public SourceSnapshotSent(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 0) {
                throw new DecodingException();
            }
        }

        @Override
        public Step getStep() {
            return Step.SOURCE_SNAPSHOT_SENT;
        }

        @Override
        public Encoded[] getEncodedParts() {
            return new Encoded[0];
        }
    }

    public static class TargetRequestsKeycloakAuthenticationProof extends ObvTransferStep {
        public final String keycloakServerUrl;
        public final String clientId;
        public final String fullSas;
        public final long sessionNumber;
        public final String clientSecret; // may be null

        public TargetRequestsKeycloakAuthenticationProof(String keycloakServerUrl, String clientId, String clientSecret, String fullSas, long sessionNumber) {
            this.keycloakServerUrl = keycloakServerUrl;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.fullSas = fullSas;
            this.sessionNumber = sessionNumber;
        }

        public TargetRequestsKeycloakAuthenticationProof(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 5 && encodedParts.length != 4) {
                throw new DecodingException();
            }
            this.keycloakServerUrl = encodedParts[0].decodeString();
            this.clientId = encodedParts[1].decodeString();
            this.fullSas = encodedParts[2].decodeString();
            this.sessionNumber = encodedParts[3].decodeLong();
            if (encodedParts.length == 5) {
                this.clientSecret = encodedParts[4].decodeString();
            } else {
                this.clientSecret = null;
            }
        }

        @Override
        public Step getStep() {
            return Step.TARGET_REQUESTS_KEYCLOAK_AUTHENTICATION_PROOF;
        }

        @Override
        public Encoded[] getEncodedParts() {
            if (clientSecret == null) {
                return new Encoded[]{
                        Encoded.of(keycloakServerUrl),
                        Encoded.of(clientId),
                        Encoded.of(fullSas),
                        Encoded.of(sessionNumber),
                };
            } else {
                return new Encoded[]{
                        Encoded.of(keycloakServerUrl),
                        Encoded.of(clientId),
                        Encoded.of(fullSas),
                        Encoded.of(sessionNumber),
                        Encoded.of(clientSecret),
                };
            }
        }
    }


    public static class TargetSnapshotReceived extends ObvTransferStep {
        public TargetSnapshotReceived() {
        }

        public TargetSnapshotReceived(Encoded[] encodedParts) throws DecodingException {
            if (encodedParts.length != 0) {
                throw new DecodingException();
            }
        }

        @Override
        public Step getStep() {
            return Step.TARGET_SNAPSHOT_RECEIVED;
        }

        @Override
        public Encoded[] getEncodedParts() {
            return new Encoded[0];
        }
    }
}

