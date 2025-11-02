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

package io.olvid.messenger.discussion.gallery

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.databases.entity.Fyle
import io.olvid.messenger.databases.entity.FyleMessageJoinWithStatus
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.linkpreview.OpenGraph
import io.olvid.messenger.discussion.message.attachments.constantSp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FyleListItem(
    modifier: Modifier = Modifier,
    fyleAndStatus: FyleAndStatus,
    fileName: AnnotatedString,
    onClick: () -> Unit,
    contextMenu: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    extraHorizontalPadding: Dp = 0.dp,
    previewBorder: Boolean = true,
) {
    val context = LocalContext.current
    val size = with(LocalDensity.current) {
        (if (previewBorder) 56 else 64).dp.roundToPx()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = extraHorizontalPadding)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        contextMenu?.invoke()
        Image(
            modifier = Modifier
                .size(64.dp)
                .then(
                    if (previewBorder)
                        Modifier
                            .padding(4.dp)
                            .clip(shape = RoundedCornerShape(size = 8.dp))
                            .border(
                                width = 1.dp,
                                color = colorResource(id = R.color.lightGrey),
                                shape = RoundedCornerShape(size = 8.dp)
                            )
                    else
                        Modifier
                ),
            contentScale = if (fyleAndStatus.fyleMessageJoinWithStatus.mimeType == "application/pdf") ContentScale.Fit else ContentScale.Crop,
            painter = rememberAsyncImagePainter(
                model = PreviewUtils.getBitmapPreview(
                    fyleAndStatus.fyle,
                    fyleAndStatus.fyleMessageJoinWithStatus,
                    size
                )
                    ?: fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType.getDrawableResourceForMimeType()

            ),
            contentDescription = fyleAndStatus.fyleMessageJoinWithStatus.fileName
        )
        Column(modifier = Modifier.padding(start = 4.dp + extraHorizontalPadding, end = 8.dp)) {
            Text(
                text = fileName,
                color = colorResource(id = R.color.almostBlack),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = constantSp(value = 14),
                lineHeight = constantSp(value = 16),
            )
            Text(
                text = fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType,
                style = OlvidTypography.subtitle1.copy(fontSize = constantSp(value = 12)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorResource(
                    id = R.color.greyTint
                )
            )
            Text(
                text = Formatter.formatShortFileSize(
                    context,
                    fyleAndStatus.fyleMessageJoinWithStatus.size
                ),
                style = OlvidTypography.subtitle1.copy(fontSize = constantSp(value = 12)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorResource(
                    id = R.color.greyTint
                )
            )
        }
    }
}

@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL, widthDp = 320)
@Preview(widthDp = 240)
@Composable
private fun FyleListItemPreview() {
    Column {
        FyleListItem(
            modifier = Modifier.background(colorResource(id = R.color.almostWhite)),
            fyleAndStatus = FyleAndStatus().apply {
                fyle = Fyle()
                fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                    0,
                    0,
                    byteArrayOf(),
                    "",
                    "",
                    "txt/plain",
                    0,
                    1024,
                    byteArrayOf(),
                    0,
                    null
                )
            },
            onClick = {},
            fileName = AnnotatedString("myFile with a long wrapping name.txt"),
        )

        Spacer(modifier = Modifier.height(8.dp))

        FyleListItem(
            modifier = Modifier.background(colorResource(id = R.color.almostWhite)),
            fyleAndStatus = FyleAndStatus().apply {
                fyle = Fyle()
                fyleMessageJoinWithStatus = FyleMessageJoinWithStatus(
                    0,
                    0,
                    byteArrayOf(),
                    "",
                    "",
                    "application/pdf",
                    FyleMessageJoinWithStatus.STATUS_DOWNLOADING,
                    1024,
                    byteArrayOf(),
                    0,
                    null
                )
            },
            onClick = {},
            fileName = AnnotatedString("normal name.pdf"),
        )
    }
}


fun String.getDrawableResourceForMimeType(): Int {
    return if (startsWith("audio/")) {
        R.drawable.mime_type_icon_audio
    } else if (startsWith("image/")) {
        R.drawable.mime_type_icon_image
    } else if (startsWith("video/")) {
        R.drawable.mime_type_icon_video
    } else if (startsWith("text/")) {
        R.drawable.mime_type_icon_text
    } else {
        when (this) {
            OpenGraph.MIME_TYPE -> R.drawable.mime_type_icon_link
            "application/zip", "application/gzip", "application/x-bzip", "application/x-bzip2", "application/x-7z-compressed" -> R.drawable.mime_type_icon_zip
            else -> R.drawable.mime_type_icon_file
        }
    }
}