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

package io.olvid.messenger.webrtc.components

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetScaffoldState
import androidx.compose.material.BottomSheetValue.Collapsed
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.contacts.ContactListScreen
import io.olvid.messenger.main.contacts.ContactListViewModel
import io.olvid.messenger.webrtc.WebrtcCallService
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.BLUETOOTH
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.HEADSET
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.LOUDSPEAKER
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.MUTED
import io.olvid.messenger.webrtc.WebrtcCallService.AudioOutput.PHONE
import io.olvid.messenger.webrtc.WebrtcCallService.CallParticipantPojo
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.CALL_INITIATION_NOT_SUPPORTED
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.CONTACT_NOT_FOUND
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.COULD_NOT_SEND
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.ICE_CONNECTION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.ICE_SERVER_CREDENTIALS_CREATION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.INTERNAL_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.KICKED
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.NONE
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.PEER_CONNECTION_CREATION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.PERMISSION_DENIED
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.SERVER_AUTHENTICATION_ERROR
import io.olvid.messenger.webrtc.WebrtcCallService.FailReason.SERVER_UNREACHABLE
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CALL_REJECTED
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CONNECTED
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.CONNECTING_TO_PEER
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.HANGED_UP
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.RECONNECTING
import io.olvid.messenger.webrtc.WebrtcCallService.PeerState.START_CALL_MESSAGE_SENT
import io.olvid.messenger.webrtc.WebrtcCallService.State.BUSY
import io.olvid.messenger.webrtc.WebrtcCallService.State.CALL_ENDED
import io.olvid.messenger.webrtc.WebrtcCallService.State.CALL_IN_PROGRESS
import io.olvid.messenger.webrtc.WebrtcCallService.State.CONNECTING
import io.olvid.messenger.webrtc.WebrtcCallService.State.FAILED
import io.olvid.messenger.webrtc.WebrtcCallService.State.GETTING_TURN_CREDENTIALS
import io.olvid.messenger.webrtc.WebrtcCallService.State.INITIAL
import io.olvid.messenger.webrtc.WebrtcCallService.State.INITIALIZING_CALL
import io.olvid.messenger.webrtc.WebrtcCallService.State.RINGING
import io.olvid.messenger.webrtc.WebrtcCallService.State.WAITING_FOR_AUDIO_PERMISSION
import io.olvid.messenger.webrtc.WebrtcPeerConnectionHolder.Companion.localScreenTrack
import io.olvid.messenger.webrtc.WebrtcPeerConnectionHolder.Companion.localVideoTrack
import io.olvid.messenger.webrtc.components.CallAction.AddParticipant
import io.olvid.messenger.webrtc.components.CallAction.GoToDiscussion
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack
import java.util.Locale


@Composable
fun WebrtcCallService.State.humanReadable(failReason: FailReason): String {
    return when (this) {
        INITIAL, INITIALIZING_CALL -> stringResource(id = R.string.webrtc_status_initializing_call)
        WAITING_FOR_AUDIO_PERMISSION -> stringResource(id = R.string.webrtc_status_waiting_for_permission)
        GETTING_TURN_CREDENTIALS -> stringResource(id = R.string.webrtc_status_verifying_credentials)
        RINGING -> stringResource(id = R.string.webrtc_status_ringing)
        CONNECTING -> stringResource(id = R.string.webrtc_status_connecting_to_peer)
        BUSY -> stringResource(id = R.string.webrtc_status_contact_busy)
        CALL_IN_PROGRESS -> ""
        CALL_ENDED -> stringResource(id = R.string.webrtc_status_ending_call)
        FAILED ->
            when (failReason) {
                NONE, CONTACT_NOT_FOUND, INTERNAL_ERROR, ICE_SERVER_CREDENTIALS_CREATION_ERROR, COULD_NOT_SEND -> stringResource(
                    id = R.string.webrtc_failed_internal
                )

                SERVER_UNREACHABLE, PEER_CONNECTION_CREATION_ERROR, SERVER_AUTHENTICATION_ERROR -> stringResource(
                    id = R.string.webrtc_failed_network_error
                )

                PERMISSION_DENIED, CALL_INITIATION_NOT_SUPPORTED -> stringResource(id = R.string.webrtc_failed_no_call_permission)
                ICE_CONNECTION_ERROR -> stringResource(id = R.string.webrtc_failed_connection_to_contact_lost)
                KICKED -> stringResource(id = R.string.webrtc_failed_kicked)
            }
    }
}

@Composable
fun PeerState.humanReadable(): String {
    return when (this) {
        PeerState.INITIAL, START_CALL_MESSAGE_SENT -> stringResource(id = R.string.webrtc_status_initializing_call)
        CONNECTING_TO_PEER -> stringResource(id = R.string.webrtc_status_connecting_to_peer)
        PeerState.RINGING -> stringResource(id = R.string.webrtc_status_ringing)
        PeerState.BUSY -> stringResource(id = R.string.webrtc_status_contact_busy)
        CALL_REJECTED -> stringResource(id = R.string.webrtc_status_call_rejected)
        CONNECTED -> stringResource(id = R.string.webrtc_status_call_in_progress)
        RECONNECTING -> stringResource(id = R.string.webrtc_status_reconnecting)
        HANGED_UP -> stringResource(id = R.string.webrtc_status_contact_hanged_up)
        PeerState.KICKED -> stringResource(id = R.string.webrtc_status_contact_kicked)
        PeerState.FAILED -> stringResource(id = R.string.webrtc_status_contact_failed)
    }
}

@Composable
fun getPeerStateText(
    peerState: PeerState,
    singleContact: Boolean
): String? =
    when (peerState) {
        PeerState.BUSY -> if (singleContact) {
            null
        } else {
            stringResource(id = R.string.webrtc_status_contact_busy)
        }

        CALL_REJECTED -> stringResource(id = R.string.webrtc_status_call_rejected)
        CONNECTING_TO_PEER -> if (singleContact) {
            null
        } else {
            stringResource(id = R.string.webrtc_status_connecting_to_peer)
        }

        CONNECTED -> stringResource(id = R.string.webrtc_status_call_in_progress)
        RECONNECTING -> stringResource(id = R.string.webrtc_status_reconnecting)
        PeerState.RINGING -> if (singleContact) {
            null
        } else {
            stringResource(id = R.string.webrtc_status_ringing)
        }

        HANGED_UP -> stringResource(id = R.string.webrtc_status_contact_hanged_up)
        PeerState.KICKED -> stringResource(id = R.string.webrtc_status_contact_kicked)
        PeerState.FAILED -> stringResource(id = R.string.webrtc_status_contact_failed)
        PeerState.INITIAL, START_CALL_MESSAGE_SENT -> if (singleContact) {
            null
        } else {
            stringResource(id = R.string.webrtc_status_initializing_call)
        }
    }

