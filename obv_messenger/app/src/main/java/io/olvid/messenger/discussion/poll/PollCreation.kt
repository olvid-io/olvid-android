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

package io.olvid.messenger.discussion.poll

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.components.AccessibilityFakeButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.components.OlvidTopAppBar
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.plus
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.olvidDatePickerDefaults
import io.olvid.messenger.designsystem.theme.olvidGreyBackgroundTextFieldColors
import io.olvid.messenger.designsystem.theme.olvidSwitchDefaults
import io.olvid.messenger.designsystem.theme.olvidTimeInputDefaults
import io.olvid.messenger.discussion.poll.PollCreationViewModel.Companion.NONE_ANSWER
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.util.Calendar


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollCreationScreen(
    pollViewModel: PollCreationViewModel = viewModel(),
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorResource(R.color.almostWhite),
        contentColor = colorResource(R.color.almostBlack),
        topBar = {
            OlvidTopAppBar(
                titleText = stringResource(R.string.label_poll_create),
                actions = {
                    SendButton(
                        onClick = {
                            pollViewModel.sendPoll(context)
                            onBackPressed()
                        },
                        enabled = pollViewModel.canSendPoll()
                    )
                },
                onBackPressed = onBackPressed
            )
        }
    ) { contentPadding ->
        val lazyListState = rememberLazyListState(
            initialFirstVisibleItemIndex = 1
        )

        val reorderableState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
        ) { from, to ->
            pollViewModel.reorderAnswers(from.index - 1, to.index - 1)
        }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .cutoutHorizontalPadding()
                .systemBarsHorizontalPadding(),
            state = lazyListState,
            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues() + PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            item {
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.label_poll_question),
                    style = OlvidTypography.h3
                )
                Spacer(modifier = Modifier.height(8.dp))
                SectionCard {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                        ,
                        shape = RoundedCornerShape(12.dp),
                        value = pollViewModel.question,
                        placeholder = {
                            Text(
                                stringResource(R.string.hint_poll_question),
                                style = OlvidTypography.body1.copy(
                                    fontSize = 18.sp,
                                    color = colorResource(R.color.almostBlack).copy(alpha = .3f)
                                )
                            )
                        },
                        textStyle = OlvidTypography.body1.copy(
                            fontSize = 18.sp,
                            color = colorResource(R.color.almostBlack)
                        ),                        onValueChange = { pollViewModel.updateQuestion(it) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        colors = olvidGreyBackgroundTextFieldColors()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.label_poll_answers),
                    style = OlvidTypography.h3
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(
                items = pollViewModel.answers,
                key = { _, answer -> answer.uuid }
            ) { index, answer ->
                ReorderableItem(
                    enabled = answer.text.isNotBlank() && answer.uuid != NONE_ANSWER.uuid,
                    state = reorderableState,
                    key = answer.uuid
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = if (answer.uuid == NONE_ANSWER.uuid) stringResource(R.string.text_none_answer) else answer.text,
                        placeholder = {
                            Text(
                                stringResource(R.string.hint_poll_answer_add),
                                style = OlvidTypography.body1.copy(
                                    color = colorResource(R.color.almostBlack).copy(alpha = .3f)
                                )
                            )
                        },
                        enabled = answer.uuid != NONE_ANSWER.uuid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        onValueChange = { value ->
                            pollViewModel.updateAnswerText(answer.uuid, value)
                        },
                        textStyle = OlvidTypography.body1.copy(
                            fontSize = 18.sp,
                            color = colorResource(R.color.almostBlack),
                            fontStyle = if (answer.uuid == NONE_ANSWER.uuid) FontStyle.Italic else FontStyle.Normal
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = olvidGreyBackgroundTextFieldColors(),
                        leadingIcon =
                            if (pollViewModel.isQuizModeEnabled()) {
                                {
                                    Checkbox(
                                        checked = answer.uuid == pollViewModel.quizAnswer,
                                        onCheckedChange = {
                                            pollViewModel.updateQuizAnswer(answer.uuid)
                                        })
                                }
                            } else {
                                null
                            },
                        trailingIcon = {
                            AnimatedVisibility(answer.text.isNotBlank() && answer.uuid != NONE_ANSWER.uuid) {
                                Icon(
                                    modifier = Modifier
                                        .requiredSize(24.dp)
                                        .draggableHandle(),
                                    painter = painterResource(id = R.drawable.ic_drag_handle),
                                    contentDescription = "Reorder"
                                )
                            }
                        },
                        supportingText = {
                            if(pollViewModel.duplicateAnswers.contains(answer.uuid)) {
                                Text(
                                    stringResource(R.string.error_poll_duplicate_answer),
                                    color = colorResource(R.color.red)
                                )
                            }
                        }
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.label_poll_options),
                    style = OlvidTypography.h3
                )
                Spacer(modifier = Modifier.height(8.dp))
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                value = pollViewModel.multipleChoice,
                                role = Role.Switch,
                                onValueChange = { pollViewModel.updateMultipleChoice(it) })
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, true),
                            text = stringResource(R.string.label_poll_answer_multiple_choice),
                            )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = pollViewModel.multipleChoice,
                            onCheckedChange = null,
                            colors = olvidSwitchDefaults()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                value = pollViewModel.hasNoneAnswer,
                                role = Role.Switch,
                                onValueChange = { pollViewModel.updateHasNoneAnswer(it) })
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, true),
                            text = stringResource(
                                R.string.label_poll_include_none_answer,
                                stringResource(R.string.text_none_answer)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = pollViewModel.hasNoneAnswer,
                            onCheckedChange = null,
                            colors = olvidSwitchDefaults()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                /*
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = CenterVertically) {
                    Text(modifier = Modifier.weight(1f, true),
                            text = stringResource(R.string.label_poll_quiz_mode))
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = pollViewModel.isQuizModeEnabled(),
                        onCheckedChange = { value -> pollViewModel.enableQuizMode(value) },
                        colors = olvidSwitchDefaults()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                */

                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                value = pollViewModel.expirationDateEnabled,
                                role = Role.Switch,
                                onValueChange = { pollViewModel.expirationDateEnabled = it })
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = CenterVertically
                    ) {
                        Text( modifier = Modifier.weight(1f, true),
                            text = stringResource(R.string.label_poll_add_expiration_date))
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = pollViewModel.expirationDateEnabled,
                            onCheckedChange = null,
                            colors = olvidSwitchDefaults()
                        )
                    }

                    AnimatedVisibility(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        visible = pollViewModel.expirationDateEnabled
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = 8.dp),
                                color = colorResource(R.color.lightGrey)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = CenterVertically
                            ) {
                                Text(modifier = Modifier.weight(1f, true),
                                    text = stringResource(R.string.label_poll_expiration_date))
                                Text(
                                    modifier = Modifier
                                        .background(
                                            color = colorResource(R.color.lightGrey),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                        ) { pollViewModel.toggleDatePicker() }
                                        .padding(8.dp),
                                    text = StringUtils.getDayOfDateString(
                                        context,
                                        pollViewModel.expirationDate
                                    ).toString()
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    modifier = Modifier
                                        .background(
                                            color = colorResource(R.color.lightGrey),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                        ) { pollViewModel.toggleTimePicker() }
                                        .padding(8.dp),
                                    text = DateUtils.formatDateTime(
                                        context,
                                        pollViewModel.expirationDate,
                                        DateUtils.FORMAT_SHOW_TIME
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (pollViewModel.showDatePicker) {
                        val datePickerState =
                            rememberDatePickerState(
                                initialSelectedDateMillis = pollViewModel.expirationDate,
                                selectableDates = object : SelectableDates {
                                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                                        return utcTimeMillis > System.currentTimeMillis() - 86_400_000
                                    }

                                    override fun isSelectableYear(year: Int): Boolean {
                                        return year >= LocalDate.now().year
                                    }
                                })
                        DatePickerDialog(
                            modifier = Modifier.padding(top = 16.dp),
                            shape = RoundedCornerShape(24.dp),
                            onDismissRequest = { pollViewModel.toggleDatePicker() },
                            confirmButton = {
                                OlvidTextButton(
                                    text = stringResource(R.string.button_label_ok),
                                    onClick = {
                                        datePickerState.selectedDateMillis?.let {
                                            val selectedDate = Calendar.getInstance()
                                            selectedDate.timeInMillis = it
                                            val expirationDateCalendar = Calendar.getInstance()
                                            expirationDateCalendar.timeInMillis =
                                                pollViewModel.expirationDate
                                            if (selectedDate.sameDay(expirationDateCalendar).not()) {
                                                expirationDateCalendar.set(
                                                    selectedDate.get(Calendar.YEAR),
                                                    selectedDate.get(Calendar.MONTH),
                                                    selectedDate.get(Calendar.DAY_OF_MONTH)
                                                )
                                                pollViewModel.expirationDate =
                                                    expirationDateCalendar.timeInMillis
                                            }
                                        }
                                        pollViewModel.toggleDatePicker()
                                    }
                                )
                            },
                            dismissButton = {
                                OlvidTextButton(
                                    text = stringResource(R.string.button_label_cancel),
                                    onClick = { pollViewModel.toggleDatePicker() }
                                )
                            },
                            colors = olvidDatePickerDefaults()
                        ) {
                            val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
                            DatePicker(
                                state = datePickerState,
                                title = null,
                                dateFormatter = dateFormatter,
                                headline = {
                                    DatePickerDefaults.DatePickerHeadline(
                                        selectedDateMillis = datePickerState.selectedDateMillis,
                                        displayMode = datePickerState.displayMode,
                                        dateFormatter = dateFormatter,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                    )
                                },
                                showModeToggle = false,
                                colors = olvidDatePickerDefaults()
                            )
                        }
                    }
                    if (pollViewModel.showTimePicker) {
                        val currentTime = Calendar.getInstance()
                        currentTime.timeInMillis = pollViewModel.expirationDate
                        val timePickerState = rememberTimePickerState(
                            initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
                            initialMinute = currentTime.get(Calendar.MINUTE),
                        )
                        TimePickerDialog(
                            onDismiss = { pollViewModel.toggleTimePicker() },
                            onConfirm = {
                                pollViewModel.expirationDate = currentTime.timeInMillis
                                pollViewModel.toggleTimePicker()
                            }
                        ) {
                            LaunchedEffect(timePickerState.hour, timePickerState.minute) {
                                currentTime.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                currentTime.set(Calendar.MINUTE, timePickerState.minute)
                            }
                            TimeInput(
                                state = timePickerState,
                                colors = olvidTimeInputDefaults()
                            )
                        }
                    }
                }
                AccessibilityFakeButton(
                    modifier = Modifier.height(16.dp),
                    text = stringResource(R.string.button_label_send),
                    onClick = {
                        pollViewModel.sendPoll(context)
                        onBackPressed()
                    },
                    enabled = pollViewModel.canSendPoll()
                )
            }
        }
    }
}

