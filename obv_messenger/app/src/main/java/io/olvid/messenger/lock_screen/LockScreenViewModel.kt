/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.lock_screen

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.tasks.DeleteAllHiddenProfilesAndLimitedVisibilityMessages
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LockScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val _pinLocked = MutableStateFlow(true)
    val pinLocked: StateFlow<Boolean> = _pinLocked

    val lockoutRemainingSeconds: MutableState<Int> = mutableIntStateOf(0)

    private val _biometryAvailable = MutableStateFlow(false)
    val biometryAvailable: StateFlow<Boolean> = _biometryAvailable

    private val _keyWiped = MutableStateFlow(false)
    val keyWiped: StateFlow<Boolean> = _keyWiped

    // Signals the Activity to send the unlock service intent
    private val _unlockRequested = MutableStateFlow(false)
    val unlockRequested: StateFlow<Boolean> = _unlockRequested

    private var previousPin = ""
    private var deleting = false
    private var failedPinCount = 0
    private var countdownJob: Job? = null

    init {
        val prefs = getLockPrefs()
        failedPinCount = prefs.getInt(LockScreenActivity.FAILED_PINS_PREFERENCE_KEY, 0)
    }

    fun checkBiometryAndConfigureState(): Boolean {
        val biometricManager = BiometricManager.from(getApplication())
        val canAuthenticate = when(biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS, BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> true
            else -> false
        }

        if (canAuthenticate && SettingsActivity.useBiometryToUnlock()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && LockScreenActivity.detectFingerprintAddition()) {
                _keyWiped.value = true
                _biometryAvailable.value = false
                return false
            }
            _biometryAvailable.value = true
            return true
        }
        _biometryAvailable.value = false
        return false
    }

    fun tryToEnablePinInput() {
        val prefs = getLockPrefs()
        failedPinCount = prefs.getInt(LockScreenActivity.FAILED_PINS_PREFERENCE_KEY, 0)
        val currentTimestamp = SystemClock.elapsedRealtime()
        val unlockTimestamp = prefs.getLong(LockScreenActivity.PIN_LOCK_END_TIMESTAMP_PREFERENCE_KEY, 0)

        if (currentTimestamp < unlockTimestamp) {
            val lockTimestamp = prefs.getLong(LockScreenActivity.PIN_LOCK_TIMESTAMP_PREFERENCE_KEY, -1)
            if (lockTimestamp != -1L && lockTimestamp < currentTimestamp) {
                startCountdown(unlockTimestamp)
                return
            }
        }

        lockoutRemainingSeconds.value = 0
        _pinLocked.value = false
    }

    private fun startCountdown(unlockTimestamp: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val currentTimestamp = SystemClock.elapsedRealtime()
                val remainingMs = unlockTimestamp - currentTimestamp
                if (remainingMs <= 0) break
                val remainingSeconds = 1 + (remainingMs - 1) / 1000
                lockoutRemainingSeconds.value = remainingSeconds.toInt()
                delay(1000)
            }
            lockoutRemainingSeconds.value = 0
            tryToEnablePinInput()
        }
    }

    /**
     * Called on every PIN text change. Returns true if the PIN was correct and unlock was triggered.
     */
    fun onPinChange(pin: String): Boolean {
        if (_pinLocked.value) return false

        if (pin != previousPin) {
            when {
                StringUtils.isASubstringOfB(previousPin, pin) -> {
                    // typing forward
                    deleting = false
                    LockScreenActivity.storeFailedPinCount(failedPinCount + 1)
                }
                StringUtils.isASubstringOfB(pin, previousPin) -> {
                    // deleting
                    if (!deleting) {
                        deleting = true
                        failedPinCount++
                        LockScreenActivity.storeFailedPinCount(failedPinCount)
                    }
                }
                else -> {
                    // paste or completely different value
                    deleting = false
                    failedPinCount++
                    LockScreenActivity.storeFailedPinCount(failedPinCount)
                }
            }
        }

        if (failedPinCount >= LockScreenActivity.PIN_FAIL_COUNT_BEFORE_TIME_OUT) {
            LockScreenActivity.storeLockTimestamps()
            previousPin = ""
            deleting = false
            failedPinCount = 0
            _pinLocked.value = true
            tryToEnablePinInput()
            return false
        }

        var shouldUnlock = SettingsActivity.verifyPIN(pin)
        if (!shouldUnlock && SettingsActivity.useEmergencyPIN()) {
            if (SettingsActivity.verifyEmergencyPIN(pin)) {
                shouldUnlock = true
                App.runThread(DeleteAllHiddenProfilesAndLimitedVisibilityMessages())
            }
        }

        if (shouldUnlock) {
            LockScreenActivity.storeFailedPinCount(0)
            if (_keyWiped.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LockScreenActivity.generateFingerprintAdditionDetectionKey()
            }
            _unlockRequested.value = true
            return true
        }

        previousPin = pin
        return false
    }

    fun onBiometricSuccess() {
        if (_biometryAvailable.value && !_keyWiped.value && SettingsActivity.useBiometryToUnlock()) {
            _unlockRequested.value = true
        }
    }

    private fun getLockPrefs() = getApplication<Application>().getSharedPreferences(
        getApplication<Application>().getString(R.string.preference_filename_lock),
        Context.MODE_PRIVATE
    )
}
