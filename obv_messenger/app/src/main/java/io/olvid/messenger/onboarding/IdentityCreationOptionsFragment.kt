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
package io.olvid.messenger.onboarding

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation.findNavController
import com.google.android.material.textfield.TextInputEditText
import io.olvid.engine.engine.types.EngineAPI.ApiKeyPermission
import io.olvid.engine.engine.types.EngineAPI.ApiKeyStatus
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.TextChangeListener
import io.olvid.messenger.fragments.SubscriptionStatusFragment
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.CHECKING
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.INVALID
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.UNCHECKED
import io.olvid.messenger.onboarding.OnboardingViewModel.VALIDATED_STATUS.VALID
import java.util.UUID

class IdentityCreationOptionsFragment : Fragment(), OnClickListener,
    EngineNotificationListener {
    private val viewModel: OnboardingViewModel by activityViewModels()
    private lateinit var activity: FragmentActivity
    private var started = false

    private var serverEditText: TextInputEditText? = null
    private var apiKeyEditText: TextInputEditText? = null

    private var validateServerButton: Button? = null
    private var continueButton: Button? = null

    private var licenseStatusLoader: ViewGroup? = null
    private var licenseStatusSpinner: View? = null
    private var licenseStatusMessage: View? = null
    private var licenseStatusError: View? = null
    private var licenseStatusPlaceholder: ViewGroup? = null

    private var serverStatusImageView: ImageView? = null
    private var serverStatusSpinner: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity()
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS,
            this
        )
        AppSingleton.getEngine().addNotificationListener(
            EngineNotifications.API_KEY_STATUS_QUERY_FAILED,
            this
        )
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
        return inflater.inflate(
            R.layout.fragment_onboarding_identity_creation_options,
            container,
            false
        )
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

        view.findViewById<View>(R.id.focus_hugger).requestFocus()

        continueButton = view.findViewById(R.id.button_continue)
        continueButton?.setOnClickListener(this)

        view.findViewById<View>(R.id.back_button).setOnClickListener(this)

        validateServerButton = view.findViewById(R.id.button_validate_configuration)
        validateServerButton?.setOnClickListener(this)

        serverEditText = view.findViewById(R.id.server_edit_text)
        apiKeyEditText = view.findViewById(R.id.api_key_edit_text)

        serverEditText?.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                if (started) {
                    viewModel.validateServer(s?.toString())
                }
            }
        })
        apiKeyEditText?.addTextChangedListener(object : TextChangeListener() {
            override fun afterTextChanged(s: Editable) {
                if (started) {
                    viewModel.setApiKey(s?.toString())
                }
            }
        })

        licenseStatusLoader = view.findViewById(R.id.license_status_loader)
        licenseStatusSpinner = view.findViewById(R.id.query_license_status_spinner)
        licenseStatusMessage = view.findViewById(R.id.query_license_status_text_view)
        licenseStatusError = view.findViewById(R.id.query_license_status_error_view)
        licenseStatusPlaceholder = view.findViewById(R.id.license_status_placeholder)

        serverStatusImageView = view.findViewById(R.id.query_server_status)
        serverStatusSpinner = view.findViewById(R.id.query_server_spinner)

        viewModel.validatedStatus.observe(
            viewLifecycleOwner
        ) { validatedStatus: Pair<VALIDATED_STATUS, VALIDATED_STATUS> ->
            when (validatedStatus.first) {
                UNCHECKED -> {
                    validateServerButton?.isEnabled = true
                    continueButton?.isEnabled = false
                    serverStatusSpinner?.visibility = View.GONE
                    serverStatusImageView?.visibility = View.GONE
                }

                CHECKING -> {
                    validateServerButton?.isEnabled = false
                    continueButton?.isEnabled = false
                    serverStatusSpinner?.visibility = View.VISIBLE
                    serverStatusImageView?.visibility = View.GONE
                }

                VALID -> {
                    validateServerButton?.isEnabled = validatedStatus.second != VALID && (apiKeyEditText?.getText() != null && apiKeyEditText?.getText()
                        .toString().isNotEmpty())
                    continueButton?.isEnabled = true
                    serverStatusSpinner?.visibility = View.GONE
                    serverStatusImageView?.visibility = View.VISIBLE
                    serverStatusImageView?.setImageResource(R.drawable.ic_ok_green)
                }

                INVALID -> {
                    validateServerButton?.isEnabled = true
                    continueButton?.isEnabled = false
                    serverStatusSpinner?.visibility = View.GONE
                    serverStatusImageView?.visibility = View.VISIBLE
                    serverStatusImageView?.setImageResource(R.drawable.ic_remove)
                }

                else -> {}
            }
            when (validatedStatus.second) {
                UNCHECKED -> {
                    licenseStatusPlaceholder?.visibility = View.GONE
                    licenseStatusLoader?.visibility = View.GONE
                }

                CHECKING -> {
                    licenseStatusPlaceholder?.visibility = View.GONE
                    licenseStatusLoader?.visibility = View.VISIBLE
                    licenseStatusSpinner?.visibility = View.VISIBLE
                    licenseStatusMessage?.visibility = View.VISIBLE
                    licenseStatusError?.visibility = View.GONE
                }

                VALID -> {
                    licenseStatusPlaceholder?.visibility = View.VISIBLE
                    licenseStatusLoader?.visibility = View.GONE
                }

                INVALID -> {
                    licenseStatusPlaceholder?.visibility = View.GONE
                    licenseStatusLoader?.visibility = View.VISIBLE
                    licenseStatusSpinner?.visibility = View.GONE
                    licenseStatusMessage?.visibility = View.GONE
                    licenseStatusError?.visibility = View.VISIBLE
                }
                else -> {}
            }
        }
        if (viewModel.isDeepLinked) {
            viewModel.isDeepLinked = false
            viewModel.checkServerAndApiKey()
        }
    }

    override fun onStart() {
        super.onStart()
        serverEditText!!.setText(viewModel.unvalidatedServer)
        apiKeyEditText!!.setText(viewModel.unformattedApiKey)
        started = true
    }

    override fun onStop() {
        super.onStop()
        started = false
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
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS,
            this
        )
        AppSingleton.getEngine().removeNotificationListener(
            EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED,
            this
        )
    }


    override fun onClick(v: View) {
        when (v.id) {
            R.id.back_button -> {
                activity.onBackPressed()
            }
            R.id.button_continue -> {
                findNavController(v).navigate(R.id.action_identity_creation)
            }
            R.id.button_validate_configuration -> {
                // first hide the keyboard
                val imm =
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.hideSoftInputFromWindow(v.windowToken, 0)

                viewModel.checkServerAndApiKey()
            }
        }
    }

    override fun callback(notificationName: String, userInfo: HashMap<String, Any>) {
        when (notificationName) {
            EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS -> {
                val bytesOwnedIdentity =
                    userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_BYTES_OWNED_IDENTITY_KEY] as ByteArray?
                val apiKey =
                    userInfo[EngineNotifications.API_KEY_STATUS_QUERY_SUCCESS_API_KEY_KEY] as UUID?

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
                if (apiKey != null) {
                    viewModel.apiKeyValidationFinished(apiKey, true)
                    Handler(Looper.getMainLooper()).post {
                        licenseStatusLoader!!.visibility = View.GONE
                        licenseStatusPlaceholder!!.visibility = View.VISIBLE

                        val newSubscriptionStatusFragment =
                            SubscriptionStatusFragment.newInstance(
                                bytesOwnedIdentity,
                                apiKeyStatus,
                                apiKeyExpirationTimestamp,
                                permissions,
                                true,
                                false,
                                false
                            )
                        val transaction =
                            childFragmentManager.beginTransaction()
                        transaction.replace(
                            R.id.license_status_placeholder,
                            newSubscriptionStatusFragment
                        )
                        transaction.commit()
                    }
                }
            }

            EngineNotifications.API_KEY_STATUS_QUERY_FAILED -> {
                val apiKey =
                    userInfo[EngineNotifications.API_KEY_STATUS_QUERY_FAILED_API_KEY_KEY] as UUID?
                if (apiKey != null) {
                    viewModel.apiKeyValidationFinished(apiKey, false)
                    Handler(Looper.getMainLooper()).post {
                        licenseStatusSpinner!!.visibility = View.GONE
                        licenseStatusMessage!!.visibility = View.GONE
                        licenseStatusError!!.visibility = View.VISIBLE
                    }
                }
            }

            EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED -> {
                val server =
                    userInfo[EngineNotifications.WELL_KNOWN_DOWNLOAD_FAILED_SERVER_KEY] as String?
                if (server != null) {
                    viewModel.serverValidationFinished(server, false)
                }
            }

            EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS -> {
                val server =
                    userInfo[EngineNotifications.WELL_KNOWN_DOWNLOAD_SUCCESS_SERVER_KEY] as String?
                if (server != null) {
                    viewModel.serverValidationFinished(server, true)
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
}
