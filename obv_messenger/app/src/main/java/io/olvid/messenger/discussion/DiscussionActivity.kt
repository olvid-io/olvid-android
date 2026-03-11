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
package io.olvid.messenger.discussion


import android.annotation.SuppressLint
import android.app.Notification
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager.LayoutParams
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.SecureUriHandler
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.onBackPressed
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.tasks.ApplyDiscussionRetentionPoliciesTask
import io.olvid.messenger.databases.tasks.ReplaceDiscussionDraftTask
import io.olvid.messenger.databases.tasks.SaveDraftTask
import io.olvid.messenger.databases.tasks.SaveMultipleAttachmentsTask
import io.olvid.messenger.databases.tasks.SetDraftJsonExpirationTask
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.discussion.compose.ComposeMessageArea
import io.olvid.messenger.discussion.compose.ComposeMessageViewModel
import io.olvid.messenger.discussion.compose.EphemeralSettingsGroup
import io.olvid.messenger.discussion.compose.EphemeralViewModel
import io.olvid.messenger.discussion.compose.MessageEditHandler
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.mention.MentionViewModel
import io.olvid.messenger.discussion.message.MessageActionMenu
import io.olvid.messenger.discussion.search.DiscussionSearchViewModel
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.owneddetails.SelectDetailsPhotoViewModel
import io.olvid.messenger.settings.SettingsActivity
import java.io.FileInputStream
import java.io.IOException

class DiscussionActivity : LockableActivity() {
    val discussionViewModel: DiscussionViewModel by viewModels()
    private val discussionSearchViewModel: DiscussionSearchViewModel by viewModels()
    private val composeMessageViewModel: ComposeMessageViewModel by viewModels { FACTORY }
    private val linkPreviewViewModel: LinkPreviewViewModel by viewModels()
    private val mentionViewModel: MentionViewModel by viewModels()
    private val ephemeralViewModel: EphemeralViewModel by viewModels()
    private val invitationViewModel: InvitationListViewModel by viewModels()
    val audioAttachmentServiceBinding by lazy {
        runCatching { AudioAttachmentServiceBinding(this) }.onFailure { finishAndClearViewModel() }
            .getOrNull()
    }
    private val callHandler by lazy { CallHandler(this, supportFragmentManager) }
    private val locationMessageHandler by lazy { LocationMessageHandler(this, discussionViewModel) }
    private val messageEditHandler by lazy {
        MessageEditHandler(
            discussionViewModel,
            composeMessageViewModel,
            mentionViewModel
        )
    }

    data class LockedState(val icon: Int, val message: Int)

    private var lockedState by mutableStateOf<LockedState?>(null)

    private var discussionBackground by mutableStateOf<DiscussionBackground>(DiscussionBackground.Default)

    private var toolbarClickedCallback: Runnable? = null

    private var sendReadReceipt = false

    private val FACTORY: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (ComposeMessageViewModel::class.java.isAssignableFrom(modelClass)) {
                try {
                    return modelClass.getConstructor(LiveData::class.java, LiveData::class.java)
                        .newInstance(
                            discussionViewModel.discussionIdLiveData,
                            discussionViewModel.discussionCustomization
                        )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            try {
                return modelClass.getDeclaredConstructor().newInstance()
            } catch (e: InstantiationException) {
                throw RuntimeException("Cannot create an instance of $modelClass", e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException("Cannot create an instance of $modelClass", e)
            }
        }
    }

    @OptIn(
        ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class,
        ExperimentalLayoutApi::class
    )
    @SuppressLint(
        "UnsupportedChromeOsCameraSystemFeature",
        "ClickableViewAccessibility",
        "NotifyDataSetChanged"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.Transparent.toArgb(),
                ContextCompat.getColor(this, R.color.blackOverlay)
            )
        )

        super.onCreate(savedInstanceState)

        onBackPressed {
            finishAndClearViewModel()
        }

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES

