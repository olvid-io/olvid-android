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

package io.olvid.messenger.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.navigation.compose.rememberNavController
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.EngineNotificationListener
import io.olvid.engine.engine.types.EngineNotifications
import io.olvid.engine.engine.types.SimpleEngineNotificationListener
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.billing.SubscriptionOfferDialog
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.google_services.GoogleServicesUtils
import io.olvid.messenger.onboarding.flow.OnboardingFlowActivity
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.settings.BackupV2ViewModel.BackupNowState
import io.olvid.messenger.settings.composables.BackupFailedDialog
import io.olvid.messenger.settings.composables.CredentialsNotErasedDialog
import io.olvid.messenger.settings.composables.ManageBackupsDialog
import java.util.concurrent.Executor


class BackupV2PreferenceFragment : Fragment() {
    private val viewModel: BackupV2ViewModel by activityViewModels()
    private lateinit var credentialManager: CredentialManager
    private lateinit var executor: Executor
    private val useCredentialManager = mutableStateOf(true)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        credentialManager = CredentialManager.create(App.getContext())
        executor = Executor { runnable -> activity?.runOnUiThread(runnable) }

        credentialManager.checkIfAvailable(executor, viewModel.credentialManagerAvailable)

        if (!viewModel.disableSeedGeneration.value && AppSingleton.getEngine().deviceBackupSeed == null) {
            activity?.supportFragmentManager?.let {
                BackupV2KeyGenerationDialogFragment().apply {
                    onDismissListener = {
                        if (AppSingleton.getEngine().deviceBackupSeed == null) {
                            try {
                                activity?.supportFragmentManager?.popBackStack()
                            } catch (_: Exception) {}
                        }
                    }
                    show(it, null)
                }
            }
        }

        return ComposeView(layoutInflater.context).apply {
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorResource(R.color.almostWhite))
                        .verticalScroll(rememberScrollState())
                        .padding(all = 16.dp),
                    verticalArrangement = spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var showTip: Boolean by remember { mutableStateOf(false) }
                    var hasMultiDeviceLicence: Boolean by remember { mutableStateOf(false) }
                    var hasMultipleDevices: Boolean by remember { mutableStateOf(false) }
                    val googleServicesAvailable =
                        remember { GoogleServicesUtils.googleServicesAvailable(context) }
                    var showPurchaseFragment by remember { mutableStateOf(false) }
                    var purchaseEngineListener: EngineNotificationListener? by remember {
                        mutableStateOf(
                            null
                        )
                    }

                    var showNotDeleteCredentialsDialog by remember { mutableStateOf(false) }
                    var showSuccessFailureDialog by remember { mutableStateOf(false) }
                    var savingBackupSeed by remember { mutableStateOf(false) }
                    var passwordManagerFailed by remember { mutableStateOf(false) }


                    BackupsHeader()

