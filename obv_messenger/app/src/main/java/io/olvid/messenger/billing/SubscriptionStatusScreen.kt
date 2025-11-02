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

package io.olvid.messenger.billing

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.engine.engine.types.EngineAPI
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidTextButton

@Composable
fun SubscriptionStatusScreen(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    viewModel: SubscriptionPurchaseViewModel,
    apiKeyStatus: EngineAPI.ApiKeyStatus?,
    apiKeyExpirationTimestamp: Long?,
    apiKeyPermissions: List<EngineAPI.ApiKeyPermission?>,
    licenseQuery: Boolean,
    showInAppPurchase: Boolean,
    anotherIdentityHasCallsPermission: Boolean,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .background(
                color = colorResource(R.color.almostWhite),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(contentPadding)
    ) {
        SubscriptionStatusHeader(
            viewModel = viewModel,
            apiKeyStatus = apiKeyStatus,
            apiKeyExpirationTimestamp = apiKeyExpirationTimestamp,
            licenseQuery = licenseQuery,
            showInAppPurchase = showInAppPurchase,
            onSubscribeClicked = { viewModel.showSubscriptionPlans = true },
            onFixPaymentClicked = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/account/subscriptions?sku=premium_2020_monthly&package=io.olvid.messenger".toUri()
                    )
                )
            }
        )

        if (viewModel.showSubscriptionPlans) {
            Spacer(modifier = Modifier.height(4.dp))
            SubscriptionPurchaseScreen(viewModel = viewModel)
        }

        if (!licenseQuery) {
            FreeFeatures()
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colorResource(R.color.lightGrey))
        }


        PremiumFeatures(
            apiKeyPermissions = apiKeyPermissions,
            anotherIdentityHasCallsPermission = anotherIdentityHasCallsPermission
        )
    }
}

@Composable
fun SubscriptionStatusHeader(
    viewModel: SubscriptionPurchaseViewModel,
    apiKeyStatus: EngineAPI.ApiKeyStatus?,
    apiKeyExpirationTimestamp: Long?,
    licenseQuery: Boolean,
    showInAppPurchase: Boolean,
    onSubscribeClicked: () -> Unit,
    onFixPaymentClicked: () -> Unit
) {
    val context = LocalContext.current
    val (statusText, expirationText, showSubscribeButton) = when (apiKeyStatus) {
        EngineAPI.ApiKeyStatus.UNKNOWN -> Triple(
            stringResource(if (licenseQuery) R.string.text_unknown_license else R.string.text_no_subscription),
            if (licenseQuery) null else stringResource(R.string.text_free_features_available),
            !licenseQuery && showInAppPurchase
        )

        EngineAPI.ApiKeyStatus.VALID -> Triple(
            stringResource(if (licenseQuery) R.string.text_valid_license else R.string.text_subscription_active),
            apiKeyExpirationTimestamp?.let {
                stringResource(
                    R.string.text_will_expire,
                    StringUtils.getPreciseAbsoluteDateString(
                        context,
                        it,
                        context.getString(R.string.text_date_time_separator)
                    )
                )
            },
            false
        )

        EngineAPI.ApiKeyStatus.LICENSES_EXHAUSTED -> Triple(
            stringResource(if (licenseQuery) R.string.label_unable_to_activate else R.string.text_invalid_subscription),
            stringResource(R.string.text_subscription_used_by_another_user),
            !licenseQuery && showInAppPurchase
        )

        EngineAPI.ApiKeyStatus.EXPIRED -> Triple(
            stringResource(if (licenseQuery) R.string.text_expired_license else R.string.text_subscription_expired),
            apiKeyExpirationTimestamp?.let {
                stringResource(
                    R.string.text_expired_since,
                    StringUtils.getPreciseAbsoluteDateString(
                        context,
                        it,
                        context.getString(R.string.text_date_time_separator)
                    )
                )
            },
            !licenseQuery && showInAppPurchase
        )

        EngineAPI.ApiKeyStatus.OPEN_BETA_KEY -> Triple(
            stringResource(R.string.text_beta_feature_tryout),
            apiKeyExpirationTimestamp?.let {
                stringResource(
                    R.string.text_premium_features_available_for_free_until,
                    StringUtils.getPreciseAbsoluteDateString(
                        context,
                        it,
                        context.getString(R.string.text_date_time_separator)
                    )
                )
            } ?: stringResource(R.string.text_premium_features_available),
            !licenseQuery && showInAppPurchase
        )

        EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY -> Triple(
            stringResource(R.string.text_premium_features_free_trial),
            apiKeyExpirationTimestamp?.let {
                stringResource(
                    R.string.text_premium_features_available_until,
                    StringUtils.getPreciseAbsoluteDateString(
                        context,
                        it,
                        context.getString(R.string.text_date_time_separator)
                    )
                )
            } ?: stringResource(R.string.text_premium_features_available),
            !licenseQuery && showInAppPurchase
        )

        EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_GRACE_PERIOD, EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD -> Triple(
            stringResource(R.string.text_awaiting_subscription_payment),
            apiKeyExpirationTimestamp?.let {
                if (apiKeyStatus == EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD) {
                    stringResource(
                        R.string.text_subscription_on_hold_since,
                        StringUtils.getPreciseAbsoluteDateString(
                            context,
                            it,
                            context.getString(R.string.text_date_time_separator)
                        )
                    )
                } else {
                    stringResource(
                        R.string.text_subscription_in_grace_period_until,
                        StringUtils.getPreciseAbsoluteDateString(
                            context,
                            it,
                            context.getString(R.string.text_date_time_separator)
                        )
                    )
                }
            },
            false
        )

        EngineAPI.ApiKeyStatus.FREE_TRIAL_KEY_EXPIRED -> Triple(
            stringResource(if (licenseQuery) R.string.text_free_trial_expired else R.string.text_free_trial_ended),
            apiKeyExpirationTimestamp?.let {
                stringResource(
                    if (licenseQuery) R.string.text_expired_since else R.string.text_ended_on,
                    StringUtils.getPreciseAbsoluteDateString(
                        context,
                        it,
                        context.getString(R.string.text_date_time_separator)
                    )
                )
            },
            !licenseQuery && showInAppPurchase
        )

        else -> Triple("", null, false)
    }
    Text(
        text = statusText,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = colorResource(id = R.color.almostBlack)
    )
    expirationText?.let {
        Text(text = it, fontSize = 12.sp, color = colorResource(id = R.color.greyTint))
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colorResource(R.color.lightGrey))
    if (showSubscribeButton && viewModel.showSubscriptionPlans.not()) {
        OlvidActionButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            onClick = onSubscribeClicked,
            icon = R.drawable.ic_subscribe,
            text = stringResource(id = R.string.button_label_see_subscription_options)
        )
    }

    if (apiKeyStatus == EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_GRACE_PERIOD || apiKeyStatus == EngineAPI.ApiKeyStatus.AWAITING_PAYMENT_ON_HOLD) {
        UpdatePaymentMethod(onFixPaymentClicked)
    }
}

