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

package io.olvid.messenger.main;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.OwnedIdentityDao;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.onboarding.flow.OnboardingFlowActivity;
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsActivity;
import io.olvid.messenger.settings.SettingsActivity;

public class OwnIdentitySelectorPopupWindow {
    @NonNull private final AppCompatActivity activity;
    @NonNull private final View anchorView;

    @NonNull private final ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
    private int popupPixelWidth;
    private final int offsetPixelX;
    private final int offsetPixelY;

    @NonNull private final PopupWindow popupWindow;
    @NonNull private final Observer<OwnedIdentity> currentIdentityObserver;
    @NonNull private final OwnedIdentityListAdapter adapter;
    @NonNull private final LiveData<List<OwnedIdentityDao.OwnedIdentityAndUnreadMessageCount>> ownedIdentitiesAndMessageCount;
    @NonNull private final Observer<List<OwnedIdentityDao.OwnedIdentityAndUnreadMessageCount>> separatorObserver;

    public OwnIdentitySelectorPopupWindow(@NonNull AppCompatActivity activity, @NonNull View anchorView) {
        this.activity = activity;
        this.anchorView = anchorView;
        final DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        offsetPixelX = activity.getResources().getDimensionPixelSize(R.dimen.owned_identity_list_popup_y_offset);
        offsetPixelY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, metrics) + offsetPixelX;

