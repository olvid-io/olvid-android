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

package io.olvid.messenger.billing

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceScreen
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.AppSingleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object SubscriptionRepository {

    const val QUERY_TIMEOUT = 10_000L

    fun initialize(context: Context) {
    }

    fun reconnect() {
    }

    fun newIdentityAvailableForSubscription(bytesOwnedIdentity: ByteArray) {
    }

    fun loadSubscriptionSettingsHeader(activity: FragmentActivity, preferenceScreen: PreferenceScreen) {
    }

    fun queryFreeTrial(identity: ByteArray) {
        AppSingleton.getEngine().queryFreeTrial(identity)
    }

    fun startFreeTrial(identity: ByteArray) {
        AppSingleton.getEngine().startFreeTrial(identity)
    }

    fun freeTrialEvents(identity: ByteArray): Flow<FreeTrialEvent> = callbackFlow {
        val listener = object : EngineNotificationListener {
            var engineNumber: Long = -1L

            override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
                val bytesOwnedIdentity = userInfo[
                    when (notificationName) {
                        EngineNotifications.FREE_TRIAL_QUERY_SUCCESS -> EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY
                        EngineNotifications.FREE_TRIAL_QUERY_FAILED -> EngineNotifications.FREE_TRIAL_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY
                        EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS -> EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS_BYTES_OWNED_IDENTITY_KEY
                        EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED -> EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED_BYTES_OWNED_IDENTITY_KEY
                        else -> ""
                    }
                ] as? ByteArray

                if (bytesOwnedIdentity?.contentEquals(identity) == true) {
                    when (notificationName) {
                        EngineNotifications.FREE_TRIAL_QUERY_SUCCESS -> {
                            val available = userInfo[EngineNotifications.FREE_TRIAL_QUERY_SUCCESS_AVAILABLE_KEY] as? Boolean
                            trySend(FreeTrialEvent.QuerySuccess(available ?: false))
                        }
                        EngineNotifications.FREE_TRIAL_QUERY_FAILED -> {
                            trySend(FreeTrialEvent.QueryFailed)
                        }
                        EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS -> {
                            trySend(FreeTrialEvent.RetrieveSuccess)
                        }
                        EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED -> {
                            trySend(FreeTrialEvent.RetrieveFailed)
                        }
                    }
                }
            }

            override fun setEngineNotificationListenerRegistrationNumber(number: Long) {
                engineNumber = number
            }

            override fun getEngineNotificationListenerRegistrationNumber(): Long {
                return engineNumber
            }

            override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
                return engineNumber != -1L
            }
        }

        val engine = AppSingleton.getEngine()
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS, listener)
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_FAILED, listener)
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS, listener)
        engine.addNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED, listener)

        awaitClose {
            engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_SUCCESS, listener)
            engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_QUERY_FAILED, listener)
            engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_SUCCESS, listener)
            engine.removeNotificationListener(EngineNotifications.FREE_TRIAL_RETRIEVE_FAILED, listener)
        }
    }


    sealed class FreeTrialEvent {
        data class QuerySuccess(val available: Boolean) : FreeTrialEvent()
        object QueryFailed : FreeTrialEvent()
        object RetrieveSuccess : FreeTrialEvent()
        object RetrieveFailed : FreeTrialEvent()
    }
}