fun AudioOutput.drawableResource() = when (this) {
    PHONE -> R.drawable.ic_phone_grey
    HEADSET -> R.drawable.ic_headset_grey
    LOUDSPEAKER -> R.drawable.ic_speaker_grey
    BLUETOOTH -> R.drawable.ic_speaker_bluetooth_grey
    MUTED -> R.drawable.ic_speaker_off
}

private fun AudioOutput.stringResource() = when (this) {
    PHONE -> R.string.text_audio_output_phone
    HEADSET -> R.string.text_audio_output_headset
    LOUDSPEAKER -> R.string.text_audio_output_loudspeaker
    BLUETOOTH -> R.string.text_audio_output_bluetooth
    MUTED-> R.string.text_audio_output_no_sound
}

@Composable
fun AudioOutput.Composable() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(
                id = this@Composable.drawableResource()
            ), contentDescription = name
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(
                id = this@Composable.stringResource()
            )
        )
    }
}

@Composable
private fun BoxScope.PreCall(
    name: String = "",
    status: String = "",
    cameraEnabled: Boolean = false,
    videoTrack: VideoTrack?,
    mirror: Boolean = true,
    initialViewSetup: (initialView: InitialView) -> Unit = {}
) {

    videoTrack?.let {
        if (cameraEnabled) {
            VideoRenderer(modifier = Modifier.fillMaxSize(), videoTrack = it, zoomable = true, mirror = mirror)
        }
    }

    if (cameraEnabled.not()) {
        EncryptedCallNotice(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            color = Color(0xFF8B8D97)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            InitialView(
                modifier = Modifier
                    .size(100.dp), initialViewSetup = initialViewSetup
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = name,
                textAlign = TextAlign.Center,
                style = OlvidTypography.h3,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                textAlign = TextAlign.Center,
                style = OlvidTypography.body2,
                color = Color(0xFF8B8D97),
            )
            Spacer(Modifier.height(100.dp))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EncryptedCallNotice(
                modifier = Modifier
                    .padding(top = 48.dp),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))
            InitialView(
                modifier = Modifier
                    .size(100.dp), initialViewSetup = initialViewSetup
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = name,
                style = OlvidTypography.h3,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                textAlign = TextAlign.Center,
                style = OlvidTypography.body2,
                color = Color.White
            )
        }
    }
}

@Composable
private fun OlvidLogo(
    large: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(if (large) 48.dp else 16.dp)
            .background(
                shape = RoundedCornerShape(if (large) 12.dp else 4.dp),
                brush = Brush.verticalGradient(
                    listOf(
                        colorResource(id = R.color.olvid_gradient_light),
                        colorResource(id = R.color.olvid_gradient_dark)
                    )
                )
            )
    ) {
        Image(
            modifier = Modifier
                .align(Alignment.Center)
                .size(if (large) 42.dp else 14.dp),
            painter = painterResource(id = R.drawable.icon_olvid_no_padding),
            contentDescription = "Olvid"
        )
    }
}

@Preview(device = "spec:width=411dp,height=891dp")
@Composable
fun PreCallScreenPreview() {
    AppCompatTheme {
        Box (modifier = Modifier
            .fillMaxHeight()
            .background(Color.Black)) {
            PreCall(
                "Alice Border",
                "Connexion...",
                false,
                null
            ) { it.setInitial(byteArrayOf(0, 1, 35), "A") }
        }
    }
}