                    if (viewModel.credentialManagerAvailable.value == true) {
                        UseCredentialManagerSwitch(
                            useCredentialManager = useCredentialManager,
                            onToggleUseCredentialManager = { useCredentialsManager ->
                                if (useCredentialsManager) {
                                    val backupSeed: String? =
                                        AppSingleton.getEngine().deviceBackupSeed
                                    val activity = activity
                                    if (backupSeed == null || activity == null) {
                                        useCredentialManager.value = false
                                    } else {
                                        savingBackupSeed = true
                                        passwordManagerFailed = false
                                        showSuccessFailureDialog = true

                                        val request = CreatePasswordRequest(
                                            id = SettingsActivity.credentialManagerDeviceId,
                                            password = backupSeed,
                                        )

                                        credentialManager.createCredentialAsync(
                                            context = activity,
                                            request = request,
                                            cancellationSignal = null,
                                            executor = executor,
                                            callback = object :
                                                CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException> {
                                                override fun onResult(result: CreateCredentialResponse) {
                                                    SettingsActivity.useCredentialsManagerForBackups = true
                                                    savingBackupSeed = false
                                                }

                                                override fun onError(e: CreateCredentialException) {
                                                    Logger.x(e)
                                                    useCredentialManager.value = false
                                                    passwordManagerFailed = true
                                                    savingBackupSeed = false
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    SettingsActivity.useCredentialsManagerForBackups = false
                                    showNotDeleteCredentialsDialog = true
                                }
                            },
                            onChooseCredentialManager = BackupV2KeyGenerationDialogFragment.onChooseCredentialManager(
                                credentialManager,
                                context
                            )
                        )

                        if (showNotDeleteCredentialsDialog) {
                            CredentialsNotErasedDialog(
                                onDismiss = {
                                    showNotDeleteCredentialsDialog = false
                                },
                            )
                        }

                        if (showSuccessFailureDialog) {
                            SuccessFailureDialog(
                                generateFailed = false,
                                savingBackupSeed = savingBackupSeed,
                                passwordManagerFailed = passwordManagerFailed,
                                onDismiss = {
                                    showSuccessFailureDialog = false
                                }
                            )
                        }
                    }

                    Column (
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorResource(R.color.lighterGrey))
                    ) {
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            onClick = {
                                viewModel.resetYourBackups()
                                App.runThread {
                                    viewModel.deviceBackupSeed.value = AppSingleton.getEngine().deviceBackupSeed
                                    viewModel.fetchDeviceBackup(false)
                                }
                                viewModel.showManageBackupsDialog.value = true
                            },
                            shape = RectangleShape,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colorResource(R.color.olvid_gradient_light)
                            )
                        ) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.pref_manage_backups_title),
                                textAlign = TextAlign.Start,
                                style = OlvidTypography.body1,
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = colorResource(R.color.lightGrey)
                        )

                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            enabled = viewModel.backupNowState.value != BackupNowState.IN_PROGRESS,
                            onClick = {
                                viewModel.backupNowState.value = BackupNowState.IN_PROGRESS
                                App.runThread {
                                    if (AppSingleton.getEngine().backupDeviceAndProfilesNow()) {
                                        viewModel.backupNowState.value = BackupNowState.SUCCESS
                                    } else {
                                        viewModel.backupNowState.value = BackupNowState.FAILED
                                    }
                                }
                            },
                            shape = RectangleShape,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colorResource(R.color.olvid_gradient_light)
                            )
                        ) {
                            Text(
                                modifier = Modifier.weight(1f, true),
                                text = stringResource(R.string.pref_initiate_backup_now_title),
                                textAlign = TextAlign.Start,
                                style = OlvidTypography.body1,
                            )

                            AnimatedVisibility(visible = viewModel.backupNowState.value != BackupNowState.NONE) {
                                Spacer(Modifier.width(16.dp))
                                if (viewModel.backupNowState.value == BackupNowState.IN_PROGRESS) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = colorResource(id = R.color.olvid_gradient_light)
                                    )
                                } else {
                                    Image(
                                        modifier = Modifier.size(24.dp),
                                        painter = if (viewModel.backupNowState.value == BackupNowState.SUCCESS) painterResource(R.drawable.ic_ok_outline) else painterResource(R.drawable.ic_error_outline),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.label_backups_encrypted),
                        style = OlvidTypography.body2,
                        color = colorResource(R.color.greyTint),
                    )

                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorResource(R.color.lighterGrey)),
                        onClick = {
                            activity?.let {
                                it.navigateToSettingsFragment(BackupV2SecurityPreferenceFragment::class.java.name)
                                if (SettingsActivity.useApplicationLockScreen()) {
                                    UnifiedForegroundService.lockApplication(it, R.string.message_unlock_before_showing_backup_key)
                                }
                            }
                        },
                        shape = RectangleShape,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorResource(R.color.olvid_gradient_light)
                        )
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, true),
                            text = stringResource(R.string.pref_security_title),
                            textAlign = TextAlign.Start,
                            style = OlvidTypography.body1,
                        )
                        Spacer(Modifier.width(16.dp))
                        Image(
                            painter = painterResource(R.drawable.pref_widget_chevron_right),
                            colorFilter = ColorFilter.tint(LocalContentColor.current),
                            contentDescription = null,
                        )
                    }


                    if (viewModel.showManageBackupsDialog.value) {
                        val navController = rememberNavController()

                        ManageBackupsDialog(
                            navController = navController,
                            deviceBackupSeed = viewModel.deviceBackupSeed,
                            backupSeedError = viewModel.backupSeedError,
                            showingBackupsForOtherKey = viewModel.showingBackupsForOtherKey,
                            selectedDeviceBackupProfile = viewModel.selectedProfileBackup,
                            fetchingDeviceBackup = viewModel.fetchingDeviceBackup,
                            fetchingProfileBackups = viewModel.fetchingProfileBackups,
                            fetchError = viewModel.fetchError,
                            deviceBackupProfiles = viewModel.deviceBackup.value,
                            profileBackupSnapshots = viewModel.profileBackups.value,
                            onFetchForDevice = {
                                viewModel.fetchDeviceBackup(viewModel.showingBackupsForOtherKey.value)
                            },
                            onFetchForProfile = {
                                viewModel.fetchProfileBackups()
                            },
                            onCancelCurrentFetch = {
                                viewModel.cancelCurrentFetch(true)
                            },
                            onLoadFromCredentialsManager = if (viewModel.credentialManagerAvailable.value == true) {
                                { onLoad ->
                                    credentialManager.loadCredentials(activity = activity, executor = executor, onNoCredential = {
                                        App.toast(R.string.toast_message_no_key_found, Toast.LENGTH_SHORT)
                                    }, onLoad = onLoad)
                                }
                            } else null,
                            onValidateSeed = {
                                viewModel.validateDeviceBackupSeed()
                            },
                            onRestoreSnapshot = { profileBackupSnapshot ->
                                viewModel.selectedProfileBackup.value?.let { selectedProfile ->
                                    OnboardingFlowActivity.snapshotToRestore = Triple(
                                        selectedProfile,
                                        profileBackupSnapshot,
                                        viewModel.selectedProfileDeviceList.value
                                    )
                                    val intent = Intent(context, OnboardingFlowActivity::class.java)
                                    intent.putExtra(OnboardingFlowActivity.RESTORE_BACKUP_INTENT_EXTRA, true)
                                    startActivity(intent)

                                    viewModel.resetYourBackups()
                                }
                            },
                            onDeleteSnapshot = { profileBackupSnapshot ->
                                viewModel.deleteProfileSnapshot(profileBackupSnapshot)
                            },
                            onDismiss = {
                                viewModel.resetYourBackups()
                            },
                        )
                    }

                    if (viewModel.showBackupFailed.value) {
                        BackupFailedDialog(
                            onDismiss = {
                                viewModel.showBackupFailed.value = false
                            },
                        )
                    }

                    LaunchedEffect(viewModel.backupNowState.value) {
                        if (viewModel.backupNowState.value == BackupNowState.FAILED) {
                            viewModel.showBackupFailed.value = true
                        }
                    }

                    LaunchedEffect(Unit) {
                        App.runThread {
                            AppSingleton.getBytesCurrentIdentity()?.let {
                                hasMultiDeviceLicence = AppDatabase.getInstance()
                                    .ownedIdentityDao()[it]?.hasMultiDeviceApiKeyPermission() == true
                                hasMultipleDevices =
                                    AppDatabase.getInstance().ownedDeviceDao()
                                        .getAllSync(it).size > 1
                            }
                            showTip = true
                        }
                    }
                    AnimatedVisibility(
                        visible = showTip && (googleServicesAvailable || hasMultiDeviceLicence),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        BackupsTip(hasMultiDeviceLicence = hasMultiDeviceLicence,
                            hasMultipleDevices = hasMultipleDevices,
                            subscribeClick = {
                                showPurchaseFragment = true
                            },
                            addDeviceClick = {
                                App.startTransferFlowAsSource(this@BackupV2PreferenceFragment.context)
                            })
                        if (showPurchaseFragment) {
                            SubscriptionOfferDialog(
                                activity = activity,
                                onDismissCallback = { showPurchaseFragment = false },
                                onPurchaseCallback = {
                                    // wait for the purchase acknowledged notification
                                    purchaseEngineListener = object :
                                        SimpleEngineNotificationListener(EngineNotifications.VERIFY_RECEIPT_SUCCESS) {
                                        override fun callback(userInfo: HashMap<String, Any>?) {
                                            AppSingleton.getEngine().removeNotificationListener(
                                                EngineNotifications.VERIFY_RECEIPT_SUCCESS,
                                                purchaseEngineListener
                                            )
                                            purchaseEngineListener = null
                                            hasMultiDeviceLicence = true
                                        }
                                    }
                                    AppSingleton.getEngine().addNotificationListener(
                                        EngineNotifications.VERIFY_RECEIPT_SUCCESS,
                                        purchaseEngineListener
                                    )
                                })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        useCredentialManager.value = SettingsActivity.useCredentialsManagerForBackups
    }
}

