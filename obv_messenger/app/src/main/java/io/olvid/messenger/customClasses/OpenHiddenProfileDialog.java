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

package io.olvid.messenger.customClasses;


import android.content.DialogInterface;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;
import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.OwnedIdentityDao;
import io.olvid.messenger.settings.SettingsActivity;

public abstract class OpenHiddenProfileDialog {
    @NonNull
    private final AlertDialog dialog;
    @Nullable
    private List<OwnedIdentityDao.OwnedIdentityPasswordAndSalt> ownedIdentityPasswordAndSalts;

    public OpenHiddenProfileDialog(@NonNull FragmentActivity activity) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_view_open_hidden_profile, null);
        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        String message = getAdditionalMessage();
        if (message != null) {
            messageTextView.setText(message);
            messageTextView.setVisibility(View.VISIBLE);
        }
        TextInputEditText passwordEditText = dialogView.findViewById(R.id.password_text_view);
        passwordEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    selectIdentityFromPassword(s.toString());
                }
            }
        });


        AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                .setView(dialogView)
                .setNegativeButton(R.string.button_label_cancel, null);

        dialog = builder.create();
        dialog.setOnShowListener(d -> {
            passwordEditText.requestFocus();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        dialog.setOnDismissListener((DialogInterface d) -> onDismissCallback());
        dialog.show();

        ownedIdentityPasswordAndSalts = null;

        App.runThread(() -> {
            ownedIdentityPasswordAndSalts = AppDatabase.getInstance().ownedIdentityDao().getHiddenIdentityPasswordsAndSalts();
            activity.runOnUiThread(() -> {
                if (passwordEditText.getText() != null) {
                    selectIdentityFromPassword(passwordEditText.getText().toString());
                }
            });
        });
    }

    @Nullable
    protected String getAdditionalMessage() {
        return null;
    }

    private void selectIdentityFromPassword(String password) {
        if (ownedIdentityPasswordAndSalts == null || password == null || password.length() == 0) {
            return;
        }
        for (OwnedIdentityDao.OwnedIdentityPasswordAndSalt ownedIdentityPasswordAndSalt : ownedIdentityPasswordAndSalts) {
            try {
                byte[] hash = SettingsActivity.computePINHash(password, ownedIdentityPasswordAndSalt.unlock_salt);
                if (Arrays.equals(ownedIdentityPasswordAndSalt.unlock_password, hash)) {
                    onHiddenIdentityPasswordEntered(dialog, ownedIdentityPasswordAndSalt.bytes_owned_identity);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    protected void onDismissCallback() {
        // no onDismissCallback by default, but may be overridden
        // if so, remember to remove onDismiss before dismissing in onHiddenIdentityPasswordEntered
    }

    protected abstract void onHiddenIdentityPasswordEntered(AlertDialog dialog, byte[] byteOwnedIdentity);
}