@OptIn(
    ExperimentalMaterialApi::class
)
@Composable
fun CallScreen(
    webrtcCallService: WebrtcCallService?,
    contactListViewModel: ContactListViewModel,
    addingParticipant: Boolean,
    onCallAction: (CallAction) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val statusBarHeight = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBarHeight =
        with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val callButtonSize by remember(screenWidthDp) { mutableFloatStateOf((screenWidthDp/6f).coerceAtMost(56f)) }

    val callState = webrtcCallService?.getState()?.observeAsState()
    val callDuration = webrtcCallService?.getCallDuration()?.observeAsState()
    val callParticipants = webrtcCallService?.getCallParticipantsLiveData()?.observeAsState()
    val contact = callParticipants?.value?.takeIf { it.size == 1 }?.firstOrNull()?.contact
    val iAmTheCaller = webrtcCallService?.isCaller ?: false

    val microphoneMuted = webrtcCallService?.getMicrophoneMuted()?.observeAsState()
    val pipAspectCallback: (Context, Int, Int) -> Unit = { contextArg, width, height ->
        setPictureInPictureAspectRatio(context = contextArg, width = width, height = height)
    }

    val unfilteredContacts =
        AppSingleton.getCurrentIdentityLiveData().switchMap { ownedIdentity: OwnedIdentity? ->
            if (ownedIdentity == null) {
                return@switchMap null
            }
            AppDatabase.getInstance().contactDao()
                .getAllForOwnedIdentityWithChannelExcludingSome(
                    ownedIdentity.bytesOwnedIdentity,
                    callParticipants?.value?.map { it.bytesContactIdentity } ?: emptyList())
        }.observeAsState()
    contactListViewModel.setUnfilteredContacts(unfilteredContacts.value?.filter { it.oneToOne })
    contactListViewModel.setUnfilteredNotOneToOneContacts(unfilteredContacts.value?.filter { it.oneToOne.not() })

    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberBottomSheetState(
            initialValue = Collapsed
        )
    )

    LaunchedEffect(bottomSheetScaffoldState.bottomSheetState.currentValue) {
        if (addingParticipant && bottomSheetScaffoldState.bottomSheetState.isCollapsed) {
            onCallAction(AddParticipant(false))
        }
    }

    var fullScreenMode by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(fullScreenMode) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        insetsController.apply {
            systemBarsBehavior = if (fullScreenMode) {
                hide(WindowInsetsCompat.Type.navigationBars())
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.navigationBars())
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        onDispose {
            insetsController.apply {
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }

    val peekHeight by animateDpAsState(
        targetValue = if (fullScreenMode) 0.dp else (navigationBarHeight + (32 + callButtonSize).dp),
        label = "peekHeightAnimated"
    )
    val snackbarHostState = remember {
        SnackbarHostState()
    }

    LaunchedEffect(webrtcCallService?.speakingWhileMuted) {
        if (webrtcCallService?.speakingWhileMuted == true) {
            val result = snackbarHostState
                .showSnackbar(
                    message = context.getString(R.string.webrtc_speaking_while_muted),
                    actionLabel = context.getString(R.string.webrtc_speaking_while_muted_action),
                )
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    if (webrtcCallService.microphoneMuted) {
                        webrtcCallService.toggleMuteMicrophone()
                    }
                } else -> {}
            }
        }
    }

    webrtcCallService?.let {
        val participants = callParticipants?.value.orEmpty()
        if (context.isInPictureInPictureMode) {
            Box {
                VideoCallContent(
                    participants,
                    webrtcCallService,
                    peekHeight,
                    onCallAction,
                    isPip = true,
                    pipAspectCallback = pipAspectCallback
                )
            }
        } else {
            BottomSheetScaffold(
                backgroundColor = Color(0xFF222222),
                snackbarHost = {
                    SnackbarHost(
                        modifier = Modifier.widthIn(max = 400.dp),
                        hostState = snackbarHostState
                    )
                },
                sheetBackgroundColor = Color.Black,
                sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                scaffoldState = bottomSheetScaffoldState,
                sheetContent = {
                    CallBottomSheetContent(
                        addingParticipant,
                        statusBarHeight,
                        bottomSheetScaffoldState,
                        contactListViewModel,
                        webrtcCallService,
                        onCallAction,
                        microphoneMuted,
                        callState,
                        contact,
                        callDuration,
                        callParticipants,
                        navigationBarHeight,
                        iAmTheCaller,
                        callButtonSize
                    )
                },
                sheetPeekHeight = peekHeight
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (bottomSheetScaffoldState.bottomSheetState.isCollapsed)
                                fullScreenMode = !fullScreenMode
                            else
                                coroutineScope.launch {
                                    if (bottomSheetScaffoldState.bottomSheetState.isExpanded) {
                                        bottomSheetScaffoldState.bottomSheetState.collapse()
                                    }
                                }
                        }
                        .statusBarsPadding()
                ) {
                    if (callState?.value == CALL_IN_PROGRESS || callState?.value == CALL_ENDED) {
                        VideoCallContent(
                            participants = participants,
                            webrtcCallService = webrtcCallService,
                            peekHeight = peekHeight,
                            onCallAction = onCallAction,
                            pipAspectCallback = pipAspectCallback
                        )
                    } else {
                        var initialViewSetup: (InitialView) -> Unit by remember {
                            mutableStateOf({})
                        }
                        var name by remember {
                            mutableStateOf("")
                        }
                        val selectedCamera by webrtcCallService.selectedCameraLiveData.observeAsState()

                        LaunchedEffect(callParticipants) {
                            App.runThread {
                                if (callParticipants?.value.orEmpty().size > 1 && webrtcCallService.bytesGroupOwnerAndUidOrIdentifier != null) {
                                    with(webrtcCallService) {
                                        when (discussionType) {
                                            Discussion.TYPE_GROUP -> {
                                                val group = bytesOwnedIdentity?.let { ownId ->
                                                    bytesGroupOwnerAndUidOrIdentifier?.let { groupId ->
                                                        AppDatabase.getInstance()
                                                            .groupDao()[ownId, groupId]
                                                    }
                                                }
                                                group?.getCustomPhotoUrl()?.let {
                                                    initialViewSetup = { initialView: InitialView ->
                                                        initialView.setPhotoUrl(
                                                            bytesGroupOwnerAndUidOrIdentifier,
                                                            it
                                                        )
                                                    }
                                                } ifNull {
                                                    initialViewSetup = { initialView ->
                                                        initialView.setGroup(
                                                            bytesGroupOwnerAndUidOrIdentifier
                                                        )
                                                    }
                                                }
                                                name = getString(
                                                    R.string.text_count_contacts_from_group,
                                                    callParticipants?.value.orEmpty().size,
                                                    group?.getCustomName() ?: ""
                                                )
                                            }

                                            Discussion.TYPE_GROUP_V2 -> {
                                                val group =  bytesOwnedIdentity?.let { ownId ->
                                                    bytesGroupOwnerAndUidOrIdentifier?.let { groupId ->
                                                        AppDatabase.getInstance()
                                                            .group2Dao()[ownId, groupId]
                                                    }
                                                }
                                                group?.getCustomPhotoUrl()?.let {
                                                    initialViewSetup = { initialView ->
                                                        initialView.setPhotoUrl(
                                                            bytesGroupOwnerAndUidOrIdentifier,
                                                            it
                                                        )
                                                    }
                                                } ifNull {
                                                    initialViewSetup = { initialView ->
                                                        initialView.setGroup(
                                                            bytesGroupOwnerAndUidOrIdentifier
                                                        )
                                                    }
                                                }
                                                name = getString(
                                                    R.string.text_count_contacts_from_group,
                                                    callParticipants?.value.orEmpty().size,
                                                    group?.getCustomName() ?: ""
                                                ) // this group has members, so no need to check if getCustomName() returns ""
                                            }

                                            else -> {}
                                        }
                                    }
                                } else {
                                    initialViewSetup = { initialView: InitialView ->
                                        contact?.let {
                                            initialView.setContact(
                                                contact
                                            )
                                        }
                                    }
                                    name = contact?.getCustomDisplayName().orEmpty()
                                }
                            }
                        }
                        PreCall(
                            name = name,
                            status = if (callState?.value != CALL_IN_PROGRESS) {
                                callState?.value?.humanReadable(webrtcCallService.failReason) ?: ""
                            } else {
                                formatDuration(callDuration?.value ?: 0)
                            },
                            initialViewSetup = initialViewSetup,
                            cameraEnabled = webrtcCallService.cameraEnabled,
                            videoTrack = localVideoTrack?.takeIf { callState?.value != CALL_ENDED },
                            mirror = selectedCamera?.mirror == true
                        )
                    }

                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.TopEnd),
                        visible = !fullScreenMode,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        SpeakerToggle(
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.End))
                                .padding(top = 10.dp, end = 10.dp),
                            audioOutputs = webrtcCallService.availableAudioOutputs, { audioOutput ->
                                webrtcCallService.selectAudioOutput(audioOutput)
                            }) { onToggleSpeaker ->
                            SpeakerToggleButton(webrtcCallService.selectedAudioOutput.drawableResource(), onToggleSpeaker)
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
private fun ColumnScope.CallBottomSheetContent(
    addingParticipant: Boolean,
    statusBarHeight: Dp,
    bottomSheetScaffoldState: BottomSheetScaffoldState,
    contactListViewModel: ContactListViewModel,
    webrtcCallService: WebrtcCallService?,
    onCallAction: (CallAction) -> Unit,
    microphoneMuted: State<Boolean?>?,
    @Suppress("unused") callState: State<WebrtcCallService.State?>?,
    contact: Contact?,
    callDuration: State<Int?>?,
    callParticipants: State<List<CallParticipantPojo>?>?,
    navigationBarHeight: Dp,
    iAmTheCaller: Boolean,
    callButtonSize: Float
) {
    val haptics = LocalHapticFeedback.current

    if (addingParticipant) {
        Spacer(modifier = Modifier.height(statusBarHeight))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Spacer(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(40.dp)
            .height(4.dp)
            .background(color = Color(0x80D9D9D9), shape = RoundedCornerShape(50))
    )
    LaunchedEffect(addingParticipant) {
        if (addingParticipant) {
            bottomSheetScaffoldState.bottomSheetState.expand()
        } else {
            bottomSheetScaffoldState.bottomSheetState.collapse()
            contactListViewModel.selectedContacts.clear()
        }
    }
    if (addingParticipant) {
        AddParticipantScreen(contactListViewModel) {
            webrtcCallService?.callerAddCallParticipants(contactListViewModel.selectedContacts)
            onCallAction(AddParticipant(false))
        }
    } else {
        val callMediaState =
            CallMediaState(
                isMicrophoneEnabled = microphoneMuted?.value?.not() ?: true,
                isCameraEnabled = webrtcCallService?.cameraEnabled ?: false,
                isScreenShareEnabled = webrtcCallService?.screenShareActive ?: false,
                selectedAudioOutput = webrtcCallService?.selectedAudioOutput ?: PHONE,
                audioOutputs = webrtcCallService?.availableAudioOutputs.orEmpty()
            )
        CallControls(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            actions = buildOngoingCallControlActions(callMediaState = callMediaState),
            onToggleSpeaker = { webrtcCallService?.selectAudioOutput(it) },
            callMediaState = callMediaState,
            onCallAction = onCallAction,
            callButtonSize = callButtonSize
        )

        Spacer(modifier = Modifier.height(navigationBarHeight))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = contact?.getCustomDisplayName().orEmpty(),
                style = OlvidTypography.body1,
                color = Color.White
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_phone_outgoing),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.text_ongoing_call, formatDuration(callDuration?.value ?: 0)) ,
                    // Body2
                    style = OlvidTypography.body2,
                    color = Color(0xFF8B8D97),
                )
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .height(1.dp)
                .background(
                    Color(0xFF29282D)
                )
        )
        LazyColumn {
            if (iAmTheCaller) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCallAction(AddParticipant(true)) }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF29282D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                modifier = Modifier
                                    .size(24.dp),
                                painter = painterResource(id = R.drawable.ic_add_user),
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(id = R.string.webrtc_add_participants),
                            style = OlvidTypography.h3,
                            color = Color.White
                        )
                    }
                }
            }
            callParticipants?.value?.let { callParticipants ->
                items(callParticipants) { callParticipant ->
                    var kickParticipant by remember {
                        mutableStateOf(false)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (callParticipant.contact?.oneToOne == true) {
                                        onCallAction(GoToDiscussion(callParticipant.contact))
                                    }
                                },
                                onLongClick = {
                                    if (webrtcCallService?.isCaller == true && callParticipants.size > 1) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        kickParticipant = true
                                    }
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (webrtcCallService?.isCaller == true) {
                            DropdownMenu(
                                expanded = kickParticipant,
                                onDismissRequest = { kickParticipant = false }) {
                                DropdownMenuItem(onClick = {
                                    kickParticipant = false
                                    webrtcCallService.callerKickParticipant(
                                        callParticipant.bytesContactIdentity
                                    )
                                }) {
                                    Text(text = stringResource(id = R.string.dialog_title_webrtc_kick_participant))
                                }
                            }
                        }
                        Box {
                            InitialView(
                                modifier = Modifier.requiredSize(56.dp),
                                initialViewSetup = { initialView ->
                                callParticipant.contact?.let {
                                    initialView.setContact(it)
                                }
                            })
                            if (callParticipant.peerIsMuted) {
                                Icon(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(4.dp)
                                        .background(Color.Black, shape = CircleShape)
                                        .align(Alignment.BottomEnd),
                                    painter = painterResource(id = R.drawable.ic_microphone_off),
                                    tint = Color.White,
                                    contentDescription = "muted"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = callParticipant.displayName ?: "",
                                style = OlvidTypography.h3,
                                color = Color.White
                            )
                            val peerStatus = getPeerStateText(
                                peerState = callParticipant.peerState,
                                singleContact = callParticipants.size == 1
                            )
                            AnimatedVisibility(visible = peerStatus != null) {
                                Text(
                                    text = peerStatus ?: "",
                                    style = OlvidTypography.h3,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (callParticipant.contact?.oneToOne == true) {
                            IconButton(onClick = {
                                onCallAction(
                                    GoToDiscussion(
                                        callParticipant.contact
                                    )
                                )
                            }) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_chat_circle),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(navigationBarHeight))
}

//@Composable
//fun BoxScope.MultiVideoCallContent(
//    participants: List<CallParticipantPojo>,
//    webrtcCallService: WebrtcCallService,
//    peekHeight: Dp
//) {
//    val selectedCamera by webrtcCallService.selectedCameraLiveData.observeAsState()
//    // multi
//    Column {
//        LazyRow(
//            modifier = Modifier.padding(
//                start = 16.dp,
//                top = 8.dp,
//                bottom = 10.dp
//            ),
//            horizontalArrangement = Arrangement.spacedBy(10.dp)
//        ) {
//            items(items = participants.filterNot {
//                it.bytesContactIdentity.contentEquals(
//                    webrtcCallService.selectedParticipant
//                )
//            }) { callParticipant ->
//                val remoteVideoTrack =
//                    webrtcCallService.getCallParticipant(callParticipant.bytesContactIdentity)?.peerConnectionHolder?.remoteVideoTrack
//                Card(
//                    modifier = Modifier
//                        .size(72.dp)
//                        .clickable {
//                            webrtcCallService.selectedParticipant =
//                                callParticipant.bytesContactIdentity
//                        },
//                    shape = RoundedCornerShape(12.dp)
//                ) {
//                    CallParticipant(
//                        callParticipant = callParticipant,
//                        videoTrack = remoteVideoTrack,
//                        audioLevel = webrtcCallService.getCallParticipant(callParticipant.bytesContactIdentity)?.peerConnectionHolder?.peerAudioLevel
//                    )
//
//                }
//
//            }
//        }
//        if (webrtcCallService.selectedParticipant.contentEquals(webrtcCallService.bytesOwnedIdentity!!)) {
//            if (webrtcCallService.screenShareActive) {
//                ScreenShareOngoing { webrtcCallService.toggleScreenShare() }
//            } else {
//                CallParticipant(
//                    modifier = Modifier
//                        .clip(
//                            RoundedCornerShape(
//                                topStart = 20.dp,
//                                topEnd = 20.dp
//                            )
//                        )
//                        .fillMaxSize(),
//                    bytesOwnedIdentity = webrtcCallService.bytesOwnedIdentity,
//                    mirror = selectedCamera?.mirror == true,
//                    videoTrack = localVideoTrack.takeIf { webrtcCallService.cameraEnabled },
//                    screenTrack = localScreenTrack.takeIf { webrtcCallService.screenShareActive },
//                    zoomable = true,
//                    audioLevel = webrtcCallService.getAudioLevel(webrtcCallService.bytesOwnedIdentity)
//                )
//            }
//        } else {
//            val remoteVideoTrack =
//                webrtcCallService.getCallParticipant(webrtcCallService.selectedParticipant)?.peerConnectionHolder?.remoteVideoTrack
//            CallParticipant(
//                callParticipant = CallParticipantPojo(
//                    webrtcCallService.getCallParticipant(
//                        webrtcCallService.selectedParticipant
//                    )!!
//                ), videoTrack = remoteVideoTrack,
//                zoomable = true,
//                audioLevel = webrtcCallService.getAudioLevel(webrtcCallService.selectedParticipant)
//            )
//        }
//    }
//    if (webrtcCallService.selectedParticipant.contentEquals(webrtcCallService.bytesOwnedIdentity!!)
//            .not()
//    ) {
//        Card(
//            modifier = Modifier
//                .size(120.dp)
//                .align(Alignment.BottomEnd)
//                .offset(y = -peekHeight)
//                .clickable {
//                    webrtcCallService.selectedParticipant =
//                        webrtcCallService.bytesOwnedIdentity!!
//                }
//                .padding(end = 16.dp, bottom = 8.dp),
//            shape = RoundedCornerShape(20.dp)
//        ) {
//            CallParticipant(
//                bytesOwnedIdentity = webrtcCallService.bytesOwnedIdentity,
//                mirror = selectedCamera?.mirror == true,
//                videoTrack = localVideoTrack.takeIf { webrtcCallService.cameraEnabled },
//                screenTrack = localScreenTrack.takeIf { webrtcCallService.screenShareActive },
//                audioLevel = webrtcCallService.getAudioLevel(webrtcCallService.bytesOwnedIdentity)
//            )
//        }
//    }
//}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AudioCallContent(
    participants: List<CallParticipantPojo>,
    webrtcCallService: WebrtcCallService,
    onCallAction: (CallAction) -> Unit,
    isPip: Boolean = false
) {
    if (isPip) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OlvidLogo(large = true)
            Spacer(modifier = Modifier.height(32.dp))
            Row (verticalAlignment = Alignment.CenterVertically)
            {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(id = R.drawable.ic_phone_outgoing),
                    contentDescription = stringResource(id = R.string.text_ongoing_call)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = participants.size.toString(),
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    } else {
        val haptics = LocalHapticFeedback.current
        LazyColumn(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(participants) { audioParticipant ->
                var menu by remember {
                    mutableStateOf(false)
                }
                Box {
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false }) {
                        DropdownMenuItem(onClick = {
                            menu = false
                            webrtcCallService.callerKickParticipant(
                                audioParticipant.bytesContactIdentity
                            )
                        }) {
                            Text(text = stringResource(id = R.string.dialog_title_webrtc_kick_participant))
                        }
                    }
                    AudioParticipant(modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .combinedClickable(
                            onClick = {
                                if (audioParticipant.contact?.oneToOne == true) {
                                    onCallAction(GoToDiscussion(audioParticipant.contact))
                                }
                            },
                            onLongClick = {
                                if (webrtcCallService.isCaller && participants.size > 1) {
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.LongPress
                                    )
                                    menu = true
                                }
                            }
                        ),
                        initialViewSetup = audioParticipant.initialViewSetup(),
                        name = audioParticipant.displayName ?: "",
                        isMute = audioParticipant.peerIsMuted,
                        state = audioParticipant.peerState,
                        audioLevel = webrtcCallService.getAudioLevel(audioParticipant.bytesContactIdentity))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun VideoCallContent(
    participants: List<CallParticipantPojo>,
    webrtcCallService: WebrtcCallService,
    peekHeight: Dp,
    onCallAction: (CallAction) -> Unit,
    isPip: Boolean = false,
    pipAspectCallback: ((Context, Int, Int) -> Unit)? = null
) {
    val selectedCamera by webrtcCallService.selectedCameraLiveData.observeAsState()
    val speakingColor = colorResource(id = R.color.olvid_gradient_light)
    val notSpeakingColor = Color(0xFF29282D)
    val borderColorOwned by animateColorAsState(
        if ((webrtcCallService.getAudioLevel(webrtcCallService.bytesOwnedIdentity)
                ?: 0.0) > 0.1
        ) speakingColor else notSpeakingColor,
        label = "borderColorOwned",
        animationSpec = tween(durationMillis = 1000, easing = EaseOutExpo)
    )

    if (participants.size == 1) {
        // 1to1
        val remoteVideoTrack =
            webrtcCallService.getCallParticipant(participants.firstOrNull()?.bytesContactIdentity)?.peerConnectionHolder?.remoteVideoTrack
        val remoteScreenTrack =
            webrtcCallService.getCallParticipant(participants.firstOrNull()?.bytesContactIdentity)?.peerConnectionHolder?.remoteScreenTrack
        if (webrtcCallService.selectedParticipant.contentEquals(webrtcCallService.bytesOwnedIdentity)
                .not()
        ) {
            CallParticipant(
                callParticipant = participants.firstOrNull(),
                videoTrack = remoteVideoTrack,
                screenTrack = remoteScreenTrack,
                peekHeight = peekHeight,
                modifier = Modifier.fillMaxSize(),
                zoomable = true,
                audioLevel = webrtcCallService.getAudioLevel(participants.firstOrNull()?.bytesContactIdentity),
                isPip = isPip,
                pipAspectCallback = pipAspectCallback
            )
            AnimatedVisibility(!isPip) {
                Card(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Start))
                        .padding(start = 10.dp, top = 10.dp)
                        .clickable {
                            webrtcCallService.selectedParticipant =
                                webrtcCallService.bytesOwnedIdentity
                        }
                        .border(
                            width = 2.dp,
                            color = borderColorOwned,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    CallParticipant(
                        modifier = Modifier.sizeIn(maxWidth = 120.dp, maxHeight = 120.dp),
                        bytesOwnedIdentity = webrtcCallService.bytesOwnedIdentity,
                        mirror = selectedCamera?.mirror == true,
                        videoTrack = localVideoTrack.takeIf { webrtcCallService.cameraEnabled },
                        audioLevel = webrtcCallService.getAudioLevel(webrtcCallService.bytesOwnedIdentity)
                    )
                }
            }
        } else {
            if (webrtcCallService.screenShareActive) {
                ScreenShareOngoing { webrtcCallService.toggleScreenShare() }
            } else {
                CallParticipant(
                    bytesOwnedIdentity = webrtcCallService.bytesOwnedIdentity,
                    mirror = selectedCamera?.mirror == true,
                    videoTrack = localVideoTrack.takeIf { webrtcCallService.cameraEnabled },
                    screenTrack = localScreenTrack.takeIf { webrtcCallService.screenShareActive },
                    peekHeight = peekHeight,
                    zoomable = true,
                    modifier = Modifier.fillMaxSize(),
                    audioLevel = webrtcCallService.getAudioLevel(webrtcCallService.bytesOwnedIdentity),
                    isPip = isPip,
                    fitVideo = true
                )
            }
            AnimatedVisibility(!isPip) {
                Card(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Start))
                        .padding(start = 10.dp, top = 10.dp)
                        .clickable {
                            webrtcCallService.selectedParticipant =
                                participants.firstOrNull()?.bytesContactIdentity!!
                        },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    CallParticipant(
                        modifier = Modifier.sizeIn(maxWidth = 120.dp, maxHeight = 120.dp),
                        callParticipant = participants.firstOrNull(),
                        videoTrack = remoteVideoTrack,
                        screenTrack = remoteScreenTrack,
                        audioLevel = webrtcCallService.getAudioLevel(participants.firstOrNull()?.bytesContactIdentity),
                        pipAspectCallback = pipAspectCallback
                    )
                }
            }
        }
    } else if (participants.size == 2) {
        val borderColorFirst by animateColorAsState(
            if ((webrtcCallService.getAudioLevel(participants.first().bytesContactIdentity)
                    ?: 0.0) > 0.1
            ) speakingColor else notSpeakingColor,
            label = "borderColor",
            animationSpec = tween(durationMillis = 1000, easing = EaseOutExpo)
        )
        val borderColorSecond by animateColorAsState(
            if ((webrtcCallService.getAudioLevel(participants[1].bytesContactIdentity)
                    ?: 0.0) > 0.1
            ) speakingColor else notSpeakingColor,
            label = "borderColor",
            animationSpec = tween(durationMillis = 1000, easing = EaseOutExpo)
        )
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            pipAspectCallback?.invoke(context, 9, 16)
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isPip) 0.dp else peekHeight)
        ) {
            val boxWithConstraintsScope = this
            FlowRow(
                modifier = Modifier
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.SpaceBetween,
                maxItemsInEachRow = if (maxWidth > maxHeight) 2 else 1
            ) {
                Card(
                    modifier = Modifier
                        .then(
                            if (boxWithConstraintsScope.maxWidth > boxWithConstraintsScope.maxHeight)
                                Modifier
                                    .fillMaxHeight()
                                    .width(boxWithConstraintsScope.maxWidth / 2 - 6.dp)
                            else
                                Modifier
                                    .fillMaxWidth()
                                    .height(boxWithConstraintsScope.maxHeight / 2 - 6.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = borderColorFirst,
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    val remoteVideoTrack =
                        webrtcCallService.getCallParticipant(participants.first().bytesContactIdentity)?.peerConnectionHolder?.remoteVideoTrack
                    val remoteScreenTrack =
                        webrtcCallService.getCallParticipant(participants.first().bytesContactIdentity)?.peerConnectionHolder?.remoteScreenTrack
                    CallParticipant(
                        videoTrack = remoteVideoTrack,
                        screenTrack = remoteScreenTrack,
                        callParticipant = participants.first(),
                        zoomable = true,
                        audioLevel = webrtcCallService.getAudioLevel(participants.first().bytesContactIdentity),
                        isPip = isPip
                    )
                }
                Card(
                    modifier = Modifier
                        .then(
                            if (boxWithConstraintsScope.maxWidth > boxWithConstraintsScope.maxHeight)
                                Modifier
                                    .fillMaxHeight()
                                    .width(boxWithConstraintsScope.maxWidth / 2 - 6.dp)
                            else
                                Modifier
                                    .fillMaxWidth()
                                    .height(boxWithConstraintsScope.maxHeight / 2 - 6.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = borderColorSecond,
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    val remoteVideoTrack =
                        webrtcCallService.getCallParticipant(participants[1].bytesContactIdentity)?.peerConnectionHolder?.remoteVideoTrack
                    val remoteScreenTrack =
                        webrtcCallService.getCallParticipant(participants[1].bytesContactIdentity)?.peerConnectionHolder?.remoteScreenTrack
                    CallParticipant(
                        videoTrack = remoteVideoTrack,
                        screenTrack = remoteScreenTrack,
                        callParticipant = participants[1],
                        zoomable = true,
                        audioLevel = webrtcCallService.getAudioLevel(participants[1].bytesContactIdentity),
                        isPip = isPip
                    )
                }
            }
        }
        AnimatedVisibility(!isPip) {
            Card(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Start))
                    .padding(start = 10.dp, top = 10.dp)
                    .border(
                        width = 2.dp,
                        color = borderColorOwned,
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp)
            ) {
                CallParticipant(
                    modifier = Modifier.sizeIn(maxWidth = 120.dp, maxHeight = 120.dp),
                    bytesOwnedIdentity = webrtcCallService.bytesOwnedIdentity,
                    mirror = selectedCamera?.mirror == true,
                    videoTrack = localVideoTrack.takeIf { webrtcCallService.cameraEnabled },
                    audioLevel = webrtcCallService.getAudioLevel(webrtcCallService.bytesOwnedIdentity)
                )
            }
        }
    } else if (participants.size > 2) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            pipAspectCallback?.invoke(context, 9, 16)
        }
        AudioCallContent(
            participants = participants,
            webrtcCallService = webrtcCallService,
            onCallAction = onCallAction,
            isPip = isPip
        )
        //MultiVideoCallContent(participants = participants, webrtcCallService = webrtcCallService, peekHeight = peekHeight)
    }
}

