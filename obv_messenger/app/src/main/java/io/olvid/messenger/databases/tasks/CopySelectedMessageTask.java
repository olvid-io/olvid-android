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


import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Message;

public class CopySelectedMessageTask implements Runnable {
    private final WeakReference<FragmentActivity> activityWeakReference;
    private final Long selectedMessageId;
    private final boolean copyAttachments;


    public CopySelectedMessageTask(FragmentActivity activity, Long selectedMessageId, boolean copyAttachnments) {
        activityWeakReference = new WeakReference<>(activity);
        this.selectedMessageId = selectedMessageId;
        this.copyAttachments = copyAttachnments;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Message message = db.messageDao().get(selectedMessageId);

        if (message != null) {
            final ClipData clipData;

            if (!copyAttachments) {
                if (message.contentBody != null && message.contentBody.length() > 0) {
                    clipData = ClipData.newPlainText(App.getContext().getString(R.string.label_text_copied_from_olvid), message.contentBody);
                } else {
                    clipData = null;
                }
            } else {
                List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses = AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getCompleteFylesAndStatusForMessageSyncWithoutLinkPreview(selectedMessageId);
                if (fyleAndStatuses.size() > 0) {
                    List<ClipData.Item> clipItems = new ArrayList<>();
                    List<String> mimeTypes = new ArrayList<>();
                    if (message.contentBody != null && message.contentBody.length() > 0) {
                        clipItems.add(new ClipData.Item(message.contentBody));
                        mimeTypes.add("text/plain");
                    }
                    for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : fyleAndStatuses) {
                        clipItems.add(new ClipData.Item(fyleAndStatus.getContentUri()));
                        mimeTypes.add(fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType());
                    }
                    ClipDescription clipDescription = new ClipDescription(App.getContext().getString(R.string.label_message_copied_from_olvid), mimeTypes.toArray(new String[0]));
                    clipData = new ClipData(clipDescription, clipItems.get(0));
                    for (ClipData.Item item : clipItems.subList(1, clipItems.size())) {
                        clipData.addItem(item);
                    }
                } else {
                    if (message.contentBody != null && message.contentBody.length() > 0) {
                        clipData = ClipData.newPlainText(App.getContext().getString(R.string.label_text_copied_from_olvid), message.contentBody);
                    } else {
                        clipData = null;
                    }
                }
            }


            if (clipData != null) {
                final FragmentActivity activity = activityWeakReference.get();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clipData);
                            if (copyAttachments) {
                                App.toast(R.string.toast_message_complete_message_copied_to_clipboard, Toast.LENGTH_SHORT);
                            } else {
                                App.toast(R.string.toast_message_message_text_copied_to_clipboard, Toast.LENGTH_SHORT);
                            }
                        }
                    });
                }
            }
        }
    }
}
