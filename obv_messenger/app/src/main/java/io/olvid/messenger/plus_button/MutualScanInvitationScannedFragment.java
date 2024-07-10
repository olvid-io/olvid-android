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

package io.olvid.messenger.plus_button;

import android.animation.ObjectAnimator;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;

import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.SimpleEngineNotificationListener;
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Discussion;

public class MutualScanInvitationScannedFragment extends Fragment implements View.OnClickListener, Observer<Discussion> {
    AppCompatActivity activity;
    PlusButtonViewModel viewModel;

    View spinner;
    TextView message;
    View dismissButton;
    TextView discussButton;

    String contactShortDisplayName;

    boolean timeOutFired;
    SimpleEngineNotificationListener mutualScanFinishedListener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppCompatActivity) requireActivity();
        viewModel = new ViewModelProvider(activity).get(PlusButtonViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plus_button_mutual_scan_invitation_scanned, container, false);
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
                ObjectAnimator.ofArgb(activity.getWindow(), "statusBarColor", activity.getWindow().getStatusBarColor(), ContextCompat.getColor(activity, R.color.olvid_gradient_dark)).start();
            }
        }

        String uri = viewModel.getScannedUri();
        if (uri == null) {
            activity.finish();
            return;
        }

        ObvMutualScanUrl mutualScanUrl;
        Matcher matcher = ObvMutualScanUrl.MUTUAL_SCAN_PATTERN.matcher(uri);
        if (matcher.find()) {
            mutualScanUrl = ObvMutualScanUrl.fromUrlRepresentation(uri);
        } else {
            mutualScanUrl = null;
        }
        if (mutualScanUrl == null || viewModel.getCurrentIdentity() == null) {
            activity.finish();
            return;
        }

        contactShortDisplayName = StringUtils.removeCompanyFromDisplayName(mutualScanUrl.displayName);

        spinner = view.findViewById(R.id.mutual_scan_spinner);
        message = view.findViewById(R.id.mutual_scan_explanation_text_view);
        dismissButton = view.findViewById(R.id.dismiss_button);
        discussButton = view.findViewById(R.id.discuss_button);

        view.findViewById(R.id.back_button).setOnClickListener(this);
        dismissButton.setOnClickListener(this);


        final byte[] bytesOwnedIdentity = viewModel.getCurrentIdentity().bytesOwnedIdentity;
        final byte[] bytesContactIdentity = mutualScanUrl.getBytesIdentity();

        if (Arrays.equals(bytesContactIdentity, bytesOwnedIdentity)) {
            displaySelfInvite();
        } else {
            if (AppSingleton.getEngine().verifyMutualScanSignedNonceUrl(bytesOwnedIdentity, mutualScanUrl)) {
                App.runThread(() -> {
                    // check the discussion to start the correct listener
                    final Discussion discussion = AppDatabase.getInstance().discussionDao().getByContactWithAnyStatus(bytesOwnedIdentity, bytesContactIdentity);

                    if (discussion == null || !discussion.isNormal()) {
                        // not a contact yet or not a one-to-one contact --> listen to the discussion creation
                        LiveData<Discussion> discussionLiveData = AppDatabase.getInstance().discussionDao().getByContactLiveData(bytesOwnedIdentity, bytesContactIdentity);
                        new Handler(Looper.getMainLooper()).post(() -> discussionLiveData.observe(getViewLifecycleOwner(), this));
                    } else {
                        // listen to protocol notification
                        mutualScanFinishedListener = new SimpleEngineNotificationListener(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED) {
                            @Override
                            public void callback(HashMap<String, Object> userInfo) {
                                byte[] notificationBytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_OWNED_IDENTITIY_KEY);
                                byte[] notificationBytesContactIdentity = (byte[]) userInfo.get(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED_BYTES_CONTACT_IDENTITIY_KEY);

                                if (Arrays.equals(bytesOwnedIdentity, notificationBytesOwnedIdentity)
                                        && Arrays.equals(bytesContactIdentity, notificationBytesContactIdentity)) {
                                    new Handler(Looper.getMainLooper()).post(() -> onChanged(discussion));
                                }
                            }
                        };
                        AppSingleton.getEngine().addNotificationListener(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED, mutualScanFinishedListener);
                    }


                    // start the protocol
                    try {
                        AppSingleton.getEngine().startMutualScanTrustEstablishmentProtocol(bytesOwnedIdentity, bytesContactIdentity, mutualScanUrl.signature);
                    } catch (Exception ignored) {
                        App.toast(R.string.toast_message_failed_to_invite_contact, Toast.LENGTH_SHORT);
                        activity.finish();
                    }

                    // start a timeout to say the protocol was started
                    timeOutFired = false;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!timeOutFired) {
                            timeOutFired = true;
                            viewModel.setDismissOnMutualScanFinished(false);
                            spinner.setVisibility(View.GONE);
                            message.setText(getString(R.string.text_explanation_mutual_scan_pending, contactShortDisplayName));
                            message.setVisibility(View.VISIBLE);
                            dismissButton.setVisibility(View.VISIBLE);
                        }
                    }, 5_000);
                });
            } else {
                displayBadSignature();
            }
        }
    }

    private void displaySelfInvite() {
        spinner.setVisibility(View.GONE);
        message.setText(R.string.text_explanation_warning_cannot_invite_yourself);
        message.setVisibility(View.VISIBLE);
        dismissButton.setVisibility(View.VISIBLE);
    }

    private void displayBadSignature() {
        spinner.setVisibility(View.GONE);
        message.setText(getString(R.string.text_explanation_invalid_mutual_scan_qr_code, contactShortDisplayName));
        message.setVisibility(View.VISIBLE);
        dismissButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.back_button) {
            activity.onBackPressed();
        }
        if (id == R.id.dismiss_button) {
            activity.finish();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onStop() {
        super.onStop();
        timeOutFired = true;
        if (mutualScanFinishedListener != null) {
            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.MUTUAL_SCAN_CONTACT_ADDED, mutualScanFinishedListener);
        }
    }

    @Override
    public void onChanged(Discussion discussion) {
        if (discussion != null) {
            // this is the listener to switch to the correct discussion once contact addition is done
            if (timeOutFired) {
                message.setText(getString(R.string.text_explanation_mutual_scan_success, contactShortDisplayName));
                dismissButton.setVisibility(View.GONE);
                discussButton.setText(getString(R.string.button_label_discuss_with, contactShortDisplayName));
                discussButton.setVisibility(View.VISIBLE);
                discussButton.setOnClickListener(v -> {
                    App.openDiscussionActivity(activity, discussion.id);
                    activity.finish();
                });
            } else {
                App.openDiscussionActivity(activity, discussion.id);
                activity.finish();
            }
        }
    }
}