fun VideoTrack?.isEnabledSafe() = try {
    this?.enabled() == true
} catch (_: Exception) {
    false
}

@Composable
fun CallParticipant(
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier.fillMaxSize(),
    callParticipant: CallParticipantPojo? = null,
    bytesOwnedIdentity: ByteArray? = null,
    mirror: Boolean = false,
    videoTrack: VideoTrack?,
    screenTrack: VideoTrack? = null,
    zoomable: Boolean = false,
    peekHeight: Dp = 0.dp,
    audioLevel: Double?,
    isPip: Boolean = false,
    pipAspectCallback: ((Context, Int, Int) -> Unit)? = null,
    fitVideo: Boolean = false
) {

    BoxWithConstraints(modifier = modifier) {
        val largeLayout = maxWidth > 200.dp
        val hasVideo = callParticipant?.peerVideoSharing != false && videoTrack.isEnabledSafe()
        val hasScreenShare = callParticipant?.peerScreenSharing != false && screenTrack.isEnabledSafe()

        if (hasVideo || hasScreenShare) {
            if (hasScreenShare) {
                VideoRenderer(
                    modifier = Modifier.fillMaxSize(),
                    videoTrack = screenTrack!!,
                    zoomable = zoomable,
                    mirror = false,
                    pipAspectCallback = pipAspectCallback,
                    fitVideo = true
                )
                if (hasVideo) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Start))
                            .then(
                                if (isPip)
                                    Modifier
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .sizeIn(maxWidth = 60.dp, maxHeight = 60.dp)
                                else
                                    Modifier
                                        .offset(x = 10.dp, y = -(peekHeight + 10.dp))
                                        .sizeIn(maxWidth = 120.dp, maxHeight = 120.dp)
                            ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        VideoRenderer(
                            videoTrack = videoTrack!!,
                            mirror = mirror,
                            matchVideoAspectRatio = true
                        )
                    }
                }
            } else {
                VideoRenderer(
                    videoTrack = videoTrack!!,
                    zoomable = zoomable,
                    mirror = mirror,
                    matchVideoAspectRatio = !largeLayout,
                    pipAspectCallback = pipAspectCallback,
                    fitVideo = fitVideo
                )
            }
            callParticipant?.peerState.takeUnless { it == CONNECTED }?.let {
                Text(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = if (isPip) 0.dp else peekHeight)
                        .background(
                            colorResource(id = R.color.blackOverlay),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    text = getPeerStateText(peerState = it, singleContact = false) ?: "",
                    textAlign = TextAlign.Center,
                    style = OlvidTypography.body2.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
            }
        } else {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                pipAspectCallback?.invoke(context, 9, 16)
            }
            val radius by animateFloatAsState(
                targetValue = audioLevel?.toFloat() ?: 0f,
                animationSpec = tween(durationMillis = 600),
                label = "waveRadius"
            )
            val alpha by animateFloatAsState(
                targetValue = audioLevel?.toFloat() ?: 0f,
                animationSpec = tween(durationMillis = 600),
                label = "waveAlpha"
            )
            Box (
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isPip) 0.dp else peekHeight)
            ) {
                InitialView(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .sizeIn(maxWidth = 200.dp)
                        .fillMaxSize(.5f)
                        .drawBehind {
                            if (radius > .1f) {
                                drawCircle(
                                    color = Color.White,
                                    radius = size.minDimension / 2 * (1 + radius),
                                    alpha = alpha,
                                    style = Fill
                                )
                            }
                        },
                    initialViewSetup = { view ->
                        if (bytesOwnedIdentity != null) {
                            App.runThread {
                                AppDatabase.getInstance().ownedIdentityDao().get(bytesOwnedIdentity)?.let {
                                    view.setOwnedIdentity(it)
                                }
                            }
                        } else {
                            callParticipant?.initialViewSetup()?.invoke(view)
                        }
                    }
                )
                callParticipant?.peerState.takeUnless { it == CONNECTED }?.let {
                    Text(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 130.dp),
                        text = getPeerStateText(peerState = it, singleContact = false) ?: "",
                        textAlign = TextAlign.Center,
                        style = OlvidTypography.body2.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )
                }
            }
        }
        if (callParticipant?.peerIsMuted == true) {
            Icon(
                modifier = Modifier
                    .padding(end = 6.dp, bottom = if (!isPip) (peekHeight + 6.dp) else 6.dp)
                    .size(if (largeLayout) 32.dp else 16.dp)
                    .align(Alignment.BottomEnd)
                    .background(colorResource(id = R.color.red), CircleShape)
                    .padding(if (largeLayout) 4.dp else 2.dp),
                painter = painterResource(id = R.drawable.ic_microphone_off),
                tint = Color.White,
                contentDescription = "muted"
            )
        } else if (largeLayout) {
            AudioLevel(
                modifier = Modifier
                    .padding(end = 6.dp, bottom = peekHeight)
                    .padding(bottom = 6.dp)
                    .size(32.dp)
                    .align(Alignment.BottomEnd),
                audioLevel
            )
        }
        if (largeLayout) {
            (callParticipant?.displayName
                ?: AppSingleton.getContactCustomDisplayName(
                    bytesOwnedIdentity ?: byteArrayOf()
                ))?.let { name ->
                Text(
                    modifier = Modifier
                        .then(
                            if (hasVideo && hasScreenShare)
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.End
                                        )
                                    )
                            else
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.Start
                                        )
                                    )
                        )
                        .padding(
                            start = 6.dp,
                            bottom = peekHeight + 6.dp,
                            end = 6.dp
                        )
                        .background(
                            colorResource(id = R.color.blackOverlay),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    style = OlvidTypography.body2.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    text = name
                )
            }
        }
    }
}

