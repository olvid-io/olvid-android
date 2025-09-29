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
package io.olvid.messenger.plus_button.configuration

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.invitations.BoxedChar
import io.olvid.messenger.onboarding.flow.animations.shake
import io.olvid.messenger.plus_button.PlusButtonViewModel
import io.olvid.messenger.services.UnifiedForegroundService
import io.olvid.messenger.services.UnifiedForegroundService.ServiceBinder
import io.olvid.messenger.services.UnifiedForegroundService.WebClientSubService
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.webclient.WebClientManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed class WebClientUiState {
    object Connecting : WebClientUiState()
    object EnterSas : WebClientUiState()
    object SasError : WebClientUiState()
    object Success : WebClientUiState()
    object AlreadyRunning : WebClientUiState()
    object Error : WebClientUiState()
}

@Composable
fun WebClientScannedScreen(
    viewModel: PlusButtonViewModel,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var webClientService by remember { mutableStateOf<WebClientSubService?>(null) }
    var isBound by remember { mutableStateOf(false) }
    var uiState by remember { mutableStateOf<WebClientUiState>(WebClientUiState.Connecting) }
    var sasCode by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val binder = service as? ServiceBinder
                val serviceInstance = binder?.webClientService
                if (serviceInstance?.manager == null) {
                    onFinish()
                    return
                }
                webClientService = serviceInstance
                isBound = true

                if (serviceInstance.manager.currentState == WebClientManager.State.ERROR ||
                    serviceInstance.manager.currentState == WebClientManager.State.FINISHING
                ) {
                    onFinish()
                    return
                }

                if (serviceInstance.isAlreadyRunning && serviceInstance.currentState == WebClientManager.State.WAITING_FOR_RECONNECTION) {
                    serviceInstance.restartService()
                } else if (serviceInstance.isAlreadyRunning) {
                    uiState = WebClientUiState.AlreadyRunning
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
                webClientService = null
                if (uiState != WebClientUiState.Success) {
                    onFinish()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val scannedUri = viewModel.scannedUri
        if (scannedUri == null) {
            onFinish()
            return@DisposableEffect onDispose {}
        }

        val eventBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WebClientSubService.READY_FOR_BIND_BROADCAST_ACTION) {
                    val bindIntent = Intent(context, UnifiedForegroundService::class.java).apply {
                        putExtra(
                            UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA,
                            UnifiedForegroundService.SUB_SERVICE_WEB_CLIENT
                        )
                    }
                    context.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                    LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
                }
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(
            eventBroadcastReceiver,
            IntentFilter(WebClientSubService.READY_FOR_BIND_BROADCAST_ACTION)
        )

        val connectIntent = Intent(context, UnifiedForegroundService::class.java).apply {
            putExtra(
                UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA,
                UnifiedForegroundService.SUB_SERVICE_WEB_CLIENT
            )
            action = WebClientSubService.ACTION_CONNECT
            putExtra(WebClientSubService.CONNECTION_DATA_INTENT_EXTRA, scannedUri)
        }
        context.startService(connectIntent)

        onDispose {
            runCatching {
                if (isBound) {
                    context.unbindService(serviceConnection)
                    isBound = false
                    webClientService?.onUnbind()
                }
            }
            runCatching { LocalBroadcastManager.getInstance(context).unregisterReceiver(eventBroadcastReceiver) }
        }
    }

    DisposableEffect(webClientService?.manager) {
        val service = webClientService ?: return@DisposableEffect onDispose {}

        val activity = context as? FragmentActivity
        if (activity != null && SettingsActivity.useApplicationLockScreen() && SettingsActivity.isWebclientUnlockRequired) {
            UnifiedForegroundService.lockApplication(
                activity,
                R.string.message_unlock_before_web_client
            )
        }

        val sasObserver = Observer<String> { sas ->
            if (sas.isNotEmpty() && uiState != WebClientUiState.AlreadyRunning) {
                uiState = WebClientUiState.EnterSas
            }
        }
        val closingObserver = Observer<Boolean> { closed ->
            if (closed) {
                uiState = WebClientUiState.Error
            }
        }

        service.sasCodeLiveData.observeForever(sasObserver)
        service.serviceClosingLiveData?.observeForever(closingObserver)

        onDispose {
            service.sasCodeLiveData.removeObserver(sasObserver)
            service.serviceClosingLiveData?.removeObserver(closingObserver)
        }
    }

    when (uiState) {
        WebClientUiState.Connecting -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OlvidCircularProgress()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.webclient_protocol_in_progress),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center
                )
            }
        }
        WebClientUiState.EnterSas, WebClientUiState.SasError -> {
            if (uiState == WebClientUiState.EnterSas) {
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .shake(uiState == WebClientUiState.SasError),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.dialog_title_webclient_sas_input),
                    style = OlvidTypography.h2,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(colorResource(R.color.almostWhite), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SasInput(
                        sasCode = sasCode,
                        onSasCodeChange = {
                            if (uiState == WebClientUiState.SasError) {
                                uiState = WebClientUiState.EnterSas
                            }
                            if (it.length <= 4) {
                                sasCode = it
                                if (it.length == 4) {
                                    if (webClientService?.verifySasCode(it) == true) {
                                        keyboardController?.hide()
                                        uiState = WebClientUiState.Success
                                    } else {
                                        coroutineScope.launch {
                                            sasCode = ""
                                            uiState = WebClientUiState.SasError
                                        }
                                    }
                                }
                            }
                        },
                        isError = uiState == WebClientUiState.SasError,
                        focusRequester = focusRequester
                    )
                    AnimatedVisibility(visible = uiState == WebClientUiState.SasError) {
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = stringResource(R.string.webclient_error_sas_incorrect),
                            color = colorResource(R.color.red),
                            style = OlvidTypography.body2
                        )
                    }
                }
            }
        }
        WebClientUiState.Success -> {
            LaunchedEffect(Unit) {
                delay(3000)
                onFinish()
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "Success",
                    tint = colorResource(id = R.color.green),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.webclient_successfully_connected),
                    style = OlvidTypography.h2,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center
                )
            }
        }
        WebClientUiState.AlreadyRunning -> {
            AlertDialog(
                containerColor = colorResource(R.color.dialogBackground),
                onDismissRequest = onFinish,
                title = { Text(stringResource(R.string.label_webclient_already_running), style = OlvidTypography.h2, color = colorResource(R.color.almostBlack)) },
                text = { Text(stringResource(R.string.label_webclient_restart_connection), style = OlvidTypography.h3, color = colorResource(R.color.almostBlack)) },
                confirmButton = {
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_ok),
                        onClick = {
                            webClientService?.restartService()
                            uiState = WebClientUiState.Connecting
                        }
                    )
                },
                dismissButton = {
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        contentColor = colorResource(R.color.greyTint),
                        onClick = onFinish
                    )
                }
            )
        }
        WebClientUiState.Error -> {
            // Generic error, just finish
            LaunchedEffect(Unit) {
                onFinish()
            }
        }
    }
}

@Composable
private fun SasInput(
    sasCode: String,
    onSasCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean,
    focusRequester: FocusRequester
) {
    BasicTextField(
        modifier = modifier.focusRequester(focusRequester).requiredWidth(with(LocalDensity.current) { 160.sp.toDp() }),
        value = sasCode,
        onValueChange = onSasCodeChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { index ->
                    BoxedChar(
                        modifier = Modifier.weight(1f),
                        error = isError,
                        index = index,
                        text = sasCode
                    )
                }
            }
        },
        cursorBrush = SolidColor(Color.Transparent)
    )
}
