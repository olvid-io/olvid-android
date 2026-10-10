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

package io.olvid.messenger.onboarding.flow.screens.ftux

import android.text.Spanned
import android.text.style.StyleSpan
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.icons.OlvidLogo
import io.olvid.messenger.designsystem.icons.OlvidLogoSize
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.onboarding.flow.OnboardingRoutes
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight

/**
 * Sealed page definition. Each variant carries exactly the data its layout needs,
 * lets `when` dispatch in [FtuxPage] stay exhaustive, and keeps per-page composables
 * (Welcome / Standard / Media) independently previewable.
 */
private sealed interface FtuxPageDef {
    @get:StringRes
    val titleRes: Int
    @get:StringRes
    val bodyRes: Int

    /** First page — centred Olvid brand mark (rendered via [OlvidLogo]), no device mockup. */
    data class Welcome(
        @field:StringRes override val titleRes: Int,
        @field:StringRes override val bodyRes: Int,
    ) : FtuxPageDef

    /**
     * Middle pages — title + body + single device-mockup VectorDrawable.
     * [alignment] reflects how the mockup sits inside its Figma frame (some sit
     * bottom-end with the phone extending off the right edge, some are
     * bottom-centred, some sit higher up — see screens 3-6 of the FTUX file).
     */
    data class Standard(
        @field:StringRes override val titleRes: Int,
        @field:StringRes override val bodyRes: Int,
        @field:DrawableRes val mockupRes: Int,
        val alignment: Alignment = Alignment.BottomCenter,
    ) : FtuxPageDef

    /** Last page — single pre-rendered illustration covering the device chrome,
     *  rasters and badges in one asset (no per-element overlay). */
    data class Media(
        @field:StringRes override val titleRes: Int,
        @field:StringRes override val bodyRes: Int,
        @field:DrawableRes val illustrationRes: Int,
        val alignment: Alignment = Alignment.Center,
    ) : FtuxPageDef
}

private val ftuxPages: List<FtuxPageDef> = listOf(
    FtuxPageDef.Welcome(
        titleRes = R.string.ftux_page_welcome_title,
        bodyRes = R.string.ftux_page_welcome_body,
    ),
    FtuxPageDef.Standard(
        titleRes = R.string.ftux_page_private_title,
        bodyRes = R.string.ftux_page_private_body,
        mockupRes = R.drawable.ftux_mockup_contacts,
        alignment = Alignment.BottomEnd,
    ),
    FtuxPageDef.Standard(
        titleRes = R.string.ftux_page_everyone_title,
        bodyRes = R.string.ftux_page_everyone_body,
        mockupRes = R.drawable.ftux_mockup_lock,
        alignment = Alignment.BottomCenter,
    ),
    FtuxPageDef.Standard(
        titleRes = R.string.ftux_page_protect_title,
        bodyRes = R.string.ftux_page_protect_body,
        mockupRes = R.drawable.ftux_image_protect,
        // raster with a checkmark that must not be mirrored: stay pinned to the right edge in RTL
        alignment = AbsoluteAlignment.BottomRight,
    ),
    FtuxPageDef.Standard(
        titleRes = R.string.ftux_page_pricing_title,
        bodyRes = R.string.ftux_page_pricing_body,
        mockupRes = R.drawable.ftux_mockup_pricing,
        alignment = Alignment.BottomCenter,
    ),
    FtuxPageDef.Media(
        titleRes = R.string.ftux_page_media_title,
        bodyRes = R.string.ftux_page_media_body,
        illustrationRes = R.drawable.ftux_image_media,
        alignment = Alignment.BottomCenter,
    ),
)

private val BrandGradientColors = listOf(
    Color(0xFF42A5FF),
    Color(0xFF4379FF),
    Color(0xFF8E5CFF),
)

// Dimensions of the overlaid "Get Started" CTA, shared so the Media page can
// reserve the matching amount of space below its body text.
private val FtuxCtaHeight = 50.dp
private val FtuxCtaVerticalPadding = 16.dp

fun NavGraphBuilder.ftuxCarousel(
    onFinish: () -> Unit,
) {
    composable(OnboardingRoutes.FTUX_CAROUSEL) {
        FtuxCarouselContent(onFinish = onFinish)
    }
}

