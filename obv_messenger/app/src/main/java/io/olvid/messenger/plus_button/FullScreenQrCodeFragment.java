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

package io.olvid.messenger.plus_button;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import io.olvid.messenger.App;
import io.olvid.messenger.R;

public class FullScreenQrCodeFragment extends Fragment {
    private AppCompatActivity activity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plus_button_full_screen_qr_code, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setOnClickListener(v -> activity.onBackPressed());
        view.findViewById(R.id.back_button).setOnClickListener(v -> activity.onBackPressed());

        // set screen brightness to the max
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        if (params != null && activity.getWindow() != null) {
            params.screenBrightness = 1f;
            activity.getWindow().setAttributes(params);
        }
        
        PlusButtonViewModel viewModel = new ViewModelProvider(activity).get(PlusButtonViewModel.class);
        if (viewModel.getFullScreenQrCodeUrl() == null) {
            return;
        }
        App.setQrCodeImage(view.findViewById(R.id.qr_code_image_view), viewModel.getFullScreenQrCodeUrl());
    }

    @Override
    public void onStop() {
        super.onStop();
        // restore initial screen brightness
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        if (params != null && activity.getWindow() != null) {
            params.screenBrightness = -1.0f;
            activity.getWindow().setAttributes(params);
        }
    }
}
