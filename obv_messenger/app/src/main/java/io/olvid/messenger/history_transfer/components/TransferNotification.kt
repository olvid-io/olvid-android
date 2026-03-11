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

package io.olvid.messenger.history_transfer.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.history_transfer.types.TransferProgress
import io.olvid.messenger.history_transfer.types.TransferProgress.Connecting.getStepName

@Composable
fun TransferNotification(
    modifier: Modifier = Modifier,
    transferProgress: TransferProgress,
    onClick: () -> Unit,
    onAbort: () -> Unit,
) {
    var showAbortConfirmation by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .widthIn(max = 360.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.newDialogBackground),
            contentColor = colorResource(R.color.almostBlack),
        ),
        border = BorderStroke(1.dp, colorResource(R.color.newDialogBorder)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp),
            verticalAlignment = CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = transferProgress is TransferProgress.ContactingOtherDevice
                            || transferProgress is TransferProgress.Connecting
                            || transferProgress is TransferProgress.Negotiating
                ) {
                    OlvidCircularProgress(
                        size = 48.dp,
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = transferProgress is TransferProgress.TransferringMessages
                ) {
                    OlvidCircularProgress(
                        progress = (transferProgress as? TransferProgress.TransferringMessages)?.let {
                            it.progress.toFloat() / it.total
                        } ?: 1f,
                        size = 48.dp,
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = transferProgress is TransferProgress.TransferringFiles
                ) {
                    OlvidCircularProgress(
                        progress = (transferProgress as? TransferProgress.TransferringFiles)?.let {
                            it.progress.toFloat() / it.total
                        } ?: 1f,
                        size = 48.dp,
                    )
                }
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_transfer),
                    tint = colorResource(R.color.olvid_gradient_light),
                    contentDescription = null
                )
            }

            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f, true), verticalArrangement = Arrangement.Center) {
                Text(
                    text = stringResource(R.string.history_transfer_title),
                    color = colorResource(id = R.color.primary700),
                    style = OlvidTypography.h3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = transferProgress.getStepName(true),
                    color = colorResource(id = R.color.greyTint),
                    style = OlvidTypography.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))

            IconButton(
                modifier = Modifier
                    .size(48.dp)
                    .padding(4.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = colorResource(id = R.color.red).copy(alpha = .5f),
                    contentColor = colorResource(R.color.red)
                ),
                shape = CircleShape,
                onClick = {
                    showAbortConfirmation = true
                }
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.ic_stop),
                    contentDescription = stringResource(R.string.button_label_cancel)
                )
            }
        }
    }

    if (showAbortConfirmation) {
        DialogSecure(
            onDismissRequest = {
                @Suppress("AssignedValueIsNeverRead")
                showAbortConfirmation = false
            }
        ) {
            BaseDialogContent(
                title = stringResource(R.string.history_transfer_abort_dialog_title),
                content = {
                    Text(
                        text = stringResource(R.string.history_transfer_abort_dialog_message),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint),
                    )
                },
                actions = {
                    Spacer(Modifier.weight(1f))
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        contentColor = colorResource(R.color.olvid_gradient_light),
                    ) {
                        @Suppress("AssignedValueIsNeverRead")
                        showAbortConfirmation = false
                    }
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_abort_transfer),
                        contentColor = colorResource(R.color.olvid_gradient_light),
                        onClick = {
                            onAbort.invoke()
                            @Suppress("AssignedValueIsNeverRead")
                            showAbortConfirmation = false
                        }
                    )
                }
            )
        }
    }
}


@Preview
@Composable
fun TransferNotificationPreview() {
    Column(
        modifier = Modifier
            .background(colorResource(R.color.almostWhite))
            .padding(16.dp),
        verticalArrangement = spacedBy(16.dp)
    ) {
        TransferNotification(
            transferProgress = TransferProgress.ContactingOtherDevice,
            onClick = { },
            onAbort = { }
        )

        TransferNotification(
            transferProgress = TransferProgress.Negotiating,
            onClick = { },
            onAbort = { }
        )

        TransferNotification(
            transferProgress = TransferProgress.TransferringMessages(150, 3000),
            onClick = { },
            onAbort = { }
        )

        TransferNotification(
            transferProgress = TransferProgress.TransferringFiles(5000L, 10000L),
            onClick = { },
            onAbort = { }
        )
    }
}
