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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Arrays;
import java.util.regex.Matcher;

import io.olvid.engine.engine.types.identities.ObvMutualScanUrl;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.customClasses.StringUtils;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.OwnedIdentity;

public class MutualScanInvitationScannedFragment extends Fragment implements View.OnClickListener  {
    AppCompatActivity activity;
    PlusButtonViewModel viewModel;

    InitialView contactInitialView;
    TextView contactNameTextView;
    TextView mutualScanExplanationTextView;
    TextView mutualScanWarningTextView;
    CardView successCard;
    TextView successExplanationTextView;

    Button cancelButton;
    Button addContactButton;

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

        view.findViewById(R.id.back_button).setOnClickListener(this);
        view.findViewById(R.id.success_button).setOnClickListener(this);

        contactInitialView = view.findViewById(R.id.contact_initial_view);
        contactNameTextView = view.findViewById(R.id.contact_name_text_view);
        mutualScanExplanationTextView = view.findViewById(R.id.mutual_scan_explanation_text_view);
        mutualScanWarningTextView = view.findViewById(R.id.mutual_scan_warning_text_view);
        cancelButton = view.findViewById(R.id.cancel_button);
        addContactButton = view.findViewById(R.id.add_contact_button);
        successCard = view.findViewById(R.id.mutual_scan_success_card);
        successExplanationTextView = view.findViewById(R.id.mutual_scan_success_explanation_text_view);

        cancelButton.setOnClickListener(this);

        String uri = viewModel.getScannedUri();
        if (uri == null) {
            activity.finish();
            return;
        }

        ObvMutualScanUrl mutualScanUrl = null;
        Matcher matcher = ObvMutualScanUrl.MUTUAL_SCAN_PATTERN.matcher(uri);
        if (matcher.find()) {
            mutualScanUrl = ObvMutualScanUrl.fromUrlRepresentation(uri);
        }
        if (mutualScanUrl == null || viewModel.getCurrentIdentity() == null) {
            activity.finish();
            return;
        }

        if (Arrays.equals(mutualScanUrl.getBytesIdentity(), viewModel.getCurrentIdentity().bytesOwnedIdentity)) {
            displaySelfInvite(viewModel.getCurrentIdentity());
        } else {
            ObvMutualScanUrl finalMutualScanUrl = mutualScanUrl;

            if (AppSingleton.getEngine().verifyMutualScanSignedNonceUrl(viewModel.getCurrentIdentity().bytesOwnedIdentity, finalMutualScanUrl)) {
                App.runThread(() -> {
                    final Contact contact = AppDatabase.getInstance().contactDao().get(viewModel.getCurrentIdentity().bytesOwnedIdentity, finalMutualScanUrl.getBytesIdentity());
                    new Handler(Looper.getMainLooper()).post(() -> displayContact(viewModel.getCurrentIdentity(), contact, finalMutualScanUrl));
                });
            } else {
                displayBadSignature(mutualScanUrl);
            }
        }
    }

    private void displayContact(@NonNull OwnedIdentity ownedIdentity, @Nullable Contact contact, @NonNull ObvMutualScanUrl mutualScanUrl) {
        if (contact != null) {
            contactInitialView.setContact(contact);

            mutualScanWarningTextView.setVisibility(View.VISIBLE);
            mutualScanWarningTextView.setBackgroundResource(R.drawable.background_ok_message);
            mutualScanWarningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_ok_outline, 0, 0, 0);
            if (contact.oneToOne) {
                mutualScanWarningTextView.setText(getString(R.string.text_explanation_warning_mutual_scan_contact_already_known, mutualScanUrl.displayName));
            } else {
                mutualScanWarningTextView.setText(getString(R.string.text_explanation_warning_mutual_scan_contact_already_known_not_one_to_one, mutualScanUrl.displayName));
            }
        } else {
            contactInitialView.setInitial(mutualScanUrl.getBytesIdentity(), StringUtils.getInitial(mutualScanUrl.displayName));
            mutualScanWarningTextView.setVisibility(View.VISIBLE);
            mutualScanWarningTextView.setBackgroundResource(R.drawable.background_warning_message);
            mutualScanWarningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_warning_outline, 0, 0, 0);
            mutualScanWarningTextView.setText(getString(R.string.text_explanation_warning_mutual_scan_direct, mutualScanUrl.displayName));
        }

        contactNameTextView.setText(mutualScanUrl.displayName);
        mutualScanExplanationTextView.setVisibility(View.VISIBLE);
        mutualScanExplanationTextView.setText(getString(R.string.text_explanation_mutual_scan_add_contact, mutualScanUrl.displayName));

        addContactButton.setVisibility(View.VISIBLE);
        addContactButton.setOnClickListener(v -> {
            try {
                AppSingleton.getEngine().startMutualScanTrustEstablishmentProtocol(ownedIdentity.bytesOwnedIdentity, mutualScanUrl.getBytesIdentity(), mutualScanUrl.signature);
                addContactButton.setEnabled(false);
                successCard.setVisibility(View.VISIBLE);
                successExplanationTextView.setText(getString(R.string.text_explanation_mutual_scan_success, mutualScanUrl.displayName));
                cancelButton.setVisibility(View.GONE);
            } catch (Exception e) {
                App.toast(R.string.toast_message_failed_to_invite_contact, Toast.LENGTH_SHORT);
            }
        });
    }

    private void displaySelfInvite(@NonNull OwnedIdentity ownedIdentity) {
        contactInitialView.setOwnedIdentity(ownedIdentity);
        contactNameTextView.setText(ownedIdentity.displayName);

        mutualScanExplanationTextView.setVisibility(View.GONE);
        mutualScanWarningTextView.setVisibility(View.VISIBLE);
        mutualScanWarningTextView.setBackgroundResource(R.drawable.background_error_message);
        mutualScanWarningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_error_outline, 0, 0, 0);
        mutualScanWarningTextView.setText(R.string.text_explanation_warning_cannot_invite_yourself);

        addContactButton.setVisibility(View.GONE);
        addContactButton.setOnClickListener(null);
    }

    private void displayBadSignature(@NonNull ObvMutualScanUrl mutualScanUrl) {
        contactInitialView.setInitial(mutualScanUrl.getBytesIdentity(), StringUtils.getInitial(mutualScanUrl.displayName));
        contactNameTextView.setText(mutualScanUrl.displayName);

        mutualScanExplanationTextView.setVisibility(View.GONE);
        mutualScanWarningTextView.setVisibility(View.VISIBLE);
        mutualScanWarningTextView.setBackgroundResource(R.drawable.background_error_message);
        mutualScanWarningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_error_outline, 0, 0, 0);
        mutualScanWarningTextView.setText(getString(R.string.text_explanation_invalid_mutual_scan_qr_code, mutualScanUrl.displayName));

        addContactButton.setVisibility(View.GONE);
        addContactButton.setOnClickListener(null);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.cancel_button || id == R.id.back_button) {
            activity.onBackPressed();
        }
        if (id == R.id.success_button) {
            activity.finish();
        }
    }
}
