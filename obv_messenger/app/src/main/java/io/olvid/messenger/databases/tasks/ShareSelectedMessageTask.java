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


import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.Message;

public class ShareSelectedMessageTask implements Runnable {
    private final WeakReference<FragmentActivity> activityWeakReference;
    private final Long selectedMessageId;


    public ShareSelectedMessageTask(FragmentActivity activity, Long selectedMessageId) {
        activityWeakReference = new WeakReference<>(activity);
        this.selectedMessageId = selectedMessageId;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Message message = db.messageDao().get(selectedMessageId);

        if (message != null) {
            if ((message.messageType != Message.TYPE_OUTBOUND_MESSAGE
                    && message.messageType != Message.TYPE_INBOUND_MESSAGE)
                    || message.wipeStatus != Message.WIPE_STATUS_NONE
                    || message.limitedVisibility) {
                return;
            }

            Intent intent = new Intent();
            String mimeType = null;
            boolean multiple = ((message.contentBody != null && message.contentBody.length() > 0) && (message.totalAttachmentCount > 0)) || (message.totalAttachmentCount > 1);
            if (message.contentBody != null && message.contentBody.length() > 0) {
                intent.putExtra(Intent.EXTRA_TEXT, message.contentBody);
                mimeType = "text/plain";
            }
            if (message.hasAttachments()) {
                List<FyleMessageJoinWithStatusDao.FyleAndStatus> fyleAndStatuses = db.fyleMessageJoinWithStatusDao().getCompleteFylesAndStatusForMessageSyncWithoutLinkPreview(message.id);
                if (multiple) {
                    ArrayList<Uri> uris = new ArrayList<>(fyleAndStatuses.size());
                    for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : fyleAndStatuses) {
                        uris.add(fyleAndStatus.getContentUri());
                        mimeType = mimeGcd(mimeType, fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType());
                    }
                    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                } else {
                    FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus = fyleAndStatuses.get(0);
                    intent.putExtra(Intent.EXTRA_STREAM, fyleAndStatus.getContentUri());
                    mimeType = fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType();
                }
            }
            if (multiple) {
                intent.setAction(Intent.ACTION_SEND_MULTIPLE);
            } else {
                intent.setAction(Intent.ACTION_SEND);
            }
            intent.setType(mimeType);
            FragmentActivity activity = activityWeakReference.get();
            if (activity != null) {
                activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.title_sharing_chooser)));
            }
        }
    }

    private static String mimeGcd(String mimeType1, String mimeType2) {
        if (mimeType1 == null) {
            return mimeType2;
        }
        if (mimeType2 == null) {
            return mimeType1;
        }
        if (mimeType1.equals(mimeType2)) {
            return mimeType1;
        }
        String prefix = mimeType1.split("/")[0];
        if (mimeType2.split("/")[0].equals(prefix)) {
            return prefix + "/*";
        }
        return "*/*";
    }
}