@Composable
fun UpdatePaymentMethod(onUpdatePaymentClicked: () -> Unit) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colorResource(R.color.lightGrey))
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.label_fix_payment_method),
                    color = colorResource(id = R.color.almostBlack),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(id = R.string.label_fix_payment_method_explanation),
                    fontSize = 12.sp
                )
            }
            OlvidTextButton(
                text = stringResource(id = R.string.button_label_update),
                onClick = onUpdatePaymentClicked
            )
        }
    }
}

@Composable
fun FreeFeatures() {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(id = R.string.text_free_features),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorResource(id = R.color.almostBlack)
        )
        Spacer(modifier = Modifier.height(4.dp))
        FeatureItem(
            text = stringResource(id = R.string.text_feature_send_receive_messages),
            icon = R.drawable.ic_message,
            iconTint = colorResource(id = R.color.orange),
            activated = true
        )
        FeatureItem(
            text = stringResource(id = R.string.text_feature_create_groups),
            icon = R.drawable.ic_group,
            iconTint = colorResource(id = R.color.olvid_gradient_light),
            activated = true
        )
        FeatureItem(
            text = stringResource(id = R.string.text_feature_receive_secure_calls),
            icon = R.drawable.ic_phone_failed_in,
            activated = true
        )
    }
}

@Composable
fun PremiumFeatures(
    apiKeyPermissions: List<EngineAPI.ApiKeyPermission?>,
    anotherIdentityHasCallsPermission: Boolean
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(id = R.string.text_premium_features),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorResource(id = R.color.almostBlack)
        )
        Spacer(modifier = Modifier.height(4.dp))

        val hasCallPermission = apiKeyPermissions.contains(EngineAPI.ApiKeyPermission.CALL)
        val callFeatureText = if (hasCallPermission) {
            stringResource(id = R.string.text_feature_initiate_secure_calls)
        } else if (anotherIdentityHasCallsPermission) {
            stringResource(id = R.string.text_feature_initiate_secure_calls_from_another_profile)
        } else {
            stringResource(id = R.string.text_feature_initiate_secure_calls)
        }
        FeatureItem(
            text = callFeatureText,
            icon = if (hasCallPermission || anotherIdentityHasCallsPermission) R.drawable.ic_phone_failed_out else R.drawable.ic_phone_outgoing_grey,
            textColor = if (hasCallPermission || anotherIdentityHasCallsPermission) colorResource(id = R.color.almostBlack) else colorResource(
                id = R.color.grey
            ),
            activated = hasCallPermission || anotherIdentityHasCallsPermission
        )

        val hasMultiDevicePermission =
            apiKeyPermissions.contains(EngineAPI.ApiKeyPermission.MULTI_DEVICE)
        FeatureItem(
            text = stringResource(id = R.string.text_feature_multi_device),
            icon = if (hasMultiDevicePermission) R.drawable.ic_multi_device else R.drawable.ic_device,
            textColor = if (hasMultiDevicePermission) colorResource(id = R.color.almostBlack) else colorResource(
                id = R.color.grey
            ),
            activated = hasMultiDevicePermission
        )
    }
}


@Composable
fun FeatureItem(
    text: String,
    icon: Int,
    iconTint: Color = Color.Unspecified,
    textColor: Color = colorResource(id = R.color.almostBlack),
    activated: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = iconTint
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = textColor
        )
        Spacer(modifier = Modifier.width(8.dp))
        Image(
            painter = painterResource(id = if (activated) R.drawable.ic_activated_green else R.drawable.ic_deactivated_grey),
            contentDescription = null
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubscriptionStatusScreenPreview() {
    SubscriptionStatusScreen(
        viewModel = viewModel(),
        apiKeyStatus = EngineAPI.ApiKeyStatus.VALID,
        apiKeyExpirationTimestamp = System.currentTimeMillis(),
        apiKeyPermissions = emptyList(),
        licenseQuery = false,
        showInAppPurchase = true,
        anotherIdentityHasCallsPermission = false,
    )
}