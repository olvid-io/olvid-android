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

package io.olvid.messenger.onboarding.flow

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.EngineAPI.ListenerPriority
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.ObvBytesKey
import io.olvid.engine.engine.types.ObvDeviceBackupForRestore
import io.olvid.engine.engine.types.ObvDeviceList
import io.olvid.engine.engine.types.ObvProfileBackupsForRestore
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.engine.engine.types.sync.ObvProfileBackupSnapshot
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.customClasses.DeviceBackupProfile
import io.olvid.messenger.customClasses.KeycloakInfo
import io.olvid.messenger.customClasses.ProfileBackupSnapshot
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.backups.AppDeviceSnapshot
import io.olvid.messenger.settings.composables.ProfilePictureLabelAndKey
import net.openid.appauth.AuthState
import java.util.HashMap
import java.util.concurrent.atomic.AtomicInteger


class BackupsV2ViewModel : ViewModel() {
    val credentialManagerAvailable = mutableStateOf<Boolean?>(null)

    val backupKey: MutableState<String?> = mutableStateOf(null)
    val backupKeyCheckState: MutableState<BackupKeyCheckState> = mutableStateOf(BackupKeyCheckState.NONE)
    val backupProfileSnapshotsFetchState : MutableState<BackupSnapshotsFetchState> = mutableStateOf(BackupSnapshotsFetchState.NONE)
    val backupRestoreState : MutableState<BackupRestoreState> = mutableStateOf(BackupRestoreState.SUCCESS)

    val deviceBackup: MutableState<List<DeviceBackupProfile>> = mutableStateOf(emptyList())
    val profileSnapshots: MutableState<List<ProfileBackupSnapshot>> = mutableStateOf(emptyList())
    val selectedDeviceBackupProfile: MutableState<DeviceBackupProfile?> = mutableStateOf(null)
    val selectedProfileDeviceList: MutableState<ObvDeviceList?> = mutableStateOf(null)
    val keycloakInfo: MutableState<KeycloakInfo?> = mutableStateOf(null)

    private val fetchCount = AtomicInteger(0)
    private var selectedSnapshotToRestore: ProfileBackupSnapshot? = null
    private var keycloakAuthState: AuthState? = null

    fun checkBackupSeed() {
        backupKey.value?.let {
            val fetchId = fetchCount.addAndGet(1)
            deviceBackup.value = emptyList()
            backupKeyCheckState.value = BackupKeyCheckState.CHECKING
            App.runThread {
                try {
                    val obvDeviceBackupForRestore = AppSingleton.getEngine().fetchDeviceBackup(BuildConfig.SERVER_NAME, it)
                    if (fetchId != fetchCount.get()) {
                        return@runThread
                    }

                    when (obvDeviceBackupForRestore.status) {
                        ObvDeviceBackupForRestore.Status.SUCCESS -> Unit

                        ObvDeviceBackupForRestore.Status.NETWORK_ERROR,
                        ObvDeviceBackupForRestore.Status.ERROR -> {
                            backupKeyCheckState.value = BackupKeyCheckState.ERROR
                            return@runThread
                        }

                        ObvDeviceBackupForRestore.Status.PERMANENT_ERROR -> {
                            backupKeyCheckState.value = BackupKeyCheckState.UNKNOWN
                            return@runThread
                        }
                    }

                    val nickNames = (obvDeviceBackupForRestore.appDeviceBackupSnapshot as? AppDeviceSnapshot)?.owned_identities

                    var ownedIdentities = AppDatabase.getInstance().ownedIdentityDao().all.map { it.bytesOwnedIdentity }

                    deviceBackup.value = obvDeviceBackupForRestore.profiles.map { obvDeviceBackupProfile ->
                        DeviceBackupProfile(
                            bytesProfileIdentity = obvDeviceBackupProfile.bytesProfileIdentity,
                            nickName = nickNames?.get(ObvBytesKey(obvDeviceBackupProfile.bytesProfileIdentity))?.custom_name,
                            identityDetails = obvDeviceBackupProfile.identityDetails.identityDetails,
                            keycloakManaged = obvDeviceBackupProfile.keycloakManaged,
                            photo = obvDeviceBackupProfile.identityDetails.photoUrl?.let {
                                App.absolutePathFromRelative(it)
                            } ?: obvDeviceBackupProfile.identityDetails.photoServerLabel?.let { label ->
                                obvDeviceBackupProfile.identityDetails.photoServerKey?.let { key ->
                                    ProfilePictureLabelAndKey(identity = obvDeviceBackupProfile.bytesProfileIdentity, photoLabel = label, photoKey = key)
                                }
                            },
                            profileAlreadyPresent = ownedIdentities.any { bytesIdentity -> bytesIdentity.contentEquals(obvDeviceBackupProfile.bytesProfileIdentity) },
                            profileBackupSeed = obvDeviceBackupProfile.profileBackupSeed
                        )
                    }
                    backupKeyCheckState.value = BackupKeyCheckState.DEVICE_KEY

                } catch (e: Exception) {
                    Logger.x(e)
                    backupKeyCheckState.value = BackupKeyCheckState.NONE
                }
            }
        }
    }

