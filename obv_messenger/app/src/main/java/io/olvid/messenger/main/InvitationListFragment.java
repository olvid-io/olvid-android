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

package io.olvid.messenger.main;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import io.olvid.engine.datatypes.Constants;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonGroupDetails;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.ObvDialog;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.LoadAwareAdapter;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.entity.Invitation;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;


public class InvitationListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, EngineNotificationListener {
    private FragmentActivity activity;
    private InvitationListViewModel invitationListViewModel;
    private InvitationListAdapter adapter;
    private boolean invitationsAreVisible;
    private Long engineNotificationListenerRegistrationNumber;
    private SwipeRefreshLayout swipeRefreshLayout;

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = requireActivity();
        invitationListViewModel = new ViewModelProvider(this).get(InvitationListViewModel.class);
        invitationsAreVisible = false;
        engineNotificationListenerRegistrationNumber = null;
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.SERVER_POLLED, this);
        AppSingleton.getContactNamesCache().observe(activity, (HashMap<BytesKey, String> cache) -> {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppSingleton.getEngine().removeNotificationListener(EngineNotifications.SERVER_POLLED, this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.activity_main_fragment_invitation_list, container, false);

        EmptyRecyclerView recyclerView = rootView.findViewById(R.id.invitation_list_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        View recyclerEmptyView = rootView.findViewById(R.id.invitation_list_empty_view);
        recyclerView.setEmptyView(recyclerEmptyView);

        View loadingSpinner = rootView.findViewById(R.id.loading_spinner);
        recyclerView.setLoadingSpinner(loadingSpinner);

        adapter = new InvitationListAdapter(this);
        invitationListViewModel.getInvitations().observe(activity, adapter);
        recyclerView.setAdapter(adapter);


        swipeRefreshLayout = rootView.findViewById(R.id.discussion_list_swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.primary700);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(getResources().getColor(R.color.dialogBackground));

        return rootView;
    }

    @Override
    public void onRefresh() {
        if (AppSingleton.getBytesCurrentIdentity() != null) {
            AppSingleton.getEngine().downloadMessages(AppSingleton.getBytesCurrentIdentity());
            App.runThread(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                        App.toast(R.string.toast_message_polling_failed, Toast.LENGTH_SHORT);
                    }
                });
            });
        } else {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void callback(String notificationName, HashMap<String, Object> userInfo) {
        if (EngineNotifications.SERVER_POLLED.equals(notificationName)) {
            byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.SERVER_POLLED_BYTES_OWNED_IDENTITY_KEY);
            final Boolean success = (Boolean) userInfo.get(EngineNotifications.SERVER_POLLED_SUCCESS_KEY);
            if (success != null
                    && Arrays.equals(bytesOwnedIdentity, AppSingleton.getBytesCurrentIdentity())) {
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        if (!success) {
                            App.toast(R.string.toast_message_polling_failed, Toast.LENGTH_SHORT);
                        }
                    });
                }
            }
        }
    }


    @Override
    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
        engineNotificationListenerRegistrationNumber = registrationNumber;
    }

    @Override
    public long getEngineNotificationListenerRegistrationNumber() {
        return engineNotificationListenerRegistrationNumber;
    }

    @Override
    public boolean hasEngineNotificationListenerRegistrationNumber() {
        return engineNotificationListenerRegistrationNumber != null;
    }


    public void invitationClicked(int viewId, @NonNull final Invitation invitation, @Nullable String lastSas) {
        ObvDialog dialog = invitation.associatedDialog;
        if (viewId == R.id.button_accept) {
            switch (dialog.getCategory().getId()) {
                case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY: {
                    try {
                        dialog.setResponseToAcceptInvite(true);
                        AppSingleton.getEngine().respondToDialog(dialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY: {
                    try {
                        dialog.setResponseToAcceptMediatorInvite(true);
                        AppSingleton.getEngine().respondToDialog(dialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY: {
                    try {
                        dialog.setResponseToAcceptGroupInvite(true);
                        AppSingleton.getEngine().respondToDialog(dialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY: {
                    try {
                        dialog.setResponseToAcceptOneToOneInvitation(true);
                        AppSingleton.getEngine().respondToDialog(dialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        } else if (viewId == R.id.button_reject) {
            switch (dialog.getCategory().getId()) {
                case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY: {
                    try {
                        dialog.setAbortOneToOneInvitationSent(true);
                        AppSingleton.getEngine().respondToDialog(dialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY: {
                    try {
                        dialog.setResponseToAcceptOneToOneInvitation(false);
                        AppSingleton.getEngine().respondToDialog(dialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        } else if (viewId == R.id.button_ignore) {
            switch (dialog.getCategory().getId()) {
                case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY: {
                    try {
                        dialog.setResponseToAcceptInvite(false);
                        AppSingleton.getEngine().respondToDialog(dialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY: {
                    try {
                        dialog.setResponseToAcceptMediatorInvite(false);
                        AppSingleton.getEngine().respondToDialog(dialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY: {
                    try {
                        dialog.setResponseToAcceptGroupInvite(false);
                        AppSingleton.getEngine().respondToDialog(dialog);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        } else if (viewId == R.id.button_validate_sas) {
            if (dialog.getCategory().getId() == ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY && lastSas != null) {
                try {
                    invitationListViewModel.setLastSas(lastSas, dialog.getUuid());
                    dialog.setResponseToSasExchange(lastSas.getBytes(StandardCharsets.UTF_8));
                    AppSingleton.getEngine().respondToDialog(dialog);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (viewId == R.id.button_ok) {
            if (dialog.getCategory().getId() == ObvDialog.Category.MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY) {
                try {
                    AppSingleton.getEngine().deletePersistedDialog(invitation.dialogUuid);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (viewId == R.id.button_abort) {
            switch (dialog.getCategory().getId()) {
                case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY:
                case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY:
                case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY:
                case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY: {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(InvitationListFragment.this.getContext(), R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_abort_invitation)
                            .setMessage(getString(R.string.dialog_message_abort_invitation))
                            .setPositiveButton(R.string.button_label_ok, (dialog1, which) -> {
                                try {
                                    AppSingleton.getEngine().abortProtocol(invitation.associatedDialog);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            })
                            .setNegativeButton(R.string.button_label_cancel, null);
                    builder.create().show();
                    break;
                }
                case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY: {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(InvitationListFragment.this.getContext(), R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_abort_invitation)
                            .setMessage(getString(R.string.dialog_message_abort_invitation))
                            .setPositiveButton(R.string.button_label_ok, (dialog1, which) -> {
                                try {
                                    ObvDialog obvDialog = invitation.associatedDialog;
                                    obvDialog.setAbortOneToOneInvitationSent(true);
                                    AppSingleton.getEngine().respondToDialog(obvDialog);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            })
                            .setNegativeButton(R.string.button_label_cancel, null);
                    builder.create().show();
                }
            }
        }
    }

    public void setInvitationsAreVisible(boolean visible) {
        if (!invitationsAreVisible && visible) {
            if (adapter != null && adapter.invitations != null) {
                for (Invitation invitation: adapter.invitations) {
                    AndroidNotificationManager.clearInvitationNotification(invitation.dialogUuid);
                }
            }
        }
        invitationsAreVisible = visible;
    }

    public class InvitationListAdapter extends LoadAwareAdapter<InvitationListAdapter.ViewHolder> implements Observer<List<Invitation>> {
        private List<Invitation> invitations = null;
        private final LayoutInflater inflater;

        private static final int TYPE_SIMPLE = 0;
        private static final int TYPE_SAS = 1;
        private static final int TYPE_GROUP = 2;

        InvitationListAdapter(InvitationListFragment fragment) {
            this.inflater = LayoutInflater.from(fragment.getContext());
        }

        @Override
        public boolean isLoadingDone() {
            return invitations != null;
        }

        @Override
        public int getItemViewType(int position) {
            if (invitations == null) {
                return 0;
            }
            Invitation invitation = invitations.get(position);
            switch (invitation.associatedDialog.getCategory().getId()) {
                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                    return TYPE_GROUP;
                case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY:
                case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY:
                    return TYPE_SAS;
                case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY:
                case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY:
                case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                case ObvDialog.Category.MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY:
                case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY:
                case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY:
                default:
                    return TYPE_SIMPLE;
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View invitationRootView;
            switch (viewType) {
                case TYPE_GROUP:
                    invitationRootView = inflater.inflate(R.layout.item_view_invitation_group, parent, false);
                    break;
                case TYPE_SAS:
                    invitationRootView = inflater.inflate(R.layout.item_view_invitation_sas, parent, false);
                    break;
                case TYPE_SIMPLE:
                default:
                    invitationRootView = inflater.inflate(R.layout.item_view_invitation_simple, parent, false);
                    break;
            }
            return new ViewHolder(invitationRootView, viewType);
        }


        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (invitations == null) {
                return;
            }
            Invitation invitation = invitations.get(position);
            if (invitationsAreVisible) {
                AndroidNotificationManager.clearInvitationNotification(invitation.dialogUuid);
            }

            final ObvDialog dialog = invitation.associatedDialog;
            String invitationName;
            switch (dialog.getCategory().getId()) {
                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                {
                    try {
                        JsonGroupDetails groupDetails = AppSingleton.getJsonObjectMapper().readValue(dialog.getCategory().getSerializedGroupDetails(), JsonGroupDetails.class);
                        invitationName = groupDetails.getName();
                    } catch (Exception e) {
                        invitationName = null;
                        e.printStackTrace();
                    }
                    holder.initialView.setGroup(dialog.getCategory().getBytesGroupOwnerAndUid());
                    break;
                }
                default: {
                    switch (dialog.getCategory().getId()) {
                        case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY: {
                            invitationName = AppSingleton.getContactCustomDisplayName(invitation.associatedDialog.getCategory().getBytesContactIdentity());
                            if (invitationName != null) {
                                holder.initialView.setFromCache(invitation.associatedDialog.getCategory().getBytesContactIdentity());
                            } else {
                                invitationName = invitation.associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails();
                                holder.initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), StringUtils.getInitial(invitationName));
                            }
                            break;
                        }
                        case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY:
                        case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY: {
                            invitationName = AppSingleton.getContactCustomDisplayName(invitation.associatedDialog.getCategory().getBytesContactIdentity());
                            if (invitationName == null) {
                                invitationName = getString(R.string.text_deleted_contact);
                            }
                            holder.initialView.setFromCache(invitation.associatedDialog.getCategory().getBytesContactIdentity());
                            break;
                        }
                        case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY:
                        case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY:
                        case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                        case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                        case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY:
                        case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                        case ObvDialog.Category.MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY:
                        default:
                            try {
                                JsonIdentityDetails identityDetails = AppSingleton.getJsonObjectMapper().readValue(invitation.associatedDialog.getCategory().getContactDisplayNameOrSerializedDetails(), JsonIdentityDetails.class);
                                invitationName = identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName());
                            } catch (Exception e) {
                                e.printStackTrace();
                                invitationName = null;
                            }
                            holder.initialView.setNullTrustLevel();
                            holder.initialView.setInitial(invitation.associatedDialog.getCategory().getBytesContactIdentity(), StringUtils.getInitial(invitationName));
                            break;
                    }
                }
            }

            holder.nameTextView.setText(invitationName);
            holder.statusTextView.setText(invitation.getStatusText());
            invitation.displayStatusDescriptionTextAsync(holder.statusDescriptionTextView);
            holder.additionalHeaderSpace.removeAllViews();

            switch (dialog.getCategory().getId()) {
                case ObvDialog.Category.INVITE_SENT_DIALOG_CATEGORY:
                case ObvDialog.Category.INVITE_ACCEPTED_DIALOG_CATEGORY:
                case ObvDialog.Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                case ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY: {
                    holder.timestampTextView.setText(StringUtils.getNiceDateString(getContext(), invitation.invitationTimestamp));
                    holder.abortButton.setVisibility(View.VISIBLE);
                    holder.abortButton.setEnabled(true);
                    holder.goToButton.setVisibility(View.GONE);
                    holder.okButton.setVisibility(View.GONE);
                    holder.ignoreButton.setVisibility(View.GONE);
                    holder.acceptButton.setVisibility(View.GONE);
                    holder.rejectButton.setVisibility(View.GONE);
                    break;
                }
                case ObvDialog.Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                case ObvDialog.Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY: {
                    holder.timestampTextView.setText(StringUtils.getNiceDateString(getContext(), invitation.associatedDialog.getCategory().serverTimestamp));
                    holder.abortButton.setVisibility(View.GONE);
                    holder.goToButton.setVisibility(View.GONE);
                    holder.okButton.setVisibility(View.GONE);
                    holder.ignoreButton.setVisibility(View.VISIBLE);
                    holder.ignoreButton.setEnabled(true);
                    holder.acceptButton.setVisibility(View.VISIBLE);
                    holder.acceptButton.setEnabled(true);
                    holder.rejectButton.setVisibility(View.GONE);
                    break;
                }
                case ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY: {
                    holder.timestampTextView.setText(StringUtils.getNiceDateString(getContext(), invitation.associatedDialog.getCategory().serverTimestamp));
                    holder.abortButton.setVisibility(View.GONE);
                    holder.goToButton.setVisibility(View.GONE);
                    holder.okButton.setVisibility(View.GONE);
                    holder.ignoreButton.setVisibility(View.GONE);
                    holder.acceptButton.setVisibility(View.VISIBLE);
                    holder.acceptButton.setEnabled(true);
                    holder.rejectButton.setVisibility(View.VISIBLE);
                    holder.rejectButton.setEnabled(true);
                    break;
                }
                case ObvDialog.Category.MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY: {
                    holder.timestampTextView.setText(StringUtils.getNiceDateString(getContext(), invitation.invitationTimestamp));
                    final byte[] bytesContactIdentity = dialog.getCategory().getBytesContactIdentity();
                    holder.goToButton.setOnClickListener(view -> App.openOneToOneDiscussionActivity(view.getContext(), dialog.getBytesOwnedIdentity(), bytesContactIdentity, true));
                    holder.abortButton.setVisibility(View.GONE);
                    holder.goToButton.setVisibility(View.VISIBLE);
                    holder.okButton.setVisibility(View.VISIBLE);
                    holder.okButton.setEnabled(true);
                    holder.ignoreButton.setVisibility(View.GONE);
                    holder.acceptButton.setVisibility(View.GONE);
                    holder.rejectButton.setVisibility(View.GONE);
                    break;
                }
                case ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY: {
                    holder.timestampTextView.setText(StringUtils.getNiceDateString(getContext(), invitation.associatedDialog.getCategory().serverTimestamp));
                    holder.yourSasTextView.setText(new String(dialog.getCategory().getSasToDisplay(), StandardCharsets.UTF_8));
                    holder.theirSasEditText.setVisibility(View.VISIBLE);
                    if (dialog.getUuid().equals(invitationListViewModel.getLastSasDialogUUID())) {
                        if (invitation.invitationTimestamp != holder.invitationTimestamp){
                            holder.theirSasEditText.setText(invitationListViewModel.getLastSas());
                            holder.theirSasEditText.setSelection(0, invitationListViewModel.getLastSas() == null ? 0 : invitationListViewModel.getLastSas().length());
                            holder.wrongCodeTextView.setVisibility(View.VISIBLE);
                            holder.theirSasEditText.requestFocus();
                            holder.validateSasButton.setEnabled(invitationListViewModel.getLastSas() != null && invitationListViewModel.getLastSas().length() == Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS);
                        }
                    } else {
                        holder.theirSasEditText.setText("");
                        holder.wrongCodeTextView.setVisibility(View.GONE);
                        holder.validateSasButton.setEnabled(false);
                        holder.invitationTimestamp = invitation.invitationTimestamp;
                    }
                    holder.sasCorrectImageView.setVisibility(View.GONE);

                    holder.abortButton.setVisibility(View.VISIBLE);
                    holder.abortButton.setEnabled(true);
                    holder.validateSasButton.setVisibility(View.VISIBLE);
                    break;
                }
                case ObvDialog.Category.SAS_CONFIRMED_DIALOG_CATEGORY: {
                    holder.timestampTextView.setText(StringUtils.getNiceDateString(getContext(), invitation.invitationTimestamp));
                    holder.yourSasTextView.setText(new String(dialog.getCategory().getSasToDisplay(), StandardCharsets.UTF_8));
                    if (dialog.getUuid().equals(invitationListViewModel.getLastSasDialogUUID())) {
                        Context context = InvitationListFragment.this.getContext();
                        if (context != null) {
                            InputMethodManager manager = ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE));
                            Activity activity = getActivity();
                            if (manager != null && activity != null) {
                                if (activity.getCurrentFocus() != null) {
                                    if (activity.getCurrentFocus().getWindowToken() != null) {
                                        manager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
                                    }
                                }
                            }
                        }
                    }
                    holder.theirSasEditText.setVisibility(View.GONE);
                    holder.wrongCodeTextView.setVisibility(View.GONE);
                    holder.sasCorrectImageView.setVisibility(View.VISIBLE);

                    holder.abortButton.setVisibility(View.VISIBLE);
                    holder.abortButton.setEnabled(true);
                    holder.validateSasButton.setVisibility(View.GONE);
                    break;
                }
                case ObvDialog.Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY: {
                    holder.timestampTextView.setText(StringUtils.getNiceDateString(getContext(), invitation.associatedDialog.getCategory().serverTimestamp));
                    invitation.listGroupMembersAsync(holder.groupMembersTextView);
                    holder.groupMembersGroup.setVisibility(View.VISIBLE);
                    holder.okButton.setVisibility(View.GONE);
                    holder.goToButton.setVisibility(View.GONE);
                    holder.ignoreButton.setVisibility(View.VISIBLE);
                    holder.ignoreButton.setEnabled(true);
                    holder.acceptButton.setVisibility(View.VISIBLE);
                    holder.acceptButton.setEnabled(true);
                    holder.abortButton.setVisibility(View.GONE);
                    break;
                }
            }
        }

        @Override
        public int getItemCount() {
            if (invitations != null) {
                return invitations.size();
            }
            return 0;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(@Nullable final List<Invitation> invitations) {
            if ((this.invitations != null) && (invitations != null)) {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    final List<Invitation> oldList = InvitationListAdapter.this.invitations;
                    final @NonNull List<Invitation> newList = invitations;

                    @Override
                    public int getOldListSize() {
                        return oldList.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return newList.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return oldList.get(oldItemPosition).dialogUuid.equals(newList.get(newItemPosition).dialogUuid);
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        ObvDialog oldDialog = oldList.get(oldItemPosition).associatedDialog;
                        if ((oldDialog.getCategory().getId() == ObvDialog.Category.SAS_EXCHANGE_DIALOG_CATEGORY) && oldDialog.getUuid().equals(invitationListViewModel.getLastSasDialogUUID())){
                            return false;
                        }
                        if ((oldDialog.getCategory().getId() == ObvDialog.Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY || oldDialog.getCategory().getId() == ObvDialog.Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY)) {
                            return false;
                        }
                        return oldDialog.getCategory().getId() == newList.get(newItemPosition).associatedDialog.getCategory().getId();
                    }
                });
                this.invitations = invitations;
                result.dispatchUpdatesTo(this);
            } else {
                this.invitations = invitations;
                notifyDataSetChanged();
            }
        }

        public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            final View invitationRootView;
            // header
            final InitialView initialView;
            final TextView timestampTextView;
            final TextView nameTextView;
            final TextView statusTextView;
            final TextView statusDescriptionTextView;
            final LinearLayout additionalHeaderSpace;

            // default buttons
            final Button rejectButton;
            final Button acceptButton;
            final Button ignoreButton;
            final Button abortButton;
            final Button okButton;
            final Button goToButton;

            // SAS
            final ImageView sasCorrectImageView;
            final TextView yourSasTextView;
            final EditText theirSasEditText;
            final TextView wrongCodeTextView;
            final Button validateSasButton;
            long invitationTimestamp;

            // Group
            final TextView groupMembersTextView;
            final LinearLayout groupMembersGroup;

            ViewHolder(final View invitationRootView, int viewType) {
                super(invitationRootView);
                this.invitationRootView = invitationRootView;

                initialView = this.invitationRootView.findViewById(R.id.invitation_initial_view);
                timestampTextView = this.invitationRootView.findViewById(R.id.invitation_timestamp_text_view);
                nameTextView = this.invitationRootView.findViewById(R.id.invitation_name_text_view);
                statusTextView = this.invitationRootView.findViewById(R.id.invitation_status_text_view);
                statusDescriptionTextView = this.invitationRootView.findViewById(R.id.invitation_status_description_text_view);
                additionalHeaderSpace = this.invitationRootView.findViewById(R.id.additional_header_space);

                acceptButton = this.invitationRootView.findViewById(R.id.button_accept);
                rejectButton = this.invitationRootView.findViewById(R.id.button_reject);
                ignoreButton = this.invitationRootView.findViewById(R.id.button_ignore);
                abortButton = this.invitationRootView.findViewById(R.id.button_abort);
                okButton = this.invitationRootView.findViewById(R.id.button_ok);
                goToButton = this.invitationRootView.findViewById(R.id.button_go_to);

                sasCorrectImageView = this.invitationRootView.findViewById(R.id.imageview_sas_correct);
                theirSasEditText = this.invitationRootView.findViewById(R.id.their_sas);
                yourSasTextView = this.invitationRootView.findViewById(R.id.your_sas);
                wrongCodeTextView = this.invitationRootView.findViewById(R.id.sas_input_wrong_code_text_view);
                validateSasButton = this.invitationRootView.findViewById(R.id.button_validate_sas);
                invitationTimestamp = 0;

                groupMembersTextView = this.invitationRootView.findViewById(R.id.group_members_text_view);
                groupMembersGroup = this.invitationRootView.findViewById(R.id.group_members_group);

                switch (viewType) {
                    case TYPE_GROUP:
                        acceptButton.setOnClickListener(this);
                        ignoreButton.setOnClickListener(this);
                        abortButton.setOnClickListener(this);
                        okButton.setOnClickListener(this);

                        groupMembersTextView.setMovementMethod(LinkMovementMethod.getInstance());
                        break;
                    case TYPE_SAS:
                        validateSasButton.setOnClickListener(this);
                        abortButton.setOnClickListener(this);

                        theirSasEditText.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
                            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                                if (validateSasButton.isEnabled()) {
                                    validateSasButton.performClick();
                                }
                                return true;
                            }
                            return false;
                        });
                        theirSasEditText.addTextChangedListener(new TextChangeListener() {
                            @Override
                            public void afterTextChanged(Editable editable) {
                                validateSasButton.setEnabled((editable != null) && (editable.length() == Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS));
                            }
                        });
                        break;
                    case TYPE_SIMPLE:
                        rejectButton.setOnClickListener(this);
                        acceptButton.setOnClickListener(this);
                        ignoreButton.setOnClickListener(this);
                        abortButton.setOnClickListener(this);
                        okButton.setOnClickListener(this);
                        break;
                }

            }

            @Override
            public void onClick(final View view) {
                int position = getLayoutPosition();
                if (invitations != null && position >= 0 && invitations.size() > position && view instanceof Button) {
                    view.setEnabled(false);
                    App.runThread(new Runnable() {
                        final WeakReference<View> viewWeakReference = new WeakReference<>(view);
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ignored) {}
                            final View v = viewWeakReference.get();
                            if (v != null) {
                                new Handler(Looper.getMainLooper()).post(() -> v.setEnabled(true));
                            }
                        }
                    });
                    if (theirSasEditText != null) {
                        InvitationListFragment.this.invitationClicked(view.getId(), invitations.get(position), theirSasEditText.getText() == null ? null : theirSasEditText.getText().toString());
                    } else {
                        InvitationListFragment.this.invitationClicked(view.getId(), invitations.get(position), null);
                    }
                }
            }
        }
    }
}

