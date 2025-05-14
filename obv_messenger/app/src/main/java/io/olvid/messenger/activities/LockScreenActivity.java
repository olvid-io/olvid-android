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

package io.olvid.messenger.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.security.KeyStore;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import io.olvid.engine.Logger;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.tasks.DeleteAllHiddenProfilesAndLimitedVisibilityMessages;
import io.olvid.messenger.databases.tasks.DeleteAllLimitedVisibilityMessages;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;


public class LockScreenActivity extends AppCompatActivity {
    public static final String FORWARD_TO_INTENT_EXTRA = "forward_to";
    public static final String CUSTOM_MESSAGE_RESOURCE_ID_INTENT_EXTRA = "custom_message_resource_id";

    public static final String FAILED_PINS_PREFERENCE_KEY = "failed_pins";
    public static final String PIN_LOCK_TIMESTAMP_PREFERENCE_KEY = "pin_lock_timestamp";
    public static final String PIN_LOCK_END_TIMESTAMP_PREFERENCE_KEY = "pin_lock_end_timestamp";

    public static final int PIN_FAIL_COUNT_BEFORE_TIME_OUT = 3;
    public static final int PIN_INPUT_LOCK_DURATION = 60_000;

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

    private final Timer timer = new Timer("LockScreen pin timeout timer");
    private TimerTask timerTask = null;

    private boolean pinLocked = true;
    private String previousPin = "";
    private int failedPinCount = 0;
    private boolean deleting = false;

    @Override
    protected void attachBaseContext(Context baseContext) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_lock_screen);

        unlockEventBroadcastReceiver = new UnlockEventBroadcastReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(unlockEventBroadcastReceiver, new IntentFilter(UnifiedForegroundService.LockSubService.APP_UNLOCKED_BROADCAST_ACTION));

        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        boolean isNeutral = SettingsActivity.lockScreenNeutral();

        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(ContextCompat.getColor(this, isNeutral ? R.color.black : R.color.olvid_gradient_light));
        }

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

        TextView customMessageTextView = findViewById(R.id.custom_message_text_view);
        ImageView olvidLogo = findViewById(R.id.olvid_logo);
        int customMessageResourceId = getIntent().getIntExtra(CUSTOM_MESSAGE_RESOURCE_ID_INTENT_EXTRA, -1);
        if (isNeutral) {
            ConstraintLayout container = findViewById(R.id.lock_screen_container);
            if (customMessageResourceId != -1) {
                customMessageTextView.setVisibility(View.VISIBLE);
                customMessageTextView.setText(customMessageResourceId);
            } else {
                customMessageTextView.setVisibility(View.GONE);
            }
            olvidLogo.setVisibility(View.GONE);
            container.setBackgroundColor(ContextCompat.getColor(this, R.color.black));
        } else {
            if (customMessageResourceId != -1) {
                customMessageTextView.setVisibility(View.VISIBLE);
                customMessageTextView.setText(customMessageResourceId);
                olvidLogo.setVisibility(View.GONE);
            } else {
                customMessageTextView.setVisibility(View.GONE);
                olvidLogo.setVisibility(View.VISIBLE);
            }
        }

        biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) { super.onAuthenticationError(errorCode, errString); }

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
    }

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
                storeFailedPinCount(failedPinCount + 1); // already store a failed PIN count to avoid free validations on activity kill/restart
            } else if (StringUtils.isASubstringOfB(PIN, previousPin)) {
                if (!deleting) {
                    deleting = true;
                    failedPinCount++;
                    storeFailedPinCount(failedPinCount);
                }
            } else {
                deleting = false;
                failedPinCount++;
                storeFailedPinCount(failedPinCount);
            }
        }

        if (failedPinCount >= PIN_FAIL_COUNT_BEFORE_TIME_OUT) {
            storeLockTimestamps();
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
            storeFailedPinCount(0);
            if (keyWiped) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    generateFingerprintAdditionDetectionKey();
                }
            }
            unlock();
            return true;
        }
        previousPin = PIN;
        return false;
    }

    public static void storeFailedPinCount(int failedPinCount) {
        SharedPreferences preference = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_lock), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preference.edit();
        editor.putInt(FAILED_PINS_PREFERENCE_KEY, failedPinCount);
        editor.apply();
    }

    public static void storeLockTimestamps() {
        long currentTimestamp = SystemClock.elapsedRealtime();
        long pinUnlockTimestamp = currentTimestamp + PIN_INPUT_LOCK_DURATION;

        Logger.i("Too many pin fails, locking for " + (PIN_INPUT_LOCK_DURATION / 1000) + " seconds");

        SharedPreferences preference = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_lock), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preference.edit();
        editor.putInt(FAILED_PINS_PREFERENCE_KEY, 0);
        editor.putLong(PIN_LOCK_TIMESTAMP_PREFERENCE_KEY, currentTimestamp);
        editor.putLong(PIN_LOCK_END_TIMESTAMP_PREFERENCE_KEY, pinUnlockTimestamp);
        editor.apply();

        if (SettingsActivity.wipeMessagesOnUnlockFails()) {
            App.runThread(new DeleteAllLimitedVisibilityMessages());
        }
    }

    private void disablePinInput() {
        pinLocked = true;
        pinInput.setEnabled(false);
        pinInput.setText("");

        tryToEnablePinInput();
    }

    private void tryToEnablePinInput() {
        SharedPreferences preference = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_lock), Context.MODE_PRIVATE);
        failedPinCount = preference.getInt(FAILED_PINS_PREFERENCE_KEY, 0);
        long currentTimestamp = SystemClock.elapsedRealtime();
        long unlockTimestamp = preference.getLong(PIN_LOCK_END_TIMESTAMP_PREFERENCE_KEY, 0);

        if (currentTimestamp < unlockTimestamp) {
            // check if we are not in the past (in case of reboot)
            long lockTimestamp = preference.getLong(PIN_LOCK_TIMESTAMP_PREFERENCE_KEY, -1);
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

    private void forward() {
        Intent forwardToIntent = getIntent().getParcelableExtra(FORWARD_TO_INTENT_EXTRA);
        getIntent().removeExtra(FORWARD_TO_INTENT_EXTRA);
        if (forwardToIntent != null) {
            startActivity(forwardToIntent);
        } else {
            if (isTaskRoot()) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
            }
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            forward();
            return;
        }
        configureInputForPINOrPassword();

        BiometricManager biometricManager = BiometricManager.from(this);
        biometryAvailable = BiometricManager.BIOMETRIC_SUCCESS == biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);

        if (biometryAvailable && SettingsActivity.useBiometryToUnlock()) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && detectFingerprintAddition()) {
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

    private void openBiometricPrompt() {
        biometricPrompt.authenticate(promptInfo);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (openBiometricsOnNextWindowFocus) {
                openBiometricsOnNextWindowFocus = false;
                if (!pinLocked) {
                    openBiometricPrompt();
                }
            }
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    class UnlockEventBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            forward();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void generateFingerprintAdditionDetectionKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias("lock")) {
                keyStore.deleteEntry("lock");
            }

            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(new KeyGenParameterSpec.Builder("lock", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            keyGenerator.generateKey();
        } catch (Exception e) {
            // Nothing to do
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void deleteFingerprintAdditionDetectionKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias("lock")) {
                keyStore.deleteEntry("lock");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean detectFingerprintAddition() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            SecretKey key = (SecretKey) keyStore.getKey("lock", null);
            if (key == null) {
                return true;
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (Exception e) {
            if (e instanceof KeyPermanentlyInvalidatedException) {
                return true;
            } else {
                e.printStackTrace();
            }
        }
        return false;
    }
}
