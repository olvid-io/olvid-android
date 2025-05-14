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
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation.findNavController
import io.olvid.engine.datatypes.ObvBase64
import io.olvid.engine.engine.types.EngineAPI.ApiKeyPermission
import io.olvid.engine.engine.types.EngineAPI.ApiKeyStatus
import io.olvid.engine.engine.types.EngineAPI.ApiKeyStatus.LICENSES_EXHAUSTED
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.RegisterApiKeyResult.FAILED
import io.olvid.engine.engine.types.RegisterApiKeyResult.INVALID_KEY
import io.olvid.engine.engine.types.RegisterApiKeyResult.SUCCESS
import io.olvid.engine.engine.types.RegisterApiKeyResult.WAIT_FOR_SERVER_SESSION
import io.olvid.engine.engine.types.identities.ObvKeycloakState
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.activities.ObvLinkActivity
import io.olvid.messenger.customClasses.ConfigurationKeycloakPojo
import io.olvid.messenger.customClasses.ConfigurationPojo
import io.olvid.messenger.customClasses.ConfigurationSettingsPojo
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.fragments.SubscriptionStatusFragment
import io.olvid.messenger.openid.KeycloakAuthenticationStartFragment
import io.olvid.messenger.openid.KeycloakBrowserChooserDialog
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.openid.KeycloakTasks
import io.olvid.messenger.openid.KeycloakTasks.AuthenticateCallback
import io.olvid.messenger.openid.KeycloakTasks.DiscoverKeycloakServerCallback
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff
import net.openid.appauth.AuthState
import org.jose4j.jwk.JsonWebKeySet
import java.util.UUID

