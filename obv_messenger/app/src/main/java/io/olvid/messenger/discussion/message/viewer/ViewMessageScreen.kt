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

package io.olvid.messenger.discussion.message.viewer

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.designsystem.avatar.Avatar
import io.olvid.messenger.designsystem.avatar.AvatarSource
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.message.LINK_ANNOTATION_TAG
import io.olvid.messenger.discussion.message.MENTION_ANNOTATION_TAG
import io.olvid.messenger.discussion.message.getAnnotatedStringContent
import io.olvid.messenger.discussion.message.getRawStringContent
import io.olvid.messenger.discussion.message.inlineContentMap
import io.olvid.messenger.discussion.message.openMention
import io.olvid.messenger.settings.SettingsActivity
import kotlinx.coroutines.launch

@Composable
fun ViewMessageScreen(
    message: Message,
    onBackPressed: () -> Unit,
) {
    val context = LocalContext.current
    var rawView by rememberSaveable { mutableStateOf(false) }
    var fontScale by remember { mutableFloatStateOf(SettingsActivity.lastDiscussionFontScale) }
    LifecycleResumeEffect(Unit) {
        fontScale = SettingsActivity.lastDiscussionFontScale

        onPauseOrDispose {
            SettingsActivity.lastDiscussionFontScale = fontScale
        }
    }

    Scaffold(
        containerColor = colorResource(R.color.almostWhite),
        contentColor = colorResource(R.color.almostBlack),
        topBar = {
            OlvidTopAppBar(
                titleText = stringResource(R.string.menu_action_view_message),
                onBackPressed = onBackPressed
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) {
                        var initialSize: Float? = null
                        var initialZoom = 1f
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
                                            initialZoom = fontScale
                                        } else {
                                            fontScale = (initialZoom * event.calculateCentroidSize(true) / initSize)
                                                .coerceIn(1f, 3f)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(16.dp)
            ) {
                SenderCard(message = message)

                Spacer(modifier = Modifier.height(16.dp))

                val text = remember(message.contentBody, rawView) {
                    if (rawView) {
                        getRawStringContent(context, message)
                    } else {
                        getAnnotatedStringContent(
                            context = context,
                            message = message,
                            discussionSearchViewModel = null
                        ).withClickableAnnotations(context)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorResource(R.color.lighterGrey))
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = text,
                            color = colorResource(R.color.almostBlack),
                            style = OlvidTypography.body1.copy(
                                fontSize = 16.sp * fontScale,
                                lineHeight = 1.4.em
                            ),
                            inlineContent = inlineContentMap(scale = fontScale)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            FormattedRawToggle(
                rawView = rawView,
                onRawViewChange = { rawView = it }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = colorResource(R.color.lightGrey)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.label_select_message_to_copy),
                    style = OlvidTypography.body2,
                    color = colorResource(R.color.greyTint)
                )
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = colorResource(R.color.greyTint)
                )
            }
        }
    }
}

@Composable
private fun SenderCard(message: Message) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.lighterGrey))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            modifier = Modifier.size(48.dp),
            target = AvatarSource.fromCache(message.senderIdentifier),
            onClick = { App.openProfileActivity(context, message.senderIdentifier) }
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = ContactCacheSingleton.getContactCustomDisplayName(message.senderIdentifier)
                    ?: stringResource(R.string.text_deleted_contact),
                style = OlvidTypography.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorResource(R.color.almostBlack)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = StringUtils.getPreciseAbsoluteDateString(context, message.timestamp, " - ").toString(),
                style = OlvidTypography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorResource(R.color.greyTint)
            )
        }
    }
}

@Composable
private fun FormattedRawToggle(
    rawView: Boolean,
    onRawViewChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val thumbBias = remember { Animatable(if (rawView) 1f else -1f) }
    LaunchedEffect(rawView) {
        thumbBias.animateTo(if (rawView) 1f else -1f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.lighterGrey))
            .padding(2.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val raw = thumbBias.value > 0f
                        onRawViewChange(raw)
                        scope.launch { thumbBias.animateTo(if (raw) 1f else -1f) }
                    },
                    onDragCancel = {
                        scope.launch { thumbBias.animateTo(if (rawView) 1f else -1f) }
                    }
                ) { change, _ ->
                    change.consume()
                    val bias = (change.position.x / size.width) * 2f - 1f
                    scope.launch { thumbBias.snapTo(bias.coerceIn(-1f, 1f)) }
                }
            }
    ) {
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = thumbBias.value, verticalBias = 0f))
                    .fillMaxWidth(.5f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorResource(R.color.lightGrey))
            )
        }
        Row {
            ToggleSegment(
                label = stringResource(R.string.label_formatted_text),
                selected = !rawView,
                onClick = { onRawViewChange(false) }
            )
            ToggleSegment(
                label = stringResource(R.string.label_raw_text),
                selected = rawView,
                onClick = { onRawViewChange(true) }
            )
        }
    }
}

@Composable
private fun RowScope.ToggleSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val textColor by animateColorAsState(
        targetValue = colorResource(if (selected) R.color.almostBlack else R.color.greyTint),
        label = "textColor"
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = OlvidTypography.body2,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = textColor
        )
    }
}

private val keepExistingSpanStyles = TextLinkStyles(style = SpanStyle())

private fun AnnotatedString.withClickableAnnotations(context: Context): AnnotatedString =
    buildAnnotatedString {
        append(this@withClickableAnnotations)
        this@withClickableAnnotations.getStringAnnotations(LINK_ANNOTATION_TAG, 0, length)
            .forEach { annotation ->
                addLink(
                    LinkAnnotation.Url(annotation.item, styles = keepExistingSpanStyles),
                    annotation.start,
                    annotation.end
                )
            }
        this@withClickableAnnotations.getStringAnnotations(MENTION_ANNOTATION_TAG, 0, length)
            .forEach { annotation ->
                addLink(
                    LinkAnnotation.Clickable(
                        tag = MENTION_ANNOTATION_TAG,
                        styles = keepExistingSpanStyles,
                        linkInteractionListener = { openMention(context, annotation.item) }
                    ),
                    annotation.start,
                    annotation.end
                )
            }
    }
