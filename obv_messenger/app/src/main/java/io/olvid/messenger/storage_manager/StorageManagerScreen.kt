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

package io.olvid.messenger.storage_manager

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.olvid.engine.Logger
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.DiscussionAndUsage
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndOrigin
import io.olvid.messenger.designsystem.components.HiddenProfilePasswordDialog
import io.olvid.messenger.designsystem.components.OlvidActionRow
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidSortButton
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.designsystem.components.SortMenuItem
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.gallery.getDrawableResourceForMimeType
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.storage_manager.StorageManagerViewModel.BucketDestination
import io.olvid.messenger.storage_manager.StorageManagerViewModel.DiscussionSortKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun StorageManagerScreen(
    viewModel: StorageManagerViewModel,
    onBackPressed: () -> Unit,
) {
    val ownedIdentity by AppSingleton.getCurrentIdentityLiveData().observeAsState()
    val otherIdentities by viewModel.otherIdentities.observeAsState(emptyList())
    val otherIdentitySizes by viewModel.otherIdentitySizes.observeAsState(emptyMap())
    val currentIdentityTotalSize by viewModel.currentIdentityTotalSize.observeAsState(0L)
    val discussionsWithUsage by viewModel.discussionsWithUsage.observeAsState(emptyList())
    val discussionColorMap by viewModel.discussionColorMap.observeAsState(emptyMap())
    val sentByMePreview by viewModel.sentByMePreview.observeAsState(emptyList())
    val sentByMeCount by viewModel.sentByMeCount.observeAsState(0)
    val largeFilesPreview by viewModel.largeFilesPreview.observeAsState(emptyList())
    val largeFilesCount by viewModel.largeFilesCount.observeAsState(0)
    val allFilesCount by viewModel.allFilesCount.observeAsState(0)
    val discussionSortKey by viewModel.discussionSortKeyLiveData.observeAsState(DiscussionSortKey.SIZE)
    var showProfileMenu by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorResource(R.color.almostWhite),
        contentColor = colorResource(R.color.almostBlack),
        topBar = {
            OlvidTopAppBar(
                titleText = stringResource(R.string.button_label_manage_storage),
                onBackPressed = onBackPressed,
                actions = {
                    Box {
                        InitialView(
                            modifier = Modifier.requiredSize(40.dp),
                            initialViewSetup = { initialView ->
                                ownedIdentity?.let {
                                    initialView.setOwnedIdentity(it)
                                }
                            },
                            onClick = { showProfileMenu = true },
                            onLongClick = { showPasswordDialog = true },
                        )
                        OlvidDropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false }
                        ) {
                            // we use Unit here so that when switching profile,
                            // the list is not updated before the dropdown menu is closed and disappears
                            // next time the menu is opened, this will be recomputed
                            val sortedIdentities = remember(Unit) {
                                otherIdentities
                                    .map { identity ->
                                        identity to (otherIdentitySizes[Logger.toHexString(identity.bytesOwnedIdentity)] ?: 0L)
                                    }
                                    .sortedByDescending { (_, size) -> size }
                            }
                            sortedIdentities.forEach { (identity, size) ->
                                DropdownMenuItem(
                                    modifier = Modifier.width(240.dp),
                                    leadingIcon = {
                                        InitialView(
                                            modifier = Modifier.requiredSize(32.dp),
                                            initialViewSetup = { it.setOwnedIdentity(identity) }
                                        )
                                    },
                                    text = {
                                        Column {
                                            Text(
                                                text = identity.getCustomDisplayName(),
                                                style = OlvidTypography.body1,
                                                color = colorResource(R.color.almostBlack),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = Formatter.formatShortFileSize(context, size),
                                                style = OlvidTypography.subtitle1,
                                                color = colorResource(R.color.greyTint),
                                            )
                                        }
                                    },
                                    onClick = {
                                        showProfileMenu = false
                                        AppSingleton.getInstance()
                                            .selectIdentity(identity.bytesOwnedIdentity, null)
                                    }
                                )
                            }
                        }
                        if (showPasswordDialog) {
                            HiddenProfilePasswordDialog(
                                onDismiss = { showPasswordDialog = false },
                                onIdentitySelected = { bytesOwnedIdentity ->
                                    showPasswordDialog = false
                                    AppSingleton.getInstance()
                                        .selectIdentity(bytesOwnedIdentity, null)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
            )
        }) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(bottom = 16.dp)
        ) {
            item(key = -1) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(R.color.lighterGrey),
                            contentColor = colorResource(R.color.almostBlack)
                        )
                    ) {
                        DiscussionUsageBar(
                            discussions = discussionsWithUsage,
                            colorMap = discussionColorMap,
                            totalSize = currentIdentityTotalSize,
                        )
                    }

                    // files
                    if (allFilesCount > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colorResource(R.color.lighterGrey),
                                contentColor = colorResource(R.color.almostBlack)
                            )
                        ) {
                            if (sentByMeCount > 0) {
                                SmartBucketRow(
                                    title = stringResource(R.string.label_storage_sent_by_me),
                                    previews = sentByMePreview,
                                    count = sentByMeCount,
                                    onClick = {
                                        viewModel.bucketDestinationLiveData.value =
                                            BucketDestination.SentByMe
                                    }
                                )
                            }
                            // Large files bucket
                            if (largeFilesCount > 0) {
                                if (sentByMeCount > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                SmartBucketRow(
                                    title = stringResource(R.string.label_storage_large_files),
                                    previews = largeFilesPreview,
                                    count = largeFilesCount,
                                    onClick = {
                                        viewModel.bucketDestinationLiveData.value =
                                            BucketDestination.LargeFiles()
                                    }
                                )
                            }
                            // all files
                            OlvidActionRow(label = stringResource(R.string.label_storage_all_files) + " ($allFilesCount)") {
                                viewModel.bucketDestinationLiveData.value =
                                    BucketDestination.AllFiles
                            }
                        }
                    } else {
                        Text(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            text = stringResource(R.string.explanation_no_files),
                            textAlign = TextAlign.Center,
                            style = OlvidTypography.h2,
                            color = colorResource(R.color.almostBlack)
                        )
                    }

                    if (discussionsWithUsage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionHeader(
                                modifier = Modifier.weight(1f),
                                title = stringResource(R.string.label_storage_discussions)
                            )
                            val currentDiscussionSortKey =
                                discussionSortKey ?: DiscussionSortKey.SIZE
                            OlvidSortButton(
                                items = listOf(
                                    SortMenuItem(
                                        label = stringResource(R.string.menu_action_sort_size),
                                        isActive = currentDiscussionSortKey == DiscussionSortKey.SIZE,
                                        onClick = { viewModel.setDiscussionSortKey(DiscussionSortKey.SIZE) }
                                    ),
                                    SortMenuItem(
                                        label = stringResource(R.string.menu_action_sort_date),
                                        isActive = currentDiscussionSortKey == DiscussionSortKey.DATE,
                                        onClick = { viewModel.setDiscussionSortKey(DiscussionSortKey.DATE) }
                                    ),
                                )
                            )
                        }
                    }
                }
            }
            itemsIndexed(
                discussionsWithUsage,
                key = { _, item -> item.discussion.id }) { index, item ->
                val isFirst = index == 0
                val isLast = index == discussionsWithUsage.lastIndex
                val shape = when {
                    isFirst && isLast -> RoundedCornerShape(12.dp)
                    isFirst -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    isLast -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    else -> RectangleShape
                }
                DiscussionStorageItem(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = if (isFirst) 8.dp else 0.dp)
                        .clip(shape)
                        .background(colorResource(R.color.lighterGrey)),
                    item = item,
                    onClick = {
                        viewModel.bucketDestinationLiveData.value =
                            BucketDestination.ByDiscussion(item.discussion.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun DiscussionUsageBar(
    discussions: List<DiscussionAndUsage>,
    colorMap: Map<Long, Color>,
    totalSize: Long,
) {
    val context = LocalContext.current

    val discussionSum = discussions.sumOf { it.totalSize }
    val topDiscussions = discussions.take(colorMap.size)
    val otherSize = discussions.drop(colorMap.size).sumOf { it.totalSize }

    // One Animatable per bar segment + one for "other"
    val animatedSegmentCachedSizes : MutableMap<Long, Float> = remember { mutableMapOf() }
    val animatedSegments = remember(topDiscussions) { topDiscussions.map { Animatable(animatedSegmentCachedSizes[it.discussion.id] ?: 0f) } }
    val animatedOtherWeight = remember(topDiscussions) { Animatable(animatedSegmentCachedSizes[-1L] ?: 0f) }
    var chipsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(topDiscussions, discussionSum) {
        if (discussionSum == 0L) return@LaunchedEffect

        // Staggered animation for each segment
        animatedSegments.forEachIndexed { index, animatable ->
            launch {
                delay((index * 80L).milliseconds)
                val target = if (discussionSum > 0) topDiscussions[index].totalSize.toFloat() / discussionSum else 0f
                animatedSegmentCachedSizes[topDiscussions[index].discussion.id] = target
                animatable.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                )
            }
        }
        // "Other" segment starts after the last top segment
        launch {
            delay((topDiscussions.size * 80L).milliseconds)
            chipsVisible = true
            val target = if (discussionSum > 0 && otherSize > 0) otherSize.toFloat() / discussionSum else 0f
            animatedSegmentCachedSizes[-1] = target
            animatedOtherWeight.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = Formatter.formatShortFileSize(context, totalSize),
            style = OlvidTypography.h2,
            color = colorResource(R.color.almostBlack)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Segmented bar with animated weights
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colorResource(R.color.greyOverlay))
        ) {
            topDiscussions.forEachIndexed { index, item ->
                val color = colorMap[item.discussion.id] ?: return@forEachIndexed
                val animWeight = animatedSegments.getOrNull(index)?.value ?: 0f
                if (animWeight > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(animWeight)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
            val otherAnimWeight = animatedOtherWeight.value
            if (otherAnimWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(otherAnimWeight)
                        .fillMaxHeight()
                        .background(colorResource(R.color.greyTint))
                )
            }
            // Remaining space fills the background; always non-zero to satisfy Row weight constraints
            val usedWeight =
                animatedSegments.sumOf { it.value.toDouble() }.toFloat() + otherAnimWeight
            val remaining = (1f - usedWeight).coerceAtLeast(0.001f)
            Box(modifier = Modifier
                .weight(remaining)
                .fillMaxHeight())
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Legend chips with staggered fade-in
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(topDiscussions) { index, item ->
                val color = colorMap[item.discussion.id] ?: return@itemsIndexed
                val chipAlpha by animateFloatAsState(
                    targetValue = if (chipsVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 300, delayMillis = index * 60),
                    label = "chipAlpha"
                )
                Row(
                    modifier = Modifier.alpha(chipAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.discussion.title ?: "",
                        style = OlvidTypography.subtitle1,
                        color = colorResource(R.color.greyTint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp)
                    )
                }
            }
            if (otherSize > 0) {
                item {
                    val chipAlpha by animateFloatAsState(
                        targetValue = if (chipsVisible) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = topDiscussions.size * 60
                        ),
                        label = "chipAlphaOther"
                    )
                    Row(
                        modifier = Modifier.alpha(chipAlpha),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colorResource(R.color.greyTint), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.label_other),
                            style = OlvidTypography.subtitle1,
                            color = colorResource(R.color.greyTint),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    modifier: Modifier = Modifier,
    title: String,
) {
    Text(
        modifier = modifier.padding(start = 32.dp, end = 16.dp),
        text = title.uppercase(),
        style = OlvidTypography.body1,
        color = colorResource(R.color.greyTint),
    )
}

@Composable
private fun SmartBucketRow(
    title: String,
    previews: List<FyleAndOrigin>,
    count: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = OlvidTypography.body1,
                color = colorResource(R.color.almostBlack),
                modifier = Modifier.weight(1f)
            )
            /*
            Text(
                text = "$count",
                style = OlvidTypography.subtitle1,
                color = colorResource(R.color.greyTint),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colorResource(R.color.greyTint),
                modifier = Modifier.size(16.dp)
            )
             */
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columnsCount = remember(maxWidth) {
                maxOf(
                    (maxWidth + 4.dp).value.roundToInt() / (72.dp + 4.dp).value.roundToInt(),
                    3
                )
            }
            val itemSize = remember(maxWidth, columnsCount) {
                ((maxWidth - (columnsCount * 2 + 8).dp).value.roundToInt() / columnsCount).dp
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                previews.take(columnsCount).forEachIndexed { index, item ->
                    val mimeType = item.fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType
                    Box(modifier = Modifier.size(itemSize)) {
                        if (PreviewUtils.mimeTypeIsSupportedImageOrVideo(mimeType)) {
                            var imageUri: ImageRequest? by remember {
                                mutableStateOf(null)
                            }
                            val cacheKey = "${item.fyleAndStatus.fyleMessageJoinWithStatus.fyleId}-${item.fyleAndStatus.fyleMessageJoinWithStatus.messageId}"

                            LaunchedEffect(
                                item.fyleAndStatus.fyleMessageJoinWithStatus.miniPreview != null,
                                item.fyleAndStatus.fyle.filePath
                            ) {
                                imageUri = if (item.fyleAndStatus.fyle.isComplete) {
                                    ImageRequest.Builder(context)
                                        .data(item.fyleAndStatus.deterministicContentUriForGallery)
                                        .placeholderMemoryCacheKey(cacheKey)
                                        .build()
                                } else if (item.fyleAndStatus.fyleMessageJoinWithStatus.miniPreview != null) {
                                    ImageRequest.Builder(context)
                                        .data(item.fyleAndStatus.fyleMessageJoinWithStatus.miniPreview)
                                        .memoryCacheKey(cacheKey)
                                        .build()
                                } else {
                                    null
                                }
                            }

                            AsyncImage(
                                modifier = Modifier
                                    .size(itemSize)
                                    .clip(RoundedCornerShape(8.dp)),
                                model = imageUri,
                                imageLoader = App.imageLoader,
                                contentScale = ContentScale.Crop,
                                contentDescription = item.fyleAndStatus.fyleMessageJoinWithStatus.fileName
                            )
                            if (mimeType.startsWith("video/")) {
                                Icon(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp),
                                    painter = painterResource(id = R.drawable.attachment_video),
                                    tint = Color.White,
                                    contentDescription = null
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(itemSize)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorResource(R.color.greyOverlay)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    modifier = Modifier.fillMaxSize(0.5f),
                                    painter = painterResource(mimeType.getDrawableResourceForMimeType()),
                                    contentDescription = null
                                )
                            }
                            Text(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(2.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = .6f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 4.dp),
                                text = item.fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                                style = OlvidTypography.subtitle1,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextChip(
                            modifier = Modifier.align(Alignment.TopEnd),
                            text = Formatter.formatShortFileSize(
                                LocalContext.current,
                                item.fyleAndStatus.fyleMessageJoinWithStatus.size
                            )
                        )
                        if (count > columnsCount && index == columnsCount - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = .6f))
                            )
                            Text(
                                text = "+${count - columnsCount}",
                                style = OlvidTypography.body1,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionStorageItem(
    modifier: Modifier = Modifier,
    item: DiscussionAndUsage,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialView(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .requiredSize(40.dp),
            initialViewSetup = { initialView ->
                initialView.setDiscussion(item.discussion)
            }
        )
        Text(
            modifier = Modifier
                .weight(1f),
            text = item.discussion.title ?: "",
            color = colorResource(R.color.almostBlack),
            style = OlvidTypography.h3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = Formatter.formatShortFileSize(context, item.totalSize),
            color = colorResource(R.color.almostBlack),
            style = OlvidTypography.subtitle1,
            maxLines = 1,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colorResource(R.color.almostBlack),
            modifier = Modifier
                .padding(end = 16.dp, start = 4.dp)
                .size(24.dp)
        )
    }
}
