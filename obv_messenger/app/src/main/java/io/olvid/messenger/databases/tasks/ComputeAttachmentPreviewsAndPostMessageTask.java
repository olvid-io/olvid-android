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
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.olvid.engine.encoder.Encoded;
import io.olvid.messenger.App;
import io.olvid.messenger.customClasses.JpegUtils;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.MessageRecipientInfo;

public class ComputeAttachmentPreviewsAndPostMessageTask implements Runnable {
    public static final int PREVIEW_SIZE = 40;
    private static final HashSet<Long> runningTaskMessageId = new HashSet<>();

    private final long messageId;
    @Nullable
    private final MessageRecipientInfo messageRecipientInfo; // non null when this is a repost for a specific contact
    private byte[] extendedPayload = null;

    public ComputeAttachmentPreviewsAndPostMessageTask(long messageId, @Nullable MessageRecipientInfo messageRecipientInfo) throws Exception {
        if (messageRecipientInfo == null) {
            synchronized (runningTaskMessageId) {
                if (runningTaskMessageId.contains(messageId)) {
                    throw new Exception("Already computing a previews for this message");
                }
                runningTaskMessageId.add(messageId);
            }
        }
        this.messageId = messageId;
        this.messageRecipientInfo = messageRecipientInfo;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        try {
            // first, mark the message as STATUS_COMPUTING_PREVIEW
            Message message = db.runInTransaction(() -> {
                Message transactionMessage = db.messageDao().get(messageId);
                if (transactionMessage == null) {
                    return null;
                }

                if (transactionMessage.status != Message.STATUS_UNPROCESSED
                        && transactionMessage.status != Message.STATUS_COMPUTING_PREVIEW // required for restart after fail
                        && messageRecipientInfo == null) {
                    // message is not in the right status
                    return null;
                }

                transactionMessage.status = Message.STATUS_COMPUTING_PREVIEW;
                db.messageDao().updateStatus(transactionMessage.id, transactionMessage.status);

                return transactionMessage;
            });

            if (message == null) {
                return;
            }

            extendedPayload = new byte[0];

            try {
                // compute the previews for fyles that work
                List<Integer> previewAttachmentNumbers = new ArrayList<>();
                List<Bitmap> previewBitmaps = new ArrayList<>();

                List<FyleMessageJoinWithStatusDao.FyleAndStatus> attachmentFyles = db.fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(messageId);
                int attachmentNumber = 0;
                for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : attachmentFyles) {
                    // stop computing previews after 25!
                    if (previewBitmaps.size() == 25) {
                        break;
                    }

                    if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType())) {
                        // actually compute the 40x40 previews
                        Bitmap bitmap = computePreview(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus);
                        if (bitmap != null) {
                            previewAttachmentNumbers.add(attachmentNumber);
                            previewBitmaps.add(bitmap);
                        }
                    }
                    attachmentNumber++;
                }


                if (previewBitmaps.size() != 0) {
                    // assemble the previews
                    int rowSize = (int) Math.ceil(Math.sqrt(previewBitmaps.size()));
                    Bitmap assembled = Bitmap.createBitmap(PREVIEW_SIZE * rowSize, PREVIEW_SIZE * ((previewBitmaps.size() - 1) / rowSize + 1), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(assembled);
                    Paint black = new Paint();
                    black.setColor(0xff000000);
                    canvas.drawPaint(black);
                    for (int i = 0; i < previewBitmaps.size(); i++) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || previewBitmaps.get(i).getConfig() != Bitmap.Config.HARDWARE) {
                            //noinspection IntegerDivisionInFloatingPointContext
                            canvas.drawBitmap(previewBitmaps.get(i), PREVIEW_SIZE * (i % rowSize), PREVIEW_SIZE * (i / rowSize), null);
                        } else {
                            //noinspection IntegerDivisionInFloatingPointContext
                            canvas.drawBitmap(previewBitmaps.get(i).copy(Bitmap.Config.ARGB_8888, false), PREVIEW_SIZE * (i % rowSize), PREVIEW_SIZE * (i / rowSize), null);
                        }
                    }

