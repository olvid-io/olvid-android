/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.onboarding.flow.screens.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.identities.ObvIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.MainActivity
import io.olvid.messenger.onboarding.OnboardingViewModel
import io.olvid.messenger.onboarding.flow.OnboardingAction
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import io.olvid.messenger.onboarding.flow.OnboardingScreen
import io.olvid.messenger.onboarding.flow.OnboardingStep
import io.olvid.messenger.openid.KeycloakManager
import io.olvid.messenger.openid.KeycloakManager.KeycloakCallback
import io.olvid.messenger.owneddetails.EditOwnedIdentityDetailsScreen
import io.olvid.messenger.owneddetails.HiddenProfilePasswordCreationDialog
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsViewModel.ValidStatus.INVALID
import io.olvid.messenger.owneddetails.rememberDetailsPhotoPicker
import io.olvid.messenger.settings.SettingsActivity.Companion.isHiddenProfileClosePolicyDefined
import java.util.UUID

/**
 * Compose replacement for the legacy IdentityCreationFragment: the final identity-creation form, reusing
 * [EditOwnedIdentityDetailsScreen] on the shared [OwnedIdentityDetailsViewModel]. Handles keycloak-locked
 * details (prefilled + non-editable), and the actual identity generation including keycloak upload.
 */
fun NavGraphBuilder.additionalIdentityCreation(
    onboardingViewModel: OnboardingViewModel,
    detailsViewModel: OwnedIdentityDetailsViewModel,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    composable(
        OnboardingRoutes.ADDITIONAL_IDENTITY_CREATION,
        enterTransition = { slideIntoContainer(SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) }
    ) {
        val context = LocalContext.current
        val resources = LocalResources.current
        val activity = LocalActivity.current
        val valid by detailsViewModel.valid.observeAsState()
        val forceDisabled by onboardingViewModel.forceDisabled.observeAsState(false)
        var profileHidden by remember { mutableStateOf(detailsViewModel.isProfileHidden) }
        var showPasswordCreation by remember { mutableStateOf(false) }

        val keycloakManaged = onboardingViewModel.keycloakSerializedAuthState != null
        val showNicknameAndHidden = !onboardingViewModel.isFirstIdentity

        val clearViewModelAndGoBack = {
            detailsViewModel.firstName = null
            detailsViewModel.lastName = null
            detailsViewModel.company = null
            detailsViewModel.position = null
            detailsViewModel.nickname = null
            detailsViewModel.isProfileHidden = false
            onBack()
        }

        // Initialize the shared details view-model. Its fields are Compose-state-backed, so the form
        // (which binds directly to the view-model) reflects this prefill — including keycloak-locked
        // details and the locked state — reactively.
        LaunchedEffect(Unit) {
            detailsViewModel.bytesOwnedIdentity = ByteArray(0)
            detailsViewModel.isIdentityInactive = false
            onboardingViewModel.setForceDisabled(false)
            if (keycloakManaged) {
                detailsViewModel.detailsLocked = true
                detailsViewModel.pictureLocked = false
                onboardingViewModel.keycloakUserDetails?.let { details ->
                    detailsViewModel.firstName = details.firstName
                    detailsViewModel.lastName = details.lastName
                    detailsViewModel.company = details.company
                    detailsViewModel.position = details.position
                    if (details.identity != null && !onboardingViewModel.isKeycloakRevocationAllowed) {
                        onboardingViewModel.setForceDisabled(true)
                    }
                }
            } else {
                detailsViewModel.detailsLocked = false
                detailsViewModel.pictureLocked = false
            }
        }

        BackHandler {
            clearViewModelAndGoBack()
        }


        val photoPicker = rememberDetailsPhotoPicker { detailsViewModel.absolutePhotoUrl = it }

        fun finishToMain() {
            App.showMainActivityTab(context, MainActivity.DISCUSSIONS_TAB)
            activity?.finish()
        }

        fun identityCreated(obvIdentity: ObvIdentity) {
            if (keycloakManaged) {
                KeycloakManager.uploadOwnIdentity(
                    obvIdentity.getBytesIdentity(),
                    object : KeycloakCallback<Void?> {
                        override fun success(result: Void?) {
                            if (detailsViewModel.password != null && !isHiddenProfileClosePolicyDefined) {
                                App.openAppDialogConfigureHiddenProfileClosePolicy()
                            }
                            finishToMain()
                        }

                        override fun failed(rfc: Int) {
                            activity?.runOnUiThread {
                                SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                                    .setTitle(R.string.dialog_title_identity_provider_error)
                                    .setMessage(R.string.dialog_message_failed_to_upload_identity_to_keycloak)
                                    .setPositiveButton(R.string.button_label_ok, null)
                                    .setOnDismissListener { finishToMain() }
                                    .create()
                                    .show()
                            }
                        }
                    })
            } else {
                if (detailsViewModel.password != null && !isHiddenProfileClosePolicyDefined) {
                    App.openAppDialogConfigureHiddenProfileClosePolicy()
                }
                finishToMain()
            }
        }

        fun doCreateIdentity(server: String, apiKey: UUID?) {
            onboardingViewModel.setForceDisabled(true)
            AppSingleton.getInstance().generateIdentity(
                server,
                apiKey,
                detailsViewModel.jsonIdentityDetails,
                detailsViewModel.absolutePhotoUrl,
                detailsViewModel.nickname,
                detailsViewModel.password,
                detailsViewModel.salt,
                onboardingViewModel.keycloakServer,
                onboardingViewModel.getSupportedKeycloakAuthMethods(),
                onboardingViewModel.keycloakJwks,
                onboardingViewModel.keycloakSignatureKey,
                onboardingViewModel.keycloakSerializedAuthState,
                onboardingViewModel.isKeycloakTransferRestricted,
                { obvIdentity: ObvIdentity -> identityCreated(obvIdentity) },
                { onboardingViewModel.setForceDisabled(false) }
            )
        }

        fun createIdentity() {
            val server = onboardingViewModel.server
            if (server.isNullOrEmpty()) return
            var apiKey = onboardingViewModel.apiKey
            @Suppress("SENSELESS_COMPARISON")
            if (apiKey == null && BuildConfig.HARDCODED_API_KEY != null) {
                apiKey = UUID.fromString(BuildConfig.HARDCODED_API_KEY)
            }
            if (detailsViewModel.jsonIdentityDetails.isEmpty()) return
            if (onboardingViewModel.forceDisabled.value == true) return

            if (BuildConfig.SERVER_NAME != server) {
                SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_non_default_server)
                    .setMessage(
                        resources.getString(
                            R.string.dialog_message_non_default_server,
                            server
                        )
                    )
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_proceed) { _, _ ->
                        doCreateIdentity(
                            server,
                            apiKey
                        )
                    }
                    .create()
                    .show()
            } else {
                doCreateIdentity(server, apiKey)
            }
        }

        // build the "special options" summary (identity provider / server / license)
        val optionsSummary = buildString {
            if (keycloakManaged) {
                append(
                    stringResource(
                        R.string.text_option_identity_provider,
                        onboardingViewModel.keycloakServer ?: ""
                    )
                )
            }
            if (BuildConfig.SERVER_NAME != onboardingViewModel.server) {
                if (isNotEmpty()) append("\n")
                append(
                    stringResource(
                        R.string.text_option_server,
                        onboardingViewModel.server ?: ""
                    )
                )
            }
            onboardingViewModel.apiKey?.let {
                if (isNotEmpty()) append("\n")
                append(stringResource(R.string.text_option_license_code, Logger.getUuidString(it)))
            }
        }

        OnboardingScreen(
            step = OnboardingStep(
                title = stringResource(R.string.activity_title_identity_creation),
                actions = listOf(
                    OnboardingAction(
                        label = AnnotatedString(
                            stringResource(
                                if (onboardingViewModel.isFirstIdentity) R.string.button_label_generate_my_id
                                else R.string.button_label_generate_new_id
                            )
                        ),
                        type = BUTTON,
                        enabled = valid != null && valid != INVALID && !forceDisabled,
                        onClick = { createIdentity() },
                    )
                )
            ),
            onBack = clearViewModelAndGoBack,
            onClose = onClose,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = if (keycloakManaged)
                    stringResource(R.string.explanation_choose_display_name_keycloak)
                else
                    stringResource(R.string.explanation_choose_display_name),
                style = OlvidTypography.body1,
                color = colorResource(R.color.greyTint),
            )
            Spacer(modifier = Modifier.height(16.dp))


            EditOwnedIdentityDetailsScreen(
                viewModel = detailsViewModel,
                showNicknameAndHidden = showNicknameAndHidden,
                profileHidden = profileHidden,
                onPhotoTake = photoPicker.takePhoto,
                onPhotoChoose = photoPicker.chooseImage,
                onPhotoRemove = { detailsViewModel.absolutePhotoUrl = null },
                onChooseImageFromOlvid = null,
                onHiddenClick = {
                    if (detailsViewModel.isProfileHidden) {
                        detailsViewModel.isProfileHidden = false
                        profileHidden = false
                    } else {
                        showPasswordCreation = true
                    }
                },
                focusFirstName = !keycloakManaged,
            )

            if (keycloakManaged && onboardingViewModel.keycloakUserDetails?.identity != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(
                        if (onboardingViewModel.isKeycloakRevocationAllowed)
                            R.string.text_explanation_warning_identity_creation_keycloak_revocation_needed
                        else
                            R.string.text_explanation_warning_identity_creation_keycloak_revocation_impossible
                    ),
                    style = OlvidTypography.body2,
                    color = colorResource(
                        if (onboardingViewModel.isKeycloakRevocationAllowed) R.color.orange else R.color.red
                    ),
                )
            }

            if (optionsSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    text = optionsSummary,
                    style = OlvidTypography.subtitle1,
                    color = colorResource(R.color.greyTint),
                )
            }
        }

        if (showPasswordCreation) {
            HiddenProfilePasswordCreationDialog(
                onDismiss = { showPasswordCreation = false },
                onPasswordSet = { hash, salt ->
                    showPasswordCreation = false
                    detailsViewModel.setPasswordAndSalt(hash, salt)
                    detailsViewModel.isProfileHidden = true
                    profileHidden = true
                }
            )
        }
    }
}
