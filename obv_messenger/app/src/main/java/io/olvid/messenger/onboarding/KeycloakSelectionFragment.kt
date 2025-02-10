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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.text.Editable
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation.findNavController
import com.google.android.material.textfield.TextInputEditText
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.CHECKING
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.INVALID
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.UNCHECKED
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.VALID
import io.olvid.messenger.openid.KeycloakAuthenticationStartFragment
import io.olvid.messenger.openid.KeycloakBrowserChooserDialog
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.openid.KeycloakTasks
import io.olvid.messenger.openid.KeycloakTasks.AuthenticateCallback
import io.olvid.messenger.openid.KeycloakTasks.DiscoverKeycloakServerCallback
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff
import net.openid.appauth.AuthState
import org.jose4j.jwk.JsonWebKeySet


class KeycloakSelectionFragment : Fragment(), OnClickListener,
    EngineNotificationListener {
    private val viewModel: OnboardingViewModel by activityViewModels()
    private lateinit var activity: FragmentActivity
    private var started = false

    private var keycloakServerEditText: TextInputEditText? = null
    private var keycloakClientIdEditText: TextInputEditText? = null
    private var keycloakClientSecretEditText: TextInputEditText? = null

    private var validateButton: Button? = null
    private var authenticateButton: Button? = null
    private var authenticationBrowserButton: ImageButton? = null

    private var serverStatusImageView: ImageView? = null
    private var serverStatusSpinner: View? = null
    private var focusHugger: View? = null

    private var manualViewGroup: ViewGroup? = null
    private var autoViewGroup: ViewGroup? = null
    private var autoLogoImageView: ImageView? = null
    private var autoExplanationTextView: TextView? = null

    private val keycloakAuthenticationStartFragment by lazy { KeycloakAuthenticationStartFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity()
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED,
            this
        )
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS,
            this
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =
            inflater.inflate(R.layout.fragment_onboarding_keycloak_selection, container, false)
        activity.onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.keycloakServer = null
                    viewModel.isDeepLinked = false
                    remove()
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            })
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

        focusHugger = view.findViewById(R.id.focus_hugger)


        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(
            R.id.authentication_fragment_placeholder,
            keycloakAuthenticationStartFragment
        )
        transaction.commit()

        val backButton = view.findViewById<ImageView>(R.id.back_button)
        backButton.setOnClickListener(this)

        manualViewGroup = view.findViewById(R.id.keycloak_manual_configuration)
        autoViewGroup = view.findViewById(R.id.keycloak_auto_configuration)
        autoLogoImageView = view.findViewById(R.id.keycloak_auto_successful_image_view)
        autoExplanationTextView = view.findViewById(R.id.keycloak_explanation_text_view)

        view.findViewById<View>(R.id.keycloak_manual_configuration_switch)
            .setOnClickListener {
                autoViewGroup?.animate()?.alpha(0f)?.setDuration(200)?.setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            autoViewGroup?.visibility = View.GONE
                            manualViewGroup?.visibility = View.VISIBLE
                            manualViewGroup?.alpha = 0f
                            manualViewGroup?.animate()?.alpha(1f)?.setDuration(200)
                        }
                    })
            }

        validateButton = view.findViewById(R.id.button_validate_configuration)
        validateButton?.setOnClickListener(this)

        authenticateButton = view.findViewById(R.id.button_authenticate)
        authenticateButton?.setOnClickListener(this)

        authenticationBrowserButton = view.findViewById(R.id.button_authentication_browser)
        authenticationBrowserButton?.setOnClickListener(this)

        keycloakServerEditText = view.findViewById(R.id.keycloak_server_edit_text)
        keycloakClientIdEditText = view.findViewById(R.id.keycloak_client_id_edit_text)
        keycloakClientSecretEditText = view.findViewById(R.id.keycloak_client_secret_edit_text)

        keycloakServerEditText?.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable?) {
                if (started) {
                    viewModel.keycloakServer = s?.toString()
                }
            }
        })

        keycloakClientIdEditText?.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable?) {
                if (started) {
                    viewModel.keycloakClientId = s?.toString()
                }
                authenticateButton?.isEnabled = s != null && s.toString().isNotEmpty()
            }
        })

        keycloakClientSecretEditText?.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable?) {
                if (started) {
                    viewModel.keycloakClientSecret = s?.toString()
                }
            }
        })

        view.findViewById<View>(R.id.show_password_button)
            .setOnTouchListener { v: View, event: MotionEvent ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val pos = keycloakClientSecretEditText?.selectionStart
                        keycloakClientSecretEditText?.transformationMethod = null
                        pos?.let { keycloakClientSecretEditText?.setSelection(it) }
                    }

                    MotionEvent.ACTION_UP -> {
                        val pos = keycloakClientSecretEditText?.selectionStart
                        keycloakClientSecretEditText?.transformationMethod =
                            PasswordTransformationMethod()
                        pos?.let { keycloakClientSecretEditText?.setSelection(it) }
                        v.performClick()
                    }
                }
                true
            }

        serverStatusImageView = view.findViewById(R.id.query_server_status)
        serverStatusSpinner = view.findViewById(R.id.query_server_spinner)

        viewModel.keycloakValidatedStatus.observe(
            viewLifecycleOwner
        ) { validatedStatus: VALIDATED_STATUS? ->
            when (validatedStatus) {
                UNCHECKED -> {
                    validateButton?.visibility = View.VISIBLE
                    validateButton?.isEnabled = true
                    authenticateButton?.visibility = View.GONE
                    authenticationBrowserButton?.visibility = View.GONE
                    serverStatusSpinner?.visibility = View.GONE
                    serverStatusImageView?.visibility = View.GONE
                }

                CHECKING -> {
                    validateButton?.visibility = View.VISIBLE
                    validateButton?.isEnabled = false
                    authenticateButton?.visibility = View.GONE
                    authenticationBrowserButton?.visibility = View.GONE
                    serverStatusSpinner?.visibility = View.VISIBLE
                    serverStatusImageView?.visibility = View.GONE
                }

                VALID -> {
                    validateButton?.visibility = View.GONE
                    authenticateButton?.visibility = View.VISIBLE
                    authenticationBrowserButton?.visibility = View.VISIBLE
                    serverStatusSpinner?.visibility = View.GONE
                    serverStatusImageView?.visibility = View.VISIBLE
                    serverStatusImageView?.setImageResource(R.drawable.ic_ok_green)
                }

                INVALID -> {
                    validateButton?.visibility = View.VISIBLE
                    validateButton?.isEnabled = true
                    authenticateButton?.visibility = View.GONE
                    authenticationBrowserButton?.visibility = View.GONE
                    serverStatusSpinner?.visibility = View.GONE
                    serverStatusImageView?.visibility = View.VISIBLE
                    serverStatusImageView?.setImageResource(R.drawable.ic_remove)
                }
                else -> {}
            }
        }

        if (viewModel.isConfiguredFromMdm) {
            keycloakServerEditText?.isEnabled = false
            keycloakClientIdEditText?.isEnabled = false
            keycloakClientSecretEditText?.isEnabled = false
            backButton.setImageResource(R.drawable.ic_close_blue_or_white)
        } else {
            keycloakServerEditText?.isEnabled = true
            keycloakClientIdEditText?.isEnabled = true
            keycloakClientSecretEditText?.isEnabled = true
            backButton.setImageResource(R.drawable.ic_arrow_back_blue_or_white)
        }

        if (viewModel.isDeepLinked) {
            manualViewGroup?.visibility = View.GONE
            validateKeycloakServer()
        }
    }

    override fun onStart() {
        super.onStart()
        keycloakServerEditText!!.setText(viewModel.keycloakServer)
        keycloakClientIdEditText!!.setText(viewModel.keycloakClientId)
        keycloakClientSecretEditText!!.setText(viewModel.keycloakClientSecret)
        started = true
        if (focusHugger != null) {
            focusHugger!!.requestFocus()
        }
    }

    override fun onStop() {
        super.onStop()
        started = false
    }

    override fun onDetach() {
        super.onDetach()
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS,
            this
        )
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED,
            this
        )
    }


    private fun validateKeycloakServer() {
        val keycloakServerUrl = viewModel.keycloakServer ?: return

        KeycloakTasks.discoverKeycloakServer(
            keycloakServerUrl,
            object : DiscoverKeycloakServerCallback {
                override fun success(serverUrl: String, authState: AuthState, jwks: JsonWebKeySet) {
                    activity.runOnUiThread {
                        keycloakServerEditText!!.setText(serverUrl)
                        keycloakServerEditText!!.setSelection(serverUrl.length)
                        viewModel.keycloakValidationSuccess(
                            keycloakServerUrl,
                            serverUrl,
                            authState.jsonSerializeString(),
                            jwks
                        )
                        if (manualViewGroup!!.visibility != View.VISIBLE) {
                            if (viewModel.isConfiguredFromMdm) {
                                autoLogoImageView!!.setImageResource(R.drawable.olvid_blue_or_white)
                                autoExplanationTextView!!.setText(R.string.text_explanation_onboarding_keycloak_mdm)
                            } else {
                                autoLogoImageView!!.setImageResource(R.drawable.ic_ok_green)
                                autoExplanationTextView!!.setText(R.string.text_explanation_onboarding_keycloak_parsed_configuration)
                            }
                            autoViewGroup!!.visibility = View.VISIBLE
                        }
                    }
                }

                override fun failed() {
                    activity.runOnUiThread {
                        viewModel.keycloakValidationFailed(keycloakServerUrl)
                        manualViewGroup!!.visibility = View.VISIBLE
                    }
                }
            })
    }


    override fun onClick(v: View) {
        if (v.id == R.id.back_button) {
            val imm =
                activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(v.windowToken, 0)

            activity.onBackPressed()
        } else if (v.id == R.id.button_validate_configuration) {
            if (viewModel.keycloakServer == null) {
                return
            }
            val imm =
                activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(v.windowToken, 0)

            validateKeycloakServer()
        } else if (v.id == R.id.button_authentication_browser) {
            KeycloakBrowserChooserDialog.openBrowserChoiceDialog(v)
        } else if (v.id == R.id.button_authenticate) {
            val imm =
                activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(v.windowToken, 0)
            val serializedAuthState = viewModel.keycloakSerializedAuthState
            val clientId = viewModel.keycloakClientId
            val clientSecret = viewModel.keycloakClientSecret
            if (serializedAuthState != null && clientId != null) {
                keycloakAuthenticationStartFragment.authenticate(
                    serializedAuthState,
                    clientId,
                    clientSecret,
                    object : AuthenticateCallback {
                        override fun success(authState: AuthState) {
                            viewModel.keycloakSerializedAuthState =
                                authState.jsonSerializeString()
                            val keycloakServer = viewModel.keycloakServer
                            val jwks = viewModel.keycloakJwks
                            if (keycloakServer == null || jwks == null) {
                                failed(KeycloakTasks.RFC_USER_NOT_AUTHENTICATED)
                                return
                            }
                            if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                                    View.VISIBLE
                            }
                            if (keycloakAuthenticationStartFragment.authenticationSpinnerText != null) {
                                keycloakAuthenticationStartFragment.authenticationSpinnerText.setText(
                                    R.string.label_retrieving_user_details
                                )
                            }

                            KeycloakTasks.getOwnDetails(
                                activity,
                                keycloakServer,
                                authState,
                                clientSecret,
                                jwks,
                                null,
                                object : KeycloakCallback<Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>> {
                                    override fun success(userDetailsAndApiKeyAndRevocationAllowed: Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>) {
                                        if (userDetailsAndApiKeyAndRevocationAllowed.first == null || userDetailsAndApiKeyAndRevocationAllowed.second == null) {
                                            failed(0)
                                            return
                                        }

                                        val minimumBuildVersion =
                                            if (userDetailsAndApiKeyAndRevocationAllowed.second.minimumBuildVersions != null) userDetailsAndApiKeyAndRevocationAllowed.second.minimumBuildVersions["android"] else null
                                        if (minimumBuildVersion != null && minimumBuildVersion > BuildConfig.VERSION_CODE) {
                                            failed(VERSION_OUTDATED)
                                            return
                                        }

                                        viewModel.keycloakUserDetails =
                                            userDetailsAndApiKeyAndRevocationAllowed.first.userDetails
                                        viewModel.isKeycloakRevocationAllowed =
                                            userDetailsAndApiKeyAndRevocationAllowed.second.revocationAllowed
                                        viewModel.isKeycloakTransferRestricted =
                                            userDetailsAndApiKeyAndRevocationAllowed.second.transferRestricted
                                        viewModel.keycloakSignatureKey =
                                            userDetailsAndApiKeyAndRevocationAllowed.first.signatureKey
                                        viewModel.setApiKey(null)

                                        activity.runOnUiThread {
                                            if (userDetailsAndApiKeyAndRevocationAllowed.first.server != null) {
                                                viewModel.validateServer(userDetailsAndApiKeyAndRevocationAllowed.first.server)
                                                if (keycloakAuthenticationStartFragment.authenticationSpinnerText != null) {
                                                    keycloakAuthenticationStartFragment.authenticationSpinnerText.setText(
                                                        R.string.label_checking_server
                                                    )
                                                }
                                                AppSingleton.getEngine().queryServerWellKnown(
                                                    userDetailsAndApiKeyAndRevocationAllowed.first.server
                                                )
                                            } else {
                                                if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                                    keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                                                        View.GONE
                                                }
                                                findNavController(v).navigate(R.id.action_keycloak_identity_creation)
                                            }
                                        }
                                    }

                                    override fun failed(rfc: Int) {
                                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                            activity.runOnUiThread {
                                                keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                                                    View.GONE
                                            }
                                        }
                                        if (rfc == VERSION_OUTDATED) {
                                            val builder = SecureAlertDialogBuilder(
                                                activity, R.style.CustomAlertDialog
                                            )
                                                .setTitle(R.string.dialog_title_outdated_version)
                                                .setMessage(R.string.explanation_keycloak_olvid_version_outdated)
                                                .setPositiveButton(R.string.button_label_update) { dialog: DialogInterface?, which: Int ->
                                                    val appPackageName = activity.packageName
                                                    try {
                                                        startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW, Uri.parse(
                                                                    "market://details?id=$appPackageName"
                                                                )
                                                            )
                                                        )
                                                    } catch (_: ActivityNotFoundException) {
                                                        try {
                                                            startActivity(
                                                                Intent(
                                                                    Intent.ACTION_VIEW, Uri.parse(
                                                                        "https://play.google.com/store/apps/details?id=$appPackageName"
                                                                    )
                                                                )
                                                            )
                                                        } catch (ee: Exception) {
                                                            ee.printStackTrace()
                                                        }
                                                    }
                                                }
                                                .setNegativeButton(
                                                    R.string.button_label_cancel,
                                                    null
                                                )
                                            activity.runOnUiThread { builder.create().show() }
                                        } else {
                                            App.toast(
                                                R.string.toast_message_unable_to_retrieve_details,
                                                Toast.LENGTH_SHORT,
                                                Gravity.CENTER
                                            )
                                        }
                                    }
                                })
                        }

                        override fun failed(rfc: Int) {}
                    })
            }
        }
    }

    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        when (notificationName) {
            EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED -> {
                val server =
                    userInfo[EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY] as String?
                if (server != null) {
                    viewModel.serverValidationFinished(server, false)
                    activity.runOnUiThread {
                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                            keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                                View.GONE
                        }
                        App.toast(
                            R.string.toast_message_unable_to_connect_to_server,
                            Toast.LENGTH_SHORT,
                            Gravity.CENTER
                        )
                    }
                }
            }

            EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS -> {
                val server =
                    userInfo[EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY] as String?
                if (server != null) {
                    viewModel.serverValidationFinished(server, true)
                    activity.runOnUiThread {
                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                            keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                                View.GONE
                        }
                        findNavController(authenticateButton!!).navigate(R.id.action_keycloak_identity_creation)
                    }
                }
            }
        }
    }

    private var engineNotificationRegistrationNumber: Long = -1
    override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
        this.engineNotificationRegistrationNumber = registrationNumber
    }

    override fun getEngineNotificationListenerRegistrationNumber(): Long {
        return engineNotificationRegistrationNumber
    }

    override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
        return engineNotificationRegistrationNumber != -1L
    }

    companion object {
        private const val VERSION_OUTDATED = 1343657
    }
}
