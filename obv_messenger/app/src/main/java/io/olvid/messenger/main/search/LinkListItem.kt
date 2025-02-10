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

package io.olvid.messenger.main.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import io.olvid.messenger.App
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.discussion.linkpreview.LinkPreviewViewModel
import io.olvid.messenger.discussion.linkpreview.OpenGraph


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkListItem(fyleAndStatus: FyleAndStatus, onClick: () -> Unit, linkPreviewViewModel: LinkPreviewViewModel, globalSearchViewModel: GlobalSearchViewModel) {
    val context = LocalContext.current
    var opengraph by remember {
        mutableStateOf<OpenGraph?>(null)
    }
    LaunchedEffect(fyleAndStatus.fyle.id) {
        linkPreviewViewModel.linkPreviewLoader(
            fyleAndStatus.fyle,
            fyleAndStatus.fyleMessageJoinWithStatus.fileName,
            fyleAndStatus.fyleMessageJoinWithStatus.messageId
        ) {
            opengraph = it
        }
    }
    opengraph?.let { link ->
        Row(
            modifier = Modifier
                .height(Min)
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = {
                    App.openLink(
                        context,
                        link.getSafeUri()
                    )
                })
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Image(
                modifier = Modifier
                    .size(64.dp)
                    .padding(4.dp),
                painter = rememberAsyncImagePainter(
                    model = link.bitmap
                        ?: drawable.mime_type_icon_link
                ),
                contentScale = ContentScale.Crop,
                contentDescription = ""
            )
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            ) {
                link.getSafeUri()?.let {
                    Text(
                        text = globalSearchViewModel.highlight(content = it.toString()),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                link.title?.let {
                    Text(
                        text = globalSearchViewModel.highlight(content = it),
                        style = OlvidTypography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(
                            id = color.greyTint
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                link.description?.let {
                    Text(
                        text = globalSearchViewModel.highlight(content = it),
                        style = OlvidTypography.subtitle1,
                        color = colorResource(
                            id = color.greyTint
                        ),
                        maxLines = if (link.shouldShowCompleteDescription()) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}