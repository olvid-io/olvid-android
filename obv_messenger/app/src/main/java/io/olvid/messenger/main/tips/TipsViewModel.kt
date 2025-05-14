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

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.troubleshooting.shouldShowTroubleshootingTip


class TipsViewModel : ViewModel() {
    var tipToShow : Tip? by mutableStateOf(null)

    fun refreshTipToShow(activity: ComponentActivity) {
        val backupStatus = SettingsActivity.backupsV2Status
        if (backupStatus == SettingsActivity.PREF_KEY_BACKUPS_V2_STATUS_NOT_CONFIGURED) {
            if (AppDatabase.getInstance().contactDao().countAll() > 1) {
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
        if (SettingsActivity.muteTroubleshootingTipUntil < System.currentTimeMillis()
            && activity.shouldShowTroubleshootingTip()) {
            tipToShow = Tip.TROUBLESHOOTING
            return
        }
        if (!SettingsActivity.muteNewTranslationsTip) {
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
    }
}