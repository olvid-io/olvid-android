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

package io.olvid.messenger.discussion.scan

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val PROGRESS_SMOOTHING_MS = 150

// camera screen: always dark, like the camera app
private val CameraBackground = Color.Black
private val CameraOverlay = Color(0x80000000)

@Composable
internal fun ScanCameraScreen(
    viewModel: DocumentScannerViewModel,
    hasCameraPermission: Boolean,
    cameraPermissionDenied: Boolean,
    permissionBlocked: Boolean,
    automatic: Boolean,
    onRequestCameraPermission: () -> Unit,
    onAutomaticChange: (automatic: Boolean) -> Unit,
    onClose: () -> Unit,
    onCaptured: (index: Int) -> Unit,
    onShowPages: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val cameraState = remember { ScannerCameraState() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val blue = colorResource(R.color.olvid_gradient_light)

    LaunchedEffect(viewModel.addedPage) {
        viewModel.addedPage?.let {
            viewModel.addedPage = null
            onCaptured(it)
        }
    }

    val capture = {
        if (!viewModel.capturing) {
            viewModel.capturing = true
            cameraState.takePicture(
                executor = executor,
                onCaptured = { upright, outline, focalRatio ->
                    viewModel.addCapture(upright, outline, focalRatio)
                    mainExecutor.execute { viewModel.capturing = false }
                },
                onError = {
                    mainExecutor.execute {
                        viewModel.capturing = false
                        App.toast(R.string.toast_message_scan_failed, Toast.LENGTH_SHORT)
                    }
                },
            )
        }
    }
    // the stability moves by steps, one per analyzed frame
    val progress by animateFloatAsState(
        if (automatic) cameraState.stability else 0f,
        tween(PROGRESS_SMOOTHING_MS, easing = LinearEasing),
        label = "auto capture",
    )
    LaunchedEffect(automatic, cameraState.stability >= 1f) {
        if (automatic && cameraState.stability >= 1f) capture()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.importImages(uris)
    }

    Column(Modifier.fillMaxSize().background(CameraBackground).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.button_label_close), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (hasCameraPermission) IconButton(onClick = { cameraState.toggleTorch() }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                Icon(
                    painterResource(R.drawable.ic_pref_flash),
                    contentDescription = stringResource(R.string.button_label_scan_flash),
                    tint = if (cameraState.torch) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!hasCameraPermission) {
                if (cameraPermissionDenied) {
                    CameraPermissionPlaceholder(permissionBlocked, onRequestCameraPermission, Modifier.align(Alignment.Center))
                }
                return@Box
            }
            ScannerCameraPreview(cameraState, Modifier.fillMaxSize())
            val hint = if (automatic && cameraState.outline != null) R.string.label_scan_hint_hold_still else R.string.label_scan_hint
            Text(
                text = stringResource(hint),
                color = Color.White,
                style = OlvidTypography.body2,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(CameraOverlay, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(R.drawable.ic_gallery, R.string.button_label_import, size = 56) {
                activity?.let { App.prepareForStartActivityForResult(it) }
                importLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            ShutterButton(
                enabled = hasCameraPermission && !viewModel.capturing,
                progress = progress,
                progressColor = blue,
                onClick = capture,
            )
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                viewModel.pages.lastOrNull()?.let { last ->
                    Image(
                        (viewModel.rendered[last.id] ?: last.preview).asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(BorderStroke(2.dp, Color.White), RoundedCornerShape(8.dp))
                            .clickable(interactionSource = null, indication = ripple(color = Color.White), onClick = onShowPages),
                    )
                    PageBadge(viewModel.pages.size, blue, Modifier.align(Alignment.TopEnd).padding(2.dp))
                }
                if (viewModel.capturing) OlvidCircularProgress(color = Color.White, size = 32.dp)
            }
        }

        if (hasCameraPermission) Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(50))
                .padding(4.dp),
        ) {
            ModeChip(R.string.label_scan_mode_manual, selected = !automatic) { onAutomaticChange(false) }
            ModeChip(R.string.label_scan_mode_auto, selected = automatic) { onAutomaticChange( true) }
        }
    }
}

