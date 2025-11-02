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

package io.olvid.messenger.plus_button.scan

import android.content.res.Configuration
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.identities.ObvUrlIdentity
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.plus_button.PlusButtonViewModel
import io.olvid.messenger.plus_button.ScanUiState
import io.olvid.messenger.plus_button.configuration.ConfigurationScannedScreen
import io.olvid.messenger.plus_button.configuration.WebClientScannedScreen
import io.olvid.messenger.plus_button.share.ShareDialog
import kotlinx.coroutines.delay

enum class DragValue {
    Collapsed,
    HalfExpanded,
    Expanded
}

// Delay before showing alternative scan option
const val scanAlternativeDelay = 5_000L

@Composable
fun ScanScreen(
    onCancel: () -> Unit,
    scanOnly: Boolean = false,
    plusButtonViewModel: PlusButtonViewModel = viewModel(),
    activity: AppCompatActivity
) {
    val context = LocalContext.current
    val currentUiState by plusButtonViewModel.scanUiState.collectAsState()
    var isScanOnly by remember { mutableStateOf(scanOnly) }

    DisposableEffect(Unit) {
        onDispose {
            plusButtonViewModel.resetScanState()
        }
    }

    val ownedIdentity by AppSingleton.getCurrentIdentityLiveData().observeAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Bottom sheet state
        val density = LocalDensity.current
        val bottomSheetState = remember {
            AnchoredDraggableState(
                initialValue = if (isScanOnly) DragValue.Collapsed else DragValue.HalfExpanded,
                positionalThreshold = { distance: Float -> distance * 0.5f },
                velocityThreshold = { 100f },
                snapAnimationSpec = spring(),
                decayAnimationSpec = splineBasedDecay(density),
                confirmValueChange = { true }
            )
        }
        val navigationBarHeight = with(density) { WindowInsets.safeDrawing.getBottom(this) }.toFloat()
        val statusBarHeight = with(density) { WindowInsets.safeDrawing.getTop(this) }.toFloat()
        val screenHeight = maxHeight.value*density.density
        val collapsedOffset = screenHeight - navigationBarHeight - with(density) { 37.dp.toPx() }
        val halfExpandedOffset = (screenHeight - navigationBarHeight + statusBarHeight) / 2

        bottomSheetState.updateAnchors(
            DraggableAnchors {
                DragValue.Collapsed at if (plusButtonViewModel.isDeepLinked) statusBarHeight else collapsedOffset
                DragValue.HalfExpanded at if (plusButtonViewModel.isDeepLinked) statusBarHeight else halfExpandedOffset
                DragValue.Expanded at statusBarHeight
            }
        )

        LaunchedEffect(isScanOnly) {
            if (isScanOnly) {
                bottomSheetState.animateTo(DragValue.Collapsed)
            } else {
                bottomSheetState.animateTo(DragValue.HalfExpanded)
            }
        }

        LaunchedEffect(currentUiState) {
            when (currentUiState) {
                ScanUiState.WebClientScanned,
                ScanUiState.ConfigurationScanned ->
                    bottomSheetState.animateTo(DragValue.Expanded)

                is ScanUiState.InvitationScanned, is ScanUiState.MutualScanError, is ScanUiState.MutualScanProcessing ->
                    bottomSheetState.animateTo(DragValue.HalfExpanded)

                ScanUiState.UrlScan -> {
                    plusButtonViewModel.scannedUri?.let { url ->
                        App.openLink(context, url.toUri())
                    }
                }

                ScanUiState.TextScan -> {
                    plusButtonViewModel.scannedUri?.let { text ->
                        App.displayText(context, text)
                    }
                }

                else -> {}
            }
        }

        LaunchedEffect(ownedIdentity) {
            ownedIdentity?.let { ownedIdentity ->
                plusButtonViewModel.currentIdentity = ownedIdentity
                val identityDetails = ownedIdentity.getIdentityDetails()
                val urlIdentity = if (identityDetails != null) {
                    ObvUrlIdentity(
                        ownedIdentity.bytesOwnedIdentity,
                        identityDetails.formatDisplayName(
                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                            false
                        )
                    )
                } else {
                    ObvUrlIdentity(ownedIdentity.bytesOwnedIdentity, ownedIdentity.displayName)
                }
                plusButtonViewModel.getQrImage(urlIdentity.getUrlRepresentation(true))
            }
        }

        DisposableEffect(plusButtonViewModel.mutualScanDiscussionId) {
            plusButtonViewModel.mutualScanDiscussionId?.let { discussionId ->
                val liveData = AppDatabase.getInstance().discussionDao().getByIdAsync(discussionId)
                liveData.observe(activity) { discussion ->
                    if (discussion.isLocked.not()) {
                        App.openDiscussionActivity(context, discussionId)
                        onCancel()
                    }
                }
                onDispose {
                    liveData.removeObservers(activity)
                }
            } ?: onDispose {  }
        }


        // Scan alternative dialog scan dialog
        var shareDialogExpanded by rememberSaveable { mutableStateOf(false) }
        if (shareDialogExpanded) {
            ShareDialog(onDismissRequest = {
                shareDialogExpanded = false
                onCancel()
            })
        }

        val currentOffset = bottomSheetState.requireOffset()
        val progress = ((collapsedOffset - currentOffset) / collapsedOffset).coerceIn(0f, 1f)


        val isMutualScanActive = currentUiState is ScanUiState.MutualScanProcessing ||
                currentUiState is ScanUiState.MutualScanPending ||
                currentUiState is ScanUiState.MutualScanSuccess ||
                currentUiState is ScanUiState.MutualScanError

        val largeScreen = maxWidth > 600.dp && LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (largeScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (plusButtonViewModel.isDeepLinked.not()) {
                    QrCodeScanner(
                        modifier = Modifier.weight(1f),
                        largeScreen = true,
                        currentUiState = currentUiState,
                        progress = .5f,
                        onCancel = onCancel,
                        onQrCodeScanned = { url ->
                            plusButtonViewModel.handleLink(activity, url)
                        },
                        plusButtonViewModel = plusButtonViewModel,
                    )
                }
                BottomSheetContent(
                    modifier = Modifier.weight(1f),
                    largeScreen = true,
                    currentUiState = currentUiState,
                    isMutualScanActive = isMutualScanActive,
                    progress = .9f,
                    openShareDialog = { shareDialogExpanded = true },
                    onCancel = onCancel,
                    plusButtonViewModel = plusButtonViewModel,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                if (plusButtonViewModel.isDeepLinked.not()) {
                    QrCodeScanner(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) {
                                bottomSheetState.requireOffset().toDp()
                            }),
                        currentUiState = currentUiState,
                        progress = progress,
                        onCancel = onCancel,
                        onQrCodeScanned = { url ->
                            plusButtonViewModel.handleLink(activity, url)
                        },
                        plusButtonViewModel = plusButtonViewModel,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = .8f))
                    )
                }

                // Bottom Sheet
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = ((bottomSheetState.requireOffset() / density.density).coerceAtLeast(0f)).dp)
                        .anchoredDraggable(bottomSheetState, Orientation.Vertical)
                ) {
                    BottomSheetContent(
                        modifier = Modifier.fillMaxSize(),
                        currentUiState = currentUiState,
                        isMutualScanActive = isMutualScanActive,
                        progress = progress,
                        openShareDialog = { shareDialogExpanded = true },
                        onCancel = onCancel,
                        plusButtonViewModel = plusButtonViewModel,
                    )
                }
            }
        }
    }
}

