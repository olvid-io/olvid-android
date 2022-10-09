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

package io.olvid.messenger.databases.tasks;


import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;

public class SaveMultipleAttachmentsTask implements Runnable {
    private final Context context;
    private final Uri folderUri; // already validated Uri, no need to validate it again here
    @Nullable private final Long messageId;
    @Nullable private final ArrayList<FyleMessageJoinWithStatusDao.FyleAndStatus> selectedFyleAndStatuses;

    public SaveMultipleAttachmentsTask(Context context, Uri folderUri, long messageId) {
        this.context = context;
        this.folderUri = folderUri;
        this.messageId = messageId;
        this.selectedFyleAndStatuses = null;
    }

    public SaveMultipleAttachmentsTask(Context context, Uri folderUri, @NonNull ArrayList<FyleMessageJoinWithStatusDao.FyleAndStatus> selectedFyleAndStatuses) {
        this.context = context;
        this.folderUri = folderUri;
        this.messageId = null;
        this.selectedFyleAndStatuses = selectedFyleAndStatuses;
    }

    @Override
    public void run() {
        int filesSaved = 0;
        int filesToSave = 0;
        int incompleteFiles = 0;

        final List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatusesToSave;
        if (messageId != null) {
            fyleAndStatusesToSave = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getFylesAndStatusForMessageSync(messageId);
        } else if (selectedFyleAndStatuses != null) {
            fyleAndStatusesToSave = selectedFyleAndStatuses;
        } else {
            return;
        }

        DocumentFile folder = DocumentFile.fromTreeUri(context, folderUri);

        if (folder != null) {
            for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleMessageJoinWithStatus : fyleAndStatusesToSave) {
                if (!fyleMessageJoinWithStatus.fyle.isComplete()) {
                    incompleteFiles++;
                    continue;
                }
                filesToSave++;

                DocumentFile fileToWriteTo = folder.createFile(fyleMessageJoinWithStatus.fyleMessageJoinWithStatus.getNonNullMimeType(), fyleMessageJoinWithStatus.fyleMessageJoinWithStatus.fileName);
                if (fileToWriteTo != null) {
                    try (OutputStream os = context.getContentResolver().openOutputStream(fileToWriteTo.getUri())) {
                        if (os == null) {
                            throw new Exception("Unable to write to provided Uri");
                        }
                        // attachment was saved --> mark is as opened
                        fyleMessageJoinWithStatus.fyleMessageJoinWithStatus.markAsOpened();

                        try (FileInputStream fis = new FileInputStream(App.absolutePathFromRelative(fyleMessageJoinWithStatus.fyle.filePath))) {
                            byte[] buffer = new byte[262_144];
                            int c;
                            while ((c = fis.read(buffer)) != -1) {
                                os.write(buffer, 0, c);
                            }
                        }
                        filesSaved++;
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (filesSaved == 0 && filesToSave != 0) {
            App.toast(R.string.toast_message_unable_to_write_to_folder, Toast.LENGTH_SHORT);
        } else if (filesSaved + incompleteFiles < filesToSave) {
            App.toast(R.string.toast_message_some_attachments_could_not_be_saved, Toast.LENGTH_SHORT);
        } else if (incompleteFiles != 0) {
            App.toast(R.string.toast_message_some_incomplete_attachments_not_saved, Toast.LENGTH_LONG);
        } else {
            App.toast(R.string.toast_message_all_attachments_saved, Toast.LENGTH_SHORT);
        }
    }
}
