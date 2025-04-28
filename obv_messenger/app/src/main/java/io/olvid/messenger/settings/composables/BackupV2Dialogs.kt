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

package io.olvid.messenger.settings.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.theme.OlvidTypography


@Composable
fun CredentialsNotErasedDialog(
    onDismiss: () -> Unit,
) {
    Dialog (
        onDismissRequest =onDismiss
    ) {
        Column (
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.dialogBackground))
                .width(280.dp)
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = spacedBy(8.dp),
            ) {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_warning_outline),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(end = 4.dp),
                    text = AnnotatedString(stringResource(R.string.dialog_message_credentials_not_deleted)).formatMarkdown(),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row (
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = onDismiss,
                ) {
                    Text(
                        text = stringResource(R.string.button_label_ok),
                        color = colorResource(R.color.olvid_gradient_light),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun CredentialsNotErasedDialogPreview() {
    CredentialsNotErasedDialog({})
}


@Composable
fun RestoreSnapshotConfirmationDialog(
    displayName: String,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.dialogBackground))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = spacedBy(8.dp),
            ) {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_backup),
                    colorFilter = ColorFilter.tint(colorResource(R.color.olvid_gradient_light)),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(end = 4.dp),
                    text = AnnotatedString(stringResource(R.string.dialog_message_confirm_restore_profile, displayName)).formatMarkdown(),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = onDismiss,
                ) {
                    Text(
                        text = stringResource(R.string.button_label_cancel),
                        color = colorResource(R.color.greyTint),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors().copy(
                        containerColor = colorResource(R.color.olvid_gradient_light),
                        contentColor = colorResource(R.color.alwaysWhite)
                    ),
                    onClick = onRestore,
                ) {
                    Text(
                        text = stringResource(R.string.button_label_restore_snapshot),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun RestoreConfirmationPreview() {
    RestoreSnapshotConfirmationDialog(
        displayName = "Clara G.",
        onRestore = { },
        onDismiss = { },
    )
}


@Composable
fun DeleteSnapshotConfirmationDialog(
    fromThisDevice: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.dialogBackground))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = spacedBy(8.dp),
            ) {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_delete_red),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(end = 4.dp),
                    text = if (fromThisDevice)
                        AnnotatedString(stringResource(R.string.dialog_message_backup_snapshot_delete_confirmation)).formatMarkdown()
                    else
                        AnnotatedString(stringResource(R.string.dialog_message_backup_snapshot_delete_confirmation_current_device)).formatMarkdown(),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = onDismiss,
                ) {
                    Text(
                        text = stringResource(R.string.button_label_cancel),
                        color = colorResource(R.color.greyTint),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors().copy(
                        containerColor = colorResource(R.color.red),
                        contentColor = colorResource(R.color.alwaysWhite)
                    ),
                    onClick = onDelete,
                ) {
                    Text(
                        text = stringResource(R.string.button_label_delete),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun DeleteConfirmationPreview() {
    DeleteSnapshotConfirmationDialog(
        fromThisDevice = true,
        onDelete = { },
        onDismiss = { },
    )
}



@Composable
fun BackupFailedDialog(
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorResource(R.color.dialogBackground))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = spacedBy(8.dp),
            ) {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_backup),
                    colorFilter = ColorFilter.tint(colorResource(R.color.red)),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(end = 4.dp),
                    text = stringResource(R.string.dialog_message_failed_to_backup),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    modifier = Modifier.height(40.dp),
                    elevation = null,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = onDismiss,
                ) {
                    Text(
                        text = stringResource(R.string.button_label_ok),
                        color = colorResource(R.color.greyTint),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun BackupFailedDialogPreview() {
    BackupFailedDialog {  }
}