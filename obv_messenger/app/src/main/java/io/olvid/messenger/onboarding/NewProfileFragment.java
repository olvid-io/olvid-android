/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
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
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import io.olvid.messenger.R;

public class NewProfileFragment extends Fragment {
    private AppCompatActivity activity;
    private View focusHugger;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_new_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        TextView explanationTextView = view.findViewById(R.id.explanation_textview);
        explanationTextView.setMovementMethod(ScrollingMovementMethod.getInstance());

        focusHugger = view.findViewById(R.id.focus_hugger);

        view.findViewById(R.id.back_button).setOnClickListener(v -> activity.onBackPressed());
        Button continueButton = view.findViewById(R.id.button_continue);
        Button scanButton = view.findViewById(R.id.button_scan_configuration);

        if (continueButton != null) {
            continueButton.setOnClickListener(v -> Navigation.findNavController(view).navigate(NewProfileFragmentDirections.actionIdentityCreation()));
        }
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> Navigation.findNavController(view).navigate(NewProfileFragmentDirections.actionScanConfiguration()));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (focusHugger != null) {
            focusHugger.requestFocus();
        }
    }
}
