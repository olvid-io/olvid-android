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

package io.olvid.messenger.main.contacts.suggested

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.DialogFullScreen
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.theme.OlvidTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val ONBOARDING_PAGES = 2

/**
 * Two-page first-run explainer shown over the swipe stack. Dismissing it (Passer / J'ai compris
 * / close) is the caller's responsibility — it persists the "seen" preference.
 */
@Composable
fun SuggestedContactsOnboarding(
    items: List<SuggestedContactItem>,
    onDismiss: () -> Unit,
) {
    DialogFullScreen(onDismissRequest = onDismiss) {
        val pagerState = rememberPagerState { ONBOARDING_PAGES }
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.almostWhite))
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.content_description_close_button),
                        tint = colorResource(R.color.almostBlack),
                    )
                }
            }

            HorizontalPager(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                state = pagerState,
                userScrollEnabled = false,
            ) { page ->
                when (page) {
                    0 -> OnboardingPageSwipe(items = items)
                    else -> OnboardingPageOrigin(items = items)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (pagerState.currentPage == 0) {
                OlvidTextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    text = stringResource(R.string.button_label_skip),
                    contentColor = colorResource(R.color.greyTint),
                    onClick = onDismiss,
                )
                OlvidActionButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    large = true,
                    text = stringResource(R.string.button_label_next),
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                )
            } else {
                OlvidActionButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    large = true,
                    text = stringResource(R.string.button_label_understood),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageSwipe(items: List<SuggestedContactItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            Spacer(modifier = Modifier.height(24.dp))
        }
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(R.string.label_suggested_contacts_onboarding_title_1),
            textAlign = TextAlign.Center,
            style = OlvidTypography.h2,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.almostBlack),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(R.string.label_suggested_contacts_onboarding_message_1),
            textAlign = TextAlign.Center,
            style = OlvidTypography.body1,
            color = colorResource(R.color.greyTint),
        )
        Spacer(modifier = Modifier.height(8.dp))

        // self-running demo: gestures are disabled and the deck swipes itself, alternating right
        // (invite) and left (skip) so both outcomes are shown. Its own state and no-op callbacks
        // mean it never sends an invite, and it recycles when the small deck is exhausted so the
        // loop never stops. The direction lives outside the recycled state so alternation survives.
        val demoItems = remember(items) { items.take(3) }
        var demoRound by remember { mutableIntStateOf(0) }
        var nextDirection by remember { mutableStateOf(SwipeDirection.RIGHT) }
        var demoPlaying by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .padding(horizontal = 64.dp)
                    .padding(bottom = 8.dp)
                    .aspectRatio(CARD_ASPECT)
                    .fillMaxWidth(),
            ) {
                // render the deck at its design size and scale it to the measured slot:
                // the content is never squeezed, and since the
                // pager is full-bleed the shadow and the swipe-out animation are not clipped
                val scale = (maxWidth.value / CARD_WIDTH).coerceAtMost(maxHeight.value / CARD_HEIGHT).coerceAtMost(1f)
                Box(
                    modifier = Modifier
                        .requiredSize(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    key(demoRound) {
                        val demoState = rememberSwipeStackState(demoItems.size)
                        LaunchedEffect(demoState.isFinished) {
                            // the isNotEmpty() guard avoids an endless remount loop when there is no card
                            if (demoState.isFinished && demoItems.isNotEmpty()) {
                                demoRound++
                            }
                        }
                        LaunchedEffect(demoState) {
                            while (!demoState.isFinished) {
                                delay(1.2.seconds)
                                demoPlaying = true
                                // the finger lifts off at release, so it fades while still riding the
                                // flung card and is gone before the drag offset snaps back to 0
                                demoState.demoSwipe(nextDirection) { demoPlaying = false }
                                nextDirection = if (nextDirection == SwipeDirection.RIGHT) SwipeDirection.LEFT else SwipeDirection.RIGHT
                            }
                        }
                        SwipeableContactCardStack(
                            state = demoState,
                            items = demoItems,
                            onMoreInfo = {},
                            onDismissed = {},
                            currentCardUiState = null,
                            gesturesEnabled = false,
                        )
                        val fingerAlpha by animateFloatAsState(
                            targetValue = if (demoPlaying) 1f else 0f,
                            // fade-out must finish within the 240 ms fling (see DISMISS_SPEC)
                            animationSpec = tween(durationMillis = 200),
                            label = "hintFinger",
                        )
                        Icon(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 16.dp, y = (-20).dp)
                                .size(44.dp)
                                .graphicsLayer {
                                    alpha = fingerAlpha
                                    translationX = demoState.dragX * 0.35f
                                },
                            painter = painterResource(R.drawable.ic_finger_swipe),
                            contentDescription = null,
                            tint = colorResource(R.color.almostBlack),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageOrigin(items: List<SuggestedContactItem>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            Spacer(modifier = Modifier.height(24.dp))
        }
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(R.string.label_suggested_contacts_onboarding_title_2),
            textAlign = TextAlign.Center,
            style = OlvidTypography.h2,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.almostBlack),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(R.string.label_suggested_contacts_onboarding_message_2),
            textAlign = TextAlign.Center,
            style = OlvidTypography.body1,
            color = colorResource(R.color.greyTint),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Spacer(modifier = Modifier.weight(1f))
        // the diagram and its caption live on a rounded popup card, like in the Figma frame
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .widthIn(max = CARD_WIDTH.dp)
                .fillMaxWidth()
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(32.dp))
                .background(colorResource(R.color.dialogBackground))
                .padding(vertical = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 8.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OriginDiagram(items = items)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.label_suggested_contacts_onboarding_caption),
                textAlign = TextAlign.Center,
                style = OlvidTypography.body2,
                color = colorResource(R.color.greyTint),
            )
        }
        Spacer(modifier = Modifier.weight(1.5f))
    }
}

