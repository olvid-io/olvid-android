/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.LockScreenActivity;
import io.olvid.messenger.databases.tasks.DeleteAllHiddenProfilesAndLimitedVisibilityMessages;
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
    private TextView pinUnlockTimer;
    private LinearLayout pinUnlockTimerGroup;

    private InputMethodManager imm;

    private UnlockEventBroadcastReceiver unlockEventBroadcastReceiver = null;
    private boolean openBiometricsOnNextWindowFocus = false;

    private final Timer timer = new Timer("LockScreenOrNot pin timeout timer");
    private TimerTask timerTask = null;

    private boolean pinLocked = true;
    private String previousPin = "";
    private int failedPinCount = 0;
    private boolean deleting = false;
    private final OnBackPressedCallback lockedOnBackPressed = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            moveTaskToBack(true);
        }
    };

    @Override
    protected void attachBaseContext(Context baseContext) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext));
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

        getOnBackPressedDispatcher().addCallback(this, lockedOnBackPressed);

        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            boolean isNeutral = SettingsActivity.lockScreenNeutral();
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.olvid_gradient_light));

            setContentView(R.layout.activity_lock_screen);

            if (isNeutral) {
                ImageView olvidLogo = findViewById(R.id.olvid_logo);
                ConstraintLayout container = findViewById(R.id.lock_screen_container);
                olvidLogo.setVisibility(View.GONE);
                container.setBackgroundColor(ContextCompat.getColor(this, R.color.black));
            }

            unlockEventBroadcastReceiver = new UnlockEventBroadcastReceiver();
            LocalBroadcastManager.getInstance(this).registerReceiver(unlockEventBroadcastReceiver, new IntentFilter(UnifiedForegroundService.LockSubService.APP_UNLOCKED_BROADCAST_ACTION));

            imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

            pinInput = findViewById(R.id.pin_input);
            pinInput.setEnabled(false);
            fingerprintButton = findViewById(R.id.fingerprint_icon);
            ImageView okButton = findViewById(R.id.button_ok);
            biometryDisabledTextview = findViewById(R.id.biometry_disabled_textview);
            pinUnlockTimer = findViewById(R.id.pin_unlock_timer);
            pinUnlockTimerGroup = findViewById(R.id.pin_unlock_timer_group);

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
                if (pinLocked) {
                    return;
                }

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

            String promptTitle = getString(R.string.dialog_title_unlock_olvid);
            if (isNeutral) {
                promptTitle = promptTitle.replace("Olvid ", "");
            }
            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(promptTitle)
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
        if (pinLocked) {
            return false;
        }

        String PIN = pinInput.getText().toString();
        if (!Objects.equals(previousPin, PIN)) {
            if (StringUtils.isASubstringOfB(previousPin, PIN)) {
                deleting = false;
                LockScreenActivity.storeFailedPinCount(failedPinCount + 1); // already store a failed PIN count to avoid free validations on activity kill/restart
            } else if (StringUtils.isASubstringOfB(PIN, previousPin)) {
                if (!deleting) {
                    deleting = true;
                    failedPinCount++;
                    LockScreenActivity.storeFailedPinCount(failedPinCount);
                }
            } else {
                deleting = false;
                failedPinCount++;
                LockScreenActivity.storeFailedPinCount(failedPinCount);
            }
        }

        if (failedPinCount >= LockScreenActivity.PIN_FAIL_COUNT_BEFORE_TIME_OUT) {
            LockScreenActivity.storeLockTimestamps();
            disablePinInput();

            previousPin = "";
            deleting = false;
            failedPinCount = 0;

            return false;
        }

        boolean unlock = SettingsActivity.verifyPIN(PIN);
        if (!unlock && SettingsActivity.useEmergencyPIN()) {
            if (SettingsActivity.verifyEmergencyPIN(PIN)) {
                unlock = true;
                App.runThread(new DeleteAllHiddenProfilesAndLimitedVisibilityMessages());
            }
        }

        if (unlock) {
            LockScreenActivity.storeFailedPinCount(0);
            if (keyWiped) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    LockScreenActivity.generateFingerprintAdditionDetectionKey();
                }
            }
            unlock();
            return true;
        }
        previousPin = PIN;
        return false;
    }

    private void disablePinInput() {
        pinLocked = true;
        pinInput.setEnabled(false);
        pinInput.setText("");

        tryToEnablePinInput();
    }

    private void tryToEnablePinInput() {
        SharedPreferences preference = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_lock), Context.MODE_PRIVATE);
        failedPinCount = preference.getInt(LockScreenActivity.FAILED_PINS_PREFERENCE_KEY, 0);
        long currentTimestamp = SystemClock.elapsedRealtime();
        long unlockTimestamp = preference.getLong(LockScreenActivity.PIN_LOCK_END_TIMESTAMP_PREFERENCE_KEY, 0);

        if (currentTimestamp < unlockTimestamp) {
            // check if we are not in the past (in case of reboot)
            long lockTimestamp = preference.getLong(LockScreenActivity.PIN_LOCK_TIMESTAMP_PREFERENCE_KEY, -1);
            if (lockTimestamp != -1 && lockTimestamp < currentTimestamp) {
                if (timerTask != null) {
                    timerTask.cancel();
                    timerTask = null;
                }

                timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> {
                            timerTask = null;
                            tryToEnablePinInput();
                        });
                    }
                };
                timer.schedule(timerTask, 1_000);

                int remainingSeconds = 1 + (int) ((unlockTimestamp - currentTimestamp - 1) / 1000);
                if (pinUnlockTimerGroup != null && pinUnlockTimer != null) {
                    if (pinUnlockTimerGroup.getVisibility() != View.VISIBLE) {
                        pinUnlockTimerGroup.setAlpha(0);
                        pinUnlockTimerGroup.setVisibility(View.VISIBLE);
                        pinUnlockTimerGroup.animate().alpha(1);
                    }
                    pinUnlockTimer.setText(getString(R.string.x_seconds, remainingSeconds));
                }

                return;
            }
        }

        if (pinUnlockTimerGroup != null) {
            if (pinUnlockTimerGroup.getVisibility() != View.GONE) {
                pinUnlockTimerGroup.animate().alpha(0);
                pinUnlockTimerGroup.setVisibility(View.GONE);
            }
        }

        // we can enable the PIN input
        pinLocked = false;
        pinInput.setEnabled(true);
        pinInput.requestFocus();
        if (imm != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> imm.showSoftInput(pinInput, InputMethodManager.SHOW_IMPLICIT), 200);
        }
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
        lockedOnBackPressed.setEnabled(UnifiedForegroundService.LockSubService.isApplicationLocked());

        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            if (pinInput == null || fingerprintButton == null || biometryDisabledTextview == null || pinUnlockTimerGroup == null || pinUnlockTimer == null) {
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

            tryToEnablePinInput();
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
