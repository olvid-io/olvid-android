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
package io.olvid.messenger.group

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.RecyclerView
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.MultilineSummaryPreferenceCategory
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.discussion.settings.DiscussionSettingsActivity
import io.olvid.messenger.group.GroupTypeModel.RemoteDeleteSetting
import io.olvid.messenger.group.components.GroupAdminsSelectionDialog


class GroupCustomSettingsPreferenceFragment(private val isGroupCreation: Boolean) :
    PreferenceFragmentCompat() {
    private val groupCreationViewModel: GroupCreationViewModel by activityViewModels()
    private val groupV2DetailsViewModel: GroupV2DetailsViewModel by activityViewModels()
    private var admins: Preference? = null
    private var readOnlyPreference: SwitchPreference? = null
    private var remoteDeletePreference: ListPreference? = null
    private var readOncePreference: SwitchPreference? = null
    private var visibilityDurationPreference: ListPreference? = null
    private var existenceDurationPreference: ListPreference? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (isGroupCreation) {
            (view as? ViewGroup)?.addView(ComposeView(view.context).apply {
                setContent {
                    val chooseAdmins by groupCreationViewModel.chooseAdmins.observeAsState()
                    val admins by groupCreationViewModel.admins.observeAsState()
                    AppCompatTheme {
                        GroupAdminsSelectionDialog(
                            stateOpened = chooseAdmins ?: false,
                            changeOpenedState = { newState -> groupCreationViewModel.chooseAdmins.value = newState },
                            admins = admins,
                            selectAdmins = { selectedAdmins -> groupCreationViewModel.admins.value = selectedAdmins },
                            members = groupCreationViewModel.selectedContacts
                        )
                    }
                }
            })
            view.findViewById<RecyclerView>(R.id.recycler_view)?.setPadding(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, view.resources.displayMetrics).toInt())
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_preferences_group_custom_settings, rootKey)
        preferenceManager.preferenceDataStore = object : PreferenceDataStore() {
            override fun putString(key: String?, value: String?) {}
            override fun putBoolean(key: String?, value: Boolean) {}
            override fun getString(key: String?, defValue: String?): String? { return null }
            override fun getBoolean(key: String?, defValue: Boolean): Boolean { return false }
        }

        admins = preferenceScreen.findPreference("pref_key_admin")
        readOnlyPreference = preferenceScreen.findPreference("pref_key_discussion_read_only") as? SwitchPreference
        remoteDeletePreference = preferenceScreen.findPreference("pref_key_discussion_remote_delete") as? ListPreference

        readOncePreference = preferenceScreen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_READ_ONCE) as? SwitchPreference
        visibilityDurationPreference = preferenceScreen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_VISIBILITY_DURATION) as? ListPreference
        existenceDurationPreference =  preferenceScreen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_EXISTENCE_DURATION) as? ListPreference

        if (!isGroupCreation) {
            admins?.let {
                preferenceScreen.removePreference(it)
            }
            preferenceScreen.findPreference<MultilineSummaryPreferenceCategory>(
                DiscussionSettingsActivity.PREF_KEY_DISCUSSION_CATEGORY_SHARED_EPHEMERAL_SETTINGS
            )?.let {
                preferenceScreen.removePreference(it)
            }
        }

        remoteDeletePreference?.value =
            groupV2DetailsViewModel.getGroupTypeLiveData().value?.remoteDeleteSetting?.toString()
        readOnlyPreference?.isChecked =
            groupV2DetailsViewModel.getGroupTypeLiveData().value?.readOnlySetting ?: false


        readOnlyPreference?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, newValue ->
                groupV2DetailsViewModel.updateReadOnly(newValue as Boolean)
                true
            }

        remoteDeletePreference?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, newValue ->
                groupV2DetailsViewModel.updateRemoteDelete(RemoteDeleteSetting.byString(newValue as String))
                true
            }


        if (isGroupCreation) {
            readOncePreference?.isChecked = groupCreationViewModel.settingsReadOnce
            visibilityDurationPreference?.value =
                "${groupCreationViewModel.settingsVisibilityDuration ?: "null"}"
            existenceDurationPreference?.value =
                "${groupCreationViewModel.settingsExistenceDuration ?: "null"}"

            groupCreationViewModel.selectedContactCount.observe(this) {count : Int ->
                admins?.isEnabled = count != 0
            }

            groupCreationViewModel.admins.observe(this) {
                admins?.summary = StringUtils.joinContactDisplayNames(
                    (groupCreationViewModel.selectedContacts?.filter {
                        groupCreationViewModel.admins.value?.contains(it) == true
                    }?.map { it.getCustomDisplayName() }
                        ?.toMutableList() ?: mutableListOf<String>()).apply {
                            this.add(0, App.getContext().getString(R.string.text_you))
                        }.toTypedArray(),
                    5
                )
            }


            admins?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    groupCreationViewModel.chooseAdmins.value = true
                    true
                }

            readOncePreference?.onPreferenceChangeListener =
                OnPreferenceChangeListener { _, newValue ->
                    groupCreationViewModel.settingsReadOnce = newValue as Boolean
                    true
                }

            visibilityDurationPreference?.apply {
                onPreferenceChangeListener =
                    OnPreferenceChangeListener { _, newValue ->
                        groupCreationViewModel.settingsVisibilityDuration = try {
                            (newValue?.takeIf { it != "null" } as? String)?.toLong()
                        } catch (ex: Exception) {
                            null
                        }
                        true
                    }
                summaryProvider = SummaryProvider<ListPreference> {
                    groupCreationViewModel.settingsVisibilityDuration?.let { visibilityDuration ->
                        listView.post { value = visibilityDuration.toString() }
                        if (entry == null) {
                            val values: MutableList<CharSequence> =
                                visibilityDurationPreference?.entryValues.orEmpty().toMutableList()
                            val entries: MutableList<CharSequence?> =
                                visibilityDurationPreference?.entries.orEmpty().toMutableList()
                            values.add(visibilityDuration.toString())
                            entries.add(
                                StringUtils.getNiceDurationString(
                                    requireContext(),
                                    visibilityDuration
                                )
                            )
                            visibilityDurationPreference?.entryValues = values.toTypedArray()
                            visibilityDurationPreference?.entries = entries.toTypedArray()
                        }
                        getString(R.string.pref_discussion_visibility_duration_summary, entry)
                    } ?: kotlin.run {
                        visibilityDurationPreference?.value = "null"
                        getString(R.string.pref_discussion_visibility_duration_summary_null)
                    }
                }
            }

            existenceDurationPreference?.apply {
                onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                    groupCreationViewModel.settingsExistenceDuration = try {
                        (newValue?.takeIf { it != "null" } as? String)?.toLong()
                    } catch (ex: Exception) {
                        null
                    }
                    true
                }
                summaryProvider = SummaryProvider<ListPreference> {
                    groupCreationViewModel.settingsExistenceDuration?.let { existenceDuration ->
                        listView.post { value = existenceDuration.toString() }
                        if (entry == null) {
                            val values: MutableList<CharSequence> =
                                existenceDurationPreference?.entryValues.orEmpty().toMutableList()
                            val entries: MutableList<CharSequence?> =
                                existenceDurationPreference?.entries.orEmpty().toMutableList()
                            values.add(existenceDuration.toString())
                            entries.add(
                                StringUtils.getNiceDurationString(
                                    requireContext(),
                                    existenceDuration
                                )
                            )
                            entryValues = values.toTypedArray()
                            existenceDurationPreference?.entries = entries.toTypedArray()
                        }
                        getString(R.string.pref_discussion_existence_duration_summary, entry)

                    } ?: kotlin.run {
                        existenceDurationPreference?.value = "null"
                        getString(R.string.pref_discussion_existence_duration_summary_null)
                    }
                }
            }
        }
    }
}