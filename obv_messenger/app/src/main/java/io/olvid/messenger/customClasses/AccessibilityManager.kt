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

package io.olvid.messenger.customClasses

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.preference.PreferenceManager

object AccessibilityManager {

    private const val PREF_KEY_ACCESSIBILITY_WHITELIST = "pref_key_accessibility_whitelist"

    // Well-known, trusted accessibility services that are part of the Android ecosystem?
    // We keep it empty for now, as we also want to protect against malicious services reusing a well-known package identifier
    private val INITIAL_TRUSTED_SERVICES = emptySet<String>()

    var untrustedAccessibilityServices: List<String> by mutableStateOf(emptyList())

    fun refreshUntrustedAccessibilityServices(context: Context): String? {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val userWhitelist = getWhitelist(context)

        untrustedAccessibilityServices = enabledServices.filter { serviceInfo ->
            runCatching {
                val isSystemApp = context.packageManager.getApplicationInfo(
                    serviceInfo.resolveInfo.serviceInfo.packageName,
                    0
                ).flags and ApplicationInfo.FLAG_SYSTEM != 0
                val isUserTrusted = userWhitelist.contains(serviceInfo.id)
                !isSystemApp && !isUserTrusted
            }.getOrDefault(false)
        }.map { it.id }
        return untrustedAccessibilityServices.firstOrNull()
    }

    private fun getSharedPreferences(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun getWhitelist(context: Context): Set<String> {
        return getSharedPreferences(context).getStringSet(PREF_KEY_ACCESSIBILITY_WHITELIST, INITIAL_TRUSTED_SERVICES) ?: INITIAL_TRUSTED_SERVICES
    }

    fun addToWhitelist(context: Context, serviceId: String) {
        val whitelist = getWhitelist(context).toMutableSet()
        if (whitelist.add(serviceId)) {
            getSharedPreferences(context).edit(commit = true) {
                putStringSet(
                    PREF_KEY_ACCESSIBILITY_WHITELIST,
                    whitelist
                )
            }
            refreshUntrustedAccessibilityServices(context)
        }
    }

    fun removeFromWhitelist(context: Context, serviceId: String) {
        val whitelist = getWhitelist(context).toMutableSet()
        if (whitelist.remove(serviceId)) {
            getSharedPreferences(context).edit(commit = true) {
                putStringSet(
                    PREF_KEY_ACCESSIBILITY_WHITELIST,
                    whitelist
                )
            }
            refreshUntrustedAccessibilityServices(context)
        }
    }
}