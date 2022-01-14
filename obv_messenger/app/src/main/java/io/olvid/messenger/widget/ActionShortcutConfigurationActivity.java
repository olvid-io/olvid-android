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

package io.olvid.messenger.widget;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.LockScreenOrNotActivity;
import io.olvid.messenger.customClasses.TextChangeListener;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.entity.ActionShortcutConfiguration;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.fragments.FilteredDiscussionListFragment;
import io.olvid.messenger.fragments.dialog.OwnedIdentitySelectionDialogFragment;
import io.olvid.messenger.settings.SettingsActivity;

public class ActionShortcutConfigurationActivity extends LockScreenOrNotActivity {
    int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    ActionShortcutConfigurationViewModel viewModel;

    private InitialView currentIdentityInitialView;
    private TextView currentNameTextView;
    private TextView currentNameSecondLineTextView;
    private ImageView currentIdentityMutedImageView;
    private View separator;
    private OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter adapter;
    private PopupWindow ownedIdentityPopupWindow;

    private TextView discussionEmptyTextView;
    private InitialView discussionInitialView;
    private TextView discussionTitleTextView;
    private TextView discussionGroupMembersTextView;

    Button widgetIconButton;
    Button widgetTintButton;
    private PopupWindow widgetIconPopupWindow;
    private PopupWindow widgetTintPopupWindow;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void notLockedOnCreate() {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        setResult(RESULT_CANCELED);

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }

        setContentView(R.layout.activity_widget_action_shortcut_configuration);

        viewModel = new ViewModelProvider(this).get(ActionShortcutConfigurationViewModel.class);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        currentIdentityInitialView = findViewById(R.id.current_identity_initial_view);
        currentNameTextView = findViewById(R.id.current_identity_name_text_view);
        currentNameSecondLineTextView = findViewById(R.id.current_identity_name_second_line_text_view);
        currentIdentityMutedImageView = findViewById(R.id.current_identity_muted_marker_image_view);
        separator = findViewById(R.id.separator);

        TextView switchProfileButton = findViewById(R.id.button_switch_profile);
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
            new OpenHiddenProfileDialog(this);
            return true;
        });

        adapter = new OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter(getLayoutInflater(), bytesOwnedIdentity -> {
            if (ownedIdentityPopupWindow != null) {
                ownedIdentityPopupWindow.dismiss();
            }
            AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, null);
        });
        Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenExceptOne(ownedIdentity == null ? null : ownedIdentity.bytesOwnedIdentity)).observe(this, adapter);

        AppSingleton.getCurrentIdentityLiveData().observe(this, this::bindOwnedIdentity);


        discussionEmptyTextView = findViewById(R.id.discussion_empty_text_view);
        discussionInitialView = findViewById(R.id.discussion_initial_view);
        discussionTitleTextView = findViewById(R.id.discussion_title_text_view);
        discussionGroupMembersTextView = findViewById(R.id.discussion_group_members_text_view);

        viewModel.getDiscussionLiveData().observe(this, this::bindDiscussion);

        EditText widgetLabelEditText = findViewById(R.id.widget_label_edit_text);
        widgetLabelEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setWidgetLabel(s == null ? null : s.toString());
            }
        });

        CheckBox widgetBadgeCheckBox = findViewById(R.id.checkbox_show_badge);
        widgetBadgeCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> viewModel.setWidgetShowBadge(isChecked));

        widgetIconButton = findViewById(R.id.change_widget_icon_button);
        widgetIconButton.setOnClickListener(v -> {
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            openIconPicker();
        });
        widgetTintButton = findViewById(R.id.change_widget_tint_button);
        widgetTintButton.setOnClickListener(v -> {
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            openColorPicker();
        });

        ViewGroup discussionFrame = findViewById(R.id.discussion_frame);
        discussionFrame.setOnClickListener(v -> {
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            DiscussionSearchDialogFragment discussionSearchDialogFragment = DiscussionSearchDialogFragment.newInstance();
            discussionSearchDialogFragment.show(getSupportFragmentManager(), "dialog");
        });

        EditText messageEditText = findViewById(R.id.message_edit_text);
        messageEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setActionMessage(s == null ? null : s.toString());
            }
        });

        CheckBox confirmCheckbox = findViewById(R.id.checkbox_confirm_send);
        confirmCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> viewModel.setActionConfirmBeforeSending(isChecked));

