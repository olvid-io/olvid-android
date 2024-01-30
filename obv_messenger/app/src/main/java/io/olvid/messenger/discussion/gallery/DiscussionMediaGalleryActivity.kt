/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

@file:OptIn(ExperimentalMaterial3Api::class)

package io.olvid.messenger.discussion.gallery

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding.AudioInfo
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding.AudioServiceBindableViewHolder
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding.timeFromMs
import io.olvid.messenger.customClasses.LockableActivity
import io.olvid.messenger.customClasses.MessageAttachmentAdapter
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatusTimestamped
import io.olvid.messenger.discussion.DiscussionActivity
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.gallery.GalleryActivity
import io.olvid.messenger.main.contacts.CustomTab
import io.olvid.messenger.services.MediaPlayerService.AudioOutput
import io.olvid.messenger.services.MediaPlayerService.AudioOutput.BLUETOOTH
import io.olvid.messenger.services.MediaPlayerService.AudioOutput.HEADSET
import io.olvid.messenger.services.MediaPlayerService.AudioOutput.LOUDSPEAKER
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


@OptIn(ExperimentalFoundationApi::class)
class DiscussionMediaGalleryActivity : LockableActivity() {

    @Composable
    fun Dp.toPx() = with(LocalDensity.current) { this@toPx.toPx().roundToInt() }
    @Composable
    fun Int.toDp() = with(LocalDensity.current) { this@toDp.toDp() }

    data class GalleryTab(val label: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )

        val viewModel by viewModels<MediaGalleryViewModel>()
        val linkPreviewViewModel by viewModels<LinkPreviewViewModel>()
        val audioAttachmentServiceBinding by lazy { AudioAttachmentServiceBinding(this) }
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
            }
            .build()

        val discussionId = intent.getLongExtra(GalleryActivity.DISCUSSION_ID_INTENT_EXTRA, -1)

        setContent {
            val tabs = listOf(
                GalleryTab(stringResource(id = R.string.label_medias)),
                GalleryTab(stringResource(id = R.string.label_documents)),
                GalleryTab(stringResource(id = R.string.label_links)),
                GalleryTab(stringResource(id = R.string.label_audio)),
            )
            val mediaItems by viewModel.getMedias(discussionId).observeAsState()
            val documentItems by viewModel.getDocuments(discussionId).observeAsState()
            val audioItems by viewModel.getAudios(discussionId).observeAsState()
            val linkItems by viewModel.getLinks(discussionId).observeAsState()

            AppCompatTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = Color.Black,
                    contentColor = Color.White
                ) {
                    Column {
                        val pagerState = rememberPagerState {
                            tabs.size
                        }

                        Header(pagerState, tabs)

                        HorizontalPager(
                            state = pagerState,
                            beyondBoundsPageCount = tabs.size - 1
                        ) { page ->
                            when (page) {
                                0 -> {
                                    MediaPage(mediaItems.orEmpty(), discussionId, imageLoader)
                                }

                                1 -> {
                                    DocumentPage(documentItems.orEmpty(), discussionId)
                                }

                                2 -> {
                                    LinkPage(linkItems.orEmpty(), discussionId, linkPreviewViewModel)
                                }

                                3 -> {
                                    AudioPage(
                                        audioItems.orEmpty(),
                                        audioAttachmentServiceBinding,
                                        discussionId
                                    )
                                }

                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(
        pagerState: PagerState,
        tabs: List<GalleryTab>
    ) {
        val coroutineScope = rememberCoroutineScope()

        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = { finish() }) {
                Icon(
                    painter = painterResource(id = drawable.ic_arrow_back),
                    contentDescription = stringResource(
                        id = string.content_description_back_button
                    )
                )
            }
            TabRow(
                backgroundColor = Color.Black,
                contentColor = Color.White,
                selectedTabIndex = pagerState.currentPage
            ) {
                tabs.forEachIndexed { index, galleryTab ->
                    CustomTab(selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(text = galleryTab.label)
                        },
                        horizontalTextPadding = 4.dp
                    )
                }
            }
        }
    }

    @Composable
    private fun AudioPage(
        audioItems: Map<String, List<FyleAndStatusTimestamped>>,
        audioAttachmentServiceBinding: AudioAttachmentServiceBinding,
        discussionId: Long
    ) {
        if (audioItems.isEmpty().not()) {
            val activity = LocalContext.current as? Activity
            LazyColumn(Modifier.fillMaxSize()) {
                audioItems.forEach { datedAudioItems ->
                    stickyHeader {
                        DateHeader(date = datedAudioItems.key)
                    }
                    items(datedAudioItems.value) { fyleAndStatusTimestamped ->
                        var playtime by remember {
                            mutableLongStateOf(0)
                        }
                        var duration by remember {
                            mutableLongStateOf(0)
                        }
                        var isPlaying by remember {
                            mutableStateOf(false)
                        }
                        var audioOutputResource by remember {
                            mutableIntStateOf(drawable.ic_speaker_light_grey)
                        }
                        var playbackSpeedState by remember {
                            mutableFloatStateOf(0f)
                        }
                        val audioServiceBindableViewHolder = remember {
                            object : AudioServiceBindable() {
                                override fun updatePlayTimeMs(
                                    audioInfo: AudioInfo?,
                                    playTimeMs: Long,
                                    playing: Boolean
                                ) {
                                    playtime = playTimeMs
                                    isPlaying = playing
                                }

                                override fun bindAudioInfo(
                                    audioInfo: AudioInfo?,
                                    audioOutput: AudioOutput?,
                                    playbackSpeed: Float
                                ) {
                                    if (audioInfo == null || audioInfo.failed) {
                                        duration = 0
                                        playtime = 0
                                    } else {
                                        duration = audioInfo.durationMs ?: 0
                                        playtime = audioInfo.seekTimeMs
                                    }
                                    isPlaying = false
                                    audioOutputResource = audioOutput.getResource()
                                    playbackSpeedState = playbackSpeed
                                }

                                override fun setFailed(failed: Boolean) {
                                    duration = 0
                                    playtime = 0
                                    isPlaying = false
                                }

                                override fun setAudioOutput(
                                    audioOutput: AudioOutput?,
                                    somethingPlaying: Boolean
                                ) {
                                    audioOutputResource = audioOutput.getResource()
                                    activity?.let {
                                        if ((somethingPlaying && (audioOutput == AudioOutput.PHONE)) != (activity.volumeControlStream == AudioManager.STREAM_VOICE_CALL)) {
                                            activity.volumeControlStream = if ((somethingPlaying && (audioOutput == AudioOutput.PHONE))) AudioManager.STREAM_VOICE_CALL else AudioManager.USE_DEFAULT_STREAM_TYPE
                                        }
                                    }
                                }

                                override fun setPlaybackSpeed(playbackSpeed: Float) {
                                    playbackSpeedState = playbackSpeed
                                }

                                override fun getFyleAndStatus(): FyleAndStatus? {
                                    return fns
                                }
                            }
                        }

                        var menuOpened by remember { mutableStateOf(false) }
                        var playbackSpeedMenuOpened by remember { mutableStateOf(false) }
                        val context = LocalContext.current

                        LaunchedEffect(fyleAndStatusTimestamped.fyleAndStatus.fyle.id) {
                            audioServiceBindableViewHolder.setFyleAndStatus(fyleAndStatusTimestamped.fyleAndStatus)
                            audioAttachmentServiceBinding.loadAudioAttachment(
                                fyleAndStatusTimestamped.fyleAndStatus,
                                audioServiceBindableViewHolder
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        audioAttachmentServiceBinding.playPause(
                                            fyleAndStatusTimestamped.fyleAndStatus,
                                            discussionId
                                        )
                                    },
                                    onLongClick = { menuOpened = true }
                                )
                        ) {
                            ContextMenu(
                                menuOpened = menuOpened,
                                onDismissRequest = { menuOpened = false },
                                onGoToMessage = {
                                    goToMessage(context = context, discussionId = discussionId, messageId = fyleAndStatusTimestamped.fyleAndStatus.fyleMessageJoinWithStatus.messageId)
                                },
                                onShare = {
                                    val intent = Intent(Intent.ACTION_SEND)
                                    intent.putExtra(Intent.EXTRA_STREAM, fyleAndStatusTimestamped.fyleAndStatus.contentUriForExternalSharing)
                                    intent.setType(fyleAndStatusTimestamped.fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType)
                                    startActivity(Intent.createChooser(intent, getString(string.title_sharing_chooser)))
                                })
                            Image(
                                modifier = Modifier
                                    .size(64.dp),
                                painter = painterResource(id = if (isPlaying) drawable.ic_pause else drawable.ic_play),
                                contentDescription = fyleAndStatusTimestamped.fyleAndStatus.fyleMessageJoinWithStatus.fileName
                            )
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row {
                                    Box (
                                        modifier = Modifier.weight(1f, true),
                                        contentAlignment = Alignment.TopEnd
                                    ) {
                                        Text(
                                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                                            text = fyleAndStatusTimestamped.fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (playbackSpeedState > 0.1f && isPlaying) {
                                            val scale = LocalConfiguration.current.fontScale
                                            Box (
                                                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                                            ){
                                                DropdownMenu(expanded = playbackSpeedMenuOpened, onDismissRequest = { playbackSpeedMenuOpened = false }) {
                                                    DropdownMenuItem(onClick = {
                                                        audioAttachmentServiceBinding.setPlaybackSpeed(1f)
                                                        playbackSpeedMenuOpened = false
                                                    }) {
                                                        Text(text = stringResource(id = R.string.menu_action_play_at_1x))
                                                    }
                                                    DropdownMenuItem(onClick = {
                                                        audioAttachmentServiceBinding.setPlaybackSpeed(1.5f)
                                                        playbackSpeedMenuOpened = false
                                                    }) {
                                                        Text(text = stringResource(id = R.string.menu_action_play_at_1_5x))
                                                    }
                                                    DropdownMenuItem(onClick = {
                                                        audioAttachmentServiceBinding.setPlaybackSpeed(2f)
                                                        playbackSpeedMenuOpened = false
                                                    }) {
                                                        Text(text = stringResource(id = R.string.menu_action_play_at_2x))
                                                    }
                                                }
                                                Text(
                                                    modifier = Modifier
                                                        .clickable {
                                                            playbackSpeedMenuOpened = true
                                                        }
                                                        .background(color = colorResource(id = R.color.lightGrey), shape = CircleShape)
                                                        .requiredWidth(36.dp)
                                                        .padding(vertical = 1.dp),
                                                    text = stringResource(id = when {
                                                        playbackSpeedState < 1.1 -> R.string.text_speed_1x
                                                        playbackSpeedState < 1.6 -> R.string.text_speed_1_5x
                                                        else -> R.string.text_speed_2x
                                                    }),
                                                    textAlign = TextAlign.Center,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorResource(id = R.color.grey),
                                                    fontSize = (12/scale).sp
                                                )
                                            }
                                        }
                                    }
                                    Image(
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = rememberRipple(bounded = false)
                                        ) {
                                            audioAttachmentServiceBinding.toggleSpeakerOutput()
                                        },
                                        painter = painterResource(id = audioOutputResource),
                                        contentDescription = ""
                                    )
                                }
                                Box (contentAlignment = Alignment.TopCenter) {
                                    Row {
                                        Text(
                                            text = timeFromMs(playtime),
                                            fontSize = 12.sp,
                                            color = if (isPlaying) colorResource(id = color.olvid_gradient_light) else colorResource(
                                                id = color.greyTint
                                            )
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = timeFromMs(duration),
                                            fontSize = 12.sp,
                                            color = colorResource(
                                                id = color.greyTint
                                            )
                                        )
                                    }
                                    androidx.compose.material.Slider(
                                        modifier = Modifier.padding(top = 8.dp),
                                        value = playtime / (duration.takeIf { it > 0 }
                                            ?: 1).toFloat(),
                                        onValueChange = { progress ->
                                            audioAttachmentServiceBinding.seekAudioAttachment(
                                                fyleAndStatusTimestamped.fyleAndStatus,
                                                (progress * 1000).roundToInt()
                                            )
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = colorResource(id = color.olvid_gradient_light),
                                            activeTrackColor = colorResource(id = color.olvid_gradient_light)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            NoContentFound()
        }
    }

    @Composable
    private fun LinkPage(
        linkItems: Map<String, List<FyleAndStatusTimestamped>>,
        discussionId: Long,
        linkPreviewViewModel: LinkPreviewViewModel
    ) {
        val cachedLinkedHeights: MutableMap<Long, Int> = remember {
            HashMap()
        }
        if (linkItems.isEmpty().not()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                linkItems.forEach { datedLinkItems ->
                    stickyHeader {
                        DateHeader(date = datedLinkItems.key)
                    }
                    items(datedLinkItems.value) {
                        var menuOpened by remember { mutableStateOf(false) }
                        val context = LocalContext.current
                        var opengraph by remember {
                            mutableStateOf<OpenGraph?>(null)
                        }
                        LaunchedEffect(it.fyleAndStatus.fyle.id) {
                            linkPreviewViewModel.linkPreviewLoader(
                                it.fyleAndStatus.fyle,
                                it.fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                                it.fyleAndStatus.fyleMessageJoinWithStatus.messageId
                            ) {
                                opengraph = it
                            }
                        }
                        opengraph?.let { link ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            App.openLink(
                                                this@DiscussionMediaGalleryActivity,
                                                link.getSafeUri()
                                            )
                                        },
                                        onLongClick = { menuOpened = true }
                                    )
                                    .onSizeChanged { size ->
                                        cachedLinkedHeights[it.fyleAndStatus.fyle.id] = size.height
                                    },
                                verticalAlignment = Alignment.Top
                            ) {
                                ContextMenu(
                                    menuOpened = menuOpened,
                                    onDismissRequest = { menuOpened = false },
                                    onGoToMessage = {
                                        goToMessage(context = context, discussionId = discussionId, messageId = it.fyleAndStatus.fyleMessageJoinWithStatus.messageId)
                                    },
                                    onShare = {
                                        link.getSafeUri()?.toString()?.let {
                                            val intent = Intent(Intent.ACTION_SEND)
                                            intent.putExtra(Intent.EXTRA_TEXT, it)
                                            intent.setType("text/plain")
                                            startActivity(Intent.createChooser(intent, getString(string.title_sharing_chooser)))
                                        }
                                    })
                                Image(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .padding(4.dp),
                                    painter = rememberAsyncImagePainter(
                                        model = link.bitmap
                                            ?: drawable.mime_type_icon_link
                                    ),
                                    contentScale = ContentScale.Crop,
                                    contentDescription = ""
                                )
                                Column(
                                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                ) {
                                    link.getSafeUri()?.let {
                                        Text(
                                            text = it.toString(),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    link.title?.let {
                                        Text(
                                            text = it,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorResource(
                                                id = color.greyTint
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    link.description?.let {
                                        Text(
                                            text = it,
                                            fontSize = 12.sp,
                                            color = colorResource(
                                                id = color.greyTint
                                            ),
                                            maxLines = if (link.shouldShowCompleteDescription()) Int.MAX_VALUE else 5,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } ?: Box(Modifier.height(cachedLinkedHeights[it.fyleAndStatus.fyle.id]?.toDp() ?: 64.dp))
                    }
                }
            }
        } else {
            NoContentFound()
        }
    }

    @Composable
    private fun DocumentPage(
        documentItems: Map<String, List<FyleAndStatusTimestamped>>,
        discussionId: Long
    ) {
        if (documentItems.isEmpty().not()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                documentItems.forEach {
                    stickyHeader {
                        DateHeader(date = it.key)
                    }
                    items(it.value) {
                        var menuOpened by remember { mutableStateOf(false) }
                        val context = LocalContext.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        App.openFyleInExternalViewer(
                                            this@DiscussionMediaGalleryActivity,
                                            it.fyleAndStatus,
                                            null
                                        )
                                    },
                                    onLongClick = { menuOpened = true }
                                )
                        ) {
                            ContextMenu(
                                menuOpened = menuOpened,
                                onDismissRequest = { menuOpened = false },
                                onGoToMessage = {
                                    goToMessage(context = context, discussionId = discussionId, messageId = it.fyleAndStatus.fyleMessageJoinWithStatus.messageId)
                                },
                                onShare = {
                                    val intent = Intent(Intent.ACTION_SEND)
                                    intent.putExtra(Intent.EXTRA_STREAM, it.fyleAndStatus.contentUriForExternalSharing)
                                    intent.setType(it.fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType)
                                    startActivity(Intent.createChooser(intent, getString(string.title_sharing_chooser)))
                                })
                            Image(
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(4.dp),
                                painter = rememberAsyncImagePainter(
                                    model = PreviewUtils.getBitmapPreview(
                                        it.fyleAndStatus.fyle,
                                        it.fyleAndStatus.fyleMessageJoinWithStatus,
                                        56.dp.toPx()
                                    )
                                        ?: MessageAttachmentAdapter.getDrawableResourceForMimeType(
                                            it.fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType
                                        )
                                ),
                                contentDescription = it.fyleAndStatus.fyleMessageJoinWithStatus.fileName
                            )
                            Column(modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                                Text(
                                    text = it.fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = it.fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType,
                                    fontSize = 12.sp,
                                    color = colorResource(
                                        id = color.greyTint
                                    )
                                )
                                Text(
                                    text = Formatter.formatShortFileSize(
                                        LocalContext.current,
                                        it.fyleAndStatus.fyleMessageJoinWithStatus.size
                                    ),
                                    fontSize = 12.sp,
                                    color = colorResource(
                                        id = color.greyTint
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else {
            NoContentFound()
        }
    }

    @Composable
    private fun MediaPage(
        mediaItems: Map<String, List<FyleAndStatusTimestamped>>,
        discussionId: Long,
        imageLoader: ImageLoader
    ) {
        if (mediaItems.isEmpty().not()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val sliderHeight = maxHeight
                val columnsCount = remember(maxWidth) {
                    maxOf(
                        (maxWidth + 4.dp).value.roundToInt() / (128.dp + 4.dp).value.roundToInt(),
                        1
                    )
                }
                val itemSize = remember(maxWidth, columnsCount) {
                    ((maxWidth + 4.dp).value.roundToInt() / columnsCount).dp
                }
                val dateHeadersIndexes = remember(columnsCount) {
                    val indexes = mutableListOf(0)
                    var index = 0
                    mediaItems.forEach {
                        index += it.value.chunked(columnsCount).count() + 1
                        indexes.add(index)
                    }
                    indexes
                }
                val listState = rememberLazyListState()

                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        mediaItems.forEach { (date, items) ->
                            stickyHeader {
                                DateHeader(date = date)
                            }
                            items.chunked(columnsCount).forEach { rowItems ->
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        rowItems.forEach { item ->
                                            Box(Modifier.weight(1f, fill = true)) {
                                                var menuOpened by remember { mutableStateOf(false) }
                                                val context = LocalContext.current
                                                ContextMenu(
                                                    menuOpened = menuOpened,
                                                    onDismissRequest = { menuOpened = false },
                                                    onGoToMessage = {
                                                        goToMessage(context = context, discussionId = discussionId, messageId = item.fyleAndStatus.fyleMessageJoinWithStatus.messageId)
                                                    },
                                                    onShare = {
                                                        val intent = Intent(Intent.ACTION_SEND)
                                                        intent.putExtra(Intent.EXTRA_STREAM, item.fyleAndStatus.contentUriForExternalSharing)
                                                        intent.setType(item.fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType)
                                                        startActivity(Intent.createChooser(intent, getString(string.title_sharing_chooser)))
                                                    })
                                                AsyncImage(
                                                    modifier = Modifier
                                                        .requiredHeight(itemSize)
                                                        .combinedClickable(
                                                            onClick = {
                                                                // TODO handle svg in DiscussionGalleryActivity
                                                                if (item.fyleAndStatus.fyleMessageJoinWithStatus.mimeType != "image/svg+xml") {
                                                                    App.openDiscussionGalleryActivity(
                                                                        this@DiscussionMediaGalleryActivity,
                                                                        discussionId,
                                                                        item.fyleAndStatus.fyleMessageJoinWithStatus.messageId,
                                                                        item.fyleAndStatus.fyle.id,
                                                                        false
                                                                    )
                                                                }
                                                            },
                                                            onLongClick = { menuOpened = true }
                                                        ),
                                                    model = item.fyleAndStatus.deterministicContentUriForGallery,
                                                    placeholder = ColorPainter(colorResource(id = R.color.grey)),
                                                    imageLoader = imageLoader,
                                                    contentScale = ContentScale.Crop,
                                                    contentDescription = item.fyleAndStatus.fyleMessageJoinWithStatus.fileName
                                                )
                                                if (item.fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("video/")) {
                                                    Icon(
                                                        modifier = Modifier
                                                            .align(Alignment.Center)
                                                            .size(itemSize / 3),
                                                        painter = painterResource(id = drawable.overlay_video_small),
                                                        contentDescription = "video"
                                                    )
                                                }
                                            }
                                        }
                                        repeat(columnsCount - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f, true))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val interactionSource = remember {
                        MutableInteractionSource()
                    }
                    FastScroll(sliderHeight, interactionSource, listState, mediaItems.keys.size, dateHeadersIndexes)

                }
            }
        } else {
            NoContentFound()
        }
    }

    @Composable
    private fun BoxScope.FastScroll(
        size: Dp,
        interactionSource: MutableInteractionSource,
        listState: LazyListState,
        steps: Int,
        indexes: List<Int>
    ) {
        val hapticFeedback = LocalHapticFeedback.current
        val index by remember { derivedStateOf { listState.firstVisibleItemIndex.toFloat() } }
        var lastIndex by remember {
            mutableIntStateOf(0)
        }
        val isDragged by interactionSource.collectIsDraggedAsState()
        val coroutineScope = rememberCoroutineScope()

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.CenterEnd),
            visible = steps > 1 && (listState.canScrollForward || listState.canScrollBackward) && (listState.isScrollInProgress || isDragged),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 500)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 500, delayMillis = 2000)
            )
        ) {
            Slider(modifier = Modifier
                .graphicsLayer {
                    rotationZ = 90f
                }
                .requiredWidth(64.dp)
                .requiredWidth(size)
                .offset(y = (-20).dp)
                .padding(horizontal = 64.dp),
                value = indexes.indexOfLast { it <= index }.toFloat(),
                onValueChange = { value: Float ->
                    val newIndex = value.roundToInt().coerceAtMost(indexes.lastIndex)
                    if (newIndex != lastIndex) {
                        coroutineScope.launch {
                            lastIndex = newIndex
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            listState.scrollToItem(indexes[newIndex])
                        }
                    }
                },
                steps = steps,
                valueRange = 0f..steps.toFloat(),
                interactionSource = interactionSource,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    activeTickColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .background(
                                color = colorResource(id = R.color.mediumGrey),
                                shape = CircleShape
                            )
                            .padding(top = 10.dp, start = 8.dp, end = 8.dp, bottom = 6.dp)
                    ) {
                        Image(
                            modifier = Modifier
                                .size(32.dp),
                            painter = painterResource(id = R.drawable.ic_fast_scroll),
                            contentDescription = "scroll"
                        )
                    }
                }
            )
        }
    }

    private fun AudioOutput?.getResource(): Int {
        return when (this) {
            HEADSET -> drawable.ic_headset_grey
            LOUDSPEAKER -> drawable.ic_speaker_blue
            BLUETOOTH -> drawable.ic_speaker_bluetooth_grey
            else -> drawable.ic_speaker_light_grey
        }
    }

    @Composable
    private fun NoContentFound() {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = stringResource(id = string.label_gallery_no_content)
            )
        }
    }

    @Composable
    private fun DateHeader(date: String, offset: Int = 0) {
        Text(
            modifier = Modifier
                .padding(top = 8.dp, start = 8.dp, bottom = 4.dp)
                .background(color = Color.Black.copy(alpha = .6f), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp)
                .offset { IntOffset(0, offset) },
            text = date,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    @Composable
    fun ContextMenu(
        menuOpened: Boolean,
        onDismissRequest: () -> Unit,
        onGoToMessage: () -> Unit,
        onShare: () -> Unit,
    ) {
        DropdownMenu(expanded = menuOpened, onDismissRequest = onDismissRequest) {
            // go to message
            DropdownMenuItem(onClick = {
                onGoToMessage()
                onDismissRequest()
            }) {
                Text(text = stringResource(id = R.string.menu_action_go_to_message))
            }
            // share
            DropdownMenuItem(onClick = {
                onShare()
                onDismissRequest()
            }) {
                Text(text = stringResource(id = R.string.menu_action_share))
            }
        }
    }

    private fun goToMessage(context: Context, discussionId: Long, messageId: Long) {
        val intent = Intent(context, DiscussionActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
        intent.putExtra(DiscussionActivity.MESSAGE_ID_INTENT_EXTRA, messageId);
        context.startActivity(intent)
    }

    private abstract class AudioServiceBindable : AudioServiceBindableViewHolder {
        var fns : FyleAndStatus? = null

        fun setFyleAndStatus(fyleAndStatus: FyleAndStatus) {
            fns = fyleAndStatus
        }
    }
}

