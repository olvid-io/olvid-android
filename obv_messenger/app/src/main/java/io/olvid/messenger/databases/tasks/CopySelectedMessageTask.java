/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;

public class CopySelectedMessageTask implements Runnable {
    private final WeakReference<AppCompatActivity> activityWeakReference;
    private final Long selectedMessageId;


    public CopySelectedMessageTask(AppCompatActivity activity, Long selectedMessageId) {
        activityWeakReference = new WeakReference<>(activity);
        this.selectedMessageId = selectedMessageId;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        Message message = db.messageDao().get(selectedMessageId);

        if (message != null) {
            if (message.contentBody != null && message.contentBody.length() > 0) {
                final ClipData clipData = ClipData.newPlainText(App.getContext().getString(R.string.label_text_copied_from_olvid), message.contentBody);
                final AppCompatActivity activity = activityWeakReference.get();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clipData);
                            App.toast(R.string.toast_message_message_copied_to_clipboard, Toast.LENGTH_SHORT);
                        }
                    });
                }
            }
        }
    }
}
