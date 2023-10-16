/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
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

package io.olvid.messenger.main.invitations

import android.util.Base64
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.engine.datatypes.Constants
import io.olvid.engine.engine.types.ObvDialog.Category
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.InitialView
import io.olvid.messenger.databases.entity.Invitation
import io.olvid.messenger.main.InitialView
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.ABORT
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.ACCEPT
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.IGNORE
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.REJECT
import io.olvid.messenger.main.invitations.InvitationListViewModel.Action.VALIDATE_SAS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

@Composable
fun InvitationListItem(
    invitationListViewModel: InvitationListViewModel,
    invitation: Invitation?,
    title: AnnotatedString,
    body: AnnotatedString,
    date: AnnotatedString,
    initialViewSetup: (initialView: InitialView) -> Unit,
    onClick: (action: Action, invitation: Invitation, lastSAS: String?) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(4.dp),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .background(colorResource(id = R.color.almostWhite))
                .padding(8.dp, 0.dp)
        ) {

            Row(
                modifier = Modifier
                    .height(Min)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // InitialView
                InitialView(
                    modifier = Modifier
                        .padding(
                            top = 12.dp,
                            start = 4.dp,
                            end = 16.dp,
                            bottom = 8.dp
                        )
                        .requiredSize(56.dp),
                    initialViewSetup = initialViewSetup,
                )

                // content
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .weight(1f)
                ) {
                    // Title
                    Text(
                        text = title,
                        color = colorResource(id = R.color.primary700),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Subtitle
                    Text(
                        text = body,
                        color = colorResource(id = R.color.grey),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Date
                    Text(
                        modifier = Modifier.padding(top = 2.dp),
                        text = date,
                        color = colorResource(id = R.color.grey),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // description
            var description: AnnotatedString? by remember {
                mutableStateOf(null)
            }
            invitation?.let {
                LaunchedEffect(invitation.invitationTimestamp) {
                    launch(Dispatchers.IO) {
                        description = AnnotatedString(
                            invitationListViewModel.displayStatusDescriptionTextAsync(
                                invitation.associatedDialog
                            ) ?: ""
                        )
                    }
                }
            }

            description?.let {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = it,
                    fontSize = 14.sp,
                    color = colorResource(id = R.color.grey)
                )
            }

            // SAS
            var sas by remember {
                mutableStateOf(TextFieldValue(""))
            }

            fun validateSas() = invitation?.let {
                if (sas.text.length == Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS) onClick(
                    VALIDATE_SAS,
                    invitation,
                    sas.text
                )
            }


            AnimatedVisibility(
                visible = listOf(
                    Category.SAS_EXCHANGE_DIALOG_CATEGORY,
                    Category.SAS_CONFIRMED_DIALOG_CATEGORY
                ).contains(invitation?.associatedDialog?.category?.id)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .align(CenterHorizontally),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {

                    val accentColor = colorResource(id = R.color.accent)

                    // your code
                    Column {
                        Text(
                            modifier = Modifier,
                            text = stringResource(id = R.string.invitation_label_your_code),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(
                                id = R.color.grey
                            )
                        )
                        Spacer(modifier = Modifier.requiredHeight(4.dp))

                        Text(
                            modifier = Modifier.requiredWidth(with(LocalDensity.current) { 72.sp.toDp() } ),
                            text = String(
                                invitation?.associatedDialog?.category?.sasToDisplay
                                    ?: byteArrayOf(), StandardCharsets.UTF_8
                            ),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            fontSize = 26.sp,
                            color = colorResource(
                                id = R.color.grey
                            )
                        )
                    }

                    // their code
                    Column {
                        if (invitation?.associatedDialog?.category?.id == Category.SAS_EXCHANGE_DIALOG_CATEGORY) {

                            val customTextSelectionColors = TextSelectionColors(
                                handleColor = accentColor,
                                backgroundColor = accentColor.copy(alpha = 0.4f)
                            )
                            val sasInputField = remember { FocusRequester() }

                            LaunchedEffect(invitationListViewModel.lastSas) {
                                invitationListViewModel.lastSas?.let {
                                    sas =
                                        TextFieldValue(it).copy(selection = TextRange(0, it.length))
                                    sasInputField.requestFocus()
                                }
                            }

                            CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {

                                BasicTextField(
                                    modifier = Modifier
                                        .requiredWidth(with(LocalDensity.current) { 72.sp.toDp() } )
                                        .onKeyEvent {
                                            if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                                                validateSas()
                                                return@onKeyEvent true
                                            }
                                            false
                                        }
                                        .focusRequester(sasInputField),
                                    value = sas,
                                    textStyle = TextStyle(
                                        fontSize = 26.sp,
                                        color = colorResource(id = R.color.grey),
                                        textAlign = TextAlign.Center
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        autoCorrect = false,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { validateSas() }
                                    ),
                                    onValueChange = { input ->
                                        val newSas = input.text.trim()
                                            .take(Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS)
                                        sas = input.copy(
                                            text = newSas,
                                            selection = if (invitationListViewModel.lastSas == newSas && input.selection.length == Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS) {
                                                TextRange(0, newSas.length)
                                            } else input.selection
                                        )
                                    },
                                    cursorBrush = SolidColor(accentColor),
                                    decorationBox = { innerTextField ->
                                        Column(
                                            modifier = Modifier.drawWithContent {
                                                drawContent()
                                                drawLine(
                                                    color = accentColor,
                                                    start = Offset(
                                                        x = 0f,
                                                        y = size.height - 1.dp.toPx(),
                                                    ),
                                                    end = Offset(
                                                        x = size.width,
                                                        y = size.height - 1.dp.toPx(),
                                                    ),
                                                    strokeWidth = 1.dp.toPx(),
                                                )
                                            }
                                        ) {
                                            Text(
                                                modifier = Modifier.fillMaxWidth(),
                                                text = stringResource(id = R.string.invitation_label_their_code),
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colorResource(
                                                    id = R.color.grey
                                                )
                                            )
                                            Spacer(modifier = Modifier.requiredHeight(4.dp))
                                            innerTextField()
                                        }

                                    }
                                )
                            }

                            AnimatedVisibility(
                                visible = sas.text == invitationListViewModel.lastSas &&
                                        invitationListViewModel.lastSasDialogUUID == invitation.associatedDialog.uuid &&
                                        invitationListViewModel.lastTimestamp != invitation.invitationTimestamp,
                                enter = EnterTransition.None,
                                exit = ExitTransition.None
                            ) {
                                Text(
                                    modifier = Modifier
                                        .width(with(LocalDensity.current) { 72.sp.toDp() } )
                                        .wrapContentWidth(CenterHorizontally, true)
                                        .padding(vertical = 8.dp),
                                    text = stringResource(id = R.string.message_wrong_code),
                                    textAlign = TextAlign.Center,
                                    color = colorResource(
                                        id = R.color.red
                                    )
                                )
                            }
                        } else { //Category.SAS_CONFIRMED_DIALOG_CATEGORY
                            Text(
                                modifier = Modifier,
                                text = stringResource(id = R.string.invitation_label_their_code),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorResource(
                                    id = R.color.grey
                                )
                            )
                            Spacer(modifier = Modifier.requiredHeight(4.dp))
                            Image(
                                modifier = Modifier
                                    .align(CenterHorizontally)
                                    .size(32.dp),
                                painter = painterResource(id = R.drawable.ic_ok),
                                contentDescription = stringResource(
                                    id = R.string.content_description_code_valid
                                )
                            )
                        }
                    }
                }
            }

            // group contacts
            AnimatedVisibility(
                visible = listOf(
                    Category.GROUP_V2_INVITATION_DIALOG_CATEGORY,
                    Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY,
                    Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY
                ).contains(invitation?.associatedDialog?.category?.id)
            ) {
                var contacts by remember {
                    mutableStateOf<AnnotatedString?>(null)
                }
                invitation?.associatedDialog?.let { dialog ->
                    LaunchedEffect(dialog.uuid) {
                        launch(Dispatchers.IO) {
                            contacts = invitationListViewModel.listGroupMembersAsync(dialog)
                        }
                    }
                }
                val context = LocalContext.current
                contacts?.let {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp, 8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.label_group_members),
                            color = colorResource(
                                id = R.color.primary700
                            ),
                            fontSize = 16.sp
                        )
                        ClickableText(
                            text = it,
                            style = TextStyle(
                                color = colorResource(
                                    id = R.color.primary700
                                ),
                                fontSize = 14.sp
                            )
                        ) { offset ->
                            it.getStringAnnotations(
                                tag = "CONTACT",
                                start = offset,
                                end = offset
                            ).firstOrNull()?.let { annotation ->
                                with(annotation.item.split("|")) {
                                    App.openContactDetailsActivity(
                                        context,
                                        Base64.decode(
                                            getOrNull(0) ?: "",
                                            Base64.NO_PADDING
                                        ),
                                        Base64.decode(
                                            getOrNull(1) ?: "",
                                            Base64.NO_PADDING
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // bottom buttons
            Row(modifier = Modifier.focusProperties { canFocus = false }) {
                AnimatedVisibility(
                    visible = listOf(
                        Category.INVITE_SENT_DIALOG_CATEGORY,
                        Category.INVITE_ACCEPTED_DIALOG_CATEGORY,
                        Category.MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY,
                        Category.ONE_TO_ONE_INVITATION_SENT_DIALOG_CATEGORY,
                        Category.SAS_CONFIRMED_DIALOG_CATEGORY,
                    ).contains(invitation?.associatedDialog?.category?.id)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { onClick(ABORT, invitation!!, null) }) {
                            Text(
                                stringResource(id = R.string.button_label_abort).uppercase(),
                                color = colorResource(id = R.color.accent)
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = listOf(
                        Category.ACCEPT_INVITE_DIALOG_CATEGORY,
                        Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY,
                        Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY,
                    ).contains(invitation?.associatedDialog?.category?.id)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { onClick(IGNORE, invitation!!, null) }) {
                            Text(
                                stringResource(id = R.string.button_label_ignore).uppercase(),
                                color = colorResource(id = R.color.accent)
                            )
                        }
                        TextButton(onClick = { onClick(ACCEPT, invitation!!, null) }) {
                            Text(
                                stringResource(id = R.string.button_label_accept).uppercase(),
                                color = colorResource(id = R.color.accent)
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = listOf(
                        Category.ACCEPT_ONE_TO_ONE_INVITATION_DIALOG_CATEGORY,
                    ).contains(invitation?.associatedDialog?.category?.id)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { onClick(REJECT, invitation!!, null) }) {
                            Text(
                                stringResource(id = R.string.button_label_reject).uppercase(),
                                color = colorResource(id = R.color.accent)
                            )
                        }
                        TextButton(onClick = { onClick(ACCEPT, invitation!!, null) }) {
                            Text(
                                stringResource(id = R.string.button_label_accept).uppercase(),
                                color = colorResource(id = R.color.accent)
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = listOf(
                        Category.SAS_EXCHANGE_DIALOG_CATEGORY,
                    ).contains(invitation?.associatedDialog?.category?.id)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { onClick(ABORT, invitation!!, null) }) {
                            Text(
                                stringResource(id = R.string.button_label_abort).uppercase(),
                                color = colorResource(id = R.color.accent)
                            )
                        }
                        val enabled = sas.text.length == Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS
                        TextButton(
                            onClick = { validateSas() },
                            enabled = enabled
                        ) {
                            Text(
                                stringResource(id = R.string.button_label_validate).uppercase(),
                                color = colorResource(id = R.color.accent).copy(alpha = if (enabled) 1f else ContentAlpha.disabled),
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = listOf(
                        Category.GROUP_V2_INVITATION_DIALOG_CATEGORY,
                        Category.GROUP_V2_FROZEN_INVITATION_DIALOG_CATEGORY,
                    ).contains(invitation?.associatedDialog?.category?.id)
                ) {
                    val enabled =
                        invitation?.associatedDialog?.category?.id == Category.GROUP_V2_INVITATION_DIALOG_CATEGORY
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { onClick(REJECT, invitation!!, null) }) {
                            Text(
                                stringResource(id = R.string.button_label_reject).uppercase(),
                                color = colorResource(id = R.color.accent)
                            )
                        }
                        TextButton(
                            onClick = { onClick(ACCEPT, invitation!!, null) },
                            enabled = enabled
                        ) {
                            Text(
                                stringResource(id = R.string.button_label_accept).uppercase(),
                                color = if (enabled) colorResource(id = R.color.accent) else colorResource(
                                    id = R.color.accent
                                ).copy(alpha = ContentAlpha.disabled)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun InvitationListItemPreview() {
    AppCompatTheme {
        InvitationListItem(
            invitationListViewModel = viewModel(modelClass = InvitationListViewModel::class.java),
            onClick = { _, _, _ -> },
            invitation = null,
            title = AnnotatedString("title"),
            body = AnnotatedString("body"),
            date = AnnotatedString("date"),
            initialViewSetup = {}
        )
    }
}