class ConfigurationScannedFragment : Fragment(), OnClickListener,
    EngineNotificationListener {
    private lateinit var activity: AppCompatActivity
    private val viewModel: PlusButtonViewModel by activityViewModels()

    private var configurationPojo: ConfigurationPojo? = null
    private var configurationApiKeyUuid: UUID? = null
    private var statusQueried: Boolean = false
    private var callbackCalled: Boolean = false

    private var titleTextView: TextView? = null

    private var invalidCardView: CardView? = null
    private var invalidMalformedLayout: LinearLayout? = null
    private var invalidMalformedExplanationTextView: TextView? = null
    private var invalidBadServerLayout: LinearLayout? = null
    private var licenseServerUrlTextView: TextView? = null
    private var ownServerUrlTextView: TextView? = null
    private var okButton: Button? = null

    private var newCardView: CardView? = null
    private var cancelButton: Button? = null
    private var activateButton: Button? = null
    private var activationSpinner: ProgressBar? = null

    private var currentCardView: CardView? = null

    private var newLicenseStatusPlaceholder: FrameLayout? = null

    private var settingsCardView: CardView? = null
    private var settingsDetailsTextView: TextView? = null
    private var settingsUpdateButton: Button? = null
    private var settingsCancelButton: Button? = null

    private var keycloakCardView: CardView? = null
    private var keycloakExplanationTextView: TextView? = null
    private var keycloakDetailsTextView: TextView? = null
    private var keycloakAuthenticateButton: Button? = null
    private var keycloakAuthenticationBrowserButton: ImageButton? = null
    private var keycloakCancelButton: Button? = null
    private val keycloakAuthenticationStartFragment by lazy { KeycloakAuthenticationStartFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as AppCompatActivity
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS,
            this
        )
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.API_KEY_STATUS_QUERY_FAILED,
            this
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =
            inflater.inflate(R.layout.fragment_plus_button_configuration_scanned, container, false)

        if (!viewModel.isDeepLinked) {
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        viewModel.setKeycloakData(null, null, null, null, null)
                        findNavController(rootView).popBackStack()
                    }
                })
        }

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



        view.findViewById<View>(R.id.back_button).setOnClickListener(this)

        titleTextView = view.findViewById(R.id.title_text_view)

        invalidCardView = view.findViewById(R.id.invalid_license_card_view)
        invalidMalformedLayout = view.findViewById(R.id.bad_configuration_linear_layout)
        invalidMalformedExplanationTextView =
            view.findViewById(R.id.bad_configuration_explanation_text_view)
        invalidBadServerLayout = view.findViewById(R.id.bad_server_linear_layout)
        licenseServerUrlTextView = view.findViewById(R.id.license_server_url_text_view)
        ownServerUrlTextView = view.findViewById(R.id.owned_identity_server_url_text_view)
        okButton = view.findViewById(R.id.ok_button)

        newCardView = view.findViewById(R.id.new_license_card_view)
        cancelButton = view.findViewById(R.id.cancel_button)
        activateButton = view.findViewById(R.id.activate_button)
        activationSpinner = view.findViewById(R.id.activation_spinner)

        currentCardView = view.findViewById(R.id.current_license_card_view)
        newLicenseStatusPlaceholder = view.findViewById(R.id.new_license_status_placeholder)

        settingsCardView = view.findViewById(R.id.settings_update_card_view)
        settingsDetailsTextView = view.findViewById(R.id.settings_update_details_text_view)
        settingsUpdateButton = view.findViewById(R.id.settings_update_button)
        settingsCancelButton = view.findViewById(R.id.settings_cancel_button)

        keycloakCardView = view.findViewById(R.id.keycloak_update_card_view)
        keycloakExplanationTextView = view.findViewById(R.id.keycloak_explanation_text_view)
        keycloakDetailsTextView = view.findViewById(R.id.keycloak_update_details_text_view)
        keycloakAuthenticateButton = view.findViewById(R.id.keycloak_authenticate_button)
        keycloakAuthenticationBrowserButton = view.findViewById(R.id.button_authentication_browser)
        keycloakCancelButton = view.findViewById(R.id.keycloak_cancel_button)

        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(
            R.id.authentication_fragment_placeholder,
            keycloakAuthenticationStartFragment
        )
        transaction.commit()



        okButton?.setOnClickListener(this)
        cancelButton?.setOnClickListener(this)
        activateButton?.setOnClickListener(this)
        settingsUpdateButton?.setOnClickListener(this)
        settingsCancelButton?.setOnClickListener(this)
        keycloakAuthenticateButton?.setOnClickListener(this)
        keycloakAuthenticationBrowserButton?.setOnClickListener(this)
        keycloakCancelButton?.setOnClickListener(this)

        val uri = viewModel.scannedUri
        if (uri == null) {
            activity.finish()
            return
        }

        var configurationPojo: ConfigurationPojo? = null
        val matcher = ObvLinkActivity.CONFIGURATION_PATTERN.matcher(uri)
        if (matcher.find()) {
            try {
                configurationPojo = AppSingleton.getJsonObjectMapper().readValue(
                    ObvBase64.decode(matcher.group(2)),
                    ConfigurationPojo::class.java
                )
            } catch (e: Exception) {
                // nothing to do
                e.printStackTrace()
            }
        }
        if (configurationPojo == null) {
            activity.finish()
            return
        }

        this.configurationPojo = configurationPojo
        if (configurationPojo.server != null && configurationPojo.apikey != null) {
            displayLicense(viewModel.currentIdentity, this.configurationPojo!!)
        } else if (configurationPojo.settings != null) {
            displaySettings(this.configurationPojo!!.settings)
        } else if (configurationPojo.keycloak != null) {
            displayKeycloak(this.configurationPojo!!.keycloak)
        } else {
            activity.finish()
        }
    }

    override fun onDetach() {
        super.onDetach()
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS,
            this
        )
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.API_KEY_STATUS_QUERY_FAILED,
            this
        )
    }

    private fun displayLicense(
        ownedIdentity: OwnedIdentity?,
        configurationPojo: ConfigurationPojo
    ) {
        // set correct title
        titleTextView?.setText(R.string.activity_title_license_activation)

        if (ownedIdentity == null) {
            activity.finish()
            return
        }

        if (ownedIdentity.keycloakManaged) {
            invalidCardView?.visibility = View.VISIBLE
            invalidMalformedLayout?.visibility = View.VISIBLE
            invalidMalformedExplanationTextView?.setText(R.string.text_explanation_keycloak_license_activation_impossible)
            return
        }

        if (!ownedIdentity.active) {
            invalidCardView?.visibility = View.VISIBLE
            invalidMalformedLayout?.visibility = View.VISIBLE
            invalidMalformedExplanationTextView?.setText(R.string.text_explanation_inactive_identity_activation_link)
            return
        }

        if (configurationPojo.server == null || configurationPojo.apikey == null) {
            invalidCardView?.visibility = View.VISIBLE
            invalidMalformedLayout?.visibility = View.VISIBLE
            invalidMalformedExplanationTextView?.setText(R.string.text_explanation_malformed_activation_link)
            return
        }

        try {
            configurationApiKeyUuid = UUID.fromString(configurationPojo.apikey)
        } catch (e: Exception) {
            invalidCardView?.visibility = View.VISIBLE
            invalidMalformedLayout?.visibility = View.VISIBLE
            invalidMalformedExplanationTextView?.setText(R.string.text_explanation_malformed_activation_link)
            return
        }

        val ownServer =
            AppSingleton.getEngine().getServerOfIdentity(ownedIdentity.bytesOwnedIdentity)
        if (ownServer == null) {
            activity.finish()
            return
        }

        if (configurationPojo.server != ownServer) {
            invalidCardView?.visibility = View.VISIBLE
            invalidBadServerLayout?.visibility = View.VISIBLE
            licenseServerUrlTextView?.text = configurationPojo.server
            ownServerUrlTextView?.text = ownServer
            return
        }

        // show current status
        currentCardView?.visibility = View.VISIBLE
        val currentSubscriptionStatusFragment = SubscriptionStatusFragment.newInstance(
            ownedIdentity.bytesOwnedIdentity,
            ownedIdentity.getApiKeyStatus(),
            ownedIdentity.apiKeyExpirationTimestamp,
            ownedIdentity.getApiKeyPermissions(),
            false,
            false,
            AppSingleton.getOtherProfileHasCallsPermission()
        )
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(
            R.id.current_license_status_placeholder,
            currentSubscriptionStatusFragment
        )
        transaction.commit()

        // wait for new status from engine
        newCardView?.visibility = View.VISIBLE
        activateButton?.isEnabled = false

        // query new engine for status
        statusQueried = true
        Handler(Looper.getMainLooper()).postDelayed({
            if (callbackCalled) {
                return@postDelayed
            }
            statusQueryFailed()
        }, 5000)
        AppSingleton.getEngine()
            .queryApiKeyStatus(ownedIdentity.bytesOwnedIdentity, configurationApiKeyUuid)
    }

    private fun displaySettings(settingsPojo: ConfigurationSettingsPojo) {
        // set correct title
        titleTextView?.setText(R.string.activity_title_settings_update)

        settingsCardView?.visibility = View.VISIBLE
        try {
            settingsDetailsTextView?.text = settingsPojo.prettyPrint(activity)
        } catch (e: Exception) {
            e.printStackTrace()
            App.toast(R.string.toast_message_error_parsing_settings_update_link, Toast.LENGTH_SHORT)
            activity.onBackPressed()
        }
    }

    private fun updateSettings() {
        configurationPojo?.settings?.toBackupPojo()?.restore()
    }

    private fun displayKeycloak(keycloakPojo: ConfigurationKeycloakPojo) {
        // set correct title
        titleTextView?.setText(R.string.activity_title_identity_provider)

        App.runThread {
            val alreadyManaged: Boolean
            val sameServer: Boolean

            // check if current identity is already managed by keycloak (and if yes, by the same server)
            val currentIdentity =
                viewModel.currentIdentity
            if (currentIdentity == null) {
                activity.finish()
                return@runThread
            }
            viewModel.currentIdentityServer =
                AppSingleton.getEngine().getServerOfIdentity(currentIdentity.bytesOwnedIdentity)

            if (currentIdentity.keycloakManaged) {
                alreadyManaged = true
                var keycloakState: ObvKeycloakState? = null
                try {
                    keycloakState = AppSingleton.getEngine()
                        .getOwnedIdentityKeycloakState(currentIdentity.bytesOwnedIdentity)
                } catch (e: Exception) {
                    // nothing
                }
                // we have the same server
                sameServer =
                    keycloakState != null && keycloakState.keycloakServer.startsWith(keycloakPojo.server)
            } else {
                sameServer = false
                alreadyManaged = false
            }
            activity.runOnUiThread {
                // set message text
                keycloakCardView?.visibility = View.VISIBLE
                keycloakAuthenticateButton?.isEnabled = false
                keycloakAuthenticationBrowserButton?.isEnabled = false
                if (alreadyManaged) {
                    if (sameServer) {
                        keycloakExplanationTextView?.setText(R.string.explanation_keycloak_update_same_server)
                    } else {
                        keycloakExplanationTextView?.setText(R.string.explanation_keycloak_update_change_server)
                        discoverKeycloak(keycloakPojo)
                    }
                } else {
                    keycloakExplanationTextView?.setText(R.string.explanation_keycloak_update_new)
                    discoverKeycloak(keycloakPojo)
                }
                keycloakDetailsTextView?.text =
                    getString(R.string.text_option_identity_provider, keycloakPojo.server)
            }
        }
    }

    private fun discoverKeycloak(keycloakPojo: ConfigurationKeycloakPojo) {
        KeycloakTasks.discoverKeycloakServer(
            keycloakPojo.server,
            object : DiscoverKeycloakServerCallback {
                override fun success(serverUrl: String, authState: AuthState, jwks: JsonWebKeySet) {
                    activity.runOnUiThread {
                        keycloakAuthenticateButton?.isEnabled = true
                        keycloakAuthenticationBrowserButton?.isEnabled = true
                    }
                    viewModel.setKeycloakData(
                        serverUrl,
                        authState.jsonSerializeString(),
                        jwks,
                        keycloakPojo.clientId,
                        keycloakPojo.clientSecret
                    )
                }

                override fun failed() {
                    activity.runOnUiThread { keycloakExplanationTextView?.setText(R.string.explanation_keycloak_unable_to_contact_server) }
                }
            })
    }

    private fun authenticateKeycloak() {
        if (viewModel.keycloakSerializedAuthState == null || viewModel.keycloakJwks == null || viewModel.keycloakServerUrl == null) {
            return
        }
        // check if owned identity is already bound and transfer is restricted
        if (viewModel.currentIdentity?.let {
                KeycloakManager.isOwnedIdentityTransferRestricted(it.bytesOwnedIdentity)
        } == true) {
            val builder = SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                .setTitle(R.string.dialog_title_rebind_keycloak_restricted)
                .setMessage(R.string.dialog_message_rebind_keycloak_restricted)
                .setPositiveButton(R.string.button_label_ok, null)
            builder.create().show()
            return
        }

        keycloakAuthenticationStartFragment.authenticate(
            viewModel.keycloakSerializedAuthState!!,
            viewModel.keycloakClientId!!,
            viewModel.keycloakClientSecret,
            object : AuthenticateCallback {
                override fun success(authState: AuthState) {
                    viewModel.keycloakSerializedAuthState = authState.jsonSerializeString()
                    if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                        keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                            View.VISIBLE
                    }
                    if (keycloakAuthenticationStartFragment.authenticationSpinnerText != null) {
                        keycloakAuthenticationStartFragment.authenticationSpinnerText.setText(R.string.label_retrieving_user_details)
                    }

                    KeycloakTasks.getOwnDetails(
                        activity,
                        viewModel.keycloakServerUrl!!,
                        authState,
                        viewModel.keycloakClientSecret,
                        viewModel.keycloakJwks!!,
                        null,
                        object :
                            KeycloakCallback<Pair<KeycloakUserDetailsAndStuff?, KeycloakServerRevocationsAndStuff?>> {
                            override fun success(result: Pair<KeycloakUserDetailsAndStuff?, KeycloakServerRevocationsAndStuff?>) {
                                val keycloakUserDetailsAndStuff = result.first
                                val revocationAllowed =
                                    result.second != null && result.second?.revocationAllowed == true
                                val minimumBuildVersion =
                                    if (result.second != null && result.second?.minimumBuildVersions != null) result.second!!.minimumBuildVersions["android"] else null

                                if (keycloakUserDetailsAndStuff == null || keycloakUserDetailsAndStuff.server != viewModel.currentIdentityServer) {
                                    activity.runOnUiThread {
                                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                            keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                                                View.GONE
                                        }
                                        keycloakAuthenticateButton?.isEnabled = false
                                        keycloakAuthenticationBrowserButton?.isEnabled = false
                                        keycloakExplanationTextView?.setText(R.string.explanation_keycloak_update_bad_server)
                                    }
                                    return
                                } else if (minimumBuildVersion != null && minimumBuildVersion > BuildConfig.VERSION_CODE) {
                                    activity.runOnUiThread {
                                        if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                            keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                                                View.GONE
                                        }
                                        keycloakAuthenticateButton?.isEnabled = false
                                        keycloakAuthenticationBrowserButton?.isEnabled = false
                                        keycloakExplanationTextView?.setText(R.string.explanation_keycloak_olvid_version_outdated)
                                    }
                                    return
                                }
                                viewModel.keycloakUserDetails = keycloakUserDetailsAndStuff
                                viewModel.isKeycloakRevocationAllowed = revocationAllowed

                                activity.runOnUiThread {
                                    if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                        keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                                            View.GONE
                                    }
                                    keycloakAuthenticateButton?.let { findNavController(it).navigate(R.id.action_keycloak_bind) }
                                }
                            }

                            override fun failed(rfc: Int) {
                                if (keycloakAuthenticationStartFragment.authenticationSpinnerGroup != null) {
                                    activity.runOnUiThread {
                                        keycloakAuthenticationStartFragment.authenticationSpinnerGroup.visibility =
                                            View.GONE
                                    }
                                }
                                App.toast(
                                    R.string.toast_message_unable_to_retrieve_details,
                                    Toast.LENGTH_SHORT,
                                    Gravity.CENTER
                                )
                            }
                        })
                }

                override fun failed(rfc: Int) {
                    // do nothing, the user may try to authenticate again
                }
            })
    }

    override fun onClick(v: View) {
        if (viewModel.currentIdentity == null) {
            return
        }
        val id = v.id
        when (id) {
            R.id.ok_button, R.id.cancel_button, R.id.settings_cancel_button, R.id.back_button, R.id.keycloak_cancel_button -> {
                activity.onBackPressed()
            }
            R.id.activate_button -> {
                activateButton?.isEnabled = false
                activationSpinner?.visibility = View.VISIBLE
                App.runThread {
                    when (AppSingleton.getEngine().registerOwnedIdentityApiKeyOnServer(
                        viewModel.currentIdentity?.bytesOwnedIdentity, configurationApiKeyUuid
                    )) {
                        SUCCESS -> activity.finish()
                        INVALID_KEY -> Handler(Looper.getMainLooper())
                            .post {
                                activationSpinner?.visibility = View.GONE
                                App.toast(
                                    R.string.toast_message_license_rejected_by_server,
                                    Toast.LENGTH_LONG
                                )
                            }

                        FAILED, WAIT_FOR_SERVER_SESSION, null -> Handler(
                            Looper.getMainLooper()
                        ).post {
                            activateButton?.isEnabled = true
                            activationSpinner?.visibility = View.GONE
                            App.toast(
                                R.string.toast_message_error_retry,
                                Toast.LENGTH_LONG
                            )
                        }
                    }
                }
            }
            R.id.settings_update_button -> {
                updateSettings()
                activity.finish()
            }
            R.id.keycloak_authenticate_button -> {
                authenticateKeycloak()
            }
            R.id.button_authentication_browser -> {
                KeycloakBrowserChooserDialog.openBrowserChoiceDialog(v)
            }
        }
    }

    // to be called on main thread
    private fun statusQueryFailed() {
        val spinner =
            newLicenseStatusPlaceholder?.findViewById<View>(R.id.query_license_status_spinner)
        if (spinner != null) {
            newLicenseStatusPlaceholder?.removeView(spinner)
        }
        val queryLicenseTextView =
            newLicenseStatusPlaceholder?.findViewById<TextView>(R.id.query_license_status_text_view)
        queryLicenseTextView?.setText(R.string.label_unable_to_check_license_status)
        queryLicenseTextView?.setTextColor(ContextCompat.getColor(activity, R.color.red))

        activateButton?.isEnabled = true
    }

    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        when (notificationName) {
            EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS -> {
                if (!statusQueried) {
                    return
                }
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val apiKey =
                    userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY] as UUID?
                if (viewModel.currentIdentity == null || !viewModel.currentIdentity?.bytesOwnedIdentity.contentEquals(
                        bytesOwnedIdentity
                    ) || configurationApiKeyUuid != apiKey
                ) {
                    // notification for another query... ignore it
                    return
                } else {
                    callbackCalled = true
                }
                val apiKeyStatus =
                    userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_STATUS_KEY] as ApiKeyStatus?
                val permissions =
                    userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_PERMISSIONS_KEY] as List<ApiKeyPermission>?
                val apiKeyExpirationTimestamp =
                    if (userInfo.containsKey(EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY)) {
                        userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_EXPIRATION_TIMESTAMP_KEY] as Long
                    } else {
                        null
                    }

                Handler(Looper.getMainLooper()).post {
                    newLicenseStatusPlaceholder?.removeAllViews()
                    val newSubscriptionStatusFragment =
                        SubscriptionStatusFragment.newInstance(
                            bytesOwnedIdentity,
                            apiKeyStatus,
                            apiKeyExpirationTimestamp,
                            permissions,
                            true,
                            false,
                            AppSingleton.getOtherProfileHasCallsPermission()
                        )
                    val transaction =
                        childFragmentManager.beginTransaction()
                    transaction.replace(
                        R.id.new_license_status_placeholder,
                        newSubscriptionStatusFragment
                    )
                    transaction.commit()
                    activateButton?.isEnabled =
                        apiKeyStatus != LICENSES_EXHAUSTED
                }
            }

            EngineNotifications.API_KEY_STATUS_QUERY_FAILED -> {
                if (!statusQueried) {
                    return
                }
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.API_KEY_STATUS_QUERY_FAILED_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val apiKey =
                    userInfo[EngineNotifications.API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY] as UUID?
                if (viewModel.currentIdentity == null || !viewModel.currentIdentity?.bytesOwnedIdentity.contentEquals(
                        bytesOwnedIdentity
                    ) || configurationApiKeyUuid != apiKey
                ) {
                    // notification for another query... ignore it
                    return
                } else {
                    callbackCalled = true
                }
                Handler(Looper.getMainLooper()).post { this.statusQueryFailed() }
            }
        }
    }

    private var engineNotificationRegistrationNumber: Long? = null

    override fun setEngineNotificationListenerRegistrationNumber(registrationNumber: Long) {
        engineNotificationRegistrationNumber = registrationNumber
    }

    override fun getEngineNotificationListenerRegistrationNumber(): Long {
        return engineNotificationRegistrationNumber ?: 0
    }

    override fun hasEngineNotificationListenerRegistrationNumber(): Boolean {
        return engineNotificationRegistrationNumber != null
    }
}
