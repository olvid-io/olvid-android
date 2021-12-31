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

package io.olvid.messenger.fragments.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.olvid.messenger.R;
import io.olvid.messenger.settings.SettingsActivity;


public class LockScreenPINVerificationDialogFragment extends DialogFragment implements View.OnClickListener {
    private Runnable onPINEnteredCallback = null;

    private EditText pinInput;

    public static LockScreenPINVerificationDialogFragment newInstance() {
        return new LockScreenPINVerificationDialogFragment();
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
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                pinInput.requestFocus();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_lock_screen_pin_verification, container, false);

        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);

        pinInput = dialogView.findViewById(R.id.pin_input);
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validatePIN();
            }
        };
        pinInput.addTextChangedListener(textWatcher);
        configureInputForPINOrPassword();
        return dialogView;
    }

    private void validatePIN() {
        String PIN = pinInput.getText().toString();
        if (SettingsActivity.verifyPIN(PIN)) {
            dismiss();
            onPINEnteredCallback.run();
        }
    }

    public void setOnPINEnteredCallBack(Runnable callBack) {
        this.onPINEnteredCallback = callBack;
    }

    private void configureInputForPINOrPassword() {
        if (pinInput != null) {
            if (SettingsActivity.isPINAPassword()) {
                pinInput.setHint(R.string.hint_enter_password);
                pinInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            } else {
                pinInput.setHint(R.string.hint_enter_pin);
                pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.button_cancel) {
            dismiss();
        }
    }
}
