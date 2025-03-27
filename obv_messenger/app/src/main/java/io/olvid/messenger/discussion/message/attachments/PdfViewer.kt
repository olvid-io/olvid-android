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
import android.os.Build
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.designsystem.components.ExpandableSearchBar
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
    pdfFyleAndStatus: FyleAndStatus
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
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(RenderablePdfPageKeyer())
                add(RenderablePdfPageFetcher.Factory())
            }
            .build()
    }
    val scope = rememberCoroutineScope()

    val maxPageWidthPx =
        with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() } * maxZoom
    val maxPageHeightPx =
        with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() } * maxZoom
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    LaunchedEffect(pdfFyleAndStatus, maxPageWidthPx) {
        renderablePages =
            pdfFyleAndStatus.fyle.filePath?.let { pdfBitmapConverter.pdfToBitmaps(it, maxPageWidthPx, maxPageHeightPx) }
                .orEmpty()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    val lazyListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

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
        Box (
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState),
            contentAlignment = Alignment.Center
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredWidth(screenWidthDp * scale),
                contentPadding = WindowInsets.safeDrawing.asPaddingValues() // add an 8.dp content padding at the top and bottom
                    .let { object: PaddingValues {
                        override fun calculateBottomPadding(): Dp {
                            return it.calculateBottomPadding() + 8.dp
                        }

                        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
                            return it.calculateLeftPadding(layoutDirection)
                        }

                        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
                            return it.calculateRightPadding(layoutDirection)
                        }

                        override fun calculateTopPadding(): Dp {
                            return it.calculateTopPadding() + 8.dp
                        }

                    } },
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(renderablePages) { index, page ->
                    PdfPage(
                        page = page,
                        searchResults = searchResults.find { it.page == index },
                        imageLoader = imageLoader
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
            visible = lazyListState.isScrollInProgress.not(),
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
                visible = searchText.isNotEmpty() || lazyListState.isScrollInProgress.not(),
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
    }
}

@Composable
fun PdfPage(
    modifier: Modifier = Modifier,
    page: RenderablePdfPage,
    searchResults: SearchResults? = null,
    imageLoader: ImageLoader
) {
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
            .drawWithContent {
                drawContent()

                val scaleFactorX = size.width / page.width
                val scaleFactorY = size.height / page.height

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