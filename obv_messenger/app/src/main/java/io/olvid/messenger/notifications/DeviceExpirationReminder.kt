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

package io.olvid.messenger.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.olvid.messenger.App
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedDevice
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.time.Duration.Companion.days

class DeviceExpirationReminder(val context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    companion object {
        fun scheduleNotifications(ownedDevice: OwnedDevice) {
            // cancel existing reminders
            WorkManager.getInstance(App.getContext()).cancelAllWorkByTag(String(ownedDevice.bytesDeviceUid))
            AndroidNotificationManager.clearDeviceExpirationNotification(ownedDevice.bytesDeviceUid)

            ownedDevice.expirationTimestamp?.let { expiration ->

                // initial instant notification
                if (expiration > System.currentTimeMillis()) {
                    AndroidNotificationManager.displayDeviceExpirationNotification(
                        ownedDevice.bytesOwnedIdentity,
                        ownedDevice.bytesDeviceUid,
                        ownedDevice.displayName,
                        expiration
                    )
                } else {
                    return
                }

                val data = Data.Builder()
                    .putByteArray(OwnedDevice.BYTES_OWNED_IDENTITY, ownedDevice.bytesOwnedIdentity)
                    .putByteArray(OwnedDevice.BYTES_DEVICE_UID, ownedDevice.bytesDeviceUid)
                    .putString(OwnedDevice.DISPLAY_NAME, ownedDevice.displayName)
                    .putLong(OwnedDevice.EXPIRATION_TIMESTAMP, expiration)
                    .build()

                val msFromExpiration = expiration - System.currentTimeMillis()
                val reminders = arrayListOf<WorkRequest>()
                if (msFromExpiration > 7.days.inWholeMilliseconds) {
                    reminders.add(
                        OneTimeWorkRequestBuilder<DeviceExpirationReminder>()
                            .setInputData(data)
                            .addTag(String(ownedDevice.bytesDeviceUid))
                            .setInitialDelay(msFromExpiration - 7.days.inWholeMilliseconds, MILLISECONDS)
                            .build()
                    )
                }
                if (msFromExpiration > 2.days.inWholeMilliseconds) {
                    reminders.add(
                        OneTimeWorkRequestBuilder<DeviceExpirationReminder>()
                            .setInputData(data)
                            .addTag(String(ownedDevice.bytesDeviceUid))
                            .setInitialDelay(msFromExpiration - 2.days.inWholeMilliseconds, MILLISECONDS)
                            .build()
                    )
                }
                if (reminders.isNotEmpty()) {
                    WorkManager.getInstance(App.getContext()).enqueue(reminders)
                }
            }
        }
    }

    override fun doWork(): Result {
        val bytesOwnedIdentity = inputData.getByteArray(OwnedDevice.BYTES_OWNED_IDENTITY)
        val deviceUid = inputData.getByteArray(OwnedDevice.BYTES_DEVICE_UID)
        val displayName = inputData.getString(OwnedDevice.DISPLAY_NAME)
        val expirationTimestamp = inputData.getLong(OwnedDevice.EXPIRATION_TIMESTAMP, 0)

        bytesOwnedIdentity?.let {
            deviceUid?.let {
                val ownedDevice = AppDatabase.getInstance().ownedDeviceDao().get(bytesOwnedIdentity, deviceUid)
                ownedDevice?.let {
                    if (ownedDevice.expirationTimestamp == expirationTimestamp) {
                        AndroidNotificationManager.displayDeviceExpirationNotification(bytesOwnedIdentity, deviceUid, displayName, expirationTimestamp)
                    }
                }
            }
        }
        return Result.success()
    }
}