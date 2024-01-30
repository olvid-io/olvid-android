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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import java.util.regex.Matcher;

import io.olvid.messenger.App;
import io.olvid.messenger.BuildConfig;
import io.olvid.messenger.R;
import io.olvid.messenger.activities.ObvLinkActivity;
import io.olvid.messenger.services.MDMConfigurationSingleton;
import io.olvid.messenger.settings.SettingsActivity;


public class OnboardingActivity extends AppCompatActivity {
    public static final String LINK_URI_INTENT_EXTRA = "link_uri";
    public static final String FIRST_ID_INTENT_EXTRA = "first_id";
    public static final String PROFILE_CREATION = "profile_creation";

    public static final String ALREADY_CREATED_BUNDLE_EXTRA = "already_created";

    @Override
    protected void attachBaseContext(Context baseContext) {
        super.attachBaseContext(SettingsActivity.overrideContextScales(baseContext));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        App.setAppDialogsBlocked(true);

        Window window = getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        OnboardingViewModel viewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);
        viewModel.setForceDisabled(false);
        // set the server to the hardcoded server
        if (viewModel.getServer() == null) {
            viewModel.setServer(BuildConfig.SERVER_NAME);
        }

        setContentView(R.layout.activity_onboarding);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) {
            finish();
            return;
        }

        // Remove the empty start fragment
        // We had to add this fragment to avoid the scan fragment requesting camera permission even though it was poped from the back stack
        navHostFragment.getNavController().popBackStack(R.id.empty, true);


        if (savedInstanceState != null && savedInstanceState.getBoolean(ALREADY_CREATED_BUNDLE_EXTRA, false)) {
            setIntent(null);
        }

        Intent intent = getIntent();
        if (intent != null) {
            boolean firstIdentity = intent.getBooleanExtra(FIRST_ID_INTENT_EXTRA, false);
            viewModel.setFirstIdentity(firstIdentity);

            //////////////////////////
            // check for managed configuration from MDM
            //////////////////////////
            try {
                String mdmKeycloakConfigurationUri = MDMConfigurationSingleton.getKeycloakConfigurationUri();
                if (mdmKeycloakConfigurationUri != null) {
                    // this configuration is only used when creating the first profile
                    if (firstIdentity) {
                        Matcher matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(mdmKeycloakConfigurationUri);
                        if (matcher.find() && viewModel.parseScannedConfigurationUri(matcher.group(2)) && viewModel.getKeycloakServer() != null) {
                            viewModel.setConfiguredFromMdm(true);
                            viewModel.setDeepLinked(true);
                            navHostFragment.getNavController().navigate(R.id.keycloak_selection);
                            return;
                        } else {
                            viewModel.setKeycloakServer(null);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (intent.hasExtra(LINK_URI_INTENT_EXTRA)) {
                String uri = intent.getStringExtra(LINK_URI_INTENT_EXTRA);
                if (uri != null) {
                    Matcher matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(uri);
                    if (matcher.find()) {
                        if (viewModel.parseScannedConfigurationUri(matcher.group(2))) {
                            viewModel.setDeepLinked(true);
                            if (viewModel.getKeycloakServer() != null) {
                                navHostFragment.getNavController().navigate(R.id.keycloak_selection);
                            } else {
                                navHostFragment.getNavController().navigate(R.id.identity_creation_options);
                            }
                            return;
                        }
                        App.toast(R.string.toast_message_invalid_configuration_link, Toast.LENGTH_SHORT);
                        return;
                    }
                    // we disable this for now, we'll see if we re-implement this in the OnboardingFlowActivity
                    /* else if (ObvLinkActivity.INVITATION_PATTERN.matcher(uri).find()) {
                        ObvUrlIdentity obvUrlIdentity = ObvUrlIdentity.fromUrlRepresentation(uri);
                        if (obvUrlIdentity != null) {
                            AlertDialog.Builder builder = new SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_invitation_link_clicked)
                                    .setPositiveButton(R.string.button_label_proceed, (DialogInterface dialog, int which) -> {
                                        if (dialog instanceof AlertDialog) {
                                            ((AlertDialog) dialog).setOnDismissListener(null);
                                        }
                                        viewModel.setInvitationLink(uri);
                                    })
                                    .setNegativeButton(R.string.button_label_cancel, null)
                                    .setOnDismissListener((DialogInterface dialog) -> {
                                        if (firstIdentity) {
                                            finishAndRemoveTask();
                                        } else {
                                            finish();
                                        }
                                    });
                            if (firstIdentity) {
                                builder.setMessage(getString(R.string.dialog_message_invitation_link_clicked_onboarding, obvUrlIdentity.displayName));
                            } else {
                                builder.setMessage(getString(R.string.dialog_message_invitation_link_clicked_add_profile, obvUrlIdentity.displayName));
                                navHostFragment.getNavController().popBackStack(R.id.welcome_screen, true);
                                navHostFragment.getNavController().navigate(R.id.new_profile);
                            }
                            builder.create().show();
                            return;
                        }
                    }*/
                }
                App.toast(R.string.toast_message_invalid_link, Toast.LENGTH_SHORT);
            }

            if (intent.getBooleanExtra(PROFILE_CREATION, false)) {
                navHostFragment.getNavController().navigate(R.id.identity_creation);
                return;
            }
        }

        navHostFragment.getNavController().navigate(R.id.scan_fragment);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        App.setAppDialogsBlocked(false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ALREADY_CREATED_BUNDLE_EXTRA, true);
    }
}