fun CredentialManager.loadCredentials(activity: FragmentActivity?, executor: Executor, onNoCredential: (() -> Unit)? = null, onLoad: (String) -> Unit) {
    val request = GetCredentialRequest(
        credentialOptions = listOf(
            GetPasswordOption()
        ),
    )
    Logger.i("Requesting credentials from password manager")

    activity?.let {
        getCredentialAsync(it, request, null, executor,
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    (result.credential as? PasswordCredential)?.let {
                        onLoad.invoke(it.password)
                    }
                }

                override fun onError(e: GetCredentialException) {
                    if (e is NoCredentialException) {
                        Logger.i("No credentials available")
                        onNoCredential?.invoke()
                    } else {
                        Logger.x(e)
                    }
                }
            }
        )
    }
}

@Preview
@Composable
fun BackupsHeader() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colorResource(R.color.lighterGrey))
            .padding(all = 16.dp),
        verticalArrangement = spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            modifier = Modifier.size(80.dp),
            painter = painterResource(R.drawable.ic_backup),
            contentDescription = null,
        )
        Text(
            text = stringResource(R.string.label_backup_headers_title),
            style = OlvidTypography.h1.copy(
                fontWeight = FontWeight.Medium
            ),
            color = colorResource(R.color.almostBlack),
        )
        Text(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(
                        bounded = false,
                        color = colorResource(R.color.blueOverlay)
                    )
                ) {
                    try {
                        App.openLink(
                            context,
                            context.getString(R.string.backup_faq_link).toUri()
                        )
                    } catch (_: Exception) {}
                },
            text = buildAnnotatedString {
                append(AnnotatedString(stringResource(R.string.explanation_backups)).formatMarkdown())
                append(" ")
                withStyle(SpanStyle(color = colorResource(id = R.color.olvid_gradient_light))) {
                    append(stringResource(R.string.label_learn_more))
                }
            },
            style = OlvidTypography.body1,
            color = colorResource(R.color.almostBlack),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun EnableBackupsV2Card(
    hasLegacyBackups: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 512.dp)
            .fillMaxWidth()
            .padding(all = 16.dp)
            .border(
                width = 1.dp,
                color = colorResource(R.color.greyTint),
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .background(colorResource(R.color.lighterGrey))
            .padding(top = 16.dp, start = 12.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = spacedBy(8.dp)
        ) {
            Image(
                modifier = Modifier
                    .width(24.dp),
                painter = painterResource(R.drawable.ic_backup),
                contentDescription = null
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.enable_backups_title),
                    color = colorResource(R.color.almostBlack),
                    style = OlvidTypography.h3
                )
                Text(
                    text = if (hasLegacyBackups)
                        stringResource(R.string.enable_backups_message_with_legacy)
                    else
                        stringResource(R.string.enable_backups_message),
                    color = colorResource(R.color.greyTint),
                    style = OlvidTypography.body1
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                modifier = Modifier.height(40.dp),
                colors = ButtonDefaults.buttonColors().copy(
                    contentColor = colorResource(R.color.alwaysWhite),
                    containerColor = colorResource(R.color.olvid_gradient_light)
                ),
                elevation = null,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                onClick = onClick,
            ) {
                Text(
                    text = if (hasLegacyBackups)
                        stringResource(R.string.button_label_switch_to_new_backups)
                    else
                        stringResource(R.string.button_label_enable_backups)
                )
            }
        }
    }
}

