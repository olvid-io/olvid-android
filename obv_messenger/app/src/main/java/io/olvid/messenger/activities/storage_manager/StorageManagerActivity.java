/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.activities.storage_manager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LockScreenOrNotActivity;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask;
import io.olvid.messenger.databases.tasks.SaveMultipleAttachmentsTask;
import io.olvid.messenger.fragments.dialog.OwnedIdentitySelectionDialogFragment;
import io.olvid.messenger.settings.SettingsActivity;

public class StorageManagerActivity extends LockScreenOrNotActivity {
    private StorageManagerViewModel viewModel;
    private AudioAttachmentServiceBinding audioAttachmentServiceBinding;

    private InitialView currentIdentityInitialView;
    private TextView currentNameTextView;
    private TextView currentNameSecondLineTextView;
    private ImageView currentIdentityMutedImageView;

    private View anchor;
    private OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter adapter;
    private PopupWindow popupWindow;

    private TextView totalUsageTextView;
    private LinearLayout usageBarLinearLayout;
    private View sizePhotosView;
    private View sizeVideosView;
    private View sizeAudioView;
    private View sizeOtherView;

    View[] tabViews;
    ViewPager2 viewPager;
    TabViewPagerAdapter tabsPagerAdapter;

    private ActionMode.Callback actionModeCallback;
    private ActionMode actionMode;

