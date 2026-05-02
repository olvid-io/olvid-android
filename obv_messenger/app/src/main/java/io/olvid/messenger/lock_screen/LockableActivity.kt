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
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.olvid.messenger.App
import io.olvid.messenger.appdialogs.AppDialogShowActivity
import io.olvid.messenger.services.MDMConfigurationSingleton
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.SettingsActivity

abstract class LockableActivity : AppCompatActivity() {

    companion object {
        const val CUSTOM_LOCK_SCREEN_MESSAGE_RESOURCE_ID_INTENT_EXTRA = "custom_lock_screen_message_resource_id"
    }

    protected var disableScaling = false
    private var eventBroadcastReceiver: EventBroadcastReceiver? = null
    private var activityResumed = false
    private var attachedDensityDpi = 0

    override fun attachBaseContext(baseContext: Context) {
        if (disableScaling) {
            attachedDensityDpi = baseContext.resources.configuration.densityDpi
            super.attachBaseContext(baseContext)
        } else {
            val newContext = SettingsActivity.overrideContextScales(baseContext)
            attachedDensityDpi = newContext.resources.configuration.densityDpi
            super.attachBaseContext(newContext)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (attachedDensityDpi != 0 && newConfig.densityDpi != attachedDensityDpi) {
            val customScreenScale = SettingsActivity.screenScale
            if (customScreenScale != 1.0f) {
                // this is a hack, but we need to update values that were overridden in attachBaseContext (overridden values are not updated on configuration change)
                val configuration = resources.configuration
                configuration.screenWidthDp = (newConfig.screenWidthDp / customScreenScale).toInt()
                configuration.screenHeightDp = (newConfig.screenHeightDp / customScreenScale).toInt()
                configuration.smallestScreenWidthDp = (newConfig.smallestScreenWidthDp / customScreenScale).toInt()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if ((MDMConfigurationSingleton.isLockScreenRequired() && !SettingsActivity.isPINConfigured())
            || UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            val intent = Intent(this, LockScreenActivity::class.java)
            intent.putExtra(LockScreenActivity.FORWARD_TO_INTENT_EXTRA, getIntent())
            startActivity(intent)
            finish()
            getIntent().action = null
            return
        }

        eventBroadcastReceiver = EventBroadcastReceiver()
        val intentFilter = IntentFilter(UnifiedForegroundService.LockSubService.APP_LOCKED_BROADCAST_ACTION)
        intentFilter.addAction(App.NEW_APP_DIALOG_BROADCAST_ACTION)
        intentFilter.addAction(App.CURRENT_HIDDEN_PROFILE_CLOSED_BROADCAST_ACTION)
        intentFilter.addAction(SettingsActivity.ACTIVITY_RECREATE_REQUIRED_ACTION)
        LocalBroadcastManager.getInstance(this).registerReceiver(eventBroadcastReceiver!!, intentFilter)
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true

        window?.let { w ->
            if (SettingsActivity.preventScreenCapture(this)) {
                w.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                w.setHideOverlayWindows(true)
            }
        }
        if (SettingsActivity.useApplicationLockScreen() && UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            showLockScreen(null)
        } else {
            App.showAppDialogs()
        }
    }

    override fun onPause() {
        super.onPause()
        activityResumed = false
    }

    override fun onDestroy() {
        super.onDestroy()
        eventBroadcastReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
            eventBroadcastReceiver = null
        }
    }

    private fun showLockScreen(customLockScreenMessageResourceId: Int?) {
        val lockIntent = Intent(this, LockScreenActivity::class.java)
        if (customLockScreenMessageResourceId != null) {
            lockIntent.putExtra(LockScreenActivity.CUSTOM_MESSAGE_RESOURCE_ID_INTENT_EXTRA, customLockScreenMessageResourceId)
        }
        startActivity(lockIntent)
    }

    inner class EventBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UnifiedForegroundService.LockSubService.APP_LOCKED_BROADCAST_ACTION -> {
                    if (App.isVisible()) {
                        if (activityResumed) {
                            val customId = intent.getIntExtra(CUSTOM_LOCK_SCREEN_MESSAGE_RESOURCE_ID_INTENT_EXTRA, -1)
                            showLockScreen(if (customId == -1) null else customId)
                        }
                    } else {
                        // finish the activity in the background to prevent any risk of it shortly appearing before the lock screen
                        if (App.shouldActivitiesBeKilledOnLockAndHiddenProfileClosedOnBackground()) {
                            finish()
                        }
                    }
                }
                App.CURRENT_HIDDEN_PROFILE_CLOSED_BROADCAST_ACTION -> {
                    if (!App.isVisible()) {
                        finish()
                    }
                }
                App.NEW_APP_DIALOG_BROADCAST_ACTION -> {
                    if (App.isVisible() && App.requestAppDialogShowing()) {
                        val dialogIntent = Intent(this@LockableActivity, AppDialogShowActivity::class.java)
                        dialogIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(dialogIntent)
                    }
                }
                SettingsActivity.ACTIVITY_RECREATE_REQUIRED_ACTION -> {
                    recreate()
                }
            }
        }
    }
}
