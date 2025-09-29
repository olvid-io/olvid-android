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

package io.olvid.messenger.plus_button


import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.CallButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.components.PlusButton
import io.olvid.messenger.main.MainActivity.Companion.LINK_URI_INTENT_EXTRA
import io.olvid.messenger.main.contacts.ContactListViewModel
import io.olvid.messenger.plus_button.scan.SCAN_ONLY_EXTRA_KEY
import io.olvid.messenger.plus_button.scan.ScanActivity
import io.olvid.messenger.plus_button.share.ShareDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusButtonContainer(
    plusButtonVisible: Boolean,
    callButtonVisible: Boolean,
    onCallClicked: () -> Unit,
    insetsForOldAndroid: Insets,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newMessageExpanded by rememberSaveable { mutableStateOf(false) }
    var newContactExpanded by rememberSaveable { mutableStateOf(false) }
    var shareDialogExpanded by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (Build.VERSION.SDK_INT >= 30) {
                    Modifier.safeDrawingPadding()
                } else {
                    Modifier.absolutePadding(
                        top = insetsForOldAndroid.top.dp / LocalDensity.current.density,
                        bottom = insetsForOldAndroid.bottom.dp / LocalDensity.current.density,
                        left = insetsForOldAndroid.left.dp / LocalDensity.current.density,
                        right = insetsForOldAndroid.right.dp / LocalDensity.current.density,
                    )
                }
            )
    ) {
        if (shareDialogExpanded) {
            ShareDialog(onDismissRequest = { shareDialogExpanded = false })
        }
        if (newMessageExpanded) {
            val contactListViewModel = viewModel<ContactListViewModel>()
            val onDismiss = {
                newMessageExpanded = false
                contactListViewModel.setFilter(null)
            }

            val animateDismiss: () -> Unit = {
                scope.launch { state.hide() }.invokeOnCompletion {
                    if (!state.isVisible) {
                        onDismiss.invoke()
                    }
                }
            }

            if (newContactExpanded) {
                BasicAlertDialog(onDismissRequest = { newContactExpanded = false }) {
                    Surface(
                        modifier = Modifier.wrapContentHeight().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = colorResource(R.color.dialogBackground),
                        tonalElevation = AlertDialogDefaults.TonalElevation
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OlvidTextButton(
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                text = stringResource(R.string.new_contact_double_scan),
                                large = true,
                                onClick = {
                                    newContactExpanded = false
                                    context.startActivity(
                                        Intent(
                                            context,
                                            ScanActivity::class.java
                                        )
                                    )
                                    animateDismiss.invoke()
                                })
                            HorizontalDivider(color = colorResource(R.color.lightGrey))
                            OlvidTextButton(
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                text = stringResource(R.string.new_contact_remotely),
                                large = true,
                                onClick = {
                                    shareDialogExpanded = true
                                    newContactExpanded = false
                                    animateDismiss.invoke()
                                })
                            /*
                            Spacer(modifier = Modifier.height(8.dp))
                            OlvidTextButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.new_contact_cancel),
                                onClick = {
                                    newContactExpanded = false
                                })
                            */
                        }
                    }
                }
            }

            val compact = LocalWindowInfo.current.containerSize.height < 540 * LocalDensity.current.density

            ModalBottomSheet(
                modifier = Modifier
                    .then(
                        if (compact)
                            Modifier.statusBarsPadding()
                        else
                            Modifier.statusBarsPadding().padding(top = 24.dp)
                    ),
                sheetState = state,
                containerColor = colorResource(R.color.almostWhite),
                contentColor = colorResource(R.color.almostBlack),
                onDismissRequest = onDismiss,
                dragHandle = {
                    if (compact)
                        Box {}
                    else
                        BottomSheetDefaults.DragHandle()
                },
                contentWindowInsets = { WindowInsets() }
            ) {
                val view = LocalView.current
                (view.parent as? DialogWindowProvider)?.window?.let { window ->
                    SideEffect {
                        // prevent status bar going dark
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                    }
                }
                NewMessageScreen(
                    contactListViewModel = contactListViewModel,
                    dismissParent = animateDismiss,
                    onNewContact = { newContactExpanded = true },
                    onNewGroup = {
                        App.openGroupCreationActivity(context)
                        animateDismiss.invoke()
                    },
                    onGoToScanScreen = { link ->
                        context.startActivity(
                            Intent(
                                context,
                                ScanActivity::class.java
                            ).putExtra(SCAN_ONLY_EXTRA_KEY, link == null).apply {
                                link?.let {
                                    putExtra(LINK_URI_INTENT_EXTRA, it)
                                }
                            }
                        )
                        animateDismiss.invoke()
                    }
                )
            }
        }
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = dimensionResource(R.dimen.tab_bar_size) + 16.dp,
                    end = 16.dp
                ),
            visible = plusButtonVisible,
            enter = scaleIn(),
            exit = scaleOut()
        ) {
            PlusButton {
                newMessageExpanded = true
            }
        }
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = dimensionResource(R.dimen.tab_bar_size) + 16.dp,
                    end = 16.dp
                ),
            visible = callButtonVisible,
            enter = scaleIn(),
            exit = scaleOut()
        ) {
            CallButton(
                onClick = onCallClicked
            )
        }
    }
}
