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

package io.olvid.messenger.main.tips

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.troubleshooting.shouldShowTroubleshootingTip

fun installTimestamp() : Long? = runCatching {
    val context = App.getContext()
    val packageManager = context.packageManager
    val packageName = context.packageName
    val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
    packageInfo.firstInstallTime
}.getOrNull()

class TipsViewModel : ViewModel() {
    companion object {
        private const val READ_RECEIPT_MUTE_DURATION = 2 * 86_400_000L
        private const val TROUBLESHOOTING_MUTE_DURATION = 7 * 86_400_000L
        private const val OTHER_DEVICE_EXPIRING_SOON_THRESHOLD = 7 * 86_400_000L
        private const val OFFLINE_DEVICE_ALERT_THRESHOLD = 30 * 86_400_000L
        private const val EXPIRING_DEVICE_MUTE_DURATION = 2 * 86_400_000L
        private const val OFFLINE_DEVICE_MUTE_DURATION = 30 * 86_400_000L
        private const val RATING_MUTE_DURATION = 180 * 86_400_000L
        private const val RATING_INSTALL_MIN_AGE = 30 * 86_400_000L
    }

    var tipToShow: Tip? by mutableStateOf(null)
    var deviceExpirationDays by mutableIntStateOf(0)
    private val firstInstallTimestamp = installTimestamp() ?: 0L

    fun refreshTipToShow(activity: ComponentActivity) {
        AppSingleton.getBytesCurrentIdentity()?.let { bytesOwnedIdentity ->
            if (KeycloakManager.authenticationRequiredOwnedIdentities.contains(BytesKey(bytesOwnedIdentity))) {
                tipToShow = Tip.AUTHENTICATION_REQUIRED
                return
            }
        }
        if (SettingsActivity.isVersionOutdated()) {
            tipToShow = Tip.VERSION_OUTDATED
            return
        }
        if (SettingsActivity.isUpdateAvailable() && SettingsActivity.isUpdateAvailableTipDismissed.not()) {
            tipToShow = Tip.UPDATE_AVAILABLE
            return
        }
        val contactCount = AppDatabase.getInstance().contactDao().countAll()
        if (!SettingsActivity.defaultSendReadReceipt) {
            val lastReadReceiptAnswer = SettingsActivity.lastReadReceiptTipTimestamp
            if (lastReadReceiptAnswer != -1L
                && (System.currentTimeMillis() - lastReadReceiptAnswer > READ_RECEIPT_MUTE_DURATION)
                && contactCount > 0
            ) {
                tipToShow = Tip.PROMPT_FOR_READ_RECEIPTS
                return
            }
        }
        AppSingleton.getBytesCurrentIdentity()?.let { bytesCurrentIdentity ->
            val devices =
                AppDatabase.getInstance().ownedDeviceDao().getAllSync(bytesCurrentIdentity)

            if (System.currentTimeMillis() - SettingsActivity.lastExpiringDeviceTipTimestamp > EXPIRING_DEVICE_MUTE_DURATION) {
                devices.filter {
                    it.currentDevice.not()
                            && it.expirationTimestamp?.let { expiration ->
                        expiration - System.currentTimeMillis() in 0..OTHER_DEVICE_EXPIRING_SOON_THRESHOLD
                    } ?: false
                }.minByOrNull {
                    it.expirationTimestamp!!
                }?.let { expiringDevice ->
                    deviceExpirationDays =
                        ((expiringDevice.expirationTimestamp!! - System.currentTimeMillis()) / 86_400_000L).toInt()
                            .coerceAtLeast(0) + 1
                    tipToShow = Tip.EXPIRING_DEVICE
                    return
                }
            }

            if (System.currentTimeMillis() - SettingsActivity.lastOfflineDeviceTipTimestamp > OFFLINE_DEVICE_MUTE_DURATION) {
                devices.firstOrNull {
                    it.currentDevice.not()
                            && it.lastRegistrationTimestamp?.let { lastSeen ->
                        System.currentTimeMillis() - lastSeen > OFFLINE_DEVICE_ALERT_THRESHOLD
                    } ?: false
                }?.let {
                    tipToShow = Tip.OFFLINE_DEVICE
                    return
                }
            }
        }

        val backupStatus = SettingsActivity.backupsV2Status
        if (backupStatus == SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_NOT_CONFIGURED) {
            if (contactCount > 1) {
                // only prompt to configure backups once the user has at least two contacts among all their profiles
                tipToShow = Tip.CONFIGURE_BACKUPS
                return
            }
        }
        if (backupStatus == SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_KEY_REMINDER) {
            if (AppSingleton.getEngine().deviceBackupSeed != null) {
                tipToShow = Tip.WRITE_BACKUP_KEY
                return
            }
        }

        if (System.currentTimeMillis() - SettingsActivity.lastRatingTipTimestamp > RATING_MUTE_DURATION
            && System.currentTimeMillis() - firstInstallTimestamp > RATING_INSTALL_MIN_AGE
            && contactCount > 10
            && AppDatabase.getInstance().messageDao().countOutbound() > 50
        ) {
            tipToShow = Tip.PLAY_STORE_REVIEW
            return
        }

        if (System.currentTimeMillis() - SettingsActivity.lastTroubleshootingTipTimestamp > TROUBLESHOOTING_MUTE_DURATION
            && activity.shouldShowTroubleshootingTip()
        ) {
            tipToShow = Tip.TROUBLESHOOTING
            return
        }
        if (SettingsActivity.muteNewTranslationsTip.not()) {
            tipToShow = Tip.NEW_TRANSLATIONS
            return
        }
        tipToShow = null
    }


    enum class Tip {
        CONFIGURE_BACKUPS,
        WRITE_BACKUP_KEY,
        TROUBLESHOOTING,
        NEW_TRANSLATIONS,
        EXPIRING_DEVICE,
        AUTHENTICATION_REQUIRED,
        OFFLINE_DEVICE,
        PLAY_STORE_REVIEW,
        PROMPT_FOR_READ_RECEIPTS,
        UPDATE_AVAILABLE,
        VERSION_OUTDATED,
    }
}