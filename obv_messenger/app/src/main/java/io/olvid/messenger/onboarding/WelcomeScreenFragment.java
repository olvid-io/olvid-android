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

package io.olvid.messenger.onboarding;

import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import io.olvid.messenger.R;

public class WelcomeScreenFragment extends Fragment {
    private AppCompatActivity activity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_onboarding_welcome_screen, container, false);

        activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                activity.finishAndRemoveTask();
            }
        });

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.getWindow().getDecorView().setSystemUiVisibility(0);
        }
        activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.olvid_gradient_light));

        TextView explanationTextView = view.findViewById(R.id.explanation_textview);
        explanationTextView.setMovementMethod(ScrollingMovementMethod.getInstance());

        Button restoreBackupButton = view.findViewById(R.id.button_restore_backup);
        Button continueButton = view.findViewById(R.id.button_continue);
        Button scanButton = view.findViewById(R.id.button_scan_configuration);
        if (restoreBackupButton != null) {
            restoreBackupButton.setOnClickListener(v -> Navigation.findNavController(view).navigate(WelcomeScreenFragmentDirections.actionRestore()));
        }
        if (continueButton != null) {
            continueButton.setOnClickListener(v -> Navigation.findNavController(view).navigate(WelcomeScreenFragmentDirections.actionIdentityCreation()));
        }
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> Navigation.findNavController(view).navigate(WelcomeScreenFragmentDirections.actionScanConfiguration()));
        }
    }
}
