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
package io.olvid.messenger.discussion.settings

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ComposeViewPreference
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.discussion.compose.EphemeralSettingsGroup
import io.olvid.messenger.discussion.compose.EphemeralViewModel
import io.olvid.messenger.discussion.settings.DiscussionSettingsViewModel.SettingsChangedListener

class DiscussionSharedEphemeralSettingsPreferenceFragment : PreferenceFragmentCompat(), SettingsChangedListener {
    private lateinit var activity: FragmentActivity
    private val discussionSettingsViewModel: DiscussionSettingsViewModel by activityViewModels()
    private val ephemeralViewModel: EphemeralViewModel by activityViewModels()
    private var lockedState by mutableStateOf<Boolean>(false)

    private var discussionSettingsDataStore: DiscussionSettingsDataStore? = null
    private var sharedSettingsCategory: PreferenceCategory? = null
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.discussion_fragment_preferences_shared_ephemeral_settings, rootKey)
        activity = requireActivity()
        ephemeralViewModel.setDiscussionId(discussionSettingsViewModel.discussionId, true)
        discussionSettingsDataStore = discussionSettingsViewModel.discussionSettingsDataStore
        preferenceManager.preferenceDataStore = discussionSettingsDataStore
        val screen = preferenceScreen
        sharedSettingsCategory = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS)
        screen.findPreference<ComposeViewPreference>(DiscussionSettingsActivity.PREF_KEY_COMPOSE_EPHEMERAL_SETTINGS)?.setContent {
            EphemeralSettingsGroup(
                ephemeralViewModel = ephemeralViewModel, expanded = null, locked = lockedState
            )
        }
        discussionSettingsViewModel.addSettingsChangedListener(this)
    }

    override fun onSettingsChanged(discussionCustomization: DiscussionCustomization?) { }

    override fun onLockedOrGroupAdminChanged(locked: Boolean, nonAdminGroup: Boolean) {
        if (locked) {
            if (sharedSettingsCategory != null) {
                sharedSettingsCategory!!.setSummary(R.string.pref_discussion_category_shared_ephemeral_settings_summary)
            }
            lockedState = true
        } else if (nonAdminGroup) {
            // joined group
            if (sharedSettingsCategory != null) {
                sharedSettingsCategory!!.setSummary(R.string.pref_discussion_category_shared_ephemeral_settings_summary_only_owner)
            }
            lockedState = true
        } else {
            // own group or no group
            if (sharedSettingsCategory != null) {
                sharedSettingsCategory!!.setSummary(R.string.pref_discussion_category_shared_ephemeral_settings_summary)
            }
            lockedState = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        discussionSettingsViewModel.removeSettingsChangedListener(this)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.dialogBackground))
    }
}