@Composable
private fun SendButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    TextButton(
        modifier = Modifier.semantics {
            onClick(label = context.getString(R.string.button_label_send)) {
                onClick()
                true
            }
        },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = colorResource(R.color.olvid_gradient_light),
            disabledContentColor = colorResource(R.color.olvid_gradient_light).copy(alpha = .5f)
        ),
        onClick = onClick,
        enabled = enabled
    ) {
        Text(
            text = stringResource(R.string.button_label_send),
            maxLines = 1,
            style = OlvidTypography.h2.copy(fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_send),
            contentDescription = null,
        )
    }
}

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        shape = RoundedCornerShape(24.dp),
        containerColor = colorResource(R.color.dialogBackground),
        onDismissRequest = onDismiss,
        dismissButton = {
            OlvidTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.button_label_cancel),
            )
        },
        confirmButton = {
            OlvidTextButton(
                onClick = onConfirm,
                text = stringResource(R.string.button_label_ok),
            )
        },
        text = {
            // we override the MaterialTheme as the olvidTimeInputDefaults() are not enough to change some elements
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    onSurfaceVariant = colorResource(R.color.darkGrey),
                    outline = colorResource(R.color.dialogBackground),
                    primary = colorResource(R.color.dialogBackground),
                )
            ) {
                content()
            }
        }
    )
}

@Composable
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
fun PollScreenPreview() {
    PollCreationScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
fun TimePickerPreview() {
    val currentTime = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colorResource(R.color.dialogBackground))
            .padding(24.dp)
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                onSurfaceVariant = colorResource(R.color.darkGrey),
                outline = colorResource(R.color.dialogBackground),
                primary = colorResource(R.color.dialogBackground),
            )
        ) {
            TimeInput(
                state = timePickerState,
                colors = olvidTimeInputDefaults()
            )
        }
    }
}

fun Calendar.sameDay(calendar: Calendar): Boolean {
    return get(Calendar.YEAR) == calendar.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == calendar.get(
        Calendar.DAY_OF_YEAR
    )
}