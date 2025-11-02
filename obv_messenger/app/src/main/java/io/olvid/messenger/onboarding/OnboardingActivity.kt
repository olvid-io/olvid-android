/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
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
package io.olvid.messenger.onboarding

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.navigation.fragment.NavHostFragment
import io.olvid.messenger.App
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.services.MDMConfigurationSingleton
import io.olvid.messenger.settings.SettingsActivity.Companion.overrideContextScales

class OnboardingActivity : AppCompatActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(overrideContextScales(baseContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        App.setAppDialogsBlocked(true)

        window?.apply {
            setFlags(
                LayoutParams.FLAG_SECURE,
                LayoutParams.FLAG_SECURE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setHideOverlayWindows(true)
            }
        }

        viewModel.setForceDisabled(false)
        // set the server to the hardcoded server
        if (viewModel.server == null) {
            viewModel.validateServer(BuildConfig.SERVER_NAME)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        setContentView(R.layout.activity_onboarding)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment?

        if (navHostFragment == null) {
            finish()
            return
        }

        findViewById<ConstraintLayout>(R.id.root_constraint_layout)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime())
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    updateMargins(bottom = insets.bottom)
                }
                windowInsets
            }
        }


        // Remove the empty start fragment
        // We had to add this fragment to avoid the scan fragment requesting camera permission even though it was popped from the back stack
        navHostFragment.navController.popBackStack(R.id.empty, true)


        if (savedInstanceState != null && savedInstanceState.getBoolean(
                ALREADY_CREATED_BUNDLE_EXTRA,
                false
            )
        ) {
            intent = null
        }

        val intent = intent
        if (intent != null) {
            val firstIdentity = intent.getBooleanExtra(FIRST_ID_INTENT_EXTRA, false)
            viewModel.isFirstIdentity = firstIdentity

            //////////////////////////
            // check for managed configuration from MDM
            //////////////////////////
            try {
                val mdmKeycloakConfigurationUri =
                    MDMConfigurationSingleton.getKeycloakConfigurationUri()
                if (mdmKeycloakConfigurationUri != null) {
                    // this configuration is only used when creating the first profile
                    if (firstIdentity) {
                        val matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(
                            mdmKeycloakConfigurationUri
                        )
                        if (matcher.find() && viewModel.parseScannedConfigurationUri(matcher.group(2)) && viewModel.keycloakServer != null) {
                            viewModel.isConfiguredFromMdm = true
                            viewModel.isDeepLinked = true
                            navHostFragment.navController.navigate(R.id.keycloak_selection)
                            return
                        } else {
                            viewModel.keycloakServer = null
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (intent.hasExtra(LINK_URI_INTENT_EXTRA)) {
                val uri = intent.getStringExtra(LINK_URI_INTENT_EXTRA)
                if (uri != null) {
                    val matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(uri)
                    if (matcher.find()) {
                        if (viewModel.parseScannedConfigurationUri(matcher.group(2))) {
                            viewModel.isDeepLinked = true
                            if (viewModel.keycloakServer != null) {
                                navHostFragment.navController.navigate(R.id.keycloak_selection)
                            } else {
                                navHostFragment.navController.navigate(R.id.identity_creation_options)
                            }
                            return
                        }
                        App.toast(
                            R.string.toast_message_invalid_configuration_link,
                            Toast.LENGTH_SHORT
                        )
                        return
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
                App.toast(R.string.toast_message_invalid_link, Toast.LENGTH_SHORT)
            }

            if (intent.getBooleanExtra(PROFILE_CREATION, false)) {
                navHostFragment.navController.navigate(R.id.identity_creation)
                return
            }
        }

        navHostFragment.navController.navigate(R.id.scan_fragment)
    }

    override fun onDestroy() {
        super.onDestroy()

        App.setAppDialogsBlocked(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ALREADY_CREATED_BUNDLE_EXTRA, true)
    }

    companion object {
        const val LINK_URI_INTENT_EXTRA: String = "link_uri"
        const val FIRST_ID_INTENT_EXTRA: String = "first_id"
        const val PROFILE_CREATION: String = "profile_creation"

        const val ALREADY_CREATED_BUNDLE_EXTRA: String = "already_created"
    }
}
