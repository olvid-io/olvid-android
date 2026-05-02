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

package io.olvid.messenger.services

import android.content.Context
import android.content.RestrictionsManager
import io.olvid.messenger.App

object MDMConfigurationSingleton {

    private const val KEYCLOAK_CONFIGURATION_URI = "keycloak_configuration_uri"
    private const val DISABLE_NEW_VERSION_NOTIFICATION = "disable_new_version_notification"
    private const val SETTINGS_CONFIGURATION_URI = "settings_configuration_uri"
    private const val ALTERNATE_TURN_SERVER_URL = "alternate_turn_server_url"
    private const val ALTERNATE_TURN_SERVER_USERNAME = "alternate_turn_server_username"
    private const val ALTERNATE_TURN_SERVER_PASSWORD = "alternate_turn_server_password"
    private const val USER_AGENT_OVERRIDE = "user_agent_override"
    private const val USER_AGENT_OVERRIDE_FOR_LINK_PREVIEWS = "user_agent_override_for_link_previews"
    private const val REQUIRE_LOCK_SCREEN = "require_lock_screen"
    private const val ALLOW_DISABLE_LOCK_SCREEN = "allow_disable_lock_screen"

    @Volatile
    private var config: Config? = null

    private fun config(): Config = config ?: synchronized(this) {
        config ?: Config().also { config = it }
    }

    @JvmStatic
    fun getKeycloakConfigurationUri(): String? = config().keycloakConfigurationUri

    @JvmStatic
    fun getDisableNewVersionNotification(): Boolean = config().disableNewVersionNotification

    @JvmStatic
    fun getSettingsConfigurationUri(): String? = config().settingsConfigurationUri

    @JvmStatic
    fun getAlternateTurnServerUrl(): String? = config().alternateTurnServerUrl

    @JvmStatic
    fun getAlternateTurnServerUsername(): String? = config().alternateTurnServerUsername

    @JvmStatic
    fun getAlternateTurnServerPassword(): String? = config().alternateTurnServerPassword

    @JvmStatic
    fun getUserAgentOverride(): String? = config().userAgentOverride

    @JvmStatic
    fun overrideUserAgentForLinkPreviews(): Boolean = config().userAgentOverrideForLinkPreviews

    @JvmStatic
    fun isLockScreenRequired(): Boolean = config().requireLockScreen

    @JvmStatic
    fun getAllowDisableLockScreenString(): String? = config().allowDisableLockScreenString


    private class Config {
        val keycloakConfigurationUri: String?
        val disableNewVersionNotification: Boolean
        val settingsConfigurationUri: String?
        val alternateTurnServerUrl: String?
        val alternateTurnServerUsername: String?
        val alternateTurnServerPassword: String?
        val userAgentOverride: String?
        val userAgentOverrideForLinkPreviews: Boolean
        val requireLockScreen: Boolean
        val allowDisableLockScreenString: String?

        init {
            val restrictionsManager = App.getContext().getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
            val restrictions = restrictionsManager.applicationRestrictions

            if (restrictions != null) {
                keycloakConfigurationUri = if (restrictions.containsKey(KEYCLOAK_CONFIGURATION_URI)) restrictions.getString(KEYCLOAK_CONFIGURATION_URI) else null
                disableNewVersionNotification = restrictions.containsKey(DISABLE_NEW_VERSION_NOTIFICATION) && restrictions.getBoolean(DISABLE_NEW_VERSION_NOTIFICATION, false)
                settingsConfigurationUri = if (restrictions.containsKey(SETTINGS_CONFIGURATION_URI)) restrictions.getString(SETTINGS_CONFIGURATION_URI) else null
                if (restrictions.containsKey(ALTERNATE_TURN_SERVER_URL) && restrictions.containsKey(ALTERNATE_TURN_SERVER_USERNAME) && restrictions.containsKey(ALTERNATE_TURN_SERVER_PASSWORD)) {
                    alternateTurnServerUrl = restrictions.getString(ALTERNATE_TURN_SERVER_URL)
                    alternateTurnServerUsername = restrictions.getString(ALTERNATE_TURN_SERVER_USERNAME)
                    alternateTurnServerPassword = restrictions.getString(ALTERNATE_TURN_SERVER_PASSWORD)
                } else {
                    alternateTurnServerUrl = null
                    alternateTurnServerUsername = null
                    alternateTurnServerPassword = null
                }
                if (restrictions.containsKey(USER_AGENT_OVERRIDE)) {
                    userAgentOverride = restrictions.getString(USER_AGENT_OVERRIDE)
                    userAgentOverrideForLinkPreviews = restrictions.containsKey(USER_AGENT_OVERRIDE_FOR_LINK_PREVIEWS) && restrictions.getBoolean(USER_AGENT_OVERRIDE_FOR_LINK_PREVIEWS, false)
                } else {
                    userAgentOverride = null
                    userAgentOverrideForLinkPreviews = false
                }
                requireLockScreen = restrictions.containsKey(REQUIRE_LOCK_SCREEN) && restrictions.getBoolean(REQUIRE_LOCK_SCREEN, false)
                allowDisableLockScreenString = restrictions.getString(ALLOW_DISABLE_LOCK_SCREEN, null)
            } else {
                keycloakConfigurationUri = null
                disableNewVersionNotification = false
                settingsConfigurationUri = null
                alternateTurnServerUrl = null
                alternateTurnServerUsername = null
                alternateTurnServerPassword = null
                userAgentOverride = null
                userAgentOverrideForLinkPreviews = false
                requireLockScreen = false
                allowDisableLockScreenString = null
            }
        }
    }
}