        setContent {
            val discussion by discussionViewModel.discussion.observeAsState()
            val showChannelCreationSpinner by discussionViewModel.discussionContacts.map { contacts ->
                contacts?.takeIf { it.size == 1 }?.firstOrNull()?.shouldShowChannelCreationSpinner()
            }.observeAsState()
            val discussionAndGroupMembersCount by discussionViewModel.discussionGroupMemberCountLiveData.observeAsState()

            BackHandler {
                if (discussionViewModel.fullScreenPhotoUrl != null) {
                    discussionViewModel.fullScreenPhotoUrl = null
                } else if (discussionViewModel.selectedMessageInfo != null) {
                    discussionViewModel.selectedMessageInfo = null
                } else if (discussionSearchViewModel.searchExpanded) {
                    discussionSearchViewModel.reset()
                } else if (composeMessageViewModel.emojiExpanded) {
                    composeMessageViewModel.emojiExpanded = false
                } else if (discussionViewModel.isSelectingForDeletion) {
                    discussionViewModel.deselectAll()
                } else {
                    finishAndClearViewModel()
                }
            }

            val messageListState = rememberLazyListState()
            val context = LocalContext.current
            val view = LocalView.current

            LaunchedEffect(messageListState.isScrollInProgress) {
                if (messageListState.isScrollInProgress) {
                    (context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.hideSoftInputFromWindow(view.windowToken, 0)
                    composeMessageViewModel.emojiExpanded = false
                }
            }

            val fontScale = remember { mutableFloatStateOf(1f) }

            SharedTransitionLayout {
                CompositionLocalProvider(LocalUriHandler provides SecureUriHandler(this@DiscussionActivity)) {
                    Scaffold(
                        containerColor = when (discussionBackground) {
                            is DiscussionBackground.Color -> Color((discussionBackground as DiscussionBackground.Color).color)
                            DiscussionBackground.Default -> colorResource(id = R.color.almostWhite)
                            else -> Color.Transparent // Image background or no background
                        },
                        topBar = {
                            DiscussionTopAppBar(
                                discussion = discussion,
                                discussionViewModel = discussionViewModel,
                                discussionSearchViewModel = discussionSearchViewModel,
                                invitationViewModel = invitationViewModel,
                                lazyListState = messageListState,
                                supportFragmentManager = supportFragmentManager,
                                toolbarClickedCallback = toolbarClickedCallback,
                                finishAndClearViewModel = ::finishAndClearViewModel,
                                onBackPressed = {
                                    onBackPressedDispatcher.onBackPressed()
                                },
                                sharedTransitionScope = this@SharedTransitionLayout,
                            )
                        }
                    ) { contentPadding ->

                        // discussion custom background
                        AsyncImage(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = contentPadding.calculateTopPadding(),
                                    bottom = 0.dp,
                                ),
                            model = when (discussionBackground) {
                                is DiscussionBackground.Image -> (discussionBackground as DiscussionBackground.Image).bitmap
                                else -> null
                            },
                            contentScale = ContentScale.Crop,
                            contentDescription = null,
                            imageLoader = App.imageLoader
                        )


                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = contentPadding.calculateTopPadding())
                                .pointerInput(Unit) {
                                    var initialSize: Float? = null
                                    var initialScale = 1f
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.changes.size == 2) {
                                                event.changes.forEach { it.consume() }
                                                if (event.type == PointerEventType.Press) {
                                                    initialSize = null
                                                } else if (event.type == PointerEventType.Move) {
                                                    val initSize = initialSize
                                                    if (initSize == null) {
                                                        initialSize =
                                                            event.calculateCentroidSize(true)
                                                        initialScale = fontScale.floatValue
                                                    } else {
                                                        fontScale.floatValue =
                                                            (initialScale * event.calculateCentroidSize(
                                                                true
                                                            ) / initSize).coerceIn(1f, 3f)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            MessageList(
                                modifier = Modifier
                                    .weight(1f, true)
                                    .zIndex(if (discussionViewModel.selectedMessageInfo != null) 1f else 0f),
                                fontScale = fontScale.floatValue,
                                lazyListState = messageListState,
                                discussionViewModel = discussionViewModel,
                                discussionSearchViewModel = discussionSearchViewModel,
                                linkPreviewViewModel = linkPreviewViewModel,
                                invitationViewModel = invitationViewModel,
                                locationMessageHandler = locationMessageHandler,
                                callHandler = callHandler,
                                messageEditHandler = messageEditHandler,
                                composeMessageViewModel = composeMessageViewModel,
                                sendReadReceipt = sendReadReceipt,
                                messageClicked = ::messageClicked,
                                saveAttachment = ::saveAttachment,
                                saveAllAttachments = ::saveAllAttachments,
                                openMap = locationMessageHandler::openMap,
                                openDiscussionDetailsCallback = {
                                    toolbarClickedCallback?.run()
                                },
                                sharedTransitionScope = this@SharedTransitionLayout,
                            )

                            // footers
                            val lightGreyColor = colorResource(R.color.lightGrey)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colorResource(R.color.almostWhite))
                                    .drawBehind {
                                        drawLine(
                                            color = lightGreyColor,
                                            strokeWidth = 1.dp.toPx(),
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                        )
                                    }
                            ) {
                                if (discussion?.discussionType == Discussion.TYPE_CONTACT
                                    && showChannelCreationSpinner == true
                                    && discussion?.active == true
                                ) {
                                    val messages =
                                        discussionViewModel.pagedMessages.collectAsLazyPagingItems()
                                    val hasMessages = messages.itemSnapshotList.any { message -> message?.status == Message.STATUS_PROCESSING }
                                    if (hasMessages) {
                                        DiscussionNoChannel(messageRes = R.string.message_discussion_no_channel)
                                    }
                                } else if (discussionAndGroupMembersCount?.discussion != null
                                    && discussionAndGroupMembersCount?.discussion?.discussionType == Discussion.TYPE_GROUP_V2
                                    && discussionAndGroupMembersCount?.updating != 0
                                ) {
                                    DiscussionNoChannel(
                                        messageRes =
                                            if (discussionAndGroupMembersCount?.updating == Group2.UPDATE_SYNCING)
                                                R.string.message_discussion_group_v2_updating
                                            else
                                                R.string.message_discussion_group_v2_creating
                                    )
                                }
                                lockedState?.let {
                                    DiscussionLocked(state = it)
                                } ?: run {
                                    ComposeMessageArea(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .cutoutHorizontalPadding()
                                            .systemBarsHorizontalPadding()
                                            .padding(4.dp),
                                        fontScale = fontScale.floatValue,
                                        audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                                        discussionViewModel = discussionViewModel,
                                        composeMessageViewModel = composeMessageViewModel,
                                        linkPreviewViewModel = linkPreviewViewModel,
                                        mentionViewModel = mentionViewModel,
                                        ephemeralViewModel = ephemeralViewModel
                                    )
                                }
                            }
                        }
                    }
                }
                // Ephemeral settings
                if (composeMessageViewModel.openEphemeralSettings) {
                    EphemeralSettingsGroup(
                        modifier = Modifier.safeDrawingPadding(),
                        ephemeralViewModel = ephemeralViewModel,
                        expanded = composeMessageViewModel.openEphemeralSettings,
                    ) {
                        if (ephemeralViewModel.getValid()
                                .value == true
                        ) {
                            val jsonExpiration = JsonExpiration()
                            if (ephemeralViewModel.getReadOnce()) {
                                jsonExpiration.setReadOnce(true)
                            }
                            jsonExpiration.setVisibilityDuration(ephemeralViewModel.getVisibility())
                            jsonExpiration.setExistenceDuration(ephemeralViewModel.getExistence())
                            App.runThread(
                                SetDraftJsonExpirationTask(
                                    discussionViewModel.discussionId!!,
                                    jsonExpiration
                                )
                            )
                        }
                        composeMessageViewModel.openEphemeralSettings = false
                    }
                }
                AnimatedVisibility(
                    visible = discussionViewModel.fullScreenPhotoUrl != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .clickable(
                                interactionSource = null,
                                indication = null,
                            ) { discussionViewModel.fullScreenPhotoUrl = null }
                            .safeDrawingPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = discussionViewModel.fullScreenPhotoUrl,
                            contentDescription = "profile photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "profile-photo"),
                                    animatedVisibilityScope = this@AnimatedVisibility
                                ),
                            imageLoader = App.imageLoader
                        )
                    }
                }


                discussionViewModel.selectedMessageInfo?.let { message ->
                    val discussion by discussionViewModel.discussion.observeAsState()
                    discussion?.let {
                        MessageActionMenu(
                            message = message,
                            fontScale = fontScale.floatValue,
                            discussion = it,
                            onDismiss = { discussionViewModel.selectedMessageInfo = null },
                            sharedTransitionScope = this,
                            discussionViewModel = discussionViewModel,
                            messageEditHandler = messageEditHandler,
                            linkPreviewViewModel = linkPreviewViewModel,
                        )
                    }
                }
            }
        }

        discussionViewModel.latestServerTimestampOfMessageToMarkAsRead = 0

        discussionViewModel.discussion.observe(this) { discussion: Discussion? ->
            if (discussion == null) {
                toolbarClickedCallback = null
                finishAndClearViewModel()
                return@observe
            }

            if (discussion.isLocked) {
                toolbarClickedCallback = if (discussion.discussionType == Discussion.TYPE_CONTACT) {
                    Runnable {
                        App.runThread {
                            val contact = AppDatabase.getInstance()
                                .contactDao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                            if (contact != null) {
                                App.openContactDetailsActivity(
                                    this@DiscussionActivity,
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                            }
                        }
                    }
                } else {
                    null
                }
                discussionViewModel.canEdit = false
                setLocked(
                    locked = true,
                    lockedAsInactive = false,
                    lockedAsPreDiscussion = false,
                    lockedAsReadOnly = false
                )
            } else {
                when (discussion.discussionType) {
                    Discussion.TYPE_CONTACT -> {
                        if (!discussion.isPreDiscussion) {
                            discussionViewModel.canEdit = true
                            toolbarClickedCallback = Runnable {
                                App.openContactDetailsActivity(
                                    this@DiscussionActivity,
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                            }
                        }
                    }

                    Discussion.TYPE_GROUP -> {
                        if (!discussion.isPreDiscussion) {
                            discussionViewModel.canEdit = true
                            toolbarClickedCallback = Runnable {
                                App.openGroupDetailsActivity(
                                    this@DiscussionActivity,
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                            }
                        }
                    }

                    Discussion.TYPE_GROUP_V2 -> {
                        if (!discussion.isPreDiscussion) {
                            App.runThread {
                                discussionViewModel.canEdit = AppDatabase.getInstance()
                                    .group2Dao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                                    ?.ownPermissionEditOrRemoteDeleteOwnMessages == true
                            }
                            toolbarClickedCallback = Runnable {
                                App.openGroupV2DetailsActivity(
                                    this@DiscussionActivity,
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                            }
                        }
                    }
                }
                if (discussion.isPreDiscussion) {
                    setLocked(
                        locked = true,
                        lockedAsInactive = false,
                        lockedAsPreDiscussion = true,
                        lockedAsReadOnly = false
                    )
                } else if (discussion.isReadOnly) {
                    setLocked(
                        locked = true,
                        lockedAsInactive = false,
                        lockedAsPreDiscussion = false,
                        lockedAsReadOnly = true
                    )
                } else if (discussion.active) {
                    setLocked(
                        locked = false,
                        lockedAsInactive = false,
                        lockedAsPreDiscussion = false,
                        lockedAsReadOnly = false
                    )
                } else {
                    setLocked(
                        locked = true,
                        lockedAsInactive = true,
                        lockedAsPreDiscussion = false,
                        lockedAsReadOnly = false
                    )
                }
            }

            if (discussion.unread) {
                App.runThread {
                    AppDatabase.getInstance().discussionDao()
                        .updateDiscussionUnreadStatus(discussion.id, false)
                }
            }
        }

        discussionViewModel.discussionCustomization.observe(this) { discussionCustomization: DiscussionCustomization? ->
            // background color and image
            if (discussionCustomization != null) {
                val backgroundImageAbsolutePath =
                    App.absolutePathFromRelative(discussionCustomization.backgroundImageUrl)
                if (backgroundImageAbsolutePath != null) {
                    App.runThread {
                        var bitmap = BitmapFactory.decodeFile(backgroundImageAbsolutePath)
                        if (bitmap.byteCount > SelectDetailsPhotoViewModel.MAX_BITMAP_SIZE) {
                            return@runThread
                        }
                        try {
                            val exifInterface = ExifInterface(backgroundImageAbsolutePath)
                            val orientation = exifInterface.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                            bitmap = PreviewUtils.rotateBitmap(bitmap, orientation)
                        } catch (_: IOException) {
                            Logger.d("Error creating ExifInterface for file $backgroundImageAbsolutePath")
                        }
                        val finalBitmap = bitmap
                        Handler(Looper.getMainLooper()).post {
                            discussionBackground = DiscussionBackground.Image(finalBitmap)
                        }
                    }
                } else {
                    val colorJson = discussionCustomization.colorJson
                    if (colorJson != null) {
                        val color = colorJson.color + ((colorJson.alpha * 255).toInt() shl 24)
                        discussionBackground = DiscussionBackground.Color(color)
                    } else {
                        discussionBackground = DiscussionBackground.Default
                    }
                }
            } else {
                discussionBackground = DiscussionBackground.Default
            }

            // readReceipt
            val sendReadReceipt = discussionCustomization?.prefSendReadReceipt
                ?: SettingsActivity.defaultSendReadReceipt

            if (sendReadReceipt && !this@DiscussionActivity.sendReadReceipt) {
                // receipts were just switched to true, or settings were loaded --> send read receipts for all messages already ready for notification
                this@DiscussionActivity.sendReadReceipt = true
                val discussion = discussionViewModel.discussion.value
                App.runThread {
                    for (messageId in discussionViewModel.messageIdsToMarkAsRead) {
                        val message = AppDatabase.getInstance().messageDao()[messageId]
                        if (message != null && message.messageType == Message.TYPE_INBOUND_MESSAGE) {
                            message.sendMessageReturnReceipt(
                                discussion,
                                Message.RETURN_RECEIPT_STATUS_READ
                            )
                        }
                    }
                }
            } else {
                this@DiscussionActivity.sendReadReceipt = sendReadReceipt
            }
            val retainWipedOutboundMessages =
                discussionCustomization?.prefRetainWipedOutboundMessages
                    ?: SettingsActivity.defaultRetainWipedOutboundMessages

            discussionViewModel.retainWipedOutboundMessages = retainWipedOutboundMessages
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // prevent intent replay (typically on activity recreation following a dark mode change
        if (intent.hasExtra(ALREADY_PLAYED_INTENT_EXTRA)) {
            return
        }
        intent.putExtra(ALREADY_PLAYED_INTENT_EXTRA, true)

        intent.getStringExtra(SEARCH_QUERY_INTENT_EXTRA)?.let { searchQuery ->
            intent.removeExtra(SEARCH_QUERY_INTENT_EXTRA)
            val messageId = intent.getLongExtra(MESSAGE_ID_INTENT_EXTRA, -1)
            discussionSearchViewModel.focusSearchOnOpen = false
            discussionSearchViewModel.searchExpanded = true
            discussionSearchViewModel.searchText = searchQuery
            discussionSearchViewModel.filter(
                discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1), // SEARCH_QUERY_INTENT_EXTRA is only set from global search results, so we use the DISCUSSION_ID_INTENT_EXTRA to get discussion id. The discussion id is not set in the view model yet anyway...
                filterString = searchQuery,
                messageIdToSetAsCurrent = messageId
            )
        }

        if (intent.hasExtra(DISCUSSION_ID_INTENT_EXTRA)) {
            val discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, 0)
            val messageId = intent.getLongExtra(MESSAGE_ID_INTENT_EXTRA, -1)
            App.runThread {
                val discussion = AppDatabase.getInstance().discussionDao().getById(discussionId)
                runOnUiThread {
                    if (discussion == null) {
                        discussionNotFound()
                    } else {
                        setDiscussionId(discussion.id, intent)
                        if (messageId != -1L) {
                            discussionViewModel.scrollToFirstUnread = false
                            discussionViewModel.scrollToMessageRequest = ScrollRequest(messageId)
                        }
                    }
                }
            }
        } else if (intent.hasExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA) && intent.hasExtra(
                BYTES_CONTACT_IDENTITY_INTENT_EXTRA
            )
        ) {
            val bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
            val bytesContactIdentity = intent.getByteArrayExtra(BYTES_CONTACT_IDENTITY_INTENT_EXTRA)
            App.runThread {
                val discussion =
                    if (bytesOwnedIdentity != null && bytesContactIdentity != null) AppDatabase.getInstance()
                        .discussionDao().getByContactWithAnyStatus(
                            bytesOwnedIdentity,
                            bytesContactIdentity
                        ) else null
                runOnUiThread {
                    if (discussion == null) {
                        discussionNotFound()
                    } else {
                        setDiscussionId(discussion.id, intent)
                    }
                }
            }
        } else if (intent.hasExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA) && intent.hasExtra(
                BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA
            )
        ) {
            val bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
            val bytesGroupOwnerAndUid = intent.getByteArrayExtra(
                BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA
            )
            App.runThread {
                val discussion = if (bytesOwnedIdentity != null && bytesGroupOwnerAndUid != null)
                    AppDatabase.getInstance().discussionDao().getByGroupOwnerAndUidWithAnyStatus(
                        bytesOwnedIdentity,
                        bytesGroupOwnerAndUid
                    )
                else
                    null
                runOnUiThread {
                    if (discussion == null) {
                        discussionNotFound()
                    } else {
                        setDiscussionId(discussion.id, intent)
                    }
                }
            }
        } else if (intent.hasExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA) && intent.hasExtra(
                BYTES_GROUP_IDENTIFIER_INTENT_EXTRA
            )
        ) {
            val bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
            val bytesGroupIdentifier = intent.getByteArrayExtra(BYTES_GROUP_IDENTIFIER_INTENT_EXTRA)
            App.runThread {
                val discussion = if (bytesOwnedIdentity != null && bytesGroupIdentifier != null)
                    AppDatabase.getInstance().discussionDao()
                        .getByGroupIdentifierWithAnyStatus(bytesOwnedIdentity, bytesGroupIdentifier)
                else
                    null
                runOnUiThread {
                    if (discussion == null) {
                        discussionNotFound()
                    } else {
                        setDiscussionId(discussion.id, intent)
                    }
                }
            }
        } else {
            finishAndClearViewModel()
            Logger.w("Missing discussion extras in intent.")
        }
    }

    private fun discussionNotFound() {
        finishAndClearViewModel()
        App.toast(R.string.toast_message_discussion_not_found, Toast.LENGTH_SHORT)
    }

    private fun finishAndClearViewModel() {
        saveDraft()
        discussionViewModel.discussionId?.let { discussionId ->
            App.runThread(ApplyDiscussionRetentionPoliciesTask(discussionId))
        }
        discussionViewModel.deselectAll()
        discussionViewModel.discussionId = null
        finish()
    }

    private fun setDiscussionId(discussionId: Long, intent: Intent) {
        AndroidNotificationManager.setCurrentShowingDiscussionId(discussionId)
        AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionId)
        AndroidNotificationManager.clearMissedCallNotification(discussionId)
        AndroidNotificationManager.clearNeutralNotification()


        if ((discussionViewModel.discussionId != null) && (discussionId != discussionViewModel.discussionId)) {
            discussionViewModel.markMessagesRead(true)
            saveDraft()
        }

        discussionViewModel.discussionId = discussionId
        var remoteInputDraftText: String? = null
        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            remoteInputDraftText = intent.getStringExtra(Notification.EXTRA_REMOTE_INPUT_DRAFT)
        }
        if (remoteInputDraftText != null) {
            App.runThread(ReplaceDiscussionDraftTask(discussionId, remoteInputDraftText, null))
        }
    }

    override fun onResume() {
        super.onResume()
        if (discussionViewModel.screenShotBlockedForEphemeral) {
            val window = window
            window?.setFlags(
                LayoutParams.FLAG_SECURE,
                LayoutParams.FLAG_SECURE
            )
        }
        discussionViewModel.markAsReadOnPause = true

        discussionViewModel.discussionId?.let { discussionId ->
            AndroidNotificationManager.setCurrentShowingDiscussionId(discussionId)
            AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionId)
            AndroidNotificationManager.clearMissedCallNotification(discussionId)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        audioAttachmentServiceBinding?.release()
    }

    override fun onPause() {
        super.onPause()
        AndroidNotificationManager.setCurrentShowingDiscussionId(null)
        saveDraft()
        if (discussionViewModel.markAsReadOnPause) {
            discussionViewModel.markMessagesRead(true)
        }
    }

    private fun saveAttachment() {
        if (discussionViewModel.longClickedFyleAndStatus?.fyle?.isComplete == true) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.nonNullMimeType)
                .putExtra(
                    Intent.EXTRA_TITLE,
                    discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.fileName
                )
            App.startActivityForResult(this, saveAttachmentLauncher, intent)
        }
    }

    private val saveAttachmentLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val uri = data.data
                    if (StringUtils.validateUri(uri)) {
                        App.runThread {
                            try {
                                this@DiscussionActivity.contentResolver.openOutputStream(
                                    uri!!
                                ).use { os ->
                                    if (os == null) {
                                        throw Exception("Unable to write to provided Uri")
                                    }
                                    if (discussionViewModel.longClickedFyleAndStatus == null) {
                                        throw Exception()
                                    }
                                    // attachment was saved --> mark it as opened
                                    discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.markAsOpened()
                                    FileInputStream(
                                        App.absolutePathFromRelative(
                                            discussionViewModel.longClickedFyleAndStatus?.fyle?.filePath
                                        )
                                    ).use { fis ->
                                        val buffer = ByteArray(262144)
                                        var c: Int
                                        while ((fis.read(buffer).also { c = it }) != -1) {
                                            os.write(buffer, 0, c)
                                        }
                                    }
                                    App.toast(
                                        R.string.toast_message_attachment_saved,
                                        Toast.LENGTH_SHORT
                                    )
                                }
                            } catch (_: Exception) {
                                App.toast(
                                    R.string.toast_message_failed_to_save_attachment,
                                    Toast.LENGTH_SHORT
                                )
                            }
                        }
                    }
                }
            }
        }

    private fun saveAllAttachments() {
        if (discussionViewModel.longClickedFyleAndStatus != null) {
            val builder =
                SecureAlertDialogBuilder(this@DiscussionActivity, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_save_all_attachments)
                    .setMessage(R.string.dialog_message_save_all_attachments)
                    .setPositiveButton(R.string.button_label_ok) { _: DialogInterface?, _: Int ->
                        App.startActivityForResult(
                            this,
                            saveAllAttachmentsLauncher,
                            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        )
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
            builder.create().show()
        }
    }

    private val saveAllAttachmentsLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val folderUri = data.data
                    if (StringUtils.validateUri(folderUri) && discussionViewModel.longClickedFyleAndStatus != null) {
                        App.runThread(
                            SaveMultipleAttachmentsTask(
                                this,
                                folderUri,
                                discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.messageId
                            )
                        )
                    }
                }
            }
        }

    private fun saveDraft() {
        if (discussionViewModel.discussionId != null
            && discussionViewModel.locked == false
            && composeMessageViewModel.getMessageBeingEdited().value == null
            && !composeMessageViewModel.sending
        ) {
            val trimAndMentions = Utils.removeProtectionFEFFsAndTrim(
                composeMessageViewModel.rawNewMessageText, mentionViewModel.mentions
            )
            App.runThread(
                SaveDraftTask(
                    discussionViewModel.discussionId!!,
                    trimAndMentions.first,
                    composeMessageViewModel.getDraftMessage().value,
                    trimAndMentions.second,
                    !discussionViewModel.markAsReadOnPause // keep the empty draft if only exiting the discussion for a short operation
                )
            )
        }
    }

    private fun setLocked(
        locked: Boolean,
        lockedAsInactive: Boolean,
        lockedAsPreDiscussion: Boolean,
        lockedAsReadOnly: Boolean
    ) {
        if (locked) {
            lockedState = if (lockedAsPreDiscussion) {
                LockedState(R.drawable.ic_timer, R.string.message_discussion_waiting_to_join)
            } else if (lockedAsInactive) {
                LockedState(R.drawable.ic_block, R.string.message_discussion_blocked)
            } else if (lockedAsReadOnly) {
                LockedState(R.drawable.ic_show_password, R.string.message_discussion_readonly)
            } else {
                LockedState(R.drawable.ic_lock, R.string.message_discussion_locked)
            }
        } else if (this.discussionViewModel.locked != false) {
            lockedState = null
        }
        discussionViewModel.locked = locked
    }


    private fun messageClicked(message: Message) {
        if (discussionViewModel.isSelectingForDeletion) {
            discussionViewModel.selectMessageId(
                message.id,
                message.isForwardable,
                if (message.isBookmarkableAndDetailable) message.bookmarked else null
            )
        } else {
            when (message.messageType) {
                Message.TYPE_MEDIATOR_INVITATION_SENT,
                Message.TYPE_MEDIATOR_INVITATION_ACCEPTED,
                Message.TYPE_MEDIATOR_INVITATION_IGNORED -> {
                    App.runThread {
                        runCatching {
                            discussionViewModel.discussion.value?.let { discussion ->
                                if (!discussion.bytesDiscussionIdentifier.contentEquals(message.senderIdentifier)) {
                                    val contact: Contact? = AppDatabase.getInstance().contactDao()
                                        .get(
                                            discussion.bytesOwnedIdentity,
                                            message.senderIdentifier
                                        )
                                    contact?.let {
                                        runOnUiThread {
                                            App.openOneToOneDiscussionActivity(
                                                this@DiscussionActivity,
                                                it.bytesOwnedIdentity,
                                                it.bytesContactIdentity,
                                                false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    data class ScrollRequest(
        val messageId: Long,
        val highlight: Boolean = true,
        val triggeredBySearch: Boolean = false,
    ) {
        companion object {
            private const val SCROLL_TO_BOTTOM = -2L
            private const val NO_SCROLL_ID = -1L

            val None: ScrollRequest = ScrollRequest(NO_SCROLL_ID)
            val ToBottom: ScrollRequest = ScrollRequest(SCROLL_TO_BOTTOM)
        }
    }

    companion object {
        internal const val FULL_SCREEN_MAP_FRAGMENT_TAG = "full_screen_map_fragment_tag"

        private const val ALREADY_PLAYED_INTENT_EXTRA = "already_played"
        const val DISCUSSION_ID_INTENT_EXTRA: String = "discussion_id"
        const val MESSAGE_ID_INTENT_EXTRA: String = "msg_id"
        const val SEARCH_QUERY_INTENT_EXTRA: String = "search_query"
        const val BYTES_OWNED_IDENTITY_INTENT_EXTRA: String = "bytes_owned_identity"
        const val BYTES_CONTACT_IDENTITY_INTENT_EXTRA: String = "bytes_contact_identity"
        const val BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA: String = "bytes_group_uid"
        const val BYTES_GROUP_IDENTIFIER_INTENT_EXTRA: String = "bytes_group_identifier"

        // legacy ComposeMessageFragment constants
        const val ICON_EMOJI = 6
        const val ICON_SEND_LOCATION = 7
        const val ICON_INTRODUCE = 8
        const val ICON_ATTACH_POLL = 9

        const val SHORTCUT_PREFIX: String = "discussion_"
    }
}
