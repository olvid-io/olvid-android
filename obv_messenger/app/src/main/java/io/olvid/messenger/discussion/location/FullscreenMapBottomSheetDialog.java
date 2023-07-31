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

package io.olvid.messenger.discussion.location;

import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.Objects;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.jsons.JsonLocation;

public class FullscreenMapBottomSheetDialog extends BottomSheetDialogFragment {
    private static final String DISCUSSION_ID = "discussionId";

    private long discussionId = -1;
    private final FullscreenMapDialogFragment parentFragment;
    private FragmentActivity activity;

    private FullscreenMapBottomSheetDialog(FullscreenMapDialogFragment parentFragment) {
        this.parentFragment = parentFragment;
    }

    public static FullscreenMapBottomSheetDialog newInstance(long discussionId, FullscreenMapDialogFragment parentFragment) {
        FullscreenMapBottomSheetDialog fragment = new FullscreenMapBottomSheetDialog(parentFragment);
        Bundle bundle = new Bundle();
        bundle.putLong(DISCUSSION_ID, discussionId);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // check that discussionId was correctly set
        if (this.getArguments() != null) {
            discussionId = this.getArguments().getLong(DISCUSSION_ID);
        }

        // make bottom sheet transparent
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme);

        // get current activity
        this.activity = requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // dismiss if required arguments are not set
        if (discussionId == -1) {
            dismiss();
            return null;
        }

        LiveData<List<Message>> sharingMessagesLiveData = AppDatabase.getInstance().messageDao().getCurrentlySharingLocationMessagesInDiscussionLiveData(discussionId);

        View view = inflater.inflate(R.layout.bottom_sheet_fragment_fullscreen_map, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.bottom_sheet_fullscreen_map_recycler_view);

        PersonListAdapter adapter = new PersonListAdapter(activity);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        sharingMessagesLiveData.observe(this, adapter);

        // set constraint layout max height to half screen size
        DisplayMetrics displayMetrics = requireContext().getResources().getDisplayMetrics();
        ConstraintLayout constraintLayout = view.findViewById(R.id.constraintLayout);
        constraintLayout.setMaxHeight(displayMetrics.heightPixels/2);

        return view;
    }

    private class PersonListAdapter extends RecyclerView.Adapter<PersonListAdapter.PersonListViewHolder> implements Observer<List<Message>> {
        private List<Message> currentlyShownMessages = null;
        private final LayoutInflater inflater;

        public PersonListAdapter(Context context) {
            inflater  = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public PersonListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PersonListViewHolder(inflater.inflate(R.layout.item_view_fullscreen_map_bottom_sheet_dialog, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PersonListViewHolder holder, int position) {
            holder.updateContent(currentlyShownMessages.get(position));
        }

        @Override
        public int getItemCount() {
            return (currentlyShownMessages == null) ? 0 : currentlyShownMessages.size();
        }

        @Override
        public void onChanged(List<Message> messages) {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return (currentlyShownMessages == null) ? 0 : currentlyShownMessages.size();
                }

                @Override
                public int getNewListSize() {
                    return (messages == null) ? 0 : messages.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return currentlyShownMessages.get(oldItemPosition).id == messages.get(newItemPosition).id;
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return Objects.equals(currentlyShownMessages.get(oldItemPosition).jsonLocation, messages.get(newItemPosition).jsonLocation);
                }
            });
            diffResult.dispatchUpdatesTo(this);
            currentlyShownMessages = messages;
        }

        private class PersonListViewHolder extends RecyclerView.ViewHolder {
            private final ConstraintLayout constraintLayout;
            private final InitialView initialView;
            private final TextView displayNameTextView;
            private final TextView lastUpdateTextView;
            private final ImageView openInThirdPartyAppImageView;

            public PersonListViewHolder(@NonNull View itemView) {
                super(itemView);
                constraintLayout = itemView.findViewById(R.id.constraint_layout);
                initialView = itemView.findViewById(R.id.initial_view);
                displayNameTextView = itemView.findViewById(R.id.display_name_text_view);
                lastUpdateTextView = itemView.findViewById(R.id.last_update_text_view);
                openInThirdPartyAppImageView = itemView.findViewById(R.id.open_third_party_app_image_view);
            }

            public void updateContent(Message message) {
                constraintLayout.setOnClickListener((view) -> {
                    if (parentFragment.mapView != null) {
                        parentFragment.mapView.centerOnMarker(message.id, true);
                        dismiss();
                    }
                });

                initialView.setFromCache(message.senderIdentifier);
                displayNameTextView.setText(AppSingleton.getContactCustomDisplayName(message.senderIdentifier));
                lastUpdateTextView.setText(getString(R.string.label_share_location_latest_update, StringUtils.getLongNiceDateString(App.getContext(), message.getJsonLocation().getTimestamp())));
                openInThirdPartyAppImageView.setOnClickListener((view) -> {
                    JsonLocation jsonLocation = message.getJsonLocation();
                    App.openLocationInMapApplication(getActivity(), jsonLocation.getTruncatedLatitudeString(), jsonLocation.getTruncatedLongitudeString(), message.contentBody, null);
                });
            }
        }
    }
}
