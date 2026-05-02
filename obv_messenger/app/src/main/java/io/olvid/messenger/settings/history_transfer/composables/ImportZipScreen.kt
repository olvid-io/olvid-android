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

package io.olvid.messenger.settings.history_transfer.composables

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidOutlinedSecondaryButton
import io.olvid.messenger.designsystem.components.OlvidPasswordInput
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.settings.history_transfer.HistoryTransferRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream


fun NavGraphBuilder.importZipScreen(
    onChooseZipFile: () -> Unit,
    selectedOwnedIdentity: State<OwnedIdentity?>,
    pickingFile: State<Boolean>,
    selectedZipUri: State<Uri?>,
    onZipPasswordFound: (String?) -> Unit,
) {
    composable(
        HistoryTransferRoutes.IMPORT_ZIP_SCREEN,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) },
    ) {
        val displayName = remember {
            val details = selectedOwnedIdentity.value?.getIdentityDetails()

            return@remember selectedOwnedIdentity.value?.customDisplayName
                ?: details?.formatFirstAndLastName(
                    JsonIdentityDetails.FORMAT_STRING_FIRST_LAST,
                    false
                )
                ?: selectedOwnedIdentity.value?.displayName
                ?: ""
        }

        val context = LocalContext.current

        // reset the zip password to null everytime the zip file changes
        var zipPassword: String? by rememberSaveable(selectedZipUri.value) { mutableStateOf(null) }
        var showZipPasswordInputDialog by rememberSaveable { mutableStateOf(false) }
        var showPasswordErrorDialog by rememberSaveable { mutableStateOf(false) }
        var showErrorDialog by rememberSaveable { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.lightGrey))
                .verticalScroll(rememberScrollState())
                .padding(all = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.almostWhite))
                    .padding(all = 16.dp),
                verticalArrangement = spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier
                        .size(80.dp)
                        .background(colorResource(R.color.orange), CircleShape)
                        .padding(12.dp),
                    painter = painterResource(R.drawable.ic_zip),
                    contentDescription = null,
                    tint = colorResource(R.color.almostWhite),
                )
                Text(
                    text = stringResource(R.string.history_transfer_via_zip_title_import),
                    style = OlvidTypography.h1.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.history_transfer_import_zip_description, displayName).formatMarkdownToAnnotatedString(),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center,
                )
                OlvidActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.button_label_choose_zip_file),
                    large = true,
                    icon = R.drawable.ic_folder,
                    onClick = onChooseZipFile,
                )
            }

            if (pickingFile.value) {
                Spacer(Modifier.height(64.dp))
                OlvidCircularProgress()
            }

            // whenever the zip uri changes or a new password is entered, check if zip can be opened
            LaunchedEffect(selectedZipUri.value, zipPassword) {
                val uri = selectedZipUri.value ?: return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            // open the zip input stream
                            val zipInputStream = ZipInputStream(inputStream, zipPassword?.toCharArray())

                            // try to read a few bytes of the first entry
                            zipInputStream.nextEntry?.let {
                                val buffer = ByteArray(16)
                                zipInputStream.read(buffer)
                            }

                            // if we reach this, the provided password is correct (or there is no password)
                            withContext(Dispatchers.Main) {
                                onZipPasswordFound(zipPassword)
                            }
                        } ?: run {
                            // if we did not get an inputStream
                            showErrorDialog = true
                        }
                    } catch (e: ZipException) {
                        Logger.x(e)
                        // this is thrown if the password is wrong
                        if (e.type == ZipException.Type.WRONG_PASSWORD) {
                            if (zipPassword == null) {
                                // never prompted for a password --> do so
                                showZipPasswordInputDialog = true
                            } else {
                                // else, show a password error dialog
                                showPasswordErrorDialog = true
                            }
                        } else {
                            showErrorDialog = true
                        }
                    } catch (e: Exception) {
                        // error reading the zip
                        Logger.x(e)
                        showErrorDialog = true
                    }
                }
            }


            if (showErrorDialog) {
                DialogSecure(
                    onDismissRequest = { showErrorDialog = false },
                ) {
                    BaseDialogContent(
                        title = stringResource(R.string.dialog_title_error_reading_zip),
                        content = {
                            Text(
                                text = stringResource(R.string.dialog_message_error_reading_zip),
                                style = OlvidTypography.body2,
                                color = colorResource(R.color.greyTint),
                            )
                        },
                        actions = {
                            Spacer(Modifier.weight(1f, true))
                            OlvidTextButton(
                                text = stringResource(R.string.button_label_ok)
                            ) {
                                showErrorDialog = false
                            }
                        }
                    )
                }
            }

            if (showPasswordErrorDialog) {
                DialogSecure(
                    onDismissRequest = {
                        showPasswordErrorDialog = false
                        showZipPasswordInputDialog = true
                    },
                ) {
                    BaseDialogContent(
                        title = stringResource(R.string.dialog_title_error_zip_password),
                        content = {
                            Text(
                                text = stringResource(R.string.dialog_message_error_zip_password),
                                style = OlvidTypography.body2,
                                color = colorResource(R.color.greyTint),
                            )
                        },
                        actions = {
                            Spacer(Modifier.weight(1f, true))
                            OlvidTextButton(
                                text = stringResource(R.string.button_label_ok)
                            ) {
                                showPasswordErrorDialog = false
                                showZipPasswordInputDialog = true
                            }
                        }
                    )
                }
            }

            if (showZipPasswordInputDialog) {
                DialogSecure(
                    onDismissRequest = {
                        showZipPasswordInputDialog = false
                    },
                ) {
                    val password = rememberSaveable { mutableStateOf(zipPassword ?: "") }
                    BaseDialogContent(
                        title = stringResource(R.string.dialog_title_zip_password_required),
                        content = {
                            Text(
                                text = stringResource(R.string.dialog_message_zip_password_required),
                                style = OlvidTypography.body2,
                                color = colorResource(R.color.greyTint),
                            )
                            Spacer(Modifier.height(8.dp))
                            OlvidPasswordInput(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                password = password,
                            )
                        },
                        actions = {
                            OlvidOutlinedSecondaryButton(
                                modifier = Modifier.weight(1f, true).padding(8.dp),
                                text = stringResource(R.string.button_label_cancel)
                            ) {
                                showZipPasswordInputDialog = false
                            }
                            OlvidActionButton(
                                modifier = Modifier.weight(1f, true).padding(8.dp),
                                text = stringResource(R.string.button_label_ok)
                            ) {
                                showZipPasswordInputDialog = false
                                zipPassword = password.value
                            }
                        }
                    )
                }
            }
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    locale = "fr"
)
@Composable
private fun ImportZipScreenPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = HistoryTransferRoutes.IMPORT_ZIP_SCREEN,
    ) {
        importZipScreen(
            onChooseZipFile = {},
            selectedOwnedIdentity = mutableStateOf(
                OwnedIdentity(
                    ByteArray(2),
                    "Lisa Martin",
                    null,
                    0,
                    0,
                    null,
                    0,
                    null,
                    true,
                    true,
                    "Lisa 💗",
                    null,
                    null,
                    false,
                    false,
                    null,
                    false,
                    true,
                    true,
                    true,
                ),
            ),
            pickingFile = mutableStateOf(false),
            selectedZipUri = mutableStateOf(null),
            onZipPasswordFound = {},
        )
    }
}