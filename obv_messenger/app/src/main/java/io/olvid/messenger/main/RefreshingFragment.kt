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

package io.olvid.messenger.main

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import java.util.*

open class RefreshingFragment : Fragment(), OnRefreshListener, EngineNotificationListener {

    val refreshingViewModel: RefreshingViewModel by activityViewModels()
    private var engineNotificationListenerRegistrationNumber: Long? = null

    override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
        engineNotificationListenerRegistrationNumber = registrationNumber
    }

    override fun getEngineNotificationListenerRegistrationNumber(): Long {
        return engineNotificationListenerRegistrationNumber ?: 0
    }

    override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
        return engineNotificationListenerRegistrationNumber != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineNotificationListenerRegistrationNumber = null
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.SERVER_POLLED, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.SERVER_POLLED, this)
    }

    override fun onRefresh() {
        if (AppSingleton.getBytesCurrentIdentity() != null) {
            refreshingViewModel.setRefresh(true)
            AppSingleton.getEngine().downloadMessages(AppSingleton.getBytesCurrentIdentity())
            App.runThread {
                try {
                    Thread.sleep(5000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                Handler(Looper.getMainLooper()).post {
                    if (refreshingViewModel.isRefreshing.value) {
                        refreshingViewModel.setRefresh(false)
                        App.toast(R.string.toast_message_polling_failed, Toast.LENGTH_SHORT)
                    }
                }
            }
        } else {
            refreshingViewModel.setRefresh(false)
        }
    }

    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        if (EngineNotifications.SERVER_POLLED == notificationName) {
            val bytesOwnedIdentity =
                userInfo[EngineNotifications.SERVER_POLLED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
            val success = userInfo[EngineNotifications.SERVER_POLLED_SUCCESS_KEY] as Boolean?
            if (success != null
                && Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())
            ) {
                if (refreshingViewModel.isRefreshing.value) {
                    Handler(Looper.getMainLooper()).post {
                        refreshingViewModel.setRefresh(false)
                        if (!success) {
                            App.toast(R.string.toast_message_polling_failed, Toast.LENGTH_SHORT)
                        }
                    }
                }
            }
        }
    }
}