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
package io.olvid.messenger.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.databases.entity.TextBlock
import io.olvid.messenger.databases.entity.jsons.JsonExpiration
import io.olvid.messenger.databases.tasks.DeleteAttachmentTask
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.olvidSwitchDefaults
import io.olvid.messenger.gallery.GalleryViewModel.GalleryType.DRAFT
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private enum class GalleryMenuType {
    STANDARD,
    INCOMPLETE_OR_DRAFT,
    UPLOADING,
    DELETE_ONLY,
    NONE,
}

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    initialMessageId: Long,
    initialFyleId: Long,
    showTextBlocksInitially: Boolean,
    onBack: () -> Unit,
    onSave: (FyleAndStatus) -> Unit,
    onOpen: (FyleAndStatus) -> Unit,
    onFinish: () -> Unit,
    onFinishUp: () -> Unit,
    applyFlagSecure: () -> Unit,
    onGoToDiscussion: ((discussionId: Long, messageId: Long) -> Unit)? = null,
) {
    val fyleList by viewModel.imageAndVideoFyleAndStatusList.observeAsState()
    val currentFyle by viewModel.currentFyleAndStatus.observeAsState()
    val currentMessage by viewModel.currentAssociatedMessage.observeAsState()
    val currentExpiration by viewModel.currentAssociatedMessageExpiration.observeAsState()
    val textBlocks by viewModel.currentAssociatedTextBlocks.observeAsState()

    var controlsShown by rememberSaveable { mutableStateOf(showTextBlocksInitially) }
    var showTextBlocks by rememberSaveable { mutableStateOf(showTextBlocksInitially) }
    var isCurrentPageZoomed by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetFyle by remember { mutableStateOf<FyleAndStatus?>(null) }
    var screenShotBlocked by remember { mutableStateOf(false) }

    // OCR text block copy menu (anchored at the tapped block's bottom-right corner)
    var ocrMenuBlocks by remember { mutableStateOf<List<TextBlock>?>(null) }
    // Anchor in px (screen-space bounding box of the tapped block, already mapped by ZoomableImage)
    var ocrMenuAnchorPxX by remember { mutableFloatStateOf(0f) }
    var ocrMenuAnchorPxY by remember { mutableFloatStateOf(0f) }

    val pagerState = rememberPagerState { fyleList?.size ?: 0 }
    val context = LocalContext.current
    val resources = LocalResources.current
    val view = LocalView.current

    // Gate video playback until the initial scroll to the target page has completed,
    // preventing a video at page 0 from briefly playing when the gallery opens on an image.
    var initialScrollDone by remember { mutableStateOf(false) }

    // Sync pager position to ViewModel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentPagerPosition(pagerState.currentPage)
    }

    // Pause the media player whenever the current page is not a video.
    LaunchedEffect(pagerState.currentPage, fyleList, initialScrollDone) {
        if (!initialScrollDone) return@LaunchedEffect
        val isCurrentVideo = fyleList?.getOrNull(pagerState.currentPage)
            ?.fyleMessageJoinWithStatus?.nonNullMimeType?.startsWith("video/") == true
        if (!isCurrentVideo) {
            viewModel.mediaPlayer?.pause()
        }
    }

    // Mark current attachment as opened
    LaunchedEffect(currentFyle) {
        currentFyle?.fyleMessageJoinWithStatus?.markAsOpened()
    }

    // Jump to initial position once list arrives, then allow video playback
    LaunchedEffect(fyleList) {
        val list = fyleList ?: return@LaunchedEffect
        if (list.isEmpty()) {
            initialScrollDone = true
            return@LaunchedEffect
        }
        val target = list.indexOfFirst {
            it.fyleMessageJoinWithStatus.messageId == initialMessageId
                    && it.fyleMessageJoinWithStatus.fyleId == initialFyleId
        }
        if (target > 0) pagerState.scrollToPage(target)
        initialScrollDone = true
    }

    // System bars visibility
    LaunchedEffect(controlsShown) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            if (controlsShown) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // FLAG_SECURE for ephemeral messages
    LaunchedEffect(currentMessage) {
        val msg = currentMessage ?: return@LaunchedEffect
        if (!screenShotBlocked) {
            val shouldBlock = if (msg.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ) {
                true
            } else {
                runCatching {
                    val exp = AppSingleton.getJsonObjectMapper()
                        .readValue(msg.jsonExpiration, JsonExpiration::class.java)
                    exp.getVisibilityDuration() != null
                }.getOrDefault(false)
            }
            if (shouldBlock) {
                screenShotBlocked = true
                applyFlagSecure()
            }
        }
    }

    // Menu type derived from current message + fyle
    val menuType = remember(currentMessage, currentFyle) {
        val msg = currentMessage
        val fyle = currentFyle
        when {
            fyle == null || msg == null
                    || fyle.fyleMessageJoinWithStatus.messageId != msg.id -> GalleryMenuType.NONE
            msg.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ
                    || fyle.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_FAILED
                    || fyle.fyleMessageJoinWithStatus.status == FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED -> GalleryMenuType.DELETE_ONLY
            msg.status == Message.STATUS_DRAFT || !fyle.fyle.isComplete -> GalleryMenuType.INCOMPLETE_OR_DRAFT
            msg.status == Message.STATUS_UNPROCESSED
                    || msg.status == Message.STATUS_PROCESSING -> GalleryMenuType.UPLOADING
            else -> GalleryMenuType.STANDARD
        }
    }

    val readOnce = currentMessage?.wipeStatus == Message.WIPE_STATUS_WIPE_ON_READ

    // Expiration timer
    var expirationText by remember { mutableStateOf<String?>(null) }
    var expirationColor by remember { mutableStateOf(Color.White) }
    var expirationIsVisibility by remember { mutableStateOf(false) }

    LaunchedEffect(currentExpiration, readOnce) {
        val expiration = currentExpiration
        if (expiration != null) {
            while (true) {
                val remaining =
                    maxOf(0L, (expiration.expirationTimestamp - System.currentTimeMillis()) / 1000)
                expirationIsVisibility = expiration.wipeOnly
                expirationText = when {
                    remaining < 60 -> resources.getString(R.string.text_timer_s, remaining)
                    remaining < 3600 -> resources.getString(R.string.text_timer_m, remaining / 60)
                    remaining < 86400 -> resources.getString(R.string.text_timer_h, remaining / 3600)
                    remaining < 31536000 -> resources.getString(R.string.text_timer_d, remaining / 86400)
                    else -> resources.getString(R.string.text_timer_y, remaining / 31536000)
                }
                expirationColor = when {
                    readOnce -> Color.Red
                    remaining < 60 -> Color.Red
                    remaining < 3600 -> Color(0xFFFF9800)
                    remaining < 86400 -> Color.Gray
                    else -> Color.DarkGray
                }
                delay(500L.milliseconds)
            }
        } else if (readOnce) {
            expirationText = null
            expirationColor = Color.Red
        } else {
            expirationText = null
        }
    }

    // Main UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isCurrentPageZoomed) {
                if (isCurrentPageZoomed) return@pointerInput

                val velocityTracker = VelocityTracker()

                awaitEachGesture {
                    val downEvent = awaitFirstDown()
                    val pointerId = downEvent.id
                    val startPosition = downEvent.position

                    velocityTracker.resetTracking()
                    velocityTracker.addPosition(downEvent.uptimeMillis, startPosition)

                    var isCancelledByHorizontalDrag = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }

                        // Break if the finger was lifted or tracking was lost
                        if (change == null || !change.pressed) break

                        if (change.positionChanged()) {
                            val currentPosition = change.position

                            // Calculate total absolute displacement from the initial touch point
                            val totalDragX = abs(currentPosition.x - startPosition.x)
                            val totalDragY = abs(currentPosition.y - startPosition.y)

                            if (2 * totalDragX > totalDragY) {
                                isCancelledByHorizontalDrag = true
                                break
                            }

                            velocityTracker.addPosition(change.uptimeMillis, currentPosition)
                            change.consume()
                        }
                    }

                    // Only detect flings if there was no horizontal movement
                    if (!isCancelledByHorizontalDrag) {
                        val velocityY = velocityTracker.calculateVelocity().y
                        if (abs(velocityY) > 1000) {
                            if (velocityY < 0) {
                                onFinishUp.invoke()
                            } else {
                                onFinish.invoke()
                            }
                        }
                    }
                }
            }
    ) {
        val list = fyleList
        if (list != null && list.isEmpty()) {
            Text(
                text = stringResource(R.string.text_nothing_to_see_here),
                color = Color.White,
                fontStyle = FontStyle.Italic,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable { onFinish() }
            )
        } else {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !isCurrentPageZoomed,
                modifier = Modifier
                    .fillMaxSize()
            ) { page ->
                val fyleAndStatus = list?.getOrNull(page)
                if (fyleAndStatus != null) {
                    val isCurrentPage = page == pagerState.currentPage
                    val isVideo = fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("video/")
                    if (isVideo) {
                        GalleryVideoPlayer(
                            modifier = Modifier.fillMaxSize(),
                            mediaPlayer = viewModel.mediaPlayer,
                            fyleAndStatus = fyleAndStatus,
                            isCurrentPage = isCurrentPage && initialScrollDone,
                            initialScrollDone = initialScrollDone,
                            onFlingDown = onFinish,
                            onFlingUp = onFinishUp,
                            onDoubleTap = { controlsShown = !controlsShown }
                        )
                    } else {
                        ZoomableImage(
                            fyleAndStatus = fyleAndStatus,
                            linkPreviewData = viewModel.linkPreviewOpenGraph,
                            textBlocks = if (isCurrentPage && showTextBlocks) textBlocks else null,
                            initialScrollDone = initialScrollDone,
                            onSingleTap = { tappedBlocks ->
                                if (tappedBlocks.isNullOrEmpty()) {
                                    controlsShown = !controlsShown
                                } else if (tappedBlocks.size == 1) {
                                    // Single block: copy directly
                                    copyTextBlock(context, tappedBlocks.first().text)
                                } else {
                                    // Word + block: show dropdown menu
                                    val anchor = tappedBlocks
                                        .find { !it.isBlock } ?: tappedBlocks.first()
                                    ocrMenuAnchorPxX = anchor.boundingBox?.right?.toFloat() ?: 0f
                                    ocrMenuAnchorPxY = anchor.boundingBox?.bottom?.toFloat() ?: 0f
                                    ocrMenuBlocks = tappedBlocks
                                }
                            },
                            onZoomedChanged = { zoomed -> isCurrentPageZoomed = zoomed },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Top bar
            AnimatedVisibility(
                visible = controlsShown,
                enter = slideInVertically(tween(200, easing = LinearEasing)) { -it },
                exit = slideOutVertically(tween(200, easing = LinearEasing)) { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                GalleryTopBar(
                    menuType = menuType,
                    onBack = onBack,
                    onOpen = { currentFyle?.let { onOpen(it) } },
                    onSave = { currentFyle?.let { onSave(it) } },
                    onShare = {
                        currentFyle?.let { fyle ->
                            val shareIntent = Intent(Intent.ACTION_SEND)
                            shareIntent.putExtra(
                                Intent.EXTRA_STREAM,
                                fyle.contentUriForExternalSharing
                            )
                            shareIntent.type = fyle.fyleMessageJoinWithStatus.nonNullMimeType
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    resources.getString(R.string.title_sharing_chooser)
                                )
                            )
                        }
                    },
                    onDelete = {
                        currentFyle?.let { fyle ->
                            if (viewModel.galleryType == DRAFT) {
                                App.runThread(DeleteAttachmentTask(fyle))
                            } else {
                                deleteTargetFyle = fyle
                                showDeleteDialog = true
                            }
                        }
                    },
                    onGoToDiscussion = onGoToDiscussion?.let { callback ->
                        currentMessage?.let { msg -> { callback(msg.discussionId, msg.id) } }
                    }
                )
            }

            // Bottom bar
            AnimatedVisibility(
                visible = controlsShown,
                enter = slideInVertically(tween(200, easing = LinearEasing)) { it },
                exit = slideOutVertically(tween(200, easing = LinearEasing)) { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GalleryBottomBar(
                    fyleAndStatus = currentFyle,
                    showTextBlockToggle = currentFyle?.fyleMessageJoinWithStatus?.textExtracted == true
                            && !currentFyle?.fyleMessageJoinWithStatus?.textContent.isNullOrEmpty(),
                    showTextBlocks = showTextBlocks,
                    onToggleTextBlocks = { showTextBlocks = it }
                )
            }

            // Expiration badge (top-end corner)
            if (expirationText != null || readOnce) {
                GalleryExpirationBadge(
                    text = expirationText,
                    color = expirationColor,
                    isReadOnce = readOnce,
                    isVisibilityExpiration = expirationIsVisibility,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.End
                            )
                        )
                )
            }

            // OCR text block copy dropdown, anchored at the tapped block's bottom-right corner
            if (ocrMenuBlocks != null) {
                val density = LocalDensity.current
                // Convert the px anchor (screen-space from ZoomableImage) to Dp offset
                // relative to the top-left of this Box, which fills the screen.
                val anchorXDp = with(density) { ocrMenuAnchorPxX.toDp() }
                val anchorYDp = with(density) { ocrMenuAnchorPxY.toDp() }
                Box(modifier = Modifier.absoluteOffset(x = anchorXDp, y = anchorYDp)) {
                    OlvidDropdownMenu(
                        expanded = true,
                        onDismissRequest = { ocrMenuBlocks = null }
                    ) {
                        val wordBlock = ocrMenuBlocks?.find { !it.isBlock }
                        val paraBlock = ocrMenuBlocks?.find { it.isBlock }
                        if (wordBlock != null) {
                            OlvidDropdownMenuItem(
                                text = stringResource(R.string.menu_action_copy_text_from_image),
                                onClick = {
                                    copyTextBlock(context, wordBlock.text)
                                    ocrMenuBlocks = null
                                }
                            )
                        }
                        if (paraBlock != null) {
                            OlvidDropdownMenuItem(
                                text = stringResource(R.string.menu_action_copy_text_block_from_image),
                                onClick = {
                                    copyTextBlock(context, paraBlock.text)
                                    ocrMenuBlocks = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        val fyleToDelete = deleteTargetFyle
        if (fyleToDelete != null) {
            DialogSecure(onDismissRequest = { showDeleteDialog = false }) {
                BaseDialogContent(
                    title = stringResource(R.string.dialog_title_delete_attachment),
                    content = {
                        Text(
                            text = stringResource(
                                R.string.dialog_message_delete_attachment_gallery,
                                fyleToDelete.fyleMessageJoinWithStatus.fileName
                            ).formatMarkdownToAnnotatedString(),
                            style = OlvidTypography.body2
                        )
                    },
                    actions = {
                        Spacer(Modifier.weight(1f))
                        OlvidTextButton(
                            text = stringResource(R.string.button_label_cancel),
                            contentColor = colorResource(R.color.greyTint),
                            onClick = { showDeleteDialog = false }
                        )
                        Spacer(Modifier.width(8.dp))
                        OlvidActionButton(
                            text = stringResource(R.string.button_label_delete),
                            containerColor = colorResource(R.color.red),
                            onClick = {
                                App.runThread(DeleteAttachmentTask(fyleToDelete))
                                showDeleteDialog = false
                            }
                        )
                    }
                )
            }
        }
    }
}

private fun copyTextBlock(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
    clipboard?.setPrimaryClip(
        ClipData.newPlainText(
            context.getString(R.string.label_text_copied_from_olvid),
            text
        )
    )
    App.toast(R.string.toast_message_text_copied_to_clipboard, Toast.LENGTH_SHORT)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    menuType: GalleryMenuType,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onGoToDiscussion: (() -> Unit)? = null,
) {
    // Items shown as direct icon buttons in the toolbar (showAsAction="always")
    val toolbarItems: List<Pair<Int, () -> Unit>> = when (menuType) {
        GalleryMenuType.STANDARD,
        GalleryMenuType.UPLOADING -> listOf(
            R.drawable.ic_open to onOpen,
            R.drawable.ic_share to onShare,
            R.drawable.ic_delete to onDelete,
        )
        GalleryMenuType.INCOMPLETE_OR_DRAFT -> listOf(
            R.drawable.ic_delete to onDelete,
        )
        GalleryMenuType.DELETE_ONLY -> listOf(
            R.drawable.ic_delete to onDelete,
        )
        GalleryMenuType.NONE -> emptyList()
    }

    // Items shown in overflow dropdown (showAsAction="ifRoom")
    val overflowItems: List<Pair<Int, () -> Unit>> = when (menuType) {
        GalleryMenuType.STANDARD,
        GalleryMenuType.UPLOADING -> listOf(
            R.string.menu_action_save to onSave,
        )
        else -> emptyList()
    }

    var menuOpened by remember { mutableStateOf(false) }

    TopAppBar(
        title = {},
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.6f),
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        windowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_white),
                    contentDescription = stringResource(R.string.content_description_back_button)
                )
            }
        },
        actions = {
            if (onGoToDiscussion != null) {
                IconButton(onClick = onGoToDiscussion) {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(R.drawable.ic_page_view),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            toolbarItems.forEach { (iconRes, action) ->
                IconButton(onClick = action) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (iconRes == R.drawable.ic_delete)
                            colorResource(R.color.red) else Color.White
                    )
                }
            }
            if (overflowItems.isNotEmpty()) {
                IconButton(onClick = { menuOpened = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null
                    )
                    OlvidDropdownMenu(
                        expanded = menuOpened,
                        onDismissRequest = { menuOpened = false }
                    ) {
                        overflowItems.forEach { (labelRes, action) ->
                            OlvidDropdownMenuItem(
                                text = stringResource(labelRes),
                                onClick = {
                                    menuOpened = false
                                    action()
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun GalleryBottomBar(
    fyleAndStatus: FyleAndStatus?,
    showTextBlockToggle: Boolean,
    showTextBlocks: Boolean,
    onToggleTextBlocks: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AnimatedVisibility(
            visible = showTextBlockToggle,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Switch(
                    checked = showTextBlocks,
                    onCheckedChange = onToggleTextBlocks,
                    colors = olvidSwitchDefaults(),
                    thumbContent = {
                        Icon(
                            modifier = Modifier.padding(4.dp),
                            painter = painterResource(R.drawable.ic_message_status_draft),
                            contentDescription = null
                        )
                    }
                )
            }
        }

        if (fyleAndStatus != null) {
            val join = fyleAndStatus.fyleMessageJoinWithStatus
            var imageResolution = join.imageResolution
            if (imageResolution != null &&
                (imageResolution.startsWith("a") || imageResolution.startsWith("v"))
            ) {
                imageResolution = imageResolution.substring(1)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = join.fileName,
                        color = Color.White,
                        style = OlvidTypography.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = join.mimeType ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        style = OlvidTypography.subtitle1,
                        maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatter.formatShortFileSize(context, join.size),
                        color = Color.White,
                        style = OlvidTypography.body2
                    )
                    if (!imageResolution.isNullOrEmpty()) {
                        Text(
                            text = imageResolution,
                            color = Color.White.copy(alpha = 0.7f),
                            style = OlvidTypography.subtitle1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryExpirationBadge(
    text: String?,
    color: Color,
    isReadOnce: Boolean,
    isVisibilityExpiration: Boolean,
    modifier: Modifier = Modifier,
) {
    val iconRes = when {
        isReadOnce -> R.drawable.ic_burn_small
        isVisibilityExpiration -> R.drawable.ic_eye_small
        else -> R.drawable.ic_timer_small
    }
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            if (!text.isNullOrEmpty()) {
                Text(
                    text = text,
                    color = color,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
