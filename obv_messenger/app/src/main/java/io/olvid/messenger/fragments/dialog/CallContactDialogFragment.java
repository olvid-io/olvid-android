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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Transformations;

import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.fragments.FilteredContactListFragment;
import io.olvid.messenger.settings.SettingsActivity;

public class CallContactDialogFragment extends DialogFragment implements View.OnClickListener {
    private Button callButton;
    private FilteredContactListFragment filteredContactListFragment;
    private CheckBox multiCallCheckBox;
    private List<Contact> selectedContacts;

    public static CallContactDialogFragment newInstance() {
        return new CallContactDialogFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        View dialogView = inflater.inflate(R.layout.dialog_fragment_start_call, container, false);
        EditText dialogContactNameFilter = dialogView.findViewById(R.id.dialog_discussion_filter);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());
        callButton = dialogView.findViewById(R.id.button_call);
        callButton.setOnClickListener(this);

        multiCallCheckBox = dialogView.findViewById(R.id.mulit_call_checkbox);
        multiCallCheckBox.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            if (callButton == null || filteredContactListFragment == null) {
                return;
            }
            if (isChecked) {
                callButton.setVisibility(View.VISIBLE);
                filteredContactListFragment.setSelectable(true);
            } else {
                callButton.setVisibility(View.GONE);
                filteredContactListFragment.setSelectable(false);
            }
            callButton.setEnabled(selectedContacts != null && selectedContacts.size() != 0);
        });


        filteredContactListFragment = new FilteredContactListFragment();
        filteredContactListFragment.removeBottomPadding();
        filteredContactListFragment.setContactFilterEditText(dialogContactNameFilter);
        filteredContactListFragment.setUnfilteredContacts(Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return null;
            }
            return AppDatabase.getInstance().contactDao().getAllForOwnedIdentityWithChannel(ownedIdentity.bytesOwnedIdentity);
        }));
        filteredContactListFragment.setOnClickDelegate(new FilteredContactListFragment.FilteredContactListOnClickDelegate() {
            @Override
            public void contactClicked(View view, final Contact contact) {
                dismiss();
                App.startWebrtcCall(view.getContext(), contact.bytesOwnedIdentity, contact.bytesContactIdentity);
            }

            @Override
            public void contactLongClicked(View view, Contact contact) {
            }
        });
        filteredContactListFragment.setSelectedContactsObserver(this::onContactsSelected);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.dialog_filtered_contact_list_placeholder, filteredContactListFragment);
        transaction.commit();

        return dialogView;
    }

    @Override
    public void onClick(View view) {
        long id = view.getId();
        if (id == R.id.button_call) {
            dismiss();
            if (selectedContacts == null || selectedContacts.size() == 0) {
                return;
            }
            App.startWebrtcMultiCall(view.getContext(), selectedContacts.get(0).bytesOwnedIdentity, selectedContacts, null);
        }
    }

    public void onContactsSelected(List<Contact> selectedContacts) {
        this.selectedContacts = selectedContacts;
        if (callButton != null) {
            callButton.setEnabled(selectedContacts != null && selectedContacts.size() != 0);
        }
    }
}
