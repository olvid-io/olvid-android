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

package io.olvid.messenger.designsystem.theme

import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import io.olvid.messenger.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import io.olvid.messenger.discussion.message.attachments.constantSp


@Composable
fun olvidDefaultTextFieldColors() = TextFieldDefaults.colors(
    cursorColor = colorResource(R.color.olvid_gradient_light),
    focusedTextColor = colorResource(R.color.almostBlack),
    focusedContainerColor = Color.Transparent,
    focusedLabelColor = colorResource(R.color.olvid_gradient_light),
    focusedIndicatorColor = colorResource(R.color.olvid_gradient_light),
    unfocusedTextColor = colorResource(R.color.almostBlack),
    unfocusedContainerColor = Color.Transparent,
    unfocusedLabelColor = colorResource(R.color.greyTint),
    unfocusedIndicatorColor = colorResource(R.color.greyTint),
    disabledTextColor = colorResource(R.color.lightGrey),
    disabledContainerColor = Color.Transparent,
    disabledLabelColor = colorResource(R.color.lightGrey),
    disabledIndicatorColor = colorResource(R.color.lightGrey),
    errorTextColor = colorResource(R.color.red),
    errorContainerColor = Color.Transparent,
    errorLabelColor = colorResource(R.color.red),
    errorIndicatorColor = colorResource(R.color.red),
    errorLeadingIconColor = colorResource(R.color.red),
    errorTrailingIconColor = colorResource(R.color.red),
    errorPlaceholderColor = colorResource(R.color.greyTint),
    unfocusedPlaceholderColor = colorResource(R.color.greyTint),
    focusedPlaceholderColor = colorResource(R.color.greyTint),
    disabledPlaceholderColor = colorResource(R.color.greyTint),
)

@Composable
fun olvidGreyBackgroundTextFieldColors() = TextFieldDefaults.colors(
    cursorColor = colorResource(R.color.olvid_gradient_light),
    focusedTextColor = colorResource(R.color.almostBlack),
    focusedContainerColor = colorResource(R.color.lighterGrey),
    focusedLabelColor = Color.Transparent,
    focusedIndicatorColor = colorResource(R.color.olvid_gradient_light),
    unfocusedTextColor = colorResource(R.color.almostBlack),
    unfocusedContainerColor = colorResource(R.color.lighterGrey),
    unfocusedLabelColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledTextColor = colorResource(R.color.mediumGrey),
    disabledContainerColor = colorResource(R.color.lighterGrey),
    disabledLabelColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorTextColor = colorResource(R.color.red),
    errorContainerColor = colorResource(R.color.lighterGrey),
    errorLabelColor = colorResource(R.color.red),
    errorIndicatorColor = colorResource(R.color.red),
    errorLeadingIconColor = colorResource(R.color.red),
    errorTrailingIconColor = colorResource(R.color.red),
    errorPlaceholderColor = colorResource(R.color.greyTint),
    unfocusedPlaceholderColor = colorResource(R.color.greyTint),
    focusedPlaceholderColor = colorResource(R.color.greyTint),
    disabledPlaceholderColor = colorResource(R.color.greyTint),
)

@Composable
fun olvidSwitchDefaults() = SwitchDefaults.colors(
    uncheckedTrackColor = colorResource(R.color.almostWhite),
    uncheckedBorderColor = colorResource(R.color.darkGrey),
    checkedTrackColor = colorResource(R.color.olvid_gradient_light),
    checkedBorderColor = colorResource(R.color.olvid_gradient_light),
    checkedThumbColor = colorResource(R.color.alwaysWhite),
    uncheckedThumbColor = colorResource(R.color.greyTint),
    checkedIconColor = colorResource(R.color.olvid_gradient_light),
    uncheckedIconColor = colorResource(R.color.almostWhite),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun olvidDatePickerDefaults() = DatePickerDefaults.colors(
    containerColor = colorResource(R.color.dialogBackground),
    titleContentColor = colorResource(R.color.almostBlack),
    headlineContentColor = colorResource(R.color.almostBlack),
    weekdayContentColor = colorResource(R.color.almostBlack),
    subheadContentColor = colorResource(R.color.almostBlack),
    dayContentColor = colorResource(R.color.almostBlack),
    dayInSelectionRangeContentColor = colorResource(R.color.alwaysWhite),
    dayInSelectionRangeContainerColor = colorResource(R.color.olvid_gradient_light),
    selectedYearContainerColor = colorResource(R.color.olvid_gradient_light),
    selectedYearContentColor = colorResource(R.color.alwaysWhite),
    yearContentColor = colorResource(R.color.almostBlack),
    selectedDayContainerColor = colorResource(R.color.olvid_gradient_light),
    selectedDayContentColor = colorResource(R.color.alwaysWhite),
    todayContentColor = colorResource(R.color.olvid_gradient_light),
    navigationContentColor = colorResource(R.color.almostBlack),
    todayDateBorderColor = Color.Transparent,
    disabledYearContentColor = colorResource(R.color.mediumGrey),
    disabledDayContentColor = colorResource(R.color.mediumGrey),
    dateTextFieldColors = olvidDefaultTextFieldColors()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun olvidTimeInputDefaults() = TimePickerDefaults.colors(
    selectorColor = colorResource(R.color.almostBlack),
    containerColor = Color.Transparent,
    timeSelectorSelectedContainerColor = colorResource(R.color.olvid_gradient_light),
    timeSelectorSelectedContentColor = colorResource(R.color.alwaysWhite),
    timeSelectorUnselectedContainerColor = colorResource(R.color.lightGrey),
    timeSelectorUnselectedContentColor = colorResource(R.color.almostBlack),
    periodSelectorBorderColor = colorResource(R.color.transparent),
    periodSelectorSelectedContainerColor = colorResource(R.color.olvid_gradient_light),
    periodSelectorSelectedContentColor = colorResource(R.color.alwaysWhite),
    periodSelectorUnselectedContainerColor = colorResource(R.color.lightGrey),
    periodSelectorUnselectedContentColor = colorResource(R.color.almostBlack),
)

@Composable
fun backupKeyStyle(): TextStyle {
    return TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = constantSp(20),
        lineHeight = constantSp(28),
        fontFamily = FontFamily.Monospace,
        textDirection = TextDirection.ContentOrLtr,
        textAlign = TextAlign.Center,
    )
}
