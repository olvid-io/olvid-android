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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.FyleProgressSingleton
import io.olvid.messenger.ProgressStatus
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.AudioAttachmentServiceBinding
import io.olvid.messenger.customClasses.formatBytesSpeed
import io.olvid.messenger.customClasses.formatEtaSeconds
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndOrigin
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.databases.tasks.DeleteAttachmentFromAllMessagesTask
import io.olvid.messenger.databases.tasks.StartAttachmentDownloadTask
import io.olvid.messenger.databases.tasks.StopAttachmentDownloadTask
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidSortButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.designsystem.components.SortMenuItem
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.plus
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.gallery.AudioListItem
import io.olvid.messenger.discussion.gallery.FyleListItem
import io.olvid.messenger.discussion.message.attachments.AttachmentDownloadProgress
import io.olvid.messenger.discussion.message.attachments.getProgressLabel
import io.olvid.messenger.main.contacts.CustomTab
import io.olvid.messenger.onboarding.flow.animations.shimmer
import io.olvid.messenger.storage_manager.StorageManagerViewModel.BucketDestination
import io.olvid.messenger.storage_manager.StorageManagerViewModel.SortKey
import io.olvid.messenger.storage_manager.StorageManagerViewModel.SortOrder
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt
import java.time.format.TextStyle as MonthTextStyle

