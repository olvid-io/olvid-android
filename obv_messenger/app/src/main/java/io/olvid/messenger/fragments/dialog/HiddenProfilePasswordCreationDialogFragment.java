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

package io.olvid.messenger.fragments.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.Arrays;
import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.OwnedIdentityDao;
import io.olvid.messenger.settings.SettingsActivity;


public class HiddenProfilePasswordCreationDialogFragment extends DialogFragment implements View.OnClickListener {
    private OnPasswordChosenCallback onPasswordSetCallback = null;

    private Button createPasswordButton;
    private EditText passwordFirstInput;
    private EditText passwordSecondInput;
    private TextView textPasswordErrorMessage;

    private List<OwnedIdentityDao.OwnedIdentityPasswordAndSalt> ownedIdentityPasswordAndSalts;

    public static HiddenProfilePasswordCreationDialogFragment newInstance() {
        return new HiddenProfilePasswordCreationDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }

        App.runThread(() -> ownedIdentityPasswordAndSalts = AppDatabase.getInstance().ownedIdentityDao().getHiddenIdentityPasswordsAndSalts());
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                passwordFirstInput.requestFocus();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_hidden_profile_password_creation, container, false);

        createPasswordButton = dialogView.findViewById(R.id.button_create_password);
        createPasswordButton.setOnClickListener(this);

        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);

        textPasswordErrorMessage = dialogView.findViewById(R.id.text_pin_error_message);

        passwordFirstInput = dialogView.findViewById(R.id.password_first_input);
        passwordSecondInput = dialogView.findViewById(R.id.password_second_input);
        TextWatcher textWatcher = new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                validatePINs();
            }
        };
        passwordFirstInput.addTextChangedListener(textWatcher);
        passwordSecondInput.addTextChangedListener(textWatcher);

        return dialogView;
    }

    private void validatePINs() {
        String firstPIN = passwordFirstInput.getText().toString();
        if (firstPIN.length() < 4) {
            if (firstPIN.length() != 0) {
                textPasswordErrorMessage.setText(R.string.error_text_password_too_short);
            } else {
                textPasswordErrorMessage.setText(null);
            }
            passwordSecondInput.setEnabled(false);
            createPasswordButton.setEnabled(false);
            return;
        } else {
            textPasswordErrorMessage.setText(null);
            passwordSecondInput.setEnabled(true);
        }

        String secondPIN = passwordSecondInput.getText().toString();
        if (!firstPIN.equals(secondPIN)) {
            if (secondPIN.length() > 0) {
                textPasswordErrorMessage.setText(R.string.error_text_password_mismatch);
            } else {
                textPasswordErrorMessage.setText(null);
            }
            createPasswordButton.setEnabled(false);
        } else {
            textPasswordErrorMessage.setText(null);
            createPasswordButton.setEnabled(true);
        }
    }

    public void setOnPasswordSetCallback(OnPasswordChosenCallback callback) {
        this.onPasswordSetCallback = callback;
    }


    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_create_password) {
            String password = passwordFirstInput.getText().toString();
            String passwordConfirmation = passwordSecondInput.getText().toString();
            if (password.equals(passwordConfirmation)) {
                if (ownedIdentityPasswordAndSalts != null) {
                    for (OwnedIdentityDao.OwnedIdentityPasswordAndSalt ownedIdentityPasswordAndSalt : ownedIdentityPasswordAndSalts) {
                        try {
                            byte[] hash = SettingsActivity.computePINHash(password, ownedIdentityPasswordAndSalt.unlock_salt);
                            if (Arrays.equals(ownedIdentityPasswordAndSalt.unlock_password, hash)) {
                                App.toast(R.string.toast_message_password_already_used_other_profile, Toast.LENGTH_SHORT);
                                return;
                            }
                        } catch (Exception ignored) { }
                    }
                }
                dismiss();
                if (onPasswordSetCallback != null) {
                    onPasswordSetCallback.onPasswordChosen(password);
                }
            }
        } else if (id == R.id.button_cancel) {
            dismiss();
        }
    }

    public interface OnPasswordChosenCallback {
        void onPasswordChosen(String password);
    }
}