        @SuppressLint("InflateParams")
        View popupView = LayoutInflater.from(activity).inflate(R.layout.popup_owned_identity_selector, null);
        this.popupPixelWidth = (int) Math.max(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 240, metrics), metrics.widthPixels * .8f);
        this.popupWindow = new PopupWindow(popupView, this.popupPixelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        this.popupWindow.setElevation(12);
        this.popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.background_rounded_dialog));

        final InitialView currentIdentityInitialView = popupView.findViewById(R.id.current_identity_initial_view);
        final TextView currentNameTextView = popupView.findViewById(R.id.current_identity_name_text_view);
        final TextView currentNameSecondLineTextView = popupView.findViewById(R.id.current_identity_name_second_line_text_view);
        final ImageView currentIdentityMutedImageView = popupView.findViewById(R.id.current_identity_muted_marker_image_view);
        currentIdentityInitialView.setOnClickListener(v -> popupWindow.dismiss());
        this.currentIdentityObserver = (OwnedIdentity ownedIdentity) -> {
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
            if (ownedIdentity.shouldMuteNotifications()) {
                currentIdentityMutedImageView.setVisibility(View.VISIBLE);
            } else {
                currentIdentityMutedImageView.setVisibility(View.GONE);
            }
        };

        RecyclerView ownedIdentityListRecyclerView = popupView.findViewById(R.id.owned_identity_list_recycler_view);
        adapter = new OwnedIdentityListAdapter(activity, bytesOwnedIdentity -> {
            popupWindow.dismiss();
            AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, null);
        });
        ownedIdentityListRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        ownedIdentityListRecyclerView.setAdapter(adapter);

        ownedIdentitiesAndMessageCount = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenWithUnreadMessageCount(ownedIdentity != null ? ownedIdentity.bytesOwnedIdentity : new byte[0]));

        final View separator = popupView.findViewById(R.id.separator);
        separatorObserver = (List<OwnedIdentityDao.OwnedIdentityAndUnreadMessageCount> ownedIdentityAndUnreadMessageCounts) -> {
            if (ownedIdentityAndUnreadMessageCounts == null || ownedIdentityAndUnreadMessageCounts.size() == 0) {
                separator.setVisibility(View.GONE);
            } else {
                separator.setVisibility(View.VISIBLE);
            }
        };

        final TextView manageButton = popupView.findViewById(R.id.button_manage);
        manageButton.setOnClickListener(v -> {
            activity.startActivity(new Intent(activity, OwnedIdentityDetailsActivity.class));
            popupWindow.dismiss();
        });

        final TextView addProfileButton = popupView.findViewById(R.id.button_add_profile);
        addProfileButton.setOnClickListener(v -> {
            popupWindow.dismiss();
            Intent onboardingIntent = new Intent(activity, OnboardingFlowActivity.class);
            onboardingIntent.putExtra(OnboardingFlowActivity.NEW_PROFILE_INTENT_EXTRA, true);
            activity.startActivity(onboardingIntent);
        });
        addProfileButton.setOnLongClickListener(v -> {
            popupWindow.dismiss();
            new OpenHiddenProfileDialog(activity);
            return true;
        });

        this.globalLayoutListener = () -> {
            popupPixelWidth = (int) Math.max(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 240, metrics), metrics.widthPixels * .8f);
            popupWindow.update(popupPixelWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        };
    }

    public void open() {
        // start all observers
        anchorView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
        AppSingleton.getCurrentIdentityLiveData().observe(activity, currentIdentityObserver);
        ownedIdentitiesAndMessageCount.observe(activity, adapter);
        ownedIdentitiesAndMessageCount.observe(activity, separatorObserver);

        popupWindow.setOnDismissListener(() -> {
            anchorView.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
            AppSingleton.getCurrentIdentityLiveData().removeObserver(currentIdentityObserver);
            ownedIdentitiesAndMessageCount.removeObserver(adapter);
            ownedIdentitiesAndMessageCount.removeObserver(separatorObserver);
        });
        popupWindow.setAnimationStyle(R.style.FadeInAndOutAnimation);
        popupWindow.showAsDropDown(anchorView,  -offsetPixelX, -offsetPixelY);
    }


    private static class OpenHiddenProfileDialog extends io.olvid.messenger.customClasses.OpenHiddenProfileDialog {
        public OpenHiddenProfileDialog(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @Override
        protected void onHiddenIdentityPasswordEntered(AlertDialog dialog, byte[] byteOwnedIdentity) {
            dialog.dismiss();
            AppSingleton.getInstance().selectIdentity(byteOwnedIdentity, null);
            if (!SettingsActivity.isHiddenProfileClosePolicyDefined()) {
                App.openAppDialogConfigureHiddenProfileClosePolicy();
            }
        }
    }

    private static class OwnedIdentityListAdapter extends RecyclerView.Adapter<OwnedIdentityViewHolder> implements Observer<List<OwnedIdentityDao.OwnedIdentityAndUnreadMessageCount>> {
        @NonNull private final Context context;
        @Nullable private final ClickListener clickListener;
        private List<OwnedIdentityDao.OwnedIdentityAndUnreadMessageCount> ownedIdentities;

        public OwnedIdentityListAdapter(@NonNull Context context, @Nullable ClickListener clickListener) {
            this.context = context;
            this.clickListener = clickListener;
        }

        @Override
        public void onChanged(List<OwnedIdentityDao.OwnedIdentityAndUnreadMessageCount> ownedIdentities) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                final List<OwnedIdentityDao.OwnedIdentityAndUnreadMessageCount> oldList = OwnedIdentityListAdapter.this.ownedIdentities;
                final List<OwnedIdentityDao.OwnedIdentityAndUnreadMessageCount> newList = ownedIdentities;

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
                    return Arrays.equals(oldList.get(oldItemPosition).ownedIdentity.bytesOwnedIdentity, newList.get(newItemPosition).ownedIdentity.bytesOwnedIdentity);
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
            View view = LayoutInflater.from(context).inflate(R.layout.item_view_owned_identity_with_unread_messages, parent, false);
            return new OwnedIdentityViewHolder(view, clickListener);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull OwnedIdentityViewHolder holder, int position) {
            if (ownedIdentities == null || ownedIdentities.size() <= position || position < 0) {
                return;
            }
            OwnedIdentityDao.OwnedIdentityAndUnreadMessageCount ownedIdentityAndUnreadMessageCount = ownedIdentities.get(position);
            holder.bytesOwnedIdentity = ownedIdentityAndUnreadMessageCount.ownedIdentity.bytesOwnedIdentity;
            if (ownedIdentityAndUnreadMessageCount.unreadMessageCount + ownedIdentityAndUnreadMessageCount.unreadInvitationCount + ownedIdentityAndUnreadMessageCount.unreadDiscussionCount == 0) {
                holder.unreadMessageLabel.setVisibility(View.GONE);
            } else {
                holder.unreadMessageLabel.setVisibility(View.VISIBLE);
                holder.unreadMessageLabel.setText(Long.toString(ownedIdentityAndUnreadMessageCount.unreadMessageCount + ownedIdentityAndUnreadMessageCount.unreadInvitationCount + ownedIdentityAndUnreadMessageCount.unreadDiscussionCount));
            }

            holder.initialView.setOwnedIdentity(ownedIdentityAndUnreadMessageCount.ownedIdentity);

            if (ownedIdentityAndUnreadMessageCount.ownedIdentity.shouldMuteNotifications()) {
                holder.notificationMutedImageView.setVisibility(View.VISIBLE);
            } else {
                holder.notificationMutedImageView.setVisibility(View.GONE);
            }

            if (ownedIdentityAndUnreadMessageCount.ownedIdentity.customDisplayName != null) {
                holder.displayNameTextView.setText(ownedIdentityAndUnreadMessageCount.ownedIdentity.customDisplayName);
                JsonIdentityDetails identityDetails = ownedIdentityAndUnreadMessageCount.ownedIdentity.getIdentityDetails();
                holder.displayNameSecondLineTextView.setVisibility(View.VISIBLE);
                if (identityDetails != null) {
                    holder.displayNameSecondLineTextView.setText(identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()));
                } else {
                    holder.displayNameSecondLineTextView.setText(ownedIdentityAndUnreadMessageCount.ownedIdentity.displayName);
                }
            } else {
                JsonIdentityDetails identityDetails = ownedIdentityAndUnreadMessageCount.ownedIdentity.getIdentityDetails();
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
                    holder.displayNameTextView.setText(ownedIdentityAndUnreadMessageCount.ownedIdentity.displayName);
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

    private static class OwnedIdentityViewHolder extends RecyclerView.ViewHolder {
        final InitialView initialView;
        final TextView displayNameTextView;
        final TextView displayNameSecondLineTextView;
        final TextView unreadMessageLabel;
        final ImageView notificationMutedImageView;

        byte[] bytesOwnedIdentity;

        public OwnedIdentityViewHolder(@NonNull View itemView, @Nullable ClickListener clickListener) {
            super(itemView);
            initialView = itemView.findViewById(R.id.initial_view);
            displayNameTextView = itemView.findViewById(R.id.owned_identity_display_name_text_view);
            displayNameSecondLineTextView = itemView.findViewById(R.id.owned_identity_display_name_second_line_text_view);
            unreadMessageLabel = itemView.findViewById(R.id.owned_identity_unread_messages_label);
            notificationMutedImageView = itemView.findViewById(R.id.notifications_muted_image_view);
            if (clickListener != null) {
                itemView.setOnClickListener(v -> clickListener.onClick(bytesOwnedIdentity));
            }
        }
    }

    private interface ClickListener {
        void onClick(byte[] bytesOwnedIdentity);
    }
}
