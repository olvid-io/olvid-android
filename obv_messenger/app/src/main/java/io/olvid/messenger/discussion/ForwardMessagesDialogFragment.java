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

package io.olvid.messenger.discussion;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.Arrays;
import java.util.List;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.ForwardMessagesTask;
import io.olvid.messenger.fragments.FilteredDiscussionListFragment;
import io.olvid.messenger.fragments.dialog.OwnedIdentitySelectionDialogFragment;
import io.olvid.messenger.settings.SettingsActivity;
import kotlin.jvm.functions.Function1;

public class ForwardMessagesDialogFragment extends DialogFragment implements View.OnClickListener {
    private FragmentActivity activity;
    private DiscussionViewModel viewModel;

    private InitialView currentIdentityInitialView;
    private TextView currentNameTextView;
    private TextView currentNameSecondLineTextView;
    private ImageView currentIdentityMutedImageView;
    private View separator;
    private OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter adapter;
    private PopupWindow ownedIdentityPopupWindow;
    private TextView transferButton;

    private List<Long> selectedDiscussionIds;

    public static ForwardMessagesDialogFragment newInstance() {
        return new ForwardMessagesDialogFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = requireActivity();
        viewModel = new ViewModelProvider(activity).get(DiscussionViewModel.class);
        viewModel.setForwardMessageBytesOwnedIdentity(AppSingleton.getBytesCurrentIdentity());
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
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
                // make the dialog background transparent to have the rounded corners of the layout
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_forward_messages, container, false);

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        currentIdentityInitialView = dialogView.findViewById(R.id.current_identity_initial_view);
        currentNameTextView = dialogView.findViewById(R.id.current_identity_name_text_view);
        currentNameSecondLineTextView = dialogView.findViewById(R.id.current_identity_name_second_line_text_view);
        currentIdentityMutedImageView = dialogView.findViewById(R.id.current_identity_muted_marker_image_view);
        separator = dialogView.findViewById(R.id.separator);

        final EditText contactNameFilter = dialogView.findViewById(R.id.discussion_filter);
        dialogView.findViewById(R.id.button_cancel).setOnClickListener(v -> dismiss());
        transferButton = dialogView.findViewById(R.id.button_forward);
        transferButton.setOnClickListener(this);
        transferButton.setEnabled(selectedDiscussionIds != null && selectedDiscussionIds.size() != 0);

        TextView switchProfileButton = dialogView.findViewById(R.id.button_switch_profile);
        switchProfileButton.setOnClickListener(v -> {
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            openSwitchProfilePopup();
        });
        switchProfileButton.setOnLongClickListener(v -> {
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            new OpenHiddenProfileDialog(activity);
            return true;
        });

        adapter = new OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter(getLayoutInflater(), (byte[] bytesOwnedIdentity) -> {
            if (ownedIdentityPopupWindow != null) {
                ownedIdentityPopupWindow.dismiss();
            }
            viewModel.setForwardMessageBytesOwnedIdentity(bytesOwnedIdentity);
        });
        Transformations.switchMap(viewModel.getForwardMessageOwnedIdentityLiveData(), (OwnedIdentity ownedIdentity) -> AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenExceptOne(ownedIdentity == null ? null : ownedIdentity.bytesOwnedIdentity)).observe(this, adapter);


        FilteredDiscussionListFragment filteredDiscussionListFragment = new FilteredDiscussionListFragment();

        LiveData<List<DiscussionDao.DiscussionAndGroupMembersNames>> unfilteredDiscussions = Transformations.switchMap(viewModel.getForwardMessageOwnedIdentityLiveData(), new Function1<OwnedIdentity, LiveData<List<DiscussionDao.DiscussionAndGroupMembersNames>>>() {
            byte[] bytesOwnedIdentity = null;

            @Override
            public LiveData<List<DiscussionDao.DiscussionAndGroupMembersNames>> invoke(OwnedIdentity ownedIdentity) {
                ForwardMessagesDialogFragment.this.bindOwnedIdentity(ownedIdentity);
                if (ownedIdentity == null) {
                    if (bytesOwnedIdentity != null) {
                        bytesOwnedIdentity = null;
                        filteredDiscussionListFragment.deselectAll();
                    }
                    return null;
                } else {
                    if (!Arrays.equals(bytesOwnedIdentity, ownedIdentity.bytesOwnedIdentity)) {
                        bytesOwnedIdentity = ownedIdentity.bytesOwnedIdentity;
                        filteredDiscussionListFragment.deselectAll();
                    }
                    return AppDatabase.getInstance().discussionDao().getAllWritableWithGroupMembersNamesOrderedByActivity(ownedIdentity.bytesOwnedIdentity);
                }
            }
        });

