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

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation.findNavController
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.identities.ObvIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsFragment
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.INVALID
import io.olvid.messenger.settings.SettingsActivity.Companion.isHiddenProfileClosePolicyDefined
import java.util.UUID

class IdentityCreationFragment : Fragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()
    private val detailsViewModel: OwnedIdentityDetailsViewModel by activityViewModels()
    private var specialOptionsGroup: View? = null
    private var specialOptionsTextView: TextView? = null
    private lateinit var activity: FragmentActivity

    private var generateIdButton: Button? = null
    private var focusHugger: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =
            inflater.inflate(R.layout.fragment_onboarding_identity_creation, container, false)
        activity = requireActivity()

        val ownedIdentityDetailsFragment = OwnedIdentityDetailsFragment()
        ownedIdentityDetailsFragment.setShowNicknameAndHidden(!viewModel.isFirstIdentity)
        if (viewModel.keycloakSerializedAuthState != null) {
            // we are in the keycloak settings --> load the details and lock the fragment edit
            ownedIdentityDetailsFragment.setLockedUserDetails(
                viewModel.keycloakUserDetails,
                false
            )
            activity.onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        remove()
                        viewModel.keycloakSerializedAuthState = null
                        if (viewModel.isConfiguredFromMdm) {
                            findNavController(rootView).popBackStack()
                        } else {
                            if (!findNavController(rootView).popBackStack(
                                    R.id.keycloak_selection,
                                    true
                                )
                            ) {
                                findNavController(rootView).popBackStack()
                            }
                        }
                    }
                })
        } else if (!viewModel.isFirstIdentity) {
            activity.onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        remove()
                        activity.finish()
                    }
                })
        }

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(
            R.id.fragment_identity_details_placeholder,
            ownedIdentityDetailsFragment
        )
        transaction.commit()

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        detailsViewModel.bytesOwnedIdentity = ByteArray(0)
        detailsViewModel.isIdentityInactive = false
        view.findViewById<View>(R.id.top_bar)?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.updatePadding(top = insets.top)
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = view.context.resources.getDimensionPixelSize(R.dimen.tab_bar_size) + insets.top
                }
                WindowInsetsCompat.CONSUMED
            }
        }

        view.findViewById<View>(R.id.back_button)
            .setOnClickListener { activity.onBackPressed() }

        focusHugger = view.findViewById(R.id.focus_hugger)

        specialOptionsGroup = view.findViewById(R.id.special_options_group)
        specialOptionsTextView = view.findViewById(R.id.special_options_text_view)

        generateIdButton = view.findViewById(R.id.button_generate_id)
        if (viewModel.isFirstIdentity) {
            generateIdButton?.setText(R.string.button_label_generate_my_id)
        } else {
            generateIdButton?.setText(R.string.button_label_generate_new_id)
        }
        generateIdButton?.setOnClickListener { v: View ->
            val imm =
                v.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(v.windowToken, 0)
            createIdentity()
        }

        val warningTextView = view.findViewById<TextView>(R.id.keycloak_warning_textview)
        warningTextView.visibility = View.GONE
        viewModel.setForceDisabled(false)

        if (viewModel.keycloakSerializedAuthState != null) {
            val explanationTextView = view.findViewById<TextView>(R.id.explanation_textview)
            explanationTextView.setText(R.string.explanation_choose_display_name_keycloak)

            if (viewModel.keycloakUserDetails?.identity != null) {
                warningTextView.visibility = View.VISIBLE
                if (viewModel.isKeycloakRevocationAllowed) {
                    warningTextView.setText(R.string.text_explanation_warning_identity_creation_keycloak_revocation_needed)
                    warningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_warning_outline,
                        0,
                        0,
                        0
                    )
                    warningTextView.setBackgroundResource(R.drawable.background_warning_message)
                } else {
                    warningTextView.setText(R.string.text_explanation_warning_identity_creation_keycloak_revocation_impossible)
                    warningTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_error_outline,
                        0,
                        0,
                        0
                    )
                    warningTextView.setBackgroundResource(R.drawable.background_error_message)
                    viewModel.setForceDisabled(true)
                }
            }
        }

        class GenerateButtonObserver {
            var ready: Boolean = false
            var forceDisabled: Boolean = false

            fun validStatusChanged(validStatus: ValidStatus?) {
                this.ready = validStatus != null && validStatus != INVALID
                enable()
            }

            fun forceDisabledChanged(forceDisabled: Boolean?) {
                this.forceDisabled = forceDisabled != null && forceDisabled
                enable()
            }

            fun enable() {
                if (generateIdButton != null) {
                    generateIdButton!!.isEnabled = ready && !forceDisabled
                }
            }
        }

        val generateButtonObserver = GenerateButtonObserver()

        detailsViewModel.valid.observe(
            viewLifecycleOwner
        ) { validStatus: ValidStatus? -> generateButtonObserver.validStatusChanged(validStatus) }
        viewModel.forceDisabled.observe(
            viewLifecycleOwner
        ) { forceDisabled: Boolean? ->
            generateButtonObserver.forceDisabledChanged(
                forceDisabled
            )
        }
    }

    override fun onStart() {
        super.onStart()
        var hasOption = false
        val sb = StringBuilder()
        if (viewModel.keycloakSerializedAuthState != null) {
            hasOption = true
            sb.append(
                getString(
                    R.string.text_option_identity_provider,
                    viewModel.keycloakServer
                )
            )
        }
        if (BuildConfig.SERVER_NAME != viewModel.server) {
            if (hasOption) {
                sb.append("\n")
            }
            hasOption = true
            sb.append(getString(R.string.text_option_server, viewModel.server))
        }
        if (viewModel.apiKey != null) {
            if (hasOption) {
                sb.append("\n")
            }
            hasOption = true
            sb.append(
                getString(
                    R.string.text_option_license_code, Logger.getUuidString(
                        viewModel.apiKey
                    )
                )
            )
        }

        if (hasOption) {
            specialOptionsGroup!!.visibility = View.VISIBLE
            specialOptionsTextView!!.text = sb.toString()
        } else {
            specialOptionsGroup!!.visibility = View.GONE
        }
        if (focusHugger != null) {
            focusHugger!!.requestFocus()
        }
    }


    private fun createIdentity() {
        val server = viewModel.server
        if (server == null || server.isEmpty()) {
            return
        }
        var apiKey = viewModel.apiKey
        if (apiKey == null && BuildConfig.HARDCODED_API_KEY != null) {
            apiKey = UUID.fromString(BuildConfig.HARDCODED_API_KEY)
        }

        val identityDetails = detailsViewModel.jsonIdentityDetails
        val absolutePhotoUrl = detailsViewModel.absolutePhotoUrl
        if (identityDetails == null || identityDetails.isEmpty) {
            return
        }

        if (viewModel.forceDisabled.value != null && !viewModel.forceDisabled.value!!) {
            viewModel.setForceDisabled(true)
            AppSingleton.getInstance().generateIdentity(
                server,
                apiKey,
                identityDetails,
                absolutePhotoUrl,
                detailsViewModel.nickname,
                detailsViewModel.password,
                detailsViewModel.salt,
                viewModel.keycloakServer,
                viewModel.keycloakClientId,
                viewModel.keycloakClientSecret,
                viewModel.keycloakJwks,
                viewModel.keycloakSignatureKey,
                viewModel.keycloakSerializedAuthState,
                viewModel.isKeycloakTransferRestricted,
                { obvIdentity: ObvIdentity -> this.identityCreatedCallback(obvIdentity) },
                { viewModel.setForceDisabled(false) })
        }
    }

    private fun identityCreatedCallback(obvIdentity: ObvIdentity) {
        if (viewModel.keycloakSerializedAuthState != null) {
            KeycloakManager.getInstance().uploadOwnIdentity(
                obvIdentity.bytesIdentity,
                object : KeycloakCallback<Void?> {
                    override fun success(result: Void?) {
                        if (detailsViewModel.password != null && !isHiddenProfileClosePolicyDefined) {
                            App.openAppDialogConfigureHiddenProfileClosePolicy()
                        }
                        App.showMainActivityTab(activity, MainActivity.DISCUSSIONS_TAB)
                        activity.finish()
                    }

                    override fun failed(rfc: Int) {
                        activity.runOnUiThread {
                            val builder =
                                SecureAlertDialogBuilder(
                                    activity, R.style.CustomAlertDialog
                                )
                                    .setTitle(R.string.dialog_title_identity_provider_error)
                                    .setMessage(R.string.dialog_message_failed_to_upload_identity_to_keycloak)
                                    .setPositiveButton(R.string.button_label_ok, null)
                                    .setOnDismissListener { dialog: DialogInterface? ->
                                        App.showMainActivityTab(
                                            activity,
                                            MainActivity.DISCUSSIONS_TAB
                                        )
                                        activity.finish()
                                    }
                            builder.create().show()
                        }
                    }
                }
            )
        } else {
            if (detailsViewModel.password != null && !isHiddenProfileClosePolicyDefined) {
                App.openAppDialogConfigureHiddenProfileClosePolicy()
            }
            App.showMainActivityTab(activity, MainActivity.DISCUSSIONS_TAB)
            activity.finish()
        }
    }
}