@Composable
private fun FtuxCarouselContent(
    onFinish: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { ftuxPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage by remember { derivedStateOf { pagerState.currentPage == ftuxPages.lastIndex } }

    // System back animates to the previous page on every page except the
    // first; on the first page the handler stays disabled so back propagates
    // to the activity and ends the FTUX flow naturally.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }
    val dotsVerticalPadding = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.02f
    }.coerceIn(8.dp, 24.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colorResource(R.color.almostWhite))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.End
                    )
                )
        ) {
            // Top controls fade out on the last page (where the Get Started
            // CTA replaces them) but stay in the layout so the row height
            // doesn't shift between pages.
            val controlsAlpha by animateFloatAsState(
                targetValue = if (isLastPage) 0f else 1f,
                label = "ftux_top_controls_alpha",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // When the controls fade out on the last page we also drop
                // them from the accessibility tree so TalkBack doesn't focus
                // invisible "disabled" buttons next to the Get Started CTA.
                val controlsSemantics = if (isLastPage) {
                    Modifier.clearAndSetSemantics { }
                } else {
                    Modifier
                }
                OlvidTextButton(
                    modifier = Modifier
                        .alpha(controlsAlpha)
                        .then(controlsSemantics),
                    onClick = onFinish,
                    text = stringResource(R.string.button_label_skip),
                    large = true,
                    enabled = !isLastPage,
                    contentColor = colorResource(R.color.almostBlack)
                )
                Spacer(modifier = Modifier.weight(1f))
                OlvidTextButton(
                    modifier = Modifier
                        .alpha(controlsAlpha)
                        .then(controlsSemantics),
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    text = stringResource(R.string.ftux_next),
                    large = true,
                    enabled = !isLastPage,
                )
            }

            PagerDots(
                pageCount = ftuxPages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = dotsVerticalPadding),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(interactionSource = null, indication = null) {
                        if (!isLastPage) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
            ) { pageIndex ->
                FtuxPage(ftuxPages[pageIndex])
            }
        }
        AnimatedVisibility(
            visible = isLastPage,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            OlvidActionButton(
                onClick = onFinish,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = 24.dp, vertical = FtuxCtaVerticalPadding)
                    .height(FtuxCtaHeight),
                text = stringResource(
                    R.string.ftux_start
                )
            )
        }
    }
}

@Composable
private fun FtuxPage(page: FtuxPageDef, modifier: Modifier = Modifier) {
    when (page) {
        is FtuxPageDef.Welcome -> FtuxWelcomePage(page, modifier)
        is FtuxPageDef.Standard -> FtuxStandardPage(page, modifier)
        is FtuxPageDef.Media -> FtuxMediaPage(page, modifier)
    }
}

/**
 * A page title plus the character range of its highlighted word. The range is
 * derived from the `<b>…</b>` span the title resource wraps around its highlight
 * word, so it is exact in every locale (no word-boundary heuristics) and cannot
 * drift from the title — the word and its position live in the same string.
 * `<b>` is used purely as a position marker (the title is already fully bold via
 * [GradientHighlightedTitle]); it is the project's existing highlight convention
 * and, being attribute-free, survives the machine-translation pipeline intact.
 * [highlightStart] is -1 when the title carries no marker.
 */
private data class FtuxTitle(
    val text: String,
    val highlightStart: Int,
    val highlightEnd: Int,
)

@Composable
private fun ftuxTitleFor(@StringRes titleRes: Int): FtuxTitle {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration).get(0)
        ?: LocalLocale.current.platformLocale
    // getText() preserves the inline <b> span; stringResource() would flatten it
    // to plain text and lose the highlight position.
    return remember(titleRes, configuration) {
        val styled = context.resources.getText(titleRes)
        val text = styled.toString()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        // First style span only — a single contiguous highlight per title,
        // matching the single-range gradient in GradientHighlightedTitle.
        val span = (styled as? Spanned)
            ?.getSpans(0, styled.length, StyleSpan::class.java)
            ?.firstOrNull { styled.getSpanStart(it) < styled.getSpanEnd(it) }
        FtuxTitle(
            text = text,
            highlightStart = span?.let { styled.getSpanStart(it) } ?: -1,
            highlightEnd = span?.let { styled.getSpanEnd(it) } ?: -1,
        )
    }
}

