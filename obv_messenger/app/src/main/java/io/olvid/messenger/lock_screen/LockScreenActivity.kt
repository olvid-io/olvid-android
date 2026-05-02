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
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.view.inputmethod.InputMethodManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
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
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.onBackPressed
import io.olvid.messenger.databases.tasks.DeleteAllLimitedVisibilityMessages
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.services.MDMConfigurationSingleton
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class LockScreenActivity : AppCompatActivity() {

    companion object {
        const val FORWARD_TO_INTENT_EXTRA = "forward_to"
        const val CUSTOM_MESSAGE_RESOURCE_ID_INTENT_EXTRA = "custom_message_resource_id"

        const val FAILED_PINS_PREFERENCE_KEY = "failed_pins"
        const val PIN_LOCK_TIMESTAMP_PREFERENCE_KEY = "pin_lock_timestamp"
        const val PIN_LOCK_END_TIMESTAMP_PREFERENCE_KEY = "pin_lock_end_timestamp"

        const val PIN_FAIL_COUNT_BEFORE_TIME_OUT = 3
        const val PIN_INPUT_LOCK_DURATION = 60_000

        @JvmStatic
        fun storeFailedPinCount(failedPinCount: Int) {
            val prefs = App.getContext().getSharedPreferences(
                App.getContext().getString(R.string.preference_filename_lock),
                MODE_PRIVATE
            )
            prefs.edit { putInt(FAILED_PINS_PREFERENCE_KEY, failedPinCount) }
        }

        @JvmStatic
        fun storeLockTimestamps() {
            val currentTimestamp = SystemClock.elapsedRealtime()
            val pinUnlockTimestamp = currentTimestamp + PIN_INPUT_LOCK_DURATION

            val prefs = App.getContext().getSharedPreferences(
                App.getContext().getString(R.string.preference_filename_lock),
                MODE_PRIVATE
            )
            prefs.edit(commit = true) {
                putInt(FAILED_PINS_PREFERENCE_KEY, 0)
                    .putLong(PIN_LOCK_TIMESTAMP_PREFERENCE_KEY, currentTimestamp)
                    .putLong(PIN_LOCK_END_TIMESTAMP_PREFERENCE_KEY, pinUnlockTimestamp)
            }

            if (SettingsActivity.wipeMessagesOnUnlockFails()) {
                App.runThread(DeleteAllLimitedVisibilityMessages())
            }
        }

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.N)
        fun generateFingerprintAdditionDetectionKey() {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (keyStore.containsAlias("lock")) {
                    keyStore.deleteEntry("lock")
                }
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                keyGenerator.init(
                    KeyGenParameterSpec.Builder("lock", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setUserAuthenticationRequired(true)
                        .setInvalidatedByBiometricEnrollment(true)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                keyGenerator.generateKey()
            } catch (_: Exception) {
            }
        }

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.N)
        fun deleteFingerprintAdditionDetectionKey() {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (keyStore.containsAlias("lock")) {
                    keyStore.deleteEntry("lock")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.N)
        fun detectFingerprintAddition(): Boolean {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val key = keyStore.getKey("lock", null) as? SecretKey ?: return true
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
            } catch (e: Exception) {
                if (e is KeyPermanentlyInvalidatedException) return true
                e.printStackTrace()
            }
            return false
        }
    }

    private val viewModel: LockScreenViewModel by viewModels()
    private var unlockEventBroadcastReceiver: UnlockEventBroadcastReceiver? = null
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        super.onCreate(savedInstanceState)
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        val setupMode = MDMConfigurationSingleton.isLockScreenRequired() && !SettingsActivity.isPINConfigured()
        val customMessageResourceId = intent.getIntExtra(CUSTOM_MESSAGE_RESOURCE_ID_INTENT_EXTRA, -1)
        val isNeutral = SettingsActivity.lockScreenNeutral()
        val customMessage = if (customMessageResourceId != -1) getString(customMessageResourceId) else null

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
            onBackPressed {
                moveTaskToBack(true)
            }

            val pinLocked by viewModel.pinLocked.collectAsState()

            LockScreen(
                viewModel = viewModel,
                isNeutral = isNeutral,
                customMessage = customMessage,
                setupMode = setupMode,
                onBiometricRequest = {
                    if (!pinLocked)
                        biometricPrompt.authenticate(promptInfo)
                },
                onSetupPinRequest = { openPINCreationDialog() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val setupMode = MDMConfigurationSingleton.isLockScreenRequired() && !SettingsActivity.isPINConfigured()
        if (!setupMode && !UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            forward()
            return
        }

        val shouldOpenBiometrics = viewModel.checkBiometryAndConfigureState()
        viewModel.tryToEnablePinInput()

        if (shouldOpenBiometrics && !viewModel.pinLocked.value && !setupMode) {
            biometricPrompt.authenticate(promptInfo)
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

    private fun forward() {
        // dismiss the keyboard before forwarding (otherwise the MainActivity may mess it's padding on older APIs)
        currentFocus?.let { view ->
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(view.windowToken,0)
        }
        @Suppress("DEPRECATION")
        val forwardToIntent = intent.getParcelableExtra<Intent>(FORWARD_TO_INTENT_EXTRA)
        intent.removeExtra(FORWARD_TO_INTENT_EXTRA)
        if (forwardToIntent != null) {
            startActivity(forwardToIntent)
        } else if (isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
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

            // Navigate forward
            forward()
        }
        dialog.show(supportFragmentManager, "dialog")
    }

    inner class UnlockEventBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            forward()
        }
    }
}