@Composable
fun AudioLevel(modifier: Modifier = Modifier, audioLevel: Double?) {
    BoxWithConstraints(
        modifier = modifier
            .background(color = Color.White.copy(alpha = .05f), shape = CircleShape)
            .clip(
                CircleShape
            )
    ) {
        val padding = maxWidth / 12
        val width = maxWidth / 3 - padding * 2
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(3) {
                Spacer(
                    modifier = Modifier
                        .background(color = Color.Blue, shape = RoundedCornerShape(12.dp))
                        .width(width)
                        .heightIn(min = width)
                        .fillMaxHeight(
                            (audioLevel?.toFloat() ?: 0f)
                                .times(if (it == 1) 1f else 0.66f)
                                .coerceAtLeast(.1f)
                        )
                )
            }
        }
    }
}

@Composable
@Preview
fun AudioLevelPreview() {
    AppCompatTheme {
        Row {
            AudioLevel(modifier = Modifier.size(64.dp), audioLevel = 0.0)
            Spacer(modifier = Modifier.width(4.dp))
            AudioLevel(modifier = Modifier.size(64.dp), audioLevel = 1.0)
        }
    }
}

@Composable
private fun ScreenShareOngoing(stopScreenShare: () -> Unit) {
    Column(
        Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier.widthIn(max = 200.dp),
            text = stringResource(id = R.string.webrtc_screen_sharing_ongoing),
            style = OlvidTypography.h1,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        IconButton(onClick = stopScreenShare) {
            Image(
                painter = painterResource(id = R.drawable.ic_stop_screen_share),
                contentDescription = stringResource(id = R.string.webrtc_stop_screen_sharing)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.webrtc_stop_screen_sharing),
            style = OlvidTypography.body2,
            color = Color(0xFF8B8D97),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AddParticipantScreen(
    contactListViewModel: ContactListViewModel,
    onSelectionDone: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Text(
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp),
        text = stringResource(id = R.string.webrtc_add_participants),
        style = OlvidTypography.h2.copy(
            fontWeight = FontWeight.Medium
        ),
        color = Color.White,
    )
    var textFieldValue: TextFieldValue by remember { mutableStateOf(TextFieldValue(contactListViewModel.getFilter().orEmpty())) }
    Box(modifier = Modifier.padding(start = 20.dp, end = 24.dp)) {
        BasicTextField(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .border(
                    width = 1.dp,
                    color = Color(0xFF39393D),
                    shape = RoundedCornerShape(size = 16.dp)
                )
                .padding(12.dp),
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                contactListViewModel.setFilter(it.text)
            },
            singleLine = true,
            textStyle = OlvidTypography.body1.copy(
                color = Color.White,
            ),
            cursorBrush = SolidColor(Color.White),
        )
        if (contactListViewModel.getFilter().isNullOrEmpty()) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp),
                text = stringResource(id = R.string.hint_search_contact_name),
                style = OlvidTypography.body1,
                color = Color(0xFF8B8D97),
            )
        }
        Icon(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            painter = painterResource(id = R.drawable.ic_search),
            tint = Color(0xFF39393D),
            contentDescription = "search"
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    ContactListScreen(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp)
            .imePadding()
            .navigationBarsPadding(),
        contactListViewModel = contactListViewModel,
        refreshing = false,
        onRefresh = null,
        selectable = true,
        onClick = {
            textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
        },
        onSelectionDone = {
            keyboard?.hide()
            onSelectionDone()
        },
        onInvite = { },
        onScrollStart = { keyboard?.hide() }
    )
}