private enum class BucketTab { IMAGES, FILES, AUDIO, ALL }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StorageBucketScreen(
    viewModel: StorageManagerViewModel,
    destination: BucketDestination,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding,
    onGoToMessage: () -> Unit,
    onNavigateUp: () -> Unit,
    onSaveSelected: () -> Unit,
) {
    val sortOrder by viewModel.sortOrderMutableLiveData.observeAsState(
        SortOrder(
            SortKey.SIZE,
            false
        )
    )
    val selectedCount by viewModel.selectedCountLiveData.observeAsState(0)
    val selectedSize by viewModel.selectedSizeLiveData.observeAsState(0L)
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = (selectedCount ?: 0) > 0) {
        viewModel.clearSelectedFyles()
    }

    val sentByMeTitle = stringResource(R.string.label_storage_sent_by_me)
    val largeFilesTitle = stringResource(R.string.label_storage_large_files)
    val allFilesTitle = stringResource(R.string.label_storage_all_files)
    val discussionTitle by if (destination is BucketDestination.ByDiscussion) {
        AppDatabase.getInstance().discussionDao()
            .getByIdAsync(destination.discussionId)
    } else {
        MutableLiveData(null)
    }.observeAsState()
    val titleText = when (destination) {
        is BucketDestination.SentByMe -> sentByMeTitle
        is BucketDestination.LargeFiles -> largeFilesTitle
        is BucketDestination.ByDiscussion -> discussionTitle?.title ?: ""
        is BucketDestination.AllFiles -> allFilesTitle
    }

    val tabs = listOf(
        BucketTab.IMAGES to stringResource(R.string.label_medias),
        BucketTab.FILES to stringResource(R.string.label_documents),
        BucketTab.AUDIO to stringResource(R.string.label_audio),
        BucketTab.ALL to stringResource(R.string.label_all),
    )
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    val currentSortOrder = sortOrder ?: SortOrder(SortKey.SIZE, false)

    LaunchedEffect(destination) {
        viewModel.setBucketDestination(destination)
    }

    val mediaFyles by viewModel.bucketMediaFyles.observeAsState()
    val fileFyles by viewModel.bucketFileFyles.observeAsState()
    val audioFyles by viewModel.bucketAudioFyles.observeAsState()
    val allFyles by viewModel.bucketAllFyles.observeAsState()

    LaunchedEffect(destination, allFyles?.isNotEmpty()) {
        if (allFyles?.isNotEmpty() == true) {
            pagerState.scrollToPage(
                when {
                    mediaFyles?.isNotEmpty() == true -> 0
                    fileFyles?.isNotEmpty() == true -> 1
                    audioFyles?.isNotEmpty() == true -> 2
                    else -> 3
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OlvidTopAppBar(
            titleText = titleText,
            onBackPressed = {
                if ((selectedCount ?: 0) > 0) {
                    viewModel.clearSelectedFyles()
                } else {
                    onNavigateUp()
                }
            },
            elevationShadow = false,
            actions = {
                OlvidSortButton(
                    items = listOf(
                        SortKey.SIZE to (stringResource(R.string.menu_action_sort_size) to stringResource(
                            R.string.menu_action_sort_size_alt
                        )),
                        SortKey.DATE to (stringResource(R.string.menu_action_sort_date) to stringResource(
                            R.string.menu_action_sort_date_alt
                        )),
                        SortKey.NAME to (stringResource(R.string.menu_action_sort_name) to stringResource(
                            R.string.menu_action_sort_name_alt
                        )),
                    ).map { (key, labels) ->
                        val isActive = currentSortOrder.sortKey == key
                        SortMenuItem(
                            label = if (isActive && ((currentSortOrder.ascending) xor (key == SortKey.NAME))) labels.second else labels.first,
                            isActive = isActive,
                            onClick = {
                                viewModel.setSortOrder(
                                    if (isActive) SortOrder(key, !currentSortOrder.ascending)
                                    else SortOrder(key, key == SortKey.NAME) // by default, use descending sort order, except for alphabetical
                                )
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        )

        // Type tab row
        PrimaryTabRow(
            modifier = Modifier.cutoutHorizontalPadding().systemBarsHorizontalPadding(),
            selectedTabIndex = pagerState.currentPage,
            containerColor = colorResource(R.color.almostWhite),
            contentColor = colorResource(R.color.almostBlack),
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        selectedTabIndex = pagerState.currentPage,
                        matchContentSize = false
                    ),
                    color = colorResource(R.color.blueOrWhite)
                )
            },
            divider = {},
        ) {
            tabs.forEachIndexed { index, (_, label) ->
                CustomTab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(text = label) },
                    tabHeight = 36.dp
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
                beyondViewportPageCount = tabs.size - 1
            ) { page ->
                when (tabs[page].first) {
                    BucketTab.IMAGES -> MediaGridPage(
                        fyles = mediaFyles,
                        viewModel = viewModel,
                        destination = destination,
                        sortOrder = currentSortOrder,
                    )

                    BucketTab.FILES -> DocumentListPage(
                        fyles = fileFyles,
                        viewModel = viewModel,
                        sortOrder = currentSortOrder,
                    )

                    BucketTab.AUDIO -> AudioListPage(
                        fyles = audioFyles,
                        viewModel = viewModel,
                        audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                        sortOrder = currentSortOrder,
                    )

                    BucketTab.ALL -> DocumentListPage(
                        fyles = allFyles,
                        viewModel = viewModel,
                        sortOrder = currentSortOrder,
                    )
                }
            }


            // Selection bottom bar
            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier.align(Alignment.BottomStart),
                visible = (selectedCount ?: 0) > 0,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                val context = LocalContext.current
                val currentPageFyles = when (tabs[pagerState.currentPage].first) {
                    BucketTab.IMAGES -> mediaFyles
                    BucketTab.FILES -> fileFyles
                    BucketTab.AUDIO -> audioFyles
                    BucketTab.ALL -> allFyles
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorResource(R.color.almostWhite))
                        .cutoutHorizontalPadding()
                        .systemBarsHorizontalPadding()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.action_mode_title_storage,
                            selectedCount ?: 0,
                            selectedCount ?: 0
                        ) + " • " + Formatter.formatShortFileSize(context, selectedSize ?: 0L),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.almostBlack),
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedVisibility(
                        visible = selectedCount < 2
                    ) {
                        IconButton(onClick = onGoToMessage) {
                            Icon(
                                modifier = Modifier.size(32.dp),
                                painter = painterResource(R.drawable.ic_page_view),
                                contentDescription = stringResource(R.string.menu_action_go_to_message),
                                tint = colorResource(R.color.almostBlack)
                            )
                        }
                    }
                    IconButton(onClick = { currentPageFyles?.let { viewModel.selectAllFyles(it) } }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_select_all),
                            contentDescription = stringResource(R.string.menu_action_select_all),
                            tint = colorResource(R.color.almostBlack)
                        )
                    }
                    IconButton(onClick = { showSaveConfirmDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = stringResource(R.string.menu_action_save),
                            tint = colorResource(R.color.olvid_gradient_light)
                        )
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.menu_action_delete),
                            tint = colorResource(R.color.red)
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        val context = LocalContext.current
        val count = selectedCount ?: 0
        DialogSecure(onDismissRequest = { showDeleteConfirmDialog = false }) {
            BaseDialogContent(
                title = stringResource(R.string.dialog_title_confirm_deletion),
                content = {
                    Text(
                        text = pluralStringResource(
                            R.plurals.dialog_message_delete_attachments,
                            count,
                            count
                        ) + " (" + Formatter.formatShortFileSize(context, selectedSize ?: 0L) + ")",
                        style = OlvidTypography.body2,
                    )
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = pluralStringResource(
                            R.plurals.dialog_message_delete_attachments_mutli_explanation,
                            count,
                            count
                        ),
                        style = OlvidTypography.subtitle1.copy(fontStyle = FontStyle.Italic),
                        color = colorResource(R.color.greyTint),
                    )
                },
                actions = {
                    Spacer(modifier = Modifier.weight(1f))
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        onClick = { showDeleteConfirmDialog = false },
                        contentColor = colorResource(R.color.greyTint),
                    )
                    Spacer(Modifier.width(8.dp))
                    OlvidActionButton(
                        text = stringResource(R.string.button_label_delete),
                        containerColor = colorResource(R.color.red),
                        onClick = {
                            showDeleteConfirmDialog = false
                            App.runThread {
                                val fylesToDelete = ArrayList(viewModel.selectedFyles)
                                viewModel.clearSelectedFyles()
                                for (fyleAndStatus in fylesToDelete) {
                                    DeleteAttachmentFromAllMessagesTask(fyleAndStatus.fyle.id).run()
                                }
                            }
                        }
                    )
                }
            )
        }
    }

    // Save confirmation dialog
    if (showSaveConfirmDialog) {
        val count = selectedCount ?: 0
        DialogSecure(onDismissRequest = { showSaveConfirmDialog = false }) {
            BaseDialogContent(
                title = stringResource(R.string.dialog_title_save_selected_attachments),
                content = {
                    Text(
                        text = pluralStringResource(
                            R.plurals.dialog_message_save_selected_attachments,
                            count,
                            count
                        )
                    )
                },
                actions = {
                    Spacer(modifier = Modifier.weight(1f))
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        onClick = { showSaveConfirmDialog = false }
                    )
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_ok),
                        onClick = {
                            showSaveConfirmDialog = false
                            onSaveSelected()
                        }
                    )
                }
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridPage(
    fyles: List<FyleAndOrigin>?,
    viewModel: StorageManagerViewModel,
    destination: BucketDestination,
    sortOrder: SortOrder,
) {
    if (fyles == null) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().cutoutHorizontalPadding().systemBarsHorizontalPadding()) {
            val columnsCount = remember(maxWidth) {
                maxOf(
                    (maxWidth + 4.dp).value.roundToInt() / (128.dp + 4.dp).value.roundToInt(),
                    3
                )
            }
            val itemSize = remember(maxWidth, columnsCount) {
                ((maxWidth - (columnsCount * 2 + 8).dp).value.roundToInt() / columnsCount).dp
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                    .asPaddingValues() + PaddingValues(bottom = if (viewModel.isSelecting()) 64.dp else 0.dp)
            ) {
                items(6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(columnsCount) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .requiredSize(itemSize)
                                    .shimmer(true)
                            )
                        }
                    }
                }
            }
        }
        return
    }
    if (fyles.isEmpty()) {
        NoContentFound()
        return
    }
    val context = LocalContext.current
    val groupedByDate = remember(fyles, sortOrder.sortKey) {
        if (sortOrder.sortKey == SortKey.DATE) {
            fyles.groupBy { dateGroupKey(it.message?.timestamp ?: 0L) }
        } else null
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().cutoutHorizontalPadding().systemBarsHorizontalPadding()) {
        val columnsCount = remember(maxWidth) {
            maxOf(
                (maxWidth + 4.dp).value.roundToInt() / (128.dp + 4.dp).value.roundToInt(),
                3
            )
        }
        val itemSize = remember(maxWidth, columnsCount) {
            ((maxWidth - (columnsCount * 2 + 8).dp).value.roundToInt() / columnsCount).dp
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                .asPaddingValues() + PaddingValues(bottom = if (viewModel.isSelecting()) 64.dp else 0.dp)
        ) {
            if (groupedByDate != null) {
                for ((date, groupFyles) in groupedByDate) {
                    stickyHeader(key = date) {
                        BucketDateHeader(date)
                    }
                    groupFyles.chunked(columnsCount).forEach { rowItems ->
                        item {
                            MediaGridRow(rowItems, columnsCount, itemSize, sortOrder, viewModel, destination, context)
                        }
                    }
                }
            } else {
                fyles.chunked(columnsCount).forEach { rowItems ->
                    item {
                        MediaGridRow(rowItems, columnsCount, itemSize, sortOrder, viewModel, destination, context)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridRow(
    rowItems: List<FyleAndOrigin>,
    columnsCount: Int,
    itemSize: Dp,
    sortOrder: SortOrder,
    viewModel: StorageManagerViewModel,
    destination: BucketDestination,
    context: android.content.Context,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        rowItems.forEach { item ->
            val fyleAndStatus = item.fyleAndStatus
            val isSelected = viewModel.isSelected(fyleAndStatus)
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = {
                            if (viewModel.isSelecting()) {
                                viewModel.selectFyle(fyleAndStatus)
                            } else {
                                downloadAwareClick(fyleAndStatus) {
                                    val bytesOwnedIdentity = AppSingleton.getCurrentIdentityLiveData().value?.bytesOwnedIdentity
                                    val sortOrderString = when (sortOrder.sortKey) {
                                        SortKey.SIZE -> "size"
                                        SortKey.NAME -> "name"
                                        else -> null
                                    }
                                    val messageId = item.fyleAndStatus.fyleMessageJoinWithStatus.messageId
                                    val fyleId = item.fyleAndStatus.fyle.id
                                    when (destination) {
                                        is BucketDestination.AllFiles -> App.openOwnedIdentityGalleryActivity(context, bytesOwnedIdentity, sortOrderString, sortOrder.ascending, messageId, fyleId)
                                        is BucketDestination.SentByMe -> App.openSentByMeGalleryActivity(context, bytesOwnedIdentity, sortOrderString, sortOrder.ascending, messageId, fyleId)
                                        is BucketDestination.LargeFiles -> App.openLargeFilesGalleryActivity(context, bytesOwnedIdentity, destination.minSize, sortOrderString, sortOrder.ascending, messageId, fyleId)
                                        is BucketDestination.ByDiscussion -> App.openDiscussionGalleryActivityFromStorageManager(context, destination.discussionId, messageId, fyleId, sortOrderString, sortOrder.ascending)
                                    }
                                }
                            }
                        },
                        onLongClick = { viewModel.selectFyle(fyleAndStatus) }
                    )
            ) {
                var imageUri: ImageRequest? by remember {
                    mutableStateOf(null)
                }
                val cacheKey = "${fyleAndStatus.fyleMessageJoinWithStatus.fyleId}-${fyleAndStatus.fyleMessageJoinWithStatus.messageId}"

                LaunchedEffect(
                    fyleAndStatus.fyleMessageJoinWithStatus.miniPreview != null,
                    fyleAndStatus.fyle.filePath
                ) {
                    imageUri = if (fyleAndStatus.fyle.isComplete) {
                        ImageRequest.Builder(context)
                            .data(fyleAndStatus.deterministicContentUriForGallery)
                            .placeholderMemoryCacheKey(cacheKey)
                            .build()
                    } else if (fyleAndStatus.fyleMessageJoinWithStatus.miniPreview != null) {
                        ImageRequest.Builder(context)
                            .data(fyleAndStatus.fyleMessageJoinWithStatus.miniPreview)
                            .memoryCacheKey(cacheKey)
                            .build()
                    } else {
                        null
                    }
                }

                AsyncImage(
                    modifier = Modifier.requiredSize(itemSize),
                    model = imageUri,
                    imageLoader = App.imageLoader,
                    contentScale = ContentScale.Crop,
                    contentDescription = fyleAndStatus.fyleMessageJoinWithStatus.fileName
                )
                if (item.fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType.startsWith("video/")) {
                    Icon(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp),
                        painter = painterResource(id = R.drawable.attachment_video),
                        tint = Color.White,
                        contentDescription = "video"
                    )
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .requiredSize(itemSize)
                            .background(colorResource(R.color.blueOverlay))
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                            .background(
                                color = Color.Black.copy(alpha = .6f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(2.dp)
                            .size(16.dp)
                    )
                }
                AttachmentStatusBadge(fyleAndStatus = fyleAndStatus)
                TextChip(
                    modifier = Modifier.align(Alignment.TopEnd),
                    text = Formatter.formatShortFileSize(LocalContext.current, fyleAndStatus.fyleMessageJoinWithStatus.size)
                )
                if (sortOrder.sortKey == SortKey.NAME) {
                    TextChip(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        text = fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                    )
                } else {
                    getProgressLabel(fyleAndStatus.fyleMessageJoinWithStatus.status)?.let { label ->
                        TextChip(
                            modifier = Modifier.align(Alignment.BottomStart),
                            text = label,
                        )
                    }
                }
            }
        }
        repeat(columnsCount - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f, true))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentListPage(
    fyles: List<FyleAndOrigin>?,
    viewModel: StorageManagerViewModel,
    sortOrder: SortOrder,
) {
    if (fyles == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().cutoutHorizontalPadding().systemBarsHorizontalPadding(),
            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                .asPaddingValues() + PaddingValues(bottom = if (viewModel.isSelecting()) 64.dp else 0.dp)
        ) {
            items(8) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .shimmer(true)
                )
            }
        }
        return
    }
    if (fyles.isEmpty()) {
        NoContentFound()
        return
    }
    val context = LocalContext.current
    val groupedByDate = remember(fyles, sortOrder.sortKey) {
        if (sortOrder.sortKey == SortKey.DATE) {
            fyles.groupBy { dateGroupKey(it.message?.timestamp ?: 0L) }
        } else null
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().cutoutHorizontalPadding().systemBarsHorizontalPadding(),
        contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            .asPaddingValues() + PaddingValues(bottom = if (viewModel.isSelecting()) 64.dp else 0.dp)
    ) {
        if (groupedByDate != null) {
            for ((date, groupFyles) in groupedByDate) {
                stickyHeader(key = date) { BucketDateHeader(date) }
                items(groupFyles) { item ->
                    val fyleAndStatus = item.fyleAndStatus
                    val isSelected = viewModel.isSelected(fyleAndStatus)
                    Box {
                        FyleListItem(
                            modifier = Modifier.background(
                                if (isSelected) colorResource(R.color.blueOverlay) else Color.Transparent
                            ),
                            fyleAndStatus = fyleAndStatus,
                            fileName = AnnotatedString(fyleAndStatus.fyleMessageJoinWithStatus.fileName),
                            onClick = {
                                if (viewModel.isSelecting()) {
                                    viewModel.selectFyle(fyleAndStatus)
                                } else {
                                    downloadAwareClick(fyleAndStatus) {
                                        App.openFyleViewer(context, fyleAndStatus) {
                                            fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened()
                                        }
                                    }
                                }
                            },
                            onLongClick = { viewModel.selectFyle(fyleAndStatus) }
                        )
                        AttachmentStatusBadge(fyleAndStatus = fyleAndStatus, alignment = Alignment.TopEnd)
                        getProgressLabel(fyleAndStatus.fyleMessageJoinWithStatus.status)?.let { label ->
                            TextChip(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
                                text = label,
                            )
                        }
                    }
                }
            }
        } else {
            items(fyles) { item ->
                val fyleAndStatus = item.fyleAndStatus
                val isSelected = viewModel.isSelected(fyleAndStatus)
                Box {
                    FyleListItem(
                        modifier = Modifier.background(
                            if (isSelected) colorResource(R.color.blueOverlay) else Color.Transparent
                        ),
                        fyleAndStatus = fyleAndStatus,
                        fileName = AnnotatedString(fyleAndStatus.fyleMessageJoinWithStatus.fileName),
                        onClick = {
                            if (viewModel.isSelecting()) {
                                viewModel.selectFyle(fyleAndStatus)
                            } else {
                                downloadAwareClick(fyleAndStatus) {
                                    App.openFyleViewer(context, fyleAndStatus) {
                                        fyleAndStatus.fyleMessageJoinWithStatus.markAsOpened()
                                    }
                                }
                            }
                        },
                        onLongClick = { viewModel.selectFyle(fyleAndStatus) }
                    )
                    AttachmentStatusBadge(fyleAndStatus = fyleAndStatus, alignment = Alignment.TopEnd)
                    getProgressLabel(fyleAndStatus.fyleMessageJoinWithStatus.status)?.let { label ->
                        TextChip(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
                            text = label,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioListPage(
    fyles: List<FyleAndOrigin>?,
    viewModel: StorageManagerViewModel,
    audioAttachmentServiceBinding: AudioAttachmentServiceBinding,
    sortOrder: SortOrder,
) {
    if (fyles == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().cutoutHorizontalPadding().systemBarsHorizontalPadding(),
            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                .asPaddingValues() + PaddingValues(bottom = if (viewModel.isSelecting()) 64.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(6) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp)
                        .shimmer(true)
                )
            }
        }
        return
    }
    if (fyles.isEmpty()) {
        NoContentFound()
        return
    }
    val groupedByDate = remember(fyles, sortOrder.sortKey) {
        if (sortOrder.sortKey == SortKey.DATE) {
            fyles.groupBy { dateGroupKey(it.message?.timestamp ?: 0L) }
        } else null
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().cutoutHorizontalPadding().systemBarsHorizontalPadding(),
        contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            .asPaddingValues() + PaddingValues(bottom = if (viewModel.isSelecting()) 64.dp else 0.dp),
    ) {
        if (groupedByDate != null) {
            for ((date, groupFyles) in groupedByDate) {
                stickyHeader(key = date) { BucketDateHeader(date) }
                items(groupFyles) { item ->
                    val fyleAndStatus = item.fyleAndStatus
                    val isSelected = viewModel.isSelected(fyleAndStatus)
                    Box {
                        AudioListItem(
                            modifier = Modifier.background(
                                if (isSelected) colorResource(R.color.blueOverlay) else Color.Transparent
                            ).padding(vertical = 4.dp),
                            fyleAndStatus = fyleAndStatus,
                            activity = null,
                            audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                            discussionId = item.discussion?.id ?: -1L,
                            onClick = if (viewModel.isSelecting()) {{viewModel.selectFyle(fyleAndStatus)}} else null,
                            onLongClick = { viewModel.selectFyle(fyleAndStatus) },
                            onIncompleteClick = { App.runThread(StartAttachmentDownloadTask(fyleAndStatus.fyleMessageJoinWithStatus)) },
                        )
                        TextChip(
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp),
                            text = Formatter.formatShortFileSize(LocalContext.current, fyleAndStatus.fyleMessageJoinWithStatus.size)
                        )
                        AttachmentStatusBadge(fyleAndStatus = fyleAndStatus, alignment = Alignment.TopEnd)
                        getProgressLabel(fyleAndStatus.fyleMessageJoinWithStatus.status)?.let { label ->
                            TextChip(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
                                text = label,
                            )
                        }
                    }
                }
            }
        } else {
            items(fyles) { item ->
                val fyleAndStatus = item.fyleAndStatus
                val isSelected = viewModel.isSelected(fyleAndStatus)
                Box {
                    AudioListItem(
                        modifier = Modifier.background(
                            if (isSelected) colorResource(R.color.blueOverlay) else Color.Transparent
                        ).padding(vertical = 4.dp),
                        fyleAndStatus = fyleAndStatus,
                        activity = null,
                        audioAttachmentServiceBinding = audioAttachmentServiceBinding,
                        discussionId = item.discussion?.id ?: -1L,
                        onClick = if (viewModel.isSelecting()) {{viewModel.selectFyle(fyleAndStatus)}} else null,
                        onLongClick = { viewModel.selectFyle(fyleAndStatus) },
                        onIncompleteClick = { App.runThread(StartAttachmentDownloadTask(fyleAndStatus.fyleMessageJoinWithStatus)) },
                    )
                    TextChip(
                        modifier = Modifier.align(Alignment.TopStart),
                        text = Formatter.formatShortFileSize(LocalContext.current, fyleAndStatus.fyleMessageJoinWithStatus.size)
                    )
                    AttachmentStatusBadge(fyleAndStatus = fyleAndStatus, alignment = Alignment.TopEnd)
                    getProgressLabel(fyleAndStatus.fyleMessageJoinWithStatus.status)?.let { label ->
                        TextChip(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
                            text = label,
                        )
                    }
                }
            }
        }
    }
}


private fun dateGroupKey(timestamp: Long): String {
    val date = LocalDate.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
    val monthName = date.month.getDisplayName(MonthTextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return if (date.year == LocalDate.now().year) monthName else "$monthName ${date.year}"
}

@Composable
private fun BucketDateHeader(date: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.almostWhite))
            .padding(start = 12.dp, top = 12.dp, bottom = 4.dp, end = 12.dp),
        text = date,
        style = OlvidTypography.body1,
        fontWeight = FontWeight.Medium,
        color = colorResource(R.color.almostBlack),
    )
}

@Composable
private fun NoContentFound() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = stringResource(R.string.label_gallery_no_content)
        )
    }
}