@Composable
private fun ftuxBody(@StringRes bodyRes: Int): String =
    stringResource(bodyRes, stringResource(R.string.app_name))

/**
 * First-page layout: app icon at the top, then centred title + tagline below it.
 * Matches the Figma welcome screen, where the brand mark anchors the composition.
 */
@Composable
private fun FtuxWelcomePage(page: FtuxPageDef.Welcome, modifier: Modifier = Modifier) {
    val onSurface = colorResource(R.color.almostBlack)
    val textPadding = Modifier.padding(horizontal = 24.dp)
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        OlvidLogo(size = OlvidLogoSize.EXTRA_LARGE)
        Spacer(modifier = Modifier.height(24.dp))
        GradientHighlightedTitle(
            title = ftuxTitleFor(page.titleRes),
            color = onSurface,
            textAlign = TextAlign.Center,
            modifier = textPadding,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = ftuxBody(page.bodyRes),
            color = onSurface,
            style = OlvidTypography.h1,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = textPadding.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.weight(2f))
    }
}

/**
 * Shared layout for the illustration-driven middle pages and the final media page:
 * title + body anchored to the top, mockup illustration anchored to the bottom centre
 * (matching how Anim1/Anim2/etc sit in the Figma compositions). The illustration is
 * supplied by the caller so each variant can overlay extra content.
 */
@Composable
private fun FtuxIllustrationPageLayout(
    page: FtuxPageDef,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit,
) {
    val onSurface = colorResource(R.color.almostBlack)
    // 24dp horizontal gutter is applied only to the text content. The
    // illustration Box keeps the full page width so the illustration can
    // bleed to the screen edges; each illustration positions itself inside
    // that Box via its own Image `alignment` parameter.
    val textPadding = Modifier.padding(horizontal = 24.dp)
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        GradientHighlightedTitle(
            title = ftuxTitleFor(page.titleRes),
            color = onSurface,
            modifier = textPadding,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = ftuxBody(page.bodyRes),
            color = onSurface,
            fontSize = 16.sp,
            modifier = textPadding.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f),
        ) {
            illustration()
        }
    }
}

@Composable
private fun FtuxStandardPage(page: FtuxPageDef.Standard, modifier: Modifier = Modifier) {
    FtuxIllustrationPageLayout(page, modifier) {
        // ContentScale.Inside keeps the mockup at its intrinsic size when the
        // illustration box is tall enough, and scales it down (preserving aspect
        // ratio) when portrait constraints would otherwise force a top-crop.
        Image(
            painter = painterResource(page.mockupRes),
            modifier = Modifier.fillMaxSize(),
            alignment = page.alignment,
            contentScale = ContentScale.Inside,
            contentDescription = null,
        )
    }
}

@Composable
private fun FtuxMediaPage(page: FtuxPageDef.Media, modifier: Modifier = Modifier) {
    val onSurface = colorResource(R.color.almostBlack)
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        // ContentScale.Fit scales the illustration to fit entirely within its
        // share of vertical space while preserving aspect ratio, so it is never
        // cropped top/bottom (FillWidth used to over-scale the height and clip
        // the image). This works the same in portrait and landscape.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                painter = painterResource(page.illustrationRes),
                contentDescription = null,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        GradientHighlightedTitle(
            title = ftuxTitleFor(page.titleRes),
            color = onSurface,
            textAlign = TextAlign.Start,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = ftuxBody(page.bodyRes),
            color = onSurface,
            style = OlvidTypography.body1,
            textAlign = TextAlign.Start,
        )
        // Reserve room for the overlaid Get Started CTA plus the 45dp gap the
        // Figma design leaves between the body text and the button. The CTA
        // occupies its height + bottom padding above the bottom safe inset; its
        // matching top padding falls inside this 45dp gap.
        Spacer(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .height(45.dp + FtuxCtaHeight + FtuxCtaVerticalPadding)
        )
    }

}

