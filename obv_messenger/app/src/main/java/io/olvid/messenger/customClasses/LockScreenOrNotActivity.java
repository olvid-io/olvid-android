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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.olvid.messenger.R;
import io.olvid.messenger.activities.LockScreenActivity;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;


public abstract class LockScreenOrNotActivity extends AppCompatActivity {
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean biometryAvailable = false;
    private boolean keyWiped = false;

    private EditText pinInput;
    private ImageButton fingerprintButton;
    private TextView biometryDisabledTextview;
    private UnlockEventBroadcastReceiver unlockEventBroadcastReceiver = null;
    private boolean openBiometricsOnNextWindowFocus = false;

    @Override
    protected void attachBaseContext(Context baseContext) {
        final Context newContext;
        float customFontScale = SettingsActivity.getFontScale();
        float fontScale = baseContext.getResources().getConfiguration().fontScale;
        if (customFontScale != 1.0f) {
            Configuration configuration = new Configuration();
            configuration.fontScale = fontScale * customFontScale;
            newContext = baseContext.createConfigurationContext(configuration);
        } else {
            newContext = baseContext;
        }

        super.attachBaseContext(newContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SettingsActivity.preventScreenCapture()) {
            Window window = getWindow();
            if (window != null) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }

        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.olvid_gradient_light));

            setContentView(R.layout.activity_lock_screen);

            unlockEventBroadcastReceiver = new UnlockEventBroadcastReceiver();
            LocalBroadcastManager.getInstance(this).registerReceiver(unlockEventBroadcastReceiver, new IntentFilter(UnifiedForegroundService.LockSubService.APP_UNLOCKED_BROADCAST_ACTION));

            pinInput = findViewById(R.id.pin_input);
            fingerprintButton = findViewById(R.id.fingerprint_icon);
            ImageView okButton = findViewById(R.id.button_ok);
            biometryDisabledTextview = findViewById(R.id.biometry_disabled_textview);

            TextWatcher textWatcher = new TextChangeListener() {
                @Override
                public void afterTextChanged(Editable s) {
                    validatePIN();
                }
            };
            pinInput.addTextChangedListener(textWatcher);
            pinInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!validatePIN()) {
                        Animation shakeAnimation = AnimationUtils.loadAnimation(this, R.anim.shake);
                        pinInput.startAnimation(shakeAnimation);
                        pinInput.setSelection(0, pinInput.getText().length());
                    }
                    return true;
                }
                return true;
            });
            fingerprintButton.setOnClickListener(v -> openBiometricPrompt());
            okButton.setOnClickListener(v -> {
                if (!validatePIN()) {
                    Animation shakeAnimation = AnimationUtils.loadAnimation(this, R.anim.shake);
                    pinInput.startAnimation(shakeAnimation);
                    pinInput.setSelection(0, pinInput.getText().length());
                }
            });

            biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    if (biometryAvailable && !keyWiped && SettingsActivity.useBiometryToUnlock()) {
                        unlock();
                    }
                }
            });

            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.dialog_title_unlock_olvid))
                    .setNegativeButtonText(getString(R.string.button_label_cancel))
                    .setConfirmationRequired(false)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build();
        } else {
            getWindow().setStatusBarColor(Color.TRANSPARENT);

            notLockedOnCreate();
        }
    }

    abstract protected void notLockedOnCreate();


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unlockEventBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(unlockEventBroadcastReceiver);
            unlockEventBroadcastReceiver = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            moveTaskToBack(true);
        } else {
            finish();
        }
    }

    private void configureInputForPINOrPassword() {
        if (pinInput != null && biometryDisabledTextview != null) {
            pinInput.setText(null);
            if (SettingsActivity.isPINAPassword()) {
                pinInput.setHint(R.string.hint_enter_password);
                pinInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                biometryDisabledTextview.setText(R.string.message_biometric_enrollment_detected_password);
            } else {
                pinInput.setHint(R.string.hint_enter_pin);
                pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                biometryDisabledTextview.setText(R.string.message_biometric_enrollment_detected_pin);
            }
        }
    }

    private boolean validatePIN() {
        String PIN = pinInput.getText().toString();
        if (SettingsActivity.verifyPIN(PIN)) {
            if (keyWiped) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    LockScreenActivity.generateFingerprintAdditionDetectionKey();
                }
            }
            unlock();
            return true;
        }
        return false;
    }

    private void unlock() {
        Intent unlockIntent = new Intent(this, UnifiedForegroundService.class);
        unlockIntent.setAction(UnifiedForegroundService.LockSubService.UNLOCK_APP_ACTION);
        unlockIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_LOCK);
        startService(unlockIntent);
        // do not forward yet, wait for the unlock broadcast event to do this
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            if (pinInput == null || fingerprintButton == null || biometryDisabledTextview == null) {
                // may happen if onCreate was called while app was unlocked but onResume is called after it was locked
                recreate();
                return;
            }

            configureInputForPINOrPassword();

            BiometricManager biometricManager = BiometricManager.from(this);
            biometryAvailable = BiometricManager.BIOMETRIC_SUCCESS == biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);

            if (biometryAvailable && SettingsActivity.useBiometryToUnlock()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && LockScreenActivity.detectFingerprintAddition()) {
                    keyWiped = true;
                    fingerprintButton.setVisibility(View.GONE);
                    biometryDisabledTextview.setVisibility(View.VISIBLE);
                } else {
                    fingerprintButton.setVisibility(View.VISIBLE);
                    biometryDisabledTextview.setVisibility(View.GONE);
                    openBiometricsOnNextWindowFocus = true;
                }
            } else {
                fingerprintButton.setVisibility(View.GONE);
                biometryDisabledTextview.setVisibility(View.GONE);
            }
            pinInput.requestFocus();
        }
    }

    private void openBiometricPrompt() {
        biometricPrompt.authenticate(promptInfo);
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (openBiometricsOnNextWindowFocus) {
                openBiometricsOnNextWindowFocus = false;
                openBiometricPrompt();
            }
        }
    }

    class UnlockEventBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            recreate();
        }
    }
}
