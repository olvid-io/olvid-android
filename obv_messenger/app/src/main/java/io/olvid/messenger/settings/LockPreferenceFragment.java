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

package io.olvid.messenger.settings;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.biometric.BiometricManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.LockScreenActivity;
import io.olvid.messenger.customClasses.NoClickSwitchPreference;
import io.olvid.messenger.fragments.dialog.LockScreenPINCreationDialogFragment;
import io.olvid.messenger.fragments.dialog.LockScreenPINVerificationDialogFragment;
import io.olvid.messenger.services.UnifiedForegroundService;

public class LockPreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_lock, rootKey);
        PreferenceScreen screen = getPreferenceScreen();

        NoClickSwitchPreference lockScreenPreference = screen.findPreference(SettingsActivity.PREF_KEY_USE_LOCK_SCREEN);
        if (lockScreenPreference != null) {
            lockScreenPreference.setOnPreferenceClickListener((Preference pref) -> {
                if (pref instanceof NoClickSwitchPreference) {
                    NoClickSwitchPreference preference = (NoClickSwitchPreference) pref;

                    if (preference.isChecked()) {
                        LockScreenPINVerificationDialogFragment lockScreenPINVerificationDialogFragment = LockScreenPINVerificationDialogFragment.newInstance();
                        lockScreenPINVerificationDialogFragment.setOnPINEnteredCallBack(() -> {
                            SettingsActivity.clearPIN();
                            if (preference.callChangeListener(false)) {
                                preference.setChecked(false);
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                LockScreenActivity.deleteFingerprintAdditionDetectionKey();
                            }
                            Intent settingsIntent = new Intent(UnifiedForegroundService.LockSubService.LOCK_SETTINGS_DEACTIVATED_ACTION, null, App.getContext(), UnifiedForegroundService.class);
                            settingsIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_LOCK);
                            App.getContext().startService(settingsIntent);
                        });
                        lockScreenPINVerificationDialogFragment.show(getChildFragmentManager(), "dialog");
                    } else {
                        LockScreenPINCreationDialogFragment lockScreenPINCreationDialogFragment = LockScreenPINCreationDialogFragment.newInstance();
                        lockScreenPINCreationDialogFragment.setOnPINSetCallBack(() -> {
                            if (preference.callChangeListener(true)) {
                                preference.setChecked(true);
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                LockScreenActivity.generateFingerprintAdditionDetectionKey();
                            }
                            Intent settingsIntent = new Intent(UnifiedForegroundService.LockSubService.LOCK_SETTINGS_ACTIVATED_ACTION, null, App.getContext(), UnifiedForegroundService.class);
                            settingsIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_LOCK);
                            App.getContext().startService(settingsIntent);
                        });
                        lockScreenPINCreationDialogFragment.show(getChildFragmentManager(), "dialog");
                    }
                }
                return true;
            });
        }

        SwitchPreference biometryPreference = screen.findPreference(SettingsActivity.PREF_KEY_UNLOCK_BIOMETRY);
        if (biometryPreference != null) {
            BiometricManager biometricManager = BiometricManager.from(App.getContext());
            switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                    screen.removePreference(biometryPreference);
                    break;
                case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                    biometryPreference.setSummary(R.string.pref_unlock_biometry_summary_none_enrolled);
                    biometryPreference.setEnabled(false);
                    break;
                case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                case BiometricManager.BIOMETRIC_SUCCESS:
                case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
                case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
                    break;
            }
        }

        Preference resetPINPreference = screen.findPreference(SettingsActivity.PREF_KEY_RESET_PIN);
        if (resetPINPreference != null) {
            resetPINPreference.setOnPreferenceClickListener((Preference pref) -> {
                LockScreenPINVerificationDialogFragment lockScreenPINVerificationDialogFragment = LockScreenPINVerificationDialogFragment.newInstance();
                lockScreenPINVerificationDialogFragment.setOnPINEnteredCallBack(() -> {
                    LockScreenPINCreationDialogFragment lockScreenPINCreationDialogFragment = LockScreenPINCreationDialogFragment.newInstance();
                    lockScreenPINCreationDialogFragment.show(getChildFragmentManager(), "dialog");
                });
                lockScreenPINVerificationDialogFragment.show(getChildFragmentManager(), "dialog");

                return true;
            });
        }
    }
}