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

package io.olvid.messenger.onboarding;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import io.olvid.engine.engine.types.ObvBackupKeyVerificationOutput;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.TextChangeListener;


public class RestoreEnterKeyFragment extends Fragment {
    private OnboardingViewModel viewModel;
    private FragmentActivity activity;

    private CardView selectedBackupCardview;
    private TextView selectedBackupTitle;
    private TextView selectedBackupFile;
    private EditText backupKeyEdittext;
    private LinearLayout errorSuccessLayout;
    private ImageView errorSuccessImage;
    private TextView errorSuccessText;
    private Button verifyBackupKeyButton;
    private Button restoreBackupButton;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_restore_enter_key, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        activity = requireActivity();
        viewModel = new ViewModelProvider(requireActivity()).get(OnboardingViewModel.class);

        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                if (activity.getWindow().getStatusBarColor() == 0xff000000) {
                    ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.almostWhite)).start();
                } else {
                    activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.almostWhite));
                }
            } else {
                ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.olvid_gradient_light)).start();
            }
        }

        view.findViewById(R.id.back_button).setOnClickListener(v -> activity.onBackPressed());

        selectedBackupCardview = view.findViewById(R.id.selected_backup_cardview);
        selectedBackupTitle = view.findViewById(R.id.selected_backup_title);
        selectedBackupFile = view.findViewById(R.id.selected_backup_file);
        backupKeyEdittext = view.findViewById(R.id.backup_key_edittext);
        errorSuccessLayout = view.findViewById(R.id.error_success_layout);
        errorSuccessImage = view.findViewById(R.id.error_success_image);
        errorSuccessText = view.findViewById(R.id.error_success_text);
        verifyBackupKeyButton = view.findViewById(R.id.button_check_key);
        restoreBackupButton = view.findViewById(R.id.button_restore_backup);

        backupKeyEdittext.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        backupKeyEdittext.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkBackupSeedAndHideKeyboard(v);
                return true;
            }
            return false;
        });

        backupKeyEdittext.addTextChangedListener(new TextChangeListener() {
            @Override
            public void afterTextChanged(Editable s) {
                CharSequence text = backupKeyEdittext.getText();
                viewModel.setBackupSeed(text.toString());
                verifyBackupKeyButton.setEnabled(text.length() > 0);
            }
        });

        verifyBackupKeyButton.setOnClickListener(this::checkBackupSeedAndHideKeyboard);

        viewModel.getBackupReady().observe(getViewLifecycleOwner(), ready -> {
            if (selectedBackupCardview == null || selectedBackupFile == null || selectedBackupTitle == null) {
                return;
            }
            if (ready != null && ready) {
                selectedBackupCardview.setVisibility(View.VISIBLE);
                selectedBackupFile.setText(viewModel.getBackupName().getValue());
                switch (viewModel.getBackupType()) {
                    case OnboardingViewModel.BACKUP_TYPE_FILE: {
                        selectedBackupTitle.setText(R.string.text_title_backup_file_selected);
                        selectedBackupTitle.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                        break;
                    }
                    case OnboardingViewModel.BACKUP_TYPE_CLOUD: {
                        selectedBackupTitle.setText(R.string.text_title_backup_cloud_account_selected);
                        selectedBackupTitle.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                        break;
                    }
                }
            } else {
                NavHostFragment.findNavController(this).popBackStack();
            }
        });

        viewModel.getBackupKeyValid().observe(getViewLifecycleOwner(), valid -> {
            if (verifyBackupKeyButton == null || restoreBackupButton == null) {
                return;
            }
            if (valid != null && valid) {
                verifyBackupKeyButton.setVisibility(View.GONE);
                restoreBackupButton.setVisibility(View.VISIBLE);
                restoreBackupButton.setEnabled(true);
            } else {
                verifyBackupKeyButton.setVisibility(View.VISIBLE);
                restoreBackupButton.setVisibility(View.GONE);
                restoreBackupButton.setEnabled(false);
            }
        });

        viewModel.getForceDisabled().observe(getViewLifecycleOwner(), disabled -> {
            if (disabled == null) {
                return;
            }
            restoreBackupButton.setEnabled(!disabled);
        });

        restoreBackupButton.setOnClickListener(v -> {
            if (viewModel.getForceDisabled().getValue() != null && !viewModel.getForceDisabled().getValue()) {
                if (viewModel.getBackupKeyValid().getValue() != null && viewModel.getBackupKeyValid().getValue()) {
                    viewModel.setForceDisabled(true);
                    AppSingleton.getInstance().restoreBackup(
                            activity,
                            viewModel.getBackupSeed(),
                            viewModel.getBackupContent().getValue(),
                            activity::finish,
                            () -> viewModel.setForceDisabled(false));
                }
            }
        });
    }

    private void checkBackupSeedAndHideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        switch (viewModel.validateBackupSeed()) {
            case ObvBackupKeyVerificationOutput.STATUS_SUCCESS: {
                errorSuccessLayout.setVisibility(View.VISIBLE);
                errorSuccessImage.setImageResource(R.drawable.ic_ok_green);
                errorSuccessText.setText(R.string.text_backup_key_verification_success_ready_for_verification);
                break;
            }
            case ObvBackupKeyVerificationOutput.STATUS_TOO_SHORT: {
                errorSuccessLayout.setVisibility(View.VISIBLE);
                errorSuccessImage.setImageResource(R.drawable.ic_error_outline);
                errorSuccessText.setText(R.string.text_backup_key_verification_failed_short);

                Animation shakeAnimation = AnimationUtils.loadAnimation(activity, R.anim.shake);
                backupKeyEdittext.startAnimation(shakeAnimation);
                break;
            }
            case ObvBackupKeyVerificationOutput.STATUS_TOO_LONG: {
                errorSuccessLayout.setVisibility(View.VISIBLE);
                errorSuccessImage.setImageResource(R.drawable.ic_error_outline);
                errorSuccessText.setText(R.string.text_backup_key_verification_failed_long);

                Animation shakeAnimation = AnimationUtils.loadAnimation(activity, R.anim.shake);
                backupKeyEdittext.startAnimation(shakeAnimation);
                break;
            }
            case ObvBackupKeyVerificationOutput.STATUS_BAD_KEY:
            default: {
                errorSuccessLayout.setVisibility(View.VISIBLE);
                errorSuccessImage.setImageResource(R.drawable.ic_error_outline);
                errorSuccessText.setText(R.string.text_backup_key_verification_failed);

                Animation shakeAnimation = AnimationUtils.loadAnimation(activity, R.anim.shake);
                backupKeyEdittext.startAnimation(shakeAnimation);
                break;
            }
        }
    }
}
