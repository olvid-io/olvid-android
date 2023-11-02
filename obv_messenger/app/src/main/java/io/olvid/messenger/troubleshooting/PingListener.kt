/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.main.Utils

@Composable
fun PingListener(connected: Boolean,pingCallback : (lastPing: Long) -> Unit) {

    val pingListener = remember {
        object : EngineNotificationListener {
            var registrationNumber: Long = 0
            override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
                when (notificationName) {
                    EngineNotifications.PING_LOST -> {
                        pingCallback(-1)
                    }
                    EngineNotifications.PING_RECEIVED -> {
                        (userInfo[EngineNotifications.PING_RECEIVED_DELAY_KEY] as Long?)?.let {
                            pingCallback(it)
                        }
                    }
                }
            }

            override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
                this.registrationNumber = registrationNumber
            }

            override fun getEngineNotificationListenerRegistrationNumber(): Long {
                return registrationNumber
            }

            override fun hasEngineNotificationListenerRegistrationNumber(): Boolean =
                registrationNumber != 0L
        }
    }

    DisposableEffect(connected, pingListener) {
        if (connected) {
            Utils.startPinging()
            AppSingleton.getEngine()
                .addNotificationListener(EngineNotifications.PING_LOST, pingListener)
            AppSingleton.getEngine()
                .addNotificationListener(EngineNotifications.PING_RECEIVED, pingListener)
        }
        onDispose {
            pingCallback(-1)
            Utils.stopPinging()
                AppSingleton.getEngine()
                    .removeNotificationListener(EngineNotifications.PING_LOST, pingListener)
                AppSingleton.getEngine()
                    .removeNotificationListener(EngineNotifications.PING_RECEIVED, pingListener)
        }
    }
}