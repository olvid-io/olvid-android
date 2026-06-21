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

package io.olvid.messenger.owneddetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.AppSingleton

private val NOTIFICATIONS_TO_LISTEN_TO = arrayOf(
    EngineNotifications.OWNED_DEVICE_DISCOVERY_DONE,
)

@Composable
fun DeviceDiscoveryListener(
    onDone: () -> Unit
) {
    DisposableEffect(LocalContext.current) {
        var registrationNumber: Long? = null
        val discoveryListener =
            object : EngineNotificationListener {
                override fun callback(
                    notificationName: String,
                    userInfo: HashMap<String, Any>
                ) {
                    when (notificationName) {
                        EngineNotifications.OWNED_DEVICE_DISCOVERY_DONE -> {
                            onDone()
                        }
                    }
                }

                override fun setEngineNotificationListenerRegistrationNumber(number: Long) {
                    registrationNumber = number
                }

                override fun getEngineNotificationListenerRegistrationNumber(): Long {
                    return registrationNumber ?: 0
                }

                override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
                    return registrationNumber != null
                }
            }
        NOTIFICATIONS_TO_LISTEN_TO.forEach {
            AppSingleton.getEngine()
                .addNotificationListener(it, discoveryListener)
        }

        onDispose {
            NOTIFICATIONS_TO_LISTEN_TO.forEach {
                AppSingleton.getEngine()
                    .removeNotificationListener(it, discoveryListener)
            }
        }
    }
}