                    // encode the extended message
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    assembled.compress(Bitmap.CompressFormat.JPEG, 75, baos);

                    try {
                        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                        ByteArrayOutputStream reBaos = new ByteArrayOutputStream();
                        JpegUtils.copyJpegWithoutAttributes(bais, reBaos);
                        // if previous line did not throw, overwrite the original jpeg
                        baos.reset();
                        baos.write(reBaos.toByteArray());
                    } catch (IOException e) {
                        // do nothing, we failed to strip some header --> user probably still wants to send his image...
                    }

                    Encoded[] encodedAttachmentNumbers = new Encoded[previewAttachmentNumbers.size()];
                    for (int i = 0; i < encodedAttachmentNumbers.length; i++) {
                        encodedAttachmentNumbers[i] = Encoded.of(previewAttachmentNumbers.get(i));
                    }

                    Encoded[] encodeds = new Encoded[]{
                            Encoded.of(0), // format version of the extended message
                            Encoded.of(encodedAttachmentNumbers),
                            Encoded.of(baos.toByteArray()),
                    };

                    extendedPayload = Encoded.of(encodeds).getBytes();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // now post the message with the preview
            db.runInTransaction(() -> {
                Message transactionMessage = db.messageDao().get(messageId);
                if (transactionMessage == null) {
                    return;
                }
                if (transactionMessage.status != Message.STATUS_UNPROCESSED
                        && transactionMessage.status != Message.STATUS_COMPUTING_PREVIEW // required for restart after fail
                        && messageRecipientInfo == null) {
                    // message is not in the right status
                    return;
                }

                if (messageRecipientInfo == null) {
                    message.post(false, extendedPayload);
                } else {
                    message.repost(messageRecipientInfo, extendedPayload);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (messageRecipientInfo == null) {
                runningTaskMessageId.remove(messageId);
            }
        }
    }

    @Nullable
    private Bitmap computePreview(Fyle fyle, FyleMessageJoinWithStatus fyleMessageJoinWithStatus) {
        try {
            String filePath = App.absolutePathFromRelative(fyle.filePath);
            if (filePath == null || !new File(filePath).exists()) {
                return null;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && fyleMessageJoinWithStatus.getNonNullMimeType().startsWith("image/")) {
                try {
                    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(new File(filePath)), (ImageDecoder decoder, ImageDecoder.ImageInfo info, ImageDecoder.Source source) -> {
                        decoder.setMutableRequired(true);
                        if (info.getSize().getWidth() > info.getSize().getHeight()) {
                            int width = (PREVIEW_SIZE * info.getSize().getWidth()) / info.getSize().getHeight();
                            decoder.setTargetSize(width, PREVIEW_SIZE);
                            decoder.setCrop(new Rect((width - PREVIEW_SIZE) / 2, 0, (width + PREVIEW_SIZE) / 2, PREVIEW_SIZE));
                        } else {
                            int height = (PREVIEW_SIZE * info.getSize().getHeight()) / info.getSize().getWidth();
                            decoder.setTargetSize(PREVIEW_SIZE, height);
                            decoder.setCrop(new Rect(0, (height - PREVIEW_SIZE) / 2, PREVIEW_SIZE, (height + PREVIEW_SIZE) / 2));
                        }
                    });
                } catch (Exception e) {
                    // on API28 emulator, decoding sometimes fails --> do nothing and fallback to bitmap
                }
            }

            Bitmap bitmap = PreviewUtils.getBitmapPreview(fyle, fyleMessageJoinWithStatus, PREVIEW_SIZE);
            if (bitmap == null) {
                return null;
            }
            int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            Bitmap crop = Bitmap.createBitmap(
                    bitmap,
                    (bitmap.getWidth() - size) / 2,
                    (bitmap.getHeight() - size) / 2,
                    size,
                    size
            );
            return Bitmap.createScaledBitmap(crop, PREVIEW_SIZE, PREVIEW_SIZE, true);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
