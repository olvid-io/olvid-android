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
package io.olvid.messenger

import io.olvid.engine.engine.Engine
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.identities.ObvGroupV2
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.entity.jsons.JsonSharedSettings
import io.olvid.messenger.databases.tasks.CreateOrUpdateGroupV2Task
import io.olvid.messenger.databases.tasks.UpdateGroupV2PhotoFromEngineTask

class EngineNotificationProcessorForGroupsV2 internal constructor(engine: Engine) :
    EngineNotificationListener {
    private val db: AppDatabase = AppDatabase.getInstance()
    private var registrationNumber: Long? = null

    private var createdGroupEphemeralSettings: JsonExpiration? = null
    fun setCreatedGroupEphemeralSettings(jsonExpiration: JsonExpiration?) {
        this.createdGroupEphemeralSettings = jsonExpiration
    }

    init {
        for (notificationName in arrayOf(
            EngineNotifications.GROUP_V2_CREATED_OR_UPDATED,
            EngineNotifications.GROUP_V2_PHOTO_CHANGED,
            EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED,
            EngineNotifications.GROUP_V2_DELETED,
            EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS
        )) {
            engine.addNotificationListener(notificationName, this)
        }
    }

    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        when (notificationName) {
            EngineNotifications.GROUP_V2_CREATED_OR_UPDATED -> {
                val groupV2 = userInfo[EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_GROUP_KEY] as ObvGroupV2?
                val groupWasJustCreatedByMe = userInfo[EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_NEW_GROUP_KEY] as Boolean?
                val updatedByMe = userInfo[EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_BY_ME_KEY] as Boolean?
                val createdOnOtherDevice = userInfo[EngineNotifications.GROUP_V2_CREATED_OR_UPDATED_CREATED_ON_OTHER_DEVICE] as Boolean?
                if (groupV2 == null || groupWasJustCreatedByMe == null || updatedByMe == null || createdOnOtherDevice == null) {
                    return
                }

                val jsonExpirationSettings : JsonExpiration?
                if (groupWasJustCreatedByMe && !createdOnOtherDevice) {
                    jsonExpirationSettings = createdGroupEphemeralSettings
                    // reset temp settings
                    createdGroupEphemeralSettings = null
                } else {
                    jsonExpirationSettings = null
                }

                CreateOrUpdateGroupV2Task(
                    groupV2,
                    groupWasJustCreatedByMe,
                    updatedByMe,
                    createdOnOtherDevice,
                    false,
                    jsonExpirationSettings
                ).run()
            }

            EngineNotifications.GROUP_V2_PHOTO_CHANGED -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesGroupIdentifier =
                    userInfo[EngineNotifications.GROUP_V2_PHOTO_CHANGED_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
                    return
                }
                App.runThread(
                    UpdateGroupV2PhotoFromEngineTask(
                        bytesOwnedIdentity,
                        bytesGroupIdentifier
                    )
                )
            }

            EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesGroupIdentifier =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                val updating =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_UPDATING_KEY] as Boolean?
                val creating =
                    userInfo[EngineNotifications.GROUP_V2_UPDATE_IN_PROGRESS_CHANGED_CREATING_KEY] as Boolean?
                if (bytesOwnedIdentity == null || bytesGroupIdentifier == null || updating == null || creating == null) {
                    return
                }
                db.group2Dao().updateUpdateInProgress(
                    bytesOwnedIdentity,
                    bytesGroupIdentifier,
                    if (updating) (if (creating) Group2.UPDATE_CREATING else Group2.UPDATE_SYNCING) else Group2.UPDATE_NONE
                )
            }

            EngineNotifications.GROUP_V2_DELETED -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.GROUP_V2_DELETED_BYTES_OWNED_IDENTITY] as ByteArray?
                val bytesGroupIdentifier =
                    userInfo[EngineNotifications.GROUP_V2_DELETED_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                if (bytesOwnedIdentity == null || bytesGroupIdentifier == null) {
                    return
                }
                val group2 = db.group2Dao()[bytesOwnedIdentity, bytesGroupIdentifier]
                if (group2 != null) {
                    db.runInTransaction {
                        val discussion = db.discussionDao()
                            .getByGroupIdentifier(bytesOwnedIdentity, bytesGroupIdentifier)
                        discussion?.lockWithMessage(db)
                        db.group2Dao().delete(group2)
                    }
                }
            }

            EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.KEYCLOAK_UPDATE_REQUIRED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val bytesGroupIdentifier =
                    userInfo[EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_BYTES_GROUP_IDENTIFIER_KEY] as ByteArray?
                val serializedSharedSettings =
                    userInfo[EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_SHARED_SETTINGS_KEY] as String?
                val latestModificationTimestamp =
                    userInfo[EngineNotifications.KEYCLOAK_GROUP_V2_SHARED_SETTINGS_MODIFICATION_TIMESTAMP_KEY] as Long?
                if (bytesOwnedIdentity == null || bytesGroupIdentifier == null || latestModificationTimestamp == null) {
                    return
                }
                try {
                    var jsonExpiration: JsonExpiration?
                    jsonExpiration = if (serializedSharedSettings == null) {
                        null
                    } else {
                        AppSingleton.getJsonObjectMapper().readValue(
                            serializedSharedSettings,
                            JsonSharedSettings::class.java
                        ).jsonExpiration
                    }

                    if (jsonExpiration?.likeNull() == true) {
                        jsonExpiration = null
                    }

                    val discussion = AppDatabase.getInstance().discussionDao()
                        .getByGroupIdentifier(bytesOwnedIdentity, bytesGroupIdentifier)
                    if (discussion != null) {
                        var discussionCustomization =
                            AppDatabase.getInstance().discussionCustomizationDao()[discussion.id]
                        if (discussionCustomization == null || discussionCustomization.expirationJson != jsonExpiration) {
                            // we need to update the shared settings
                            if (discussionCustomization == null) {
                                if (jsonExpiration == null) {
                                    // we don't have any customization, but there is no JsonExpiration --> do nothing
                                    return
                                }
                                discussionCustomization = DiscussionCustomization(discussion.id)
                                db.discussionCustomizationDao().insert(discussionCustomization)
                            }

                            // always use version 0 for keycloak shared settings
                            discussionCustomization.sharedSettingsVersion = 0
                            jsonExpiration?.let {
                                discussionCustomization.settingReadOnce =
                                    it.readOnce == true
                                discussionCustomization.settingVisibilityDuration =
                                    it.visibilityDuration
                                discussionCustomization.settingExistenceDuration =
                                    it.existenceDuration
                            } ifNull {
                                discussionCustomization.settingReadOnce = false
                                discussionCustomization.settingVisibilityDuration = null
                                discussionCustomization.settingExistenceDuration = null
                            }
                            Message.createDiscussionSettingsUpdateMessage(
                                db,
                                discussion.id,
                                discussionCustomization.sharedSettingsJson,
                                ByteArray(0),
                                false,
                                latestModificationTimestamp
                            )?.let { message ->
                                message.id = db.messageDao().insert(message)
                                db.discussionCustomizationDao().update(discussionCustomization)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
        this.registrationNumber = registrationNumber
    }

    override fun getEngineNotificationListenerRegistrationNumber(): Long {
        return registrationNumber ?: 0
    }

    override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
        return registrationNumber != null
    }
}