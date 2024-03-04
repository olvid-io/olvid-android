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

package io.olvid.messenger.fragments.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;

import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.fragments.FilteredDiscussionListFragment;
import io.olvid.messenger.settings.SettingsActivity;


public class DiscussionSearchDialogFragment extends DialogFragment {
    EditText dialogContactNameFilter;

    public static DiscussionSearchDialogFragment newInstance() {
        return new DiscussionSearchDialogFragment();
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
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_fragment_discussion_search, container, false);
        dialogContactNameFilter = dialogView.findViewById(R.id.dialog_discussion_filter);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> dismiss());

        FilteredDiscussionListFragment filteredDiscussionListFragment = new FilteredDiscussionListFragment();
        filteredDiscussionListFragment.setBottomPadding(0);
        if (AppSingleton.getBytesCurrentIdentity() != null) {
            filteredDiscussionListFragment.setUnfilteredDiscussions(AppDatabase.getInstance().discussionDao().getAllWithGroupMembersNames(AppSingleton.getBytesCurrentIdentity()));
        }
        filteredDiscussionListFragment.setDiscussionFilterEditText(dialogContactNameFilter);
        filteredDiscussionListFragment.setOnClickDelegate((view, searchableDiscussion) -> {
            App.openDiscussionActivity(view.getContext(), searchableDiscussion.discussionId);
            dismiss();
        });

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.dialog_filtered_discussion_list_placeholder, filteredDiscussionListFragment);
        transaction.commit();

        dialogContactNameFilter.requestFocus();
        return dialogView;
    }
}