        filteredDiscussionListFragment.setUseDialogBackground(true);
        filteredDiscussionListFragment.setShowPinned(true);
        filteredDiscussionListFragment.setUnfilteredDiscussions(unfilteredDiscussions);
        filteredDiscussionListFragment.setDiscussionFilterEditText(contactNameFilter);
        filteredDiscussionListFragment.setSelectable(true);
        filteredDiscussionListFragment.setSelectedDiscussionIdsObserver(this::onDiscussionSelected);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.filtered_discussion_list_placeholder, filteredDiscussionListFragment);
        transaction.commit();

        return dialogView;
    }

    private void bindOwnedIdentity(OwnedIdentity ownedIdentity) {
        if (currentIdentityInitialView == null || currentNameTextView == null || currentNameSecondLineTextView == null || currentIdentityMutedImageView == null || viewModel == null) {
            return;
        }


        if (ownedIdentity == null) {
            currentIdentityInitialView.setUnknown();
            currentIdentityMutedImageView.setVisibility(View.GONE);
            return;
        }

        if (ownedIdentity.customDisplayName != null) {
            currentNameTextView.setText(ownedIdentity.customDisplayName);
            JsonIdentityDetails identityDetails = ownedIdentity.getIdentityDetails();
            currentNameSecondLineTextView.setVisibility(View.VISIBLE);
            if (identityDetails != null) {
                currentNameSecondLineTextView.setText(identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()));
            } else {
                currentNameSecondLineTextView.setText(ownedIdentity.displayName);
            }
        } else {
            JsonIdentityDetails identityDetails = ownedIdentity.getIdentityDetails();
            if (identityDetails != null) {
                currentNameTextView.setText(identityDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));

                String posComp = identityDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                if (posComp != null) {
                    currentNameSecondLineTextView.setVisibility(View.VISIBLE);
                    currentNameSecondLineTextView.setText(posComp);
                } else {
                    currentNameSecondLineTextView.setVisibility(View.GONE);
                }
            } else {
                currentNameTextView.setText(ownedIdentity.displayName);
                currentNameSecondLineTextView.setVisibility(View.GONE);
                currentNameSecondLineTextView.setText(null);
            }
        }
        currentIdentityInitialView.setOwnedIdentity(ownedIdentity);
        if (ownedIdentity.prefMuteNotifications && (ownedIdentity.prefMuteNotificationsTimestamp == null || ownedIdentity.prefMuteNotificationsTimestamp > System.currentTimeMillis())) {
            currentIdentityMutedImageView.setVisibility(View.VISIBLE);
        } else {
            currentIdentityMutedImageView.setVisibility(View.GONE);
        }
    }



    private void openSwitchProfilePopup() {
        if (separator == null || adapter == null) {
            return;
        }
        @SuppressLint("InflateParams")
        View popupView = getLayoutInflater().inflate(R.layout.popup_switch_owned_identity, null);
        ownedIdentityPopupWindow = new PopupWindow(popupView, separator.getWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        ownedIdentityPopupWindow.setElevation(12);
        ownedIdentityPopupWindow.setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.background_half_rounded_dialog));
        ownedIdentityPopupWindow.setOnDismissListener(() -> ownedIdentityPopupWindow = null);

        EmptyRecyclerView ownedIdentityListRecyclerView = popupView.findViewById(R.id.owned_identity_list_recycler_view);
        ownedIdentityListRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        ownedIdentityListRecyclerView.setAdapter(adapter);
        ownedIdentityListRecyclerView.setEmptyView(popupView.findViewById(R.id.empty_view));

        ownedIdentityPopupWindow.setAnimationStyle(R.style.FadeInAndOutAnimation);
        ownedIdentityPopupWindow.showAsDropDown(separator);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_forward) {
            if (selectedDiscussionIds == null || selectedDiscussionIds.size() == 0) {
                return;
            }
            dismiss();
            App.runThread(new ForwardMessagesTask(viewModel.getMessageIdsToForward(), selectedDiscussionIds));
        }
    }

    private void onDiscussionSelected(List<Long> selectedDiscussionIds) {
        this.selectedDiscussionIds = selectedDiscussionIds;
        if (transferButton != null) {
            transferButton.setEnabled(selectedDiscussionIds != null && selectedDiscussionIds.size() != 0);
        }
    }

    private class OpenHiddenProfileDialog extends io.olvid.messenger.customClasses.OpenHiddenProfileDialog {
        public OpenHiddenProfileDialog(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @Override
        protected void onHiddenIdentityPasswordEntered(AlertDialog dialog, byte[] byteOwnedIdentity) {
            dialog.dismiss();
            viewModel.setForwardMessageBytesOwnedIdentity(byteOwnedIdentity);
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        // clear the selected identity in case it is hidden to avoid briefly showing the hidden profile discussion list on re-open
        // do not use null here as the switchMap LiveData depending on it will not be properly updated
        viewModel.setForwardMessageBytesOwnedIdentity(new byte[0]);
    }
}
