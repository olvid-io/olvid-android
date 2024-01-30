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

package io.olvid.messenger.webrtc;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
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
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.Logger;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.fragments.FilteredContactListFragment;
import io.olvid.messenger.settings.SettingsActivity;

public class CallParticipantAdditionDialogFragment extends DialogFragment {
    private static final String BYTES_OWNED_IDENTITY_KEY = "bytes_owned_identity";

    private WebrtcServiceConnection webrtcServiceConnection;
    private WebrtcCallService webrtcCallService;
    private FilteredContactListFragment filteredContactListFragment;
    private byte[] bytesOwnedIdentity;
    private List<Contact> selectedContacts = null;
    private DialogClosedListener dialogClosedListener = null;

    public static CallParticipantAdditionDialogFragment newInstance(@NonNull byte[] bytesOwnedIdentity) {
        CallParticipantAdditionDialogFragment fragment = new CallParticipantAdditionDialogFragment();
        Bundle args = new Bundle();
        args.putByteArray(BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            bytesOwnedIdentity = getArguments().getByteArray(BYTES_OWNED_IDENTITY_KEY);
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
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (getActivity() != null) {
            webrtcServiceConnection = new WebrtcServiceConnection();
            Intent serviceBindIntent = new Intent(getActivity(), WebrtcCallService.class);
            getActivity().bindService(serviceBindIntent, webrtcServiceConnection, 0);
        } else {
            dismiss();
        }
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

    @Override
    public void onDetach() {
        super.onDetach();
        if (dialogClosedListener != null) {
            dialogClosedListener.onClose();
        }
    }

    public void setDialogClosedListener(DialogClosedListener dialogClosedListener) {
        this.dialogClosedListener = dialogClosedListener;
    }

    private void setWebrtcCallService(WebrtcCallService webrtcCallService) {
        if (webrtcCallService != null) {
            this.webrtcCallService = webrtcCallService;
            if (filteredContactListFragment != null) {
                filteredContactListFragment.setUnfilteredContacts(
                        Transformations.switchMap(this.webrtcCallService.getCallParticipantsLiveData(), callParticipantPojos -> {
                            if (callParticipantPojos == null) {
                                return AppDatabase.getInstance().contactDao().getAllForOwnedIdentityWithChannel(bytesOwnedIdentity);
                            }
                            List<byte[]> excludedContacts = new ArrayList<>(callParticipantPojos.size());
                            for (WebrtcCallService.CallParticipantPojo callParticipantPojo: callParticipantPojos) {
                                excludedContacts.add(callParticipantPojo.bytesContactIdentity);
                            }
                            return AppDatabase.getInstance().contactDao().getAllForOwnedIdentityWithChannelExcludingSome(bytesOwnedIdentity, excludedContacts);
                        }));
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_pick_multiple_contacts, container, false);
        EditText dialogContactNameFilter = dialogView.findViewById(R.id.dialog_discussion_filter);
        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        dialogTitle.setText(R.string.dialog_title_invite_call_participants);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());
        Button okButton = dialogView.findViewById(R.id.button_ok);
        okButton.setOnClickListener(view -> {
            dismiss();

            if (selectedContacts == null || selectedContacts.size() == 0) {
                return;
            }

            if (webrtcCallService != null) {
                webrtcCallService.callerAddCallParticipants(selectedContacts);
            }
        });

        filteredContactListFragment = new FilteredContactListFragment();
        filteredContactListFragment.setContactFilterEditText(dialogContactNameFilter);
        filteredContactListFragment.setSelectable(true);

        Observer<List<Contact>> selectedContactsObserver = (List<Contact> contacts) -> selectedContacts = contacts;
        filteredContactListFragment.setSelectedContactsObserver(selectedContactsObserver);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.dialog_filtered_contact_list_placeholder, filteredContactListFragment);
        transaction.commit();

        return dialogView;
    }

    private class WebrtcServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!(service instanceof WebrtcCallService.WebrtcCallServiceBinder)) {
                Logger.e("☎ CallParticipantAdditionDialogFragment bound to bad service!!!");
                dismiss();
                return;
            }
            WebrtcCallService.WebrtcCallServiceBinder binder = (WebrtcCallService.WebrtcCallServiceBinder) service;
            setWebrtcCallService(binder.getService());
        }

        @Override
        public void onNullBinding(ComponentName name) {
            dismiss();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            setWebrtcCallService(null);
            dismiss();
        }
    }

    interface DialogClosedListener {
        void onClose();
    }
}
