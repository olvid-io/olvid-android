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
package io.olvid.messenger.settings

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.sync.ObvSyncAtom
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.AppDatabase.Companion.getInstance
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.databases.tasks.ContactDisplayNameFormatChangedTask
import io.olvid.messenger.settings.SettingsActivity.AutoJoinGroupsCategory
import io.olvid.messenger.settings.SettingsActivity.Companion.autoJoinGroups
import io.olvid.messenger.settings.SettingsActivity.Companion.contactDisplayNameFormat
import io.olvid.messenger.settings.SettingsActivity.Companion.getAutoJoinGroupsFromString

class ContactsAndGroupsPreferenceFragment : PreferenceFragmentCompat() {
    @JvmField
    var activity: FragmentActivity? = null
    private var contactDisplayNameFormatChanged = false
    private var displayNameHasLastNameFirst = false

    override fun onDestroy() {
        super.onDestroy()
        if (contactDisplayNameFormatChanged) {
            App.runThread(ContactDisplayNameFormatChangedTask())
        }
    }


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_preferences_contacts_and_groups, rootKey)
        activity = requireActivity()
        val screen = preferenceScreen ?: return

        val autoJoinPreference =
            screen.findPreference<ListPreference?>(SettingsActivity.PREF_KEY_AUTO_JOIN_GROUPS)
        autoJoinPreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference, newValue: Any? ->
                if (newValue is String) {
                    val previousCategory = autoJoinGroups
                    val newCategory = getAutoJoinGroupsFromString(newValue)

                    // if setting becomes more restrictive, directly accept
                    if (newCategory == previousCategory || newCategory == AutoJoinGroupsCategory.NOBODY || newCategory == AutoJoinGroupsCategory.CONTACTS && previousCategory == AutoJoinGroupsCategory.EVERYONE) {
                        try {
                            AppSingleton.getEngine()
                                .propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(
                                    ObvSyncAtom.createSettingAutoJoinGroups(newCategory.stringValue)
                                )
                            AppSingleton.getEngine().deviceBackupNeeded()
                        } catch (e: Exception) {
                            Logger.w("Failed to propagate auto join group setting change to other devices")
                            Logger.x(e)
                        }
                        return@OnPreferenceChangeListener true
                    }

                    // otherwise, check whether this change would auto-accept some pending invitations
                    App.runThread {
                        val groupInvitations =
                            getInstance().invitationDao().getAllGroupInvites()
                        val invitationsToAccept: MutableList<Invitation>
                        if (newCategory == AutoJoinGroupsCategory.CONTACTS) {
                            // filter invitations to keep only those from a oneToOne contact
                            invitationsToAccept = ArrayList()
                            for (groupInvitation in groupInvitations) {
                                val bytesGroupOwnerIdentity = groupInvitation.associatedDialog.category.bytesMediatorOrGroupOwnerIdentity
                                if (bytesGroupOwnerIdentity != null) {
                                    val contact = getInstance().contactDao().get(
                                        groupInvitation.bytesOwnedIdentity,
                                        bytesGroupOwnerIdentity
                                    )
                                    if (contact != null && contact.oneToOne) {
                                        invitationsToAccept.add(groupInvitation)
                                    }
                                }
                            }
                        } else {
                            invitationsToAccept = groupInvitations
                        }
                        if (invitationsToAccept.isEmpty()) {
                            // directly update the setting
                            autoJoinGroups = newCategory
                            try {
                                AppSingleton.getEngine()
                                    .propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(
                                        ObvSyncAtom.createSettingAutoJoinGroups(newCategory.stringValue)
                                    )
                                AppSingleton.getEngine().deviceBackupNeeded()
                            } catch (e: Exception) {
                                Logger.w("Failed to propagate auto join group setting change to other devices")
                                e.printStackTrace()
                            }
                            // in order not to trigger this listener in a loop, we remove it, set the value, and re-add the listener...
                            Handler(Looper.getMainLooper()).post {
                                val listener = autoJoinPreference.onPreferenceChangeListener
                                autoJoinPreference.onPreferenceChangeListener = null
                                autoJoinPreference.setValue(newValue)
                                autoJoinPreference.onPreferenceChangeListener = listener
                            }
                        } else {
                            activity?.let { activity ->
                                // ask for confirmation
                                val builder =
                                    SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_auto_join_pending_groups)
                                        .setMessage(
                                            activity.resources.getQuantityString(
                                                R.plurals.dialog_message_auto_join_pending_groups,
                                                invitationsToAccept.size,
                                                invitationsToAccept.size
                                            )
                                        )
                                        .setNegativeButton(R.string.button_label_cancel, null)
                                        .setPositiveButton(
                                            R.string.button_label_ok
                                        ) { _: DialogInterface?, _: Int ->
                                            autoJoinGroups = newCategory
                                            try {
                                                AppSingleton.getEngine()
                                                    .propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(
                                                        ObvSyncAtom.createSettingAutoJoinGroups(
                                                            newCategory.stringValue
                                                        )
                                                    )
                                                AppSingleton.getEngine().deviceBackupNeeded()
                                            } catch (e: Exception) {
                                                Logger.w("Failed to propagate auto join group setting change to other devices")
                                                e.printStackTrace()
                                            }
                                            for (groupInvitation in invitationsToAccept) {
                                                try {
                                                    val obvDialog =
                                                        groupInvitation.associatedDialog
                                                    obvDialog.setResponseToAcceptGroupInvite(
                                                        true
                                                    )
                                                    AppSingleton.getEngine()
                                                        .respondToDialog(obvDialog)
                                                } catch (_: Exception) { }
                                            }
                                            // in order not to trigger this listener in a loop, we remove it, set the value, and re-add the listener...
                                            val listener =
                                                autoJoinPreference.onPreferenceChangeListener
                                            autoJoinPreference.onPreferenceChangeListener = null
                                            autoJoinPreference.setValue(newValue)
                                            autoJoinPreference.onPreferenceChangeListener =
                                                listener
                                        }
                                Handler(Looper.getMainLooper()).post {
                                    builder.create().show()
                                }
                            }
                        }
                    }
                }
                return@OnPreferenceChangeListener false
            }

        val showTrustLevelsPreference =
            screen.findPreference<SwitchPreference?>(SettingsActivity.PREF_KEY_SHOW_TRUST_LEVELS)
        showTrustLevelsPreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference, _: Any? ->
                val recreateRequiredIntent =
                    Intent(SettingsActivity.ACTIVITY_RECREATE_REQUIRED_ACTION)
                recreateRequiredIntent.setPackage(App.getContext().packageName)
                // we delay sending this intent so we are sure the setting is updated when activities are recreated
                Handler(Looper.getMainLooper()).postDelayed({
                    LocalBroadcastManager.getInstance(
                        App.getContext()
                    ).sendBroadcast(recreateRequiredIntent)
                }, 200)
                return@OnPreferenceChangeListener true
            }

        val displayNameFormatPreference =
            screen.findPreference<ListPreference?>(SettingsActivity.PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT)
        val allowFirstNamePreference =
            screen.findPreference<SwitchPreference?>(SettingsActivity.PREF_KEY_SOMETIMES_SHOW_FIRST_NAME_ONLY)
        val sortByLastNamePreference =
            screen.findPreference<SwitchPreference?>(SettingsActivity.PREF_KEY_SORT_CONTACTS_BY_LAST_NAME)
        val uppercaseLastNamePreference =
            screen.findPreference<SwitchPreference?>(SettingsActivity.PREF_KEY_UPPERCASE_LAST_NAME)

        val preferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference, _: Any? ->
                contactDisplayNameFormatChanged = true
                return@OnPreferenceChangeListener true
            }
        if (displayNameFormatPreference != null && allowFirstNamePreference != null && sortByLastNamePreference != null && uppercaseLastNamePreference != null) {
            displayNameHasLastNameFirst =
                displayNameFormatHasLastNameFirst(contactDisplayNameFormat)

            displayNameFormatPreference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference, newValue: Any? ->
                    val newDisplayNameHasLastNameFirst: Boolean =
                        displayNameFormatHasLastNameFirst(newValue as? String)
                    if (newDisplayNameHasLastNameFirst xor displayNameHasLastNameFirst) {
                        if (sortByLastNamePreference.isChecked == displayNameHasLastNameFirst) {
                            sortByLastNamePreference.setChecked(newDisplayNameHasLastNameFirst)
                        }
                        displayNameHasLastNameFirst = newDisplayNameHasLastNameFirst
                    }
                    contactDisplayNameFormatChanged = true
                    true
                }
            allowFirstNamePreference.onPreferenceChangeListener = preferenceChangeListener
            sortByLastNamePreference.onPreferenceChangeListener = preferenceChangeListener
            uppercaseLastNamePreference.onPreferenceChangeListener = preferenceChangeListener
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.almostWhite))
    }

    companion object {
        private fun displayNameFormatHasLastNameFirst(format: String?): Boolean {
            return when (format) {
                JsonIdentityDetails.FORMAT_STRING_LAST_FIRST, JsonIdentityDetails.FORMAT_STRING_LAST_FIRST_COMPANY, JsonIdentityDetails.FORMAT_STRING_LAST_FIRST_POSITION_COMPANY -> true
                JsonIdentityDetails.FORMAT_STRING_FIRST_LAST, JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_COMPANY, JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY -> false
                else -> false
            }
        }
    }
}
