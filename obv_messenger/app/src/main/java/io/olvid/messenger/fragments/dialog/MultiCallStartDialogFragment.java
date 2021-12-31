/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import android.content.Context;
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
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.fragments.FilteredContactListFragment;
import io.olvid.messenger.settings.SettingsActivity;

public class MultiCallStartDialogFragment extends DialogFragment {
    private static final String BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity";
    private static final String BYTES_GROUP_OWNER_AND_UID_KEY = "bytes_group_owner_and_uid_key";
    private static final String CONTACT_IDENTITIES_BUNDLE_KEY = "bytes_contact_identities";

    private FilteredContactListFragment filteredContactListFragment;
    private byte[] bytesOwnedIdentity;
    private byte[] bytesGroupOwnerAndUidKey;
    private final Set<BytesKey> bytesContactIdentities = new HashSet<>();

    private List<Contact> selectedContacts = null;

    public static MultiCallStartDialogFragment newInstance(@NonNull byte[] bytesOwnedIdentity, @Nullable byte[] groupOwnerAndUid, @NonNull List<byte[]> bytesContactIdentities) {
        MultiCallStartDialogFragment fragment = new MultiCallStartDialogFragment();
        Bundle args = new Bundle();
        args.putByteArray(BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        if (groupOwnerAndUid != null) {
            args.putByteArray(BYTES_GROUP_OWNER_AND_UID_KEY, groupOwnerAndUid);
        }
        Bundle bytesContactIdentitiesBundle = new Bundle();
        int count = 0;
        for (byte[] bytesContactIdentity : bytesContactIdentities) {
            bytesContactIdentitiesBundle.putByteArray(Integer.toString(count), bytesContactIdentity);
            count++;
        }
        args.putBundle(CONTACT_IDENTITIES_BUNDLE_KEY, bytesContactIdentitiesBundle);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            bytesOwnedIdentity = getArguments().getByteArray(BYTES_OWNED_IDENTITY_KEY);
            bytesGroupOwnerAndUidKey = getArguments().getByteArray(BYTES_GROUP_OWNER_AND_UID_KEY);
            Bundle bytesContactIdentitiesBundle = getArguments().getBundle(CONTACT_IDENTITIES_BUNDLE_KEY);
            if (bytesContactIdentitiesBundle != null) {
                for (String key : bytesContactIdentitiesBundle.keySet()) {
                    bytesContactIdentities.add(new BytesKey(bytesContactIdentitiesBundle.getByteArray(key)));
                }
            }
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
        dialogTitle.setText(R.string.dialog_title_start_conference_call);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());
        Button okButton = dialogView.findViewById(R.id.button_ok);
        okButton.setText(R.string.button_label_call);
        okButton.setOnClickListener(view -> {
            dismiss();

            Context context = getContext();
            if (context == null || selectedContacts == null || selectedContacts.size() == 0) {
                return;
            }

            App.startWebrtcMultiCall(context, bytesOwnedIdentity, selectedContacts, bytesGroupOwnerAndUidKey);
        });

        filteredContactListFragment = new FilteredContactListFragment();
        filteredContactListFragment.setContactFilterEditText(dialogContactNameFilter);
        filteredContactListFragment.setSelectable(true);
        LiveData<List<Contact>> contactListLivedata;
        List<byte[]> bytesIdentities = new ArrayList<>(bytesContactIdentities.size());
        for (BytesKey bytesKey : bytesContactIdentities) {
            bytesIdentities.add(bytesKey.bytes);
        }
        if (bytesGroupOwnerAndUidKey != null) {
            contactListLivedata = AppDatabase.getInstance().contactGroupJoinDao().getGroupContactsAndMore(bytesOwnedIdentity, bytesGroupOwnerAndUidKey, bytesIdentities);
        } else {
            contactListLivedata = AppDatabase.getInstance().contactDao().getWithChannelAsList(bytesOwnedIdentity, bytesIdentities);
        }
        contactListLivedata.observe(this, new Observer<List<Contact>>() {
            boolean initialized = false;

            @Override
            public void onChanged(List<Contact> contacts) {
                if (!initialized && contacts != null) {
                    List<Contact> initialContacts = new ArrayList<>(bytesContactIdentities.size());
                    for (Contact contact: contacts) {
                        if (bytesContactIdentities.contains(new BytesKey(contact.bytesContactIdentity))) {
                            initialContacts.add(contact);
                        }
                    }
                    filteredContactListFragment.setSelectedContacts(initialContacts);
                    initialized = true;
                }
            }
        });
        filteredContactListFragment.setUnfilteredContacts(contactListLivedata);

        Observer<List<Contact>> selectedContactsObserver = contacts -> selectedContacts = contacts;
        filteredContactListFragment.setSelectedContactsObserver(selectedContactsObserver);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.dialog_filtered_contact_list_placeholder, filteredContactListFragment);
        transaction.commit();

        return dialogView;
    }
}
