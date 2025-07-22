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

package io.olvid.messenger.discussion.message.attachments

import android.graphics.RectF
import android.graphics.pdf.content.PdfPageGotoLinkContent
import android.os.Build
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.ImageLoader
import coil.compose.AsyncImage
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.designsystem.components.ExpandableSearchBar
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.plus
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SearchResults(
    val page: Int,
    val results: List<RectF>
)

@Composable
fun PdfViewerScreen(
    modifier: Modifier = Modifier,
    pdfFyleAndStatus: FyleAndStatus,
    onClose: () -> Unit,
) {
    val minZoom = .8f
    val maxZoom = 3f
    val context = LocalContext.current
    val pdfBitmapConverter = remember {
        PdfBitmapConverter()
    }
    var renderablePages by remember {
        mutableStateOf<List<RenderablePdfPage>>(emptyList())
    }
    var searchText by remember {
        mutableStateOf("")
    }
    var searchResults by remember {
        mutableStateOf(emptyList<SearchResults>())
    }
    var pdfNotSupported by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pdfPassword by remember { mutableStateOf<String?>(null) }
    var tempPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val maxPageWidthPx =
        with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() } * maxZoom
    val maxPageHeightPx =
        with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() } * maxZoom
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    LaunchedEffect(tempPassword) {
        passwordError = false
    }
    LaunchedEffect(pdfFyleAndStatus, maxPageWidthPx, pdfPassword) {
        try {
            renderablePages =
                pdfFyleAndStatus.fyle.filePath?.let {
                    pdfBitmapConverter.pdfToBitmaps(
                        it,
                        maxPageWidthPx,
                        maxPageHeightPx,
                        pdfPassword
                    )
                }.orEmpty()
            passwordError = false
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= 35) {
                if (pdfPassword.isNullOrEmpty().not()) {
                    pdfPassword = null
                    passwordError = true
                }
                showPasswordDialog = true
            } else {
                pdfNotSupported = true
            }
        } catch (_: Exception) {
            pdfNotSupported = true
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    val lazyListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    fun goto(destination: PdfPageGotoLinkContent.Destination) {
        if (Build.VERSION.SDK_INT >= 35) {
            scope.launch {
                lazyListState.animateScrollToItem(index = destination.pageNumber)
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                var initialSize: Float? = null
                var initialOffset: Offset = Offset.Zero
                var initialHorizontalOffset = 0
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
                                    initialOffset = event.calculateCentroid(true)
                                    initialHorizontalOffset = horizontalScrollState.value
                                    initialScale = scale
                                } else {
                                    val oldScale = scale
                                    val verticalOffset = lazyListState.firstVisibleItemScrollOffset
                                    scale =
                                        (initialScale * event.calculateCentroidSize(
                                            true
                                        ) / initSize).coerceIn(minZoom, maxZoom)

                                    scope.launch {
                                        horizontalScrollState.scrollTo(((initialOffset.x + initialHorizontalOffset) * scale / initialScale - initialOffset.x).roundToInt())
                                        lazyListState.scrollBy(((verticalOffset + initialOffset.y) * scale / oldScale - initialOffset.y - verticalOffset))
                                    }
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState),
            contentAlignment = Alignment.Center
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredWidth(screenWidthDp * scale),
                contentPadding = WindowInsets.safeDrawing.asPaddingValues() + PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 24.dp), // add more bottom padding to account for the shadow
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(renderablePages) { index, page ->
                    PdfPage(
                        page = page,
                        goto = ::goto,
                        searchResults = searchResults.find { it.page == index },
                        imageLoader = App.imageLoader
                    )
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            modifier = Modifier
                .statusBarsPadding()
                .displayCutoutPadding()
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 16.dp),
            visible = lazyListState.isScrollInProgress.not() && pdfNotSupported.not(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            FloatingActionButton(
                shape = CircleShape,
                containerColor = colorResource(R.color.olvid_gradient_dark),
                contentColor = Color.White,
                onClick = { App.openFyleInExternalViewer(context, pdfFyleAndStatus) {} }) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.ic_open_location_in_third_party_app_48),
                    contentDescription = null
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 35) {
            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(start = 20.dp, bottom = 8.dp)
                    .align(Alignment.BottomStart),
                visible = (searchText.isNotEmpty() || lazyListState.isScrollInProgress.not()) && pdfNotSupported.not(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                ExpandableSearchBar(
                    value = searchText,
                    onValueChange = { newSearchText ->
                        searchText = newSearchText
                        pdfBitmapConverter.renderer?.let { renderer ->
                            scope.launch(Dispatchers.Default) {
                                searchResults = (0 until renderer.pageCount).map { index ->
                                    renderer.openPage(index).use { page ->
                                        val results = page.searchText(newSearchText)

                                        val matchedRects = results.map {
                                            it.bounds.first()
                                        }
                                        SearchResults(
                                            page = index,
                                            results = matchedRects
                                        )
                                    }
                                }
                                searchResults.firstOrNull()?.page?.let {
                                    if (searchText.isEmpty().not()) {
                                        scope.launch { lazyListState.animateScrollToItem(it) }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
        if (pdfNotSupported) {
            AlertDialog(
                onDismissRequest = { },
                containerColor = colorResource(R.color.dialogBackground),
                title = {
                    Text(
                        stringResource(R.string.pdf_viewer_password_not_supported),
                        color = colorResource(R.color.almostBlack)
                    )
                },
                confirmButton = {
                    OlvidTextButton(
                        onClick = {
                            App.openFyleInExternalViewer(context, pdfFyleAndStatus) {
                                onClose.invoke()
                            }
                        },
                        text = stringResource(R.string.button_label_open_in_external_viewer),
                    )
                },
                dismissButton = {
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        onClick = onClose
                    )
                }
            )
        }
        if (showPasswordDialog) {
            val focusRequester = remember { FocusRequester() }
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                containerColor = colorResource(R.color.dialogBackground),
                title = {
                    Text(
                        stringResource(R.string.pdf_viewer_password_required),
                        color = colorResource(R.color.almostBlack)
                    )
                },
                text = {
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                    OutlinedTextField(
                        value = tempPassword,
                        onValueChange = { tempPassword = it },
                        label = { Text(stringResource(R.string.pdf_viewer_enter_password)) },
                        singleLine = true,
                        colors = olvidDefaultTextFieldColors(),
                        isError = passwordError,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            showKeyboardOnFocus = true,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                pdfPassword = tempPassword
                                showPasswordDialog = false
                            }
                        ),
                        supportingText = {
                            if (passwordError) {
                                Text(stringResource(R.string.pdf_viewer_incorrect_password))
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.focusRequester(focusRequester = focusRequester)
                    )
                },
                confirmButton = {
                    OlvidTextButton(
                        onClick = {
                            pdfPassword = tempPassword
                            showPasswordDialog = false
                        },
                        text = stringResource(R.string.button_label_ok),
                        enabled = tempPassword.isNotEmpty() && passwordError.not()
                    )
                },
                dismissButton = {
                    OlvidTextButton(
                        text = stringResource(R.string.button_label_cancel),
                        onClick = { showPasswordDialog = false }
                    )
                }
            )
        }
    }
}

@Composable
fun PdfPage(
    modifier: Modifier = Modifier,
    page: RenderablePdfPage,
    goto: (PdfPageGotoLinkContent.Destination) -> Unit,
    searchResults: SearchResults? = null,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val scaleFactorX by remember { derivedStateOf { canvasSize.width / page.originalWidth } }
    val scaleFactorY by remember { derivedStateOf { canvasSize.height / page.originalHeight } }

    AsyncImage(
        model = page,
        imageLoader = imageLoader,
        contentDescription = null,
        placeholder = ColorPainter(Color.White),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .aspectRatio(page.width.toFloat() / page.height.toFloat())
            .shadow(elevation = 12.dp)
            .onSizeChanged {
                canvasSize = it.toSize()
            }
            .pointerInput(Unit) {
                if (Build.VERSION.SDK_INT >= 35) {
                    detectTapGestures { offset ->
                        val scaledX = offset.x / scaleFactorX
                        val scaledY = offset.y / scaleFactorY

                        page.gotoLinks.firstOrNull { link ->
                            link.bounds.any { it.contains(scaledX, scaledY) }
                        }?.let {
                            goto(it.destination)
                            return@detectTapGestures
                        }

                        page.linkContents.firstOrNull { link ->
                            link.bounds.any { it.contains(scaledX, scaledY) }
                        }?.let {
                            App.openLink(
                                context,
                                it.uri
                            )
                        }
                    }
                }
            }
            .drawWithContent {
                drawContent()

                searchResults?.results?.forEach { rect ->
                    val adjustedRect = RectF(
                        rect.left * scaleFactorX,
                        rect.top * scaleFactorY,
                        rect.right * scaleFactorX,
                        rect.bottom * scaleFactorY
                    )

                    drawRoundRect(
                        color = Color.Yellow.copy(alpha = 0.5f),
                        topLeft = Offset(
                            x = adjustedRect.left,
                            y = adjustedRect.top
                        ),
                        size = Size(
                            width = adjustedRect.width(),
                            height = adjustedRect.height()
                        ),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
    )
}