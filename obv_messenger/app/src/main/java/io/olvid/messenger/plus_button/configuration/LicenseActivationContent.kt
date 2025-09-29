package io.olvid.messenger.plus_button.configuration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.engine.engine.types.EngineAPI
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.billing.SubscriptionPurchaseViewModel
import io.olvid.messenger.billing.SubscriptionStatusScreen
import io.olvid.messenger.customClasses.ConfigurationPojo
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.plus_button.PlusButtonViewModel

@Composable
internal fun LicenseActivationContent(
    viewModel: PlusButtonViewModel,
    configurationPojo: ConfigurationPojo,
    onCancel: () -> Unit
) {
    val licenseActivationViewModel: LicenseActivationViewModel = viewModel()
    val purchaseViewModel: SubscriptionPurchaseViewModel = viewModel()
    val context = LocalContext.current
    val ownedIdentity = viewModel.currentIdentity

    LaunchedEffect(ownedIdentity) {
        if (ownedIdentity == null) {
            onCancel()
            return@LaunchedEffect
        }
        licenseActivationViewModel.init(context, ownedIdentity, configurationPojo, onCancel)
        purchaseViewModel.updateBytesOwnedIdentity(ownedIdentity.bytesOwnedIdentity)
    }


    Column(modifier = Modifier.padding(16.dp)) {
        if (licenseActivationViewModel.errorText != null) {
            ErrorCard(errorText = licenseActivationViewModel.errorText!!, onCancel = onCancel)
        } else {
            // New Status
            Text(
                text = stringResource(R.string.label_new_api_key_status),
                style = OlvidTypography.h2,
                color = colorResource(R.color.almostBlack)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (licenseActivationViewModel.queryFailed) {
                Text(
                    text = stringResource(R.string.label_unable_to_check_license_status),
                    color = colorResource(id = R.color.red)
                )
            } else if (licenseActivationViewModel.newApiKeyStatus != null) {
                SubscriptionStatusScreen(
                    contentPadding = 16.dp,
                    viewModel = purchaseViewModel,
                    apiKeyStatus = licenseActivationViewModel.newApiKeyStatus,
                    apiKeyExpirationTimestamp = licenseActivationViewModel.newApiKeyExpirationTimestamp,
                    apiKeyPermissions = licenseActivationViewModel.newApiKeyPermissions ?: emptyList(),
                    licenseQuery = true,
                    showInAppPurchase = false,
                    anotherIdentityHasCallsPermission = false
                )
            } else {
                OlvidCircularProgress(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OlvidTextButton(
                    onClick = onCancel,
                    large = true,
                    contentColor = colorResource(R.color.greyTint),
                    text = stringResource(R.string.button_label_cancel)
                )
                Spacer(Modifier.width(8.dp))

                val canActivate =
                    (licenseActivationViewModel.newApiKeyStatus != null && licenseActivationViewModel.newApiKeyStatus != EngineAPI.ApiKeyStatus.LICENSES_EXHAUSTED) || licenseActivationViewModel.queryFailed
                if (canActivate && !licenseActivationViewModel.activating) {
                    OlvidActionButton(
                        onClick = {
                            licenseActivationViewModel.activateLicense(onSuccess = onCancel)
                        },
                        text = stringResource(R.string.button_label_activate_license)
                    )
                } else if (licenseActivationViewModel.activating) {
                    OlvidCircularProgress()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Current Status
            Text(
                text = stringResource(R.string.label_current_api_key_status),
                style = OlvidTypography.h2,
                color = colorResource(R.color.almostBlack)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SubscriptionStatusScreen(
                contentPadding = 16.dp,
                viewModel = purchaseViewModel,
                apiKeyStatus = ownedIdentity?.getApiKeyStatus(),
                apiKeyExpirationTimestamp = ownedIdentity?.apiKeyExpirationTimestamp,
                apiKeyPermissions = ownedIdentity?.getApiKeyPermissions() ?: emptyList(),
                licenseQuery = false,
                showInAppPurchase = false,
                anotherIdentityHasCallsPermission = AppSingleton.getOtherProfileHasCallsPermission()
            )
        }
    }
}