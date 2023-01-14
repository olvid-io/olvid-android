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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.fragments.FilteredContactListFragment;
import io.olvid.messenger.settings.SettingsActivity;

public class ContactIntroductionDialogFragment extends DialogFragment {
    private static final String BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity";
    private static final String BYTES_CONTACT_IDENTITY_KEY = "bytes_contact_identity";
    private static final String DISPLAY_NAME_KEY = "display_name";
    private byte[] bytesOwnedIdentity;
    private byte[] bytesContactIdentityA;
    private String displayNameA;

    private List<Contact> selectedContacts = null;

    public static ContactIntroductionDialogFragment newInstance(byte[] bytesOwnedIdentity, byte[] bytesContactIdentityA, String displayNameA) {
        ContactIntroductionDialogFragment fragment = new ContactIntroductionDialogFragment();
        Bundle args = new Bundle();
        args.putByteArray(BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        args.putByteArray(BYTES_CONTACT_IDENTITY_KEY, bytesContactIdentityA);
        args.putString(DISPLAY_NAME_KEY, displayNameA);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            bytesOwnedIdentity = getArguments().getByteArray(BYTES_OWNED_IDENTITY_KEY);
            bytesContactIdentityA = getArguments().getByteArray(BYTES_CONTACT_IDENTITY_KEY);
            displayNameA = getArguments().getString(DISPLAY_NAME_KEY);
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
        dialogTitle.setText(getResources().getString(R.string.dialog_title_introduce_contact, displayNameA));
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());

        FilteredContactListFragment filteredContactListFragment = new FilteredContactListFragment();
        filteredContactListFragment.removeBottomPadding();
        filteredContactListFragment.setSelectable(true);
        filteredContactListFragment.setContactFilterEditText(dialogContactNameFilter);
        filteredContactListFragment.setUnfilteredContacts(Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().contactDao().getAllOneToOneForOwnedIdentityWithChannelExcludingOne(ownedIdentity.bytesOwnedIdentity, bytesContactIdentityA);
        }));
        Button okButton = dialogView.findViewById(R.id.button_ok);
        okButton.setOnClickListener(view -> {
            dismiss();

            if (selectedContacts == null || selectedContacts.size() == 0) {
                return;
            }

            final byte[][] bytesNewMemberIdentities = new byte[selectedContacts.size()][];

            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Contact selectedContact : selectedContacts) {
                if (i != 0) {
                    sb.append(getString(R.string.text_contact_names_separator));
                }
                sb.append(selectedContact.getCustomDisplayName());
                bytesNewMemberIdentities[i] = selectedContact.bytesContactIdentity;
                i++;
            }

            final AlertDialog.Builder builder = new SecureAlertDialogBuilder(view.getContext(), R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_contact_introduction)
                    .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                        try {
                            AppSingleton.getEngine().startContactMutualIntroductionProtocol(bytesOwnedIdentity, bytesContactIdentityA, bytesNewMemberIdentities);
                            App.toast(R.string.toast_message_contacts_introduction_started, Toast.LENGTH_SHORT);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .setNegativeButton(R.string.button_label_cancel, null);
            if (bytesNewMemberIdentities.length == 1) {
                builder.setMessage(getString(R.string.dialog_message_contact_introduction, displayNameA, sb.toString()));
            } else {
                builder.setMessage(getString(R.string.dialog_message_contact_introduction_multiple, displayNameA, bytesNewMemberIdentities.length, sb.toString()));
            }
            builder.create().show();


        });

        Observer<List<Contact>> selectedContactsObserver = contacts -> selectedContacts = contacts;
        filteredContactListFragment.setSelectedContactsObserver(selectedContactsObserver);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.dialog_filtered_contact_list_placeholder, filteredContactListFragment);
        transaction.commit();

        return dialogView;
    }
}
