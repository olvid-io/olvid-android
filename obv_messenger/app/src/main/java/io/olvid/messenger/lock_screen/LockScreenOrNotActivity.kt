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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import io.olvid.messenger.R
import io.olvid.messenger.lock_screen.LockScreenActivity.Companion.generateFingerprintAdditionDetectionKey
import io.olvid.messenger.services.MDMConfigurationSingleton
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class LockScreenOrNotActivity : AppCompatActivity() {

    private val viewModel: LockScreenViewModel by viewModels()
    private var unlockEventBroadcastReceiver: UnlockEventBroadcastReceiver? = null
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private val lockedOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            moveTaskToBack(true)
        }
    }

    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
                navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            )
        }
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, lockedOnBackPressedCallback)

        window?.let { w ->
            if (SettingsActivity.preventScreenCapture(this)) {
                w.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
            window?.let { w ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    w.setHideOverlayWindows(true)
                }
            }

            val isNeutral = SettingsActivity.lockScreenNeutral()

            setupBiometricPrompt(isNeutral)

            // Register unlock broadcast receiver
            unlockEventBroadcastReceiver = UnlockEventBroadcastReceiver()
            LocalBroadcastManager.getInstance(this).registerReceiver(
                unlockEventBroadcastReceiver!!,
                IntentFilter(UnifiedForegroundService.LockSubService.APP_UNLOCKED_BROADCAST_ACTION)
            )

            // Observe unlock requests from ViewModel
            lifecycleScope.launch {
                viewModel.unlockRequested.collectLatest { requested ->
                    if (requested) {
                        sendUnlockToService()
                    }
                }
            }

            setContent {
                val pinLocked by viewModel.pinLocked.collectAsState()

                val setupMode = MDMConfigurationSingleton.isLockScreenRequired() && !SettingsActivity.isPINConfigured()

                LockScreen(
                    viewModel = viewModel,
                    isNeutral = isNeutral,
                    customMessage = null,
                    setupMode = setupMode,
                    onBiometricRequest = { if (!pinLocked) biometricPrompt.authenticate(promptInfo) },
                    onSetupPinRequest = { openPINCreationDialog() },
                )
            }
        } else {
            notLockedOnCreate()
        }
    }

    protected abstract fun notLockedOnCreate()

    override fun onResume() {
        super.onResume()
        lockedOnBackPressedCallback.isEnabled = UnifiedForegroundService.LockSubService.isApplicationLocked()

        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            if (unlockEventBroadcastReceiver == null) {
                // onCreate was called while the app was unlocked, but onResume is called after it locked
                recreate()
                return
            }

            val shouldOpenBiometrics = viewModel.checkBiometryAndConfigureState()
            viewModel.tryToEnablePinInput()

            val setupMode = MDMConfigurationSingleton.isLockScreenRequired() && !SettingsActivity.isPINConfigured()

            if (shouldOpenBiometrics && !viewModel.pinLocked.value && !setupMode) {
                biometricPrompt.authenticate(promptInfo)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unlockEventBroadcastReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
            unlockEventBroadcastReceiver = null
        }
    }

    private fun setupBiometricPrompt(isNeutral: Boolean) {
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.onBiometricSuccess()
                }
            }
        )

        val promptTitle = getString(
            if (isNeutral)
                R.string.dialog_title_unlock_neutral
            else
                R.string.dialog_title_unlock_olvid
        )
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setNegativeButtonText(getString(R.string.button_label_cancel))
            .setConfirmationRequired(false)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
    }

    private fun sendUnlockToService() {
        val unlockIntent = Intent(this, UnifiedForegroundService::class.java)
        unlockIntent.action = UnifiedForegroundService.LockSubService.UNLOCK_APP_ACTION
        unlockIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_LOCK)
        startService(unlockIntent)
        // Navigation happens when the APP_UNLOCKED_BROADCAST_ACTION broadcast is received
    }

    private fun openPINCreationDialog() {
        val dialog = LockScreenPINCreationDialogFragment.newInstance()
        dialog.setOnPINSetCallBack {
            // Enable lock screen preference
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit {
                    putBoolean(SettingsActivity.PREF_KEY_USE_LOCK_SCREEN, true)
                }

            // Generate fingerprint detection key
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                generateFingerprintAdditionDetectionKey()
            }

            // Notify the lock service that lock is now activated
            val settingsIntent = Intent(
                UnifiedForegroundService.LockSubService.LOCK_SETTINGS_ACTIVATED_ACTION,
                null,
                this,
                UnifiedForegroundService::class.java
            )
            settingsIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_LOCK)
            startService(settingsIntent)

            recreate()
        }
        dialog.show(supportFragmentManager, "dialog")
    }

    inner class UnlockEventBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            recreate()
        }
    }
}