// Figma diagram metrics: 64dp central avatars, 52dp side ones, 40dp faded flanking ones,
// chips straddling avatar bottoms
private val DIAGRAM_AVATAR_SIZE = 64.dp
private val DIAGRAM_SIDE_AVATAR_SIZE = 52.dp
private val DIAGRAM_FADED_AVATAR_SIZE = 40.dp
private val DIAGRAM_AVATAR_SPACING = 8.dp
private val CHIP_OVERLAP = 12.dp


@Composable
private fun OriginDiagram(items: List<SuggestedContactItem>) {
    val ownedIdentity by AppSingleton.getCurrentIdentityLiveData().observeAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            AvatarWithChip(label = stringResource(R.string.label_suggested_contacts_you)) {
                Avatar(
                    modifier = Modifier.size(DIAGRAM_AVATAR_SIZE),
                    target = ownedIdentity,
                    showBadges = false,
                )
            }

            DottedConnector()
        }
        AvatarWithChip(label = stringResource(R.string.label_suggested_contacts_common_group)) {
            Box(
                modifier = Modifier
                    .size(DIAGRAM_AVATAR_SIZE)
                    .clip(CircleShape)
                    .background(colorResource(R.color.lighterGrey)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(28.dp),
                    painter = painterResource(R.drawable.ic_group),
                    contentDescription = null,
                    tint = colorResource(R.color.green),
                )
            }
        }

        SplayedConnectors()

        // three contacts in common, flanked by two faded ones hinting there are more
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(DIAGRAM_AVATAR_SPACING),
        ) {
            DiagramContactAvatar(items.getOrNull(3), size = DIAGRAM_FADED_AVATAR_SIZE, alpha = 0.3f)
            DiagramContactAvatar(items.getOrNull(1), size = DIAGRAM_SIDE_AVATAR_SIZE)
            DiagramContactAvatar(items.getOrNull(0), size = DIAGRAM_AVATAR_SIZE)
            DiagramContactAvatar(items.getOrNull(2), size = DIAGRAM_SIDE_AVATAR_SIZE)
            DiagramContactAvatar(items.getOrNull(4), size = DIAGRAM_FADED_AVATAR_SIZE, alpha = 0.3f)
        }
    }
}

/** Avatar with its label chip straddling the bottom edge, like the card's invitation badge. The
 *  bottom padding reserves the protruding half so the connector below starts under the chip. */
@Composable
private fun AvatarWithChip(label: String, avatar: @Composable () -> Unit) {
    Box(
        modifier = Modifier.padding(bottom = CHIP_OVERLAP),
        contentAlignment = Alignment.TopCenter,
    ) {
        avatar()
        DiagramPill(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = CHIP_OVERLAP),
            text = label,
        )
    }
}

@Composable
private fun DiagramContactAvatar(item: SuggestedContactItem?, size: Dp, alpha: Float = 1f) {
    val modifier = Modifier
        .size(size)
        .graphicsLayer { this.alpha = alpha }
    if (item != null) {
        Avatar(
            modifier = modifier,
            target = item.contact,
            showBadges = false,
        )
    } else {
        // placeholder when there aren't enough suggestions to fill the row
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(colorResource(R.color.lighterGrey)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(size / 2),
                painter = painterResource(R.drawable.tab_contacts),
                contentDescription = null,
                tint = colorResource(R.color.greyTint),
            )
        }
    }
}

@Composable
private fun DiagramPill(
    text: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    Text(
        modifier = modifier
            .clip(CircleShape)
            .background(colorResource(R.color.lighterGrey))
            .border(1.dp, colorResource(R.color.lightGrey), CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        text = text,
        style = OlvidTypography.body2,
        fontWeight = fontWeight,
        color = colorResource(R.color.greyTint),
    )
}

private fun DrawScope.diagramDash() =
    PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))

@Composable
private fun ColumnScope.DottedConnector() {
    val color = colorResource(R.color.greyTint)
    Canvas(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .width(2.dp)
            .height(28.dp)
            .weight(1f, false),
    ) {
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = diagramDash(),
        )
    }
}

/** The three dotted lines fanning out from right under the "common group" chip to the three
 *  central avatars below. */
@Composable
private fun ColumnScope.SplayedConnectors() {
    val color = colorResource(R.color.greyTint)
    // the side avatars' centers sit half the middle avatar + spacing + half a side avatar away
    val avatarPitch = (DIAGRAM_AVATAR_SIZE + DIAGRAM_SIDE_AVATAR_SIZE /2 ) / 2 + DIAGRAM_AVATAR_SPACING
    // each line has its own start, spread out under the chip — they do not share an origin
    val startPitch = 24.dp
    Canvas(
        modifier = Modifier
            // no top padding: the starts stay attached to the chip above
            .padding(bottom = 8.dp)
            .width(avatarPitch * 2 + 2.dp)
            .height(40.dp)
            .weight(1f, false),
    ) {
        val dash = diagramDash()
        listOf(-1, 0, 1).forEach { i ->
            drawLine(
                color = color,
                start = Offset(size.width / 2 + i * startPitch.toPx(), 0f),
                end = Offset(size.width / 2 + i * avatarPitch.toPx(), size.height),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = dash,
            )
        }
    }
}

@Preview
@Composable
private fun SplayedConnectorsPreview() {
    Column {
        SplayedConnectors()
    }
}
