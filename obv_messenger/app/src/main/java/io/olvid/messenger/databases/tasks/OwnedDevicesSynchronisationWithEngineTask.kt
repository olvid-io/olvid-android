/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.databases.tasks

import io.olvid.engine.engine.types.identities.ObvOwnedDevice
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.notifications.DeviceExpirationReminder
import java.util.Objects


class OwnedDevicesSynchronisationWithEngineTask(
    private val bytesOwnedIdentity: ByteArray
) : Runnable {
    companion object {
        private val transferredOrRestoredOwnedIdentities: HashSet<BytesKey> = HashSet()

        fun ownedIdentityWasTransferredOrRestored(bytesOwnedIdentity: ByteArray) {
            transferredOrRestoredOwnedIdentities.add(BytesKey(bytesOwnedIdentity))
        }
    }

    override fun run() {
        val deviceDao = AppDatabase.getInstance().ownedDeviceDao()

        val obvOwnedDevices: List<ObvOwnedDevice> = AppSingleton.getEngine().getOwnedDevices(bytesOwnedIdentity) ?: return
        val dbOwnedDevices: List<OwnedDevice> = deviceDao.getAllSync(bytesOwnedIdentity)
        val dbDeviceMap: MutableMap<BytesKey, OwnedDevice> = HashMap()
        dbOwnedDevices.forEach {
            dbDeviceMap[BytesKey(it.bytesDeviceUid)] = it
        }

        val shouldTrustNewDevices = transferredOrRestoredOwnedIdentities.contains(BytesKey(bytesOwnedIdentity))
        var consumeAutoTrust = false

        obvOwnedDevices.forEach {
            val dbOwnedDevice = dbDeviceMap.remove(BytesKey(it.bytesDeviceUid))
            if (dbOwnedDevice == null) {
                // device not found in app --> insert it as untrusted
                val newOwnedDevice = OwnedDevice(it.bytesOwnedIdentity, it.bytesDeviceUid, it.serverDeviceInfo.displayName, it.currentDevice, it.currentDevice || shouldTrustNewDevices, it.channelConfirmed, it.hasPreKey, it.serverDeviceInfo.lastRegistrationTimestamp, it.serverDeviceInfo.expirationTimestamp)
                deviceDao.insert(newOwnedDevice)
                if (it.currentDevice.not()) {
                    if (shouldTrustNewDevices) {
                        consumeAutoTrust = true
                    } else {
                        AndroidNotificationManager.displayDeviceTrustNotification(newOwnedDevice.bytesOwnedIdentity, newOwnedDevice.bytesDeviceUid, newOwnedDevice.displayName)
                    }
                } else {
                    newOwnedDevice.expirationTimestamp?.let {
                        DeviceExpirationReminder.scheduleNotifications(newOwnedDevice)
                    }
                }
            } else {
                // db found both in engine and in app --> see what has changed
                val previousExpirationTimestamp = dbOwnedDevice.expirationTimestamp
                if (!Objects.equals(it.serverDeviceInfo.displayName, dbOwnedDevice.displayName)) {
                    dbOwnedDevice.displayName = it.serverDeviceInfo.displayName
                    deviceDao.updateDisplayName(dbOwnedDevice.bytesOwnedIdentity, dbOwnedDevice.bytesDeviceUid, dbOwnedDevice.displayName)
                }
                if (it.channelConfirmed != dbOwnedDevice.channelConfirmed) {
                    dbOwnedDevice.channelConfirmed = it.channelConfirmed
                    deviceDao.updateChannelConfirmed(dbOwnedDevice.bytesOwnedIdentity, dbOwnedDevice.bytesDeviceUid, dbOwnedDevice.channelConfirmed)
                }
                if (it.hasPreKey != dbOwnedDevice.hasPreKey) {
                    dbOwnedDevice.hasPreKey = it.hasPreKey
                    deviceDao.updateHasPreKey(dbOwnedDevice.bytesOwnedIdentity, dbOwnedDevice.bytesDeviceUid, dbOwnedDevice.hasPreKey)
                }
                if (it.currentDevice != dbOwnedDevice.currentDevice) {
                    dbOwnedDevice.currentDevice = it.currentDevice
                    deviceDao.updateCurrentDevice(dbOwnedDevice.bytesOwnedIdentity, dbOwnedDevice.bytesDeviceUid, dbOwnedDevice.currentDevice)
                }
                if (!Objects.equals(it.serverDeviceInfo.lastRegistrationTimestamp, dbOwnedDevice.lastRegistrationTimestamp)
                    || !Objects.equals(it.serverDeviceInfo.expirationTimestamp, dbOwnedDevice.expirationTimestamp)) {
                    dbOwnedDevice.lastRegistrationTimestamp = it.serverDeviceInfo.lastRegistrationTimestamp
                    dbOwnedDevice.expirationTimestamp = it.serverDeviceInfo.expirationTimestamp
                    deviceDao.updateTimestamps(dbOwnedDevice.bytesOwnedIdentity, dbOwnedDevice.bytesDeviceUid, dbOwnedDevice.lastRegistrationTimestamp, dbOwnedDevice.expirationTimestamp)
                }
                // notify all untrusted devices so at startup we have the notifications
                if (dbOwnedDevice.currentDevice.not() && dbOwnedDevice.trusted.not()) {
                    AndroidNotificationManager.displayDeviceTrustNotification(dbOwnedDevice.bytesOwnedIdentity, dbOwnedDevice.bytesDeviceUid, dbOwnedDevice.displayName)
                }

                // notify expiration
                if (dbOwnedDevice.currentDevice && dbOwnedDevice.expirationTimestamp != previousExpirationTimestamp) {
                    DeviceExpirationReminder.scheduleNotifications(dbOwnedDevice)
                }
            }
        }

        // remove any remaining device (it is no longer present in the engine)
        dbDeviceMap.values.forEach {
            deviceDao.delete(it)
            AndroidNotificationManager.clearDeviceTrustNotification(it.bytesDeviceUid)
        }

        if (consumeAutoTrust) {
            transferredOrRestoredOwnedIdentities.remove(BytesKey(bytesOwnedIdentity))
        }
    }
}