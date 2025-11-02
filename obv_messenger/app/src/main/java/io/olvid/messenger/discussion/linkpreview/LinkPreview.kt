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

package io.olvid.messenger.discussion.linkpreview

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.map
import coil.compose.rememberAsyncImagePainter
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ifNull
import io.olvid.messenger.databases.AppDatabase
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.DiscussionViewModel
import io.olvid.messenger.discussion.message.attachments.Attachment
import io.olvid.messenger.settings.SettingsActivity
import kotlin.math.roundToInt

@Composable
fun LinkPreview(
    modifier: Modifier = Modifier,
    message: Message,
    discussionViewModel: DiscussionViewModel?,
    linkPreviewViewModel: LinkPreviewViewModel?,
    highlighter: ((Context, AnnotatedString) -> AnnotatedString)? = null,
    onLongClick: () -> Unit = {},
    blockClicks: Boolean,
) {
    var opengraph by remember {
        mutableStateOf<OpenGraph?>(null)
    }
    message.linkPreviewFyleId?.let { linkPreviewFyleId ->
        val linkPreviewFyle by AppDatabase.getInstance()
            .fyleMessageJoinWithStatusDao()
            .getFyleAndStatusObservable(message.id, linkPreviewFyleId).map { fyleAndStatus: FyleMessageJoinWithStatusDao.FyleAndStatus? -> fyleAndStatus?.let { Attachment(fyleAndStatus.fyle, fyleAndStatus.fyleMessageJoinWithStatus) } }
            .observeAsState()
        linkPreviewFyle?.let { fyleAndStatus ->
            if (fyleAndStatus.fyle.isComplete) {
                LaunchedEffect(fyleAndStatus.fyle.id, message.messageType) {
                    if (message.messageType != Message.TYPE_INBOUND_EPHEMERAL_MESSAGE && message.wipeStatus != Message.WIPE_STATUS_REMOTE_DELETED && message.wipeStatus != Message.WIPE_STATUS_WIPED) {
                        linkPreviewViewModel?.linkPreviewLoader(
                            fyleAndStatus.fyle,
                            fyleAndStatus.fyleMessageJoinWithStatus.fileName,
                            fyleAndStatus.fyleMessageJoinWithStatus.messageId
                        ) {
                            if (message.wipeStatus != Message.WIPE_STATUS_REMOTE_DELETED && message.wipeStatus != Message.WIPE_STATUS_WIPED) {
                                opengraph = it
                                it?.getSafeUri()?.let { uri ->
                                    discussionViewModel?.messageLinkPreviewUrlCache?.put(
                                        message.id,
                                        uri.toString()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (message.linkPreviewFyleId == null && message.messageType == Message.TYPE_INBOUND_MESSAGE && SettingsActivity.isLinkPreviewInbound(LocalContext.current)) {
        val density = LocalDensity.current
        LaunchedEffect(message.id) {
            val size = with(density) {
                56.dp.toPx().roundToInt()
            }
            linkPreviewViewModel?.linkPreviewLoader(
                text = message.contentBody,
                imageWidth = size,
                imageHeight = size,
                messageId = message.id
            ) {
                opengraph = it
            }
        }
    }
    LaunchedEffect(message.wipeStatus) {
        if (message.wipeStatus == Message.WIPE_STATUS_REMOTE_DELETED || message.wipeStatus == Message.WIPE_STATUS_WIPED) {
            opengraph = null
        }
    }
    opengraph?.let {
        if (!it.isEmpty()) {
            LinkPreviewContent(
                modifier = modifier,
                openGraph = it,
                onLongClick = onLongClick,
                highlighter = highlighter,
                blockClicks = blockClicks,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkPreviewContent(
    modifier: Modifier = Modifier,
    openGraph: OpenGraph,
    onLongClick: () -> Unit = {},
    highlighter: ((Context, AnnotatedString) -> AnnotatedString)?,
    blockClicks: Boolean,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    Box(modifier = modifier
        .border(
            width = 1.dp,
            color = colorResource(id = R.color.attachmentBorder),
            shape = RoundedCornerShape(4.dp)
        )
        .background(
            color = colorResource(id = R.color.greyTint),
            shape = RoundedCornerShape(4.dp)
        )
        .then(
            if (blockClicks)
                Modifier
            else
                Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onLongClick = {
                        openGraph.getSafeUri()?.toString()?.let {
                            clipboardManager.nativeClipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    it,
                                    it
                                )
                            )
                            App.toast(
                                R.string.toast_message_link_copied,
                                Toast.LENGTH_SHORT
                            )
                        } ifNull {
                            onLongClick()
                        }
                    }) {
                    App.openLink(context, openGraph.getSafeUri())
                })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
                .background(color = colorResource(id = R.color.almostWhite))
        ) {
            if (openGraph.hasLargeImageToDisplay()) {
                Column(modifier = Modifier.padding(4.dp)) {
                    LinkTitleAndDescription(openGraph = openGraph, highlighter)
                    LinkImage(openGraph.bitmap, isLarge = true)
                }
            } else {
                Row(modifier = Modifier.padding(4.dp)) {
                    LinkImage(openGraph.bitmap)
                    Spacer(modifier = Modifier.width(4.dp))
                    LinkTitleAndDescription(openGraph = openGraph, highlighter)
                }
            }
        }
    }
}

@Composable
private fun LinkImage(bitmap: Bitmap?, isLarge: Boolean = false) {
    Image(
        modifier = Modifier
            .then(if (isLarge) bitmap?.let {
                Modifier.aspectRatio(
                    (it.width / it.height.toFloat()).coerceAtLeast(
                        .7f
                    )
                )
            } ?: Modifier.size(56.dp) else Modifier.size(56.dp)),
        painter = rememberAsyncImagePainter(
            model = bitmap
                ?: R.drawable.mime_type_icon_link
        ),
        contentScale = ContentScale.Crop,
        contentDescription = null
    )
}

@Composable
private fun LinkTitleAndDescription(
    openGraph: OpenGraph,
    highlighter: ((Context, AnnotatedString) -> AnnotatedString)?,
) {
    Column(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
        openGraph.title?.let {
            Text(
                text = highlighter?.invoke(
                    LocalContext.current,
                    AnnotatedString(it)
                ) ?: AnnotatedString(it),
                maxLines = if (openGraph.shouldShowCompleteDescription()) 2 else 1,
                style = OlvidTypography.body2.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = colorResource(id = R.color.darkGrey),
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = highlighter?.invoke(
                LocalContext.current,
                AnnotatedString(openGraph.buildDescription())
            ) ?: AnnotatedString(openGraph.buildDescription()),
            maxLines = if (openGraph.shouldShowCompleteDescription()) 100 else 5,
            style = OlvidTypography.body2,
            fontWeight = FontWeight.Normal,
            color = colorResource(id = R.color.greyTint),
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview
@Composable
fun LinkPreviewContentPreview() {
    LinkPreviewContent(
        openGraph = OpenGraph(
            title = "Link title",
            description = "Link description sufficiently long to span multiple lines",
            url = "https://olvid.io",
        ),
        highlighter = null,
        blockClicks = true,
    )
}