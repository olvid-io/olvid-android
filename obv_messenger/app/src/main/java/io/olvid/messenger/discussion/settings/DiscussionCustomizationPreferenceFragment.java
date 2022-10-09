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

package io.olvid.messenger.discussion.settings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.ImageViewPreference;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.tasks.RemoveDiscussionBackgroundImageTask;
import io.olvid.messenger.databases.tasks.SetDiscussionBackgroundImageTask;
import io.olvid.messenger.fragments.dialog.ColorPickerDialogFragment;
import io.olvid.messenger.owneddetails.SelectDetailsPhotoViewModel;


public class DiscussionCustomizationPreferenceFragment extends PreferenceFragmentCompat implements DiscussionSettingsViewModel.SettingsChangedListener {
    private FragmentActivity activity;
    private DiscussionSettingsViewModel discussionSettingsViewModel = null;
    private DiscussionSettingsDataStore discussionSettingsDataStore;

    ImageViewPreference colorPickerPreference;
    ImageViewPreference backgroundImagePreference;

    public static final int REQUEST_CODE_BACKGROUND_IMAGE = 18;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.discussion_fragment_preferences_customization, rootKey);
        activity = requireActivity();
        discussionSettingsViewModel = new ViewModelProvider(activity).get(DiscussionSettingsViewModel.class);
        discussionSettingsDataStore = discussionSettingsViewModel.getDiscussionSettingsDataStore();
        getPreferenceManager().setPreferenceDataStore(discussionSettingsDataStore);

        PreferenceScreen screen = getPreferenceScreen();

        colorPickerPreference = screen.findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_COLOR);
        if (colorPickerPreference != null) {
            colorPickerPreference.setOnPreferenceClickListener(preference -> {
                if (discussionSettingsViewModel != null && discussionSettingsViewModel.getDiscussionId() != null) {
                    ColorPickerDialogFragment colorPickerDialogFragment = ColorPickerDialogFragment.newInstance(discussionSettingsViewModel.getDiscussionId());
                    colorPickerDialogFragment.show(getChildFragmentManager(), "dialog");
                    return true;
                }
                return false;
            });
        }

        backgroundImagePreference = findPreference(DiscussionSettingsActivity.PREF_KEY_DISCUSSION_BACKGROUND_IMAGE);
        if (backgroundImagePreference != null) {
            backgroundImagePreference.setOnPreferenceClickListener(preference -> {
                if (discussionSettingsViewModel != null) {
                    DiscussionCustomization discussionCustomization = discussionSettingsViewModel.getDiscussionCustomization().getValue();
                    if (discussionCustomization != null && discussionCustomization.backgroundImageUrl != null) {
                        App.runThread(new RemoveDiscussionBackgroundImageTask(discussionCustomization.discussionId));
                    } else {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                                .setType("image/*")
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                        App.startActivityForResult(this, intent, REQUEST_CODE_BACKGROUND_IMAGE);
                    }
                    return true;
                }
                return false;
            });
        }

        discussionSettingsViewModel.addSettingsChangedListener(this);
    }

    @Override
    public void onSettingsChanged(DiscussionCustomization discussionCustomization) {
        if (colorPickerPreference != null) {
            if (discussionCustomization != null) {
                DiscussionCustomization.ColorJson colorJson = discussionCustomization.getColorJson();
                colorPickerPreference.setColor(colorJson);
            }
        }
        if (backgroundImagePreference != null) {
            if (discussionCustomization == null || discussionCustomization.backgroundImageUrl == null) {
                backgroundImagePreference.setImage(null);
                backgroundImagePreference.setSummary(R.string.pref_discussion_background_image_click_to_choose_summary);
            } else {
                Bitmap bitmap = BitmapFactory.decodeFile(App.absolutePathFromRelative(discussionCustomization.backgroundImageUrl));
                if (bitmap.getByteCount() < SelectDetailsPhotoViewModel.MAX_BITMAP_SIZE) {
                    backgroundImagePreference.setImage(App.absolutePathFromRelative(discussionCustomization.backgroundImageUrl));
                }
                backgroundImagePreference.setSummary(R.string.pref_discussion_background_image_click_to_remove_summary);
            }
        }
    }

    @Override
    public void onLockedOrGroupAdminChanged(boolean locked, boolean nonAdminGroup) {
        // nothing to do here...
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (data == null) {
            return;
        }
        if (requestCode == REQUEST_CODE_BACKGROUND_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                Long discussionId = discussionSettingsViewModel.getDiscussionId();
                Uri backgroundImageUrl = data.getData();
                if (discussionId != null && StringUtils.validateUri(backgroundImageUrl)) {
                    App.runThread(new SetDiscussionBackgroundImageTask(backgroundImageUrl, discussionId));
                }
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (discussionSettingsViewModel != null) {
            discussionSettingsViewModel.removeSettingsChangedListener(this);
        }
    }
}
