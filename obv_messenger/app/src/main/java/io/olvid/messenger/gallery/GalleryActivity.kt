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
package io.olvid.messenger.gallery

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff.Mode.SRC_IN
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.util.Pair
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager.LayoutParams
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer.Builder
import androidx.media3.ui.PlayerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.MessageExpiration
import io.olvid.messenger.databases.entity.TextBlock
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask
import io.olvid.messenger.designsystem.theme.olvidSwitchDefaults
import io.olvid.messenger.gallery.GalleryActivity.MENU_TYPE.DELETE_ONLY
import io.olvid.messenger.gallery.GalleryActivity.MENU_TYPE.INCOMPLETE_OR_DRAFT
import io.olvid.messenger.gallery.GalleryActivity.MENU_TYPE.NONE
import io.olvid.messenger.gallery.GalleryActivity.MENU_TYPE.STANDARD
import io.olvid.messenger.gallery.GalleryActivity.MENU_TYPE.UPLOADING
import io.olvid.messenger.gallery.GalleryAdapter.GalleryAdapterCallbacks
import io.olvid.messenger.gallery.GalleryAdapter.GalleryImageViewHolder
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.DISCUSSION
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.DRAFT
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.MESSAGE
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.OWNED_IDENTITY
import io.olvid.messenger.gallery.GalleryViewModel.MessageAndFyleId
import java.io.FileInputStream
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs


class GalleryActivity : LockableActivity() {
    private val viewModel: GalleryViewModel by viewModels()

    private var onPageChangeCallback: OnPageChangeCallback? = null
    private lateinit var galleryAdapter: GalleryAdapter


    private val viewPager: ViewPager2 by lazy { findViewById(R.id.gallery_pager) }
    private val galleryMotionLayout: MotionLayout by lazy { findViewById(R.id.gallery_motion_layout) }
    private val popupAnchorView: View by lazy { findViewById(R.id.popup_menu_anchor_view) }
    private val bottomBar: View by lazy { findViewById(R.id.bottom_bar) }
    private val bottomBarComposeView: ComposeView by lazy { findViewById(R.id.bottom_bar_compose_view) }
    private val fileNameTextView: TextView by lazy { findViewById(R.id.file_name_text_view) }
    private val fileSizeTextView: TextView by lazy { findViewById(R.id.file_size_text_view) }
    private val mimeTypeTextView: TextView by lazy { findViewById(R.id.mime_type_text_view) }
    private val resolutionTextView: TextView by lazy { findViewById(R.id.resolution_text_view) }
    private var isShowingVideo = false
    private val timerTextView: TextView by lazy { findViewById(R.id.message_timer_textview) }
    private val emptyView: TextView by lazy { findViewById(R.id.empty_view) }

    private var expirationTimer: Timer? = null
    private var expirationTimestamp: Long? = null
    private var visibilityExpiration = false
    private var readOnce = false

    private var gestureDetector: GestureDetector? = null
    private var screenShotBlockedForEphemeral = false
    private var selectedTextBlocks: List<TextBlock>? = null
    private var showTextBlocks by mutableStateOf(false)
    val clipboard: ClipboardManager? by lazy { getSystemService(CLIPBOARD_SERVICE) as ClipboardManager? }

    private enum class MENU_TYPE {
        STANDARD,
        INCOMPLETE_OR_DRAFT,
        UPLOADING,
        DELETE_ONLY,
        NONE,
    }

    private var currentMenuType = NONE


    private val saveAttachmentLauncher =
        registerForActivityResult(
            GetAttachmentSaveUri()
        ) { result: Pair<Uri, FyleAndStatus>? -> this.saveCallback(result) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_gallery)