fun CallParticipantPojo.initialViewSetup(): (InitialView) -> Unit = { view ->
    contact?.let {
        view.setContact(it)
    } ifNull {
        with(view) {
            reset()
            setInitial(
                bytesContactIdentity,
                StringUtils.getInitial(displayName)
            )
        }
    }
}


@Composable
fun AudioParticipant(
    modifier: Modifier = Modifier,
    initialViewSetup: (initialView: InitialView) -> Unit,
    name: String,
    isMute: Boolean,
    state: PeerState,
    audioLevel: Double?
) {
    val speakingColor = colorResource(id = R.color.olvid_gradient_light)
    val notSpeakingColor = Color(0xFF29282D)
    val borderColor by animateColorAsState(
        if ((audioLevel ?: 0.0) > 0.1) speakingColor else notSpeakingColor,
        label = "borderColor",
        animationSpec = tween(durationMillis = 1000, easing = EaseOutExpo)
    )
    Row(
        modifier = modifier
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(size = 20.dp)
            )
            .background(color = Color(0xCC000000), shape = RoundedCornerShape(size = 20.dp))
            .clip(RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialView(
            modifier = Modifier.requiredSize(56.dp),
            initialViewSetup = initialViewSetup
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column (
            modifier = Modifier.weight(1f, true)
        ){
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = OlvidTypography.body1,
                color = Color.White
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = state.humanReadable(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = OlvidTypography.body2,
                color = Color.LightGray
            )
        }
        if(isMute) {
            Icon(
                modifier = Modifier
                    .size(32.dp)
                    .background(colorResource(id = R.color.red), CircleShape)
                    .padding(4.dp),
                painter = painterResource(id = R.drawable.ic_microphone_off),
                tint = Color.White,
                contentDescription = "muted"
            )
        } else {
            AudioLevel(
                modifier = Modifier.size(32.dp),
                audioLevel = audioLevel
            )
        }
    }
}

@Preview
@Composable
fun AudioParticipantPreview() {
    AppCompatTheme {
        AudioParticipant(
            initialViewSetup = { it.setInitial(byteArrayOf(0, 12, 24), "A") },
            name = "Alic B.",
            isMute = false,
            state = CONNECTED,
            audioLevel = 0.7
        )
    }
}

@Composable
private fun formatDuration(duration: Int): String {
    val hours = duration / 3600
    return if (hours == 0) String.format(
        Locale.ENGLISH,
        "%02d:%02d",
        duration / 60,
        duration % 60
    ) else String.format(
        Locale.ENGLISH,
        "%d:%02d:%02d",
        hours,
        (duration / 60) % 60,
        duration % 60
    )
}

@Preview
@Composable
private fun CallScreenPreview() {
    AppCompatTheme {
        CallScreen(
            webrtcCallService = WebrtcCallService(),
            contactListViewModel = viewModel(),
            addingParticipant = false,
            onCallAction = {})
    }
}


@Composable
fun SpeakerToggleButton(drawableRes: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .width(60.dp)
            .height(32.dp)
            .background(color = Color(0xFF29282D), shape = RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = drawableRes),
            tint = Color.White,
            contentDescription = ""
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            painter = painterResource(id = R.drawable.ic_down),
            tint = Color.White,
            contentDescription = ""
        )
    }
}