@Preview
@Composable
private fun EnablePreview() {
    Column {
        EnableBackupsV2Card(hasLegacyBackups = true, onClick = {})
        EnableBackupsV2Card(hasLegacyBackups = false, onClick = {})
    }
}

@Composable
fun BackupsTip(
    hasMultiDeviceLicence: Boolean,
    hasMultipleDevices: Boolean,
    subscribeClick: (() -> Unit)? = null,
    addDeviceClick: (() -> Unit)? = null,
) {
        Column(
            modifier = Modifier
                .widthIn(max = 512.dp)
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = colorResource(R.color.greyTint),
                    shape = RoundedCornerShape(10.dp)
                )
                .clip(RoundedCornerShape(10.dp))
                .background(colorResource(R.color.lighterGrey))
                .padding(top = 16.dp, start = 12.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = spacedBy(8.dp)
            ) {
                Image(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(horizontal = 4.dp),
                    painter = painterResource(R.drawable.ic_tip_bulb),
                    contentDescription = null
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.backup_tip_title),
                        color = colorResource(R.color.almostBlack),
                        style = OlvidTypography.h3
                    )
                    Text(
                        text = stringResource(R.string.backup_tip_message_multi_device),
                        color = colorResource(R.color.greyTint),
                        style = OlvidTypography.body1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    modifier = Modifier
                        .size(width = 64.dp, height = 25.dp),
                    painter = painterResource(R.drawable.ic_multiple_devices),
                    contentDescription = null
                )
                if (!hasMultipleDevices || !hasMultiDeviceLicence) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors().copy(
                            contentColor = colorResource(R.color.alwaysWhite),
                            containerColor = colorResource(R.color.olvid_gradient_light)
                        ),
                        elevation = null,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        onClick = (
                                if (!hasMultiDeviceLicence)
                                    subscribeClick
                                else
                                    addDeviceClick
                                ) ?: {},
                    ) {
                        Text(
                            text = if (!hasMultiDeviceLicence)
                                stringResource(R.string.button_label_subscribe_olvid_plus)
                            else
                                stringResource(R.string.button_label_add_device),
                        )
                    }
                }
            }
    }
}

