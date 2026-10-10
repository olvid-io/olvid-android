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

package io.olvid.messenger.webrtc.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetValue.Collapsed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.avatar.AvatarTarget
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.OwnedIdentity
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.main.contacts.ContactListViewModel
import io.olvid.messenger.webrtc.WebrtcCallService
import io.olvid.messenger.webrtc.WebrtcCallService.State.CALL_ENDED
import io.olvid.messenger.webrtc.WebrtcCallService.State.CALL_IN_PROGRESS
import io.olvid.messenger.webrtc.WebrtcPeerConnectionHolder.Companion.localVideoTrack
import io.olvid.messenger.webrtc.components.CallAction.AddParticipant
import kotlinx.coroutines.launch

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
    val resources = LocalResources.current

    val statusBarHeight = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBarHeight =
        with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

    val screenWidthDp =LocalWindowInfo.current.containerDpSize.width.value
    val callButtonSize by remember(screenWidthDp) {
        mutableFloatStateOf(
            (screenWidthDp / 6f).coerceAtMost(
                56f
            )
        )
    }

    val callState = webrtcCallService?.getState()?.observeAsState()
    val callDuration = webrtcCallService?.getCallDuration()?.observeAsState()
    val callParticipants = webrtcCallService?.getCallParticipantsLiveData()?.observeAsState()
    val contact = callParticipants?.value?.firstOrNull()?.contact
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
                    message = resources.getString(R.string.webrtc_speaking_while_muted),
                    actionLabel = resources.getString(R.string.webrtc_speaking_while_muted_action),
                )
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    if (webrtcCallService.microphoneMuted) {
                        webrtcCallService.toggleMuteMicrophone()
                    }
                }

                else -> {}
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
                var callWasStarted by remember { mutableStateOf(false) }
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
                    if (callState?.value == CALL_IN_PROGRESS || (callWasStarted && callState?.value == CALL_ENDED)) {
                        callWasStarted = true
                        VideoCallContent(
                            participants = participants,
                            webrtcCallService = webrtcCallService,
                            peekHeight = peekHeight,
                            onCallAction = onCallAction,
                            pipAspectCallback = pipAspectCallback
                        )
                    } else {
                        var avatar: AvatarTarget? by remember {
                            mutableStateOf(null)
                        }
                        var name by rememberSaveable {
                            mutableStateOf("")
                        }
                        var personalNote by rememberSaveable {
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
                                                    avatar = AvatarSource.photo(
                                                        bytesGroupOwnerAndUidOrIdentifier,
                                                        it
                                                    )
                                                } ifNull {
                                                    avatar = AvatarSource.group(
                                                        bytesGroupOwnerAndUidOrIdentifier
                                                    )
                                                }
                                                name = getString(
                                                    R.string.text_count_contacts_from_group,
                                                    callParticipants?.value.orEmpty().size,
                                                    group?.getCustomName() ?: ""
                                                )
                                            }

                                            Discussion.TYPE_GROUP_V2 -> {
                                                val group = bytesOwnedIdentity?.let { ownId ->
                                                    bytesGroupOwnerAndUidOrIdentifier?.let { groupId ->
                                                        AppDatabase.getInstance()
                                                            .group2Dao()[ownId, groupId]
                                                    }
                                                }
                                                group?.getCustomPhotoUrl()?.let {
                                                    avatar = AvatarSource.photo(
                                                        bytesGroupOwnerAndUidOrIdentifier,
                                                        it
                                                    )
                                                } ifNull {
                                                    avatar = AvatarSource.group(
                                                        bytesGroupOwnerAndUidOrIdentifier
                                                    )
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
                                    avatar = contact
                                    name = if (callParticipants?.value.orEmpty().size > 1) {
                                        webrtcCallService.resources.getQuantityString(
                                            R.plurals.text_outgoing_group_call_with_contacts,
                                            callParticipants?.value.orEmpty().size - 1,
                                            callParticipants?.value.orEmpty().size - 1,
                                            contact?.getCustomDisplayName().orEmpty()
                                        )
                                    } else {
                                        contact?.getCustomDisplayName().orEmpty()
                                    }
                                    personalNote = contact?.personalNote.orEmpty()
                                }
                            }
                        }
                        PreCallScreen(
                            name = name,
                            personalNote = personalNote,
                            status = if (callState?.value != CALL_IN_PROGRESS) {
                                callState?.value?.humanReadable(webrtcCallService.failReason) ?: ""
                            } else {
                                formatDuration(callDuration?.value ?: 0)
                            },
                            avatar = avatar,
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
                            SpeakerToggleButton(
                                webrtcCallService.selectedAudioOutput.drawableResource(),
                                onToggleSpeaker
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun CallScreenPreview() {
    CallScreen(
        webrtcCallService = null,
        contactListViewModel = viewModel(),
        addingParticipant = false,
        onCallAction = {})
}
