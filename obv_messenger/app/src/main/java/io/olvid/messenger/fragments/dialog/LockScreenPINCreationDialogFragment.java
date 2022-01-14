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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.settings.SettingsActivity;


public class LockScreenPINCreationDialogFragment extends DialogFragment implements View.OnClickListener {
    private Runnable onPINSetCallback = null;
    private boolean usePassword = false;

    private Button createPINButton;
    private EditText pinFirstInput;
    private EditText pinSecondInput;
    private Button switchPasswordPIN;
    private TextView textPINErrorMessage;

    public static LockScreenPINCreationDialogFragment newInstance() {
        return new LockScreenPINCreationDialogFragment();
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
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                pinFirstInput.requestFocus();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_lock_screen_pin_creation, container, false);

        createPINButton = dialogView.findViewById(R.id.button_create_pin);
        createPINButton.setOnClickListener(this);

        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);

        switchPasswordPIN = dialogView.findViewById(R.id.button_switch_password_pin);
        switchPasswordPIN.setOnClickListener(this);

        textPINErrorMessage = dialogView.findViewById(R.id.text_pin_error_message);

        pinFirstInput = dialogView.findViewById(R.id.pin_first_input);
        pinSecondInput = dialogView.findViewById(R.id.pin_second_input);
        TextWatcher textWatcher = new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                validatePINs();
            }
        };
        pinFirstInput.addTextChangedListener(textWatcher);
        pinSecondInput.addTextChangedListener(textWatcher);

        usePassword = SettingsActivity.isPINAPassword();
        configureInputsForPINOrPassword();
        return dialogView;
    }

    private void validatePINs() {
        String firstPIN = pinFirstInput.getText().toString();
        if (firstPIN.length() < 4) {
            if (firstPIN.length() != 0) {
                if (usePassword) {
                    textPINErrorMessage.setText(R.string.error_text_password_too_short);
                } else {
                    textPINErrorMessage.setText(R.string.error_text_pin_too_short);
                }
            } else {
                textPINErrorMessage.setText(null);
            }
            pinSecondInput.setEnabled(false);
            createPINButton.setEnabled(false);
            return;
        } else {
            textPINErrorMessage.setText(null);
            pinSecondInput.setEnabled(true);
        }

        String secondPIN = pinSecondInput.getText().toString();
        if (!firstPIN.equals(secondPIN)) {
            if (secondPIN.length() > 0) {
                if (usePassword) {
                    textPINErrorMessage.setText(R.string.error_text_password_mismatch);
                } else {
                    textPINErrorMessage.setText(R.string.error_text_pin_mismatch);
                }
            } else {
                textPINErrorMessage.setText(null);
            }
            createPINButton.setEnabled(false);
        } else {
            textPINErrorMessage.setText(null);
            createPINButton.setEnabled(true);
        }
    }

    public void setOnPINSetCallBack(Runnable callBack) {
        this.onPINSetCallback = callBack;
    }

    private void configureInputsForPINOrPassword() {
        if (usePassword) {
            pinFirstInput.setText(null);
            pinFirstInput.setHint(R.string.hint_enter_password);
            pinFirstInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            pinSecondInput.setText(null);
            pinSecondInput.setHint(R.string.hint_confirm_password);
            pinSecondInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            switchPasswordPIN.setText(R.string.button_label_use_pin);
        } else {
            pinFirstInput.setText(null);
            pinFirstInput.setHint(R.string.hint_enter_pin);
            pinFirstInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            pinSecondInput.setText(null);
            pinSecondInput.setHint(R.string.hint_confirm_pin);
            pinSecondInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            switchPasswordPIN.setText(R.string.button_label_use_password);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_create_pin) {
            String firstPIN = pinFirstInput.getText().toString();
            String secondPIN = pinSecondInput.getText().toString();
            if (firstPIN.equals(secondPIN)) {
                SettingsActivity.savePIN(firstPIN, usePassword);
                dismiss();
                if (usePassword) {
                    App.toast(R.string.toast_message_new_password_set, Toast.LENGTH_SHORT);
                } else {
                    App.toast(R.string.toast_message_new_pin_set, Toast.LENGTH_SHORT);
                }
                if (onPINSetCallback != null) {
                    onPINSetCallback.run();
                }
            }
        } else if (id == R.id.button_cancel) {
            dismiss();
        } else if (id == R.id.button_switch_password_pin) {
            usePassword = !usePassword;
            configureInputsForPINOrPassword();
        }
    }
}
