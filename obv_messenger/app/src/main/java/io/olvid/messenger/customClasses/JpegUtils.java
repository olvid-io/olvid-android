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

package io.olvid.messenger.customClasses;


import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.olvid.engine.Logger;

public class JpegUtils {

    private static final byte MARKER = (byte) 0xff;
    private static final byte MARKER_SOI = (byte) 0xd8;
    private static final byte MARKER_SOS = (byte) 0xda;
    private static final byte MARKER_APP1 = (byte) 0xe1;
    private static final byte MARKER_COM = (byte) 0xfe;
    private static final byte MARKER_EOI = (byte) 0xd9;
    private static final byte MARKER_APP0 = (byte) 0xe0;
    private static final byte MARKER_APP2 = (byte) 0xe2;
    private static final byte MARKER_APP3 = (byte) 0xe3;
    private static final byte MARKER_APP4 = (byte) 0xe4;
    private static final byte MARKER_APP5 = (byte) 0xe5;
    private static final byte MARKER_APP6 = (byte) 0xe6;
    private static final byte MARKER_APP7 = (byte) 0xe7;
    private static final byte MARKER_APP8 = (byte) 0xe8;
    private static final byte MARKER_APP9 = (byte) 0xe9;
    private static final byte MARKER_APP10 = (byte) 0xea;
    private static final byte MARKER_APP11 = (byte) 0xeb;
    private static final byte MARKER_APP12 = (byte) 0xec;
    private static final byte MARKER_APP13 = (byte) 0xed;
    private static final byte MARKER_APP14 = (byte) 0xee;
    private static final byte MARKER_APP15 = (byte) 0xef;


    public static void copyJpegWithoutAttributes(InputStream inputStream, OutputStream outputStream) throws IOException, ICCProfileFoundException {
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        if (dataInputStream.readByte() != MARKER ||
                dataInputStream.readByte() != MARKER_SOI) {
            throw new IOException("Invalid marker");
        }
        dataOutputStream.writeByte(MARKER);
        dataOutputStream.writeByte(MARKER_SOI);

        byte[] bytes = new byte[8_192];

        while (true) {
            byte marker = dataInputStream.readByte();
            if (marker != MARKER) {
                throw new IOException("Invalid marker");
            }
            marker = dataInputStream.readByte();
            switch (marker) {
                case MARKER_APP2:
                    // we comment for now as Android JPEG compression introduces a SKIA ICC profile that no one else understands...
//                    throw new ICCProfileFoundException();
                case MARKER_COM:
                case MARKER_APP0:
                case MARKER_APP1:
                case MARKER_APP3:
                case MARKER_APP4:
                case MARKER_APP5:
                case MARKER_APP6:
                case MARKER_APP7:
                case MARKER_APP8:
                case MARKER_APP9:
                case MARKER_APP10:
                case MARKER_APP11:
                case MARKER_APP12:
                case MARKER_APP13:
                case MARKER_APP14:
                case MARKER_APP15: {
                    int length = dataInputStream.readUnsignedShort() - 2;
                    if (length < 0) {
                        throw new IOException("Invalid length");
                    }
                    if (dataInputStream.skipBytes(length) != length) {
                        throw new IOException("Invalid length");
                    }
                    break;
                }
                case MARKER_EOI:
                case MARKER_SOS: {
                    dataOutputStream.writeByte(MARKER);
                    dataOutputStream.writeByte(marker);
                    // Copy all the remaining data
                    int c;
                    while ((c = dataInputStream.read(bytes)) != -1) {
                        dataOutputStream.write(bytes, 0, c);
                    }
                    return;
                }
                default: {
                    // Copy JPEG segment
                    dataOutputStream.writeByte(MARKER);
                    dataOutputStream.writeByte(marker);
                    int length = dataInputStream.readUnsignedShort();
                    dataOutputStream.writeShort(length);
                    length -= 2;
                    if (length < 0) {
                        throw new IOException("Invalid length");
                    }
                    int c;
                    while (length > 0 && (c = dataInputStream.read(bytes, 0, Math.min(length, bytes.length))) >= 0) {
                        dataOutputStream.write(bytes, 0, c);
                        length -= c;
                    }
                    break;
                }
            }
        }
    }

    public static class ICCProfileFoundException extends Exception {}


    public static void resize(File imageFile, int resolution) {
        // Get the dimensions of the View
        int targetW = resolution;
        int targetH = (targetW / 9) * 16;
        int photoW = 0;
        int photoH = 0;
        boolean recompress = true;
        // Get the dimensions of the bitmap and check its orientation
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        if (bitmap.getHeight() < bitmap.getWidth()) {
            int tmp = targetH;
            targetH = targetW;
            targetW = tmp;
        }

        if (bitmap.getHeight() <= targetH && bitmap.getWidth() <= targetW) {
            recompress = false;
        } else {
            if (bitmap.getHeight() / (float) targetH > bitmap.getWidth() / (float) targetW) {
                photoH = targetH;
                photoW = (int) (bitmap.getWidth() * (float) targetH / bitmap.getHeight());
            } else {
                photoW = targetW;
                photoH = (int) (bitmap.getHeight() * (float) targetW / bitmap.getWidth());
            }
        }

        if (recompress) {
            bitmap = Bitmap.createScaledBitmap(bitmap, photoW, photoH, true);
            try {
                ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
                int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                bitmap = PreviewUtils.rotateBitmap(bitmap, orientation);
            } catch (IOException e) {
                Logger.d("Error creating ExifInterface for file " + imageFile.getAbsolutePath());
            }
            try (FileOutputStream fos = new FileOutputStream(imageFile.getAbsolutePath(), false)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void recompress(ContentResolver contentResolver, Uri inputUri, File outputFile) throws IOException {
        try (InputStream in = contentResolver.openInputStream(inputUri);
             InputStream inExif = contentResolver.openInputStream(inputUri);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            if (in == null || inExif == null) {
                throw new IOException();
            }
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            try {
                ExifInterface exifInterface = new ExifInterface(inExif);
                int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                bitmap = PreviewUtils.rotateBitmap(bitmap, orientation);
            } catch (IOException e) {
                // rotation failed --> nothing to do
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
        }
    }

    public static void recompress(File inputFile, File outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            Bitmap bitmap = BitmapFactory.decodeFile(inputFile.getAbsolutePath());
            try {
                ExifInterface exifInterface = new ExifInterface(inputFile);
                int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                bitmap = PreviewUtils.rotateBitmap(bitmap, orientation);
            } catch (IOException e) {
                // rotation failed --> nothing to do
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
        }
    }

}