//        CheckBox vibrateCheckbox = findViewById(R.id.checkbox_vibrate_on_send);
//        vibrateCheckbox.setOnCheckedChangeListener(((buttonView, isChecked) -> viewModel.setActionVibrateAfterSending(isChecked)));

        TextView cancelButton = findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(v -> finish());
        TextView saveButton = findViewById(R.id.button_save);
        saveButton.setOnClickListener(v -> configurationDone());
        viewModel.getValidLiveData().observe(this, (Boolean valid) -> saveButton.setEnabled(valid != null && valid));

        ViewGroup widgetPreview = findViewById(R.id.widget_preview);
        TextView widgetLabel = widgetPreview.findViewById(R.id.widget_label);
        viewModel.getWidgetLabelLiveData().observe(this, widgetLabel::setText);

        // the preview icon is 36dp
        int widgetIconSize = (int) (getResources().getDisplayMetrics().density * 36);

        ImageView widgetIcon = widgetPreview.findViewById(R.id.widget_icon);
        viewModel.getWidgetIconAndTineLiveData().observe(this, (Pair<String,Integer> iconAndTint) -> {
            if (iconAndTint == null || iconAndTint.first == null) {
                return;
            }
            int iconResource = ActionShortcutWidgetProvider.getIconResource(iconAndTint.first);
            widgetIconButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, iconResource, 0);

            if (iconAndTint.second == null) {
                widgetTintButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_color_none, 0);
            } else {
                Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_color_disc, null);
                if (drawable != null) {
                    drawable.mutate();
                    drawable.setColorFilter(iconAndTint.second, PorterDuff.Mode.MULTIPLY);
                    widgetTintButton.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, drawable, null);
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(widgetIconSize, widgetIconSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Drawable drawable = ResourcesCompat.getDrawable(getResources(), iconResource, null);
            if (drawable != null) {
                if (iconAndTint.second != null) {
                    drawable.setColorFilter(new PorterDuffColorFilter(iconAndTint.second, PorterDuff.Mode.SRC_IN));
                }
                drawable.setBounds(0, 0, widgetIconSize, widgetIconSize);
                drawable.draw(canvas);
            }
            widgetIcon.setImageBitmap(bitmap);
        });

        ImageView widgetBadge = widgetPreview.findViewById(R.id.widget_branding);
        viewModel.getWidgetShowBadgeLiveData().observe(this, (Boolean showBadge) -> {
            if (showBadge == null || showBadge) {
                widgetBadge.setVisibility(View.VISIBLE);
            } else {
                widgetBadge.setVisibility(View.GONE);
            }
        });


        // load widget configuration if any
        App.runThread(() -> {
            try {
                ActionShortcutConfiguration actionShortcutConfiguration = AppDatabase.getInstance().actionShortcutConfigurationDao().getByAppWidgetId(appWidgetId);
                if (actionShortcutConfiguration != null) {
                    ActionShortcutConfiguration.JsonConfiguration jsonConfiguration = actionShortcutConfiguration.getJsonConfiguration();
                    if (jsonConfiguration != null) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            viewModel.setActionDiscussionId(actionShortcutConfiguration.discussionId);

                            widgetLabelEditText.setText(jsonConfiguration.widgetLabel);
                            viewModel.setWidgetIcon(jsonConfiguration.widgetIcon);
                            viewModel.setWidgetTint(jsonConfiguration.widgetIconTint);
                            widgetBadgeCheckBox.setChecked(jsonConfiguration.widgetShowBadge);

                            messageEditText.setText(jsonConfiguration.messageToSend);
                            confirmCheckbox.setChecked(jsonConfiguration.confirmBeforeSend);
//                            vibrateCheckbox.setChecked(jsonConfiguration.vibrateOnSend);
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    private void bindOwnedIdentity(OwnedIdentity ownedIdentity) {
        if (currentIdentityInitialView == null || currentNameTextView == null || currentNameSecondLineTextView == null || currentIdentityMutedImageView == null || viewModel == null) {
            return;
        }


        if (ownedIdentity == null) {
            viewModel.setBytesOwnedIdentity(null);
            currentIdentityInitialView.setKeycloakCertified(false);
            currentIdentityInitialView.setInactive(false);
            currentIdentityInitialView.setInitial(new byte[0], " ");
            currentIdentityMutedImageView.setVisibility(View.GONE);
            return;
        }

        viewModel.setBytesOwnedIdentity(ownedIdentity.bytesOwnedIdentity);
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
    }

    private void bindDiscussion(DiscussionDao.DiscussionAndContactDisplayNames discussionAndContactNames) {
        if (discussionEmptyTextView == null || discussionInitialView == null || discussionTitleTextView == null || discussionGroupMembersTextView == null) {
            return;
        }

        if (discussionAndContactNames == null) {
            discussionEmptyTextView.setVisibility(View.VISIBLE);
            discussionInitialView.setVisibility(View.GONE);
            discussionTitleTextView.setVisibility(View.GONE);
            discussionGroupMembersTextView.setVisibility(View.GONE);
            return;
        }

        discussionEmptyTextView.setVisibility(View.GONE);

        discussionInitialView.setVisibility(View.VISIBLE);
        if (discussionAndContactNames.discussion.bytesGroupOwnerAndUid != null) {
            discussionInitialView.setKeycloakCertified(discussionAndContactNames.discussion.keycloakManaged);
            discussionInitialView.setInactive(!discussionAndContactNames.discussion.active);
            if (discussionAndContactNames.discussion.photoUrl == null) {
                discussionInitialView.setGroup(discussionAndContactNames.discussion.bytesGroupOwnerAndUid);
            } else {
                discussionInitialView.setPhotoUrl(discussionAndContactNames.discussion.bytesGroupOwnerAndUid, discussionAndContactNames.discussion.photoUrl);
            }
        } else if (discussionAndContactNames.discussion.bytesContactIdentity != null){
            discussionInitialView.setKeycloakCertified(discussionAndContactNames.discussion.keycloakManaged);
            discussionInitialView.setInactive(!discussionAndContactNames.discussion.active);
            if (discussionAndContactNames.discussion.photoUrl == null) {
                discussionInitialView.setInitial(discussionAndContactNames.discussion.bytesContactIdentity, App.getInitial(discussionAndContactNames.discussion.title));
            } else {
                discussionInitialView.setPhotoUrl(discussionAndContactNames.discussion.bytesContactIdentity, discussionAndContactNames.discussion.photoUrl);
            }
        } else {
            discussionInitialView.setKeycloakCertified(false);
            discussionInitialView.setInactive(false);
            discussionInitialView.setLocked(true);
            if (discussionAndContactNames.discussion.photoUrl == null) {
                discussionInitialView.setInitial(new byte[0], "");
            } else {
                discussionInitialView.setPhotoUrl(new byte[0], discussionAndContactNames.discussion.photoUrl);
            }
        }

        discussionTitleTextView.setVisibility(View.VISIBLE);
        discussionTitleTextView.setText(discussionAndContactNames.discussion.title);
        if (discussionAndContactNames.discussion.bytesGroupOwnerAndUid != null) {
            discussionGroupMembersTextView.setVisibility(View.VISIBLE);
            if (discussionAndContactNames.groupContactDisplayNames == null || discussionAndContactNames.groupContactDisplayNames.length() == 0) {
                StyleSpan sp = new StyleSpan(Typeface.ITALIC);
                SpannableString ss = new SpannableString(getString(R.string.text_nobody));
                ss.setSpan(sp, 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                discussionGroupMembersTextView.setText(ss);
            } else {
                discussionGroupMembersTextView.setText(discussionAndContactNames.groupContactDisplayNames);
            }
        } else {
            discussionGroupMembersTextView.setVisibility(View.GONE);
        }
    }


    private void openSwitchProfilePopup() {
        if (separator == null || adapter == null) {
            return;
        }
        View popupView = getLayoutInflater().inflate(R.layout.popup_switch_owned_identity, null);
        ownedIdentityPopupWindow = new PopupWindow(popupView, separator.getWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        ownedIdentityPopupWindow.setElevation(12);
        ownedIdentityPopupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.background_half_rounded_dialog));
        ownedIdentityPopupWindow.setOnDismissListener(() -> ownedIdentityPopupWindow = null);

        EmptyRecyclerView ownedIdentityListRecyclerView = popupView.findViewById(R.id.owned_identity_list_recycler_view);
        ownedIdentityListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        ownedIdentityListRecyclerView.setAdapter(adapter);
        ownedIdentityListRecyclerView.setEmptyView(popupView.findViewById(R.id.empty_view));

        ownedIdentityPopupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);
        ownedIdentityPopupWindow.showAsDropDown(separator);
    }

    private static class OpenHiddenProfileDialog extends io.olvid.messenger.customClasses.OpenHiddenProfileDialog {
        public OpenHiddenProfileDialog(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @Override
        protected void onHiddenIdentityPasswordEntered(byte[] byteOwnedIdentity) {
            AppSingleton.getInstance().selectIdentity(byteOwnedIdentity, null);
        }
    }

    private void openColorPicker() {
        if (widgetTintButton == null) {
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int thirtyTwoDp = (int) (32 * metrics.density);

        ScrollView popupView = new ScrollView(this);
        popupView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ConstraintLayout constraintLayout = new ConstraintLayout(this);
        constraintLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popupView.addView(constraintLayout);

        // add a flow layout for all subviews
        Flow flow = new Flow(this);
        flow.setId(View.generateViewId());

        ConstraintLayout.LayoutParams flowLayout = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT);
        flowLayout.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.topMargin = flowLayout.leftMargin = flowLayout.rightMargin = flowLayout.bottomMargin = thirtyTwoDp/4;
        flow.setLayoutParams(flowLayout);

        flow.setOrientation(Flow.HORIZONTAL);
        flow.setWrapMode(Flow.WRAP_ALIGNED);
        flow.setHorizontalStyle(Flow.CHAIN_SPREAD_INSIDE);
        flow.setHorizontalGap(thirtyTwoDp/4);
        flow.setVerticalGap(thirtyTwoDp/4);
        constraintLayout.addView(flow);

        {
            ImageView imageView = new ImageView(this);
            imageView.setId(View.generateViewId());
            imageView.setLayoutParams(new ViewGroup.LayoutParams((int) (1.5 * thirtyTwoDp), (int) (1.5 * thirtyTwoDp)));

            imageView.setImageResource(R.drawable.ic_color_none);
            imageView.setOnClickListener(v -> {
                viewModel.setWidgetTint(null);
                if (widgetTintPopupWindow != null) {
                    widgetTintPopupWindow.dismiss();
                }
            });
            constraintLayout.addView(imageView);
            flow.addView(imageView);
        }

        int[] colorInts = new int[]{
                0xfff44336, 0xffe91e63, 0xff9c27b0, 0xff673ab7, 0xff3f51b5,
                0xff2196f3, 0xff03a9f4, 0xff00bcd4, 0xff009688, 0xff4caf50,
                0xff8bc34a, 0xffcddc39, 0xffffeb3b, 0xffffc107, 0xffff9800,
                0xffff5722, 0xff795548, 0xff9e9e9e, 0xff607d8b, 0xffffffff,
                0xff000000,
        };

        for (int colorInt: colorInts) {
            ImageView imageView = new ImageView(this);
            imageView.setId(View.generateViewId());
            imageView.setLayoutParams(new ViewGroup.LayoutParams((int) (1.5 * thirtyTwoDp), (int) (1.5 * thirtyTwoDp)));

            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_color_disc, null);
            if (drawable == null) {
                continue;
            }
            drawable.mutate();
            drawable.setColorFilter(colorInt, PorterDuff.Mode.MULTIPLY);
            imageView.setImageDrawable(drawable);
            imageView.setOnClickListener(v -> {
                viewModel.setWidgetTint(colorInt);
                if (widgetTintPopupWindow != null) {
                    widgetTintPopupWindow.dismiss();
                }
            });
            constraintLayout.addView(imageView);
            flow.addView(imageView);
        }

        int popupWidth = Math.min(9 * thirtyTwoDp, metrics.widthPixels - 2*thirtyTwoDp);
        widgetTintPopupWindow = new PopupWindow(popupView, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        widgetTintPopupWindow.setElevation(12);
        widgetTintPopupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.background_rounded_dialog));
        widgetTintPopupWindow.setOnDismissListener(() -> widgetTintPopupWindow = null);
        widgetTintPopupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);

        // center the popup below the button
        int[] pos = new int[2];
        widgetTintButton.getLocationOnScreen(pos);
        final int xOffset;
        if (pos[0] - (popupWidth - widgetTintButton.getWidth()) / 2 < thirtyTwoDp) {
            xOffset = thirtyTwoDp - pos[0];
        } else if (pos[0] + (popupWidth + widgetTintButton.getWidth()) / 2 > metrics.widthPixels - thirtyTwoDp) {
            xOffset = metrics.widthPixels - thirtyTwoDp - popupWidth - pos[0];
        } else {
            xOffset = (widgetTintButton.getWidth() - popupWidth) / 2;
        }
        widgetTintPopupWindow.showAsDropDown(widgetTintButton, xOffset, 0);
    }

    private void openIconPicker() {
        if (widgetIconButton == null) {
            return;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int thirtyTwoDp = (int) (32 * metrics.density);

        ScrollView popupView = new ScrollView(this);
        popupView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ConstraintLayout constraintLayout = new ConstraintLayout(this);
        constraintLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popupView.addView(constraintLayout);

        // add a flow layout for all subviews
        Flow flow = new Flow(this);
        flow.setId(View.generateViewId());

        ConstraintLayout.LayoutParams flowLayout = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT);
        flowLayout.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        flowLayout.topMargin = flowLayout.leftMargin = flowLayout.rightMargin = flowLayout.bottomMargin = thirtyTwoDp/4;
        flow.setLayoutParams(flowLayout);

        flow.setOrientation(Flow.HORIZONTAL);
        flow.setWrapMode(Flow.WRAP_ALIGNED);
        flow.setHorizontalStyle(Flow.CHAIN_SPREAD_INSIDE);
        flow.setHorizontalGap(thirtyTwoDp/4);
        flow.setVerticalGap(thirtyTwoDp/4);
        constraintLayout.addView(flow);

        for (String iconString : ActionShortcutConfiguration.JsonConfiguration.ICONS) {
            ImageView imageView = new ImageView(this);
            imageView.setId(View.generateViewId());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(2 * thirtyTwoDp, 2 * thirtyTwoDp));
            imageView.setImageResource(ActionShortcutWidgetProvider.getIconResource(iconString));
            imageView.setBackgroundResource(R.drawable.background_circular_ripple);
            imageView.setOnClickListener(v -> {
                viewModel.setWidgetIcon(iconString);
                if (widgetIconPopupWindow != null) {
                    widgetIconPopupWindow.dismiss();
                }
            });
            constraintLayout.addView(imageView);
            flow.addView(imageView);
        }

        int popupWidth = Math.min((int) (9.3 * thirtyTwoDp), metrics.widthPixels - 2*thirtyTwoDp);
        widgetIconPopupWindow = new PopupWindow(popupView, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        widgetIconPopupWindow.setElevation(12);
        widgetIconPopupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.background_rounded_dialog));
        widgetIconPopupWindow.setOnDismissListener(() -> widgetIconPopupWindow = null);
        widgetIconPopupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);

        // center the popup below the button
        int[] pos = new int[2];
        widgetIconButton.getLocationOnScreen(pos);
        final int xOffset;
        if (pos[0] - (popupWidth - widgetIconButton.getWidth()) / 2 < thirtyTwoDp) {
            xOffset = thirtyTwoDp - pos[0];
        } else if (pos[0] + (popupWidth + widgetIconButton.getWidth()) / 2 > metrics.widthPixels - thirtyTwoDp) {
            xOffset = metrics.widthPixels - thirtyTwoDp - popupWidth - pos[0];
        } else {
            xOffset = (widgetIconButton.getWidth() - popupWidth) / 2;
        }
        widgetIconPopupWindow.showAsDropDown(widgetIconButton, xOffset, 0);
    }

    void configurationDone() {
        App.runThread(() -> {
            Long discussionId = viewModel.getActionDiscussionIdLiveData().getValue();
            String message = viewModel.getActionMessage();
            if (discussionId == null || message == null || message.trim().length() == 0) {
                return;
            }


            ActionShortcutConfiguration.JsonConfiguration configuration = new ActionShortcutConfiguration.JsonConfiguration();
            configuration.widgetLabel = viewModel.getWidgetLabelLiveData().getValue();
            configuration.widgetIcon = viewModel.getWidgetIconLiveData().getValue();
            configuration.widgetIconTint = viewModel.getWidgetTintLiveData().getValue();
            Boolean showBadge = viewModel.getWidgetShowBadgeLiveData().getValue();
            configuration.widgetShowBadge = showBadge == null || showBadge;

            configuration.messageToSend = viewModel.getActionMessage().trim();
            configuration.confirmBeforeSend = viewModel.isActionConfirmBeforeSending();
//            configuration.vibrateOnSend = viewModel.isActionVibrateAfterSending();

            try {
                ActionShortcutConfiguration actionShortcutConfiguration = AppDatabase.getInstance().actionShortcutConfigurationDao().getByAppWidgetId(appWidgetId);
                if (actionShortcutConfiguration != null) {
                    actionShortcutConfiguration.discussionId = discussionId;
                    actionShortcutConfiguration.setJsonConfiguration(configuration);

                    AppDatabase.getInstance().actionShortcutConfigurationDao().update(actionShortcutConfiguration);
                } else {
                    actionShortcutConfiguration = new ActionShortcutConfiguration(appWidgetId, discussionId, configuration);
                    AppDatabase.getInstance().actionShortcutConfigurationDao().insert(actionShortcutConfiguration);
                }
            } catch (Exception e) {
                e.printStackTrace();
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT);
                return;
            }

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            ActionShortcutWidgetProvider.configureWidget(this, appWidgetManager, appWidgetId);
            Intent resultValue = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
    }


    public static class DiscussionSearchDialogFragment extends DialogFragment {
        EditText dialogContactNameFilter;
        ActionShortcutConfigurationViewModel viewModel;

        public static DiscussionSearchDialogFragment newInstance() {
            return new DiscussionSearchDialogFragment();
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            viewModel = new ViewModelProvider(requireActivity()).get(ActionShortcutConfigurationViewModel.class);
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
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
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
            filteredDiscussionListFragment.removeBottomPadding();
            filteredDiscussionListFragment.setUnfilteredDiscussions(viewModel.getDiscussionListLiveData());
            filteredDiscussionListFragment.setDiscussionFilterEditText(dialogContactNameFilter);
            filteredDiscussionListFragment.setOnClickDelegate((view, searchableDiscussion) -> {
                viewModel.setActionDiscussionId(searchableDiscussion.discussionId);
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                dismiss();
            });

            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
            transaction.replace(R.id.dialog_filtered_discussion_list_placeholder, filteredDiscussionListFragment);
            transaction.commit();

            dialogContactNameFilter.requestFocus();
            return dialogView;
        }
    }
}
