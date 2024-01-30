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
package io.olvid.messenger.discussion.settings

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.olvid.messenger.App
import io.olvid.messenger.R.string
import io.olvid.messenger.R.xml
import io.olvid.messenger.customClasses.MultilineSummaryPreferenceCategory
import io.olvid.messenger.customClasses.MuteNotificationDialog
import io.olvid.messenger.customClasses.MuteNotificationDialog.MuteType.DISCUSSION
import io.olvid.messenger.customClasses.NoClickSwitchPreference
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.tasks.PropagatePinnedDiscussionsChangeTask
import io.olvid.messenger.discussion.settings.DiscussionSettingsViewModel.SettingsChangedListener

class DiscussionSettingsHeadersFragment : PreferenceFragmentCompat(), SettingsChangedListener {
    private var discussionSettingsDataStore: DiscussionSettingsDataStore? = null
    private val discussionSettingsViewModel: DiscussionSettingsViewModel by activityViewModels()
    private var lockedWarningPreference: MultilineSummaryPreferenceCategory? = null
    private var pinPreference: NoClickSwitchPreference? = null
    private var muteNotificationsPreference: NoClickSwitchPreference? = null
    private var sendReceiveHeaderPreference: Preference? = null
    private var sharedEphemeralSettingsHeaderPreference: Preference? = null


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(xml.discussion_preferences_headers, rootKey)
        discussionSettingsDataStore = discussionSettingsViewModel.discussionSettingsDataStore
        preferenceManager.preferenceDataStore = discussionSettingsDataStore
        discussionSettingsViewModel.discussionLiveData.observe(this) { discussion: Discussion? ->
            if (pinPreference != null && discussion != null) {
                pinPreference!!.isChecked = discussion.pinned
            }
        }
        val screen = preferenceScreen
        lockedWarningPreference =
            screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_LOCKED_EXPLANATION)
        pinPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_PIN)
        if (pinPreference != null) {
            pinPreference!!.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val discussion = discussionSettingsViewModel.discussionLiveData.value
                    if (discussion != null) {
                        App.runThread {
                            AppDatabase.getInstance().discussionDao()
                                .updatePinned(discussion.id, !discussion.pinned)
                            PropagatePinnedDiscussionsChangeTask(discussion.bytesOwnedIdentity).run()
                        }
                        return@OnPreferenceClickListener true
                    }
                    false
                }
        }
        muteNotificationsPreference =
            screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS)
        if (muteNotificationsPreference != null) {
            muteNotificationsPreference!!.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    val discussionCustomization =
                        discussionSettingsViewModel.discussionCustomization.value
                    if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications()) {
                        discussionSettingsDataStore?.putBoolean(
                            DiscussionSettingsActivity.PREF_KEY_DISCUSSION_MUTE_NOTIFICATIONS,
                            false
                        )
                    } else {
                        val context = context
                        val discussionId = discussionSettingsViewModel.discussionId
                        if (discussionId != null && context != null) {
                            val muteNotificationDialog = MuteNotificationDialog(
                                context,
                                { muteExpirationTimestamp: Long?, _: Boolean, muteExceptMentioned: Boolean ->
                                    App.runThread {
                                        var discussionCust = AppDatabase.getInstance()
                                            .discussionCustomizationDao()[discussionId]
                                        var insert = false
                                        if (discussionCust == null) {
                                            discussionCust = DiscussionCustomization(discussionId)
                                            insert = true
                                        }
                                        discussionCust.prefMuteNotifications = true
                                        discussionCust.prefMuteNotificationsTimestamp =
                                            muteExpirationTimestamp
                                        discussionCust.prefMuteNotificationsExceptMentioned =
                                            muteExceptMentioned
                                        if (insert) {
                                            AppDatabase.getInstance().discussionCustomizationDao()
                                                .insert(discussionCust)
                                        } else {
                                            AppDatabase.getInstance().discussionCustomizationDao()
                                                .update(discussionCust)
                                        }
                                    }
                                },
                                DISCUSSION,
                                discussionCustomization == null || discussionCustomization.prefMuteNotificationsExceptMentioned
                            )
                            muteNotificationDialog.show()
                        }
                    }
                    true
                }
        }
        sendReceiveHeaderPreference =
            screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_SEND_RECEIVE)
        sharedEphemeralSettingsHeaderPreference =
            screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS)
        discussionSettingsViewModel.addSettingsChangedListener(this)
    }

    override fun onSettingsChanged(discussionCustomization: DiscussionCustomization?) {
        muteNotificationsPreference?.apply {
            val shouldMute = discussionCustomization?.shouldMuteNotifications() ?: false
            isChecked = shouldMute
            if (!shouldMute) {
                summary =
                    getString(string.pref_discussion_mute_notifications_summary)
            } else if (discussionCustomization?.prefMuteNotificationsTimestamp == null) {
                summary =
                    getString(string.pref_discussion_mute_notifications_on_summary)
            } else {
                val dateString = StringUtils.getNiceDateString(
                    context, discussionCustomization.prefMuteNotificationsTimestamp ?: 0
                )
                summary =
                    getString(
                        string.pref_discussion_mute_notifications_on_until_summary,
                        dateString
                    )
            }
        }
    }

    override fun onLockedOrGroupAdminChanged(locked: Boolean, nonAdminGroup: Boolean) {
            lockedWarningPreference?.isVisible = locked
            sendReceiveHeaderPreference?.isEnabled = !locked
            sharedEphemeralSettingsHeaderPreference?.isEnabled = !locked
    }

    override fun onDestroy() {
        super.onDestroy()
        discussionSettingsViewModel.removeSettingsChangedListener(this)
    }
}
