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
package io.olvid.messenger.plus_button.configuration

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.olvid.engine.engine.types.identities.ObvKeycloakState
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakTasks
import io.olvid.messenger.plus_button.PlusButtonViewModel
import kotlinx.coroutines.launch

@Composable
fun KeycloakBindScreen(
    viewModel: PlusButtonViewModel,
    onBindSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var binding by remember { mutableStateOf(false) }
    var warningText by remember { mutableStateOf<String?>(null) }
    var warningIsError by remember { mutableStateOf(false) }
    var forceDisabled by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.keycloakUserDetails, viewModel.currentIdentity) {
        val userDetails = viewModel.keycloakUserDetails?.userDetails
        val currentIdentity = viewModel.currentIdentity

        if (userDetails?.identity != null && !userDetails.identity.contentEquals(currentIdentity?.bytesOwnedIdentity)) {
            // An identity is present and does not match ours
            if (viewModel.isKeycloakRevocationAllowed) {
                warningText =
                    context.getString(R.string.text_explanation_warning_identity_creation_keycloak_revocation_needed)
                warningIsError = false
                forceDisabled = false
            } else {
                warningText =
                    context.getString(R.string.text_explanation_warning_binding_keycloak_revocation_impossible)
                warningIsError = true
                forceDisabled = true
            }
        } else {
            warningText = null
            forceDisabled = false
        }
    }

    fun bindIdentity() {
        // just in case, block rebind of transfer restricted id. This is normally already blocked at the previous step
        if (viewModel.currentIdentity?.let { KeycloakManager.isOwnedIdentityTransferRestricted(it.bytesOwnedIdentity) } != false) {
            return
        }

        binding = true
        forceDisabled = true

        coroutineScope.launch {
            val ownedIdentity = viewModel.currentIdentity
            if (ownedIdentity == null) {
                onBindSuccess() // or onBack()
                return@launch
            }
            val obvIdentity = runCatching {
                AppSingleton.getEngine().getOwnedIdentity(ownedIdentity.bytesOwnedIdentity)
            }.getOrNull()

            if (obvIdentity != null && viewModel.keycloakClientId != null && viewModel.keycloakServerUrl != null) {
                KeycloakManager.registerKeycloakManagedIdentity(
                    obvIdentity,
                    viewModel.keycloakServerUrl!!,
                    viewModel.keycloakClientId!!,
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
                KeycloakManager.uploadOwnIdentity(
                    ownedIdentity.bytesOwnedIdentity,
                    object : KeycloakManager.KeycloakCallback<Void?> {
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
                                        0
                                    ),
                                    viewModel.keycloakUserDetails?.userDetails?.id
                                )

                            if (newObvIdentity != null) {
                                App.toast(
                                    R.string.toast_message_keycloak_bind_successful,
                                    Toast.LENGTH_SHORT
                                )
                                onBindSuccess()
                            } else {
                                failed(-980) // Custom error code for local failure
                            }
                        }

                        override fun failed(rfc: Int) {
                            KeycloakManager.unregisterKeycloakManagedIdentity(ownedIdentity.bytesOwnedIdentity)
                            binding = false

                            when (rfc) {
                                KeycloakTasks.RFC_IDENTITY_REVOKED -> {
                                    forceDisabled = true
                                    warningText =
                                        context.getString(R.string.text_explanation_warning_olvid_id_revoked_on_keycloak)
                                    warningIsError = true
                                }

                                -980, KeycloakTasks.RFC_IDENTITY_NOT_MANAGED, KeycloakTasks.RFC_AUTHENTICATION_REQUIRED -> {
                                    App.toast(
                                        R.string.toast_message_unable_to_keycloak_bind,
                                        Toast.LENGTH_SHORT
                                    )
                                    forceDisabled = false
                                }

                                else -> {
                                    App.toast(
                                        R.string.toast_message_unable_to_keycloak_bind,
                                        Toast.LENGTH_SHORT
                                    )
                                    forceDisabled = false
                                }
                            }
                        }
                    }
                )
            } else {
                App.toast(R.string.toast_message_unable_to_keycloak_bind, Toast.LENGTH_SHORT)
                binding = false
                forceDisabled = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(id = R.string.activity_title_identity_provider),
            style = OlvidTypography.h2,
            color = colorResource(R.color.almostBlack)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorResource(R.color.almostWhite),
                contentColor = colorResource(R.color.almostBlack)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(id = R.string.explanation_keycloak_bind),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint)
                )

                warningText?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                border = BorderStroke(
                                    1.dp,
                                    colorResource(if (warningIsError) R.color.red else R.color.orange)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            painter = painterResource(if (warningIsError) R.drawable.ic_error_outline else R.drawable.ic_warning_outline),
                            contentDescription = null,
                            tint = colorResource(if (warningIsError) R.color.red else R.color.orange)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(it, style = OlvidTypography.body1, color = colorResource(R.color.greyTint))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Column(Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    InitialView(
                        modifier = Modifier.size(96.dp),
                        initialViewSetup = {
                            viewModel.currentIdentity?.photoUrl?.also { photoUrl ->
                                it.setAbsolutePhotoUrl(viewModel.currentIdentity?.bytesOwnedIdentity, App.absolutePathFromRelative(photoUrl))
                            } ?: run {
                                it.setInitial(
                                    viewModel.currentIdentity?.bytesOwnedIdentity,
                                    StringUtils.getInitial(
                                        viewModel.keycloakUserDetails?.userDetails?.firstName
                                            ?: viewModel.keycloakUserDetails?.userDetails?.lastName
                                    )
                                )
                            }
                            it.setKeycloakCertified(true)
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    viewModel.keycloakUserDetails?.userDetails?.let { details ->
                        Text(
                            text = "${details.firstName.orEmpty()} ${details.lastName.orEmpty()}".trim(),
                            style = OlvidTypography.h2,
                            color = colorResource(R.color.almostBlack)
                        )
                        if (details.position != null || details.company != null) {
                            Text(
                                text = details.position.orEmpty() + (if (details.position.isNullOrEmpty().not() && details.company.isNullOrEmpty().not()) " @ " else "") + details.company.orEmpty(),
                                style = OlvidTypography.h3,
                                color = colorResource(R.color.greyTint)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(visible = binding) {
                        OlvidCircularProgress(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OlvidTextButton(
                        text = stringResource(id = R.string.button_label_cancel),
                        contentColor = colorResource(R.color.greyTint),
                        onClick = onBack
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    OlvidActionButton(
                        text = stringResource(id = R.string.button_label_manage_keycloak),
                        onClick = { bindIdentity() },
                        enabled = (viewModel.keycloakUserDetails?.userDetails?.firstName.isNullOrEmpty()
                            .not() ||
                                viewModel.keycloakUserDetails?.userDetails?.lastName.isNullOrEmpty()
                                    .not()) && !forceDisabled && !binding
                    )
                }
            }
        }
        Spacer(
            modifier = Modifier
                .height(WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
        )
    }
}