@Composable
private fun CameraPermissionPlaceholder(blocked: Boolean, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_attach_camera), contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(
            text = stringResource(R.string.label_scan_camera_permission),
            color = Color.White,
            style = OlvidTypography.body1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        OlvidActionButton(
            text = stringResource(if (blocked) R.string.button_label_open_settings else R.string.button_label_grant_access),
            shape = CircleShape,
            onClick = onRequest,
        )
    }
}

@Composable
private fun ModeChip(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = stringResource(label),
        color = if (selected) Color.Black else Color.White,
        style = OlvidTypography.body2,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(interactionSource = null, indication = ripple(color = if (selected) Color.Black else Color.White), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ShutterButton(enabled: Boolean, progress: Float, progressColor: Color, onClick: () -> Unit) {
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            drawCircle(Color.White, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(
                    progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
            }
        }
        Box(
            Modifier
                .size(64.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(enabled = enabled, interactionSource = null, indication = ripple(color = Color.Black), onClick = onClick),
        )
    }
}

@Composable
private fun RoundIconButton(@DrawableRes icon: Int, @StringRes description: Int, size: Int = 40, background: Color = CameraOverlay, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = null, indication = ripple(color = Color.White), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(description), tint = Color.White, modifier = Modifier.size((size * 0.45f).dp))
    }
}

@Composable
private fun PageBadge(number: Int, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = number.toString(),
        color = Color.White,
        style = OlvidTypography.subtitle1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 6.dp),
    )
}

@Composable
internal fun ScanReviewScreen(
    viewModel: DocumentScannerViewModel,
    currentPage: Int,
    onCurrentPageChange: (Int) -> Unit,
    onDiscard: () -> Unit,
    onAddPage: () -> Unit,
    onRetake: (index: Int) -> Unit,
    onCrop: (ScanPage) -> Unit,
    onDelete: (ScanPage) -> Unit,
    onSave: () -> Unit,
) {
    val pages = viewModel.pages
    val pagerState = rememberPagerState(initialPage = currentPage) { pages.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onCurrentPageChange(it) }
    }
    LaunchedEffect(currentPage, pages.size) {
        if (pagerState.currentPage != currentPage) pagerState.scrollToPage(currentPage)
    }
    var showFilters by remember { mutableStateOf(false) }
    var showFileNameDialog by rememberSaveable { mutableStateOf(false) }
    val index = pagerState.currentPage.coerceIn(0, pages.lastIndex)
    val page = pages.getOrNull(index) ?: return
    LaunchedEffect(showFilters, page) {
        if (showFilters) viewModel.requestFilterPreviews(page)
    }

    val blue = colorResource(R.color.olvid_gradient_light)
    val background = colorResource(R.color.almostWhite)
    val surface = colorResource(R.color.lightGrey)
    val content = colorResource(R.color.almostBlack)
    val thumbnails = rememberLazyListState()
    LaunchedEffect(index) { thumbnails.animateScrollToItem(index) }

    Column(Modifier.fillMaxSize().background(background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDiscard, colors = IconButtonDefaults.iconButtonColors(contentColor = content)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.button_label_close), tint = content)
            }
            Text(
                text = stringResource(R.string.label_scan_page_count, index + 1, pages.size),
                color = content,
                style = OlvidTypography.h3,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showFileNameDialog = true }, colors = IconButtonDefaults.iconButtonColors(contentColor = content)) {
                Icon(painterResource(R.drawable.ic_pencil), contentDescription = stringResource(R.string.button_label_scan_file_name), tint = content)
            }
        }

        if (showFileNameDialog) {
            ScanFileNameDialog(
                initial = viewModel.fileName,
                onDismiss = { showFileNameDialog = false },
                onConfirm = {
                    viewModel.fileName = it
                    showFileNameDialog = false
                },
            )
        }

        HorizontalPager(
            pagerState,
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 12.dp,
            key = { pages[it].id },
        ) { position ->
            val item = pages[position]
            Box(
                Modifier.fillMaxSize().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(surface),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = viewModel.rendered[item.id]
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(16.dp).aspectRatio(bitmap.width / bitmap.height.toFloat()),
                    )
                } else {
                    OlvidCircularProgress(color = blue, size = 48.dp)
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIconButton(R.drawable.ic_attach_camera, R.string.button_label_scan_retake) { onRetake(position) }
                    RoundIconButton(R.drawable.ic_delete_outline, R.string.button_label_delete) { onDelete(item) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolChip(R.drawable.ic_scan_filter, R.string.button_label_scan_enhance, selected = page.filter == ScanFilter.ENHANCED) {
                viewModel.update(page.copy(filter = if (page.filter == ScanFilter.ENHANCED) ScanFilter.ORIGINAL else ScanFilter.ENHANCED))
            }
            ToolChip(R.drawable.ic_contrast, R.string.button_label_scan_filters, selected = showFilters) { showFilters = !showFilters }
            ToolChip(R.drawable.ic_scan_crop, R.string.button_label_scan_crop_rotate, selected = false) { onCrop(page) }
        }

        AnimatedVisibility(visible = showFilters) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScanFilter.entries.forEach { filter ->
                    val selected = filter == page.filter
                    Column(
                        Modifier.width(92.dp).clip(RoundedCornerShape(8.dp)).clickable { viewModel.update(page.copy(filter = filter)) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(width = 64.dp, height = 84.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(surface)
                                .border(BorderStroke(if (selected) 3.dp else 0.dp, if (selected) blue else Color.Transparent), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            viewModel.filterPreviews[page.id to filter]?.let {
                                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } ?: OlvidCircularProgress(color = blue, size = 24.dp)
                        }
                        Text(
                            text = stringResource(filter.label),
                            color = if (selected) blue else content,
                            style = OlvidTypography.subtitle1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), state = thumbnails, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(pages, key = { _, item -> item.id }) { position, item ->
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 58.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(surface)
                            .border(BorderStroke(2.dp, if (position == index) blue else Color.Transparent), RoundedCornerShape(6.dp))
                            .clickable { scope.launch { pagerState.animateScrollToPage(position) } },
                    ) {
                        (viewModel.rendered[item.id] ?: item.preview).let {
                            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        PageBadge(position + 1, if (position == index) blue else colorResource(R.color.greyTint), Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
                    }
                }
            }
            RoundIconButton(R.drawable.ic_add_white, R.string.button_label_scan_add_page, size = 48, background = blue, onClick = onAddPage)
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OlvidActionButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.button_label_scan_discard),
                containerColor = Color.Transparent,
                contentColor = blue,
                outlinedColor = blue,
                large = true,
                shape = CircleShape,
                onClick = onDiscard,
            )
            OlvidActionButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.button_label_save),
                enabled = !viewModel.exporting,
                large = true,
                shape = CircleShape,
                onClick = onSave,
            )
        }
    }

    if (viewModel.exporting) {
        Surface(Modifier.fillMaxSize(), color = Color(0x99000000)) {
            Box(contentAlignment = Alignment.Center) { OlvidCircularProgress(color = Color.White, size = 56.dp) }
        }
    }
}

