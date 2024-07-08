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

package io.olvid.messenger.onboarding;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import java.util.regex.Matcher;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.ObvLinkActivity;
import io.olvid.messenger.fragments.QRCodeScannerFragment;

public class ScanFragment extends Fragment implements View.OnClickListener, QRCodeScannerFragment.ResultHandler {
    AppCompatActivity activity;
    OnboardingViewModel viewModel;
    View rootView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.getWindow().getDecorView().setSystemUiVisibility(0);
        }
        ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.black)).start();

        viewModel = new ViewModelProvider(activity).get(OnboardingViewModel.class);

        rootView = view;
        view.findViewById(R.id.back_button).setOnClickListener(this);
        view.findViewById(R.id.more_button).setOnClickListener(this);

        QRCodeScannerFragment qrCodeScannerFragment = new QRCodeScannerFragment();
        qrCodeScannerFragment.setResultHandler(this);

        view.findViewById(R.id.switch_camera_button).setOnClickListener(v -> qrCodeScannerFragment.switchCamera());

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.scanner_fragment_placeholder, qrCodeScannerFragment);
        transaction.commit();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.back_button) {
            activity.onBackPressed();
        } else if (v.getId() == R.id.more_button) {
            PopupMenu popup = new PopupMenu(activity, v, Gravity.END);
            popup.inflate(R.menu.popup_more_onboarding);
            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.popup_action_import_from_clipboard) {
                    ClipboardManager clipboard = (ClipboardManager) App.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clipData = clipboard.getPrimaryClip();
                        if ((clipData != null) && (clipData.getItemCount() > 0)) {
                            CharSequence text = clipData.getItemAt(0).getText();
                            if (text != null) {
                                Matcher matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(text);
                                if (matcher.find() && viewModel.parseScannedConfigurationUri(matcher.group(2))) {
                                    viewModel.setDeepLinked(true);
                                    if (viewModel.getKeycloakServer() != null) {
                                        Navigation.findNavController(v).navigate(R.id.action_keycloak_scanned);
                                    } else {
                                        Navigation.findNavController(v).navigate(R.id.action_configuration_scanned);
                                    }
                                    return true;
                                }
                            }
                        }
                    }
                    App.toast(R.string.toast_message_invalid_clipboard_data, Toast.LENGTH_SHORT);
                    return true;
                } else if (menuItem.getItemId() == R.id.popup_action_manual_configuration) {
                    Navigation.findNavController(v).navigate(R.id.action_configuration_scanned);
                } else if (menuItem.getItemId() == R.id.popup_action_use_keycloak) {
                    Navigation.findNavController(v).navigate(R.id.action_keycloak_scanned);
                }
                return false;
            });
            popup.show();
        }
    }

    @Override
    public boolean handleResult(String text) {
        Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(100);
        }
        Matcher matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(text);
        if (matcher.find() && viewModel.parseScannedConfigurationUri(matcher.group(2))) {
            viewModel.setDeepLinked(true);
            if (viewModel.getKeycloakServer() != null) {
                activity.runOnUiThread(() -> Navigation.findNavController(rootView).navigate(R.id.action_keycloak_scanned));
            } else {
                activity.runOnUiThread(() -> Navigation.findNavController(rootView).navigate(R.id.action_configuration_scanned));
            }
            return true;
        }

        App.toast(R.string.toast_message_unrecognized_url, Toast.LENGTH_SHORT, Gravity.BOTTOM);
        return false;
    }
}
