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

package io.olvid.messenger;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import io.olvid.messenger.fragments.QRCodeScannerFragment;

public class QRCodeImageAnalyzer implements ImageAnalysis.Analyzer {
    private final QRCodeScannerFragment.ResultHandler resultHandler;
    private final MultiFormatReader reader;

    boolean stop = false;

    public QRCodeImageAnalyzer(QRCodeScannerFragment.ResultHandler resultHandler) {
        this.resultHandler = resultHandler;

        this.reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        this.reader.setHints(hints);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        if (stop) {
            image.close();
            return;
        }
        // image is YUV --> first plane is luminance
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer data = plane.getBuffer();
        data.rewind();
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        int width = plane.getRowStride()/plane.getPixelStride();
        int height = bytes.length * plane.getPixelStride()/plane.getRowStride();
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                bytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result result = reader.decode(bitmap);
            if (resultHandler != null) {
                if (resultHandler.handleResult(result.getText())) {
                    stop = true;
                } else {
                    Thread.sleep(1000);
                }
            }
        } catch (NotFoundException | InterruptedException e) {
            // nothing to do
        }
        image.close();
    }
}
