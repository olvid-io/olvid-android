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
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.settings.SettingsActivity;

public class OwnedIdentitySelectionDialogFragment extends DialogFragment {
    private FragmentActivity activity;
    private DialogViewModel dialogViewModel;

    private boolean showAddProfileButtonAsOpenHiddenProfile = false;

    public static OwnedIdentitySelectionDialogFragment newInstance(AppCompatActivity activity, int dialogTitleResourceId, OnOwnedIdentitySelectedListener onOwnedIdentitySelectedListener) {
        DialogViewModel dialogViewModel = new ViewModelProvider(activity).get(DialogViewModel.class);
        dialogViewModel.dialogTitleResourceId = dialogTitleResourceId;
        dialogViewModel.onOwnedIdentitySelectedListener = onOwnedIdentitySelectedListener;

        return new OwnedIdentitySelectionDialogFragment();
    }

    public void setShowAddProfileButtonAsOpenHiddenProfile(boolean showAddProfileButtonAsOpenHiddenProfile) {
        this.showAddProfileButtonAsOpenHiddenProfile = showAddProfileButtonAsOpenHiddenProfile;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        activity = requireActivity();
        dialogViewModel = new ViewModelProvider(activity).get(DialogViewModel.class);


        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            if (SettingsActivity.preventScreenCapture()) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
            window.setBackgroundDrawableResource(R.drawable.background_rounded_dialog);
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
                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                int width = displayMetrics.widthPixels - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, displayMetrics);
                window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_owned_identity_selection, container, false);

        dialogView.findViewById(R.id.button_cancel).setOnClickListener(v -> dismiss());

        RecyclerView ownedIdentityListRecyclerView = dialogView.findViewById(R.id.owned_identity_list_recycler_view);
        OwnedIdentityListAdapter adapter = new OwnedIdentityListAdapter(inflater, bytesOwnedIdentity -> {
            dismiss();
            if (dialogViewModel.onOwnedIdentitySelectedListener != null) {
                dialogViewModel.onOwnedIdentitySelectedListener.onOwnedIdentitySelected(bytesOwnedIdentity);
            }
        });
        ownedIdentityListRecyclerView.setLayoutManager(new LinearLayoutManager(inflater.getContext()));
        ownedIdentityListRecyclerView.setAdapter(adapter);

        AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenLiveData().observe(this, adapter);

        TextView dialogTitleTextView = dialogView.findViewById(R.id.dialog_title_text_view);
        dialogTitleTextView.setText(dialogViewModel.dialogTitleResourceId);

        final TextView addProfileButton = dialogView.findViewById(R.id.button_add_profile);
        final TextView hiddenProfileButton = dialogView.findViewById(R.id.button_open_hidden_profile);
        if (showAddProfileButtonAsOpenHiddenProfile) {
            hiddenProfileButton.setOnClickListener(v -> {
                dismiss();
                new OpenHiddenProfileDialog(activity);
            });
            hiddenProfileButton.setVisibility(View.VISIBLE);
            addProfileButton.setVisibility(View.GONE);
        } else {
            addProfileButton.setOnClickListener(v -> {
                dismiss();
                if (dialogViewModel.onOwnedIdentitySelectedListener != null) {
                    dialogViewModel.onOwnedIdentitySelectedListener.onNewProfileCreationSelected();
                }
            });
            addProfileButton.setOnLongClickListener(v -> {
                dismiss();
                new OpenHiddenProfileDialog(activity);
                return true;
            });
            hiddenProfileButton.setVisibility(View.GONE);
            addProfileButton.setVisibility(View.VISIBLE);
        }

        return dialogView;
    }


    public static class OwnedIdentityListAdapter extends RecyclerView.Adapter<OwnedIdentityViewHolder> implements Observer<List<OwnedIdentity>> {
        @NonNull private final LayoutInflater layoutInflater;
        @Nullable private final OnClickListener onClickListener;
        private List<OwnedIdentity> ownedIdentities;

        public OwnedIdentityListAdapter(@NonNull LayoutInflater layoutInflater, @Nullable OnClickListener onClickListener) {
            this.layoutInflater = layoutInflater;
            this.onClickListener = onClickListener;
        }

        @Override
        public void onChanged(List<OwnedIdentity> ownedIdentities) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                final List<OwnedIdentity> oldList = OwnedIdentityListAdapter.this.ownedIdentities;
                final List<OwnedIdentity> newList = ownedIdentities;

                @Override
                public int getOldListSize() {
                    return oldList != null ? oldList.size() : 0;
                }

                @Override
                public int getNewListSize() {
                    return newList != null ? newList.size() : 0;
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return Arrays.equals(oldList.get(oldItemPosition).bytesOwnedIdentity, newList.get(newItemPosition).bytesOwnedIdentity);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    // we assume each time we receive a new list, we need to redraw every item
                    return false;
                }
            });
            this.ownedIdentities = ownedIdentities;
            result.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public OwnedIdentityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = layoutInflater.inflate(R.layout.item_view_owned_identity, parent, false);
            return new OwnedIdentityViewHolder(view, onClickListener);
        }

        @Override
        public void onBindViewHolder(@NonNull OwnedIdentityViewHolder holder, int position) {
            if (ownedIdentities == null || ownedIdentities.size() <= position || position < 0) {
                return;
            }
            OwnedIdentity ownedIdentity = ownedIdentities.get(position);
            holder.bytesOwnedIdentity = ownedIdentity.bytesOwnedIdentity;
            holder.initialView.setOwnedIdentity(ownedIdentity);
            if (ownedIdentity.shouldMuteNotifications()) {
                holder.notificationMutedImageView.setVisibility(View.VISIBLE);
            } else {
                holder.notificationMutedImageView.setVisibility(View.GONE);
            }

            if (ownedIdentity.customDisplayName != null) {
                holder.displayNameTextView.setText(ownedIdentity.customDisplayName);
                JsonIdentityDetails identityDetails = ownedIdentity.getIdentityDetails();
                holder.displayNameSecondLineTextView.setVisibility(View.VISIBLE);
                if (identityDetails != null) {
                    holder.displayNameSecondLineTextView.setText(identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()));
                } else {
                    holder.displayNameSecondLineTextView.setText(ownedIdentity.displayName);
                }
            } else {
                JsonIdentityDetails identityDetails = ownedIdentity.getIdentityDetails();
                if (identityDetails != null) {
                    holder.displayNameTextView.setText(identityDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));

                    String posComp = identityDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                    if (posComp != null) {
                        holder.displayNameSecondLineTextView.setVisibility(View.VISIBLE);
                        holder.displayNameSecondLineTextView.setText(posComp);
                    } else {
                        holder.displayNameSecondLineTextView.setVisibility(View.GONE);
                    }
                } else {
                    holder.displayNameTextView.setText(ownedIdentity.displayName);
                    holder.displayNameSecondLineTextView.setVisibility(View.GONE);
                    holder.displayNameSecondLineTextView.setText(null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return ownedIdentities == null ? 0 : ownedIdentities.size();
        }
    }

    public static class OwnedIdentityViewHolder extends RecyclerView.ViewHolder {
        final InitialView initialView;
        final TextView displayNameTextView;
        final TextView displayNameSecondLineTextView;
        final ImageView notificationMutedImageView;

        byte[] bytesOwnedIdentity;

        public OwnedIdentityViewHolder(@NonNull View itemView, @Nullable OnClickListener onClickListener) {
            super(itemView);
            initialView = itemView.findViewById(R.id.initial_view);
            displayNameTextView = itemView.findViewById(R.id.owned_identity_display_name_text_view);
            displayNameSecondLineTextView = itemView.findViewById(R.id.owned_identity_display_name_second_line_text_view);
            notificationMutedImageView = itemView.findViewById(R.id.notifications_muted_image_view);
            if (onClickListener != null) {
                itemView.setOnClickListener(v -> {
                    if (bytesOwnedIdentity != null) {
                        onClickListener.onClick(bytesOwnedIdentity);
                    }
                });
            }
        }
    }

    private static class OpenHiddenProfileDialog extends io.olvid.messenger.customClasses.OpenHiddenProfileDialog {
        @NonNull private final FragmentActivity activity;

        public OpenHiddenProfileDialog(@NonNull FragmentActivity activity) {
            super(activity);
            this.activity = activity;
        }

        @Override
        protected void onHiddenIdentityPasswordEntered(AlertDialog dialog, byte[] byteOwnedIdentity) {
            dialog.dismiss();
            DialogViewModel dialogViewModel = new ViewModelProvider(activity).get(DialogViewModel.class);
            if (dialogViewModel.onOwnedIdentitySelectedListener != null) {
                dialogViewModel.onOwnedIdentitySelectedListener.onOwnedIdentitySelected(byteOwnedIdentity);
            }
        }
    }


    public static class DialogViewModel extends ViewModel {
        int dialogTitleResourceId;
        OnOwnedIdentitySelectedListener onOwnedIdentitySelectedListener;
    }

    public interface OnClickListener {
        void onClick(@NonNull byte[] bytesOwnedIdentity);
    }

    public interface OnOwnedIdentitySelectedListener {
        void onOwnedIdentitySelected(@NonNull byte[] bytesOwnedIdentity);
        void onNewProfileCreationSelected();
    }
}