    fun resetBackupKey() {
        backupKey.value = null
        backupKeyCheckState.value = BackupKeyCheckState.NONE
        deviceBackup.value = emptyList()
    }

    fun fetchProfileSnapshots(deviceBackupProfile: DeviceBackupProfile) {
        val fetchId = fetchCount.addAndGet(1)
        selectedDeviceBackupProfile.value = deviceBackupProfile
        backupProfileSnapshotsFetchState.value = BackupSnapshotsFetchState.FETCHING
        profileSnapshots.value = emptyList()
        App.runThread {
            try {
                val obvProfileBackupsForRestore = AppSingleton.getEngine().fetchProfileBackups(deviceBackupProfile.bytesProfileIdentity, deviceBackupProfile.profileBackupSeed)
                if (fetchId != fetchCount.get()) {
                    return@runThread
                }

                when (obvProfileBackupsForRestore.status) {
                    ObvProfileBackupsForRestore.Status.NETWORK_ERROR,
                    ObvProfileBackupsForRestore.Status.ERROR -> {
                        backupProfileSnapshotsFetchState.value = BackupSnapshotsFetchState.ERROR
                        profileSnapshots.value = emptyList()
                        return@runThread
                    }
                    ObvProfileBackupsForRestore.Status.PERMANENT_ERROR, // in case of permanent error simply return an empty list as if successful with no results --> there is no point in retrying
                    ObvProfileBackupsForRestore.Status.SUCCESS,
                    ObvProfileBackupsForRestore.Status.TRUNCATED -> Unit
                }

                profileSnapshots.value =
                    obvProfileBackupsForRestore.snapshots?.map { obvProfileBackupForRestore ->
                        ProfileBackupSnapshot(
                            threadId = obvProfileBackupForRestore.bytesBackupThreadId,
                            version = obvProfileBackupForRestore.version,
                            timestamp = obvProfileBackupForRestore.timestamp,
                            thisDevice =  obvProfileBackupForRestore.fromThisDevice,
                            deviceName = obvProfileBackupForRestore.additionalInfo[ObvProfileBackupSnapshot.INFO_DEVICE_NAME],
                            platform = obvProfileBackupForRestore.additionalInfo[ObvProfileBackupSnapshot.INFO_PLATFORM],
                            contactCount = obvProfileBackupForRestore.contactCount,
                            groupCount = obvProfileBackupForRestore.groupCount,
                            keycloakStatus = obvProfileBackupForRestore.keycloakStatus,
                            keycloakInfo = if (obvProfileBackupForRestore.keycloakServerUrl != null && obvProfileBackupForRestore.keycloakClientId != null) {
                                KeycloakInfo(obvProfileBackupForRestore.keycloakServerUrl, obvProfileBackupForRestore.keycloakClientId, obvProfileBackupForRestore.keycloakClientSecret)
                            } else {
                                null
                            },
                            snapshot = obvProfileBackupForRestore.snapshot,
                        )
                    } ?: emptyList()
                selectedProfileDeviceList.value = obvProfileBackupsForRestore.deviceList

                backupProfileSnapshotsFetchState.value = if (obvProfileBackupsForRestore.status == ObvProfileBackupsForRestore.Status.TRUNCATED)
                    BackupSnapshotsFetchState.TRUNCATED
                else
                    BackupSnapshotsFetchState.NONE
            } catch (e: Exception) {
                Logger.x(e)
                backupProfileSnapshotsFetchState.value = BackupSnapshotsFetchState.NONE
            }
        }
    }