@Composable
private fun GradientHighlightedTitle(
    modifier: Modifier = Modifier,
    title: FtuxTitle,
    color: Color,
    textAlign: TextAlign = TextAlign.Start,
) {
    // The highlight range comes straight from the title's <annotation> span, so
    // no text search is needed and it is correct in every locale.
    val startIndex = title.highlightStart
    val endIndex = title.highlightEnd
    // After the first layout pass we know the highlighted word's bounds and can
    // build a gradient that spans exactly that rect. Before that, use a solid
    // colour so the text doesn't flash with a different visual.
    var bounds: Rect? by remember(startIndex, endIndex, title.text) { mutableStateOf(null) }
    val brush: Brush = bounds?.let {
        Brush.linearGradient(
            colors = BrandGradientColors,
            start = Offset(it.left, it.top),
            end = Offset(it.right, it.bottom),
        )
    } ?: SolidColor(BrandGradientColors.first())

    val annotated = buildAnnotatedString {
        if (startIndex < 0) {
            append(title.text)
            return@buildAnnotatedString
        }
        append(title.text.substring(0, startIndex))
        withStyle(SpanStyle(brush = brush)) {
            append(title.text.substring(startIndex, endIndex))
        }
        append(title.text.substring(endIndex))
    }
    Text(
        text = annotated,
        color = color,
        style = OlvidTypography.h1,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
        modifier = modifier.fillMaxWidth(),
        onTextLayout = { layout ->
            if (startIndex < 0 || endIndex > layout.layoutInput.text.length) return@Text
            val first = layout.getBoundingBox(startIndex)
            val last = layout.getBoundingBox(endIndex - 1)
            val measured = Rect(first.left, first.top, last.right, last.bottom)
            if (bounds != measured) bounds = measured
        },
    )
}

@Composable
private fun PagerDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val activeColor = colorResource(R.color.olvid_gradient_light)
    val inactiveColor = activeColor.copy(alpha = 0.25f)
    val pageIndicator = stringResource(
        R.string.ftux_page_indicator, currentPage + 1, pageCount,
    )
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = pageIndicator
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            // Single rounded-corner shape (radius == half height) works for both
            // the circular inactive state and the pill-shaped active state, so we
            // can morph the width and colour without animating the shape itself.
            val width by animateDpAsState(
                targetValue = if (isActive) 24.dp else 10.dp,
                label = "ftux_dot_width",
            )
            val color by animateColorAsState(
                targetValue = if (isActive) activeColor else inactiveColor,
                label = "ftux_dot_color",
            )
            Box(
                modifier = Modifier
                    .height(10.dp)
                    .width(width)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
        }
    }
}

/**
 * Wraps a page preview with the same surface and inset behaviour the live carousel
 * gives it, so the preview matches the on-device rendering for both themes.
 */
@Composable
private fun FtuxPagePreviewSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colorResource(R.color.almostWhite))
    ) {
        content()
    }
}

private val previewWelcomePage = ftuxPages[0] as FtuxPageDef.Welcome
private val previewPrivatePage = ftuxPages[1] as FtuxPageDef.Standard
private val previewEveryonePage = ftuxPages[2] as FtuxPageDef.Standard
private val previewProtectPage = ftuxPages[3] as FtuxPageDef.Standard
private val previewPricingPage = ftuxPages[4] as FtuxPageDef.Standard
private val previewMediaPage = ftuxPages[5] as FtuxPageDef.Media

@PreviewLightDark
@Composable
private fun FtuxWelcomePagePreview() {
    FtuxPagePreviewSurface { FtuxWelcomePage(previewWelcomePage) }
}

@PreviewLightDark
@Composable
private fun FtuxPrivatePagePreview() {
    FtuxPagePreviewSurface { FtuxStandardPage(previewPrivatePage) }
}

@PreviewLightDark
@Composable
private fun FtuxEveryonePagePreview() {
    FtuxPagePreviewSurface { FtuxStandardPage(previewEveryonePage) }
}

@PreviewLightDark
@Composable
private fun FtuxProtectPagePreview() {
    FtuxPagePreviewSurface { FtuxStandardPage(previewProtectPage) }
}

@PreviewLightDark
@Composable
private fun FtuxPricingPagePreview() {
    FtuxPagePreviewSurface { FtuxStandardPage(previewPricingPage) }
}

@PreviewLightDark
@Composable
private fun FtuxMediaPagePreview() {
    FtuxPagePreviewSurface { FtuxMediaPage(previewMediaPage) }
}

@Preview
@Composable
private fun FtuxCarouselPreview() {
    NavHost(
        navController = rememberNavController(),
        startDestination = OnboardingRoutes.FTUX_CAROUSEL,
    ) {
        ftuxCarousel(onFinish = {})
    }
}
