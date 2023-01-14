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

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

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

    public PromptToDeleteContactTask(Context context, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, Runnable runOnDeleteButNotOnDowngrade) {
        this.context = context;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.bytesContactIdentity = bytesContactIdentity;
        this.runOnDelete = runOnDeleteButNotOnDowngrade;
    }

    @Override
    public void run() {
        AppDatabase db = AppDatabase.getInstance();
        final Contact contact = db.contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
        if (contact != null) {
            if (contact.oneToOne && contact.capabilityOneToOneContacts) {
                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                        .setTitle(R.string.dialog_title_remove_contact)
                        .setMessage(context.getString(R.string.dialog_message_remove_contact, contact.getCustomDisplayName()))
                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                            try {
                                AppSingleton.getEngine().downgradeOneToOneContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                App.toast(R.string.toast_message_contact_removed, Toast.LENGTH_SHORT);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                        .setNegativeButton(R.string.button_label_cancel, null);

                new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
            } else {
                int groupCount = db.contactGroupJoinDao().countContactGroups(bytesOwnedIdentity, bytesContactIdentity) + db.group2MemberDao().countContactGroups(bytesOwnedIdentity, bytesContactIdentity);
                if (groupCount == 0) {
                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_delete_user)
                            .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                                try {
                                    AppSingleton.getEngine().deleteContact(contact.bytesOwnedIdentity, contact.bytesContactIdentity);
                                    App.toast(R.string.toast_message_user_deleted, Toast.LENGTH_SHORT);
                                    if (runOnDelete != null) {
                                        runOnDelete.run();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            })
                            .setNegativeButton(R.string.button_label_cancel, null);

                    int pendingGroupCount = db.groupDao().getBytesGroupOwnerAndUidOfJoinedGroupWithPendingMember(contact.bytesOwnedIdentity, contact.bytesContactIdentity).size() + db.group2PendingMemberDao().countContactGroups(bytesOwnedIdentity, bytesContactIdentity);
                    if (pendingGroupCount == 0) {
                        builder.setMessage(context.getString(R.string.dialog_message_delete_user, contact.getCustomDisplayName()));
                    } else {
                        builder.setMessage(context.getResources().getQuantityString(R.plurals.dialog_message_delete_user_with_pending_groups, pendingGroupCount, contact.getCustomDisplayName(), pendingGroupCount));
                    }
                    new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
                } else {
                    SpannableStringBuilder ssb = new SpannableStringBuilder(context.getString(R.string.dialog_message_delete_user_impossible_start, contact.getCustomDisplayName()));
                    SpannableString spannableString = new SpannableString(context.getResources().getQuantityString(R.plurals.dialog_message_delete_user_impossible_count, groupCount, groupCount));
                    spannableString.setSpan(new StyleSpan(Typeface.BOLD), 0, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.append(spannableString);
                    ssb.append(context.getResources().getQuantityString(R.plurals.dialog_message_delete_user_impossible_end, groupCount));

                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_delete_user_impossible)
                            .setMessage(ssb)
                            .setPositiveButton(R.string.button_label_ok, null);
                    new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
                }
            }
        }
    }
}