    private final ActivityResultLauncher<Intent> saveSelectedAttachmentsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult activityResult) -> {
                if (activityResult == null || activityResult.getData() == null || activityResult.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                Uri folderUri = activityResult.getData().getData();
                if (StringUtils.validateUri(folderUri) && viewModel != null && !viewModel.selectedFyles.isEmpty()) {
                    ArrayList<FyleMessageJoinWithStatusDao.FyleAndStatus> selectedAttachments = new ArrayList<>(viewModel.selectedFyles);
                    viewModel.clearSelectedFyles();
                    App.runThread(new SaveMultipleAttachmentsTask(this, folderUri, selectedAttachments));
                }
            });


    @Override
    protected void notLockedOnCreate() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.olvid_gradient_dark));
        setContentView(R.layout.activity_storage_manager);

        viewModel = new ViewModelProvider(this).get(StorageManagerViewModel.class);
        try {
            audioAttachmentServiceBinding = new AudioAttachmentServiceBinding(this);
        } catch (Exception e) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        ImageView backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> onBackPressed());

        //////////////
        // current identity
        //////////////

        currentIdentityInitialView = findViewById(R.id.current_identity_initial_view);
        currentNameTextView = findViewById(R.id.current_identity_name_text_view);
        currentNameSecondLineTextView = findViewById(R.id.current_identity_name_second_line_text_view);
        currentIdentityMutedImageView = findViewById(R.id.current_identity_muted_marker_image_view);
        anchor = toolbar;

        AppSingleton.getCurrentIdentityLiveData().observe(this, this::bindOwnedIdentity);

        TextView switchProfileButton = findViewById(R.id.button_switch_profile);
        switchProfileButton.setOnClickListener(v -> openSwitchProfilePopup());
        switchProfileButton.setOnLongClickListener(v -> {
            new OpenHiddenProfileDialog(this);
            return true;
        });

        adapter = new OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter(getLayoutInflater(), bytesOwnedIdentity -> {
            if (popupWindow != null) {
                popupWindow.dismiss();
            }
            AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, null);
        });
        Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenExceptOne(ownedIdentity == null ? null : ownedIdentity.bytesOwnedIdentity)).observe(this, adapter);

        //////////////
        // storage usage bar
        //////////////

        totalUsageTextView = findViewById(R.id.summary_usage_text_view);
        usageBarLinearLayout = findViewById(R.id.summary_usage_bar);
        usageBarLinearLayout.setClipToOutline(true);
        sizePhotosView = findViewById(R.id.size_photos);
        sizeVideosView = findViewById(R.id.size_videos);
        sizeAudioView = findViewById(R.id.size_audio);
        sizeOtherView = findViewById(R.id.size_other);

        LiveData<Long> totalUsageLiveData = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return new MutableLiveData<>(0L);
            }
            return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getTotalUsage(ownedIdentity.bytesOwnedIdentity);
        });
        LiveData<Long> photosUsageLiveData = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return new MutableLiveData<>(0L);
            }
            return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getMimeUsage(ownedIdentity.bytesOwnedIdentity, "image/%");
        });
        LiveData<Long> videosUsageLiveData = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return new MutableLiveData<>(0L);
            }
            return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getMimeUsage(ownedIdentity.bytesOwnedIdentity, "video/%");
        });
        LiveData<Long> audioUsageLiveData = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
            if (ownedIdentity == null) {
                return new MutableLiveData<>(0L);
            }
            return AppDatabase.getInstance().fyleMessageJoinWithStatusDao().getMimeUsage(ownedIdentity.bytesOwnedIdentity, "audio/%");
        });

        StorageUsageLiveData storageUsageLiveData = new StorageUsageLiveData(totalUsageLiveData, photosUsageLiveData, videosUsageLiveData, audioUsageLiveData);
        storageUsageLiveData.observe(this, this::bindUsage);

        //////////////
        // view poger
        //////////////

        tabViews = new View[4];
        tabViews[0] = findViewById(R.id.tab_images_button);
        tabViews[1] = findViewById(R.id.tab_files_button);
        tabViews[2] = findViewById(R.id.tab_audio_button);
        tabViews[3] = findViewById(R.id.tab_all_button);

        for (View view: tabViews) {
            if (view != null) {
                view.setOnClickListener(this::tabClicked);
            }
        }

        StoragePageChangeListener pageChangeListener = new StoragePageChangeListener(tabViews);

        tabsPagerAdapter = new TabViewPagerAdapter(this, audioAttachmentServiceBinding);

        viewPager = findViewById(R.id.view_pager_container);
        viewPager.setAdapter(tabsPagerAdapter);
        viewPager.registerOnPageChangeCallback(pageChangeListener);
        viewPager.setOffscreenPageLimit(3);

        //////////////
        // action mode
        //////////////

        actionModeCallback = new ActionMode.Callback() {
            private MenuInflater inflater;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                inflater = mode.getMenuInflater();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                menu.clear();
                inflater.inflate(R.menu.action_menu_storate_manager, menu);
                return true;
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == R.id.action_delete_attachments) {
                    Integer count = viewModel.selectedCountLiveData.getValue();
                    if (count == null || count == 0) {
                        return true;
                    }
                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(StorageManagerActivity.this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_confirm_deletion)
                            .setMessage(getResources().getQuantityString(R.plurals.dialog_message_delete_attachments, count, count))
                            .setPositiveButton(R.string.button_label_ok, (dialog, which) -> App.runThread(() -> {
                                List<FyleMessageJoinWithStatusDao.FyleAndStatus> fylesToDelete = new ArrayList<>(viewModel.selectedFyles);
                                viewModel.clearSelectedFyles();
                                for (FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus : fylesToDelete) {
                                    new DeleteAttachmentTask(fyleAndStatus).run();
                                }
                            }))
                            .setNegativeButton(R.string.button_label_cancel, null);
                    builder.create().show();
                } else if (item.getItemId() == R.id.action_save_attachments) {
                    Integer count = viewModel.selectedCountLiveData.getValue();
                    if (count == null || count == 0) {
                        return true;
                    }
                    final AlertDialog.Builder builder = new SecureAlertDialogBuilder(StorageManagerActivity.this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_save_selected_attachments)
                            .setMessage(getResources().getQuantityString(R.plurals.dialog_message_save_selected_attachments, count, count))
                            .setPositiveButton(R.string.button_label_ok, (DialogInterface dialog, int which) -> {
                                App.prepareForStartActivityForResult(StorageManagerActivity.this);
                                saveSelectedAttachmentsLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE));
                            })
                            .setNegativeButton(R.string.button_label_cancel, null);
                    builder.create().show();
                } else if (item.getItemId() == R.id.action_select_all) {
                    AttachmentListAdapter adapter = tabsPagerAdapter.getAdapter(viewPager.getCurrentItem());
                    if (adapter != null && adapter.fyleAndOrigins != null) {
                        viewModel.selectAllFyles(adapter.fyleAndOrigins);
                        adapter.notifyDataSetChanged();
                    }
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                viewModel.clearSelectedFyles();
                actionMode = null;
            }
        };


        viewModel.getSelectedCountLiveData().observe(this, (Integer selectedCount) -> {
            if (selectedCount != null && selectedCount > 0) {
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback);
                }
                if (actionMode != null) {
                    actionMode.setTitle(getResources().getQuantityString(R.plurals.action_mode_title_storage, selectedCount, selectedCount));
                }
            } else {
                if (actionMode != null) {
                    actionMode.finish();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_storage_manager, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (anchor != null) {
            PopupMenu popupMenu = new PopupMenu(this, anchor, Gravity.TOP | Gravity.END);
            popupMenu.inflate(R.menu.popup_storage_sort_order);
            StorageManagerViewModel.SortOrder currentSortOrder = viewModel.getCurrentSortOrder();

            if (currentSortOrder.sortKey == StorageManagerViewModel.SortKey.SIZE && !currentSortOrder.ascending) {
                MenuItem menuItem = popupMenu.getMenu().findItem(R.id.popup_action_sort_size);
                if (menuItem != null) {
                    menuItem.setTitle(R.string.menu_action_sort_size_alt);
                }
            } else if (currentSortOrder.sortKey == StorageManagerViewModel.SortKey.DATE && !currentSortOrder.ascending) {
                MenuItem menuItem = popupMenu.getMenu().findItem(R.id.popup_action_sort_date);
                if (menuItem != null) {
                    menuItem.setTitle(R.string.menu_action_sort_date_alt);
                }
            } else if (currentSortOrder.sortKey == StorageManagerViewModel.SortKey.NAME && currentSortOrder.ascending) {
                MenuItem menuItem = popupMenu.getMenu().findItem(R.id.popup_action_sort_name);
                if (menuItem != null) {
                    menuItem.setTitle(R.string.menu_action_sort_name_alt);
                }
            }
            popupMenu.setOnMenuItemClickListener((MenuItem popupItem) -> {
                int id = popupItem.getItemId();
                if (id == R.id.popup_action_sort_size) {
                    viewModel.setSortOrder(new StorageManagerViewModel.SortOrder(StorageManagerViewModel.SortKey.SIZE, currentSortOrder.sortKey == StorageManagerViewModel.SortKey.SIZE && !currentSortOrder.ascending));
                } else if (id == R.id.popup_action_sort_date) {
                    viewModel.setSortOrder(new StorageManagerViewModel.SortOrder(StorageManagerViewModel.SortKey.DATE, currentSortOrder.sortKey == StorageManagerViewModel.SortKey.DATE && !currentSortOrder.ascending));
                } else if (id == R.id.popup_action_sort_name) {
                    viewModel.setSortOrder(new StorageManagerViewModel.SortOrder(StorageManagerViewModel.SortKey.NAME, !(currentSortOrder.sortKey == StorageManagerViewModel.SortKey.NAME && currentSortOrder.ascending)));
                }
                return true;
            });
            popupMenu.show();
        }
        return true;
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
        if (anchor == null || adapter == null) {
            return;
        }
        int eightDp = (int) (getResources().getDisplayMetrics().density * 8);
        @SuppressLint("InflateParams") View popupView = getLayoutInflater().inflate(R.layout.popup_switch_owned_identity, null);
        popupWindow = new PopupWindow(popupView, anchor.getWidth() - 10 * eightDp, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(12);
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.background_half_rounded_dialog));
        popupWindow.setOnDismissListener(() -> popupWindow = null);

        EmptyRecyclerView ownedIdentityListRecyclerView = popupView.findViewById(R.id.owned_identity_list_recycler_view);
        ownedIdentityListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        ownedIdentityListRecyclerView.setAdapter(adapter);
        ownedIdentityListRecyclerView.setEmptyView(popupView.findViewById(R.id.empty_view));

        popupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);
        popupWindow.showAsDropDown(anchor, 5 * eightDp, 0);
    }

    private void bindUsage(StorageUsageLiveData.StorageUsage storageUsage) {
        if (totalUsageTextView == null || sizePhotosView == null || sizeVideosView == null || sizeAudioView == null || sizeOtherView == null || usageBarLinearLayout == null) {
            return;
        }

        if (storageUsage == null) {
            totalUsageTextView.setText("-");
            ((LinearLayout.LayoutParams) sizePhotosView.getLayoutParams()).weight = 0;
            ((LinearLayout.LayoutParams) sizeVideosView.getLayoutParams()).weight = 0;
            ((LinearLayout.LayoutParams) sizeAudioView.getLayoutParams()).weight = 0;
            ((LinearLayout.LayoutParams) sizeOtherView.getLayoutParams()).weight = 0;
        } else {
            totalUsageTextView.setText(Formatter.formatShortFileSize(this, storageUsage.total));
            ((LinearLayout.LayoutParams) sizePhotosView.getLayoutParams()).weight = (float) storageUsage.photos / storageUsage.total;
            ((LinearLayout.LayoutParams) sizeVideosView.getLayoutParams()).weight = (float) storageUsage.videos / storageUsage.total;
            ((LinearLayout.LayoutParams) sizeAudioView.getLayoutParams()).weight = (float) storageUsage.audio / storageUsage.total;
            ((LinearLayout.LayoutParams) sizeOtherView.getLayoutParams()).weight = (float) storageUsage.other / storageUsage.total;
        }

        usageBarLinearLayout.requestLayout();
    }


    private void tabClicked(View tabView) {
        int id = tabView.getId();
        if (id == R.id.tab_images_button) {
            viewPager.setCurrentItem(0);
        } else if (id == R.id.tab_files_button) {
            viewPager.setCurrentItem(1);
        } else if (id == R.id.tab_audio_button) {
            viewPager.setCurrentItem(2);
        } else if (id == R.id.tab_all_button) {
            viewPager.setCurrentItem(3);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioAttachmentServiceBinding != null) {
            audioAttachmentServiceBinding.release();
        }
    }

    private class StoragePageChangeListener extends ViewPager2.OnPageChangeCallback {
        private final View[] buttonViews;
        private final int inactiveColor;
        private final int activeColor;

        public StoragePageChangeListener(View[] buttonViews) {
            this.buttonViews = buttonViews;
            this.inactiveColor = ContextCompat.getColor(StorageManagerActivity.this, R.color.greyTint);
            this.activeColor = ContextCompat.getColor(StorageManagerActivity.this, R.color.olvid_gradient_light);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            for (int i = 0; i< buttonViews.length; i++) {
                if (i==position) {
                    int color = 0xff000000;
                    color |= (int) (positionOffset*(inactiveColor&0xff) + (1-positionOffset)*(activeColor&0xff)) & 0xff;
                    color |= (int) (positionOffset*(inactiveColor&0xff00) + (1-positionOffset)*(activeColor&0xff00)) & 0xff00;
                    color |= (int) (positionOffset*(inactiveColor&0xff0000) + (1-positionOffset)*(activeColor&0xff0000)) & 0xff0000;
                    if (buttonViews[i] instanceof ImageView) {
                        ((ImageView) buttonViews[i]).setColorFilter(color);
                    } else if (buttonViews[i] instanceof TextView) {
                        ((TextView) buttonViews[i]).setTextColor(color);
                    }
                } else if ( i == position + 1) {
                    int color = 0xff000000;
                    color |= (int) (positionOffset*(activeColor&0xff) + (1-positionOffset)*(inactiveColor&0xff)) & 0xff;
                    color |= (int) (positionOffset*(activeColor&0xff00) + (1-positionOffset)*(inactiveColor&0xff00)) & 0xff00;
                    color |= (int) (positionOffset*(activeColor&0xff0000) + (1-positionOffset)*(inactiveColor&0xff0000)) & 0xff0000;
                    if (buttonViews[i] instanceof ImageView) {
                        ((ImageView) buttonViews[i]).setColorFilter(color);
                    } else if (buttonViews[i] instanceof TextView) {
                        ((TextView) buttonViews[i]).setTextColor(color);
                    }
                } else {
                    if (buttonViews[i] instanceof ImageView) {
                        ((ImageView) buttonViews[i]).setColorFilter(inactiveColor);
                    } else if (buttonViews[i] instanceof TextView) {
                        ((TextView) buttonViews[i]).setTextColor(inactiveColor);
                    }
                }
            }
        }

        @Override
        public void onPageSelected(int position) {
            viewModel.clearSelectedFyles();
        }
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
}
