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

package io.olvid.messenger.contact

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun ContactUpdate(
    modifier: Modifier = Modifier,
    bytesContactIdentity: ByteArray,
    name: String? = null,
    positionAndCompany: String? = null,
    photoUrl: String? = null,
    onAcceptUpdate: () -> Unit = {}
) {
    val changeCount =
        listOf(name != null, positionAndCompany != null, photoUrl != null).count { it }
    BoxWithConstraints(modifier = modifier) {
        val width = if (changeCount == 1)
            maxWidth
        else
            maxWidth * 3 / 4
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(R.string.text_contact_details_updated),
                    style = OlvidTypography.h3,
                    color = colorResource(id = R.color.almostBlack)
                )
                Text(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .wrapContentWidth(align = Alignment.CenterHorizontally)
                        .background(
                            color = colorResource(id = R.color.red),
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
                name?.let {
                    Card(
                        modifier = Modifier
                            .width(width)
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
                                text = stringResource(R.string.explanation_new_contact_card_name),
                                style = OlvidTypography.body2
                            )
                            Text(
                                text = name,
                                style = OlvidTypography.body2.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
                positionAndCompany?.let {
                    Card(
                        modifier = Modifier
                            .width(width)
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
                                text = stringResource(R.string.explanation_new_contact_card_position),
                                style = OlvidTypography.body2
                            )
                            Text(
                                text = positionAndCompany,
                                style = OlvidTypography.body2.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
                photoUrl?.let {
                    Card(
                        modifier = Modifier
                            .width(width)
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
                                    if (photoUrl.isEmpty()) {
                                        initialView.setUnknown()
                                    } else {
                                        initialView.setPhotoUrl(
                                            bytesContactIdentity,
                                            photoUrl
                                        )
                                    }
                                })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.explanation_new_contact_card_photo),
                                style = OlvidTypography.body2
                            )
                        }
                    }
                }
            }
            OlvidTextButton(
                modifier = Modifier.align(Alignment.End),
                text = stringResource(R.string.button_label_update),
                onClick = onAcceptUpdate
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContactUpdatePreview() {
    ContactUpdate(
        bytesContactIdentity = byteArrayOf(),
        name = "New Name",
        positionAndCompany = "New Position",
        photoUrl = "new_photo_url"
    )
}
