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

package io.olvid.messenger.fragments.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import java.util.List;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.fragments.FilteredContactListFragment;
import io.olvid.messenger.settings.SettingsActivity;

public class GroupMemberAdditionDialogFragment extends DialogFragment {
    private static final String BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity";
    private static final String BYTES_GROUP_UID_KEY = "bytes_group_uid";
    private byte[] bytesOwnedIdentity;
    private byte[] bytesGroupUid;
    private List<Contact> selectedContacts = null;
    private Group group = null;

    public static GroupMemberAdditionDialogFragment newInstance(byte[] bytesOwnedIdentity, byte[] bytesGroupUid) {
        GroupMemberAdditionDialogFragment fragment = new GroupMemberAdditionDialogFragment();
        Bundle args = new Bundle();
        args.putByteArray(BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        args.putByteArray(BYTES_GROUP_UID_KEY, bytesGroupUid);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        if (arguments != null) {
            bytesOwnedIdentity = arguments.getByteArray(BYTES_OWNED_IDENTITY_KEY);
            bytesGroupUid = arguments.getByteArray(BYTES_GROUP_UID_KEY);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_pick_multiple_contacts, container, false);
        EditText dialogContactNameFilter = dialogView.findViewById(R.id.dialog_discussion_filter);
        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        dialogTitle.setText(R.string.dialog_title_invite_group_members);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());
        Button okButton = dialogView.findViewById(R.id.button_ok);
        okButton.setOnClickListener((View view) -> {
            dismiss();

            if (selectedContacts == null || selectedContacts.size() == 0) {
                return;
            }

            final byte[][] bytesNewMemberIdentities = new byte[selectedContacts.size()][];

            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Contact selectedContact: selectedContacts) {
                if (i != 0) {
                    sb.append(getString(R.string.text_contact_names_separator));
                }
                sb.append(selectedContact.getCustomDisplayName());
                bytesNewMemberIdentities[i] = selectedContact.bytesContactIdentity;
                i++;
            }


            final AlertDialog.Builder builder = new SecureAlertDialogBuilder(requireContext(), R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_invite_group_members)
                    .setMessage(getResources().getQuantityString(R.plurals.dialog_message_invite_group_members, selectedContacts.size(), selectedContacts.size(), group.getCustomName(), sb.toString()))
                    .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                        try {
                            AppSingleton.getEngine().inviteContactsToGroup(bytesOwnedIdentity, bytesGroupUid, bytesNewMemberIdentities);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .setNegativeButton(R.string.button_label_cancel, null);
            builder.create().show();
        });

        FilteredContactListFragment filteredContactListFragment = new FilteredContactListFragment();
        filteredContactListFragment.setContactFilterEditText(dialogContactNameFilter);
        filteredContactListFragment.removeBottomPadding();
        filteredContactListFragment.setSelectable(true);
        filteredContactListFragment.setUnfilteredContacts(Transformations.switchMap(AppDatabase.getInstance().groupDao().getLiveData(bytesOwnedIdentity, bytesGroupUid), (Group liveGroup) -> {
            if (liveGroup == null) {
                return null;
            }
            group = liveGroup;
            return AppDatabase.getInstance().contactDao().getAllForOwnedIdentityWithChannelExcludingGroup(bytesOwnedIdentity, bytesGroupUid);
        }));

        Observer<List<Contact>> selectedContactsObserver = (List<Contact> contacts) -> selectedContacts = contacts;
        filteredContactListFragment.setSelectedContactsObserver(selectedContactsObserver);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.dialog_filtered_contact_list_placeholder, filteredContactListFragment);
        transaction.commit();

        return dialogView;
    }
}