@Composable
fun BottomSheetContent(
    modifier: Modifier = Modifier,
    largeScreen: Boolean = false,
    currentUiState: ScanUiState,
    isMutualScanActive: Boolean,
    progress: Float,
    openShareDialog: () -> Unit,
    onCancel: () -> Unit,
    plusButtonViewModel: PlusButtonViewModel,
) {
    var showScanAlternative by remember { mutableStateOf(false) }
    var showDirectInviteButton by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(scanAlternativeDelay)
        showScanAlternative = true
    }
    LaunchedEffect(plusButtonViewModel.scanUiState.collectAsState().value) {
        if ((plusButtonViewModel.scanUiState.value as? ScanUiState.InvitationScanned)?.remoteInvitation == true) {
            showDirectInviteButton = true
            showScanAlternative = true
        }
    }

    val qrCodeScale = if (largeScreen) .65f else (.5f + progress * .45f) // scale from 50% to 95%

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (largeScreen)
                    Modifier
                        .background(color = colorResource(R.color.lightGrey))
                        .windowInsetsPadding(WindowInsets.Companion.safeDrawing.only(WindowInsetsSides.Companion.End))
                else
                    Modifier
                        .background(
                            color = colorResource(R.color.lightGrey),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ),
    ) {
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.TopEnd),
            visible = progress > 0.9,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            IconButton(
                colors = IconButtonDefaults.iconButtonColors().copy(
                    contentColor = colorResource(R.color.darkGrey)
                ),
                onClick = onCancel
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.content_description_close_button)
                )
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (largeScreen) {
                Spacer(Modifier
                    .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues())
                    .height(8.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .width(36.dp)
                        .height(5.dp)
                        .background(
                            color = colorResource(R.color.darkGrey),
                            shape = CircleShape
                        )
                )
            }

            AnimatedVisibility(
                visible = progress > 0,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {

                    if (isMutualScanActive) {
                        MutualScanOverlay(uiState = currentUiState, onFinish = onCancel)
                    } else if (currentUiState == ScanUiState.ConfigurationScanned) {
                        ConfigurationScannedScreen(
                            modifier = Modifier.fillMaxWidth(),
                            plusButtonViewModel = plusButtonViewModel,
                            onCancel = onCancel
                        )
                    } else if (currentUiState == ScanUiState.WebClientScanned) {
                        WebClientScannedScreen(
                            viewModel = plusButtonViewModel,
                            onFinish = onCancel
                        )
                    } else {
                        when (currentUiState) {
                            is ScanUiState.IdleScanning -> {
                                Text(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    text = stringResource(R.string.scan_screen_mutual_scan_title),
                                    style = OlvidTypography.h2.copy(color = colorResource(R.color.almostBlack)),
                                    textAlign = TextAlign.Center
                                )
                            }

                            is ScanUiState.InvitationScanned -> {
                                Text(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    text = stringResource(R.string.scan_screen_mutual_scan_yours_title, StringUtils.removeCompanyFromDisplayName(currentUiState.contactUrlIdentity.displayName)),
                                    style = OlvidTypography.h2.copy(color = colorResource(R.color.almostBlack)),
                                    textAlign = TextAlign.Center
                                )
                            }

                            else -> {}
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            plusButtonViewModel.currentIdentity?.let { currentIdentity ->
                                val activity = LocalActivity.current
                                DisposableEffect(Unit) {
                                    activity?.window?.apply {
                                        attributes?.apply {
                                            screenBrightness =
                                                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                                        }
                                        addFlags(WindowManager.LayoutParams.SCREEN_BRIGHTNESS_CHANGED)
                                    }

                                    onDispose {
                                        activity?.window?.apply {
                                            attributes?.apply {
                                                screenBrightness =
                                                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                            }
                                            addFlags(WindowManager.LayoutParams.SCREEN_BRIGHTNESS_CHANGED)
                                        }
                                    }
                                }

                                Box(
                                    Modifier
                                        .clipToBounds()
                                        .padding(top = 32.dp, bottom = 16.dp)
                                        .weight(1f, false)
                                        .fillMaxWidth(qrCodeScale)
                                        .requiredHeightIn(min = 160.dp)
                                        .aspectRatio(1f, true)
                                        .background(
                                            color = Color.White,
                                            shape = RoundedCornerShape(24.dp)
                                        ),
                                ) {
                                    plusButtonViewModel.qrImageBitmap?.let {
                                        Image(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(20.dp),
                                            contentScale = ContentScale.Fit,
                                            bitmap = it,
                                            contentDescription = "QR Code"
                                        )
                                    }
                                    InitialView(
                                        modifier = Modifier
                                            .size((70 * qrCodeScale).dp)
                                            .align(Alignment.TopCenter)
                                            .offset(y = (-25 * qrCodeScale).dp)
                                            .border(
                                                width = 5.dp,
                                                color = colorResource(R.color.alwaysWhite),
                                                shape = CircleShape
                                            ),
                                        initialViewSetup = {
                                            it.setShowBadges(false)
                                            it.setOwnedIdentity(currentIdentity)
                                        }
                                    )
                                }
                            }

                            var showInvitationDialog by remember { mutableStateOf(false) }
                            (currentUiState as? ScanUiState.InvitationScanned)?.let { invitationScanned ->
                                if (showInvitationDialog) {
                                    AlertDialog(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        properties = DialogProperties(usePlatformDefaultWidth = false),
                                        containerColor = colorResource(R.color.dialogBackground),
                                        onDismissRequest = { showInvitationDialog = false },
                                        text = {
                                            Column(
                                                horizontalAlignment = Alignment.Start,
                                                verticalArrangement = spacedBy(24.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.label_get_in_contact_with, StringUtils.removeCompanyFromDisplayName(invitationScanned.contactUrlIdentity.displayName)),
                                                    style = OlvidTypography.h2
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    InitialView(
                                                        modifier = Modifier.size(40.dp),
                                                        initialViewSetup = { initialView ->
                                                            initialView.setInitial(invitationScanned.contactUrlIdentity.bytesIdentity, StringUtils.getInitial(invitationScanned.contactUrlIdentity.displayName))
                                                        }
                                                    )
                                                    Text(
                                                        modifier = Modifier
                                                            .padding(start = 8.dp)
                                                            .weight(1f, true),
                                                        text = invitationScanned.contactUrlIdentity.displayName
                                                    )
                                                }
                                            }
                                        },
                                        textContentColor = colorResource(R.color.almostBlack),
                                        dismissButton = {
                                            OlvidTextButton(
                                                contentColor = colorResource(R.color.greyTint),
                                                text = stringResource(R.string.button_label_cancel),
                                                onClick = { showInvitationDialog = false },
                                            )
                                        },
                                        confirmButton = {
                                            val context = LocalContext.current
                                            OlvidTextButton(
                                                text = stringResource(R.string.button_label_get_in_contact_remotely),
                                                onClick = {
                                                    runCatching {
                                                        AppSingleton.getEngine()
                                                            .startTrustEstablishmentProtocol(
                                                                invitationScanned.contactUrlIdentity.bytesIdentity,
                                                                invitationScanned.contactUrlIdentity.displayName,
                                                                plusButtonViewModel.currentIdentity!!.bytesOwnedIdentity
                                                            )
                                                        App.openOneToOneDiscussionActivity(
                                                            context,
                                                            plusButtonViewModel.currentIdentity!!.bytesOwnedIdentity,
                                                            invitationScanned.contactUrlIdentity.bytesIdentity,
                                                            true
                                                        )
                                                        showInvitationDialog = false
                                                        onCancel()
                                                    }.onFailure {
                                                        App.toast(
                                                            R.string.toast_message_failed_to_invite_contact,
                                                            Toast.LENGTH_SHORT
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    )
                                }
                            }
                            AnimatedVisibility(visible = showScanAlternative) {
                                if (currentUiState is ScanUiState.InvitationScanned && showDirectInviteButton) {
                                    OlvidActionButton(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        text = stringResource(R.string.button_label_get_in_contact_remotely)
                                    ) {
                                        showInvitationDialog = true
                                    }
                                } else {
                                    TextButton(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.textButtonColors().copy(
                                            contentColor = colorResource(R.color.almostBlack)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = {
                                            if (currentUiState is ScanUiState.InvitationScanned) {
                                                showInvitationDialog = true
                                            } else {
                                                openShareDialog()
                                            }
                                        }
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                if (currentUiState is ScanUiState.InvitationScanned)
                                                    stringResource(
                                                        R.string.scan_screen_share_contact_cant_scan_title,
                                                        StringUtils.removeCompanyFromDisplayName(currentUiState.contactUrlIdentity.displayName)
                                                    )
                                                else
                                                    stringResource(
                                                        R.string.scan_screen_share_invitation_title
                                                    ),
                                                style = OlvidTypography.body2
                                            )
                                            Text(
                                                text = stringResource(R.string.scan_screen_share_invitation_subtitle),
                                                style = OlvidTypography.body2.copy(
                                                    color = colorResource(R.color.olvid_gradient_light),
                                                    fontWeight = FontWeight.Medium,
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(
                                modifier = Modifier
                                    .height(WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QrCodeScanner(
    modifier: Modifier,
    largeScreen: Boolean = false,
    currentUiState: ScanUiState,
    progress: Float,
    onCancel: () -> Unit,
    onQrCodeScanned: (String) -> Unit,
    plusButtonViewModel: PlusButtonViewModel,
) {
    var useFrontCamera by rememberSaveable { mutableStateOf(false) }
    val fraction = (.9f - progress * .6f).coerceAtLeast(.6f) // from .9f to .6f when progress is from 0 to .5f

    // Camera and Reticle
    Box(
        modifier = modifier
    ) {

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                paused = currentUiState !is ScanUiState.IdleScanning,
                useFrontCamera = useFrontCamera,
                onQrCodeScanned = onQrCodeScanned,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (currentUiState in listOf(
                            ScanUiState.UrlScan,
                            ScanUiState.TextScan)) {
                        Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = {
                                plusButtonViewModel.updateScanState(ScanUiState.IdleScanning)
                            }
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            if (currentUiState == ScanUiState.IdleScanning) {
                ScanningReticle(brush = SolidColor(Color.White), fraction = fraction)
            }
            if (currentUiState in listOf(
                    ScanUiState.UrlScan,
                    ScanUiState.TextScan,
                    ScanUiState.ConfigurationScanned,
                    ScanUiState.WebClientScanned
                )
                || currentUiState is ScanUiState.InvitationScanned
                || currentUiState is ScanUiState.MutualScanPending
                || currentUiState is ScanUiState.MutualScanProcessing
                || currentUiState is ScanUiState.MutualScanSuccess
                || currentUiState is ScanUiState.MutualScanError
            ) {
                ScanningReticle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6BB700),
                            colorResource(R.color.olvid_gradient_light)
                        )
                    ),
                    fraction = fraction
                )
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f, false).height(48.dp))
                    Image(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF589602), CircleShape)
                            .padding(8.dp)
                            .fillMaxSize(),
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = "Success"
                    )
                    Text(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .background(
                                color = colorResource(R.color.blackDarkOverlay),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        text = stringResource(R.string.label_scan_screen_code_scanned),
                        style = OlvidTypography.h3.copy(color = colorResource(R.color.alwaysWhite))
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = progress < 0.8,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ScanTopBar(
                modifier = Modifier
                    .then(
                    if (largeScreen)
                        Modifier.windowInsetsPadding(WindowInsets.Companion.safeDrawing.only(WindowInsetsSides.Companion.Start))
                    else
                        Modifier
                ),
                onCancelClick = onCancel,
                onSwitchCamera = {
                    useFrontCamera = !useFrontCamera
                }.takeIf { currentUiState == ScanUiState.IdleScanning })
        }
    }
}

@Composable
fun ColumnScope.MutualScanOverlay(
    uiState: ScanUiState,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    Spacer(Modifier.weight(1f))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                colorResource(R.color.almostWhite),
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (uiState) {
            is ScanUiState.MutualScanProcessing -> {
                OlvidCircularProgress(modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(
                    text = stringResource(
                        R.string.text_explanation_mutual_scan_pending,
                        uiState.contactName
                    ),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center
                )
            }

            is ScanUiState.MutualScanPending -> {
                Text(
                    text = stringResource(
                        R.string.text_explanation_mutual_scan_pending,
                        uiState.contactName
                    ),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.almostBlack),
                    textAlign = TextAlign.Center
                )
                OlvidTextButton(
                    text = stringResource(R.string.button_label_ok),
                    onClick = onFinish
                )
            }

            is ScanUiState.MutualScanSuccess -> {
                if (uiState.timeout) {
                    Text(
                        text = stringResource(
                            R.string.text_explanation_mutual_scan_success,
                            uiState.contactName
                        ),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.almostBlack),
                        textAlign = TextAlign.Center
                    )
                    OlvidTextButton(
                        text = stringResource(
                            R.string.button_label_discuss_with,
                            uiState.contactName
                        ),
                        onClick = {
                            App.openDiscussionActivity(context, uiState.discussionId)
                            onFinish()
                        }
                    )
                } else {
                    App.openDiscussionActivity(context, uiState.discussionId)
                    onFinish()
                }
            }

            is ScanUiState.MutualScanError -> {
                Text(
                    text = uiState.message,
                    style = OlvidTypography.body1,
                    textAlign = TextAlign.Center,
                    color = colorResource(R.color.red)
                )
                OlvidTextButton(
                    text = stringResource(R.string.button_label_ok),
                    onClick = onFinish
                )
            }

            else -> {} // Should not happen
        }
    }
    Spacer(Modifier.weight(1f))
    Spacer(
        modifier = Modifier
            .height(WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MutualScanOverlayPreview() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {MutualScanOverlay(ScanUiState.MutualScanPending("John Doe")) { } }
}


@Composable
fun ScanTopBar(modifier: Modifier = Modifier, onCancelClick: () -> Unit, onSwitchCamera: (() -> Unit)?) {
    Row(modifier = modifier
        .statusBarsPadding()
        .height(48.dp)
        .padding(horizontal = 8.dp)) {
        OlvidTextButton(
            text = stringResource(R.string.button_label_cancel),
            large = true,
            contentColor = colorResource(R.color.alwaysWhite)
        ) {
            onCancelClick()
        }
        Spacer(modifier = Modifier.weight(1f))
        onSwitchCamera?.let {
            IconButton(
                colors = IconButtonDefaults.iconButtonColors().copy(
                    contentColor = colorResource(R.color.alwaysWhite)
                ),
                onClick = onSwitchCamera
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera_switch),
                    contentDescription = stringResource(R.string.content_description_switch_camera)
                )
            }
        }
    }
}


@Preview
@Composable
private fun ScanTopBarPreview() {
    Box(
        Modifier.background(Color.Black)
    ) {
        ScanTopBar(
            modifier = Modifier.height(48.dp),
            onCancelClick = { }
        ) { }
    }
}