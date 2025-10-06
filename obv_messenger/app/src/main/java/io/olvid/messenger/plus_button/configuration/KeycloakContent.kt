package io.olvid.messenger.plus_button.configuration

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.util.Pair
import io.olvid.engine.engine.types.identities.ObvKeycloakState
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ConfigurationKeycloakPojo
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.openid.KeycloakAuthenticator
import io.olvid.messenger.openid.KeycloakBrowserChooserDialog
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakTasks
import io.olvid.messenger.openid.jsons.KeycloakServerRevocationsAndStuff
import io.olvid.messenger.openid.jsons.KeycloakUserDetailsAndStuff
import io.olvid.messenger.plus_button.PlusButtonViewModel
import net.openid.appauth.AuthState
import net.openid.appauth.browser.BrowserSelector
import org.jose4j.jwk.JsonWebKeySet

@Composable
internal fun KeycloakContent(
    viewModel: PlusButtonViewModel,
    keycloakPojo: ConfigurationKeycloakPojo,
    onCancel: () -> Unit,
    onNavigateToKeycloakBind: () -> Unit,
    authenticator: KeycloakAuthenticator
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var authenticating by remember { mutableStateOf(false) }
    var retrievingDetails by remember { mutableStateOf(false) }
    var showTransferRestrictedDialog by remember { mutableStateOf(false) }
    var showTimeErrorDialog by remember { mutableStateOf(false) }
    var showNoBrowserDialog by remember { mutableStateOf(false) }

    var discovering by remember { mutableStateOf(false) }
    var ownedIdentityAlreadyManaged by remember { mutableStateOf(false) }
    var alreadyBoundOnSameServer by remember { mutableStateOf(false) }
    var errorMessage: String? by remember { mutableStateOf(null) }


    if (showTransferRestrictedDialog) {
        AlertDialog(
            onDismissRequest = { showTransferRestrictedDialog = false },
            title = { Text(stringResource(R.string.dialog_title_rebind_keycloak_restricted)) },
            text = { Text(stringResource(R.string.dialog_message_rebind_keycloak_restricted)) },
            confirmButton = {
                OlvidTextButton(
                    onClick = { showTransferRestrictedDialog = false },
                    text = stringResource(R.string.button_label_ok)
                )
            }
        )
    }

    if (showTimeErrorDialog) {
        AlertDialog(
            onDismissRequest = { showTimeErrorDialog = false },
            title = { Text(stringResource(R.string.dialog_title_authentication_failed_time_offset)) },
            text = { Text(stringResource(R.string.dialog_message_authentication_failed_time_offset)) },
            confirmButton = {
                OlvidTextButton(onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_DATE_SETTINGS)
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                    showTimeErrorDialog = false
                }, text = stringResource(R.string.button_label_clock_settings))
            },
            dismissButton = {
                OlvidTextButton(
                    onClick = { showTimeErrorDialog = false },
                    text = stringResource(R.string.button_label_ok)
                )
            }
        )
    }

    if (showNoBrowserDialog) {
        AlertDialog(
            onDismissRequest = { showNoBrowserDialog = false },
            title = { Text(stringResource(R.string.dialog_title_no_browser_found)) },
            text = { Text(stringResource(R.string.dialog_message_no_browser_found)) },
            confirmButton = {
                OlvidTextButton(
                    onClick = { showNoBrowserDialog = false },
                    text = stringResource(R.string.button_label_ok)
                )
            }
        )
    }


    LaunchedEffect(keycloakPojo, viewModel.currentIdentity) {
        val identity = viewModel.currentIdentity
        if (identity == null) {
            onCancel()
            return@LaunchedEffect
        }
        viewModel.currentIdentityServer =
            AppSingleton.getEngine().getServerOfIdentity(identity.bytesOwnedIdentity)


        ownedIdentityAlreadyManaged = identity.keycloakManaged
        alreadyBoundOnSameServer = if (ownedIdentityAlreadyManaged) {
            var keycloakState: ObvKeycloakState? = null
            try {
                keycloakState = AppSingleton.getEngine()
                    .getOwnedIdentityKeycloakState(identity.bytesOwnedIdentity)
            } catch (_: Exception) {
                // nothing
            }
            keycloakState != null && keycloakState.keycloakServer.startsWith(keycloakPojo.server)
        } else {
            false
        }

        if (!alreadyBoundOnSameServer) {
            discovering = true
            KeycloakTasks.discoverKeycloakServer(
                keycloakPojo.server,
                object : KeycloakTasks.DiscoverKeycloakServerCallback {
                    override fun success(
                        serverUrl: String,
                        authState: AuthState,
                        jwks: JsonWebKeySet
                    ) {
                        discovering = false
                        viewModel.setKeycloakData(
                            serverUrl,
                            authState.jsonSerializeString(),
                            jwks,
                            keycloakPojo.clientId,
                            keycloakPojo.clientSecret
                        )
                    }

                    override fun failed() {
                        discovering = false
                        errorMessage =
                            context.getString(R.string.explanation_keycloak_unable_to_contact_server)
                    }
                }
            )
        } else {
            errorMessage = context.getString(R.string.explanation_keycloak_update_same_server)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.almostWhite),
            contentColor = colorResource(R.color.almostBlack)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (discovering) {
                OlvidCircularProgress(
                    size = 128.dp
                )
            } else if (errorMessage != null) {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = colorResource(R.color.red),
                        style = OlvidTypography.body1,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OlvidTextButton(
                    modifier = Modifier.align(Alignment.End),
                    text = stringResource(R.string.button_label_ok),
                    contentColor = colorResource(R.color.greyTint),
                    onClick = onCancel,
                    large = true,
                )
            } else {
                Text(
                    color = if (ownedIdentityAlreadyManaged)
                        colorResource(R.color.orange)
                    else
                        colorResource(R.color.almostBlack),
                    text = if (ownedIdentityAlreadyManaged)
                        stringResource(R.string.explanation_keycloak_update_change_server)
                    else
                        stringResource(R.string.explanation_keycloak_update_new),
                    style = OlvidTypography.body1
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = context.getString(
                        R.string.text_option_identity_provider,
                        keycloakPojo.server
                    ),
                    style = OlvidTypography.body2,
                )
                Spacer(modifier = Modifier.height(16.dp))
                AnimatedVisibility(visible = authenticating || retrievingDetails) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OlvidCircularProgress()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (retrievingDetails) stringResource(R.string.label_retrieving_user_details)
                            else stringResource(R.string.label_authenticating),
                            style = OlvidTypography.h3,
                            textAlign = TextAlign.Center,
                            color = colorResource(R.color.almostBlack)
                        )
                    }
                }

                OlvidTextButton(
                    modifier = Modifier.align(Alignment.Start),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    text = stringResource(R.string.content_description_authentication_browser_choice),
                    icon = R.drawable.ic_settings,
                    onClick = { KeycloakBrowserChooserDialog.openBrowserChoiceDialog(context) },
                    contentColor = colorResource(R.color.olvid_gradient_light),
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        contentColor = colorResource(R.color.greyTint),
                        onClick = onCancel,
                        large = true,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    OlvidActionButton(
                        text = stringResource(R.string.button_label_authenticate),
                        onClick = {
                            if (viewModel.currentIdentity?.let {
                                    KeycloakManager.isOwnedIdentityTransferRestricted(
                                        it.bytesOwnedIdentity
                                    )
                                } == true) {
                                showTransferRestrictedDialog = true
                                return@OlvidActionButton
                            }

                            if (viewModel.keycloakSerializedAuthState != null && viewModel.keycloakClientId != null) {
                                authenticating = true
                                authenticator.authenticate(
                                    viewModel.keycloakSerializedAuthState!!,
                                    viewModel.keycloakClientId!!,
                                    viewModel.keycloakClientSecret,
                                    object : KeycloakTasks.AuthenticateCallback {
                                        override fun success(authState: AuthState) {
                                            viewModel.keycloakSerializedAuthState =
                                                authState.jsonSerializeString()
                                            authenticating = false
                                            retrievingDetails = true

                                            KeycloakTasks.getOwnDetails(
                                                activity,
                                                viewModel.keycloakServerUrl!!,
                                                authState,
                                                viewModel.keycloakClientSecret,
                                                viewModel.keycloakJwks!!,
                                                null,
                                                object :
                                                    KeycloakManager.KeycloakCallback<Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>?> {
                                                    override fun success(result: Pair<KeycloakUserDetailsAndStuff, KeycloakServerRevocationsAndStuff>?) {
                                                        val keycloakUserDetailsAndStuff = result?.first
                                                        val revocationAllowed = result?.second?.revocationAllowed == true
                                                        val minimumBuildVersion = result?.second?.minimumBuildVersions?.get("android")

                                                        if (keycloakUserDetailsAndStuff == null || keycloakUserDetailsAndStuff.server != viewModel.currentIdentityServer) {
                                                            errorMessage =
                                                                context.getString(R.string.explanation_keycloak_update_bad_server)
                                                            retrievingDetails = false
                                                            return
                                                        } else if (minimumBuildVersion != null && minimumBuildVersion > BuildConfig.VERSION_CODE) {
                                                            errorMessage =
                                                                context.getString(R.string.explanation_keycloak_olvid_version_outdated)
                                                            retrievingDetails = false
                                                            return
                                                        }
                                                        viewModel.keycloakUserDetails =
                                                            keycloakUserDetailsAndStuff
                                                        viewModel.isKeycloakRevocationAllowed =
                                                            revocationAllowed
                                                        retrievingDetails = false
                                                        onNavigateToKeycloakBind()
                                                    }

                                                    override fun failed(rfc: Int) {
                                                        retrievingDetails = false
                                                        App.toast(
                                                            R.string.toast_message_unable_to_retrieve_details,
                                                            Toast.LENGTH_SHORT
                                                        )
                                                    }
                                                })
                                        }

                                        override fun failed(rfc: Int) {
                                            authenticating = false
                                            if (rfc == KeycloakTasks.RFC_AUTHENTICATION_ERROR_TIME_OFFSET) {
                                                showTimeErrorDialog = true
                                            } else if (BrowserSelector.getAllBrowsers(activity)
                                                    .isEmpty()
                                            ) {
                                                showNoBrowserDialog = true
                                            } else {
                                                App.toast(
                                                    R.string.toast_message_authentication_failed,
                                                    Toast.LENGTH_SHORT
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        },
                        enabled = viewModel.keycloakSerializedAuthState != null && !authenticating && !retrievingDetails
                    )
                }
            }
        }
    }
}