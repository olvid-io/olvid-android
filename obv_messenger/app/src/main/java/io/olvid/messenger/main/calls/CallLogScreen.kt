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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.databases.dao.CallLogItemDao.CallLogItemAndContacts
import io.olvid.messenger.main.MainScreenEmptyList
import io.olvid.messenger.main.cutoutHorizontalPadding

@Composable
fun CallLogScreen(
    callLogViewModel: CallLogViewModel,
    onNewCallClick: () -> Unit,
    onClick: (callLogItemAndContacts: CallLogItemAndContacts) -> Unit
) {

    val callLog by callLogViewModel.callLogLiveData.observeAsState()

    AppCompatTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            callLog?.let { log ->
                if (log.isEmpty().not()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(bottom = 64.dp),
                    ) {
                        itemsIndexed(items = log) { index, call ->
                            Box {
                                CallLogItemView(
                                    modifier = Modifier
                                        .background(colorResource(id = R.color.almostWhite))
                                        .cutoutHorizontalPadding(),
                                    callLogItemAndContacts = call,
                                    title = call.getAnnotatedTitle(),
                                    date = call.callLogItem.getAnnotatedDate(),
                                    initialViewSetup = { initialView ->
                                        callLogViewModel.initialViewSetup(
                                            initialView,
                                            call
                                        )
                                    },
                                    onClick = { onClick(call) },
                                    onDeleteCallLogItem = callLogViewModel::delete
                                )
                                if (index < log.size - 1) {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 68.dp, end = 12.dp)
                                            .requiredHeight(1.dp)
                                            .align(Alignment.BottomStart)
                                            .background(
                                                color = colorResource(id = R.color.lightGrey)
                                            )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MainScreenEmptyList(
                            icon = R.drawable.ic_phone_log,
                            iconPadding = 6.dp,
                            title = R.string.explanation_empty_call_log,
                            subtitle = R.string.explanation_empty_call_log_sub
                        )
                    }
                }
            }
            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 72.dp, end = 16.dp),
                onClick = onNewCallClick
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_phone),
                    contentDescription = stringResource(
                        id = R.string.button_label_call
                    )
                )
            }
        }
    }
}