    fun retryFetchProfileSnapshots() {
        selectedDeviceBackupProfile.value?.let {
            fetchProfileSnapshots(it)
        }
    }

    var engineListenerForRestore : EngineNotificationListener? = null

    fun restoreSelectedSnapshot() {
        if (selectedDeviceBackupProfile.value == null) {
            backupRestoreState.value = BackupRestoreState.FAILED
        } else {
            selectedDeviceBackupProfile.value?.let { profile ->
                selectedSnapshotToRestore?.let { snapshot ->
                    backupRestoreState.value = BackupRestoreState.RESTORING

                    engineListenerForRestore = object: SimpleEngineNotificationListener(EngineNotifications.ENGINE_SNAPSHOT_RESTORATION_FINISHED) {
                        override fun callback(userInfo: HashMap<String?, in Any>?) {
                            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.ENGINE_SNAPSHOT_RESTORATION_FINISHED, this)
                            engineListenerForRestore = null
                            backupRestoreState.value = BackupRestoreState.SUCCESS
                        }
                    }
                    AppSingleton.getEngine().addNotificationListener(EngineNotifications.ENGINE_SNAPSHOT_RESTORATION_FINISHED, engineListenerForRestore, ListenerPriority.LOW)

                    App.runThread {
                        if (AppSingleton.getEngine().restoreProfile(
                                snapshot.snapshot,
                                AppSingleton.DEFAULT_DEVICE_DISPLAY_NAME,
                                keycloakAuthState?.jsonSerializeString(),
                            )
                        ) {
                            deviceBackup.value = deviceBackup.value.map { deviceBackupProfile ->
                                if (deviceBackupProfile.bytesProfileIdentity.contentEquals(profile.bytesProfileIdentity)) {
                                    deviceBackupProfile.copy(
                                        profileAlreadyPresent = true,
                                    )
                                } else {
                                    deviceBackupProfile
                                }
                            }
                        } else {
                            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.ENGINE_SNAPSHOT_RESTORATION_FINISHED, engineListenerForRestore)
                            backupRestoreState.value = BackupRestoreState.FAILED
                        }
                    }
                }
            }
        }
    }

    fun setSnapshotToRestore(snapshot: ProfileBackupSnapshot) {
        this.selectedSnapshotToRestore = snapshot
        this.keycloakAuthState = null
        this.keycloakInfo.value = snapshot.keycloakInfo
    }

    fun setKeycloakAuthState(authState: AuthState) {
        this.keycloakAuthState = authState
    }
}

enum class BackupKeyCheckState {
    NONE,
    CHECKING,
    DEVICE_KEY,
    //    PROFILE_KEY, // not used for now
    UNKNOWN,
    ERROR, // typically, for network errors, try again
}

enum class BackupSnapshotsFetchState {
    NONE,
    FETCHING,
    TRUNCATED, // fetch successful, but some snapshots are missing
    ERROR, // typically, for network errors, try again
}

enum class BackupRestoreState {
    RESTORING,
    SUCCESS,
    FAILED,
}