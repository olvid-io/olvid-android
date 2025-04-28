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

package io.olvid.messenger.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.engine.engine.types.sync.ObvSyncAtom;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.databases.tasks.ContactDisplayNameFormatChangedTask;

public class ContactsAndGroupsPreferenceFragment extends PreferenceFragmentCompat {
    FragmentActivity activity;
    private boolean contactDisplayNameFormatChanged = false;
    private boolean displayNameHasLastNameFirst = false;

    private static boolean displayNameFormatHasLastNameFirst(String format) {
        switch (format) {
            case JsonIdentityDetails.FORMAT_STRING_LAST_FIRST:
            case JsonIdentityDetails.FORMAT_STRING_LAST_FIRST_COMPANY:
            case JsonIdentityDetails.FORMAT_STRING_LAST_FIRST_POSITION_COMPANY:
                return true;
            case JsonIdentityDetails.FORMAT_STRING_FIRST_LAST:
            case JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_COMPANY:
            case JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY:
            default:
                return false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (contactDisplayNameFormatChanged) {
            App.runThread(new ContactDisplayNameFormatChangedTask());
        }
    }


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_preferences_contacts_and_groups, rootKey);
        activity = requireActivity();
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        {
            ListPreference autoJoinPreference = screen.findPreference(SettingsActivity.PREF_KEY_AUTO_JOIN_GROUPS);
            if (autoJoinPreference != null) {
                autoJoinPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                    if (newValue instanceof String) {
                        SettingsActivity.AutoJoinGroupsCategory previousCategory = SettingsActivity.getAutoJoinGroups();
                        SettingsActivity.AutoJoinGroupsCategory newCategory = SettingsActivity.getAutoJoinGroupsFromString((String) newValue);

                        // if setting becomes more restrictive, directly accept
                        if (newCategory == previousCategory
                                || newCategory == SettingsActivity.AutoJoinGroupsCategory.NOBODY
                                || newCategory == SettingsActivity.AutoJoinGroupsCategory.CONTACTS && previousCategory == SettingsActivity.AutoJoinGroupsCategory.EVERYONE) {
                            try {
                                AppSingleton.getEngine().propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(ObvSyncAtom.createSettingAutoJoinGroups(newCategory.getStringValue()));
                                AppSingleton.getEngine().deviceBackupNeeded();
                            } catch (Exception e) {
                                Logger.w("Failed to propagate auto join group setting change to other devices");
                                e.printStackTrace();
                            }
                            return true;
                        }

                        // otherwise, check whether this change would auto-accept some pending invitations
                        App.runThread(() -> {
                            List<Invitation> groupInvitations = AppDatabase.getInstance().invitationDao().getAllGroupInvites();
                            final List<Invitation> invitationsToAccept;
                            if (newCategory == SettingsActivity.AutoJoinGroupsCategory.CONTACTS) {
                                // filter invitations to keep only those from a oneToOne contact
                                invitationsToAccept = new ArrayList<>();
                                for (Invitation groupInvitation : groupInvitations) {
                                    byte[] bytesGroupOwnerIdentity = groupInvitation.associatedDialog.getCategory().getBytesMediatorOrGroupOwnerIdentity();
                                    if (bytesGroupOwnerIdentity != null) {
                                        Contact contact = AppDatabase.getInstance().contactDao().get(groupInvitation.bytesOwnedIdentity, bytesGroupOwnerIdentity);
                                        if (contact != null && contact.oneToOne) {
                                            invitationsToAccept.add(groupInvitation);
                                        }
                                    }
                                }
                            } else {
                                invitationsToAccept = groupInvitations;
                            }

                            if (invitationsToAccept.isEmpty()) {
                                // directly update the setting
                                SettingsActivity.setAutoJoinGroups(newCategory);
                                try {
                                    AppSingleton.getEngine().propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(ObvSyncAtom.createSettingAutoJoinGroups(newCategory.getStringValue()));
                                    AppSingleton.getEngine().deviceBackupNeeded();
                                } catch (Exception e) {
                                    Logger.w("Failed to propagate auto join group setting change to other devices");
                                    e.printStackTrace();
                                }
                                // in order not to trigger this listener in a loop, we remove it, set the value, and re-add the listener...
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    Preference.OnPreferenceChangeListener listener = autoJoinPreference.getOnPreferenceChangeListener();
                                    autoJoinPreference.setOnPreferenceChangeListener(null);
                                    autoJoinPreference.setValue((String) newValue);
                                    autoJoinPreference.setOnPreferenceChangeListener(listener);
                                });
                            } else {
                                // ask for confirmation
                                AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_auto_join_pending_groups)
                                        .setMessage(activity.getResources().getQuantityString(R.plurals.dialog_message_auto_join_pending_groups, invitationsToAccept.size(), invitationsToAccept.size()))
                                        .setNegativeButton(R.string.button_label_cancel, null)
                                        .setPositiveButton(R.string.button_label_ok, (DialogInterface dialogInterface, int which) -> {
                                            SettingsActivity.setAutoJoinGroups(newCategory);
                                            try {
                                                AppSingleton.getEngine().propagateAppSyncAtomToAllOwnedIdentitiesOtherDevicesIfNeeded(ObvSyncAtom.createSettingAutoJoinGroups(newCategory.getStringValue()));
                                                AppSingleton.getEngine().deviceBackupNeeded();
                                            } catch (Exception e) {
                                                Logger.w("Failed to propagate auto join group setting change to other devices");
                                                e.printStackTrace();
                                            }
                                            for (Invitation groupInvitation: invitationsToAccept) {
                                                try {
                                                    ObvDialog obvDialog = groupInvitation.associatedDialog;
                                                    obvDialog.setResponseToAcceptGroupInvite(true);
                                                    AppSingleton.getEngine().respondToDialog(obvDialog);
                                                } catch (Exception ignored) {}
                                            }
                                            // in order not to trigger this listener in a loop, we remove it, set the value, and re-add the listener...
                                            Preference.OnPreferenceChangeListener listener = autoJoinPreference.getOnPreferenceChangeListener();
                                            autoJoinPreference.setOnPreferenceChangeListener(null);
                                            autoJoinPreference.setValue((String) newValue);
                                            autoJoinPreference.setOnPreferenceChangeListener(listener);
                                        });
                                new Handler(Looper.getMainLooper()).post(() -> builder.create().show());
                            }
                        });
                    }
                    return false;
                });
            }
        }

        {
            final SwitchPreference showTrustLevelsPreference = screen.findPreference(SettingsActivity.PREF_KEY_SHOW_TRUST_LEVELS);
            if (showTrustLevelsPreference != null) {
                showTrustLevelsPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                    Intent recreateRequiredIntent = new Intent(SettingsActivity.ACTIVITY_RECREATE_REQUIRED_ACTION);
                    recreateRequiredIntent.setPackage(App.getContext().getPackageName());
                    // we delay sending this intent so we are sure the setting is updated when activities are recreated
                    new Handler(Looper.getMainLooper()).postDelayed(() -> LocalBroadcastManager.getInstance(App.getContext()).sendBroadcast(recreateRequiredIntent), 200);
                    return true;
                });
            }
        }

        {
            final ListPreference displayNameFormatPreference = screen.findPreference(SettingsActivity.PREF_KEY_CONTACT_DISPLAY_NAME_FORMAT);
            final SwitchPreference allowFirstNamePreference = screen.findPreference(SettingsActivity.PREF_KEY_SOMETIMES_SHOW_FIRST_NAME_ONLY);
            final SwitchPreference sortByLastNamePreference = screen.findPreference(SettingsActivity.PREF_KEY_SORT_CONTACTS_BY_LAST_NAME);
            final SwitchPreference uppercaseLastNamePreference = screen.findPreference(SettingsActivity.PREF_KEY_UPPERCASE_LAST_NAME);

            Preference.OnPreferenceChangeListener preferenceChangeListener = (Preference preference, Object newValue) -> {
                contactDisplayNameFormatChanged = true;
                return true;
            };

            if (displayNameFormatPreference != null && allowFirstNamePreference != null && sortByLastNamePreference != null && uppercaseLastNamePreference != null) {
                displayNameHasLastNameFirst = displayNameFormatHasLastNameFirst(SettingsActivity.getContactDisplayNameFormat());

                displayNameFormatPreference.setOnPreferenceChangeListener((Preference preference, Object newValue) -> {
                    boolean newDisplayNameHasLastNameFirst = displayNameFormatHasLastNameFirst((String) newValue);
                    if (newDisplayNameHasLastNameFirst ^ displayNameHasLastNameFirst) {
                        if (sortByLastNamePreference.isChecked() == displayNameHasLastNameFirst) {
                            sortByLastNamePreference.setChecked(newDisplayNameHasLastNameFirst);
                        }
                        displayNameHasLastNameFirst = newDisplayNameHasLastNameFirst;
                    }
                    contactDisplayNameFormatChanged = true;
                    return true;
                });
                allowFirstNamePreference.setOnPreferenceChangeListener(preferenceChangeListener);
                sortByLastNamePreference.setOnPreferenceChangeListener(preferenceChangeListener);
                uppercaseLastNamePreference.setOnPreferenceChangeListener(preferenceChangeListener);
            }
        }
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(ContextCompat.getColor(view.getContext(), R.color.almostWhite));
    }
}
