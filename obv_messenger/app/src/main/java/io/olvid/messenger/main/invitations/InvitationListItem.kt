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

package io.olvid.messenger.main.invitations

import android.util.Base64
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
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
import io.olvid.messenger.designsystem.theme.OlvidTypography
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvitationListItem(
    modifier: Modifier = Modifier,
    invitationListViewModel: InvitationListViewModel,
    invitation: Invitation?,
    title: AnnotatedString,
    date: AnnotatedString,
    initialViewSetup: (initialView: InitialView) -> Unit,
    onClick: (action: Action, invitation: Invitation, lastSAS: String?) -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
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
                        style = OlvidTypography.h3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Date
                    Text(
                        modifier = Modifier.padding(top = 2.dp),
                        text = date,
                        color = colorResource(id = R.color.grey),
                        style = OlvidTypography.subtitle1,
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
                    style = OlvidTypography.body2,
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

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    val accentColor = colorResource(id = R.color.accent)

                    // your code
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            modifier = Modifier,
                            text = stringResource(id = R.string.invitation_label_your_code),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = OlvidTypography.body2,
                            fontWeight = FontWeight.Medium,
                            color = colorResource(
                                id = R.color.grey
                            )
                        )
                        Spacer(modifier = Modifier.requiredHeight(4.dp))

                        val myCode = String(invitation?.associatedDialog?.category?.sasToDisplay ?: byteArrayOf(), StandardCharsets.UTF_8)

                        BasicTextField(
                            modifier = Modifier.requiredWidth(with(LocalDensity.current) { 128.sp.toDp() } ),
                            value = myCode,
                            enabled = false,
                            onValueChange = {},
                            decorationBox = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    repeat(Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS) { index ->
                                        BoxedChar(
                                            modifier = Modifier.weight(1f),
                                            error = false,
                                            index = index,
                                            text = myCode
                                        )
                                        if (index != Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS - 1) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                    }
                                }
                            }
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
                            val interactionSource = remember { MutableInteractionSource() }
                            val focused by interactionSource.collectIsFocusedAsState()

                            LaunchedEffect(invitationListViewModel.lastSas) {
                                invitationListViewModel.lastSas?.let {
                                    sas = TextFieldValue(it).copy(selection = TextRange(it.length))
                                    sasInputField.requestFocus()
                                }
                            }

                            CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                                BasicTextField(
                                    modifier = Modifier
                                        .requiredWidth(with(LocalDensity.current) { 128.sp.toDp() })
                                        .onKeyEvent {
                                            if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                                                validateSas()
                                                return@onKeyEvent true
                                            }
                                            false
                                        }
                                        .focusRequester(sasInputField)
                                        .focusable(false),
                                    value = sas,
                                    interactionSource = interactionSource,
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
                                        val previousSasLength = sas.text.length
                                        sas = input.copy(
                                            text = newSas,
                                            selection = if (invitationListViewModel.lastSas == newSas && input.selection.length == Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS) {
                                                TextRange(0, newSas.length)
                                            } else input.selection
                                        )
                                        if (previousSasLength < Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS && newSas.length == Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS) {
                                            validateSas()
                                        }
                                    },
                                    decorationBox = {
                                        Column {
                                            Text(
                                                modifier = Modifier.fillMaxWidth(),
                                                text = stringResource(id = R.string.invitation_label_their_code),
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                style = OlvidTypography.body2,
                                                fontWeight = FontWeight.Medium,
                                                color = colorResource(
                                                    id = R.color.grey
                                                )
                                            )
                                            Spacer(modifier = Modifier.requiredHeight(4.dp))
                                            Row(modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center) {
                                                repeat(Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS) { index ->
                                                    BoxedChar(
                                                        modifier = Modifier.weight(1f),
                                                        error = sas.text == invitationListViewModel.lastSas &&
                                                                invitationListViewModel.lastSasDialogUUID == invitation.associatedDialog.uuid &&
                                                                invitationListViewModel.lastTimestamp != invitation.invitationTimestamp,
                                                        index = index,
                                                        text = sas.text,
                                                        focused = focused
                                                    )
                                                    if (index != Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS - 1) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        } else { //Category.SAS_CONFIRMED_DIALOG_CATEGORY
                            Text(
                                modifier = Modifier.requiredWidth(with(LocalDensity.current) { 128.sp.toDp() }),
                                text = stringResource(id = R.string.invitation_label_their_code),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                style = OlvidTypography.body2,
                                fontWeight = FontWeight.Medium,
                                color = colorResource(
                                    id = R.color.grey
                                )
                            )
                            Spacer(modifier = Modifier.requiredHeight(2.dp))
                            Image(
                                modifier = Modifier
                                    .align(CenterHorizontally)
                                    .size(40.dp),
                                painter = painterResource(id = R.drawable.ic_ok_outline),
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
                            style = OlvidTypography.body1
                        )
                        ClickableText(
                            text = it,
                            style = OlvidTypography.body2.copy(
                                color = colorResource(
                                    id = R.color.primary700
                                )
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
            Row(modifier = Modifier
                .padding(vertical = 4.dp)
                .focusProperties { canFocus = false }) {
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
                                stringResource(id = R.string.button_label_abort),
                                color = colorResource(id = R.color.blueOrWhite)
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
                                stringResource(id = R.string.button_label_ignore),
                                color = colorResource(id = R.color.blueOrWhite)
                            )
                        }
                        TextButton(
                            onClick = { onClick(ACCEPT, invitation!!, null) }) {
                            Text(
                                stringResource(id = R.string.button_label_accept),
                                color = colorResource(id = R.color.blueOrWhite)
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
                                stringResource(id = R.string.button_label_reject),
                                color = colorResource(id = R.color.blueOrWhite)
                            )
                        }
                        TextButton(
                            onClick = { onClick(ACCEPT, invitation!!, null) }) {
                            Text(
                                stringResource(id = R.string.button_label_accept),
                                color = colorResource(id = R.color.blueOrWhite)
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
                                stringResource(id = R.string.button_label_abort),
                                color = colorResource(id = R.color.blueOrWhite)
                            )
                        }
                        val enabled = sas.text.length == Constants.DEFAULT_NUMBER_OF_DIGITS_FOR_SAS
                        TextButton(
                            onClick = { validateSas() },
                            enabled = enabled
                        ) {
                            Text(
                                stringResource(id = R.string.button_label_validate),
                                color = colorResource(id = R.color.blueOrWhite).copy(alpha = if (enabled) 1f else ContentAlpha.disabled),
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
                                stringResource(id = R.string.button_label_reject),
                                color = colorResource(id = R.color.blueOrWhite)
                            )
                        }
                        TextButton(
                            onClick = { onClick(ACCEPT, invitation!!, null) },
                            enabled = enabled
                        ) {
                            Text(
                                stringResource(id = R.string.button_label_accept),
                                color = colorResource(id = R.color.blueOrWhite).copy(alpha = if (enabled) 1f else ContentAlpha.disabled),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxedChar(
    modifier: Modifier = Modifier,
    error: Boolean,
    index: Int,
    text: String,
    focused: Boolean = true
) {
    val isFocused = focused && (text.length == index)
    val char = when {
        index == text.length -> "_"
        index > text.length -> " "
        else -> text[index].toString()
    }
    Text(
        modifier = modifier
            .border(
                1.dp, if (error) Color(0xFFE2594E) else when {
                    isFocused -> colorResource(id = R.color.blueOrWhite)
                    else -> colorResource(id = R.color.grey)
                }, RoundedCornerShape(8.dp)
            )
            .padding(vertical = 2.dp),
        text = char,
        fontSize = 26.sp,
        color =
        if (error) {
            Color(0xFFE2594E)
        } else if (isFocused) {
            colorResource(id = R.color.blueOrWhite)
        } else {
            colorResource(id = R.color.grey)
        },
        textAlign = TextAlign.Center
    )
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
            date = AnnotatedString("date"),
            initialViewSetup = {}
        )
    }
}

