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

package io.olvid.messenger.discussion.compose

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.text.SpannableString
import android.text.Spanned
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.DiscussionInputEditText
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.KeyboardUtils
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.focusAndShowKeyboard
import io.olvid.messenger.customClasses.formatSingleLineMarkdown
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.tasks.ClearDraftReplyTask
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask
import io.olvid.messenger.designsystem.components.dashedBorder
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.Utils
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.mention.MentionUrlSpan
import io.olvid.messenger.discussion.mention.MentionViewModel
import io.olvid.messenger.discussion.message.attachments.Attachment
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.viewModels.FilteredContactListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeMessageArea(
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding? = null,
    discussionViewModel: DiscussionViewModel = viewModel(),
    composeMessageViewModel: ComposeMessageViewModel = viewModel(),
    linkPreviewViewModel: LinkPreviewViewModel = viewModel(),
    mentionViewModel: MentionViewModel = viewModel(),
    ephemeralViewModel: EphemeralViewModel = viewModel(),
    filteredContactListViewModel: FilteredContactListViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity

    val density = LocalDensity.current
    val orientation = LocalConfiguration.current.orientation

    var inputEditText: DiscussionInputEditText? by remember { mutableStateOf(null) }

    val hasCamera by lazy { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val replyMessage by composeMessageViewModel.getDraftMessageReply().observeAsState()
    val linkPreview by linkPreviewViewModel.openGraph.observeAsState()
    val isEditMode by composeMessageViewModel.isEditMode().observeAsState()
    val ephemeralSettingsEnabled by composeMessageViewModel.ephemeralSettingsChanged.observeAsState()

    var keyboardHeight by remember(orientation) {
        mutableIntStateOf(KeyboardUtils.getHeight(context = context, orientation = orientation).takeIf { it > 0 } ?: ((if (orientation == Configuration.ORIENTATION_LANDSCAPE) 220 else 260) * density.density).toInt())
    }

    val controller = rememberComposeMessageController(
        discussionViewModel = discussionViewModel,
        composeMessageViewModel = composeMessageViewModel,
        ephemeralViewModel = ephemeralViewModel,
        linkPreviewViewModel = linkPreviewViewModel,
        mentionViewModel = mentionViewModel
    )

    LaunchedEffect(Unit) {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
    }

    val isRecording by composeMessageViewModel.getRecordingLiveData().observeAsState()
    LaunchedEffect(isRecording) {
        if (isRecording != true && controller.voiceMessageRecorder.isRecording()) {
            controller.onVoiceRecordingStop(discard = true)
        }
    }

    LaunchedEffect(isEditMode) {
        if (isEditMode == true) {
            inputEditText?.focusAndShowKeyboard()
        }
    }

    // Draft loading logic
    val draftMessage by composeMessageViewModel.getDraftMessage().observeAsState()
    val messageBeingEdited by composeMessageViewModel.getMessageBeingEdited().observeAsState()
    val draftAttachments: List<Attachment> by composeMessageViewModel.getDraftMessageFyles().map { it?.map { fyleAndStatus -> Attachment(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus) } ?: emptyList() }.observeAsState(emptyList())

    val lastProcessedDraftMessage = remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(inputEditText) {
        lastProcessedDraftMessage.value = null
    }

    LaunchedEffect(draftMessage, inputEditText) {
        val newDraft = draftMessage
        val oldDraft = lastProcessedDraftMessage.value

        lastProcessedDraftMessage.value = newDraft

        if (newDraft != null && (oldDraft == null || newDraft.id != oldDraft.id)) {
            if (newDraft.contentBody != null && inputEditText?.text.toString() != newDraft.contentBody) {
                try {
                    val spannableString = SpannableString(newDraft.contentBody ?: "")
                    newDraft.mentions?.forEach { mention ->
                        if (mention.rangeEnd <= spannableString.length) {
                            val color =
                                InitialView.getTextColor(
                                    context,
                                    mention.userIdentifier,
                                    ContactCacheSingleton.getContactCustomHue(
                                        mention.userIdentifier
                                    )
                                )
                            spannableString.setSpan(
                                MentionUrlSpan(
                                    mention.userIdentifier,
                                    mention.length,
                                    color,
                                    null
                                ),
                                mention.rangeStart,
                                mention.rangeEnd,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                    inputEditText?.setText(Utils.protectMentionUrlSpansWithFEFF(spannableString))
                    inputEditText?.setSelection(inputEditText?.text?.length ?: 0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(messageBeingEdited) {
        messageBeingEdited?.let { message ->
            val contentBody = message.contentBody ?: ""
            if (inputEditText?.text.toString() != contentBody) {
                inputEditText?.let {
                    Utils.applyBodyWithSpans(
                        it,
                        message,
                        null,
                        false,
                        false,
                        null
                    )
                    it.setSelection(it.text?.length ?: 0)
                }
            }
        }
    }

    Column(
        modifier = modifier.then(
            if (composeMessageViewModel.emojiExpanded.not()) {
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            } else Modifier
        )
    ) {

        // reply
        var snapshotReply by remember { mutableStateOf(replyMessage) }
        LaunchedEffect(replyMessage) {
            replyMessage?.let {
                snapshotReply = replyMessage
            }
        }
        AnimatedVisibility(replyMessage != null && snapshotReply != null) {
            snapshotReply?.let { message ->
                ReplyToMessage(
                    senderName =
                        ContactCacheSingleton.getContactCustomDisplayName(
                            message.senderIdentifier
                        )
                            ?: stringResource(R.string.text_deleted_contact),
                    sentByMe = message.messageType == Message.TYPE_OUTBOUND_MESSAGE,
                    messageBody = message.getStringContent(context, true).formatSingleLineMarkdown(),
                    onClearReply = {
                        App.runThread(ClearDraftReplyTask(discussionViewModel.discussionId))
                    },
                    onClick = { discussionViewModel.scrollToMessage(message.id) }
                )
            }
        }


        // link preview
        var snapshotOpenGraph by remember { mutableStateOf(linkPreview) }
        LaunchedEffect(linkPreview) {
            linkPreview?.takeIf { it.isEmpty().not() }?.let {
                snapshotOpenGraph = it.copy()
            }
        }

        AnimatedVisibility(
            visible = linkPreview?.isEmpty() == false && snapshotOpenGraph != null,
        ) {
            snapshotOpenGraph?.let { openGraph ->
                if (!openGraph.isEmpty()) {
                    LinkPreviewPicker(
                        openGraph = openGraph,
                        onClear = { linkPreviewViewModel.clearLinkPreview() },
                        onClick = { openGraph.getSafeUri()?.let { App.openLink(context, it) } }
                    )
                }
            }
        }


        // Edit Message
        AnimatedVisibility(visible = isEditMode == true) {
            EditMessage(
                onCancel = {
                    composeMessageViewModel.clearMessageBeingEdited()
                    inputEditText?.setText("")
                }
            )
        }

        // Mentions list
        MentionPicker(
            mentionViewModel = mentionViewModel,
            filteredContactListViewModel = filteredContactListViewModel,
            discussionViewModel = discussionViewModel,
            inputEditText = inputEditText
        )

        // Input area
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            AnimatedVisibility(
                visible = isEditMode != true
            ) {
                AnimatedContent(
                    targetState = isRecording,
                    transitionSpec = {
                        (scaleIn(tween(200)) + fadeIn(tween(200))).togetherWith(
                            scaleOut(tween(200)) + fadeOut(tween(200))
                        )
                    },
                    contentAlignment = Alignment.Center,
                    label = "LeftButton"
                ) { recording ->
                    if (recording == true) {
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
                                controller.onVoiceRecordingStop(discard = false)
                            }
                        ) {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                painter = painterResource(R.drawable.ic_stop),
                                contentDescription = stringResource(R.string.button_label_cancel)
                            )
                        }
                    } else {
                        IconButton(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(4.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = colorResource(id = R.color.lightGrey),
                                contentColor = colorResource(R.color.almostBlack)
                            ),
                            shape = CircleShape,
                            onClick = {
                                composeMessageViewModel.menuExpanded =
                                    !composeMessageViewModel.menuExpanded
                            }
                        ) {
                            Icon(
                                modifier = Modifier.size(16.dp),
                                painter = painterResource(R.drawable.ic_add_white),
                                contentDescription = stringResource(R.string.button_label_add)
                            )
                        }
                        AttachMenu(
                            expanded = composeMessageViewModel.menuExpanded,
                            hasCamera = hasCamera,
                            showContactIntroduction = discussionViewModel.discussion.value?.discussionType == Discussion.TYPE_CONTACT,
                            inputEditText = inputEditText,
                            onDismiss = { composeMessageViewModel.menuExpanded = false },
                            controller = controller
                        )
                    }
                }
            }
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .padding(start = 4.dp)
                        .background(
                            color = colorResource(R.color.lightGrey),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .then(
                            if (ephemeralSettingsEnabled == true) {
                                Modifier.dashedBorder(brush = SolidColor(colorResource(R.color.darkGrey)))
                            } else {
                                Modifier
                            }
                        )
                        .padding(start = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier.weight(1f, true),
                ) {
                    DiscussionInputEditText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .then(
                                // we keep DiscussionInputEditText in the composition tree
                                // otherwise its draft states are lost
                                // may be good to move out draft management
                                if (controller.voiceMessageRecorder.opened) {
                                    Modifier.height(0.dp).alpha(0f)
                                } else {
                                    Modifier
                                }
                            ),
                        fontScale = fontScale,
                        composeMessageViewModel = composeMessageViewModel,
                        mentionViewModel = mentionViewModel,
                        linkPreviewViewModel = linkPreviewViewModel,
                        discussionViewModel = discussionViewModel,
                        ephemeralMessage = ephemeralSettingsEnabled == true,
                        onSendMessage = {
                            controller.onSendMessage(
                                composeMessageViewModel.sending,
                                isEditMode == true,
                                isRecording,
                                inputEditText
                            )
                        },
                        onViewCreated = {
                            inputEditText = it
                        }
                    )
                    if (controller.voiceMessageRecorder.opened) {
                        SoundWave(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .align(Alignment.Center),
                            sample = controller.voiceMessageRecorder.soundWave,
                            showStopButton = false
                        ) {
                            controller.onVoiceRecordingStop(discard = false)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = ephemeralSettingsEnabled == true
                ) {
                    IconButton(
                        modifier = Modifier.size( 32.dp, 40.dp).requiredSize(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = colorResource(R.color.darkGrey)
                        ),
                        onClick = {
                            controller.onAttachTimer()
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.ic_ephemeral),
                            contentDescription = null
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isRecording != true,
                ) {
                    IconButton(
                        modifier = Modifier.size( 32.dp, 40.dp).requiredSize(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = colorResource(R.color.greyTint)
                        ),
                        onClick = {
                            controller.onEmojiToggled(inputEditText)
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.ic_attach_emoji),
                            contentDescription = stringResource(R.string.label_attach_emoji)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isEditMode != true && !composeMessageViewModel.hasText && !composeMessageViewModel.hasAttachments() && isRecording != true
                ) {
                    IconButton(
                        modifier = Modifier.size( 32.dp, 40.dp).requiredSize(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = colorResource(R.color.greyTint)
                        ),
                        onClick = {
                            controller.onVoiceRecordingStart()
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            painter = painterResource(R.drawable.ic_audio),
                            contentDescription = stringResource(R.string.content_description_attach_voice_message_button)
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
            }
            AnimatedVisibility(
                visible = composeMessageViewModel.hasAttachments() || composeMessageViewModel.hasText || isEditMode == true|| isRecording == true
            ) {
                SendButton(
                    modifier = Modifier.padding(start = 4.dp),
                    enabled = (isEditMode != true && !composeMessageViewModel.sending)
                            || (isEditMode == true && composeMessageViewModel.editIsSendable),
                    isAudioMode = false
                ) {
                    controller.onSendMessage(
                        composeMessageViewModel.sending,
                        isEditMode == true,
                        isRecording,
                        inputEditText
                    )
                }
            }
        }
        DraftAttachments(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            attachments = draftAttachments,
            discussionId = discussionViewModel.discussionId,
            audioAttachmentServiceBinding = audioAttachmentServiceBinding,
            onAttachmentClick = { attachment ->
                if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(
                        PreviewUtils.getNonNullMimeType(
                            attachment.fyleMessageJoinWithStatus.mimeType,
                            attachment.fyleMessageJoinWithStatus.fileName
                        )
                    ) && SettingsActivity.useInternalImageViewer()
                ) {
                    App.openDraftGalleryActivity(
                        activity,
                        attachment.fyleMessageJoinWithStatus.messageId,
                        attachment.fyleMessageJoinWithStatus.fyleId
                    )
                } else {
                    App.openFyleViewer(activity, attachment.fyleAndStatus, null)
                }
            },
            onDeleteClick = { fyleAndStatus ->
                App.runThread(DeleteAttachmentTask(fyleAndStatus.fyleAndStatus))
            }
        )
    }
    // Emoji keyboard area
    val emojiKeyboardFocusState = remember { mutableStateOf(false) }
    val keyboardIsShownButNotForEmojis = WindowInsets.isImeVisible && emojiKeyboardFocusState.value.not()

    LaunchedEffect(keyboardIsShownButNotForEmojis) {
        if (keyboardIsShownButNotForEmojis) {
            delay(500)
            composeMessageViewModel.emojiExpanded = false
        }
    }


    if (keyboardIsShownButNotForEmojis.not() || composeMessageViewModel.emojiExpanded) {
        AnimatedVisibility(
            visible = composeMessageViewModel.emojiExpanded,
            enter = expandVertically(tween(durationMillis = 200)),
            exit = shrinkVertically(tween(durationMillis = 200)),
        ) {
            val scope = rememberCoroutineScope()
            EmojiKeyboard(
                insertEmoji = { inputEditText?.insertTextAtSelection(it) },
                onBackSpace = {
                    inputEditText?.dispatchKeyEvent(
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                    )
                },
                onDismiss = {
                    composeMessageViewModel.emojiExpanded = false
                },
                onSwitchToKeyboard = {
                    inputEditText?.let {
                        @Suppress("DEPRECATION")
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.toggleSoftInput(
                            InputMethodManager.SHOW_FORCED,
                            InputMethodManager.HIDE_IMPLICIT_ONLY
                        )
                    }
                    scope.launch {
                        delay(500)
                        composeMessageViewModel.emojiExpanded = false
                    }
                },
                focusState = emojiKeyboardFocusState,
                height =
                    with(density) {
                        keyboardHeight.toDp()
                    }
            )
        }
    }

    if (WindowInsets.isImeVisible && keyboardIsShownButNotForEmojis) {
        val newKeyboardHeight = WindowInsets.imeAnimationTarget.getBottom(density)
        if (newKeyboardHeight > 0 && newKeyboardHeight != keyboardHeight) {
            KeyboardUtils.saveHeight(
                context = context,
                orientation = orientation,
                height = newKeyboardHeight
            )
            keyboardHeight = newKeyboardHeight
        }
    }
}


@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
fun ComposeMessageAreaPreview() {
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(4.dp)
            .background(colorResource(R.color.almostWhite))
    ) {
        ComposeMessageArea(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}