@Preview
@Composable
private fun TipPreview() {
    Column {
        BackupsTip(hasMultiDeviceLicence = false, hasMultipleDevices = false)
        BackupsTip(hasMultiDeviceLicence = true, hasMultipleDevices = false)
        BackupsTip(hasMultiDeviceLicence = true, hasMultipleDevices = true)
    }
}


@Composable
fun UseCredentialManagerSwitch(
    useCredentialManager: MutableState<Boolean>,
    onToggleUseCredentialManager: (Boolean) -> Unit,
    onChooseCredentialManager: (()->Unit)? = null,
) {
    Column (
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colorResource(R.color.lighterGrey))
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Row (
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    useCredentialManager.value = !useCredentialManager.value
                    onToggleUseCredentialManager.invoke(useCredentialManager.value)
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(weight = 1f, fill = true),
                text = stringResource(R.string.pref_use_credentials_manager_title),
                style = OlvidTypography.body1,
                color = colorResource(R.color.almostBlack),
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = useCredentialManager.value,
                onCheckedChange = {checked ->
                    useCredentialManager.value = checked
                    onToggleUseCredentialManager.invoke(useCredentialManager.value)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = colorResource(R.color.olvid_gradient_light)),
                interactionSource = interactionSource,
            )
        }

        if (onChooseCredentialManager != null) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colorResource(R.color.lightGrey)
            )

            TextButton(
                modifier = Modifier.heightIn(min = 56.dp),
                onClick = onChooseCredentialManager,
                shape = RectangleShape,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorResource(R.color.olvid_gradient_light)
                )
            ) {
                Text(
                    modifier = Modifier.weight(weight = 1f, fill = true),
                    text = stringResource(R.string.label_choose_default_password_manager),
                    style = OlvidTypography.body1,
                    textAlign = TextAlign.Start,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Image(
                    modifier = Modifier
                        .size(24.dp),
                    painter = painterResource(R.drawable.pref_widget_chevron_right),
                    colorFilter = ColorFilter.tint(LocalContentColor.current),
                    contentDescription = null
                )
            }
        }
    }
}

@Preview
@Composable
fun CredSwitchPreview() {
    UseCredentialManagerSwitch(
        useCredentialManager = remember { mutableStateOf(false) },
        onToggleUseCredentialManager = {},
        onChooseCredentialManager = { }
    )
}
