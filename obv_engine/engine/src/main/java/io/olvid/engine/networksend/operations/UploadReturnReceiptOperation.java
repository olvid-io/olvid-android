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

package io.olvid.engine.networksend.operations;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.Seed;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.datatypes.containers.IdentityAndLong;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.metamanager.IdentityDelegate;
import io.olvid.engine.networksend.coordinators.SendReturnReceiptCoordinator;
import io.olvid.engine.networksend.databases.ReturnReceipt;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;


public class UploadReturnReceiptOperation extends Operation {
    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final String server;
    private final SendReturnReceiptCoordinator.ReturnReceiptBatchProvider returnReceiptBatchProvider;
    private final List<IdentityAndLong> identityInactiveReturnReceiptIds;
    private IdentityAndLong[] returnReceiptOwnedIdentitiesAndIds;

    public UploadReturnReceiptOperation(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, String server, SendReturnReceiptCoordinator.ReturnReceiptBatchProvider returnReceiptBatchProvider , OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(computeUniqueUid(server), onFinishCallback, onCancelCallback);
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.server = server;
        this.returnReceiptBatchProvider = returnReceiptBatchProvider;
        this.identityInactiveReturnReceiptIds = new ArrayList<>();
    }

    private static UID computeUniqueUid(String server) {
        Hash sha256 = Suite.getHash(Hash.SHA256);
        return new UID(sha256.digest(server.getBytes(StandardCharsets.UTF_8)));
    }

    public String getServer() {
        return server;
    }

    public IdentityAndLong[] getReturnReceiptOwnedIdentitiesAndIds() {
        return returnReceiptOwnedIdentitiesAndIds;
    }

    public List<IdentityAndLong> getIdentityInactiveReturnReceiptIds() {
        return identityInactiveReturnReceiptIds;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            try {
                returnReceiptOwnedIdentitiesAndIds = returnReceiptBatchProvider.getBatchOFReturnReceiptIds();
                List<ReturnReceiptAndEncryptedPayload> returnReceiptAndEncryptedPayloads = new ArrayList<>();

                Logger.d("UploadReturnReceiptOperation uploading a batch of " + returnReceiptOwnedIdentitiesAndIds.length);

                HashMap<Identity, List<Long>> returnReceiptIdsByIdentity = new HashMap<>();
                for (IdentityAndLong identityAndUid : returnReceiptOwnedIdentitiesAndIds) {
                    List<Long> list = returnReceiptIdsByIdentity.get(identityAndUid.identity);
                    if (list == null) {
                        list = new ArrayList<>();
                        returnReceiptIdsByIdentity.put(identityAndUid.identity, list);
                    }
                    list.add(identityAndUid.lng);
                }

                for (Map.Entry<Identity, List<Long>> entry : returnReceiptIdsByIdentity.entrySet()) {
                    Identity ownedIdentity = entry.getKey();
                    List<Long> returnReceiptIds = entry.getValue();
                    // we need to block sending return receipts for any inactive ownedIdentity
                    if (!sendManagerSession.identityDelegate.isActiveOwnedIdentity(sendManagerSession.session, ownedIdentity)) {
                        for (Long returnReceiptId : returnReceiptIds) {
                            identityInactiveReturnReceiptIds.add(new IdentityAndLong(ownedIdentity, returnReceiptId));
                        }
                    } else {
                        ReturnReceipt[] returnReceipts = ReturnReceipt.getMany(sendManagerSession, returnReceiptIds.toArray(new Long[0]));
                        for (ReturnReceipt returnReceipt : returnReceipts) {
                            // compute the encryptedPayload
                            final Encoded payload;
                            if (returnReceipt.getAttachmentNumber() == null) {
                                payload = Encoded.of(new Encoded[]{
                                        Encoded.of(returnReceipt.getOwnedIdentity()),
                                        Encoded.of(returnReceipt.getStatus()),
                                });
                            } else {
                                payload = Encoded.of(new Encoded[]{
                                        Encoded.of(returnReceipt.getOwnedIdentity()),
                                        Encoded.of(returnReceipt.getStatus()),
                                        Encoded.of(returnReceipt.getAttachmentNumber()),
                                });
                            }

                            Seed prngSeed = sendManagerSession.identityDelegate.getDeterministicSeedForOwnedIdentity(ownedIdentity, returnReceipt.getNonce(), IdentityDelegate.DeterministicSeedContext.ENCRYPT_RETURN_RECEIPT);
                            PRNG prng = Suite.getPRNG(PRNG.PRNG_HMAC_SHA256, prngSeed);

                            AuthEnc authEnc = Suite.getAuthEnc(returnReceipt.getKey());
                            EncryptedBytes encryptedPayload = authEnc.encrypt(returnReceipt.getKey(), payload.getBytes(), prng);

                            returnReceiptAndEncryptedPayloads.add(new ReturnReceiptAndEncryptedPayload(returnReceipt, encryptedPayload));
                        }
                    }
                }

                if (cancelWasRequested()) {
                    return;
                }


                UploadReturnReceiptServerMethod serverMethod = new UploadReturnReceiptServerMethod(server, returnReceiptAndEncryptedPayloads);
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(true);

                sendManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        for (ReturnReceiptAndEncryptedPayload returnReceiptAndEncryptedPayload : returnReceiptAndEncryptedPayloads) {
                            returnReceiptAndEncryptedPayload.returnReceipt.delete();
                        }

                        finished = true;
                        return;
                    default:
                        cancel(null);
                }
            } catch (Exception e) {
                Logger.x(e);
                sendManagerSession.session.rollback();
            } finally {
                if (finished) {
                    sendManagerSession.session.commit();
                    setFinished();
                } else {
                    if (hasNoReasonForCancel()) {
                        cancel(null);
                    }
                    processCancel();
                }
            }
        } catch (SQLException e) {
            Logger.x(e);
            cancel(null);
            processCancel();
        }
    }
}


class UploadReturnReceiptServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/uploadReturnReceipt";

    private final String server;
    private final List<ReturnReceiptAndEncryptedPayload> returnReceiptAndEncryptedPayloads;

    public UploadReturnReceiptServerMethod(String server, List<ReturnReceiptAndEncryptedPayload> returnReceiptAndEncryptedPayloads) {
        this.server = server;
        this.returnReceiptAndEncryptedPayloads = returnReceiptAndEncryptedPayloads;
    }

    @Override
    protected String getServer() {
        return server;
    }

    @Override
    protected String getServerMethod() {
        return SERVER_METHOD_PATH;
    }

    @Override
    protected byte[] getDataToSend() {
        List<Encoded> encodeds = new ArrayList<>();
        for (ReturnReceiptAndEncryptedPayload returnReceiptAndEncryptedPayload : returnReceiptAndEncryptedPayloads) {
            encodeds.add(Encoded.of(new Encoded[]{
                    Encoded.of(returnReceiptAndEncryptedPayload.returnReceipt.getContactIdentity()),
                    Encoded.of(returnReceiptAndEncryptedPayload.returnReceipt.getContactDeviceUids()),
                    Encoded.of(returnReceiptAndEncryptedPayload.returnReceipt.getNonce()),
                    Encoded.of(returnReceiptAndEncryptedPayload.encryptedPayload)
            }));
        }
        return Encoded.of(encodeds.toArray(new Encoded[0])).getBytes();
    }

    @Override
    protected void parseReceivedData(Encoded[] receivedData) {
        // nothing to parse here
    }

    @Override
    protected boolean isActiveIdentityRequired() {
        return true;
    }
}

class ReturnReceiptAndEncryptedPayload {
    final ReturnReceipt returnReceipt;
    final EncryptedBytes encryptedPayload;

    public ReturnReceiptAndEncryptedPayload(ReturnReceipt returnReceipt, EncryptedBytes encryptedPayload) {
        this.returnReceipt = returnReceipt;
        this.encryptedPayload = encryptedPayload;
    }
}