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
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager.LayoutParams
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.PopupMenu.OnMenuItemClickListener
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.ActionMode.Callback
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction.Press
import androidx.compose.foundation.interaction.PressInteraction.Release
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.vectordrawable.graphics.drawable.Animatable2Compat.AnimationCallback
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import coil.compose.AsyncImage
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.engine.Logger
import io.olvid.engine.engine.types.JsonIdentityDetails
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason.FORCEFULLY_UNBLOCKED
import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason.REVOKED
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.UnreadCountsSingleton
import io.olvid.messenger.activities.ShortcutActivity
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.BytesKey
import io.olvid.messenger.customClasses.DraftAttachmentAdapter.AttachmentLongClickListener
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.customClasses.LocationIntegrationSelectorDialog
import io.olvid.messenger.customClasses.LocationIntegrationSelectorDialog.OnIntegrationSelectedListener
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder.DeletionChoice
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder.Type.DISCUSSION
import io.olvid.messenger.customClasses.SecureDeleteEverywhereDialogBuilder.Type.MESSAGE
import io.olvid.messenger.customClasses.SecureUriHandler
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.onBackPressed
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.DiscussionDao.DiscussionAndGroupMembersCount
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.databases.entity.Discussion
import io.olvid.messenger.databases.entity.DiscussionCustomization
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.Group2
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.tasks.ApplyDiscussionRetentionPoliciesTask
import io.olvid.messenger.databases.tasks.CreateReadMessageMetadata
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask
import io.olvid.messenger.databases.tasks.DeleteMessagesTask
import io.olvid.messenger.databases.tasks.PropagateBookmarkedMessageChangeTask
import io.olvid.messenger.databases.tasks.ReplaceDiscussionDraftTask
import io.olvid.messenger.databases.tasks.SaveDraftTask
import io.olvid.messenger.databases.tasks.SaveMultipleAttachmentsTask
import io.olvid.messenger.databases.tasks.SetDraftReplyTask
import io.olvid.messenger.databases.tasks.propagateMuteSettings
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.compose.ComposeMessageFragment
import io.olvid.messenger.discussion.compose.ComposeMessageFragment.EmojiKeyboardAttachDelegate
import io.olvid.messenger.discussion.compose.ComposeMessageViewModel
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.location.FullscreenMapDialogFragment
import io.olvid.messenger.discussion.mention.MentionViewModel
import io.olvid.messenger.discussion.message.DateHeader
import io.olvid.messenger.discussion.message.LocationSharing
import io.olvid.messenger.discussion.message.Message
import io.olvid.messenger.discussion.message.MessageDisclaimer
import io.olvid.messenger.discussion.message.MissedMessageCount
import io.olvid.messenger.discussion.message.ScrollDownButton
import io.olvid.messenger.discussion.message.attachments.Visibility
import io.olvid.messenger.discussion.message.copyLocationToClipboard
import io.olvid.messenger.discussion.search.DiscussionSearch
import io.olvid.messenger.discussion.settings.DiscussionSettingsActivity
import io.olvid.messenger.fragments.FullScreenImageFragment
import io.olvid.messenger.fragments.dialog.MultiCallStartDialogFragment
import io.olvid.messenger.main.invitations.InvitationListItem
import io.olvid.messenger.main.invitations.InvitationListViewModel
import io.olvid.messenger.main.invitations.getAnnotatedDate
import io.olvid.messenger.notifications.AndroidNotificationManager
import io.olvid.messenger.owneddetails.SelectDetailsPhotoViewModel
import io.olvid.messenger.services.UnifiedForegroundService.LocationSharingSubService
import io.olvid.messenger.settings.SettingsActivity
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.BASIC
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.CUSTOM_OSM
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.MAPS
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.NONE
import io.olvid.messenger.settings.SettingsActivity.LocationIntegrationEnum.OSM
import io.olvid.messenger.webrtc.CallNotificationManager
import io.olvid.messenger.webrtc.WebrtcCallService
import io.olvid.messenger.webrtc.components.CallNotification
import kotlinx.coroutines.delay
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class DiscussionActivity : LockableActivity(), OnClickListener, AttachmentLongClickListener,
    OnMenuItemClickListener {

    private val discussionViewModel: DiscussionViewModel by viewModels()
    private var optionsMenuHash = -1
    private val composeMessageViewModel: ComposeMessageViewModel by viewModels { FACTORY }
    private val linkPreviewViewModel: LinkPreviewViewModel by viewModels()
    private val mentionViewModel: MentionViewModel by viewModels()
    private val invitationViewModel: InvitationListViewModel by viewModels()
    val audioAttachmentServiceBinding by lazy {
        runCatching { AudioAttachmentServiceBinding(this) }.onFailure { finishAndClearViewModel() }
            .getOrNull()
    }
    private val rootLayout: ConstraintLayout by lazy { findViewById(R.id.root_constraint_layout) }
    private val toolBar: Toolbar by lazy { findViewById(R.id.discussion_toolbar) }
    private val toolBarInitialView: InitialView by lazy { toolBar.findViewById(R.id.title_bar_initial_view) }
    private val toolBarTitle: TextView by lazy { toolBar.findViewById(R.id.title_bar_title) }
    private val toolBarSubtitle: TextView by lazy { toolBar.findViewById(R.id.title_bar_subtitle) }
    private val composeView: ComposeView by lazy { findViewById(R.id.composeView) }
    private var scrollToMessageRequest by mutableStateOf(ScrollRequest.None)
    private var scrollToFirstUnread by mutableStateOf(true)
    private var startCollectingMessagesToMarkAsRead by mutableStateOf(false)

    private var composeAreaBottomPadding: Int? by mutableStateOf(null)
    private var lockGroupBottomPadding by mutableIntStateOf(0)
    private var statusBarTopPadding by mutableIntStateOf(0)

    private val rootBackgroundImageView: ImageView by lazy { findViewById(R.id.discussion_root_background_imageview) }
    lateinit var discussionDelegate: DiscussionDelegate

    private val composeMessageFragment by lazy {
        ComposeMessageFragment().apply {
            this.discussionDelegate = this@DiscussionActivity.discussionDelegate
            setAudioAttachmentServiceBinding(audioAttachmentServiceBinding)
        }
    }
    private val composeMessageDelegate by lazy {
        composeMessageFragment.composeMessageDelegate.apply {
            setAnimateLayoutChanges(false)
            setEmojiKeyboardAttachDelegate(object :
                EmojiKeyboardAttachDelegate {
                override fun attachKeyboardFragment(fragment: Fragment) {
                    val transaction = supportFragmentManager.beginTransaction()
                    transaction.replace(R.id.emoji_keyboard_placeholder, fragment)
                    transaction.commit()
                }

                override fun detachKeyboardFragment(fragment: Fragment) {
                    val transaction = supportFragmentManager.beginTransaction()
                    transaction.remove(fragment)
                    transaction.commit()
                }
            })
            addComposeMessageHeightListener(object :
                ComposeMessageFragment.ComposeMessageHeightListener {
                override fun onNewComposeMessageHeight(heightPixels: Int) {
                    setComposeAreaBottomPadding(heightPixels)
                }
            })
        }
    }

    private fun setComposeAreaBottomPadding(height: Int) {
        composeAreaBottomPadding = height
        spacer.let {
            val params = it.layoutParams as ConstraintLayout.LayoutParams
            params.bottomMargin = height
            it.layoutParams = params
        }
    }

    private val spacer: View by lazy { findViewById(R.id.spacer) }
    private val discussionLockedGroup: View by lazy { findViewById(R.id.discussion_locked_group) }
    private val discussionLockedImage: ImageView by lazy { findViewById(R.id.discussion_locked_icon) }
    private val discussionLockedMessage: TextView by lazy { findViewById(R.id.discussion_locked_message) }

    private val discussionNoChannelGroup: View by lazy { findViewById(R.id.discussion_no_channel_group) }
    private val discussionNoChannelImageView: ImageView by lazy { findViewById(R.id.discussion_no_channel_image_view) }
    private val discussionNoChannelMessage: TextView by lazy { findViewById(R.id.discussion_no_channel_message) }
    private var actionMode: ActionMode? = null
    private var actionModeCallback: Callback? = null

    private var locked: Boolean? = null
    private var canEdit: Boolean? = null

    private val messageIdsToMarkAsRead: MutableSet<Long> by lazy { HashSet() }
    private val editedMessageIdsToMarkAsSeen: MutableSet<Long> by lazy { HashSet() }
    private var latestServerTimestampOfMessageToMarkAsRead: Long = 0
    private var toolbarClickedCallback: Runnable? = null

    private var sendReadReceipt = false
    private var retainWipedOutboundMessages = false

    private var screenShotBlockedForEphemeral = false

    private var animateLayoutChanges = false
    private var attachmentRecyclerViewWidth = 0
    private var attachmentSpace = 0
    private var attachmentFileHeight = 0

    private var discussionSearch by mutableStateOf<DiscussionSearch?>(null)

    private val closeFragmentBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            isEnabled = false
            supportFragmentManager.findFragmentByTag(FULL_SCREEN_IMAGE_FRAGMENT_TAG)
                ?.let {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(0, R.anim.fade_out)
                        .remove(it)
                        .commit()
                }
        }
    }

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


    @OptIn(ExperimentalFoundationApi::class)
    @SuppressLint(
        "UnsupportedChromeOsCameraSystemFeature",
        "ClickableViewAccessibility",
        "NotifyDataSetChanged"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressed {
            if (composeMessageDelegate.stopVoiceRecorderIfRecording()) {
                // do nothing --> recording was stopped by on back pressed
                return@onBackPressed
            }
            finishAndClearViewModel()
        }
        onBackPressedDispatcher.addCallback(this, closeFragmentBackPressedCallback)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), ContextCompat.getColor(this, R.color.blackOverlay))
        )

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            false
        setContentView(R.layout.activity_discussion)

        monitorLockViewHeight()

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            statusBarTopPadding = insets.top
            view.updateLayoutParams<MarginLayoutParams> {
                updateMargins(bottom = insets.bottom)
            }
            toolBar.updatePadding(top = insets.top)
            windowInsets
        }


        composeView.setContent {
            val context = LocalContext.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalUriHandler provides SecureUriHandler(context)) {
                AppCompatTheme {
                    val messages = discussionViewModel.pagedMessages.collectAsLazyPagingItems()
                    val invitations by discussionViewModel.invitations.observeAsState()
                    LaunchedEffect(Unit) {
                        if (composeAreaBottomPadding == null) {
                            setComposeAreaBottomPadding(with(density) { 44.dp.toPx() }.toInt())
                        }
                    }
                    LaunchedEffect(invitations) {
                        if (discussionViewModel.discussion.value?.isPreDiscussion == true && invitations.isNullOrEmpty()
                                .not()
                        ) {
                            invitations?.firstOrNull()?.let {
                                invitationViewModel.initialViewSetup(
                                    toolBarInitialView,
                                    it
                                )
                            }
                        }
                    }
                    val selectedMessageIds by discussionViewModel.selectedMessageIds.observeAsState()
                    LaunchedEffect(selectedMessageIds) {
                        if (selectedMessageIds.isNullOrEmpty().not()) {
                            actionMode?.title =
                                getResources().getQuantityString(
                                    R.plurals.action_mode_title_discussion,
                                    selectedMessageIds?.size ?: 0,
                                    selectedMessageIds?.size ?: 0
                                )
                            actionMode?.invalidate()
                        }
                    }
                    LaunchedEffect(discussionViewModel.isSelectingForDeletion) {
                        if (discussionViewModel.isSelectingForDeletion) {
                            // if selection for deletion just started, create the actionMode
                            actionMode?.finish()
                            actionMode =
                                actionModeCallback?.let { callback ->
                                    startSupportActionMode(
                                        callback
                                    )
                                }.apply {
                                    this?.title =
                                        getResources().getQuantityString(
                                            R.plurals.action_mode_title_discussion,
                                            selectedMessageIds?.size ?: 0,
                                            selectedMessageIds?.size ?: 0
                                        )
                                }
                        } else {
                            actionMode?.finish()
                        }
                    }
                    val unreadCountAndFirstMessage by discussionViewModel.unreadCountAndFirstMessage.observeAsState()
                    val lazyListState = rememberLazyListState()
                    var searchInProgress by remember {
                        mutableStateOf(false)
                    }
                    LaunchedEffect(lazyListState.isScrollInProgress) {
                        if (lazyListState.isScrollInProgress && !searchInProgress) {
                            composeMessageDelegate.hideSoftInputKeyboard()
                        }
                    }
                    var highlightMessageId by remember {
                        mutableLongStateOf(-1L)
                    }

                    // this method returns true if the messageId was found and a scroll was indeed initiated
                    // if it returns false, it attempts to load more messages
                    suspend fun scrollTo(scrollRequest: ScrollRequest): Boolean {
                        try {
                            if (scrollRequest.triggeredBySearch) {
                                searchInProgress = true
                            }
                            val snapshot = messages.itemSnapshotList
                            val pos = snapshot.indexOfFirst { it?.id == scrollRequest.messageId }

                            if (pos != -1) {
                                if (searchInProgress) {
                                    lazyListState.scrollToItem(
                                        index = 1 + pos,
                                        scrollOffset = -(lockGroupBottomPadding + (composeAreaBottomPadding
                                            ?: 0))
                                    )
                                } else {
                                    Handler(mainLooper).postDelayed({
                                        startCollectingMessagesToMarkAsRead = true
                                    }, 300)
                                    lazyListState.scrollToItem(
                                        index = 2 + pos,
                                        scrollOffset = -2 * context.resources.displayMetrics.heightPixels / 3
                                    )
                                }
                                highlightMessageId =
                                    if (scrollRequest.highlight) scrollRequest.messageId else -1L
                            } else {
                                val firstNull = snapshot.indexOfFirst { it == null }
                                if (firstNull != -1) {
                                    // access the last snapshot message to force fetching more messages
                                    messages[firstNull]
                                    return false
                                }
                            }
                        } finally {
                            if (scrollRequest.triggeredBySearch) {
                                searchInProgress = false
                            }
                        }
                        return true
                    }
                    LaunchedEffect(discussionSearch) {
                        discussionSearch?.apply {
                            this.lazyListState = lazyListState
                            scrollTo = { messageId ->
                                scrollToMessageRequest =
                                    ScrollRequest(messageId = messageId, triggeredBySearch = true)
                            }
                            intent.getStringExtra(SEARCH_QUERY_INTENT_EXTRA)?.let { searchQuery ->
                                intent.removeExtra(SEARCH_QUERY_INTENT_EXTRA)
                                val messageId = intent.getLongExtra(MESSAGE_ID_INTENT_EXTRA, -1)
                                searchItem.expandActionView()
                                muted = true
                                (searchItem.actionView as? SearchView?)?.apply {
                                    clearFocus()
                                    setQuery(
                                        searchQuery,
                                        false
                                    )
                                }
                                muted = false
                                setInitialSearchQuery(
                                    searchQuery,
                                    messageId.takeUnless { it < 0 }
                                )
                            }
                        }
                    }
                    LaunchedEffect(
                        scrollToMessageRequest,
                        messages.itemCount,
                        messages.itemSnapshotList.indexOfFirst { it == null }) {
                        when (scrollToMessageRequest) {
                            ScrollRequest.None -> Unit
                            ScrollRequest.ToBottom -> {
                                lazyListState.animateScrollToItem(0)
                                scrollToMessageRequest = ScrollRequest.None
                            }

                            else -> {
                                if (messages.itemCount > 0 && scrollTo(scrollToMessageRequest)) {
                                    // if the scroll was successful, reset the scroll request
                                    scrollToMessageRequest = ScrollRequest.None
                                }
                            }
                        }
                    }
                    LaunchedEffect(scrollToFirstUnread, unreadCountAndFirstMessage?.messageId) {
                        if (scrollToFirstUnread) {
                            unreadCountAndFirstMessage?.messageId?.let {
                                if (it > 0) {
                                    scrollToMessageRequest = ScrollRequest(messageId = it, highlight = false)
                                    scrollToFirstUnread = false
                                } else {
                                    startCollectingMessagesToMarkAsRead = true
                                }
                            }
                        }
                    }
                    val showScrollDownButton by remember {
                        derivedStateOf {
                            lazyListState.firstVisibleItemIndex > 1
                        }
                    }
                    val stickyDate by remember {
                        derivedStateOf {
                            lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                                ?.takeIf { it.contentType != "DateHeader" }?.key?.run {
                                    messages.itemSnapshotList.items.find { it.id == this }?.timestamp?.let {
                                        StringUtils.getDayOfDateString(context, it)
                                    }
                                }
                        }
                    }
                    var scale by remember { mutableFloatStateOf(1f) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                                                    initialSize = event.calculateCentroidSize(true)
                                                    initialScale = scale
                                                } else {
                                                    scale =
                                                        (initialScale * event.calculateCentroidSize(
                                                            true
                                                        ) / initSize).coerceIn(1f, 3f)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .background(color = colorResource(id = R.color.almostWhite))
                    ) {
                        // discussion custom background
                        // TODO get rid of rootBackgroundImageView wrapper
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model = rootBackgroundImageView.drawable
                                ?: rootBackgroundImageView.background,
                            contentScale = ContentScale.Crop,
                            contentDescription = null,
                            imageLoader = App.imageLoader
                        )

                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize(),
                                state = lazyListState,
                                reverseLayout = true
                            ) {
                                // dummy item for animation
                                item(key = Long.MAX_VALUE) {
                                    Spacer(
                                        modifier = Modifier
                                            .height(1.dp.coerceAtLeast(with(LocalDensity.current) {
                                                (lockGroupBottomPadding + (composeAreaBottomPadding
                                                    ?: 0)).toDp()
                                            }))
                                    )
                                }
                                // invitations
                                invitations?.reversed()?.let { invites ->
                                    items(
                                        items = invites,
                                        key = { invitation -> invitation.dialogUuid.leastSignificantBits }) { invitation ->
                                        LaunchedEffect(invitation.dialogUuid) {
                                            AndroidNotificationManager.clearInvitationNotification(
                                                invitation.dialogUuid
                                            )
                                        }
                                        InvitationListItem(
                                            modifier = Modifier.animateItem(),
                                            invitationListViewModel = invitationViewModel,
                                            invitation = invitation,
                                            title = AnnotatedString(invitation.statusText),
                                            date = invitation.getAnnotatedDate(context = context),
                                            initialViewSetup = { initialView ->
                                                invitationViewModel.initialViewSetup(
                                                    initialView,
                                                    invitation
                                                )
                                            },
                                            onClick = { action, invite, lastSAS ->
                                                invitationViewModel.invitationClicked(
                                                    action,
                                                    invite,
                                                    lastSAS,
                                                    context
                                                )
                                            }
                                        )
                                    }
                                }
                                // messages
                                items(
                                    count = messages.itemCount,
                                    key = messages.itemKey { it.id },
                                    contentType = messages.itemContentType { it.messageType }
                                ) { index ->
                                    val message = messages[index]
                                    message?.let {
                                        LaunchedEffect(
                                            startCollectingMessagesToMarkAsRead,
                                            message.id,
                                            message.status,
                                            message.messageType
                                        ) {
                                            if (startCollectingMessagesToMarkAsRead) {
                                                if (message.status == Message.STATUS_UNREAD
                                                    || message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ
                                                ) {
                                                    messageIdsToMarkAsRead.add(message.id)
                                                    if (latestServerTimestampOfMessageToMarkAsRead < message.timestamp) {
                                                        latestServerTimestampOfMessageToMarkAsRead =
                                                            message.timestamp
                                                    }

                                                    if ((message.isInbound && message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ)
                                                        || message.messageType == Message.TYPE_INBOUND_MESSAGE && message.status == Message.STATUS_UNREAD
                                                    ) {
                                                        // only send the read receipt if the content of the message was actually displayed
                                                        App.runThread {
                                                            if (sendReadReceipt) {
                                                                message.sendMessageReturnReceipt(
                                                                    discussionViewModel.discussion.value,
                                                                    Message.RETURN_RECEIPT_STATUS_READ
                                                                )
                                                            }
                                                            CreateReadMessageMetadata(message.id).run()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        LaunchedEffect(message.wipeStatus) {
                                            if (message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ && !screenShotBlockedForEphemeral) {
                                                screenShotBlockedForEphemeral = true
                                                window?.setFlags(
                                                    LayoutParams.FLAG_SECURE,
                                                    LayoutParams.FLAG_SECURE
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.animateItem()) {
                                            // unread count
                                            (unreadCountAndFirstMessage?.unreadCount ?: 0)
                                                .let { unreadCount ->
                                                    if (unreadCount > 0) {
                                                        if (message.id == unreadCountAndFirstMessage?.messageId) {
                                                            Text(
                                                                modifier = Modifier
                                                                    .padding(4.dp)
                                                                    .fillMaxWidth()
                                                                    .wrapContentWidth(align = Alignment.CenterHorizontally)
                                                                    .background(
                                                                        color = colorResource(id = R.color.red),
                                                                        shape = RoundedCornerShape(
                                                                            14.dp
                                                                        )
                                                                    )
                                                                    .padding(
                                                                        horizontal = 18.dp,
                                                                        vertical = 6.dp
                                                                    ),
                                                                text = pluralStringResource(
                                                                    id = R.plurals.text_unread_message_count,
                                                                    unreadCount,
                                                                    unreadCount
                                                                ),
                                                                style = OlvidTypography.body2,
                                                                color = colorResource(id = R.color.alwaysWhite)
                                                            )
                                                        }
                                                    }
                                                }

                                            val messageExpiration by AppDatabase.getInstance()
                                                .messageExpirationDao().getLive(message.id)
                                                .observeAsState()
                                            var offset by remember {
                                                mutableStateOf(Offset.Zero)
                                            }
                                            // TODO convert location menu to compose
                                            // location menu android view
                                            var view: View? = null
                                            AndroidView(factory = { context ->
                                                View(context).apply { view = this }
                                            }) { v ->
                                                view = v
                                            }
                                            // missed count
                                            if (message.isInbound && message.missedMessageCount > 0) {
                                                MissedMessageCount(
                                                    modifier = Modifier
                                                        .padding(vertical = 2.dp)
                                                        .padding(
                                                            start =
                                                            if (discussionViewModel.discussion.value?.discussionType != Discussion.TYPE_CONTACT) 48.dp else 8.dp
                                                        ),
                                                    missedMessageCount = message.missedMessageCount.toInt()
                                                )
                                            }
                                            // message
                                            val interactionSource =
                                                remember { MutableInteractionSource() }
                                            LaunchedEffect(highlightMessageId) {
                                                if (message.id == highlightMessageId) {
                                                    val press = Press(offset)
                                                    try {
                                                        interactionSource.emit(press)
                                                        delay(500)
                                                        highlightMessageId = -1
                                                    } finally {
                                                        interactionSource.emit(Release(press))
                                                    }
                                                }
                                            }
                                            Message(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (!discussionViewModel.isSelectingForDeletion)
                                                            Modifier.combinedClickable(
                                                                interactionSource = interactionSource,
                                                                indication = ripple(
                                                                    color = colorResource(
                                                                        id = R.color.blueOrWhiteOverlay
                                                                    )
                                                                ),
                                                                onDoubleClick = {
                                                                    enterEditModeIfAllowed(message)
                                                                },
                                                                onLongClick = {
                                                                    messageLongClicked(
                                                                        message = message,
                                                                        offset = offset
                                                                    )
                                                                }) { messageClicked(message = message) }
                                                        else
                                                            Modifier.clickable(
                                                                interactionSource = interactionSource,
                                                                indication = ripple(
                                                                    color = colorResource(
                                                                        id = R.color.blueOrWhiteOverlay
                                                                    )
                                                                )
                                                            ) { messageClicked(message = message) }
                                                    )
                                                    .background(
                                                        if (selectedMessageIds?.contains(message.id) == true)
                                                            colorResource(id = R.color.olvid_gradient_light)
                                                        else
                                                            Color.Transparent
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    .cutoutHorizontalPadding()
                                                    .onGloballyPositioned {
                                                        offset = it.positionOnScreen()
                                                    },
                                                message = it,
                                                onClick = { messageClicked(it) },
                                                onLongClick = {
                                                    messageLongClicked(
                                                        it,
                                                        offset
                                                    )
                                                },
                                                onDoubleClick = {
                                                    enterEditModeIfAllowed(message)
                                                },
                                                onLocationClick = {
                                                    onLocationClick(message)
                                                },
                                                onLocationLongClick = {
                                                    view?.let {
                                                        showLocationContextMenu(
                                                            message = message,
                                                            view = it,
                                                            truncatedLatitudeString = message.jsonMessage.getJsonLocation().truncatedLatitudeString,
                                                            truncatedLongitudeString = message.jsonMessage.getJsonLocation().truncatedLongitudeString
                                                        )
                                                    }
                                                },
                                                onAttachmentLongClick = { fyleAndStatus ->
                                                    discussionViewModel.longClickedFyleAndStatus =
                                                        fyleAndStatus
                                                },
                                                onCallBackButtonClicked = { callLogId ->
                                                    onCallBackButtonClicked(
                                                        callLogId
                                                    )
                                                },
                                                scrollToMessage = { messageId ->
                                                    scrollToMessageRequest =
                                                        ScrollRequest(messageId)
                                                },
                                                scale = scale,
                                                useAnimatedEmojis = discussionViewModel.useAnimatedEmojis,
                                                loopAnimatedEmojis = discussionViewModel.loopAnimatedEmojis,
                                                replyAction = {
                                                    discussionDelegate.replyToMessage(
                                                        message.discussionId,
                                                        message.id
                                                    )
                                                }.takeIf {
                                                    message.messageType in listOf(
                                                        Message.TYPE_INBOUND_MESSAGE,
                                                        Message.TYPE_OUTBOUND_MESSAGE
                                                    ) && locked != true
                                                },
                                                editedSeen = {
                                                    if (message.edited == Message.EDITED_UNSEEN) {
                                                        editedMessageIdsToMarkAsSeen.add(
                                                            message.id
                                                        )
                                                    }
                                                },
                                                menuAction = {
                                                    MessageLongPressPopUp(
                                                        activity = this@DiscussionActivity,
                                                        discussionDelegate = discussionDelegate,
                                                        parentView = composeView.parent as View,
                                                        clickX = offset.x.roundToInt(),
                                                        clickY = offset.y.roundToInt(),
                                                        messageId = message.id,
                                                        statusBarTopPadding = statusBarTopPadding
                                                    )
                                                },
                                                showSender = message.isInbound && discussionViewModel.discussion.value?.discussionType != Discussion.TYPE_CONTACT,
                                                lastFromSender = messages.itemSnapshotList.getOrNull(
                                                    index + 1
                                                )?.let {
                                                    !it.senderIdentifier.contentEquals(message.senderIdentifier)
                                                            || (it.messageType != Message.TYPE_INBOUND_MESSAGE && it.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE)
                                                            || Utils.notTheSameDay(message.timestamp, it.timestamp)
                                                            || !message.isTextOnly
                                                            || (message.status == Message.STATUS_UNREAD && it.status != Message.STATUS_UNREAD)
                                                } != false,
                                                linkPreviewViewModel = linkPreviewViewModel,
                                                messageExpiration = messageExpiration,
                                                discussionViewModel = discussionViewModel,
                                                discussionSearch = discussionSearch,
                                                audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                                                openDiscussionDetailsCallback = {
                                                    toolbarClickedCallback?.run()
                                                },
                                                openViewerCallback = { markAsReadOnPause = false },
                                                saveAttachment = { saveAttachment() },
                                                saveAllAttachments = { saveAllAttachments() }
                                            )
                                        }
                                        // date header
                                        if (Utils.notTheSameDay(
                                                message.timestamp,
                                                messages.itemSnapshotList.getOrNull(
                                                    index + 1
                                                )?.timestamp ?: 0
                                            )
                                        ) {
                                            val date = StringUtils.getDayOfDateString(
                                                context,
                                                message.timestamp
                                            ).toString()
                                            DateHeader(
                                                modifier = Modifier.animateItem(),
                                                date = date
                                            )
                                        }
                                    }
                                    // disclaimer
                                    if (discussionViewModel.discussion.value?.isPreDiscussion != true && index == messages.itemCount - 1) {
                                        MessageDisclaimer(
                                            modifier = Modifier
                                                .animateItem()
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                                .widthIn(max = 400.dp)
                                        )
                                    }
                                }
                                // disclaimer when no messages
                                if (discussionViewModel.discussion.value?.isPreDiscussion != true && messages.loadState.source.isIdle && messages.itemCount == 0) {
                                    item(key = -1L) {
                                        MessageDisclaimer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                                .widthIn(max = 400.dp)
                                        )
                                    }
                                }
                            }
                            val locationMessages by discussionViewModel.currentlySharingLocationMessagesLiveData.observeAsState()
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 6.dp),
                                verticalArrangement = spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                var cachedCallData by remember { mutableStateOf(CallNotificationManager.currentCallData) }
                                LaunchedEffect(CallNotificationManager.currentCallData) {
                                    if (CallNotificationManager.currentCallData != null) {
                                        cachedCallData = CallNotificationManager.currentCallData
                                    } else {
                                        delay(500)
                                        cachedCallData = null
                                    }
                                }
                                AnimatedVisibility(
                                    // visibility is the && otherwise when currentCallData becomes non-null,
                                    // cachedCallData is still null and the animation applies to a empty CallNotification
                                    visible = cachedCallData != null && CallNotificationManager.currentCallData != null
                                ) {
                                    cachedCallData?.let {
                                        CallNotification(callData = it)
                                    }
                                }


                                AnimatedVisibility(
                                    visible = locationMessages.isNullOrEmpty()
                                        .not() && lazyListState.isScrollInProgress.not()
                                ) {
                                    val isDiscussionSharingLocation =
                                        discussionViewModel.discussionId?.let {
                                            LocationSharingSubService.isDiscussionSharingLocation(
                                                it
                                            )
                                        } == true
                                    locationMessages?.let { messages ->
                                        LocationSharing(messages = messages,
                                            isDiscussionSharingLocation = isDiscussionSharingLocation,
                                            onGotoMessage = { messageId ->
                                                scrollToMessageRequest = ScrollRequest(messageId)
                                            },
                                            onStopSharingLocation = {
                                                discussionViewModel.discussionId?.let {
                                                    LocationSharingSubService.stopSharingInDiscussion(
                                                        it, false
                                                    )
                                                }
                                            },
                                            onOpenMap = { openMap() }
                                        )
                                    }
                                }
                            }
                            AnimatedVisibility(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(
                                        end = 12.dp,
                                        bottom = 12.dp + with(LocalDensity.current) {
                                            (lockGroupBottomPadding + (composeAreaBottomPadding
                                                ?: 0)).toDp()
                                        }),
                                visible = showScrollDownButton,
                                enter = scaleIn(),
                                exit = scaleOut()
                            ) {
                                ScrollDownButton {
                                    scrollToMessageRequest = ScrollRequest.ToBottom
                                }
                            }
                            AnimatedVisibility(
                                modifier = Modifier.align(Alignment.TopCenter),
                                enter = fadeIn(),
                                exit = fadeOut(animationSpec = tween(delayMillis = 500)),
                                visible = stickyDate != null && lazyListState.isScrollInProgress
                            ) {
                                stickyDate?.let {
                                    DateHeader(date = it.toString())
                                }
                            }
                        }
                    }
                }
            }
        }

        setSupportActionBar(toolBar)

        toolBar.setOnClickListener {
            toolbarClickedCallback?.run()
        }

        val toolBarBackButtonBackdrop = toolBar.findViewById<View>(R.id.back_button_backdrop)
        val toolBarBackButton = toolBar.findViewById<View>(R.id.back_button)

        toolBarBackButtonBackdrop.setOnClickListener(this)
        toolBarBackButton.setOnClickListener(this)
        toolBarInitialView.setOnClickListener(this)

        latestServerTimestampOfMessageToMarkAsRead = 0

        discussionDelegate = object : DiscussionDelegate {
            override fun markMessagesRead() {
                this@DiscussionActivity.markMessagesRead(false)
            }

            override fun doNotMarkAsReadOnPause() {
                markAsReadOnPause = false
            }

            override fun scrollToMessage(messageId: Long) {
                scrollToMessageRequest = ScrollRequest(messageId)
            }

            override fun replyToMessage(discussionId: Long, messageId: Long) {
                App.runThread(
                    SetDraftReplyTask(
                        discussionId,
                        messageId,
                        composeMessageViewModel.rawNewMessageText?.toString()
                    )
                )
                composeMessageDelegate.showSoftInputKeyboard()
            }

            override fun editMessage(message: Message) {
                enterEditModeIfAllowed(message)
                composeMessageDelegate.showSoftInputKeyboard()

            }

            override fun initiateMessageForward(messageId: Long, openDialogCallback: Runnable?) {
                discussionViewModel.messageIdsToForward = listOf(messageId)
                Utils.openForwardMessageDialog(
                    this@DiscussionActivity,
                    listOf(messageId),
                    openDialogCallback
                )
            }

            // bookmarked == null means message is not bookmarkable
            override fun selectMessage(
                messageId: Long,
                forwardable: Boolean,
                bookmarked: Boolean?
            ) {
                discussionViewModel.selectMessageId(messageId, forwardable, bookmarked)
            }

            // called after a new message is posted (to trigger a scroll to bottom if necessary)
            override fun messageWasSent() {
                scrollToMessageRequest = ScrollRequest.ToBottom
            }
        }

        actionModeCallback = object : Callback {
            private lateinit var inflater: MenuInflater

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                inflater = mode.menuInflater
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                if (discussionViewModel.selectedMessageIds.value == null) {
                    return false
                }
                menu.clear()
                inflater.inflate(R.menu.action_menu_delete, menu)
                if (discussionViewModel.areAllSelectedMessagesBookmarkable()) {
                    if (discussionViewModel.areAllSelectedMessagesBookmarked()) {
                        inflater.inflate(R.menu.popup_action_unbookmark, menu)
                    } else {
                        inflater.inflate(R.menu.popup_action_bookmark, menu)
                    }
                }
                if (discussionViewModel.areAllSelectedMessagesForwardable()) {
                    inflater.inflate(R.menu.action_menu_discussion_forward, menu)
                }
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId == R.id.action_delete_messages) {
                    val selectedMessageIds = discussionViewModel.selectedMessageIds.value
                    if (selectedMessageIds != null) {
                        val discussion = discussionViewModel.discussion.value
                        if (discussion != null) {
                            App.runThread {
                                var allMessagesAreOutbound = true
                                var remoteDeletingMakesSense = true
                                for (messageId in selectedMessageIds) {
                                    val message =
                                        AppDatabase.getInstance().messageDao()[messageId]
                                            ?: continue
                                    if (message.wipeStatus != Message.WIPE_STATUS_NONE) {
                                        remoteDeletingMakesSense = false
                                        break
                                    }
                                    if (message.messageType != Message.TYPE_OUTBOUND_MESSAGE) {
                                        allMessagesAreOutbound = false
                                    }
                                    if (message.messageType != Message.TYPE_OUTBOUND_MESSAGE && message.messageType != Message.TYPE_INBOUND_MESSAGE && message.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE) {
                                        remoteDeletingMakesSense = false
                                        break
                                    }
                                }
                                val offerToRemoteDeleteEverywhere: Boolean
                                if (remoteDeletingMakesSense) {
                                    when (discussion.discussionType) {
                                        Discussion.TYPE_GROUP_V2 -> {
                                            val group2 = AppDatabase.getInstance()
                                                .group2Dao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                                            offerToRemoteDeleteEverywhere = if (group2 != null) {
                                                (AppDatabase.getInstance().group2MemberDao()
                                                    .groupHasMembers(
                                                        discussion.bytesOwnedIdentity,
                                                        discussion.bytesDiscussionIdentifier
                                                    )
                                                        && ((allMessagesAreOutbound && group2.ownPermissionEditOrRemoteDeleteOwnMessages)
                                                        || group2.ownPermissionRemoteDeleteAnything))
                                            } else {
                                                false
                                            }
                                        }

                                        Discussion.TYPE_GROUP -> {
                                            offerToRemoteDeleteEverywhere =
                                                AppDatabase.getInstance().contactGroupJoinDao()
                                                    .groupHasMembers(
                                                        discussion.bytesOwnedIdentity,
                                                        discussion.bytesDiscussionIdentifier
                                                    ) && (allMessagesAreOutbound && discussion.isNormal)
                                        }

                                        else -> {
                                            offerToRemoteDeleteEverywhere =
                                                allMessagesAreOutbound && discussion.isNormal
                                        }
                                    }
                                } else {
                                    offerToRemoteDeleteEverywhere = false
                                }

                                val builder: Builder = SecureDeleteEverywhereDialogBuilder(
                                    this@DiscussionActivity,
                                    MESSAGE,
                                    selectedMessageIds.size,
                                    offerToRemoteDeleteEverywhere,
                                    remoteDeletingMakesSense
                                )
                                    .setDeleteCallback { deletionChoice: DeletionChoice? ->
                                        App.runThread(
                                            DeleteMessagesTask(
                                                selectedMessageIds,
                                                deletionChoice
                                            )
                                        )
                                        discussionViewModel.deselectAll()
                                    }
                                Handler(Looper.getMainLooper()).post { builder.create().show() }
                            }
                        }
                    }
                    return true
                } else if (item.itemId == R.id.action_forward_messages) {
                    val selectedMessageIds = discussionViewModel.selectedMessageIds.value
                    if (!selectedMessageIds.isNullOrEmpty()) {
                        discussionViewModel.messageIdsToForward = ArrayList(selectedMessageIds)
                        Utils.openForwardMessageDialog(
                            this@DiscussionActivity,
                            selectedMessageIds
                        ) { discussionViewModel.deselectAll() }
                    }
                } else if (item.itemId == R.id.popup_action_bookmark) {
                    val selectedMessageIds = discussionViewModel.selectedMessageIds.value
                    if (selectedMessageIds != null) {
                        App.runThread {
                            for (messageId in selectedMessageIds) {
                                AppDatabase.getInstance().messageDao()
                                    .updateBookmarked(true, messageId)
                                val message = AppDatabase.getInstance().messageDao()[messageId]
                                val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity()
                                if (message != null && bytesOwnedIdentity != null) {
                                    PropagateBookmarkedMessageChangeTask(
                                        bytesOwnedIdentity,
                                        message,
                                        true
                                    ).run()
                                }
                            }
                        }
                    }
                    actionMode?.finish()
                    return true
                } else if (item.itemId == R.id.popup_action_unbookmark) {
                    val selectedMessageIds = discussionViewModel.selectedMessageIds.value
                    if (selectedMessageIds != null) {
                        App.runThread {
                            for (messageId in selectedMessageIds) {
                                AppDatabase.getInstance().messageDao()
                                    .updateBookmarked(false, messageId)
                                val message = AppDatabase.getInstance().messageDao()[messageId]
                                val bytesOwnedIdentity = AppSingleton.getBytesCurrentIdentity()
                                if (message != null && bytesOwnedIdentity != null) {
                                    PropagateBookmarkedMessageChangeTask(
                                        bytesOwnedIdentity,
                                        message,
                                        false
                                    ).run()
                                }
                            }
                        }
                    }
                    actionMode?.finish()
                    return true
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                discussionViewModel.deselectAll()
                actionMode = null
            }
        }


        discussionViewModel.discussion.observe(this) { discussion: Discussion? ->
            if (discussion == null) {
                optionsMenuHash = -1
                toolBarTitle.text = null
                toolBarSubtitle.visibility = View.GONE
                toolBarInitialView.setUnknown()
                toolbarClickedCallback = null
                finishAndClearViewModel()
                return@observe
            }
            if (optionsMenuHash != computeOptionsMenuHash(discussionViewModel.discussion.value, discussionViewModel.discussionCustomization.value)) {
                invalidateOptionsMenu()
            }

            if (discussion.isLocked) {
                if (discussion.title.isNullOrEmpty()) {
                    val spannableString =
                        SpannableString(getString(R.string.text_unnamed_discussion))
                    spannableString.setSpan(
                        StyleSpan(Typeface.ITALIC),
                        0,
                        spannableString.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    toolBarTitle.text = spannableString
                } else {
                    toolBarTitle.text = discussion.title
                }
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
                discussionNoChannelMessage.text = null
                canEdit = false
                setLocked(
                    locked = true,
                    lockedAsInactive = false,
                    lockedAsPreDiscussion = false,
                    lockedAsReadOnly = false
                )
            } else {
                when (discussion.discussionType) {
                    Discussion.TYPE_CONTACT -> {
                        toolBarTitle.text = discussion.title
                        if (!discussion.isPreDiscussion) {
                            canEdit = true
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
                        toolBarTitle.text = discussion.title
                        if (!discussion.isPreDiscussion) {
                            canEdit = true
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
                        if (discussion.title.isNullOrEmpty()) {
                            val spannableString =
                                SpannableString(getString(R.string.text_unnamed_group))
                            spannableString.setSpan(
                                StyleSpan(Typeface.ITALIC),
                                0,
                                spannableString.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            toolBarTitle.text = spannableString
                        } else {
                            toolBarTitle.text = discussion.title
                        }
                        if (!discussion.isPreDiscussion) {
                            App.runThread {
                                canEdit = AppDatabase.getInstance()
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
                        discussionNoChannelMessage.text = null
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

            if (!discussion.isPreDiscussion) {
                toolBarInitialView.setDiscussion(discussion)
            }
            if (discussion.unread) {
                App.runThread {
                    AppDatabase.getInstance().discussionDao()
                        .updateDiscussionUnreadStatus(discussion.id, false)
                }
            }
        }

        discussionViewModel.discussionContacts.observe(this) { contacts: List<Contact>? ->
            if (contacts == null) {
                toolBarSubtitle.visibility = View.GONE
                makeDiscussionNoChannelGroupVisible(false, null)
                return@observe
            }

            // only called if discussion is not locked (locked discussions return a null List of contacts)
            val discussion = discussionViewModel.discussion.value
            if (discussion == null) {
                toolBarSubtitle.visibility = View.GONE
                makeDiscussionNoChannelGroupVisible(false, null)
                return@observe
            }
            if (discussion.discussionType == Discussion.TYPE_CONTACT && contacts.size == 1) {
                val contact = contacts[0]
                val identityDetails = contact.getIdentityDetails()
                if (identityDetails != null) {
                    if (contact.customDisplayName != null) {
                        toolBarSubtitle.visibility = View.VISIBLE
                        toolBarSubtitle.text = identityDetails.formatDisplayName(
                            JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY,
                            SettingsActivity.uppercaseLastName
                        )
                    } else {
                        val posComp =
                            identityDetails.formatPositionAndCompany(SettingsActivity.contactDisplayNameFormat)
                        if (posComp != null) {
                            toolBarSubtitle.visibility = View.VISIBLE
                            toolBarSubtitle.text = posComp
                        } else {
                            toolBarSubtitle.visibility = View.GONE
                        }
                    }
                } else {
                    toolBarSubtitle.visibility = View.GONE
                }

                makeDiscussionNoChannelGroupVisible(
                    contact.shouldShowChannelCreationSpinner() && discussion.active,
                    R.string.message_discussion_no_channel
                )
            } else {
                toolBarSubtitle.visibility = View.VISIBLE
                // for TYPE_GROUP_V2, the view is shown/hidden by getDiscussionGroupMemberCountLiveData()
                if (discussion.discussionType != Discussion.TYPE_GROUP_V2) {
                    makeDiscussionNoChannelGroupVisible(false, null)
                }
            }
        }

        discussionViewModel.discussionGroupMemberCountLiveData.observe(this) { discussionAndGroupMembersCount: DiscussionAndGroupMembersCount? ->
            if (discussionAndGroupMembersCount?.discussion != null && !discussionAndGroupMembersCount.discussion.isLocked && discussionAndGroupMembersCount.count != -1) {
                if (discussionAndGroupMembersCount.count != 0) {
                    toolBarSubtitle.text = resources.getQuantityString(
                        R.plurals.other_members_count,
                        discussionAndGroupMembersCount.count + 1,
                        discussionAndGroupMembersCount.count + 1
                    )
                } else {
                    val text = SpannableString(getString(R.string.text_empty_group))
                    val styleSpan = StyleSpan(Typeface.ITALIC)
                    text.setSpan(styleSpan, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    toolBarSubtitle.text = text
                }
            }
            if (discussionAndGroupMembersCount?.discussion != null && discussionAndGroupMembersCount.discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                makeDiscussionNoChannelGroupVisible(
                    discussionAndGroupMembersCount.updating != 0,
                    if (discussionAndGroupMembersCount.updating == Group2.UPDATE_SYNCING) R.string.message_discussion_group_v2_updating else R.string.message_discussion_group_v2_creating
                )
            }
        }

        discussionViewModel.discussionCustomization.observe(this) { discussionCustomization: DiscussionCustomization? ->
            // reload menu to show notification muted icon if needed
            invalidateOptionsMenu()

            // background color and image
            if (discussionCustomization != null) {
                val backgroundImageAbsolutePath = App.absolutePathFromRelative(discussionCustomization.backgroundImageUrl)
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
                            rootBackgroundImageView.setImageBitmap(
                                finalBitmap
                            )
                        }
                    }
                    rootBackgroundImageView.setBackgroundColor(0x00ffffff)
                } else {
                    rootBackgroundImageView.setImageDrawable(null)
                    val colorJson = discussionCustomization.colorJson
                    if (colorJson != null) {
                        val color = colorJson.color + ((colorJson.alpha * 255).toInt() shl 24)
                        rootBackgroundImageView.setBackgroundColor(color)
                    } else {
                        rootBackgroundImageView.setBackgroundColor(
                            ContextCompat.getColor(
                                this@DiscussionActivity,
                                R.color.almostWhite
                            )
                        )
                    }
                }
            } else {
                rootBackgroundImageView.setImageDrawable(null)
                rootBackgroundImageView.setBackgroundColor(
                    ContextCompat.getColor(
                        this@DiscussionActivity,
                        R.color.almostWhite
                    )
                )
            }

            // readReceipt
            val sendReadReceipt = discussionCustomization?.prefSendReadReceipt
                ?: SettingsActivity.defaultSendReadReceipt

            if (sendReadReceipt && !this@DiscussionActivity.sendReadReceipt) {
                // receipts were just switched to true, or settings were loaded --> send read receipts for all messages already ready for notification
                this@DiscussionActivity.sendReadReceipt = true
                val discussion = discussionViewModel.discussion.value
                App.runThread {
                    for (messageId in messageIdsToMarkAsRead) {
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

            this@DiscussionActivity.retainWipedOutboundMessages = retainWipedOutboundMessages
        }

        // compute first reactions dimensions
        computeDimensions()

        handleIntent(intent)
    }

    private fun onCallBackButtonClicked(callLogItemId: Long) {
        App.runThread {
            val callLogItem =
                AppDatabase.getInstance().callLogItemDao()[callLogItemId]
            if (callLogItem != null) {
                if (callLogItem.contacts.size == 1 && callLogItem.callLogItem.bytesGroupOwnerAndUidOrIdentifier == null) {
                    if (callLogItem.oneContact != null && callLogItem.oneContact.hasChannelOrPreKey()) {
                        App.startWebrtcCall(
                            this@DiscussionActivity,
                            callLogItem.oneContact.bytesOwnedIdentity,
                            callLogItem.oneContact.bytesContactIdentity
                        )
                    }
                } else {
                    val bytesContactIdentities =
                        ArrayList<BytesKey>(callLogItem.contacts.size)
                    for (callLogItemContactJoin in callLogItem.contacts) {
                        bytesContactIdentities.add(BytesKey(callLogItemContactJoin.bytesContactIdentity))
                    }
                    val multiCallStartDialogFragment =
                        MultiCallStartDialogFragment.newInstance(
                            callLogItem.callLogItem.bytesOwnedIdentity,
                            callLogItem.callLogItem.bytesGroupOwnerAndUidOrIdentifier,
                            bytesContactIdentities
                        )
                    multiCallStartDialogFragment.show(
                        supportFragmentManager,
                        "dialog"
                    )
                }
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun onLocationClick(message: Message) {
        when (SettingsActivity.locationIntegration) {
            OSM, CUSTOM_OSM, MAPS -> {
                openMap(message)
            }

            BASIC -> {
                // if basic integration is configured
                if (message.hasAttachments()) {
                    // if have a preview: show preview
                    App.runThread {
                        val fyleAndStatuses =
                            AppDatabase.getInstance()
                                .fyleMessageJoinWithStatusDao()
                                .getFylesAndStatusForMessageSync(message.id)

                        markAsReadOnPause = false
                        if (fyleAndStatuses.size == 1) {
                            App.openDiscussionGalleryActivity(
                                this@DiscussionActivity,
                                discussionViewModel.discussionId ?: -1,
                                message.id,
                                fyleAndStatuses[0].fyle.id,
                                true,
                                false
                            )
                        } else {
                            // in case we don't have a single attachment, simply open the message gallery... This should never happen :)
                            App.openMessageGalleryActivity(
                                this@DiscussionActivity,
                                message.id,
                                -1,
                                false
                            )
                        }
                    }
                } else {
                    // else : open in a third party app
                    App.openLocationInMapApplication(
                        this@DiscussionActivity,
                        message.jsonMessage.getJsonLocation().truncatedLatitudeString,
                        message.jsonMessage.getJsonLocation().truncatedLongitudeString,
                        message.contentBody
                    ) { markAsReadOnPause = false }
                }
            }

            NONE -> {
                // if no integration is configured, offer to choose an integration
                LocationIntegrationSelectorDialog(
                    this@DiscussionActivity,
                    false,
                    object :
                        OnIntegrationSelectedListener {
                        override fun onIntegrationSelected(
                            integration: LocationIntegrationEnum,
                            customOsmServerUrl: String?
                        ) {
                            SettingsActivity.setLocationIntegration(
                                integration.string,
                                customOsmServerUrl
                            )
                            // re-run onClick if something was selected
                            if (integration == OSM || integration == MAPS || integration == BASIC || integration == CUSTOM_OSM) {
                                openMap(message)
                            }
                        }
                    }).show()
            }
        }
    }

    private fun openLocationPreviewInGallery(message: Message) {
        App.runThread {
            val fyleAndStatuses = AppDatabase.getInstance().fyleMessageJoinWithStatusDao()
                .getFylesAndStatusForMessageSync(message.id)
            markAsReadOnPause = false
            if (fyleAndStatuses.size == 1) {
                App.openDiscussionGalleryActivity(
                    this@DiscussionActivity,
                    discussionViewModel.discussionId ?: -1,
                    message.id,
                    fyleAndStatuses[0].fyle.id,
                    true,
                    false
                )
            } else {
                // in case we don't have a single attachment, simply open the message gallery... This should never happen :)
                App.openMessageGalleryActivity(this@DiscussionActivity, message.id, -1, false)
            }
        }
    }

    private fun openMap(message: Message? = null) {
        // if a map integration is configured: open fullscreen map (behaviour will change depending on message.locationType)
        FullscreenMapDialogFragment.newInstance(
            message,
            discussionViewModel.discussionId,
            null,
            SettingsActivity.locationIntegration
        )?.show(
            supportFragmentManager,
            FULL_SCREEN_MAP_FRAGMENT_TAG
        )
    }

    // can be accessed by long clicking on basic integration or on a preview
    private fun showLocationContextMenu(
        message: Message,
        view: View,
        truncatedLatitudeString: String,
        truncatedLongitudeString: String
    ) {
        val locationMessagePopUp = PopupMenu(this@DiscussionActivity, view)
        val inflater = locationMessagePopUp.menuInflater
        inflater.inflate(R.menu.popup_location_message, locationMessagePopUp.menu)

        // if your sharing message: add a red stop sharing button
        if (message.isCurrentSharingOutboundLocationMessage) {
            val stopSharingItem =
                locationMessagePopUp.menu.findItem(R.id.popup_action_location_message_stop_sharing)
            if (stopSharingItem != null) {
                stopSharingItem.isVisible = true
                val spannableString = SpannableString(stopSharingItem.title)
                spannableString.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            this@DiscussionActivity,
                            R.color.red
                        )
                    ), 0, spannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                stopSharingItem.title = spannableString
            }
        }


        // if there is no preview, do not show open preview button
        if (message.totalAttachmentCount == 0) {
            val openPreviewItem =
                locationMessagePopUp.menu.findItem(R.id.popup_action_location_message_open_preview)
            openPreviewItem?.isVisible = false
        }

        locationMessagePopUp.setOnMenuItemClickListener { item: MenuItem ->
            val itemId = item.itemId
            when (itemId) {
                R.id.popup_action_location_message_open_third_party_app -> {
                    App.openLocationInMapApplication(
                        this@DiscussionActivity,
                        truncatedLatitudeString,
                        truncatedLongitudeString,
                        message.contentBody
                    ) { markAsReadOnPause = false }
                }

                R.id.popup_action_location_message_copy_coordinates -> {
                    copyLocationToClipboard(truncatedLatitudeString, truncatedLongitudeString)
                }

                R.id.popup_action_location_message_open_preview -> {
                    openLocationPreviewInGallery(message)
                }

                R.id.popup_action_location_message_stop_sharing -> {
                    val builder =
                        SecureAlertDialogBuilder(
                            this@DiscussionActivity,
                            R.style.CustomAlertDialog
                        )
                            .setTitle(R.string.title_stop_sharing_location)
                            .setMessage(R.string.label_stop_sharing_location)
                            .setPositiveButton(R.string.button_label_stop) { _: DialogInterface?, _: Int ->
                                LocationSharingSubService.stopSharingInDiscussion(
                                    discussionViewModel.discussionId ?: -1, false
                                )
                            }
                            .setNegativeButton(R.string.button_label_cancel, null)
                    builder.create()
                        .show()
                }

                R.id.popup_action_location_message_change_integration -> {
                    LocationIntegrationSelectorDialog(
                        this@DiscussionActivity,
                        true,
                        object : OnIntegrationSelectedListener {
                            override fun onIntegrationSelected(
                                integration: LocationIntegrationEnum,
                                customOsmServerUrl: String?
                            ) {
                                SettingsActivity.setLocationIntegration(
                                    integration.string,
                                    customOsmServerUrl
                                )
                            }
                        }).show()
                }
            }
            true
        }
        locationMessagePopUp.show()
    }

    private fun enterEditModeIfAllowed(message: Message) {
        if (message.messageType != Message.TYPE_OUTBOUND_MESSAGE
            || message.wipeStatus == Message.WIPE_STATUS_WIPED
            || message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED
            || message.isLocationMessage
            || message.isPollMessage
            || locked == true
            || canEdit != true
        ) {
            // prevent editing messages that cannot be edited
            return
        }
        discussionViewModel.discussionId?.let {
            // only save draft if we were not already in edit mode
            if (composeMessageViewModel.getDraftMessageEdit().value == null) {
                // keep values and save draft after edit mode is on
                val previousDraft = composeMessageViewModel.getDraftMessage().value
                val rawText = composeMessageViewModel.rawNewMessageText
                val trimAndMentions = Utils.removeProtectionFEFFsAndTrim(
                    rawText ?: "", mentionViewModel.mentions
                )
                App.runThread(
                    SaveDraftTask(
                        it,
                        trimAndMentions.first,
                        previousDraft,
                        trimAndMentions.second,
                        true
                    )
                )
            }
            composeMessageViewModel.setDraftMessageEdit(message)
            composeMessageDelegate.showSoftInputKeyboard()
        }
    }

    private fun makeDiscussionNoChannelGroupVisible(
        showDiscussionNoChannelGroup: Boolean,
        @StringRes resourceId: Int?
    ) {
        if (showDiscussionNoChannelGroup && discussionNoChannelGroup.visibility != View.VISIBLE) {
            discussionNoChannelGroup.visibility = View.VISIBLE
            resourceId?.let { discussionNoChannelMessage.setText(it) }
            val animated = AnimatedVectorDrawableCompat.create(App.getContext(), R.drawable.dots)
            if (animated != null) {
                animated.registerAnimationCallback(object : AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable) {
                        if (discussionNoChannelImageView.drawable === animated) {
                            Handler(Looper.getMainLooper()).post { animated.start() }
                        }
                    }
                })
                discussionNoChannelImageView.setImageDrawable(animated)
                animated.start()
            }
            recomputeLockedGroupHeight()
        } else if (!showDiscussionNoChannelGroup && discussionNoChannelGroup.isVisible) {
            discussionNoChannelGroup.visibility = View.GONE
            discussionNoChannelImageView.setImageDrawable(null)
            recomputeLockedGroupHeight()
        }
    }

    private fun computeDimensions() {
        // attachments dimensions
        val metrics = resources.displayMetrics

        attachmentSpace = 2 * TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f,
            metrics
        ).toInt()
        attachmentRecyclerViewWidth = min(
            (metrics.widthPixels - TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                88f,
                metrics
            ).toInt()).toDouble(),
            max(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    392f,
                    metrics
                ).toInt()
                    .toDouble(),  // for screens larger than 480dp, the width of a cell if 400dp
                (.6 * metrics.widthPixels - TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    16f,
                    metrics
                )).toInt().toDouble()
            )
        ).toInt()
        attachmentFileHeight =
            resources.getDimensionPixelSize(R.dimen.attachment_small_preview_size)
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
                            scrollToFirstUnread = false
                            scrollToMessageRequest = ScrollRequest(messageId)
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
            markMessagesRead(true)
            saveDraft()
        }

        discussionViewModel.discussionId = discussionId
        composeMessageDelegate.setDiscussionId(discussionId)

        var remoteInputDraftText: String? = null
        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            remoteInputDraftText = intent.getStringExtra(Notification.EXTRA_REMOTE_INPUT_DRAFT)
        }
        if (remoteInputDraftText != null) {
            App.runThread(ReplaceDiscussionDraftTask(discussionId, remoteInputDraftText, null))
        }
    }

    private var markAsReadOnPause = true

    override fun onResume() {
        super.onResume()
        if (screenShotBlockedForEphemeral) {
            val window = window
            window?.setFlags(
                LayoutParams.FLAG_SECURE,
                LayoutParams.FLAG_SECURE
            )
        }
        markAsReadOnPause = true

        discussionViewModel.discussionId?.let { discussionId ->
            AndroidNotificationManager.setCurrentShowingDiscussionId(discussionId)
            AndroidNotificationManager.clearReceivedMessageAndReactionsNotification(discussionId)
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
        if (markAsReadOnPause) {
            markMessagesRead(true)
        }
    }


    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            supportFragmentManager
                .findFragmentByTag(FULL_SCREEN_IMAGE_FRAGMENT_TAG)
                ?.let {
                    closeFragmentBackPressedCallback.isEnabled = false
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(0, R.anim.fade_out)
                        .remove(it)
                        .commit()
                    return true
                }
        }
        return super.dispatchTouchEvent(event)
    }

    fun computeOptionsMenuHash(discussion: Discussion?, discussionCustomization: DiscussionCustomization?) : Int {
        if (discussion == null) {
            return -1
        }
        if (discussion.isPreDiscussion) {
            return 0
        }
        var hash = 1
        if (discussion.isNormalOrReadOnly) {
            if (discussion.discussionType == Discussion.TYPE_CONTACT) {
                hash += 2
            } else {
                hash += 4
            }
            discussionCustomization?.let {
                if (it.shouldMuteNotifications()) {
                    hash += 8
                }
            }
        }
        if (discussion.active) {
            hash += 16
        }
        return hash
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        optionsMenuHash = computeOptionsMenuHash(discussionViewModel.discussion.value, discussionViewModel.discussionCustomization.value)
        val discussion = discussionViewModel.discussion.value
        if (discussion != null) {
            if (discussion.isPreDiscussion) {
                return true
            }
            menuInflater.inflate(R.menu.menu_discussion, menu)

            val searchItem = menu.findItem(R.id.action_search)
            discussionSearch = DiscussionSearch(
                this,
                menu,
                searchItem,
                discussion.id
            )

            if (discussion.isNormalOrReadOnly) {
                when (discussion.discussionType) {
                    Discussion.TYPE_CONTACT -> {
                        menuInflater.inflate(R.menu.menu_discussion_one_to_one, menu)
                        if (discussion.active) {
                            menuInflater.inflate(R.menu.menu_discussion_one_to_one_call, menu)
                        }
                    }

                    Discussion.TYPE_GROUP, Discussion.TYPE_GROUP_V2 -> {
                        menuInflater.inflate(R.menu.menu_discussion_group, menu)
                    }
                }
                val discussionCustomization = discussionViewModel.discussionCustomization.value
                if (discussionCustomization != null && discussionCustomization.shouldMuteNotifications()) {
                    menuInflater.inflate(R.menu.menu_discussion_muted, menu)
                }
            }

            if (!discussion.active) {
                menuInflater.inflate(R.menu.menu_discussion_unblock, menu)
            }

            if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
                menuInflater.inflate(R.menu.menu_discussion_shortcut, menu)
            }

            val deleteItem = menu.findItem(R.id.action_delete_discussion)
            if (deleteItem != null) {
                val spannableString = SpannableString(deleteItem.title)
                spannableString.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            this,
                            R.color.red
                        )
                    ), 0, spannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                deleteItem.title = spannableString
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }

            R.id.action_call -> {
                val discussion = discussionViewModel.discussion.value
                if (discussion != null && discussion.isNormalOrReadOnly) {
                    when (discussion.discussionType) {
                        Discussion.TYPE_CONTACT -> {
                            val contacts = discussionViewModel.discussionContacts.value
                            if (!contacts.isNullOrEmpty() && contacts[0].hasChannelOrPreKey()) {
                                App.startWebrtcCall(
                                    this,
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier
                                )
                            }
                        }

                        Discussion.TYPE_GROUP, Discussion.TYPE_GROUP_V2 -> {
                            val contacts = discussionViewModel.discussionContacts.value
                            if (contacts != null) {
                                val bytesContactIdentities: ArrayList<BytesKey>?
                                if (contacts.size > WebrtcCallService.MAX_GROUP_SIZE_TO_SELECT_ALL_BY_DEFAULT) {
                                    bytesContactIdentities = null
                                } else {
                                    bytesContactIdentities = ArrayList<BytesKey>(contacts.size)
                                    for (contact in contacts) {
                                        bytesContactIdentities.add(BytesKey(contact.bytesContactIdentity))
                                    }
                                }
                                val multiCallStartDialogFragment = MultiCallStartDialogFragment.newInstance(
                                    discussion.bytesOwnedIdentity,
                                    discussion.bytesDiscussionIdentifier,
                                    bytesContactIdentities
                                )
                                multiCallStartDialogFragment.show(supportFragmentManager, "dialog")
                            }
                        }
                    }
                }
                return true
            }

            R.id.action_details -> {
                toolbarClickedCallback?.run()
                return true
            }

            R.id.action_gallery -> {
                if (discussionViewModel.discussionId != null) {
                    val discussion = discussionViewModel.discussion.value
                    if (discussion != null) {
                        markAsReadOnPause = false
                        App.openDiscussionMediaGalleryActivity(
                            this@DiscussionActivity,
                            discussion.id
                        )
                    }
                }
                return true
            }

            R.id.action_settings -> {
                if (discussionViewModel.discussionId != null) {
                    markAsReadOnPause = false
                    val discussion = discussionViewModel.discussion.value
                    if (discussion != null) {
                        val intent = Intent(this, DiscussionSettingsActivity::class.java)
                        intent.putExtra(
                            DiscussionSettingsActivity.DISCUSSION_ID_INTENT_EXTRA,
                            discussion.id
                        )
                        startActivity(intent)
                    }
                }
                return true
            }

            R.id.action_shortcut -> {
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
                    val discussion = discussionViewModel.discussion.value
                    if (discussion != null) {
                        App.runThread {
                            val title = discussion.title.takeUnless { it.isNullOrEmpty() }
                                ?: getString(R.string.text_unnamed_discussion)
                            val builder = ShortcutActivity.getShortcutInfo(discussion.id, title)
                            if (builder != null) {
                                try {
                                    val shortcutInfo = builder.build()
                                    ShortcutManagerCompat.requestPinShortcut(
                                        this@DiscussionActivity,
                                        shortcutInfo,
                                        null
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                } else {
                    App.toast(R.string.toast_message_shortcut_not_supported, Toast.LENGTH_SHORT)
                }
                return true
            }

            R.id.action_delete_discussion -> {
                val discussion = discussionViewModel.discussion.value
                if (discussion != null) {
                    App.runThread {
                        val canRemoteDelete: Boolean
                        if (discussion.discussionType == Discussion.TYPE_GROUP_V2) {
                            val group2 = AppDatabase.getInstance()
                                .group2Dao()[discussion.bytesOwnedIdentity, discussion.bytesDiscussionIdentifier]
                            canRemoteDelete =
                                (group2 != null && group2.ownPermissionRemoteDeleteAnything
                                        && AppDatabase.getInstance().group2MemberDao()
                                    .groupHasMembers(
                                        discussion.bytesOwnedIdentity,
                                        discussion.bytesDiscussionIdentifier
                                    ))
                        } else {
                            canRemoteDelete = false
                        }
                        val builder: Builder = SecureDeleteEverywhereDialogBuilder(
                            this@DiscussionActivity, DISCUSSION, 1, canRemoteDelete, true
                        )
                            .setDeleteCallback { deletionChoice: DeletionChoice? ->
                                App.runThread(
                                    DeleteMessagesTask(
                                        discussion.id,
                                        deletionChoice,
                                        false
                                    )
                                )
                                finishAndClearViewModel()
                            }
                        Handler(Looper.getMainLooper()).post { builder.create().show() }
                    }
                }
                return true
            }

            R.id.action_unmute -> {
                val discussionCustomization = discussionViewModel.discussionCustomization.value
                if (discussionCustomization != null) {
                    if (discussionCustomization.shouldMuteNotifications()) {
                        val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_unmute_notifications)
                            .setPositiveButton(R.string.button_label_unmute_notifications) { _: DialogInterface?, _: Int ->
                                App.runThread {
                                    val reDiscussionCustomization = AppDatabase.getInstance().discussionCustomizationDao()[discussionCustomization.discussionId]
                                    reDiscussionCustomization?.let {
                                        reDiscussionCustomization.prefMuteNotifications = false
                                        AppDatabase.getInstance().discussionCustomizationDao()
                                            .update(reDiscussionCustomization)
                                        discussionViewModel.discussion.value?.let {
                                            it.propagateMuteSettings(reDiscussionCustomization)
                                            AppSingleton.getEngine().profileBackupNeeded(it.bytesOwnedIdentity)
                                        }
                                    }
                                }
                            }
                            .setNegativeButton(R.string.button_label_cancel, null)

                        if (discussionCustomization.prefMuteNotificationsTimestamp == null) {
                            builder.setMessage(R.string.dialog_message_unmute_notifications)
                        } else {
                            builder.setMessage(
                                getString(
                                    R.string.dialog_message_unmute_notifications_muted_until,
                                    StringUtils.getLongNiceDateString(
                                        this,
                                        discussionCustomization.prefMuteNotificationsTimestamp!!
                                    )
                                )
                            )
                        }
                        builder.create().show()
                    }
                }
                return true
            }

            R.id.action_unblock -> {
                val discussion = discussionViewModel.discussion.value
                if (discussion != null && discussion.isNormalOrReadOnly) {
                    if (discussion.discussionType == Discussion.TYPE_CONTACT) {
                        val notActiveReasons = AppSingleton.getEngine()
                            .getContactActiveOrInactiveReasons(
                                discussion.bytesOwnedIdentity,
                                discussion.bytesDiscussionIdentifier
                            )
                        if (notActiveReasons != null) {
                            if (notActiveReasons.contains(REVOKED)
                                && !notActiveReasons.contains(FORCEFULLY_UNBLOCKED)
                            ) {
                                val builder: Builder = SecureAlertDialogBuilder(
                                    this, R.style.CustomAlertDialog
                                )
                                builder.setTitle(R.string.dialog_title_unblock_revoked_contact_discussion)
                                    .setMessage(R.string.dialog_message_unblock_revoked_contact_discussion)
                                    .setNegativeButton(R.string.button_label_cancel, null)
                                    .setPositiveButton(R.string.button_label_unblock) { _: DialogInterface?, _: Int ->
                                        if (!AppSingleton.getEngine().forcefullyUnblockContact(
                                                discussion.bytesOwnedIdentity,
                                                discussion.bytesDiscussionIdentifier
                                            )
                                        ) {
                                            App.toast(
                                                R.string.toast_message_failed_to_unblock_contact,
                                                Toast.LENGTH_SHORT
                                            )
                                        }
                                    }
                                builder.create().show()
                                return true
                            }
                        }

                        if (!AppSingleton.getEngine().forcefullyUnblockContact(
                                discussion.bytesOwnedIdentity,
                                discussion.bytesDiscussionIdentifier
                            )
                        ) {
                            App.toast(
                                R.string.toast_message_failed_to_unblock_contact,
                                Toast.LENGTH_SHORT
                            )
                        }
                    }
                }
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }


    override fun onClick(view: View) {
        val id = view.id

        if (id == R.id.back_button || id == R.id.back_button_backdrop) {
            onBackPressedDispatcher.onBackPressed()
        } else if (id == R.id.title_bar_initial_view) {
            val alreadyShownFragment = supportFragmentManager.findFragmentByTag(
                FULL_SCREEN_IMAGE_FRAGMENT_TAG
            )
            if (alreadyShownFragment != null) {
                closeFragmentBackPressedCallback.isEnabled = false
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(0, R.anim.fade_out)
                    .remove(alreadyShownFragment)
                    .commit()
            } else {
                if (view is InitialView) {
                    val photoUrl = view.photoUrl
                    if (photoUrl != null) {
                        closeFragmentBackPressedCallback.isEnabled = true
                        val fullScreenImageFragment = FullScreenImageFragment.newInstance(photoUrl)
                        supportFragmentManager.beginTransaction()
                            .setCustomAnimations(R.anim.fade_in, 0)
                            .replace(
                                R.id.overlay,
                                fullScreenImageFragment,
                                FULL_SCREEN_IMAGE_FRAGMENT_TAG
                            )
                            .commit()
                    }
                }
            }
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

    private fun markMessagesRead(wipeReadOnceMessages: Boolean) {
        val messageIds = messageIdsToMarkAsRead.toTypedArray<Long>()
        val editedMessageIds = editedMessageIdsToMarkAsSeen.toTypedArray<Long>()

        if (messageIds.isNotEmpty() || editedMessageIds.isNotEmpty()) {
            val latestTimestamp = latestServerTimestampOfMessageToMarkAsRead
            val discussion = discussionViewModel.discussion.value
            val discussionId = discussionViewModel.discussionId

            editedMessageIdsToMarkAsSeen.clear()
            if (wipeReadOnceMessages) {
                // we keep the list if messages are not wiped yet
                messageIdsToMarkAsRead.clear()
                latestServerTimestampOfMessageToMarkAsRead = 0
            }


            App.runThread {
                val db = AppDatabase.getInstance()
                if (discussion != null && AppDatabase.getInstance().ownedDeviceDao()
                        .doesOwnedIdentityHaveAnotherDeviceWithChannel(discussion.bytesOwnedIdentity)
                ) {
                    Message.postDiscussionReadMessage(discussion, latestTimestamp)
                }
                db.messageDao().markMessagesRead(messageIds)
                db.messageDao().markEditedMessagesSeen(editedMessageIds)
                UnreadCountsSingleton.markMessagesRead(discussionId, messageIds)

                if (wipeReadOnceMessages) {
                    for (message in db.messageDao().getWipeOnReadSubset(messageIds)) {
                        db.runInTransaction {
                            if (message.messageType == Message.TYPE_OUTBOUND_MESSAGE && retainWipedOutboundMessages) {
                                message.wipe(db)
                                message.deleteAttachments(db)
                            } else {
                                message.delete(db)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveDraft() {
        if (discussionViewModel.discussionId != null && locked == false && composeMessageViewModel.getDraftMessageEdit().value == null && composeMessageViewModel.rawNewMessageText != null) {
            val trimAndMentions = Utils.removeProtectionFEFFsAndTrim(
                composeMessageViewModel.rawNewMessageText!!, mentionViewModel.mentions
            )
            App.runThread(
                SaveDraftTask(
                    discussionViewModel.discussionId!!,
                    trimAndMentions.first,
                    composeMessageViewModel.getDraftMessage().value,
                    trimAndMentions.second,
                    !markAsReadOnPause // keep the empty draft if only exiting the discussion for a short operation
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
            val transaction = supportFragmentManager.beginTransaction()
            transaction.remove(composeMessageFragment)
            transaction.commit()
            setComposeAreaBottomPadding(0)

            if (!lockedAsPreDiscussion) {
                discussionLockedGroup.visibility = View.VISIBLE
            }
            if (lockedAsInactive) {
                discussionLockedImage.setImageResource(R.drawable.ic_block)
                discussionLockedMessage.setText(R.string.message_discussion_blocked)
            } else if (lockedAsReadOnly) {
                discussionLockedImage.setImageResource(R.drawable.ic_show_password)
                discussionLockedMessage.setText(R.string.message_discussion_readonly)
            } else {
                discussionLockedImage.setImageResource(R.drawable.ic_lock)
                discussionLockedMessage.setText(R.string.message_discussion_locked)
            }
        } else if (this.locked != false) {
            discussionLockedGroup.visibility = View.GONE
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.compose_message_placeholder, composeMessageFragment)
            transaction.commit()
        }
        this.locked = locked
        recomputeLockedGroupHeight()
        Handler(Looper.getMainLooper()).postDelayed({
            animateLayoutChanges = true
            composeMessageDelegate.setAnimateLayoutChanges(true)
        }, 500)
    }

    private fun recomputeLockedGroupHeight() {
        var h = 0
        if (discussionLockedGroup.isVisible) {
            h += discussionLockedGroup.height
        }
        if (discussionNoChannelGroup.isVisible) {
            h += discussionNoChannelGroup.height
        }
        lockGroupBottomPadding = h
    }

    private fun monitorLockViewHeight() {
        val l = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> recomputeLockedGroupHeight() }
        discussionLockedGroup.addOnLayoutChangeListener(l)
        discussionNoChannelGroup.addOnLayoutChangeListener(l)
    }

    private fun messageLongClicked(message: Message, offset: Offset = Offset.Zero) {
        if (discussionViewModel.isSelectingForDeletion) {
            discussionViewModel.selectMessageId(
                message.id,
                message.isForwardable,
                if (message.isBookmarkableAndDetailable) message.bookmarked else null
            )
        } else {
            MessageLongPressPopUp(
                this@DiscussionActivity,
                discussionDelegate,
                composeView.parent as View,
                offset.x.roundToInt(),
                offset.y.roundToInt(),
                message.id,
                statusBarTopPadding
            )
        }
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
                        try {
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
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    override fun attachmentLongClicked(
        longClickedFyleAndStatus: FyleAndStatus,
        clickedView: View,
        visibility: Visibility,
        readOnce: Boolean,
        multipleAttachments: Boolean
    ) {
        App.runThread {
            val message = AppDatabase.getInstance()
                .messageDao()[longClickedFyleAndStatus.fyleMessageJoinWithStatus.messageId]
                ?: return@runThread
            discussionViewModel.longClickedFyleAndStatus = longClickedFyleAndStatus
            runOnUiThread {
                val popup = PopupMenu(this, clickedView)
                if (visibility == Visibility.HIDDEN || readOnce) {
                    popup.inflate(R.menu.popup_attachment_delete)
                } else if (longClickedFyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_DRAFT) {
                    popup.inflate(R.menu.popup_attachment_incomplete_or_draft)
                } else if (longClickedFyleAndStatus.fyle.isComplete) {
                    popup.inflate(R.menu.popup_attachment_complete)
                    if (message.status != Message.STATUS_UNPROCESSED && message.status != Message.STATUS_COMPUTING_PREVIEW && message.status != Message.STATUS_PROCESSING) {
                        popup.inflate(R.menu.popup_attachment_delete)
                    }
                    if (multipleAttachments) {
                        popup.inflate(R.menu.popup_attachment_save_all)
                    }
                } else {
                    if (longClickedFyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED) {
                        popup.inflate(R.menu.popup_attachment_delete)
                    } else {
                        popup.inflate(R.menu.popup_attachment_incomplete_or_draft)
                    }
                }
                popup.setOnMenuItemClickListener(this)
                popup.show()
            }
        }
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        val itemId = menuItem.itemId
        when (itemId) {
            R.id.popup_action_delete_attachment -> {
                val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_delete_attachment)
                    .setMessage(
                        getString(
                            R.string.dialog_message_delete_attachment,
                            discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.fileName
                                ?: ""
                        )
                    )
                    .setPositiveButton(R.string.button_label_ok) { _: DialogInterface?, _: Int ->
                        App.runThread(
                            DeleteAttachmentTask(discussionViewModel.longClickedFyleAndStatus)
                        )
                    }
                    .setNegativeButton(R.string.button_label_cancel, null)
                builder.create().show()
                return true
            }

            R.id.popup_action_open_attachment -> {
                if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(
                        PreviewUtils.getNonNullMimeType(
                            discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.mimeType,
                            discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.fileName
                        )
                    ) && SettingsActivity.useInternalImageViewer()
                ) {
                    // we do not mark as opened here as this is done in the gallery activity
                    markAsReadOnPause = false
                    App.openDiscussionGalleryActivity(
                        this,
                        discussionViewModel.discussionId ?: -1,
                        discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.messageId
                            ?: -1,
                        discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.fyleId
                            ?: -1,
                        true,
                        false
                    )
                } else {
                    App.openFyleViewer(
                        this,
                        discussionViewModel.longClickedFyleAndStatus
                    ) {
                        markAsReadOnPause = false
                        discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.markAsOpened()
                    }
                }
                return true
            }

            R.id.popup_action_share_attachment -> {
                if (discussionViewModel.longClickedFyleAndStatus != null) {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.putExtra(
                        Intent.EXTRA_STREAM,
                        discussionViewModel.longClickedFyleAndStatus?.contentUriForExternalSharing
                    )
                    intent.setType(discussionViewModel.longClickedFyleAndStatus?.fyleMessageJoinWithStatus?.nonNullMimeType)
                    startActivity(
                        Intent.createChooser(
                            intent,
                            getString(R.string.title_sharing_chooser)
                        )
                    )
                }
                return true
            }

            R.id.popup_action_save_attachment -> {
                saveAttachment()
                return true
            }

            R.id.popup_action_save_all_attachments -> {
                saveAllAttachments()
                return true
            }

            else -> return false
        }
    }

    interface DiscussionDelegate {
        fun markMessagesRead()

        fun doNotMarkAsReadOnPause()

        fun scrollToMessage(messageId: Long)

        fun replyToMessage(discussionId: Long, messageId: Long)

        fun editMessage(message: Message)

        fun initiateMessageForward(messageId: Long, openDialogCallback: Runnable?)

        // bookmarked == null means message is not bookmarkable
        fun selectMessage(messageId: Long, forwardable: Boolean, bookmarked: Boolean?)

        // called after a new message is posted (to trigger a scroll to bottom if necessary)
        fun messageWasSent()
    }

    private data class ScrollRequest(
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
        const val FULL_SCREEN_IMAGE_FRAGMENT_TAG: String = "full_screen_image"
        const val FULL_SCREEN_MAP_FRAGMENT_TAG: String = "fullscreen-sharing-map"

        private const val ALREADY_PLAYED_INTENT_EXTRA = "already_played"
        const val DISCUSSION_ID_INTENT_EXTRA: String = "discussion_id"
        const val MESSAGE_ID_INTENT_EXTRA: String = "msg_id"
        const val SEARCH_QUERY_INTENT_EXTRA: String = "search_query"
        const val BYTES_OWNED_IDENTITY_INTENT_EXTRA: String = "bytes_owned_identity"
        const val BYTES_CONTACT_IDENTITY_INTENT_EXTRA: String = "bytes_contact_identity"
        const val BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA: String = "bytes_group_uid"
        const val BYTES_GROUP_IDENTIFIER_INTENT_EXTRA: String = "bytes_group_identifier"

        const val SHORTCUT_PREFIX: String = "discussion_"
    }
}