        val topBar = findViewById<Toolbar>(R.id.top_bar)
        setSupportActionBar(topBar)

        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
        }

        emptyView.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.none, R.anim.dismiss_from_fling_down)
        }

        val mediaPlayer = runCatching {
            Builder(this).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
            }
        }.getOrNull()

        galleryAdapter =
            GalleryAdapter(layoutInflater, mediaPlayer, object : GalleryAdapterCallbacks {
                override fun singleTapUp(textBlocks: List<TextBlock>?) {
                    selectedTextBlocks = textBlocks
                    textBlocks?.takeIf { textBlocks.isNotEmpty() }?.let {
                        val positionBlock =
                            textBlocks.find { it.isBlock.not() } ?: textBlocks.find { it.isBlock }
                        popupAnchorView.apply {
                            x = positionBlock?.boundingBox?.right?.toFloat() ?: 0f
                            y = positionBlock?.boundingBox?.bottom?.toFloat() ?: 0f
                        }
                        if (it.size > 1) {
                            PopupMenu(this@GalleryActivity, popupAnchorView, Gravity.CENTER).apply {
                                menuInflater.inflate(R.menu.popup_copy_text_block_from_image, menu)
                                setOnMenuItemClickListener { item ->

                                    when (item.itemId) {
                                        R.id.popup_action_copy_text_from_image -> {
                                            selectedTextBlocks?.find { it.isBlock.not() }?.text?.let {
                                                clipboard?.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        getString(R.string.label_text_copied_from_olvid),
                                                        it
                                                    )
                                                )
                                            }
                                            return@setOnMenuItemClickListener true
                                        }

                                        R.id.popup_action_copy_text_block_from_image -> {
                                            selectedTextBlocks?.find { it.isBlock }?.text?.let {
                                                clipboard?.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        getString(R.string.label_text_copied_from_olvid),
                                                        it
                                                    )
                                                )
                                            }
                                            return@setOnMenuItemClickListener true
                                        }

                                        else -> return@setOnMenuItemClickListener false
                                    }
                                }
                                show()
                            }
                        } else {
                            App.toast(R.string.toast_message_text_copied_to_clipboard, Toast.LENGTH_SHORT)
                            positionBlock?.text?.let {
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText(
                                        getString(R.string.label_text_copied_from_olvid),
                                        it
                                    )
                                )
                            }
                        }
                    } ?: run {
                        toggleControlsAndUi()
                    }
                }

                override fun setCurrentItem(position: Int) {
                    viewPager.setCurrentItem(position, false)
                }

                override fun setViewPagerUserInputEnabled(enabled: Boolean) {
                    viewPager.isUserInputEnabled = enabled
                }
            })
        viewPager.setAdapter(galleryAdapter)
        viewPager.setOffscreenPageLimit(1)
        viewPager.setPageTransformer(MarginPageTransformer(resources.getDimensionPixelSize(R.dimen.main_activity_page_margin)))

        if (savedInstanceState != null) {
            controlsShown =
                savedInstanceState.getBoolean(CONTROLS_SHOWN_INSTANCE_STATE_EXTRA, false)
            showTextBlocks =
                savedInstanceState.getBoolean(TEXT_BLOCKS_SHOWN_INSTANCE_STATE_EXTRA, false)
        }

        onPageChangeCallback = object : OnPageChangeCallback() {
            var currentPosition: Int = 0
            var oldPlayerView: PlayerView? = null

            @OptIn(UnstableApi::class)
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                var newVideoViewHolder: GalleryImageViewHolder?
                try {
                    val oldVideoViewHolder =
                        (galleryAdapter.recyclerView.findViewHolderForAdapterPosition(
                            currentPosition
                        ) as GalleryImageViewHolder?)
                    if (oldVideoViewHolder?.playerView != null && oldVideoViewHolder.playerView.player != null) {
                        oldPlayerView = oldVideoViewHolder.playerView
                    }
                    newVideoViewHolder =
                        (galleryAdapter.recyclerView.findViewHolderForAdapterPosition(position) as GalleryImageViewHolder?)
                } catch (e: Exception) {
                    e.printStackTrace()
                    newVideoViewHolder = null
                }

                currentPosition = position

                if (mediaPlayer != null) {
                    mediaPlayer.stop()
                    mediaPlayer.clearMediaItems()

                    if (galleryAdapter.getItemViewType(position) == GalleryAdapter.VIEW_TYPE_VIDEO) {
                        if (newVideoViewHolder?.playerView != null) {
                            PlayerView.switchTargetView(
                                mediaPlayer,
                                oldPlayerView,
                                newVideoViewHolder.playerView
                            )
                        }
                        val fyleAndStatus = galleryAdapter.getItemAt(position)
                        if (fyleAndStatus != null) {
                            var filePath = App.absolutePathFromRelative(fyleAndStatus.fyle.filePath)
                            if (filePath == null) {
                                filePath = fyleAndStatus.fyleMessageJoinWithStatus.absoluteFilePath
                            }
                            val mediaItem = MediaItem.Builder().setUri(filePath).setMediaMetadata(
                                MediaMetadata.Builder().setTitle("").build()
                            ).build()
                            mediaPlayer.setMediaItem(mediaItem)
                            mediaPlayer.playWhenReady = true
                            mediaPlayer.prepare()
                        }
                    }
                }
                viewModel.setCurrentPagerPosition(currentPosition)
            }
        }

        viewModel.imageAndVideoFyleAndStatusList.observe(this, galleryAdapter)
        viewModel.imageAndVideoFyleAndStatusList.observe(
            this
        ) { fyleAndStatuses: List<FyleAndStatus?>? ->
            if (fyleAndStatuses != null && fyleAndStatuses.isEmpty()) {
                emptyView.visibility = View.VISIBLE
            } else {
                emptyView.visibility = View.GONE
            }
        }

        viewModel.currentFyleAndStatus.observe(
            this
        ) { fyleAndStatus: FyleAndStatus? ->
            this.bindFileInfo(
                fyleAndStatus
            )
        }
        viewModel.currentAssociatedMessage.observe(
            this
        ) { message: Message? -> this.updateMenuForMessage(message) }
        viewModel.currentAssociatedMessage.observe(
            this
        ) { message: Message? ->
            this.blockScreenshotsIfMessageIsEphemeral(
                message
            )
        }
        viewModel.currentAssociatedMessageExpiration.observe(
            this
        ) { messageExpiration: MessageExpiration? ->
            this.updateMessageExpiration(
                messageExpiration
            )
        }

        bottomBarComposeView.setContent {
            val fyleAndStatus by viewModel.currentFyleAndStatus.observeAsState()
            val textBlocks by viewModel.currentAssociatedTextBlocks.observeAsState()
            LaunchedEffect(textBlocks, showTextBlocks) {
                (galleryAdapter.recyclerView.findViewHolderForAdapterPosition(
                    viewPager.currentItem
                ) as GalleryImageViewHolder?)?.imageView?.apply {
                    setTextBlocks(textBlocks?.takeIf { showTextBlocks })
                    invalidate()
                }
            }
            AnimatedVisibility(
                visible = fyleAndStatus?.fyleMessageJoinWithStatus?.textExtracted == true
                        && fyleAndStatus?.fyleMessageJoinWithStatus?.textContent.isNullOrEmpty().not(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = colorResource(R.color.blackOverlay)),
                    contentAlignment = Alignment.Center,
                ) {
                    Switch(checked = showTextBlocks,
                        onCheckedChange = { checked ->
                            showTextBlocks = checked
                        },
                        colors = olvidSwitchDefaults(),
                        thumbContent = {
                            Icon(
                                modifier = Modifier.padding(4.dp),
                                painter = painterResource(R.drawable.ic_message_status_draft),
                                contentDescription = null
                            )
                        })
                }
            }
        }

        val intent = intent
        if (intent == null) {
            finish()
            return
        }

        if (viewModel.getCurrentPagerPosition() == null) {
            val bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
            val sortOrder = intent.getStringExtra(BYTES_OWNED_IDENTITY_SORT_ORDER_INTENT_EXTRA)
            val ascending = intent.getBooleanExtra(ASCENDING_INTENT_EXTRA, true)
            val discussionId = intent.getLongExtra(DISCUSSION_ID_INTENT_EXTRA, -1)
            val draft = intent.getBooleanExtra(DRAFT_INTENT_EXTRA, false)
            val messageId = intent.getLongExtra(INITIAL_MESSAGE_ID_INTENT_EXTRA, -1)
            val fyleId = intent.getLongExtra(INITIAL_FYLE_ID_INTENT_EXTRA, -1)
            var showTextBlocks = intent.getBooleanExtra(SHOW_TEXT_BLOCKS_INTENT_EXTRA, false)


            if (discussionId != -1L) {
                viewModel.setDiscussionId(discussionId, ascending)
            } else if (bytesOwnedIdentity != null) {
                viewModel.setBytesOwnedIdentity(bytesOwnedIdentity, sortOrder, ascending)
            } else if (messageId != -1L) {
                viewModel.setMessageId(messageId, draft)
            } else {
                finish()
                return
            }

            if (messageId != -1L && fyleId != -1L) {
                val messageAndFyleId = MessageAndFyleId(messageId, fyleId)
                galleryAdapter.goToFyle(messageAndFyleId)
            }

            if (showTextBlocks) {
                this.showTextBlocks = true
                if (!controlsShown) {
                    showControlsAndUi()
                }
            }
        }

        viewPager.setOnApplyWindowInsetsListener { _: View, insets: WindowInsets ->
            this.onApplyWindowInsets(
                insets
            )
        }

        val window = window
        if (window != null && VERSION.SDK_INT >= VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (window != null) {
            window.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)
            // wait 1s before adding the handler, so that systemUiVisibility stabilizes
            Handler(Looper.getMainLooper()).postDelayed({
                window.decorView.setOnSystemUiVisibilityChangeListener { visibility: Int ->
                    if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        if (!controlsShown) {
                            showControlsAndUi()
                        }
                    }
                }
            }, 1000)
        }

        val DENSITY_INDEPENDENT_THRESHOLD = 200
        val SWIPE_THRESHOLD_VELOCITY =
            (DENSITY_INDEPENDENT_THRESHOLD * resources.displayMetrics.density).toInt()

        gestureDetector = GestureDetector(this, object : SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isShowingVideo && controlsShown && e.y > topBar.bottom) {
                    hideControlsAndUi()
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isShowingVideo) {
                    toggleControlsAndUi()
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (4 * abs(velocityX.toDouble()) < abs(velocityY.toDouble()) && (velocityY > SWIPE_THRESHOLD_VELOCITY || velocityY < -SWIPE_THRESHOLD_VELOCITY)
                    && viewPager.isUserInputEnabled
                    && e1 != null
                    && e1.y > 100 // no fling if starting from top of screen (like to show status bar)
                ) {
                    finish()
                    if (velocityY < 0) {
                        overridePendingTransition(R.anim.none, R.anim.dismiss_from_fling_up)
                    } else {
                        overridePendingTransition(R.anim.none, R.anim.dismiss_from_fling_down)
                    }
                    return true
                }
                return false
            }
        })
    }

    private fun blockScreenshotsIfMessageIsEphemeral(message: Message?) {
        if (message != null && !screenShotBlockedForEphemeral) {
            if (message.wipeStatus != Message.WIPE_STATUS_WIPE_ON_READ) {
                try {
                    val expiration = AppSingleton.getJsonObjectMapper().readValue(
                        message.jsonExpiration,
                        JsonExpiration::class.java
                    )
                    if (expiration.getVisibilityDuration() == null) {
                        return
                    }
                } catch (_: Exception) {
                    return
                }
            }

            // message is readOnce or has limited visibility duration --> prevent screenshot
            val window = window
            if (window != null) {
                screenShotBlockedForEphemeral = true
                window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)
            }
        }
    }


    private fun updateMenuForMessage(message: Message?) {
        val newMenuType: MENU_TYPE
        val fyleAndStatus = viewModel.currentFyleAndStatus.value
        newMenuType =
            if (fyleAndStatus == null || message == null || fyleAndStatus.fyleMessageJoinWithStatus.messageId != message.id) {
                NONE
            } else {
                if (message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ || fyleAndStatus.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED) {
                    DELETE_ONLY
                } else if (message.status == Message.STATUS_DRAFT || !fyleAndStatus.fyle.isComplete) {
                    INCOMPLETE_OR_DRAFT
                } else if (message.status == Message.STATUS_UNPROCESSED || message.status == Message.STATUS_PROCESSING) {
                    UPLOADING
                } else {
                    STANDARD
                }
            }

        if (newMenuType != currentMenuType) {
            currentMenuType = newMenuType
            invalidateOptionsMenu()
        }

        if (message == null) {
            if (readOnce) {
                readOnce = false
                updateExpirationTimer(true)
            }
        } else {
            if (readOnce != (message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ)) {
                readOnce = message.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ
                updateExpirationTimer(true)
            }
        }
    }

    private var lastMessageExpirationMessageId: Long = -1

    private fun updateMessageExpiration(messageExpiration: MessageExpiration?) {
        if (messageExpiration != null) {
            if (lastMessageExpirationMessageId != messageExpiration.messageId) {
                expirationTimestamp = messageExpiration.expirationTimestamp
                visibilityExpiration = messageExpiration.wipeOnly
                lastRemainingDisplayed = -1
                lastMessageExpirationMessageId = messageExpiration.messageId
                updateExpirationTimer(true)
            }
        } else {
            if (lastMessageExpirationMessageId != -1L) {
                expirationTimestamp = null
                visibilityExpiration = false
                lastMessageExpirationMessageId = -1
                updateExpirationTimer(true)
            }
        }
    }


    private var lastRemainingDisplayed: Long = -1

    private fun updateExpirationTimer(force: Boolean) {
        if (expirationTimestamp != null) {
            if (timerTextView.visibility != View.VISIBLE) {
                timerTextView.visibility = View.VISIBLE
            }
            var remaining = (expirationTimestamp!! - System.currentTimeMillis()) / 1000
            if (force) {
                lastRemainingDisplayed = -1
            }
            var color = 0
            try {
                if (remaining < 0) {
                    remaining = 0
                }
                if (remaining < 60) {
                    if (remaining == lastRemainingDisplayed) {
                        return
                    }
                    timerTextView.text = getString(R.string.text_timer_s, remaining)
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 60) {
                        color = ContextCompat.getColor(this, R.color.red)
                    }
                } else if (remaining < 3600) {
                    if (remaining / 60 == lastRemainingDisplayed / 60) {
                        return
                    }
                    timerTextView.text = getString(R.string.text_timer_m, remaining / 60)
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 3600) {
                        color = ContextCompat.getColor(this, R.color.orange)
                    }
                } else if (remaining < 86400) {
                    if (remaining / 3600 == lastRemainingDisplayed / 3600) {
                        return
                    }
                    timerTextView.text = getString(R.string.text_timer_h, remaining / 3600)
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 86400) {
                        color = ContextCompat.getColor(this, R.color.greyTint)
                    }
                } else if (remaining < 31536000) {
                    if (remaining / 86400 == lastRemainingDisplayed / 86400) {
                        return
                    }
                    timerTextView.text = getString(R.string.text_timer_d, remaining / 86400)
                    if (lastRemainingDisplayed < 0 || lastRemainingDisplayed >= 31536000) {
                        color = ContextCompat.getColor(this, R.color.greyOverlay)
                    }
                } else {
                    if (remaining / 31536000 == lastRemainingDisplayed / 31536000) {
                        return
                    }
                    timerTextView.text = getString(R.string.text_timer_y, remaining / 31536000)
                    if (lastRemainingDisplayed < 0) {
                        color = ContextCompat.getColor(this, R.color.greyOverlay)
                    }
                }
            } finally {
                lastRemainingDisplayed = remaining
                if (color != 0) {
                    if (readOnce) {
                        timerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            0,
                            R.drawable.ic_burn_small,
                            0,
                            0
                        )
                        timerTextView.setTextColor(ContextCompat.getColor(this, R.color.red))
                    } else {
                        timerTextView.setTextColor(color)
                        var drawable: Drawable?
                        if (visibilityExpiration) {
                            drawable = ContextCompat.getDrawable(this, R.drawable.ic_eye_small)
                        } else {
                            drawable = ContextCompat.getDrawable(this, R.drawable.ic_timer_small)
                        }
                        if (drawable != null) {
                            drawable = drawable.mutate()
                            drawable.colorFilter =
                                PorterDuffColorFilter(color, SRC_IN)
                            timerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                null,
                                drawable,
                                null,
                                null
                            )
                        }
                    }
                }
            }
        } else if (readOnce) {
            if (timerTextView.visibility != View.VISIBLE) {
                timerTextView.visibility = View.VISIBLE
            }
            timerTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0,
                R.drawable.ic_burn_small,
                0,
                0
            )
            timerTextView.text = null
        } else {
            if (timerTextView.visibility != View.GONE) {
                timerTextView.visibility = View.GONE
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        galleryAdapter.cleanup()
    }

    private fun bindFileInfo(fyleAndStatus: FyleAndStatus?) {
        if (fyleAndStatus == null) {
            fileNameTextView.text = null
            fileSizeTextView.text = null
            mimeTypeTextView.text = null
            resolutionTextView.text = null
            isShowingVideo = false
        } else {
            fileNameTextView.text = fyleAndStatus.fyleMessageJoinWithStatus.fileName
            fileSizeTextView.text =
                Formatter.formatShortFileSize(
                    this@GalleryActivity,
                    fyleAndStatus.fyleMessageJoinWithStatus.size
                )
            mimeTypeTextView.text = fyleAndStatus.fyleMessageJoinWithStatus.mimeType
            var imageResolution = fyleAndStatus.fyleMessageJoinWithStatus.imageResolution
            if (imageResolution != null) {
                if (imageResolution.startsWith("a") || imageResolution.startsWith("v")) {
                    imageResolution = imageResolution.substring(1)
                }
            }
            resolutionTextView.text = imageResolution
            bottomBar.invalidate()
            isShowingVideo =
                fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("video/")
            fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened()
        }
    }


    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (gestureDetector!!.onTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        invalidateOptionsMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CONTROLS_SHOWN_INSTANCE_STATE_EXTRA, controlsShown)
        outState.putBoolean(TEXT_BLOCKS_SHOWN_INSTANCE_STATE_EXTRA, showTextBlocks)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        when (currentMenuType) {
            STANDARD -> menuInflater.inflate(R.menu.menu_gallery, menu)
            INCOMPLETE_OR_DRAFT -> menuInflater.inflate(R.menu.menu_gallery_incomplete, menu)
            UPLOADING -> menuInflater.inflate(R.menu.menu_gallery_uploading, menu)
            DELETE_ONLY -> menuInflater.inflate(R.menu.menu_gallery_delete_only, menu)
            NONE -> {}
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
        } else if (item.itemId == R.id.action_open) {
            val fyleAndStatus = galleryAdapter.getItemAt(viewPager.currentItem)
            if (fyleAndStatus != null) {
                App.openFyleViewer(
                    this, fyleAndStatus
                ) { fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened() }
            }
        } else if (item.itemId == R.id.action_delete) {
            val fyleAndStatus = galleryAdapter.getItemAt(viewPager.currentItem)
            if (fyleAndStatus != null) {
                when (viewModel.galleryType) {
                    DRAFT -> {
                        App.runThread(DeleteAttachmentTask(fyleAndStatus))
                    }

                    MESSAGE, DISCUSSION, OWNED_IDENTITY -> {
                        val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_delete_attachment)
                            .setMessage(
                                getString(
                                    R.string.dialog_message_delete_attachment_gallery,
                                    fyleAndStatus.fyleMessageJoinWithStatus.fileName
                                )
                            )
                            .setPositiveButton(
                                R.string.button_label_ok
                            ) { dialog: DialogInterface?, which: Int ->
                                App.runThread(
                                    DeleteAttachmentTask(fyleAndStatus)
                                )
                            }
                            .setNegativeButton(R.string.button_label_cancel, null)
                        builder.create().show()
                    }

                    else -> {
                        val builder = SecureAlertDialogBuilder(this, R.style.CustomAlertDialog)
                            .setTitle(R.string.dialog_title_delete_attachment)
                            .setMessage(
                                getString(
                                    R.string.dialog_message_delete_attachment_gallery,
                                    fyleAndStatus.fyleMessageJoinWithStatus.fileName
                                )
                            )
                            .setPositiveButton(
                                R.string.button_label_ok
                            ) { dialog: DialogInterface?, which: Int ->
                                App.runThread(
                                    DeleteAttachmentTask(fyleAndStatus)
                                )
                            }
                            .setNegativeButton(R.string.button_label_cancel, null)
                        builder.create().show()
                    }
                }
            }
        } else if (item.itemId == R.id.action_save) {
            val fyleAndStatus = galleryAdapter.getItemAt(viewPager.currentItem)
            if (fyleAndStatus != null && fyleAndStatus.fyle.isComplete) {
                App.prepareForStartActivityForResult(this)
                saveAttachmentLauncher.launch(
                    Pair(
                        fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                        fyleAndStatus
                    )
                )
            }
        } else if (item.itemId == R.id.action_share) {
            val fyleAndStatus = galleryAdapter.getItemAt(viewPager.currentItem)
            if (fyleAndStatus != null) {
                val intent = Intent(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_STREAM, fyleAndStatus.contentUriForExternalSharing)
                intent.setType(fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType)
                startActivity(
                    Intent.createChooser(
                        intent,
                        getString(R.string.title_sharing_chooser)
                    )
                )
            }
        }
        return true
    }


    private fun saveCallback(result: Pair<Uri, FyleAndStatus>?) {
        if (result == null) {
            return
        }
        val uri = result.first
        val fyleAndStatus = result.second
        if (StringUtils.validateUri(uri) && fyleAndStatus != null) {
            App.runThread {
                try {
                    contentResolver.openOutputStream(uri).use { os ->
                        if (os == null) {
                            throw Exception("Unable to write to provided Uri")
                        }
                        FileInputStream(
                            App.absolutePathFromRelative(
                                fyleAndStatus.fyle.filePath
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

    override fun onResume() {
        super.onResume()
        if (screenShotBlockedForEphemeral) {
            val window = window
            window?.setFlags(
                LayoutParams.FLAG_SECURE,
                LayoutParams.FLAG_SECURE
            )
        }
        onPageChangeCallback?.let {
            viewPager.registerOnPageChangeCallback(it)
        }
        expirationTimer = Timer("DiscussionActivity-expirationTimers")
        expirationTimer!!.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread { updateExpirationTimer(false) }
            }
        }, 1000, 1000)
    }

    override fun onPause() {
        super.onPause()
        onPageChangeCallback?.let {
            viewPager.unregisterOnPageChangeCallback(it)
        }
        if (expirationTimer != null) {
            expirationTimer?.cancel()
            expirationTimer = null
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (controlsShown) {
                showControlsAndUi()
            } else {
                hideControlsAndUi()
            }
        }
    }


    private var controlsShown = false

    private fun hideControlsAndUi() {
        controlsShown = false
        galleryMotionLayout.transitionToStart()
        window?.decorView?.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // Hide the nav bar and status bar
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        window?.statusBarColor = ContextCompat.getColor(this, R.color.blackOverlay)
        window?.navigationBarColor = Color.TRANSPARENT
    }

    private fun showControlsAndUi() {
        controlsShown = true
        galleryMotionLayout.transitionToEnd()
        window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        window?.statusBarColor = ContextCompat.getColor(this, R.color.blackOverlay)
        window?.navigationBarColor = Color.TRANSPARENT
    }

    private fun toggleControlsAndUi() {
        if (controlsShown) {
            hideControlsAndUi()
        } else {
            showControlsAndUi()
        }
    }

    private fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val endConstraintSet = galleryMotionLayout.getConstraintSet(R.id.end)
        endConstraintSet.setMargin(R.id.top_bar, ConstraintSet.TOP, insets.systemWindowInsetTop)
        endConstraintSet.setMargin(
            R.id.bottom_bar,
            ConstraintSet.BOTTOM,
            insets.systemWindowInsetBottom
        )
        endConstraintSet.setMargin(
            R.id.top_bar,
            ConstraintSet.RIGHT,
            insets.systemWindowInsetRight
        )
        endConstraintSet.setMargin(
            R.id.bottom_bar,
            ConstraintSet.RIGHT,
            insets.systemWindowInsetRight
        )
        endConstraintSet.setMargin(
            R.id.top_bar,
            ConstraintSet.LEFT,
            insets.systemWindowInsetLeft
        )
        endConstraintSet.setMargin(
            R.id.bottom_bar,
            ConstraintSet.LEFT,
            insets.systemWindowInsetLeft
        )

        val startConstraintSet = galleryMotionLayout.getConstraintSet(R.id.start)
        startConstraintSet.setMargin(
            R.id.top_bar,
            ConstraintSet.RIGHT,
            insets.systemWindowInsetRight
        )
        startConstraintSet.setMargin(
            R.id.bottom_bar,
            ConstraintSet.RIGHT,
            insets.systemWindowInsetRight
        )
        startConstraintSet.setMargin(
            R.id.top_bar,
            ConstraintSet.LEFT,
            insets.systemWindowInsetLeft
        )
        startConstraintSet.setMargin(
            R.id.bottom_bar,
            ConstraintSet.LEFT,
            insets.systemWindowInsetLeft
        )

        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            val displayCutout = insets.displayCutout
            if (displayCutout != null) {
                if (displayCutout.safeInsetTop == 0) {
                    endConstraintSet.setMargin(
                        R.id.top_bar,
                        ConstraintSet.LEFT,
                        insets.systemWindowInsetLeft - displayCutout.safeInsetLeft
                    )
                    endConstraintSet.setMargin(
                        R.id.top_bar,
                        ConstraintSet.RIGHT,
                        insets.systemWindowInsetRight - displayCutout.safeInsetRight
                    )
                    startConstraintSet.setMargin(
                        R.id.top_bar,
                        ConstraintSet.LEFT,
                        insets.systemWindowInsetLeft - displayCutout.safeInsetLeft
                    )
                    startConstraintSet.setMargin(
                        R.id.top_bar,
                        ConstraintSet.RIGHT,
                        insets.systemWindowInsetRight - displayCutout.safeInsetRight
                    )
                }
                if (displayCutout.safeInsetBottom == 0) {
                    endConstraintSet.setMargin(
                        R.id.bottom_bar,
                        ConstraintSet.LEFT,
                        insets.systemWindowInsetLeft - displayCutout.safeInsetLeft
                    )
                    endConstraintSet.setMargin(
                        R.id.bottom_bar,
                        ConstraintSet.RIGHT,
                        insets.systemWindowInsetRight - displayCutout.safeInsetRight
                    )
                    startConstraintSet.setMargin(
                        R.id.bottom_bar,
                        ConstraintSet.LEFT,
                        insets.systemWindowInsetLeft - displayCutout.safeInsetLeft
                    )
                    startConstraintSet.setMargin(
                        R.id.bottom_bar,
                        ConstraintSet.RIGHT,
                        insets.systemWindowInsetRight - displayCutout.safeInsetRight
                    )
                }
            }
        }
        return insets
    }

    private class GetAttachmentSaveUri :
        ActivityResultContract<Pair<String, FyleAndStatus>, Pair<Uri, FyleAndStatus>?>() {
        private var fyleAndStatus: FyleAndStatus? = null

        override fun createIntent(context: Context, input: Pair<String, FyleAndStatus>): Intent {
            fyleAndStatus = input.second
            return Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_TITLE, input.first)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Pair<Uri, FyleAndStatus>? {
            if (intent != null && resultCode == RESULT_OK) {
                return Pair(intent.data, fyleAndStatus)
            }
            return null
        }
    }

    companion object {
        const val BYTES_OWNED_IDENTITY_INTENT_EXTRA: String = "bytes_owned_identity"
        const val BYTES_OWNED_IDENTITY_SORT_ORDER_INTENT_EXTRA: String = "sort_order"
        const val ASCENDING_INTENT_EXTRA: String = "ascending"
        const val DISCUSSION_ID_INTENT_EXTRA: String = "discussion_id"
        const val DRAFT_INTENT_EXTRA: String = "draft"
        const val INITIAL_MESSAGE_ID_INTENT_EXTRA: String = "initial_message_id"
        const val INITIAL_FYLE_ID_INTENT_EXTRA: String = "initial_fyle_id"
        const val SHOW_TEXT_BLOCKS_INTENT_EXTRA: String = "show_text_blocks"

        const val CONTROLS_SHOWN_INSTANCE_STATE_EXTRA: String = "controls_shown"
        const val TEXT_BLOCKS_SHOWN_INSTANCE_STATE_EXTRA: String = "text_blocks_shown"
    }
}
