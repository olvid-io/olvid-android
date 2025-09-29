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
package io.olvid.messenger.openid

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.openid.KeycloakTasks.AuthenticateCallback
import io.olvid.messenger.openid.KeycloakTasks.AuthenticationLifecycleObserver
import net.openid.appauth.AuthState
import net.openid.appauth.browser.BrowserSelector


class KeycloakAuthenticator(private val activity: ComponentActivity) {
    private val authenticateObserver: AuthenticationLifecycleObserver = AuthenticationLifecycleObserver(activity)

    init {
        activity.lifecycle.addObserver(authenticateObserver)
    }

    fun authenticate(
        serializedAuthState: String,
        clientId: String,
        clientSecret: String?,
        authenticateCallback: AuthenticateCallback
    ) {
        authenticateObserver.setCallback(authenticateCallback)

        val authenticateIntent = Intent(activity, KeycloakAuthenticationActivity::class.java)
        authenticateIntent.action = KeycloakAuthenticationActivity.Companion.AUTHENTICATE_ACTION
        authenticateIntent.putExtra(
            KeycloakAuthenticationActivity.Companion.AUTH_STATE_JSON_INTENT_EXTRA,
            serializedAuthState
        )
        authenticateIntent.putExtra(
            KeycloakAuthenticationActivity.Companion.CLIENT_ID_INTENT_EXTRA,
            clientId
        )
        if (clientSecret != null) {
            authenticateIntent.putExtra(
                KeycloakAuthenticationActivity.Companion.CLIENT_SECRET_INTENT_EXTRA,
                clientSecret
            )
        }
        App.doNotKillActivitiesOnLockOrCloseHiddenProfileOnBackground()
        authenticateObserver.authenticationResultHandler?.launch(authenticateIntent)
    }
}


class KeycloakAuthenticationStartFragment : Fragment() {
    private lateinit var authenticator: KeycloakAuthenticator
    lateinit var authenticationSpinnerGroup: View
    lateinit var authenticationSpinnerText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authenticator = KeycloakAuthenticator(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_keycloak_authentication, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authenticationSpinnerGroup = view.findViewById(R.id.authentication_spinner_group)
        authenticationSpinnerText = view.findViewById(R.id.authentication_spinner_text_view)
    }

    fun authenticate(
        serializedAuthState: String,
        clientId: String,
        clientSecret: String?,
        authenticateCallback: AuthenticateCallback
    ) {
        authenticationSpinnerGroup.visibility = View.VISIBLE
        authenticationSpinnerText.setText(R.string.label_authenticating)

        authenticator.authenticate(serializedAuthState, clientId, clientSecret, object : AuthenticateCallback {
            override fun success(authState: AuthState) {
                requireActivity().runOnUiThread { authenticationSpinnerGroup.visibility = View.GONE }
                authenticateCallback.success(authState)
            }

            override fun failed(rfc: Int) {
                requireActivity().runOnUiThread {
                    authenticationSpinnerGroup.visibility = View.GONE
                    App.toast(
                        R.string.toast_message_authentication_failed,
                        Toast.LENGTH_SHORT,
                        Gravity.CENTER
                    )
                    try {
                        if (rfc == KeycloakTasks.RFC_AUTHENTICATION_ERROR_TIME_OFFSET) {
                            val builder =
                                SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_authentication_failed_time_offset)
                                    .setMessage(
                                        requireActivity().getString(R.string.dialog_message_authentication_failed_time_offset)
                                            .formatMarkdown(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    )
                                    .setNegativeButton(R.string.button_label_ok, null)
                                    .setNeutralButton(
                                        R.string.button_label_clock_settings
                                    ) { dialog: DialogInterface?, which: Int ->
                                        try {
                                            val intent = Intent(Settings.ACTION_DATE_SETTINGS)
                                            startActivity(intent)
                                        } catch (_: Exception) {
                                        }
                                    }
                            builder.create().show()
                        } else if (BrowserSelector.getAllBrowsers(requireActivity()).isEmpty()) {
                            val builder =
                                SecureAlertDialogBuilder(requireActivity(), R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_no_browser_found)
                                    .setMessage(
                                        requireActivity().getString(R.string.dialog_message_no_browser_found)
                                            .formatMarkdown(Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    )
                                    .setNegativeButton(R.string.button_label_ok, null)
                            builder.create().show()
                        }
                    } catch (_: Exception) {
                    }
                }
                authenticateCallback.failed(rfc)
            }
        })
    }
}