private fun downloadAwareClick(
    fyleAndStatus: FyleAndStatus,
    completeClick: () -> Unit
) {
    when (fyleAndStatus.fyleMessageJoinWithStatus.status) {
        FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE ->
            App.runThread(StartAttachmentDownloadTask(fyleAndStatus.fyleMessageJoinWithStatus))
        FyleMessageJoinWithStatus.STATUS_DOWNLOADING ->
            App.runThread(StopAttachmentDownloadTask(fyleAndStatus.fyleMessageJoinWithStatus))
        FyleMessageJoinWithStatus.STATUS_DRAFT,
        FyleMessageJoinWithStatus.STATUS_UPLOADING,
        FyleMessageJoinWithStatus.STATUS_COMPLETE -> completeClick()
        // STATUS_FAILED, STATUS_UNTRANSFERRED, STATUS_COPYING: no-op
    }
}

@Composable
private fun BoxScope.AttachmentStatusBadge(
    fyleAndStatus: FyleAndStatus,
    alignment: Alignment = Alignment.BottomEnd,
) {
    val context = LocalContext.current
    val progressStatus by remember(fyleAndStatus.fyle.id, fyleAndStatus.fyleMessageJoinWithStatus.messageId) {
        FyleProgressSingleton.getProgress(fyleAndStatus.fyle.id, fyleAndStatus.fyleMessageJoinWithStatus.messageId)
    }.observeAsState()
    val progress by remember {
        derivedStateOf {
            when (progressStatus) {
                ProgressStatus.Finished -> 1f
                is ProgressStatus.InProgress -> (progressStatus as ProgressStatus.InProgress).progress
                else -> 0f
            }
        }
    }
    val speed = (progressStatus as? ProgressStatus.InProgress)?.speedAndEta?.speedBps?.formatBytesSpeed(context)
    val eta = (progressStatus as? ProgressStatus.InProgress)?.speedAndEta?.etaSeconds?.formatEtaSeconds(context)

    when (fyleAndStatus.fyleMessageJoinWithStatus.status) {
        FyleMessageJoinWithStatus.STATUS_DOWNLOADING,
        FyleMessageJoinWithStatus.STATUS_UPLOADING,
        FyleMessageJoinWithStatus.STATUS_COPYING -> AttachmentDownloadProgress(
            modifier = Modifier.align(alignment).padding(8.dp),
            speed = speed,
            eta = eta,
            progress = progress,
            large = false
        )
        FyleMessageJoinWithStatus.STATUS_DOWNLOADABLE -> Image(
            modifier = Modifier
                .align(alignment)
                .padding( 4.dp)
                .size(32.dp),
            painter = painterResource(R.drawable.ic_file_download),
            contentDescription = null,
        )
        FyleMessageJoinWithStatus.STATUS_FAILED -> Icon(
            modifier = Modifier
                .align(alignment)
                .padding(4.dp)
                .size(32.dp),
            painter = painterResource(R.drawable.ic_attachment_status_failed),
            contentDescription = null,
            tint = Color.Unspecified
        )
        FyleMessageJoinWithStatus.STATUS_UNTRANSFERRED -> Icon(
            modifier = Modifier
                .align(alignment)
                .padding(8.dp)
                .size(24.dp),
            painter = painterResource(R.drawable.ic_transfer),
            tint = colorResource(R.color.red),
            contentDescription = null
        )
        else -> Unit
    }
}

