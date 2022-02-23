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
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.olvid.messenger.App;
import io.olvid.messenger.activities.LockScreenActivity;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.appdialogs.AppDialogShowActivity;


public abstract class LockableActivity extends AppCompatActivity {
    private EventBroadcastReceiver eventBroadcastReceiver = null;
    private boolean activityResumed = false;

    public static final String CUSTOM_LOCK_SCREEN_MESSAGE_RESOURCE_ID_INTENT_EXTRA = "custom_lock_screen_message_resource_id";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            Intent intent = new Intent(this, LockScreenActivity.class);
            intent.putExtra(LockScreenActivity.FORWARD_TO_INTENT_EXTRA, getIntent());
            startActivity(intent);
            finish();
            getIntent().setAction(null);
        }

        eventBroadcastReceiver = new EventBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter(UnifiedForegroundService.LockSubService.APP_LOCKED_BROADCAST_ACTION);
        intentFilter.addAction(App.NEW_APP_DIALOG_BROADCAST_ACTION);
        intentFilter.addAction(App.CURRENT_HIDDEN_PROFILE_CLOSED_BROADCAST_ACTION);
        intentFilter.addAction(SettingsActivity.FONT_SCALE_CHANGED_BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(eventBroadcastReceiver, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.activityResumed = true;

        Window window = getWindow();
        if (window != null) {
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setHideOverlayWindows(true);
            }
        }
        if (SettingsActivity.useApplicationLockScreen() && UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            showLockScreen(null);
        } else {
            App.showAppDialogs();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.activityResumed = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(eventBroadcastReceiver);
            eventBroadcastReceiver = null;
        }
    }

    private void showLockScreen(Integer customLockScreenMessageResourceId) {
        Intent lockIntent = new Intent(this, LockScreenActivity.class);
        if (customLockScreenMessageResourceId != null) {
            lockIntent.putExtra(LockScreenActivity.CUSTOM_MESSAGE_RESOURCE_ID_INTENT_EXTRA, customLockScreenMessageResourceId);
        }
        startActivity(lockIntent);
    }

    class EventBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case UnifiedForegroundService.LockSubService.APP_LOCKED_BROADCAST_ACTION: {
                    if (App.isVisible()) {
                        if (activityResumed) {
                            int customLockScreenMessageResourceId = intent.getIntExtra(CUSTOM_LOCK_SCREEN_MESSAGE_RESOURCE_ID_INTENT_EXTRA, -1);
                            if (customLockScreenMessageResourceId == -1) {
                                showLockScreen(null);
                            } else {
                                showLockScreen(customLockScreenMessageResourceId);
                            }
                        }
                    } else {
                        // finish the activity in the background to prevent any risk of it shortly appearing before the lock screen
                        if (App.shouldActivitiesBeKilledOnLockAndHiddenProfileClosedOnBackground()) {
                            finish();
                        }
                    }
                    break;
                }
                case App.CURRENT_HIDDEN_PROFILE_CLOSED_BROADCAST_ACTION: {
                    if (!App.isVisible()) {
                        finish();
                    }
                    break;
                }
                case App.NEW_APP_DIALOG_BROADCAST_ACTION: {
                    if (App.isVisible() && App.requestAppDialogShowing()) {
                        Intent dialogIntent = new Intent(LockableActivity.this, AppDialogShowActivity.class);
                        dialogIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(dialogIntent);
                    }
                    break;
                }
                case SettingsActivity.FONT_SCALE_CHANGED_BROADCAST_ACTION: {
                    recreate();
                    break;
                }
            }
        }
    }
}
