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

package io.olvid.messenger.discussion;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import io.olvid.messenger.activities.OwnedIdentityDetailsActivity;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.OwnedIdentityDao;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.settings.SettingsActivity;

public class DiscussionOwnedIdentityPopupWindow {
    @NonNull private final FragmentActivity activity;
    @NonNull private final View anchorView;

    @NonNull private final ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
    private int popupPixelWidth;
    private int offsetPixelX = 0;
    private int offsetPixelY = 0;

    @NonNull private final PopupWindow popupWindow;
    @NonNull private final Observer<OwnedIdentity> currentIdentityObserver;
    @NonNull private final OwnedIdentityListAdapter adapter;
    @Nullable private final LiveData<List<OwnedIdentityDao.OwnedIdentityAndDiscussionId>> otherOwnedIdentitiesAndDiscussionId;
    @NonNull private final Observer<List<OwnedIdentityDao.OwnedIdentityAndDiscussionId>> separatorAndHeightObserver;


    public DiscussionOwnedIdentityPopupWindow(@NonNull FragmentActivity activity, @NonNull View anchorView, @Nullable byte[] bytesContactIdentity, @Nullable byte[] bytesGroupOwnerAndUid) {
        this.activity = activity;
        this.anchorView = anchorView;

        @SuppressLint("InflateParams")
        View popupView = LayoutInflater.from(activity).inflate(R.layout.popup_discussion_owned_identity, null);
        this.popupWindow = new PopupWindow(popupView, this.popupPixelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        this.popupWindow.setElevation(12);
        this.popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.background_popup_discussion_owned_identity));

        final InitialView currentIdentityInitialView = popupView.findViewById(R.id.current_identity_initial_view);
        final TextView currentNameTextView = popupView.findViewById(R.id.current_identity_name_text_view);
        final TextView currentNameSecondLineTextView = popupView.findViewById(R.id.current_identity_name_second_line_text_view);
        final ImageView currentIdentityMutedImageView = popupView.findViewById(R.id.current_identity_muted_marker_image_view);
        this.currentIdentityObserver = (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                currentIdentityInitialView.setKeycloakCertified(false);
                currentIdentityInitialView.setInactive(false);
                currentIdentityInitialView.setInitial(new byte[0], " ");
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
            currentIdentityInitialView.setInactive(!ownedIdentity.active);
            currentIdentityInitialView.setKeycloakCertified(ownedIdentity.keycloakManaged);
            if (ownedIdentity.photoUrl != null) {
                currentIdentityInitialView.setPhotoUrl(ownedIdentity.bytesOwnedIdentity, ownedIdentity.photoUrl);
            } else {
                currentIdentityInitialView.setInitial(ownedIdentity.bytesOwnedIdentity, App.getInitial(ownedIdentity.getCustomDisplayName()));
            }
            if (ownedIdentity.shouldMuteNotifications()) {
                currentIdentityMutedImageView.setVisibility(View.VISIBLE);
            } else {
                currentIdentityMutedImageView.setVisibility(View.GONE);
            }
        };

        final TextView manageButton = popupView.findViewById(R.id.button_manage);
        manageButton.setOnClickListener(v -> {
            activity.startActivity(new Intent(activity, OwnedIdentityDetailsActivity.class));
            popupWindow.dismiss();
        });

