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

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import java.util.UUID;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsFragment;
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel;
import io.olvid.messenger.plus_button.PlusButtonActivity;
import io.olvid.messenger.settings.SettingsActivity;

public class IdentityCreationFragment extends Fragment {
    private OnboardingViewModel viewModel;
    private OwnedIdentityDetailsViewModel detailsViewModel;
    private View specialOptionsGroup;
    private TextView specialOptionsTextView;
    private FragmentActivity activity;

    private Button generateIdButton;
    private View focusHugger;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_onboarding_identity_creation, container, false);
        activity = requireActivity();
        viewModel = new ViewModelProvider(activity).get(OnboardingViewModel.class);

        OwnedIdentityDetailsFragment ownedIdentityDetailsFragment = new OwnedIdentityDetailsFragment();
        ownedIdentityDetailsFragment.setShowNicknameAndHidden(!viewModel.isFirstIdentity());
        if (viewModel.getKeycloakSerializedAuthState() != null) {
            // we are in the keycloak settings --> load the details and lock the fragment edit
            ownedIdentityDetailsFragment.setLockedUserDetails(viewModel.getKeycloakUserDetails(), false);
            activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    remove();
                    viewModel.setKeycloakSerializedAuthState(null);
                    if (viewModel.isConfiguredFromMdm()) {
                        Navigation.findNavController(rootView).popBackStack();
                    } else {
                        if (!Navigation.findNavController(rootView).popBackStack(R.id.keycloak_selection, true)) {
                            Navigation.findNavController(rootView).popBackStack();
                        }
                    }
                }
            });
        }

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_identity_details_placeholder, ownedIdentityDetailsFragment);
        transaction.commit();

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        detailsViewModel = new ViewModelProvider(activity).get(OwnedIdentityDetailsViewModel.class);
        detailsViewModel.setBytesOwnedIdentity(new byte[0]);
        detailsViewModel.setIdentityInactive(false);

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

        focusHugger = view.findViewById(R.id.focus_hugger);

        specialOptionsGroup = view.findViewById(R.id.special_options_group);
        specialOptionsTextView = view.findViewById(R.id.special_options_text_view);

        generateIdButton = view.findViewById(R.id.button_generate_id);
        if (viewModel.isFirstIdentity()) {
            generateIdButton.setText(R.string.button_label_generate_my_id);
        } else {
            generateIdButton.setText(R.string.button_label_generate_new_id);
        }
        generateIdButton.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            createIdentity();
        });

        TextView warningTextView = view.findViewById(R.id.keycloak_warning_textview);
        warningTextView.setVisibility(View.GONE);
        viewModel.setForceDisabled(false);

        if (viewModel.getKeycloakSerializedAuthState() != null) {
            TextView explanationTextView = view.findViewById(R.id.explanation_textview);
            explanationTextView.setText(R.string.explanation_choose_display_name_keycloak);

            if (viewModel.getKeycloakUserDetails().getIdentity() != null) {
                warningTextView.setVisibility(View.VISIBLE);
                if (viewModel.isKeycloakRevocationAllowed()) {
                    warningTextView.setText(R.string.text_explanation_warning_identity_creation_keycloak_revocation_needed);
                    warningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_warning_outline, 0, 0, 0);
                    warningTextView.setBackgroundResource(R.drawable.background_warning_message);
                } else {
                    warningTextView.setText(R.string.text_explanation_warning_identity_creation_keycloak_revocation_impossible);
                    warningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_error_outline, 0, 0, 0);
                    warningTextView.setBackgroundResource(R.drawable.background_error_message);
                    viewModel.setForceDisabled(true);
                }
            }
        }

        class GenerateButtonObserver {
            boolean ready = false;
            boolean forceDisabled = false;

            public void validStatusChanged(OwnedIdentityDetailsViewModel.ValidStatus validStatus) {
                this.ready = validStatus != null && validStatus != OwnedIdentityDetailsViewModel.ValidStatus.INVALID;
                enable();
            }

            public void forceDisabledChanged(Boolean forceDisabled) {
                this.forceDisabled = forceDisabled != null && forceDisabled;
                enable();
            }

            void enable() {
                if (generateIdButton != null) {
                    generateIdButton.setEnabled(ready && !forceDisabled);
                }
            }
        }
        GenerateButtonObserver generateButtonObserver = new GenerateButtonObserver();

        detailsViewModel.getValid().observe(getViewLifecycleOwner(), generateButtonObserver::validStatusChanged);
        viewModel.getForceDisabled().observe(getViewLifecycleOwner(), generateButtonObserver::forceDisabledChanged);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (viewModel != null) {
            boolean hasOption = false;
            StringBuilder sb = new StringBuilder();
            if (viewModel.getKeycloakSerializedAuthState() != null) {
                hasOption = true;
                sb.append(getString(R.string.text_option_identity_provider, viewModel.getKeycloakServer()));
            }
            if (!BuildConfig.SERVER_NAME.equals(viewModel.getServer())) {
                if (hasOption) {
                    sb.append("\n");
                }
                hasOption = true;
                sb.append(getString(R.string.text_option_server, viewModel.getServer()));
            }
            if (viewModel.getApiKey() != null) {
                if (hasOption) {
                    sb.append("\n");
                }
                hasOption = true;
                sb.append(getString(R.string.text_option_license_code, Logger.getUuidString(viewModel.getApiKey())));
            }

            if (hasOption) {
                specialOptionsGroup.setVisibility(View.VISIBLE);
                specialOptionsTextView.setText(sb.toString());
            } else {
                specialOptionsGroup.setVisibility(View.GONE);
            }
        }
        if (focusHugger != null) {
            focusHugger.requestFocus();
        }
    }


    private void createIdentity() {
        String server = viewModel.getServer();
        if (server == null || server.length() == 0) {
            return;
        }
        UUID apiKey = viewModel.getApiKey();
        if (apiKey == null) {
            apiKey = UUID.fromString(BuildConfig.HARDCODED_API_KEY);
        }

        JsonIdentityDetails identityDetails = detailsViewModel.getJsonIdentityDetails();
        String absolutePhotoUrl = detailsViewModel.getAbsolutePhotoUrl();
        if (identityDetails == null || identityDetails.isEmpty()) {
            return;
        }

        if (viewModel.getForceDisabled().getValue() != null && !viewModel.getForceDisabled().getValue()) {
            viewModel.setForceDisabled(true);
            AppSingleton.getInstance().generateIdentity(
                    server,
                    apiKey,
                    identityDetails,
                    absolutePhotoUrl,
                    detailsViewModel.getNickname(),
                    detailsViewModel.getPassword(),
                    detailsViewModel.getSalt(),
                    viewModel.getKeycloakServer(),
                    viewModel.getKeycloakClientId(),
                    viewModel.getKeycloakClientSecret(),
                    viewModel.getKeycloakJwks(),
                    viewModel.getKeycloakSignatureKey(),
                    viewModel.getKeycloakSerializedAuthState(),
                    this::identityCreatedCallback,
                    () -> viewModel.setForceDisabled(false));
        }
    }

    private void identityCreatedCallback(ObvIdentity obvIdentity) {
        if (viewModel.getKeycloakSerializedAuthState() != null) {
            KeycloakManager.getInstance().uploadOwnIdentity(
                    obvIdentity.getBytesIdentity(),
                    new KeycloakManager.KeycloakCallback<Void>() {
                        @Override
                        public void success(Void result) {
                            if (detailsViewModel.getPassword() != null && !SettingsActivity.isHiddenProfileClosePolicyDefined()) {
                                App.openAppDialogConfigureHiddenProfileClosePolicy();
                            }
                            if (viewModel.getInvitationLink() == null) {
                                App.openCurrentOwnedIdentityDetails(activity);
                            } else {
                                Intent linkIntent = new Intent(App.getContext(), MainActivity.class);
                                linkIntent.setAction(MainActivity.FORWARD_ACTION);
                                linkIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, PlusButtonActivity.class.getName());
                                linkIntent.putExtra(PlusButtonActivity.LINK_URI_INTENT_EXTRA, viewModel.getInvitationLink());
                                startActivity(linkIntent);
                            }
                            activity.finish();
                        }

                        @Override
                        public void failed(int rfc) {
                            activity.runOnUiThread(() -> {
                                final AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                                        .setTitle(R.string.dialog_title_identity_provider_error)
                                        .setMessage(R.string.dialog_message_failed_to_upload_identity_to_keycloak)
                                        .setPositiveButton(R.string.button_label_ok, null)
                                        .setOnDismissListener(dialog -> {
                                            App.openCurrentOwnedIdentityDetails(activity);
                                            activity.finish();
                                        });
                                builder.create().show();
                            });
                        }
                    }
            );
        } else {
            if (detailsViewModel.getPassword() != null && !SettingsActivity.isHiddenProfileClosePolicyDefined()) {
                App.openAppDialogConfigureHiddenProfileClosePolicy();
            }
            if (viewModel.getInvitationLink() == null) {
                App.openCurrentOwnedIdentityDetails(activity);
            } else {
                Intent linkIntent = new Intent(App.getContext(), MainActivity.class);
                linkIntent.setAction(MainActivity.FORWARD_ACTION);
                linkIntent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, PlusButtonActivity.class.getName());
                linkIntent.putExtra(PlusButtonActivity.LINK_URI_INTENT_EXTRA, viewModel.getInvitationLink());
                startActivity(linkIntent);
            }
            activity.finish();
        }
    }
}
