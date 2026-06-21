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

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.JsonIdentityDetailsWithVersionAndPhoto
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.settings.SettingsActivity

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun UnpublishedDetailsCard(
    modifier: Modifier = Modifier,
    bytesOwnedIdentity: ByteArray,
    latestDetails: JsonIdentityDetailsWithVersionAndPhoto,
    publishedDetails: JsonIdentityDetailsWithVersionAndPhoto?,
    onPublish: () -> Unit,
    onDiscard: () -> Unit
) {
    val latestIdentityDetails = latestDetails.identityDetails
    val publishedIdentityDetails = publishedDetails?.identityDetails

    val latestFirstLine = latestIdentityDetails.formatFirstAndLastName(
        SettingsActivity.contactDisplayNameFormat,
        SettingsActivity.uppercaseLastName
    )
    val latestSecondLine = latestIdentityDetails.formatPositionAndCompany(
        SettingsActivity.contactDisplayNameFormat
    )
    val publishedFirstLine = publishedIdentityDetails?.formatFirstAndLastName(
        SettingsActivity.contactDisplayNameFormat,
        SettingsActivity.uppercaseLastName
    )
    val publishedSecondLine = publishedIdentityDetails?.formatPositionAndCompany(
        SettingsActivity.contactDisplayNameFormat
    )

    val nameChanged = latestFirstLine != publishedFirstLine
    val positionChanged = latestSecondLine != publishedSecondLine
    val photoChanged = latestDetails.photoUrl != publishedDetails?.photoUrl

    val changeCount = listOf(nameChanged, positionChanged, photoChanged).count { it }

    if (changeCount == 0) return

    BoxWithConstraints(modifier = modifier) {
        val cardWidth = if (changeCount == 1) maxWidth else maxWidth * 3 / 4
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.text_own_details_updated),
                    style = OlvidTypography.h3,
                    color = colorResource(id = R.color.almostBlack)
                )
                Text(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .wrapContentWidth(align = Alignment.CenterHorizontally)
                        .background(
                            color = colorResource(id = R.color.orange),
                            shape = CircleShape
                        )
                        .padding(horizontal = 4.dp),
                    text = changeCount.toString(),
                    fontSize = 12.sp,
                    color = colorResource(id = R.color.alwaysWhite)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .then(
                        if (changeCount > 1)
                            Modifier.height(IntrinsicSize.Min)
                        else
                            Modifier
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (nameChanged) {
                    Card(
                        modifier = Modifier
                            .width(cardWidth)
                            .then(
                                if (changeCount > 1)
                                    Modifier.fillMaxHeight()
                                else
                                    Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(R.color.lighterGrey),
                            contentColor = colorResource(R.color.almostBlack)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.explanation_unpublished_owned_card_name),
                                style = OlvidTypography.body2
                            )
                            Text(
                                text = latestFirstLine.orEmpty(),
                                style = OlvidTypography.body2
                            )
                        }
                    }
                }
                if (positionChanged) {
                    Card(
                        modifier = Modifier
                            .width(cardWidth)
                            .then(
                                if (changeCount > 1)
                                    Modifier.fillMaxHeight()
                                else
                                    Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(R.color.lighterGrey),
                            contentColor = colorResource(R.color.almostBlack)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.explanation_unpublished_owned_card_position),
                                style = OlvidTypography.body2
                            )
                            Text(
                                text = latestSecondLine.orEmpty(),
                                style = OlvidTypography.body2
                            )
                        }
                    }
                }
                if (photoChanged) {
                    Card(
                        modifier = Modifier
                            .width(cardWidth)
                            .then(
                                if (changeCount > 1)
                                    Modifier.fillMaxHeight()
                                else
                                    Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(R.color.lighterGrey),
                            contentColor = colorResource(R.color.almostBlack)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialView(
                                modifier = Modifier.size(56.dp),
                                initialViewSetup = { initialView ->
                                    if (latestDetails.photoUrl.isNullOrEmpty()) {
                                        initialView.setInitial(
                                            bytesOwnedIdentity,
                                            io.olvid.messenger.customClasses.StringUtils.getInitial(
                                                latestFirstLine
                                            )
                                        )
                                    } else {
                                        initialView.setPhotoUrl(
                                            bytesOwnedIdentity,
                                            latestDetails.photoUrl
                                        )
                                    }
                                })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.explanation_unpublished_owned_card_photo),
                                style = OlvidTypography.body2
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.align(Alignment.End),
            ) {
                OlvidTextButton(
                    text = stringResource(R.string.button_label_discard),
                    onClick = onDiscard
                )
                OlvidTextButton(
                    text = stringResource(R.string.button_label_publish),
                    onClick = onPublish
                )
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UnpublishedDetailsCardPreview() {
    UnpublishedDetailsCard(
        bytesOwnedIdentity = byteArrayOf(),
        latestDetails = JsonIdentityDetailsWithVersionAndPhoto().apply {
            identityDetails = JsonIdentityDetails("John", "Doe", "ACME", "Lead")
        },
        publishedDetails = JsonIdentityDetailsWithVersionAndPhoto().apply {
            identityDetails = JsonIdentityDetails("John", "Doe", "ACME", "Engineer")
        },
        onPublish = {},
        onDiscard = {}
    )
}
