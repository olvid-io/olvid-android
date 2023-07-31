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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import java.util.Arrays;

import io.olvid.engine.engine.types.JsonKeycloakUserDetails;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.openid.KeycloakTasks;
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsFragment;
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel;

public class KeycloakBindFragment extends Fragment {
    private PlusButtonViewModel viewModel;
    private FragmentActivity activity;

    private Button bindButton;
    private View spinnerGroup;
    private boolean forceDisabled = false;
    private TextView warningTextView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_plus_button_keycloak_bind, container, false);
        activity = requireActivity();
        viewModel = new ViewModelProvider(activity).get(PlusButtonViewModel.class);

        if (viewModel.getKeycloakSerializedAuthState() == null || viewModel.getKeycloakJwks() == null || viewModel.getKeycloakServerUrl() == null) {
            activity.finish();
            return rootView;
        }



        OwnedIdentityDetailsFragment ownedIdentityDetailsFragment = new OwnedIdentityDetailsFragment();
        ownedIdentityDetailsFragment.setLockedUserDetails(viewModel.getKeycloakUserDetails().userDetails, true);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_identity_details_placeholder, ownedIdentityDetailsFragment);
        transaction.commit();

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (viewModel.getCurrentIdentity() == null) {
            activity.finish();
            return;
        }

        OwnedIdentityDetailsViewModel detailsViewModel = new ViewModelProvider(activity).get(OwnedIdentityDetailsViewModel.class);
        detailsViewModel.setBytesOwnedIdentity(viewModel.getCurrentIdentity().bytesOwnedIdentity);
        detailsViewModel.setAbsolutePhotoUrl(App.absolutePathFromRelative(viewModel.getCurrentIdentity().photoUrl));

        view.findViewById(R.id.back_button).setOnClickListener(v -> activity.onBackPressed());

        bindButton = view.findViewById(R.id.button_keycloak_bind);
        bindButton.setOnClickListener(v -> bindIdentity());
        spinnerGroup = view.findViewById(R.id.spinner);

        warningTextView = view.findViewById(R.id.keycloak_warning_textview);
        JsonKeycloakUserDetails userDetails = viewModel.getKeycloakUserDetails().userDetails;
        if (userDetails.getIdentity() != null && !Arrays.equals(userDetails.getIdentity(), viewModel.getCurrentIdentity().bytesOwnedIdentity)) {
            // an identity is present and does not match ours
            warningTextView.setVisibility(View.VISIBLE);
            if (viewModel.isKeycloakRevocationAllowed()) {
                warningTextView.setText(R.string.text_explanation_warning_identity_creation_keycloak_revocation_needed);
                warningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_warning_outline, 0, 0, 0);
                warningTextView.setBackgroundResource(R.drawable.background_warning_message);
                forceDisabled = false;
            } else {
                warningTextView.setText(R.string.text_explanation_warning_binding_keycloak_revocation_impossible);
                warningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_error_outline, 0, 0, 0);
                warningTextView.setBackgroundResource(R.drawable.background_error_message);
                forceDisabled = true;
            }
        } else {
            warningTextView.setVisibility(View.GONE);
            forceDisabled = false;
        }

        detailsViewModel.getValid().observe(getViewLifecycleOwner(), validStatus -> bindButton.setEnabled(validStatus != null && validStatus != OwnedIdentityDetailsViewModel.ValidStatus.INVALID && !forceDisabled));
    }


    private void bindIdentity() {
        forceDisabled = true;
        spinnerGroup.setVisibility(View.VISIBLE);
        bindButton.setEnabled(false);

        App.runThread(() -> {
            OwnedIdentity ownedIdentity = viewModel.getCurrentIdentity();
            if (ownedIdentity == null) {
                activity.finish();
                return;
            }

            ObvIdentity obvIdentity;
            try {
                obvIdentity = AppSingleton.getEngine().getOwnedIdentity(ownedIdentity.bytesOwnedIdentity);
            } catch (Exception e) {
                obvIdentity = null;
            }

            if (obvIdentity != null) {
                KeycloakManager.getInstance().registerKeycloakManagedIdentity(
                        obvIdentity,
                        viewModel.getKeycloakServerUrl(),
                        viewModel.getKeycloakClientId(),
                        viewModel.getKeycloakClientSecret(),
                        viewModel.getKeycloakJwks(),
                        viewModel.getKeycloakUserDetails().signatureKey,
                        viewModel.getKeycloakSerializedAuthState(),
                        null,
                        0,
                        0,
                        true);

                KeycloakManager.getInstance().uploadOwnIdentity(
                        ownedIdentity.bytesOwnedIdentity,
                        new KeycloakManager.KeycloakCallback<Void>() {
                            @Override
                            public void success(Void result) {
                                ObvIdentity newObvIdentity = AppSingleton.getEngine().bindOwnedIdentityToKeycloak(
                                        ownedIdentity.bytesOwnedIdentity,
                                        new ObvKeycloakState(
                                                viewModel.getKeycloakServerUrl(),
                                                viewModel.getKeycloakClientId(),
                                                viewModel.getKeycloakClientSecret(),
                                                viewModel.getKeycloakJwks(),
                                                viewModel.getKeycloakUserDetails().signatureKey,
                                                viewModel.getKeycloakSerializedAuthState(),
                                                null, 0, 0),
                                        viewModel.getKeycloakUserDetails().userDetails.getId());

                                if (newObvIdentity != null) {
                                    App.toast(R.string.toast_message_keycloak_bind_successful, Toast.LENGTH_SHORT);
                                    activity.finish();
                                } else {
                                    failed(-980);
                                }
                            }

                            @Override
                            public void failed(int rfc) {
                                KeycloakManager.getInstance().unregisterKeycloakManagedIdentity(ownedIdentity.bytesOwnedIdentity);

                                switch (rfc) {
                                    case KeycloakTasks.RFC_IDENTITY_REVOKED: {
                                        forceDisabled = true;
                                        activity.runOnUiThread(() -> {
                                            spinnerGroup.setVisibility(View.GONE);

                                            warningTextView.setVisibility(View.VISIBLE);
                                            warningTextView.setText(R.string.text_explanation_warning_olvid_id_revoked_on_keycloak);
                                            warningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_error_outline, 0, 0, 0);
                                            warningTextView.setBackgroundResource(R.drawable.background_error_message);
                                            bindButton.setEnabled(false);
                                        });
                                        break;
                                    }
                                    case -980:
                                    case KeycloakTasks.RFC_IDENTITY_NOT_MANAGED:
                                    case KeycloakTasks.RFC_AUTHENTICATION_REQUIRED:
                                    default: {
                                        App.toast(R.string.toast_message_unable_to_keycloak_bind, Toast.LENGTH_SHORT);
                                        forceDisabled = false;
                                        activity.runOnUiThread(() -> {
                                            spinnerGroup.setVisibility(View.GONE);
                                            bindButton.setEnabled(true);
                                        });
                                        break;
                                    }
                                }
                            }
                        }
                );
            } else {
                App.toast(R.string.toast_message_unable_to_keycloak_bind, Toast.LENGTH_SHORT);
                
                forceDisabled = false;
                activity.runOnUiThread(() -> {
                    spinnerGroup.setVisibility(View.GONE);
                    bindButton.setEnabled(true);
                });
            }
        });
    }
}
