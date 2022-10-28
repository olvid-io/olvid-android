/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.fragments.FilteredContactListFragment;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.GroupV2DetailsViewModel;

public class GroupV2MemberAdditionDialogFragment extends DialogFragment {
    private static final String BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity";
    private static final String BYTES_GROUP_IDENTIFIER = "bytes_group_identifier";
    private static final String ADDED_GROUP_MEMBERS = "added_group_members";
    private static final String REMOVED_GROUP_MEMBERS = "removed_group_members";

    private FragmentActivity activity = null;
    private GroupV2DetailsViewModel groupV2DetailsViewModel = null;

    private byte[] bytesOwnedIdentity;
    private byte[] bytesGroupIdentifier;
    private List<byte[]> bytesAddedMemberIdentities;
    private List<byte[]> bytesRemovedMemberIdentities;

    private List<Contact> selectedContacts = null;

    public static GroupV2MemberAdditionDialogFragment newInstance(byte[] bytesOwnedIdentity, byte[] bytesGroupIdentifier, ArrayList<BytesKey> addedGroupMembers, ArrayList<BytesKey> removedGroupMembers) {
        GroupV2MemberAdditionDialogFragment fragment = new GroupV2MemberAdditionDialogFragment();
        Bundle args = new Bundle();
        args.putByteArray(BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        args.putByteArray(BYTES_GROUP_IDENTIFIER, bytesGroupIdentifier);
        args.putParcelableArrayList(ADDED_GROUP_MEMBERS, addedGroupMembers);
        args.putParcelableArrayList(REMOVED_GROUP_MEMBERS, removedGroupMembers);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        if (arguments != null) {
            bytesOwnedIdentity = arguments.getByteArray(BYTES_OWNED_IDENTITY_KEY);
            bytesGroupIdentifier = arguments.getByteArray(BYTES_GROUP_IDENTIFIER);
            bytesAddedMemberIdentities = new ArrayList<>();
            bytesRemovedMemberIdentities = new ArrayList<>();
            ArrayList<BytesKey> addedGroupMembers = arguments.getParcelableArrayList(ADDED_GROUP_MEMBERS);
            if (addedGroupMembers != null) {
                for (BytesKey addedGroupMember : addedGroupMembers) {
                    bytesAddedMemberIdentities.add(addedGroupMember.bytes);
                }
            }
            ArrayList<BytesKey> removedGroupMembers = arguments.getParcelableArrayList(REMOVED_GROUP_MEMBERS);
            if (removedGroupMembers != null) {
                for (BytesKey removedGroupMember : removedGroupMembers) {
                    bytesRemovedMemberIdentities.add(removedGroupMember.bytes);
                }
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        activity = requireActivity();
        groupV2DetailsViewModel = new ViewModelProvider(activity).get(GroupV2DetailsViewModel.class);

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
        View dialogView = inflater.inflate(R.layout.dialog_fragment_add_group_v2_members, container, false);
        EditText dialogContactNameFilter = dialogView.findViewById(R.id.dialog_discussion_filter);
        View v2Warning = dialogView.findViewById(R.id.group_v2_warning_message);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());
        Button okButton = dialogView.findViewById(R.id.button_ok);
        okButton.setOnClickListener((View view) -> {
            dismiss();

            if (selectedContacts == null || selectedContacts.size() == 0) {
                return;
            }

            if (groupV2DetailsViewModel != null) {
                groupV2DetailsViewModel.membersAdded(selectedContacts);
            }
        });

        FilteredContactListFragment filteredContactListFragment = new FilteredContactListFragment();
        filteredContactListFragment.setContactFilterEditText(dialogContactNameFilter);
        filteredContactListFragment.removeBottomPadding();
        filteredContactListFragment.setSelectable(true);

        LiveData<List<Contact>> unfilteredContacts = AppDatabase.getInstance().group2Dao().getAllValidContactsNotInGroup(bytesOwnedIdentity, bytesGroupIdentifier, bytesAddedMemberIdentities, bytesRemovedMemberIdentities);
        filteredContactListFragment.setUnfilteredContacts(unfilteredContacts);

        Observer<List<Contact>> selectedContactsObserver = (List<Contact> contacts) -> selectedContacts = contacts;
        filteredContactListFragment.setSelectedContactsObserver(selectedContactsObserver);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.dialog_filtered_contact_list_placeholder, filteredContactListFragment);
        transaction.commit();

        Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> AppDatabase.getInstance().contactDao().nonGroupV2ContactExists(ownedIdentity.bytesOwnedIdentity)).observe(this, (Boolean nonGroupV2Exists) -> v2Warning.setVisibility((nonGroupV2Exists != null && nonGroupV2Exists) ? View.VISIBLE : View.GONE));

        return dialogView;
    }
}