@Composable
fun SpeakerToggle(
    modifier: Modifier = Modifier,
    audioOutputs: List<AudioOutput>,
    onToggleSpeaker: (audioOutput: AudioOutput) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    Box(modifier = modifier) {
        content { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            audioOutputs.forEach { audioOutput ->
                DropdownMenuItem(
                    onClick = {
                        onToggleSpeaker(audioOutput)
                        expanded = false
                    }
                ) {
                    audioOutput.Composable()
                }
            }
        }
    }
}

@Preview
@Composable
fun SpeakerTogglePreview() {
    AppCompatTheme {
        SpeakerToggle(audioOutputs = listOf(BLUETOOTH), onToggleSpeaker = {}) {
            SpeakerToggleButton(R.drawable.ic_speaker_light_grey, it)
        }
    }
}

@Composable
fun EncryptedCallNotice(modifier: Modifier = Modifier, color: Color) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OlvidLogo()
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(id = R.string.call_encrypted_notice),
            style = OlvidTypography.body2,
            color = color
        )
    }
}

@Preview
@Composable
private fun ScreenShareOngoingPreview() {
    AppCompatTheme {
        ScreenShareOngoing {}
    }
}

@Preview
@Composable
private fun EncryptedCallNoticePreview() {
    AppCompatTheme {
        EncryptedCallNotice(color = Color.White)
    }
}
