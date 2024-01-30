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

package io.olvid.messenger.services;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Fyle;
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus;
import io.olvid.messenger.databases.entity.Message;


public class FyleContentProvider extends ContentProvider {
    Pattern uriPattern;

    public static final String DISPLAY_NAME = OpenableColumns.DISPLAY_NAME;
    public static final String SIZE = OpenableColumns.SIZE;

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Matcher matcher = uriPattern.matcher(uri.toString());
        if (matcher.matches()) {
            //noinspection ConstantConditions
            String sha256String = matcher.group(1).toUpperCase(Locale.ENGLISH);
            byte[] sha256 = Logger.fromHexString(sha256String);
            //noinspection ConstantConditions
            long messageId = Long.parseLong(matcher.group(2));
            Fyle fyle = AppDatabase.getInstance().fyleDao().getBySha256(sha256);
            Message message = AppDatabase.getInstance().messageDao().get(messageId);
            if (fyle != null && message != null) {
                if (fyle.isComplete()) {
                    //noinspection ConstantConditions
                    File providedFile = new File(App.absolutePathFromRelative(fyle.filePath));
                    return ParcelFileDescriptor.open(providedFile, ParcelFileDescriptor.MODE_READ_ONLY);
                } else {
                    FyleMessageJoinWithStatus fyleMessageJoinWithStatus = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().get(fyle.id, messageId);
                    if (fyleMessageJoinWithStatus != null) {
                        File providedFile = new File(fyleMessageJoinWithStatus.getAbsoluteFilePath());
                        return ParcelFileDescriptor.open(providedFile, ParcelFileDescriptor.MODE_READ_ONLY);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean onCreate() {
        //noinspection RedundantEscapeInRegexReplacement
        uriPattern = Pattern.compile(BuildConfig.CONTENT_PROVIDER_URI_PREFIX.replaceAll("[.]","\\.") + "([0-9A-Fa-f]{64})" + "/" + "([0-9]+)" + "/" + "([0-9A-Fa-f]{32})");
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (projection == null) {
            projection = new String[]{
                    DISPLAY_NAME,
                    SIZE,
            };
        }
        MatrixCursor cursor = new MatrixCursor(projection, 0);
        Matcher matcher = uriPattern.matcher(uri.toString());
        if (matcher.matches()) {
            //noinspection ConstantConditions
            String sha256String = matcher.group(1).toUpperCase(Locale.ENGLISH);
            byte[] sha256 = Logger.fromHexString(sha256String);
            //noinspection ConstantConditions
            long messageId = Long.parseLong(matcher.group(2));
            Fyle fyle = AppDatabase.getInstance().fyleDao().getBySha256(sha256);
            if (fyle != null) {
                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().get(fyle.id, messageId);
                if (fyleMessageJoinWithStatus != null) {
                    Object[] objects = new Object[projection.length];
                    for (int i = 0; i < projection.length; i++) {
                        switch (projection[i]) {
                            case DISPLAY_NAME:
                                objects[i] = fyleMessageJoinWithStatus.fileName;
                                break;
                            case SIZE:
                                objects[i] = fyleMessageJoinWithStatus.size;
                                break;
                            default:
                                objects[i] = null;
                        }
                    }
                    cursor.addRow(objects);
                }
            }
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        Matcher matcher = uriPattern.matcher(uri.toString());
        if (matcher.matches()) {
            //noinspection ConstantConditions
            String sha256String = matcher.group(1).toUpperCase(Locale.ENGLISH);
            byte[] sha256 = Logger.fromHexString(sha256String);
            //noinspection ConstantConditions
            long messageId = Long.parseLong(matcher.group(2));
            Fyle fyle = AppDatabase.getInstance().fyleDao().getBySha256(sha256);
            if (fyle != null) {
                FyleMessageJoinWithStatus fyleMessageJoinWithStatus = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().get(fyle.id, messageId);
                if (fyleMessageJoinWithStatus != null) {
                    return fyleMessageJoinWithStatus.getNonNullMimeType();
                }
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
