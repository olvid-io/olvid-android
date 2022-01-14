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

package io.olvid.engine.networksend.operations;

import java.sql.SQLException;

import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.crypto.AuthEnc;
import io.olvid.engine.crypto.PRNG;
import io.olvid.engine.crypto.Suite;
import io.olvid.engine.datatypes.EncryptedBytes;
import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.Operation;
import io.olvid.engine.datatypes.ServerMethod;
import io.olvid.engine.datatypes.UID;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.networksend.databases.ReturnReceipt;
import io.olvid.engine.networksend.datatypes.SendManagerSession;
import io.olvid.engine.networksend.datatypes.SendManagerSessionFactory;


public class UploadReturnReceiptOperation extends Operation {


    public static final int RFC_RETURN_RECEIPT_NOT_FOUND = 1;
    public static final int RFC_IDENTITY_IS_INACTIVE = 2;

    private final SendManagerSessionFactory sendManagerSessionFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final Identity ownedIdentity;
    private final long returnReceiptId;
    private final PRNG prng;

    public UploadReturnReceiptOperation(SendManagerSessionFactory sendManagerSessionFactory, SSLSocketFactory sslSocketFactory, Identity ownedIdentity, long returnReceiptId, PRNG prng, OnFinishCallback onFinishCallback, OnCancelCallback onCancelCallback) {
        super(UID.fromLong(returnReceiptId), onFinishCallback, onCancelCallback);
        this.sendManagerSessionFactory = sendManagerSessionFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.ownedIdentity = ownedIdentity;
        this.returnReceiptId = returnReceiptId;
        this.prng = prng;
    }

    public Identity getOwnedIdentity() {
        return ownedIdentity;
    }

    public long getReturnReceiptId() {
        return returnReceiptId;
    }

    @Override
    public void doCancel() {
        // Nothing special to do on cancel
    }

    @Override
    public void doExecute() {
        boolean finished = false;
        ReturnReceipt returnReceipt;
        try (SendManagerSession sendManagerSession = sendManagerSessionFactory.getSession()) {
            try {
                returnReceipt = ReturnReceipt.get(sendManagerSession, returnReceiptId);

                if (returnReceipt == null) {
                    cancel(RFC_RETURN_RECEIPT_NOT_FOUND);
                    return;
                }
                if (cancelWasRequested()) {
                    return;
                }

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
                AuthEnc authEnc = Suite.getAuthEnc(returnReceipt.getKey());
                EncryptedBytes encryptedPayload = authEnc.encrypt(returnReceipt.getKey(), payload.getBytes(), prng);

                UploadReturnReceiptServerMethod serverMethod = new UploadReturnReceiptServerMethod(
                        returnReceipt.getContactIdentity().getServer(),
                        returnReceipt.getContactIdentity(),
                        returnReceipt.getContactDeviceUids(),
                        returnReceipt.getNonce(),
                        encryptedPayload);
                serverMethod.setSslSocketFactory(sslSocketFactory);

                byte returnStatus = serverMethod.execute(sendManagerSession.identityDelegate.isActiveOwnedIdentity(sendManagerSession.session, ownedIdentity));

                sendManagerSession.session.startTransaction();
                switch (returnStatus) {
                    case ServerMethod.OK:
                        returnReceipt.delete();
                        finished = true;
                        return;
                    case ServerMethod.IDENTITY_IS_NOT_ACTIVE:
                        cancel(RFC_IDENTITY_IS_INACTIVE);
                        return;
                    default:
                        cancel(null);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
            e.printStackTrace();
        }
    }
}


class UploadReturnReceiptServerMethod extends ServerMethod {
    private static final String SERVER_METHOD_PATH = "/uploadReturnReceipt";

    private final String server;
    private final Identity contactIdentity;
    private final UID[] contactDeviceUids;
    private final byte[] nonce;
    private final EncryptedBytes encryptedPayload;

    public UploadReturnReceiptServerMethod(String server, Identity contactIdentity, UID[] contactDeviceUids, byte[] nonce, EncryptedBytes encryptedPayload) {
        this.server = server;
        this.contactIdentity = contactIdentity;
        this.contactDeviceUids = contactDeviceUids;
        this.nonce = nonce;
        this.encryptedPayload = encryptedPayload;
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
        return Encoded.of(new Encoded[]{
                Encoded.of(contactIdentity),
                Encoded.of(contactDeviceUids),
                Encoded.of(nonce),
                Encoded.of(encryptedPayload),
        }).getBytes();
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
