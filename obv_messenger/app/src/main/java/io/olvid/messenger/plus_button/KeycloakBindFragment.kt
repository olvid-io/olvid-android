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
package io.olvid.messenger.plus_button

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import io.olvid.engine.engine.types.identities.ObvKeycloakState
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.openid.KeycloakTasks
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsFragment
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.INVALID

class KeycloakBindFragment : Fragment() {
    private val viewModel: PlusButtonViewModel by activityViewModels()
    private val detailsViewModel: OwnedIdentityDetailsViewModel by activityViewModels()
    private lateinit var activity: FragmentActivity

    private var bindButton: Button? = null
    private var spinnerGroup: View? = null
    private var forceDisabled = false
    private var warningTextView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =
            inflater.inflate(R.layout.fragment_plus_button_keycloak_bind, container, false)
        activity = requireActivity()

        if (viewModel.keycloakSerializedAuthState == null || viewModel.keycloakJwks == null || viewModel.keycloakServerUrl == null) {
            activity.finish()
            return rootView
        }


        val ownedIdentityDetailsFragment = OwnedIdentityDetailsFragment()
        ownedIdentityDetailsFragment.setLockedUserDetails(
            viewModel.keycloakUserDetails?.userDetails,
            true
        )

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
        if (viewModel.currentIdentity == null) {
            activity.finish()
            return
        }

        detailsViewModel.bytesOwnedIdentity = viewModel.currentIdentity?.bytesOwnedIdentity
        detailsViewModel.absolutePhotoUrl = App.absolutePathFromRelative(
            viewModel.currentIdentity?.photoUrl
        )

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

        bindButton = view.findViewById(R.id.button_keycloak_bind)
        bindButton?.setOnClickListener { bindIdentity() }
        spinnerGroup = view.findViewById(R.id.spinner)

        warningTextView = view.findViewById(R.id.keycloak_warning_textview)
        val userDetails = viewModel.keycloakUserDetails?.userDetails
        if (userDetails?.identity != null && !userDetails.identity.contentEquals(viewModel.currentIdentity?.bytesOwnedIdentity)) {
            // an identity is present and does not match ours
            warningTextView?.visibility = View.VISIBLE
            if (viewModel.isKeycloakRevocationAllowed) {
                warningTextView?.setText(R.string.text_explanation_warning_identity_creation_keycloak_revocation_needed)
                warningTextView?.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_warning_outline,
                    0,
                    0,
                    0
                )
                warningTextView?.setBackgroundResource(R.drawable.background_warning_message)
                forceDisabled = false
            } else {
                warningTextView?.setText(R.string.text_explanation_warning_binding_keycloak_revocation_impossible)
                warningTextView?.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_error_outline,
                    0,
                    0,
                    0
                )
                warningTextView?.setBackgroundResource(R.drawable.background_error_message)
                forceDisabled = true
            }
        } else {
            warningTextView?.visibility = View.GONE
            forceDisabled = false
        }

        detailsViewModel.valid.observe(
            viewLifecycleOwner
        ) { validStatus: ValidStatus? -> bindButton?.isEnabled = validStatus != null && validStatus != INVALID && !forceDisabled }
    }


    private fun bindIdentity() {
        // just in case, block rebind of transfer restricted id. This is normally already blocked at the previous step
        if (viewModel.currentIdentity?.let {
                KeycloakManager.isOwnedIdentityTransferRestricted(it.bytesOwnedIdentity)
            } != false) {
            return
        }

        forceDisabled = true
        spinnerGroup?.visibility = View.VISIBLE
        bindButton?.isEnabled = false

        App.runThread {
            val ownedIdentity =
                viewModel.currentIdentity
            if (ownedIdentity == null) {
                activity.finish()
                return@runThread
            }
            val obvIdentity = try {
                AppSingleton.getEngine().getOwnedIdentity(ownedIdentity.bytesOwnedIdentity)
            } catch (e: Exception) {
                null
            }
            if (obvIdentity != null) {
                KeycloakManager.getInstance().registerKeycloakManagedIdentity(
                    obvIdentity,
                    viewModel.keycloakServerUrl,
                    viewModel.keycloakClientId,
                    viewModel.keycloakClientSecret,
                    viewModel.keycloakJwks,
                    viewModel.keycloakUserDetails?.signatureKey,
                    viewModel.keycloakSerializedAuthState,
                    viewModel.isKeycloakTransferRestricted,
                    null,
                    0,
                    0,
                    true
                )

                KeycloakManager.getInstance().uploadOwnIdentity(
                    ownedIdentity.bytesOwnedIdentity,
                    object : KeycloakCallback<Void?> {
                        override fun success(result: Void?) {
                            val newObvIdentity =
                                AppSingleton.getEngine().bindOwnedIdentityToKeycloak(
                                    ownedIdentity.bytesOwnedIdentity,
                                    ObvKeycloakState(
                                        viewModel.keycloakServerUrl,
                                        viewModel.keycloakClientId,
                                        viewModel.keycloakClientSecret,
                                        viewModel.keycloakJwks,
                                        viewModel.keycloakUserDetails?.signatureKey,
                                        viewModel.keycloakSerializedAuthState,
                                        viewModel.isKeycloakTransferRestricted,
                                        null,
                                        0,
                                        0),
                                    viewModel.keycloakUserDetails?.userDetails?.id
                                )

                            if (newObvIdentity != null) {
                                App.toast(
                                    R.string.toast_message_keycloak_bind_successful,
                                    Toast.LENGTH_SHORT
                                )
                                activity.finish()
                            } else {
                                failed(-980)
                            }
                        }

                        override fun failed(rfc: Int) {
                            KeycloakManager.getInstance()
                                .unregisterKeycloakManagedIdentity(ownedIdentity.bytesOwnedIdentity)

                            when (rfc) {
                                KeycloakTasks.RFC_IDENTITY_REVOKED -> {
                                    forceDisabled = true
                                    activity.runOnUiThread {
                                        spinnerGroup?.visibility = View.GONE
                                        warningTextView?.visibility = View.VISIBLE
                                        warningTextView?.setText(R.string.text_explanation_warning_olvid_id_revoked_on_keycloak)
                                        warningTextView?.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                            R.drawable.ic_error_outline,
                                            0,
                                            0,
                                            0
                                        )
                                        warningTextView?.setBackgroundResource(R.drawable.background_error_message)
                                        bindButton?.isEnabled = false
                                    }
                                }

                                -980, KeycloakTasks.RFC_IDENTITY_NOT_MANAGED, KeycloakTasks.RFC_AUTHENTICATION_REQUIRED -> {
                                    App.toast(
                                        R.string.toast_message_unable_to_keycloak_bind,
                                        Toast.LENGTH_SHORT
                                    )
                                    forceDisabled = false
                                    activity.runOnUiThread {
                                        spinnerGroup?.visibility = View.GONE
                                        bindButton?.isEnabled = true
                                    }
                                }

                                else -> {
                                    App.toast(
                                        R.string.toast_message_unable_to_keycloak_bind,
                                        Toast.LENGTH_SHORT
                                    )
                                    forceDisabled = false
                                    activity.runOnUiThread {
                                        spinnerGroup?.visibility = View.GONE
                                        bindButton?.isEnabled = true
                                    }
                                }
                            }
                        }
                    }
                )
            } else {
                App.toast(
                    R.string.toast_message_unable_to_keycloak_bind,
                    Toast.LENGTH_SHORT
                )

                forceDisabled = false
                activity.runOnUiThread {
                    spinnerGroup?.visibility = View.GONE
                    bindButton?.isEnabled = true
                }
            }
        }
    }
}
