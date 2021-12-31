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

package io.olvid.messenger.plus_button;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.zxing.Result;

import java.util.regex.Matcher;

import io.olvid.engine.engine.types.identities.ObvMutualScanUrl;
import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.ObvLinkActivity;
import io.olvid.messenger.fragments.QRCodeScannerFragment;

public class ScanFragment extends Fragment implements View.OnClickListener, QRCodeScannerFragment.ResultHandler {
    AppCompatActivity activity;
    PlusButtonViewModel viewModel;
    View rootView;
    Button showButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plus_button_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.getWindow().getDecorView().setSystemUiVisibility(0);
        }
        ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.black)).start();

        viewModel = new ViewModelProvider(activity).get(PlusButtonViewModel.class);

        rootView = view;
        view.findViewById(R.id.back_button).setOnClickListener(this);
        showButton = view.findViewById(R.id.show_my_id_button);
        showButton.setOnClickListener(this);

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
        if (id == R.id.back_button || id == R.id.show_my_id_button) {
            activity.onBackPressed();
        }
    }

    @Override
    public boolean handleResult(Result result) {
        String text = result.getText();
        Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(100);
        }
        Matcher matcher = ObvLinkActivity.INVITATION_PATTERN.matcher(text);
        if (matcher.find()) {
            viewModel.setScannedUri(matcher.group(0));
            new Handler(Looper.getMainLooper()).post(() -> Navigation.findNavController(rootView).navigate(ScanFragmentDirections.actionScannedInvitation()));
            return true;
        }

        matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(text);
        if (matcher.find()) {
            viewModel.setScannedUri(matcher.group(0));
            new Handler(Looper.getMainLooper()).post(() -> Navigation.findNavController(rootView).navigate(ScanFragmentDirections.actionScannedConfiguration()));
            return true;
        }

        matcher = ObvLinkActivity.WEB_CLIENT_PATTERN.matcher(text);
        if (matcher.find()) {
            viewModel.setScannedUri(matcher.group(0));
            new Handler(Looper.getMainLooper()).post(() -> Navigation.findNavController(rootView).navigate(ScanFragmentDirections.actionScannedWebclient()));
            return true;
        }

        matcher = ObvMutualScanUrl.MUTUAL_SCAN_PATTERN.matcher(text);
        if (matcher.find()) {
            viewModel.setScannedUri(matcher.group(0));
            new Handler(Looper.getMainLooper()).post(() -> Navigation.findNavController(rootView).navigate(ScanFragmentDirections.actionScannedMutualScanInvitation()));
            return true;
        }

        App.toast(R.string.toast_message_unrecognized_url, Toast.LENGTH_SHORT, Gravity.BOTTOM);
        return false;
    }
}
