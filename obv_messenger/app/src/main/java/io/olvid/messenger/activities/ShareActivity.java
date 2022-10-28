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

package io.olvid.messenger.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LockScreenOrNotActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.ReplaceDiscussionDraftTask;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.fragments.FilteredDiscussionListFragment;
import io.olvid.messenger.fragments.dialog.OwnedIdentitySelectionDialogFragment;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel;


public class ShareActivity extends LockScreenOrNotActivity {
    private String sharedText;
    private List<Uri> sharedFiles;

    private InitialView currentIdentityInitialView;
    private TextView currentNameTextView;
    private TextView currentNameSecondLineTextView;
    private ImageView currentIdentityMutedImageView;
    private View separator;
    private OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter adapter;
    private PopupWindow popupWindow;


    @Override
    protected void notLockedOnCreate() {
            Intent intent = getIntent();
            if (intent == null || intent.getAction() == null) {
                intentFail();
                return;
            }

            switch (intent.getAction()) {
                case Intent.ACTION_SEND: {
                    Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (sharedUri != null) {
                        sharedFiles = filterUris(Collections.singletonList(sharedUri));
                        sharedText = "";
                    } else {
                        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                        if (sharedText == null) {
                            intentFail();
                            return;
                        }
                        sharedFiles = new ArrayList<>(0);
                    }
                    break;
                }
                case Intent.ACTION_SEND_MULTIPLE: {
                    List<Uri> extraStreamUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (extraStreamUris == null) {
                        intentFail();
                        return;
                    }
                    sharedFiles = filterUris(extraStreamUris);
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (sharedText == null) {
                        sharedText = "";
                    }
                    break;
                }
                default: {
                    intentFail();
                    return;
                }
            }

            // From this point, both sharedFiles amd sharedText are non null
            if (intent.hasExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)) {
                String shortcutId = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID);
                if (shortcutId != null && shortcutId.startsWith(DiscussionActivity.SHORTCUT_PREFIX)) {
                    long discussionId = Long.parseLong(shortcutId.substring(DiscussionActivity.SHORTCUT_PREFIX.length()));
                    proceed(discussionId);
                    return;
                }
            }

            getDelegate().setLocalNightMode(AppCompatDelegate.getDefaultNightMode());
            setContentView(R.layout.activity_share);

            currentIdentityInitialView = findViewById(R.id.current_identity_initial_view);
            currentNameTextView = findViewById(R.id.current_identity_name_text_view);
            currentNameSecondLineTextView = findViewById(R.id.current_identity_name_second_line_text_view);
            currentIdentityMutedImageView = findViewById(R.id.current_identity_muted_marker_image_view);
            separator = findViewById(R.id.separator);

            final EditText contactNameFilter = findViewById(R.id.discussion_filter);
            findViewById(R.id.button_cancel).setOnClickListener(v -> finish());
            TextView switchProfileButton = findViewById(R.id.button_switch_profile);
            switchProfileButton.setOnClickListener(v -> openSwitchProfilePopup());
            switchProfileButton.setOnLongClickListener(v -> {
                new OpenHiddenProfileDialog(this);
                return true;
            });

            LiveData<List<DiscussionDao.DiscussionAndGroupMembersNames>> unfilteredDiscussions = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
                bindOwnedIdentity(ownedIdentity);
                if (ownedIdentity == null) {
                    return null;
                } else {
                    return AppDatabase.getInstance().discussionDao().getAllNotLockedWithGroupMembersNamesOrderedByActivity(ownedIdentity.bytesOwnedIdentity);
                }
            });

            adapter = new OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter(getLayoutInflater(), bytesOwnedIdentity -> {
                if (popupWindow != null) {
                    popupWindow.dismiss();
                }
                AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, null);
            });
            Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenExceptOne(ownedIdentity == null ? null : ownedIdentity.bytesOwnedIdentity)).observe(this, adapter);


            FilteredDiscussionListFragment filteredDiscussionListFragment = new FilteredDiscussionListFragment();
            filteredDiscussionListFragment.setUseDialogBackground(true);
            filteredDiscussionListFragment.setShowPinned(true);
            filteredDiscussionListFragment.setUnfilteredDiscussions(unfilteredDiscussions);
            filteredDiscussionListFragment.setDiscussionFilterEditText(contactNameFilter);
            filteredDiscussionListFragment.setOnClickDelegate((View view, FilteredDiscussionListViewModel.SearchableDiscussion searchableDiscussion) -> App.runThread(() -> proceed(searchableDiscussion.discussionId)));

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.filtered_discussion_list_placeholder, filteredDiscussionListFragment);
            transaction.commit();
    }

    private void bindOwnedIdentity(OwnedIdentity ownedIdentity) {
        if (currentIdentityInitialView == null || currentNameTextView == null || currentNameSecondLineTextView == null || currentIdentityMutedImageView == null) {
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
        if (ownedIdentity.shouldMuteNotifications()) {
            currentIdentityMutedImageView.setVisibility(View.VISIBLE);
        } else {
            currentIdentityMutedImageView.setVisibility(View.GONE);
        }
    }

    private void openSwitchProfilePopup() {
        if (separator == null || adapter == null) {
            return;
        }
        @SuppressLint("InflateParams") View popupView = getLayoutInflater().inflate(R.layout.popup_switch_owned_identity, null);
        popupWindow = new PopupWindow(popupView, separator.getWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(12);
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.background_half_rounded_dialog));
        popupWindow.setOnDismissListener(() -> popupWindow = null);

        EmptyRecyclerView ownedIdentityListRecyclerView = popupView.findViewById(R.id.owned_identity_list_recycler_view);
        ownedIdentityListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        ownedIdentityListRecyclerView.setAdapter(adapter);
        ownedIdentityListRecyclerView.setEmptyView(popupView.findViewById(R.id.empty_view));

        popupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);
        popupWindow.showAsDropDown(separator);
    }

    private static class OpenHiddenProfileDialog extends io.olvid.messenger.customClasses.OpenHiddenProfileDialog {
        public OpenHiddenProfileDialog(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @Override
        protected void onHiddenIdentityPasswordEntered(AlertDialog dialog, byte[] byteOwnedIdentity) {
            dialog.dismiss();
            AppSingleton.getInstance().selectIdentity(byteOwnedIdentity, null);
        }
    }

    private List<Uri> filterUris(List<Uri> uris) {
        List<Uri> filteredList = new ArrayList<>();
        for (Uri uri : uris) {
            if (uri != null && "content".equals(uri.getScheme())) {
                filteredList.add(uri);
            }
        }
        return filteredList;
    }

    private void intentFail() {
        App.toast(R.string.toast_message_sharing_failed, Toast.LENGTH_SHORT);
        finish();
    }

    private void proceed(long discussionId) {
        App.runThread(new ReplaceDiscussionDraftTask(discussionId, sharedText, sharedFiles));
        App.runThread(() -> {
            Discussion discussion = AppDatabase.getInstance().discussionDao().getById(discussionId);
            if (discussion != null) {
                Intent intent = new Intent(App.getContext(), MainActivity.class);
                intent.setAction(MainActivity.FORWARD_ACTION);
                intent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
                intent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
                intent.putExtra(MainActivity.BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, discussion.bytesOwnedIdentity);
                startActivity(intent);
            }
            finish();
        });
    }

}