@Composable
private fun ToolChip(@DrawableRes icon: Int, @StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    val blue = colorResource(R.color.olvid_gradient_light)
    val content = if (selected) Color.White else colorResource(R.color.almostBlack)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) blue else colorResource(R.color.lightGrey))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(text = stringResource(label), color = content, style = OlvidTypography.body2, maxLines = 1)
    }
}

internal val ScanFilter.label: Int
    get() = when (this) {
        ScanFilter.ORIGINAL -> R.string.label_scan_filter_original
        ScanFilter.ENHANCED -> R.string.label_scan_filter_enhanced
        ScanFilter.GRAYSCALE -> R.string.label_scan_filter_grayscale
        ScanFilter.BLACK_AND_WHITE -> R.string.label_scan_filter_black_and_white
    }

@Composable
private fun ScanFileNameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val confirm = { if (name.text.isNotBlank()) onConfirm(name.text.trim()) }

    DialogSecure(onDismissRequest = onDismiss) {
        BaseDialogContent(
            title = stringResource(R.string.dialog_title_scan_file_name),
            content = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = name,
                    onValueChange = { name = it },
                    shape = RoundedCornerShape(12.dp),
                    colors = olvidDefaultTextFieldColors(),
                    singleLine = true,
                    suffix = { Text(".pdf") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    label = {
                        Text(
                            text = stringResource(R.string.button_label_scan_file_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            },
            actions = {
                Spacer(Modifier.weight(1f, true))
                OlvidTextButton(text = stringResource(R.string.button_label_cancel), onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                OlvidActionButton(text = stringResource(R.string.button_label_ok), enabled = name.text.isNotBlank(), onClick = confirm)
            },
        )
    }
}
