/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.customClasses.MessageAttachmentAdapter
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FyleListItem(fyleAndStatus: FyleAndStatus, fileName: AnnotatedString, onClick: () -> Unit, contextMenu : (@Composable () -> Unit)? = null, onLongClick : (() -> Unit)? = null, extraHorizontalPadding : Dp = 0.dp) {
    val context = LocalContext.current
    val size = with(LocalDensity.current) {
        56.dp.roundToPx()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = extraHorizontalPadding)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        contextMenu?.invoke()
        Image(
            modifier = Modifier
                .size(64.dp)
                .padding(4.dp)
                .clip(shape = RoundedCornerShape(size = 8.dp))
                .border(width = 1.dp, color = colorResource(id = R.color.lightGrey), shape = RoundedCornerShape(size = 8.dp)),
            contentScale = ContentScale.Crop,
            painter = rememberAsyncImagePainter(
                model = PreviewUtils.getBitmapPreview(
                    fyleAndStatus.fyle,
                    fyleAndStatus.fyleMessageJoinWithStatus,
                    size
                )
                    ?: MessageAttachmentAdapter.getDrawableResourceForMimeType(
                        fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType
                    )
            ),
            contentDescription = fyleAndStatus.fyleMessageJoinWithStatus.fileName
        )
        Column(modifier = Modifier.padding(start = 4.dp + extraHorizontalPadding, end = 8.dp)) {
            Text(
                text = fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = fyleAndStatus.fyleMessageJoinWithStatus.nonNullMimeType,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorResource(
                    id = color.greyTint
                )
            )
            Text(
                text = Formatter.formatShortFileSize(
                    context,
                    fyleAndStatus.fyleMessageJoinWithStatus.size
                ),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorResource(
                    id = color.greyTint
                )
            )
        }
    }
}