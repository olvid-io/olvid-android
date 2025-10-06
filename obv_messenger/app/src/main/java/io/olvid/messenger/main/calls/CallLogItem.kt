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

package io.olvid.messenger.main.calls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.databases.dao.CallLogItemDao.CallLogItemAndContacts
import io.olvid.messenger.databases.entity.CallLogItem
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.InitialView

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallLogItemView(
    modifier: Modifier = Modifier,
    callLogItemAndContacts: CallLogItemAndContacts,
    initialViewSetup: (initialView: InitialView) -> Unit,
    title: AnnotatedString,
    date: AnnotatedString,
    onClick: () -> Unit,
    onDeleteCallLogItem: (callLogItem: CallLogItem) -> Unit,
) {
    Box(modifier = modifier) {
        // menu
        var menuOpened by remember { mutableStateOf(false) }
        CallLogMenu(
            menuOpened = menuOpened,
            onDismissRequest = { menuOpened = false },
            callLogItemAndContacts = callLogItemAndContacts,
            onDeleteCallLogItem = onDeleteCallLogItem,
        )

        Row(
            modifier = Modifier
                .height(Min)
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { },
                    onLongClick = { menuOpened = true },
                ), verticalAlignment = CenterVertically
        ) {
            // InitialView
            InitialView(
                modifier = Modifier
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    )
                    .requiredSize(40.dp),
                initialViewSetup = initialViewSetup,
            )

            // content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title
                Text(
                    text = title,
                    color = colorResource(id = R.color.primary700),
                    style = OlvidTypography.h3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = CenterVertically) {
                    Image(
                        modifier = Modifier.requiredSize(16.dp),
                        painter = painterResource(id = callLogItemAndContacts.callLogItem.getStatusImageResource()),
                        alignment = BottomCenter,
                        contentDescription = "Call Status")
                    Spacer(modifier = Modifier.requiredWidth(4.dp))
                    // Date
                    Text(
                        modifier = Modifier.padding(top = 2.dp),
                        text = date,
                        color = colorResource(id = R.color.grey),
                        style = OlvidTypography.subtitle1
                    )
                }
            }

            Image(
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(size = 36.dp))
                    .clickable { onClick.invoke() }
                    .padding(8.dp)
                    .size(32.dp),
                painter = painterResource(id = R.drawable.ic_phone_grey),
                contentDescription = stringResource(id = R.string.button_label_call)
            )

            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
fun CallLogMenu(
    menuOpened: Boolean,
    onDismissRequest: () -> Unit,
    callLogItemAndContacts: CallLogItemAndContacts,
    onDeleteCallLogItem: (callLogItem: CallLogItem) -> Unit,
) {
    DropdownMenu(expanded = menuOpened, onDismissRequest = onDismissRequest) {
        //delete
        DropdownMenuItem(onClick = {
            onDeleteCallLogItem(callLogItemAndContacts.callLogItem)
            onDismissRequest()
        }) {
            Text(
                text = stringResource(id = R.string.menu_action_delete_log_entry),
            )
        }
    }
}

@Preview
@Composable
private fun CallLogItemPreview() {
    AppCompatTheme {
        CallLogItemView(
            callLogItemAndContacts = CallLogItemAndContacts(),
            initialViewSetup = { initialView -> initialView.setInitial(byteArrayOf(), "A") },
            title = AnnotatedString("Call Title"),
            date = AnnotatedString("timestamp"),
            onClick = {},
            onDeleteCallLogItem = {}
        )
    }
}