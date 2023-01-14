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

package io.olvid.messenger.databases.tasks;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;

import io.olvid.engine.encoder.Encoded;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;

public class HandleMessageExtendedPayloadTask implements Runnable {
    private final byte[] bytesOwnedIdentity;
    private final byte[] messageIdentifier;
    private final byte[] extendedPayload;

    public static final int PREVIEW_SIZE_V0 = 40;

    public HandleMessageExtendedPayloadTask(byte[] bytesOwnedIdentity, byte[] messageIdentifier, byte[] extendedPayload) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.messageIdentifier = messageIdentifier;
        this.extendedPayload = extendedPayload;
    }

    @Override
    public void run() {
        try {
            Encoded[] encodeds = new Encoded(extendedPayload).decodeList();
            int version = (int) encodeds[0].decodeLong();
            switch (version) {
                case 0:
                    handle_v0(encodeds);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handle_v0(Encoded[] encodeds) throws Exception {
        if (encodeds.length != 3) {
            throw new Exception("Bad number of encoded elements");
        }

        AppDatabase db = AppDatabase.getInstance();

        Encoded[] encodedAttachmentNumbers = encodeds[1].decodeList();
        byte[] assembledJpegBytes = encodeds[2].decodeBytes();

        Bitmap assembled = null;
        int rowSize = 0;

        for (int i=0; i<encodedAttachmentNumbers.length; i++) {
            int attachmentNumber = (int) encodedAttachmentNumbers[i].decodeLong();
            FyleMessageJoinWithStatus fyleMessageJoinWithStatus = db.fyleMessageJoinWithStatusDao().getByEngineIdentifierAndNumber(bytesOwnedIdentity, messageIdentifier, attachmentNumber);

            if (fyleMessageJoinWithStatus == null
                    || fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_COMPLETE
                    || fyleMessageJoinWithStatus.miniPreview != null) {
                continue;
            }

            // the attachment exists, is not complete yet and does not have a preview
            if (assembled == null) {
                assembled = BitmapFactory.decodeByteArray(assembledJpegBytes, 0, assembledJpegBytes.length);
                rowSize = (int) Math.ceil(Math.sqrt(encodedAttachmentNumbers.length));

                if (assembled.getWidth() != rowSize * PREVIEW_SIZE_V0
                        || assembled.getHeight() != ((encodedAttachmentNumbers.length - 1) / rowSize + 1) * PREVIEW_SIZE_V0) {
                    throw new Exception("Bad assembled bitmap size");
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(assembled, PREVIEW_SIZE_V0 * (i % rowSize), PREVIEW_SIZE_V0 * (i / rowSize), PREVIEW_SIZE_V0, PREVIEW_SIZE_V0);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);

            db.fyleMessageJoinWithStatusDao().updateMiniPreview(bytesOwnedIdentity, messageIdentifier, attachmentNumber, baos.toByteArray());
        }
    }
}
