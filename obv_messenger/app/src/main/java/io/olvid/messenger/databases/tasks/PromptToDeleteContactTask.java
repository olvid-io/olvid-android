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

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;

public class PromptToDeleteContactTask implements Runnable {
    private final Context context;
    private final byte[] bytesOwnedIdentity;
    private final byte[] bytesContactIdentity;
    private final Runnable runOnDelete;

    public PromptToDeleteContactTask(Context context, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, Runnable runOnDelete) {
        this.context = context;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
        this.runOnDelete = runOnDelete;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        final Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
        if (contact != null) {
            if (db.contactGroupJoinDao().countContactGroups(bytesOwnedIdentity, bytesContactIdentity) == 0) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_delete_contact)
                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                            try {
                                AppSingleton.getEngine().deleteContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                App.toast(R.string.toast_message_contact_deleted, Toast.LENGTH_SHORT);
                                if (runOnDelete != null) {
                                    new Handler(Looper.getMainLooper()).post(runOnDelete);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);

                List<byte[]> bytesGroupOwnerAndUidList = AppDatabase.getInstance().groupDao().getBytesGroupOwnerAndUidOfJoinedGroupWithPendingMember(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                if (bytesGroupOwnerAndUidList.size() == 0) {
                    builder.setMessage(context.getString(R.string.dialog_message_delete_contact, contact.getCustomDisplayName()));
                } else {
                    builder.setMessage(context.getString(R.string.dialog_message_delete_contact_with_pending_groups, contact.getCustomDisplayName(), bytesGroupOwnerAndUidList.size()));
                }
                new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
            } else {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_delete_contact_impossible)
                        .setMessage(context.getString(R.string.dialog_message_delete_contact_impossible, contact.getCustomDisplayName()))
                        .setPositiveButton(R.string.button_label_ok, null);
                new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
            }
        }
    }
}

