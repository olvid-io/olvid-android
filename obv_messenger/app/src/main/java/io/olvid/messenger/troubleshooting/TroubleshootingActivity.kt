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

package io.olvid.messenger.troubleshooting

import android.Manifest.permission
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.Settings
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import android.text.format.Formatter
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle.State.RESUMED
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.AppDatabaseOpenCallback
import io.olvid.messenger.firebase.ObvFirebaseMessagingService
import io.olvid.messenger.google_services.GoogleServicesUtils
import io.olvid.messenger.services.AvailableSpaceHelper
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.core.view.WindowCompat


@OptIn(ExperimentalPermissionsApi::class)
class TroubleshootingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val troubleshootingDataStore = TroubleshootingDataStore(context)

            val batteryOptimizationState = remember {
                CheckState(
                    BATTERY_CHECK_STATE,
                    troubleshootingDataStore
                ) { getBatteryOptimizationsState() }
            }
            val alarmState = remember {
                CheckState(ALARM_CHECK_STATE, troubleshootingDataStore) { getAlarmState() }
            }
            val backgroundRestrictionState = remember {
                CheckState(
                    BACKGROUND_CHECK_STATE,
                    troubleshootingDataStore
                ) { getBackgroundState() }
            }
            val storageState = remember {
                CheckState(STORAGE_CHECK_STATE, troubleshootingDataStore) { getStorageState() }
            }
            val permanentSocketState = remember {
                CheckState(
                    SOCKET_CHECK_STATE,
                    troubleshootingDataStore
                ) { getPermanentSocketState() }
            }
            val backupState = remember {
                CheckState(
                    BACKUP_CHECK_STATE,
                    troubleshootingDataStore,
                    statusIsOk = { a -> a == 0 }) { getBackupState() }
            }
            val fullScreenIntentState = remember {
                CheckState(
                    FULL_SCREEN_CHECK_STATE,
                    troubleshootingDataStore
                ) { getFullScreenIntentState() }
            }
            val locationState = remember {
                CheckState(LOCATION_CHECK_STATE, troubleshootingDataStore) { getLocationState() }
            }

            val postNotificationsState = rememberPermissionState(permission.POST_NOTIFICATIONS)
            val cameraState = rememberPermissionState(permission.CAMERA)
            val microphoneState = rememberPermissionState(permission.RECORD_AUDIO)
            val locationPermissionState = rememberMultiplePermissionsState(
                listOf(
                    permission.ACCESS_FINE_LOCATION,
                    permission.ACCESS_COARSE_LOCATION
                )
            )

            LifecycleCheckerEffect(
                checks = listOf<CheckState<out Any>>(
                    batteryOptimizationState,
                    alarmState,
                    backgroundRestrictionState,
                    storageState,
                    permanentSocketState,
                    backupState,
                    fullScreenIntentState,
                    locationState
                )
            )

            val troubleshootingItems: MutableState<List<TroubleshootingItemType>> = remember {
                mutableStateOf(mutableListOf())
            }

            LaunchedEffect(Unit) {
                val list: ArrayList<Triple<Boolean, Boolean, TroubleshootingItemType>> =
                    ArrayList() // triple is (valid, critical, TroubleshootItemType)
                if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                    list.add(
                        Triple(
                            postNotificationsState.status.isGranted,
                            true,
                            TroubleshootingItemType.NOTIFICATIONS
                        )
                    )
                } else {
                    list.add(Triple(true, false, TroubleshootingItemType.NOTIFICATIONS))
                }

                if (VERSION.SDK_INT >= VERSION_CODES.M) {
                    list.add(
                        Triple(
                            cameraState.status.isGranted,
                            false,
                            TroubleshootingItemType.CAMERA
                        )
                    )
                }

                if (VERSION.SDK_INT >= VERSION_CODES.M) {
                    list.add(
                        Triple(
                            microphoneState.status.isGranted,
                            false,
                            TroubleshootingItemType.MICROPHONE
                        )
                    )
                }

                list.add(Triple(locationState.valid, false, TroubleshootingItemType.LOCATION))
                if (VERSION.SDK_INT >= VERSION_CODES.M) {
                    list.add(
                        Triple(
                            locationPermissionState.allPermissionsGranted,
                            false,
                            TroubleshootingItemType.LOCATION_PERMISSIONS
                        )
                    )
                }

                if (VERSION.SDK_INT >= VERSION_CODES.M) {
                    list.add(
                        Triple(
                            batteryOptimizationState.valid,
                            true,
                            TroubleshootingItemType.BATTERY_OPTIMIZATION
                        )
                    )
                }

                if (VERSION.SDK_INT >= VERSION_CODES.P) {
                    list.add(
                        Triple(
                            backgroundRestrictionState.valid,
                            true,
                            TroubleshootingItemType.BACKGROUND_RESTRICTION
                        )
                    )
                }

                if (VERSION.SDK_INT >= VERSION_CODES.S) {
                    list.add(Triple(alarmState.valid, true, TroubleshootingItemType.ALARM))
                }

                if (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    list.add(
                        Triple(
                            fullScreenIntentState.valid,
                            true,
                            TroubleshootingItemType.FULL_SCREEN_INTENT
                        )
                    )
                }

                if (!BuildConfig.USE_FIREBASE_LIB || !GoogleServicesUtils.googleServicesAvailable(
                        this@TroubleshootingActivity
                    )
                ) {
                    list.add(
                        Triple(
                            permanentSocketState.valid,
                            true,
                            TroubleshootingItemType.PERMANENT_WEBSOCKET
                        )
                    )
                }

                list.add(Triple(backupState.valid, true, TroubleshootingItemType.BACKUPS))

                list.add(
                    Triple(
                        AppSingleton.getWebsocketConnectivityStateLiveData().value == 2,
                        true,
                        TroubleshootingItemType.CONNECTIVITY
                    )
                )

                list.add(Triple(storageState.valid, true, TroubleshootingItemType.STORAGE))

                list.add(Triple(storageState.valid, true, TroubleshootingItemType.DB_SYNC))

                val updateAvailable = SettingsActivity.isUpdateAvailable()
                val versionOutdated = SettingsActivity.isVersionOutdated()
                list.add(
                    Triple(
                        !updateAvailable && !versionOutdated,
                        versionOutdated,
                        TroubleshootingItemType.UPDATE_AVAILABLE
                    )
                )


                list.sortBy {
                    when {
                        it.first -> 2
                        it.second.not() -> 1
                        else -> 0
                    }
                }
                troubleshootingItems.value = list.map {
                    it.third
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .systemBarsPadding()
                    .displayCutoutPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                FaqLinkHeader(
                    openFaq = {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://olvid.io/faq/".toUri()
                            )
                        )
                    },
                    onBack = {
                        onBackPressedDispatcher.onBackPressed()
                    }
                )

                troubleshootingItems.value.forEach { troubleshootingItem ->
                    when (troubleshootingItem) {

                        TroubleshootingItemType.NOTIFICATIONS -> {
                            val notificationsPermissionLauncher = rememberLauncherForActivityResult(
                                contract = RequestPermission(),
                                onResult = { granted ->
                                    if (VERSION.SDK_INT >= VERSION_CODES.M) {
                                        if (granted.not() && postNotificationsState.status.shouldShowRationale.not() && shouldShowRequestPermissionRationale(
                                                permission.POST_NOTIFICATIONS
                                            ).not()
                                        ) {
                                            openAppNotificationSettings(context = context)
                                        }
                                    }
                                }
                            )
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_notifications_valid_title),
                                description = stringResource(
                                    id = R.string.troubleshooting_notifications_valid_description
                                ) + if (BuildConfig.USE_FIREBASE_LIB && GoogleServicesUtils.googleServicesAvailable(
                                        context
                                    ) && !SettingsActivity.disablePushNotifications()
                                ) {
                                    "\n\n" + stringResource(
                                        R.string.dialog_message_about_last_push_notification,
                                        if (ObvFirebaseMessagingService.getLastPushNotificationTimestamp() == null) {
                                            "-"
                                        } else {
                                            StringUtils.getLongNiceDateString(
                                                context,
                                                ObvFirebaseMessagingService.getLastPushNotificationTimestamp()
                                            )
                                        }
                                    ) + "\n" + stringResource(
                                        id = R.string.dialog_message_about_deprioritized_push_notification,
                                        ObvFirebaseMessagingService.getDeprioritizedMessageCount(),
                                        ObvFirebaseMessagingService.getHighPriorityMessageCount()
                                    )
                                } else "",
                                titleInvalid = stringResource(id = R.string.troubleshooting_notifications_invalid_title),
                                descriptionInvalid = stringResource(id = R.string.troubleshooting_notifications_invalid_description),
                                valid = postNotificationsState.status.isGranted
                            ) {
                                TextButton(
                                    onClick = {
                                        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                                            notificationsPermissionLauncher.launch(permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.troubleshooting_request_permission))
                                }
                            }
                        }

                        TroubleshootingItemType.CAMERA -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_camera_valid_title),
                                description = stringResource(id = R.string.troubleshooting_camera_valid_description),
                                titleInvalid = stringResource(id = R.string.troubleshooting_camera_invalid_title),
                                valid = cameraState.status.isGranted,
                                critical = false,
                            ) {
                                TextButton(
                                    onClick = {
                                        cameraState.launchPermissionRequest()
                                        coroutineScope.launch {
                                            delay(100)
                                            if (lifecycle.currentState == RESUMED) {
                                                startSettingsActivity()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.troubleshooting_request_permission))
                                }
                            }
                        }

                        TroubleshootingItemType.MICROPHONE -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_audio_recording_valid_title),
                                description = stringResource(id = R.string.troubleshooting_audio_recording_valid_description),
                                titleInvalid = stringResource(id = R.string.troubleshooting_audio_recording_invalid_title),
                                valid = microphoneState.status.isGranted,
                                critical = false,
                            ) {
                                TextButton(
                                    onClick = {
                                        microphoneState.launchPermissionRequest()
                                        coroutineScope.launch {
                                            delay(100)
                                            if (lifecycle.currentState == RESUMED) {
                                                startSettingsActivity()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.troubleshooting_request_permission))
                                }
                            }
                        }

                        TroubleshootingItemType.LOCATION -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_location_valid_title),
                                description = stringResource(id = R.string.troubleshooting_location_valid_description),
                                titleInvalid = stringResource(id = R.string.troubleshooting_location_invalid_title),
                                valid = locationState.valid,
                                critical = false,
                            ) {
                                TextButton(
                                    onClick = {
                                        startActivity(Intent(ACTION_LOCATION_SOURCE_SETTINGS))
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.dialog_title_enable_location))
                                }
                            }
                        }

                        TroubleshootingItemType.LOCATION_PERMISSIONS -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_location_permissions_valid_title),
                                description = stringResource(id = R.string.troubleshooting_location_permissions_valid_description),
                                titleInvalid = stringResource(id = R.string.troubleshooting_location_permissions_invalid_title),
                                valid = locationPermissionState.allPermissionsGranted,
                                critical = false,
                            ) {
                                TextButton(
                                    onClick = {
                                        locationPermissionState.launchMultiplePermissionRequest()
                                        coroutineScope.launch {
                                            delay(100)
                                            if (lifecycle.currentState == RESUMED) {
                                                startSettingsActivity()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.troubleshooting_request_permission))
                                }
                            }
                        }

                        TroubleshootingItemType.BATTERY_OPTIMIZATION -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_battery_optimization_valid_title),
                                description = stringResource(id = R.string.troubleshooting_battery_optimization_valid_description),
                                descriptionInvalid = stringResource(id = R.string.troubleshooting_battery_optimization_invalid_description),
                                valid = batteryOptimizationState.valid,
                                checkState = batteryOptimizationState
                            ) {
                                TextButton(
                                    onClick = {
                                        startSettingsActivity(
                                            ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                        )
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.troubleshooting_request_permission))
                                }
                            }
                        }

                        TroubleshootingItemType.BACKGROUND_RESTRICTION -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_background_restriction_valid_title),
                                description = stringResource(
                                    id = R.string.troubleshooting_background_restriction_valid_description
                                ),
                                descriptionInvalid = stringResource(id = R.string.troubleshooting_background_restriction_invalid_description),
                                valid = backgroundRestrictionState.valid,
                                checkState = backgroundRestrictionState
                            ) {
                                TextButton(
                                    onClick = {
                                        startSettingsActivity()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.button_label_app_settings))
                                }
                            }
                        }

                        TroubleshootingItemType.ALARM -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_alarm_valid_title),
                                description = stringResource(
                                    id = R.string.troubleshooting_alarm_valid_description
                                ),
                                descriptionInvalid = stringResource(id = R.string.troubleshooting_alarm_invalid_description),
                                valid = alarmState.valid,
                                checkState = alarmState
                            ) {
                                TextButton(
                                    onClick = {
                                        startSettingsActivity(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.button_label_app_settings))
                                }
                            }
                        }

                        TroubleshootingItemType.FULL_SCREEN_INTENT -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_incoming_call_valid_title),
                                description = stringResource(id = R.string.troubleshooting_incoming_call_valid_description),
                                descriptionInvalid = stringResource(id = R.string.troubleshooting_incoming_call_invalid_description),
                                valid = fullScreenIntentState.valid,
                                checkState = fullScreenIntentState
                            ) {
                                TextButton(
                                    onClick = {
                                        startSettingsActivity(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.button_label_app_settings))
                                }
                            }
                        }

                        TroubleshootingItemType.PERMANENT_WEBSOCKET -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_google_valid_title),
                                description = stringResource(id = R.string.troubleshooting_google_valid_description),
                                descriptionInvalid = stringResource(id = R.string.troubleshooting_google_invalid_description),
                                valid = permanentSocketState.valid,
                                checkState = permanentSocketState
                            ) {
                                TextButton(
                                    onClick = {
                                        SettingsActivity.setUsePermanentWebSocket(true)
                                        startService(
                                            Intent(
                                                this@TroubleshootingActivity,
                                                UnifiedForegroundService::class.java
                                            )
                                        )
                                        permanentSocketState.refreshStatus()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.button_label_enable_websocket))
                                }
                            }
                        }

                        TroubleshootingItemType.BACKUPS -> {
                            val backupsStateInfo = remember(backupState.getStatus()) {
                                getBackupStateInfo()
                            }
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_olvid_backups_title),
                                description = stringResource(id = R.string.troubleshooting_backup_valid_description),
                                titleInvalid = backupsStateInfo?.title,
                                descriptionInvalid = backupsStateInfo?.description,
                                valid = backupState.valid,
                                critical = backupState.getStatus() == 2
                                        || SettingsActivity.backupsV2Status == SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_NOT_CONFIGURED,
                                checkState = backupState
                            ) {
                                TextButton(
                                    onClick = {
                                        val intent = Intent(
                                            this@TroubleshootingActivity,
                                            SettingsActivity::class.java
                                        )
                                        intent.putExtra(
                                            SettingsActivity.SUB_SETTING_PREF_KEY_TO_OPEN_INTENT_EXTRA,
                                            SettingsActivity.PREF_HEADER_KEY_BACKUP
                                        )
                                        startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.button_label_backup_settings))
                                }
                            }
                        }

                        TroubleshootingItemType.CONNECTIVITY -> {
                            var ping by remember {
                                mutableStateOf("-")
                            }
                            val connectivityState by AppSingleton.getWebsocketConnectivityStateLiveData()
                                .observeAsState()
                            PingListener(connectivityState == 2) { pingValue ->
                                ping = when (pingValue) {
                                    -1L -> {
                                        getString(R.string.label_over_max_ping_delay, 5)
                                    }

                                    0L -> {
                                        "-"
                                    }

                                    else -> {
                                        getString(R.string.label_ping_delay, pingValue)
                                    }
                                }
                            }
                            TroubleShootItem(
                                title = when (connectivityState) {
                                    2 -> stringResource(id = R.string.label_ping_connectivity_connected)
                                    1 -> stringResource(id = R.string.label_ping_connectivity_connecting)
                                    else -> stringResource(id = R.string.label_ping_connectivity_none)
                                },
                                description = ping, valid = connectivityState == 2
                            ) {
                            }
                        }


                        TroubleshootingItemType.STORAGE -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_storage_valid_title),
                                description = stringResource(
                                    id = R.string.troubleshooting_storage_valid_description,
                                    Formatter.formatShortFileSize(
                                        context,
                                        AvailableSpaceHelper.getAvailableSpace() ?: 0
                                    )
                                ),
                                titleInvalid = stringResource(id = R.string.troubleshooting_storage_invalid_title),
                                descriptionInvalid = stringResource(
                                    id = R.string.troubleshooting_storage_invalid_description,
                                    Formatter.formatShortFileSize(
                                        context,
                                        AvailableSpaceHelper.getAvailableSpace() ?: 0
                                    )
                                ),
                                valid = storageState.valid,
                                checkState = storageState
                            ) {
                                TextButton(
                                    onClick = {
                                        if (VERSION.SDK_INT >= VERSION_CODES.N_MR1) {
                                            startActivity(Intent(StorageManager.ACTION_MANAGE_STORAGE))
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.button_label_manage_storage))
                                }
                            }
                        }

                        TroubleshootingItemType.DB_SYNC -> {
                            TroubleShootItem(
                                title = stringResource(id = R.string.troubleshooting_storage_db_sync_title),
                                description = stringResource(id = R.string.troubleshooting_storage_db_sync_description),
                                valid = true,
                                additionalContent = {
                                    var inProgress by remember { mutableStateOf(false) }
                                    AnimatedVisibility(visible = !inProgress) {
                                        TextButton(
                                            onClick = {
                                                inProgress = true
                                                App.runThread {
                                                    try {
                                                        AppDatabaseOpenCallback.syncEngineDatabases(
                                                            AppSingleton.getEngine(),
                                                            AppDatabase.getInstance()
                                                        )
                                                        Thread.sleep(1000)
                                                    } catch (_: Exception) {
                                                    }
                                                    inProgress = false
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = colorResource(R.color.almostBlack)
                                            )
                                        ) {
                                            Text(text = stringResource(id = R.string.button_label_check_now))
                                        }
                                    }
                                    AnimatedVisibility(visible = inProgress) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                                .size(24.dp),
                                            color = colorResource(id = R.color.olvid_gradient_light),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            ) { }
                        }

                        TroubleshootingItemType.UPDATE_AVAILABLE -> {
                            val isUpdateAvailable = SettingsActivity.isUpdateAvailable()
                            val isOutdated = SettingsActivity.isVersionOutdated()
                            TroubleShootItem(
                                title = when {
                                    isOutdated -> stringResource(R.string.dialog_title_outdated_version)
                                    isUpdateAvailable -> stringResource(R.string.dialog_title_update_available)
                                    else -> stringResource(R.string.troubleshooting_update_valid_title)
                                },
                                description = when {
                                    isOutdated -> stringResource(R.string.dialog_message_outdated_version)
                                    isUpdateAvailable -> stringResource(R.string.dialog_message_update_available)
                                    else -> stringResource(R.string.explanation_latest_olvid_version)
                                },
                                valid = !isUpdateAvailable && !isOutdated,
                                critical = isOutdated
                            ) {
                                TextButton(
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = colorResource(R.color.almostBlack)
                                    ),
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    "market://details?id=${context.packageName}".toUri()
                                                )
                                            )
                                        } catch (_: Exception) {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    "https://play.google.com/store/apps/details?id=${context.packageName}".toUri()
                                                )
                                            )
                                        }
                                    }
                                ) {
                                    Text(text = stringResource(R.string.menu_check_update))
                                }

                            }
                        }
                    }
                }

                AppVersionHeader(betaEnabled = SettingsActivity.betaFeaturesEnabled)

                RestartAppButton()
            }
        }
    }
}

fun Activity.startSettingsActivity(action: String = ACTION_APPLICATION_DETAILS_SETTINGS) =
    startActivity(
        Intent(
            action,
            Uri.fromParts("package", packageName, null)
        ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent().apply {
        when {
            VERSION.SDK_INT >= VERSION_CODES.O -> {
                action = ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }

            else -> {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
            }
        }
    }
    context.startActivity(intent)
}