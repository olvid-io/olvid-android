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

package io.olvid.messenger.owneddetails

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.billing.SubscriptionStatusScreen
import io.olvid.messenger.databases.entity.OwnedDevice
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.settings.SettingsActivity

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun OwnedIdentityDetailsScreen(
    ownedDetailsViewModel: OwnedIdentityDetailsViewModel,
    imageClick: (String?) -> Unit,
    onReactivate: () -> Unit,
    onPublish: () -> Unit,
    onDiscard: () -> Unit,
    onAddDevice: () -> Unit,
    onTrustDevice: (OwnedDevice) -> Unit,
    onRenameDevice: (OwnedDevice) -> Unit,
    onRemoveDeviceExpiration: (OwnedDevice) -> Unit,
    onRefreshDeviceList: (OwnedDevice) -> Unit,
    onRecreateDeviceChannel: (OwnedDevice) -> Unit,
    onRemoveDevice: (OwnedDevice) -> Unit,
    sharedTransitionScope: SharedTransitionScope
) {
    val ownedIdentity by AppSingleton.getCurrentIdentityLiveData().observeAsState()
    val ownedDevices by ownedDetailsViewModel.ownedDevicesLiveData.observeAsState(emptyList())
    val showRefreshSpinner by ownedDetailsViewModel.showRefreshSpinner.observeAsState(false)

    LaunchedEffect(ownedIdentity) {
        ownedIdentity?.let {
            ownedDetailsViewModel.refresh(it.bytesOwnedIdentity)
            ownedDetailsViewModel.keycloakManaged = it.keycloakManaged
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(colorResource(R.color.almostWhite))
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            with(sharedTransitionScope) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = false,
                                color = colorResource(R.color.blueOrWhiteOverlay)
                            ),
                        ) { imageClick(ownedIdentity?.photoUrl) }
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = ownedDetailsViewModel.fullScreenPhotoUrl == null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {

                        InitialView(
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "profile-photo"),
                                    animatedVisibilityScope = this@AnimatedVisibility
                                ),
                            initialViewSetup = { initialView ->
                                ownedIdentity?.let {
                                    initialView.setOwnedIdentity(it)
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val name = ownedIdentity?.getCustomDisplayName().orEmpty()
            Text(
                text = name,
                style = OlvidTypography.h3.copy(
                    color = colorResource(R.color.almostBlack),
                    lineHeight = 32.sp,
                    fontSize = 32.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (ownedIdentity?.displayName != name) {
                Text(
                    text = ownedIdentity?.displayName.orEmpty(),
                    style = OlvidTypography.body1.copy(
                        color = colorResource(R.color.almostBlack),
                    ),
                    textAlign = TextAlign.Center
                )
            }

            // position and company
            ownedDetailsViewModel.publishedDetails?.identityDetails?.let { details ->
                val positionAndCompany = details.formatPositionAndCompany(
                    SettingsActivity.contactDisplayNameFormat
                )
                if (!positionAndCompany.isNullOrEmpty()) {
                    Text(
                        text = positionAndCompany,
                        style = OlvidTypography.body1.copy(
                            color = colorResource(R.color.almostBlack),
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Inactive identity card
        if (ownedIdentity?.active == false) {
            InactiveCard(onReactivate = onReactivate)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Unpublished draft card
        val latestDetails = ownedDetailsViewModel.latestDetails
        val publishedDetails = ownedDetailsViewModel.publishedDetails
        if (latestDetails != null && publishedDetails != null && latestDetails.version != publishedDetails.version && ownedIdentity != null) {
            UnpublishedDetailsCard(
                bytesOwnedIdentity = ownedIdentity!!.bytesOwnedIdentity,
                latestDetails = latestDetails,
                publishedDetails = publishedDetails,
                onPublish = onPublish,
                onDiscard = onDiscard
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Devices Section
        DevicesSection(
            devices = ownedDevices,
            currentDeviceIsActive = ownedIdentity?.active == true,
            showRefreshSpinner = showRefreshSpinner,
            onTrust = onTrustDevice,
            onRename = onRenameDevice,
            onRemoveExpiration = onRemoveDeviceExpiration,
            onRefresh = onRefreshDeviceList,
            onRecreateChannel = onRecreateDeviceChannel,
            onRemove = onRemoveDevice,
            onAddDevice = onAddDevice
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Subscription Status
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
                text = stringResource(id = R.string.label_subscription_status),
                style = OlvidTypography.h3.copy(color = colorResource(R.color.almostBlack)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ownedIdentity?.let { identity ->
                SubscriptionStatusScreen(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)),
                    backgroundModifier = Modifier.background(
                        color = colorResource(R.color.lighterGrey),
                        shape = RoundedCornerShape(10.dp)
                    ),
                    apiKeyStatus = identity.getApiKeyStatus(),
                    apiKeyExpirationTimestamp = identity.apiKeyExpirationTimestamp,
                    apiKeyPermissions = identity.getApiKeyPermissions() ?: emptyList(),
                    licenseQuery = false,
                    showInAppPurchase = !identity.keycloakManaged,
                    anotherIdentityHasCallsPermission = AppSingleton.getOtherProfileHasCallsPermission(),
                    contentPadding = 16.dp
                )
            }
        }
    }
}