        RecyclerView ownedIdentityListRecyclerView = popupView.findViewById(R.id.owned_identity_list_recycler_view);
        adapter = new OwnedIdentityListAdapter(activity, (byte[] bytesOwnedIdentity, long discussionId) -> {
            popupWindow.dismiss();
            AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, (OwnedIdentity ownedIdentity) -> App.openDiscussionActivity(activity, discussionId));
        });
        ownedIdentityListRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        ownedIdentityListRecyclerView.setAdapter(adapter);


        final View separator = popupView.findViewById(R.id.separator);
        separatorAndHeightObserver = (List<OwnedIdentityDao.OwnedIdentityAndDiscussionId> ownedIdentityAndDiscussionIds) -> {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            int windowHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 66, metrics);
            if (ownedIdentityAndDiscussionIds == null || ownedIdentityAndDiscussionIds.size() == 0) {
                separator.setVisibility(View.GONE);
            } else {
                separator.setVisibility(View.VISIBLE);
                windowHeight += (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 9 + 44*Math.min(3, ownedIdentityAndDiscussionIds.size()), metrics);
            }
            if (windowHeight != offsetPixelY) {
                offsetPixelY = windowHeight;
                readjustWindowPosition();
            }
        };

        if (bytesContactIdentity != null) {
            otherOwnedIdentitiesAndDiscussionId = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
                if (ownedIdentity == null) {
                    return null;
                }
                return AppDatabase.getInstance().ownedIdentityDao().getOtherNonHiddenOwnedIdentitiesForContactDiscussion(ownedIdentity.bytesOwnedIdentity, bytesContactIdentity);
            });
        } else if (bytesGroupOwnerAndUid != null) {
            otherOwnedIdentitiesAndDiscussionId = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
                if (ownedIdentity == null) {
                    return null;
                }
                return AppDatabase.getInstance().ownedIdentityDao().getOtherNonHiddenOwnedIdentitiesForGroupDiscussion(ownedIdentity.bytesOwnedIdentity, bytesGroupOwnerAndUid);
            });
        } else {
            otherOwnedIdentitiesAndDiscussionId = null;
        }

        this.globalLayoutListener = this::readjustWindowSize;
    }

    private void readjustWindowSize() {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        Rect r = new Rect();
        anchorView.getGlobalVisibleRect(r);
        offsetPixelX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 42, metrics);
        popupPixelWidth = (int) Math.min(metrics.widthPixels - r.left + offsetPixelX - offsetPixelX/5f, metrics.widthPixels * .8f);
        popupWindow.update(r.left - offsetPixelX, r.top - offsetPixelY, popupPixelWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void readjustWindowPosition() {
        Rect r = new Rect();
        anchorView.getGlobalVisibleRect(r);
        popupWindow.update(r.left - offsetPixelX, r.top - offsetPixelY, -1, -1);
    }

    public void open() {
        // start all observers
        anchorView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
        AppSingleton.getCurrentIdentityLiveData().observe(activity, currentIdentityObserver);
        if (otherOwnedIdentitiesAndDiscussionId != null) {
            otherOwnedIdentitiesAndDiscussionId.observe(activity, adapter);
            otherOwnedIdentitiesAndDiscussionId.observe(activity, separatorAndHeightObserver);
        }

        popupWindow.setOnDismissListener(() -> {
            anchorView.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
            AppSingleton.getCurrentIdentityLiveData().removeObserver(currentIdentityObserver);
            if (otherOwnedIdentitiesAndDiscussionId != null) {
                otherOwnedIdentitiesAndDiscussionId.removeObserver(adapter);
                otherOwnedIdentitiesAndDiscussionId.removeObserver(separatorAndHeightObserver);
            }
        });
        popupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);
        readjustWindowSize();
        popupWindow.showAsDropDown(anchorView, -offsetPixelX, -offsetPixelY, Gravity.TOP | Gravity.START);
    }



    private static class OwnedIdentityListAdapter extends RecyclerView.Adapter<OwnedIdentityViewHolder> implements Observer<List<OwnedIdentityDao.OwnedIdentityAndDiscussionId>> {
        @NonNull private final Context context;
        @Nullable private final ClickListener clickListener;
        private List<OwnedIdentityDao.OwnedIdentityAndDiscussionId> ownedIdentities;

        public OwnedIdentityListAdapter(@NonNull Context context, @Nullable ClickListener clickListener) {
            this.context = context;
            this.clickListener = clickListener;
        }

        @Override
        public void onChanged(List<OwnedIdentityDao.OwnedIdentityAndDiscussionId> ownedIdentities) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                final List<OwnedIdentityDao.OwnedIdentityAndDiscussionId> oldList = OwnedIdentityListAdapter.this.ownedIdentities;
                final List<OwnedIdentityDao.OwnedIdentityAndDiscussionId> newList = ownedIdentities;

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
            View view = LayoutInflater.from(context).inflate(R.layout.item_view_owned_identity, parent, false);
            return new OwnedIdentityViewHolder(view, clickListener);
        }

        @Override
        public void onBindViewHolder(@NonNull OwnedIdentityViewHolder holder, int position) {
            if (ownedIdentities == null || ownedIdentities.size() <= position || position < 0) {
                return;
            }
            OwnedIdentityDao.OwnedIdentityAndDiscussionId ownedIdentityAndDiscussionId = ownedIdentities.get(position);
            holder.bytesOwnedIdentity = ownedIdentityAndDiscussionId.ownedIdentity.bytesOwnedIdentity;
            holder.discussionId = ownedIdentityAndDiscussionId.discussionId;

            holder.initialView.setInactive(!ownedIdentityAndDiscussionId.ownedIdentity.active);
            holder.initialView.setKeycloakCertified(ownedIdentityAndDiscussionId.ownedIdentity.keycloakManaged);
            if (ownedIdentityAndDiscussionId.ownedIdentity.photoUrl == null) {
                holder.initialView.setInitial(ownedIdentityAndDiscussionId.ownedIdentity.bytesOwnedIdentity, App.getInitial(ownedIdentityAndDiscussionId.ownedIdentity.getCustomDisplayName()));
            } else {
                holder.initialView.setPhotoUrl(ownedIdentityAndDiscussionId.ownedIdentity.bytesOwnedIdentity, ownedIdentityAndDiscussionId.ownedIdentity.photoUrl);
            }

            if (ownedIdentityAndDiscussionId.ownedIdentity.shouldMuteNotifications()) {
                holder.notificationMutedImageView.setVisibility(View.VISIBLE);
            } else {
                holder.notificationMutedImageView.setVisibility(View.GONE);
            }

            if (ownedIdentityAndDiscussionId.ownedIdentity.customDisplayName != null) {
                holder.displayNameTextView.setText(ownedIdentityAndDiscussionId.ownedIdentity.customDisplayName);
                JsonIdentityDetails identityDetails = ownedIdentityAndDiscussionId.ownedIdentity.getIdentityDetails();
                holder.displayNameSecondLineTextView.setVisibility(View.VISIBLE);
                if (identityDetails != null) {
                    holder.displayNameSecondLineTextView.setText(identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()));
                } else {
                    holder.displayNameSecondLineTextView.setText(ownedIdentityAndDiscussionId.ownedIdentity.displayName);
                }
            } else {
                JsonIdentityDetails identityDetails = ownedIdentityAndDiscussionId.ownedIdentity.getIdentityDetails();
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
                    holder.displayNameTextView.setText(ownedIdentityAndDiscussionId.ownedIdentity.displayName);
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
        final ImageView notificationMutedImageView;

        byte[] bytesOwnedIdentity;
        long discussionId;

        public OwnedIdentityViewHolder(@NonNull View itemView, @Nullable ClickListener clickListener) {
            super(itemView);
            initialView = itemView.findViewById(R.id.initial_view);
            displayNameTextView = itemView.findViewById(R.id.owned_identity_display_name_text_view);
            displayNameSecondLineTextView = itemView.findViewById(R.id.owned_identity_display_name_second_line_text_view);
            notificationMutedImageView = itemView.findViewById(R.id.notifications_muted_image_view);
            if (clickListener != null) {
                itemView.setOnClickListener(v -> clickListener.onClick(bytesOwnedIdentity, discussionId));
            }
        }
    }

    private interface ClickListener {
        void onClick(byte[] bytesOwnedIdentity, long discussionId);
    }
}
