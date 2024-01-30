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

package io.olvid.messenger;

import android.media.Image;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
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
import io.olvid.messenger.settings.SettingsActivity;

public class QRCodeImageAnalyzer implements ImageAnalysis.Analyzer {
    private final QRCodeScannerFragment.ResultHandler resultHandler;
    private final BarcodeScanner scanner;
    private final MultiFormatReader reader;

    boolean stop = false;
    boolean running = false;

    public QRCodeImageAnalyzer(QRCodeScannerFragment.ResultHandler resultHandler) {
        this.resultHandler = resultHandler;
        if (SettingsActivity.useLegacyZxingScanner()) {
            scanner = null;
            reader = new MultiFormatReader();
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
            reader.setHints(hints);
        } else {
            scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build());
            reader = null;
        }
    }

    @Override
    @OptIn(markerClass = ExperimentalGetImage.class)
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (!stop && !running) {
            if (scanner != null) {
                Image image = imageProxy.getImage();
                if (image != null) {
                    InputImage inputImage = InputImage.fromMediaImage(image, imageProxy.getImageInfo().getRotationDegrees());

                    running = true;
                    scanner.process(inputImage)
                            .addOnSuccessListener(barcodes -> {
                                if (resultHandler != null && barcodes != null && barcodes.size() > 0) {
                                    App.runThread(() -> {
                                        if (resultHandler.handleResult(barcodes.get(0).getRawValue())) {
                                            stop = true;
                                        } else {
                                            try {
                                                Thread.sleep(1000);
                                            } catch (InterruptedException ignored) {
                                            }
                                            running = false;
                                        }
                                    });
                                    return;
                                }
                                running = false;
                            })
                            .addOnFailureListener(command -> running = false)
                            .addOnCanceledListener(() -> running = false)
                            .addOnCompleteListener(task -> imageProxy.close());
                    return;
                }
            } else if (reader != null) {
                // image is YUV --> first plane is luminance
                ImageProxy.PlaneProxy plane = imageProxy.getPlanes()[0];
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
            }
        }
        imageProxy.close();
    }
}
