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
package io.olvid.messenger.discussion.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R.string
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.jsons.JsonExpiration

class EphemeralViewModel : ViewModel() {
    private val discussionIdLiveData = MutableLiveData<Long?>()
    private val draftLoaded = MutableLiveData(false)
    private val defaultsLoaded = MutableLiveData(false)
    private val settingsModified = MutableLiveData(false)
    var draftJsonExpiration: JsonExpiration? = null
        private set
    private var discussionJsonExpiration: JsonExpiration? = null
    var configuringDiscussionCustomization by mutableStateOf(false)
    private var readOnceState by mutableStateOf<Boolean?>(null)
    private var visibilityState by mutableStateOf<Long?>(null)
    private var existenceState by mutableStateOf<Long?>(null)
    private val valid = MutableLiveData(false)

    companion object {
        fun visibilitySetting(duration: Long): String {
            with(App.getContext()) {
                return if (duration < 60L) {
                    getString(string.text_visible_timer_s, duration)
                } else if (duration < 3600L) {
                    getString(
                        string.text_visible_timer_m,
                        duration / 60
                    )
                } else if (duration < 86400L) {
                    getString(
                        string.text_visible_timer_h,
                        duration / 3600
                    )
                } else if (duration < 31536000L) {
                    getString(
                        string.text_visible_timer_d,
                        duration / 86400
                    )
                } else {
                    getString(
                        string.text_visible_timer_y,
                        duration / 31536000
                    )
                }
            }
        }

        fun existenceSetting(duration: Long): String {
            with(App.getContext()) {
                return if (duration < 60L) {
                    getString(string.text_existence_timer_s, duration)
                } else if (duration < 3600L) {
                    getString(
                        string.text_existence_timer_m,
                        duration / 60
                    )
                } else if (duration < 86400L) {
                    getString(
                        string.text_existence_timer_h,
                        duration / 3600
                    )
                } else if (duration < 31536000L) {
                    getString(
                        string.text_existence_timer_d,
                        duration / 86400
                    )
                } else {
                    getString(
                        string.text_existence_timer_y,
                        duration / 31536000
                    )
                }
            }
        }
    }

    @JvmField
    val discussionJsonExpirationLiveData = discussionIdLiveData.switchMap { discussionId: Long? ->
        if (discussionId != null) {
            return@switchMap AppDatabase.getInstance().discussionCustomizationDao()
                .getLiveData(discussionId)
                .map<DiscussionCustomization, JsonExpiration?> { discussionCustomization: DiscussionCustomization? ->
                    discussionJsonExpiration = discussionCustomization?.expirationJson
                    defaultsLoaded.postValue(true)
                    checkValid()
                    discussionJsonExpiration
                }
        } else {
            discussionJsonExpiration = null
            defaultsLoaded.postValue(true)
            checkValid()
        }
        null
    }

    fun setDiscussionId(discussionId: Long?, configuringDiscussionCustomization: Boolean) {
        this.configuringDiscussionCustomization = configuringDiscussionCustomization
        if (configuringDiscussionCustomization) {
            draftJsonExpiration = null
            draftLoaded.postValue(true)
        } else if (discussionId != null) {
            draftLoaded.postValue(false)
            draftJsonExpiration = null
            // fetch current draft and set its value, just once
            App.runThread {
                val draftMessage =
                    AppDatabase.getInstance().messageDao().getDiscussionDraftMessageSync(discussionId)
                if (draftMessage != null) {
                    try {
                        draftJsonExpiration = AppSingleton.getJsonObjectMapper()
                            .readValue(draftMessage.jsonExpiration, JsonExpiration::class.java)
                    } catch (e: Exception) {
                        // do nothing
                    }
                }
                draftLoaded.postValue(true)
            }
        }
        defaultsLoaded.postValue(false)
        discussionIdLiveData.postValue(discussionId)
    }

    fun getDraftLoaded(): LiveData<Boolean> {
        return draftLoaded
    }

    fun getDefaultsLoaded(): LiveData<Boolean> {
        return defaultsLoaded
    }

    fun discardDefaults() {
        discussionJsonExpiration = null
        defaultsLoaded.postValue(false)
    }

    fun getSettingsModified(): LiveData<Boolean> {
        return settingsModified
    }

    fun getValid(): LiveData<Boolean> {
        return valid
    }

    fun getReadOnce(): Boolean {
        return if (configuringDiscussionCustomization) {
            readOnceState ?: false
        } else {
            readOnceState ?: discussionJsonExpiration?.getReadOnce() ?: false
        }
    }

    fun setReadOnce(readOnce: Boolean) {
        this.readOnceState = readOnce
        checkValid()
    }

    fun getVisibility(): Long? {
        return if (configuringDiscussionCustomization) {
            visibilityState
        } else {
            visibilityState ?: discussionJsonExpiration?.visibilityDuration
        }
    }

    fun setVisibility(visibility: Long?) {
        this.visibilityState = visibility
        checkValid()
    }

    fun getExistence(): Long? {
        return if (configuringDiscussionCustomization) {
            existenceState
        } else {
            existenceState ?: discussionJsonExpiration?.existenceDuration
        }
    }

    fun setExistence(existence: Long?) {
        this.existenceState = existence
        checkValid()
    }

    fun reset() {
        readOnceState = discussionJsonExpiration?.getReadOnce() ?: false
        visibilityState = discussionJsonExpiration?.getVisibilityDuration()
        existenceState = discussionJsonExpiration?.getExistenceDuration()
        checkValid()
    }

    private fun checkValid() {
        settingsModified.postValue(
            (readOnceState ?: false) != (discussionJsonExpiration?.readOnce ?: false)
                    || visibilityState != discussionJsonExpiration?.visibilityDuration
                    || existenceState != discussionJsonExpiration?.existenceDuration
        )
        var valid = (visibilityState == null || visibilityState!! > 0) && (existenceState == null || existenceState!! > 0)
        if (discussionJsonExpiration != null && configuringDiscussionCustomization.not()) {
            if (discussionJsonExpiration?.getReadOnce() == true) {
                valid = valid and (readOnceState != false)
            }
            valid =
                valid and ((visibilityState ?: -1) <= (discussionJsonExpiration?.getVisibilityDuration() ?: Long.MAX_VALUE))
            valid =
                valid and ((existenceState ?: -1) <= (discussionJsonExpiration?.getExistenceDuration() ?: Long.MAX_VALUE))
        }
        this.valid.postValue(valid)
    }
}