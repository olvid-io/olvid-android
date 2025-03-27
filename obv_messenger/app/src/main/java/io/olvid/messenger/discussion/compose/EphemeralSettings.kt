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

package io.olvid.messenger.discussion.compose

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.designsystem.theme.OlvidTypography

private val timeUnits = mapOf(
    R.string.text_unit_s to 1L,
    R.string.text_unit_m to 60L,
    R.string.text_unit_h to 3_600L,
    R.string.text_unit_d to 86_400L,
    R.string.text_unit_y to 31_536_000L,
)

private val presetExistenceChoices = mapOf(
    R.string.pref_text_duration_null to null,
    R.string.pref_text_duration_1d to 86_400L,
    R.string.pref_text_duration_7d to 604_800L,
    R.string.pref_text_duration_30d to 2_592_000L,
)

private val presetVisibilityChoices = mapOf(
    R.string.pref_text_duration_null to null,
    R.string.pref_text_duration_5s to 5L,
    R.string.pref_text_duration_10s to 10L,
    R.string.pref_text_duration_30s to 30L,
)

@Composable
private fun getSettings(choices: Map<Int, Long?>, maxValue: Long?): List<Setting> {
    return choices.entries
        .map { entry ->
            Setting(
                label = stringResource(id = entry.key),
                value = entry.value,
                enabled = (entry.value ?: Long.MAX_VALUE) <= (maxValue
                    ?: Long.MAX_VALUE)
            )
        }
}

@Composable
fun EphemeralSettingsGroup(
    ephemeralViewModel: EphemeralViewModel,
    expanded: Boolean?, // set to null to have the preference layout
    locked: Boolean = false,
    dismiss: (() -> Unit)? = null,
) {
    AppCompatTheme {
        val discussionJsonExpiration by ephemeralViewModel.discussionJsonExpirationLiveData.observeAsState()
        val isValid by ephemeralViewModel.getValid().observeAsState()

        val draftLoaded by ephemeralViewModel.getDraftLoaded()
            .observeAsState()
        val defaultsLoaded by ephemeralViewModel.getDefaultsLoaded()
            .observeAsState()

        LaunchedEffect(draftLoaded) {
            if (ephemeralViewModel.configuringDiscussionCustomization.not() && draftLoaded == true) {
                ephemeralViewModel.draftJsonExpiration?.apply {
                    ephemeralViewModel.setReadOnce(readOnce ?: false)
                    ephemeralViewModel.setVisibility(visibilityDuration)
                    ephemeralViewModel.setExistence(existenceDuration)
                }
            }
        }

        LaunchedEffect(defaultsLoaded) {
            if (ephemeralViewModel.configuringDiscussionCustomization && defaultsLoaded == true) {
                discussionJsonExpiration?.apply {
                    ephemeralViewModel.setReadOnce(readOnce ?: false)
                    ephemeralViewModel.setVisibility(visibilityDuration)
                    ephemeralViewModel.setExistence(existenceDuration)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {

            var expandVisibility by rememberSaveable {
                mutableStateOf(false)
            }
            var expandExistence by rememberSaveable {
                mutableStateOf(false)
            }
            var expandCustom by rememberSaveable {
                mutableStateOf(false)
            }
            var expandCustomIsExistence by rememberSaveable {
                mutableStateOf(false)
            }
            var expandTimeUnit by rememberSaveable {
                mutableStateOf(false)
            }
            val baseOffset = if (expanded == null) IntOffset(0, -24) else IntOffset(-32, -40)


            if (expanded == true) {
                Popup(
                    alignment = Alignment.BottomEnd,
                    offset = with(LocalDensity.current) {
                        IntOffset(x = baseOffset.x.dp.roundToPx(), y = baseOffset.y.dp.roundToPx())
                    },
                    properties = PopupProperties(focusable = true),
                    onDismissRequest = dismiss
                ) {
                    EphemeralSettingsPopupLayout(
                        ephemeralViewModel = ephemeralViewModel,
                        onVisibilityClick = { expandVisibility = true },
                        onExistenceClick = { expandExistence = true },
                        readOnceDisabled = ephemeralViewModel.configuringDiscussionCustomization.not() && discussionJsonExpiration?.readOnce == true
                    )
                }
            } else if (expanded == null) {
                EphemeralSettingsPreferenceLayout(
                    ephemeralViewModel = ephemeralViewModel,
                    onVisibilityClick = { expandVisibility = true },
                    onExistenceClick = { expandExistence = true },
                    locked = locked,
                )
            }

            if (expandVisibility) {
                EphemeralSetting(
                    settings = getSettings(choices = presetVisibilityChoices, maxValue = discussionJsonExpiration?.visibilityDuration?.takeIf { ephemeralViewModel.configuringDiscussionCustomization.not() }),
                    selected = ephemeralViewModel.getVisibility(),
                    offset = with(LocalDensity.current) {
                        IntOffset(baseOffset.x.dp.roundToPx(), (baseOffset.y - 88).dp.roundToPx())
                    },
                    applySetting = {
                        ephemeralViewModel.setVisibility(it)
                        expandVisibility = false
                    },
                    onCustomClick = {
                        expandVisibility = false
                        expandCustomIsExistence = false
                        expandCustom = true
                    },
                    dismiss = {
                        expandVisibility = false
                    })
            }
            if (expandExistence) {
                EphemeralSetting(
                    settings = getSettings(choices = presetExistenceChoices, maxValue = discussionJsonExpiration?.existenceDuration?.takeIf { ephemeralViewModel.configuringDiscussionCustomization.not() }),
                    selected = ephemeralViewModel.getExistence(),
                    offset = with(LocalDensity.current) {
                        IntOffset(baseOffset.x.dp.roundToPx(), (baseOffset.y - 32).dp.roundToPx())
                    },
                    applySetting = {
                        ephemeralViewModel.setExistence(it)
                        expandExistence = false
                    },
                    onCustomClick = {
                        expandExistence = false
                        expandCustomIsExistence = true
                        expandCustom = true
                    },
                    dismiss = {
                        expandExistence = false
                    })
            }

            var customInput by remember {
                mutableStateOf(TextFieldValue(""))
            }
            var customValue by rememberSaveable {
                mutableStateOf<Long?>(null)
            }
            var customValueInSecs by rememberSaveable {
                mutableStateOf<Long?>(null)
            }
            var customTooMuch by rememberSaveable {
                mutableStateOf(false)
            }
            var customValid by rememberSaveable {
                mutableStateOf(true)
            }
            var visibilityUnit by rememberSaveable {
                mutableIntStateOf(timeUnits.keys.toList()[1])
            }
            var existenceUnit by rememberSaveable {
                mutableIntStateOf(timeUnits.keys.toList()[3])
            }
            var additionalTimeUnitPopupOffset by remember {
                mutableIntStateOf(0)
            }
            fun checkValidCustom() {
                customValue = customInput.text.toLongOrNull().takeIf { (it ?: 1) > 0 }
                val unit = if (expandCustomIsExistence) existenceUnit else visibilityUnit
                customValueInSecs = customValue?.times(timeUnits[unit] ?: 1L)
                customTooMuch = customValueInSecs?.let {
                    when (unit) {
                        R.string.text_unit_s -> it > 60L
                        R.string.text_unit_m -> it > 3_600L
                        R.string.text_unit_h -> it > 86_400L
                        R.string.text_unit_d -> it > 31_536_000L
                        else -> it > 31_536_000_000L
                    }
                } ?: false
                customValid = (customValueInSecs ?: -1) <= (
                        (if (expandCustomIsExistence) discussionJsonExpiration?.existenceDuration else discussionJsonExpiration?.visibilityDuration)
                            .takeIf { ephemeralViewModel.configuringDiscussionCustomization.not() }
                            ?: Long.MAX_VALUE)
            }

            LaunchedEffect(expandCustom) {
                if (expandCustom) {
                    // after opening custom, set the unit and value to match the current setting
                    val timeInSeconds = if (expandCustomIsExistence) ephemeralViewModel.getExistence() else ephemeralViewModel.getVisibility()
                    customValueInSecs = timeInSeconds
                    if (timeInSeconds == null || timeInSeconds <= 0) {
                        customValue = null
                    } else {
                        // timeInSeconds > 0 so we know last will find something
                        timeUnits.entries.last {
                            it.value <= timeInSeconds
                        }.run {
                            if (expandCustomIsExistence) {
                                existenceUnit = key
                            } else {
                                visibilityUnit = key
                            }
                            customValue = timeInSeconds / value
                        }
                    }

                    customInput = customValue?.let {
                        TextFieldValue(it.toString()).run {
                            copy(selection = TextRange(0, text.length))
                        }
                    } ?: TextFieldValue("")
                    checkValidCustom()
                }
            }

            if (expandCustom) {
                fun dismissCustom() {
                    if (customValid && customTooMuch.not()) {
                        if (expandCustomIsExistence) {
                            ephemeralViewModel.setExistence(customValueInSecs)
                        } else {
                            ephemeralViewModel.setVisibility(customValueInSecs)
                        }
                    }
                    expandCustom = false
                }

                Popup(
                    alignment = Alignment.BottomEnd,
                    offset = with(LocalDensity.current) {
                        IntOffset(
                            x = (baseOffset.x - 4).dp.roundToPx(),
                            y = (baseOffset.y - if (expandCustomIsExistence) 44 else 100).dp.roundToPx()
                        )
                    },
                    properties = PopupProperties(focusable = true),
                    onDismissRequest = {
                        dismissCustom()
                    }) {

                    Surface(
                        elevation = 8.dp, shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    top = 16.dp,
                                    bottom = 16.dp,
                                    start = 16.dp,
                                    end = 10.dp
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val focusRequester = remember {
                                    FocusRequester()
                                }
                                LaunchedEffect(Unit) {
                                    focusRequester.requestFocus()
                                }
                                Text(text = stringResource(id = if (expandCustomIsExistence) R.string.preset_ephemeral_existence_duration else R.string.preset_ephemeral_visibility_duration))
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    modifier = Modifier
                                        .focusRequester(focusRequester)
                                        .size(width = 60.dp, height = 32.dp)
                                        .border(
                                            width = 1.dp,
                                            color = if (customTooMuch.not() && customValid) Color(0xFFE1E2E9) else Color(
                                                0xFFE2594E
                                            ),
                                            shape = RoundedCornerShape(size = 6.dp)
                                        )
                                        .wrapContentHeight(align = Alignment.CenterVertically),
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 16.sp,
                                        color = if (customTooMuch.not() && customValid) colorResource(id = R.color.almostBlack) else Color(0xFFE2594E),
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardActions = KeyboardActions(onDone = {
                                        dismissCustom()
                                    }),
                                    singleLine = true,
                                    cursorBrush = SolidColor(colorResource(id = R.color.almostBlack)),
                                    value = customInput,
                                    onValueChange = { inputValue ->
                                        customInput = inputValue
                                        checkValidCustom()
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Row(
                                    modifier = Modifier
                                        .requiredHeight(33.dp)
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFFE1E2E9),
                                            shape = RoundedCornerShape(size = 6.dp)
                                        )
                                        .clip(RoundedCornerShape(size = 6.dp))
                                        .clickable {
                                            expandTimeUnit = true
                                        }
                                        .padding(
                                            start = 12.dp,
                                            top = 4.dp,
                                            end = 8.dp,
                                            bottom = 4.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = if (expandCustomIsExistence.not()) visibilityUnit else existenceUnit)
                                    )
                                    Spacer(modifier = Modifier.requiredWidth(4.dp))
                                    Icon(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .requiredSize(18.dp),
                                        painter = painterResource(id = R.drawable.ic_chevron_down),
                                        contentDescription = ""
                                    )
                                }
                            }
                            AnimatedVisibility(visible = customValid.not()) {
                                Text(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .widthIn(max = 240.dp),
                                    text = stringResource(id = R.string.ephemeral_settings_invalid),
                                    style = OlvidTypography.body2,
                                    color = Color(0xFFE2594E),
                                    onTextLayout = {
                                       additionalTimeUnitPopupOffset = it.size.height
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (expandTimeUnit) {
                val additionalOffset = if (isValid == false) additionalTimeUnitPopupOffset else 0
                Popup(alignment = Alignment.BottomEnd,
                    offset = with(LocalDensity.current) {
                        IntOffset(
                            x = (baseOffset.x + 16).dp.roundToPx(),
                            y = (baseOffset.y - if (expandCustomIsExistence) 96 else 152).dp.roundToPx() - additionalOffset
                        )
                    },
                    properties = PopupProperties(focusable = true),
                    onDismissRequest = {
                        expandTimeUnit = false
                    }
                ) {

                    Surface(
                        elevation = 8.dp, shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.width(Min)) {
                            timeUnits.keys.forEachIndexed { index, textId ->
                                Row(modifier = Modifier
                                    .clickable {
                                        if (expandCustomIsExistence.not()) {
                                            visibilityUnit = textId
                                        } else {
                                            existenceUnit = textId
                                        }
                                        checkValidCustom()
                                        expandTimeUnit = false
                                    }
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .weight(1f, true),
                                        text = stringResource(id = textId)
                                    )
                                }
                                Divider(color = Color(0x1F111111)).takeIf { index < 4 }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EphemeralSettingsPopupLayout(
    modifier: Modifier = Modifier,
    ephemeralViewModel: EphemeralViewModel,
    onVisibilityClick: () -> Unit,
    onExistenceClick: () -> Unit,
    readOnceDisabled: Boolean
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .widthIn(max = 300.dp),
        elevation = 8.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            val interactionSource = remember {
                MutableInteractionSource()
            }
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .then(
                        if (readOnceDisabled) Modifier
                            .alpha(0.5f)
                            .padding(horizontal = 16.dp)
                        else Modifier
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current
                            ) {
                                ephemeralViewModel.setReadOnce(!ephemeralViewModel.getReadOnce())
                            }
                            .padding(horizontal = 16.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_zap),
                    tint = Color(0xFF8B8D97),
                    contentDescription = stringResource(id = R.string.preset_ephemeral_read_once)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.preset_ephemeral_read_once),
                    style = OlvidTypography.body1
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    enabled = readOnceDisabled.not(),
                    checked = ephemeralViewModel.getReadOnce(),
                    onCheckedChange = { ephemeralViewModel.setReadOnce(it) },
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = colorResource(id = R.color.alwaysLightGrey),
                        checkedThumbColor = colorResource(id = R.color.olvid_gradient_light),
                    )
                )
            }
            Divider(color = Color(0x1F111111))
            Row(
                modifier = Modifier
                    .clickable {
                        onVisibilityClick()
                    }
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_eye),
                    tint = Color(0xFF8B8D97),
                    contentDescription = stringResource(id = R.string.preset_ephemeral_visibility_duration)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.preset_ephemeral_visibility_duration),
                    style = OlvidTypography.body1
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ephemeralViewModel.getVisibility()?.let {
                        StringUtils.getNiceDurationString(
                            LocalContext.current, it
                        )
                    } ?: stringResource(id = R.string.pref_text_duration_null),
                    style = OlvidTypography.body1.copy(color = Color(0xFF8B8D97))
                )
                Icon(
                    modifier = Modifier
                        .padding(top = 4.dp, start = 4.dp)
                        .requiredSize(18.dp),
                    painter = painterResource(id = R.drawable.ic_chevron_down),
                    tint = Color(0xFF8B8D97),
                    contentDescription = ""
                )
            }
            Divider(color = Color(0x1F111111))
            Row(
                modifier = Modifier
                    .clickable {
                        onExistenceClick()
                    }
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_clock),
                    tint = Color(0xFF8B8D97),
                    contentDescription = stringResource(id = R.string.preset_ephemeral_existence_duration)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.preset_ephemeral_existence_duration),
                    style = OlvidTypography.body1
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ephemeralViewModel.getExistence()?.let {
                        StringUtils.getNiceDurationString(
                            LocalContext.current, it
                        )
                    } ?: stringResource(id = R.string.pref_text_duration_null),
                    style = OlvidTypography.body1.copy(color = Color(0xFF8B8D97))
                )
                Icon(
                    modifier = Modifier
                        .padding(top = 4.dp, start = 4.dp)
                        .requiredSize(18.dp),
                    painter = painterResource(id = R.drawable.ic_chevron_down),
                    tint = Color(0xFF8B8D97),
                    contentDescription = ""
                )
            }
        }
    }
}



@Composable
fun EphemeralSettingsPreferenceLayout(
    modifier: Modifier = Modifier,
    ephemeralViewModel: EphemeralViewModel,
    onVisibilityClick: () -> Unit,
    onExistenceClick: () -> Unit,
    locked: Boolean,
) {

    Column (
        modifier = modifier.fillMaxWidth()
    ) {
        val interactionSource = remember {
            MutableInteractionSource()
        }
        Row(
            modifier = Modifier
                .then(
                    if (locked) Modifier
                        .alpha(0.5f)
                    else Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current
                        ) {
                            ephemeralViewModel.setReadOnce(!ephemeralViewModel.getReadOnce())
                        }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.requiredSize(32.dp),
                painter = painterResource(id = R.drawable.ic_settings_zap),
                tint = colorResource(id = R.color.greyTint),
                contentDescription = stringResource(id = R.string.preset_ephemeral_read_once)
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(id = R.string.preset_ephemeral_read_once),
                    style = OlvidTypography.body1
                )
                Text(
                    text = stringResource(id = R.string.preset_ephemeral_read_once_explanation),
                    style = OlvidTypography.body2.copy(color = colorResource(id = R.color.greyTint))
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = ephemeralViewModel.getReadOnce(),
                onCheckedChange = { ephemeralViewModel.setReadOnce(it) },
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = colorResource(id = R.color.alwaysLightGrey),
                    checkedThumbColor = colorResource(id = R.color.olvid_gradient_light),
                )
            )
        }
        Row(
            modifier = Modifier
                .then(
                    if (locked) Modifier
                        .alpha(0.5f)
                    else Modifier
                        .clickable {
                            onVisibilityClick()
                        }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.requiredSize(32.dp),
                painter = painterResource(id = R.drawable.ic_settings_eye),
                tint = colorResource(id = R.color.greyTint),
                contentDescription = stringResource(id = R.string.preset_ephemeral_visibility_duration)
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row (verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.preset_ephemeral_visibility_duration),
                        style = OlvidTypography.body1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ephemeralViewModel.getVisibility()?.let {
                            StringUtils.getNiceDurationString(
                                LocalContext.current, it
                            )
                        } ?: stringResource(id = R.string.pref_text_duration_null),
                        style = OlvidTypography.body1.copy(color = if (locked) Color.Unspecified else Color(0xFF8B8D97))
                    )
                    Icon(
                        modifier = Modifier
                            .padding(top = 4.dp, start = 4.dp)
                            .requiredSize(18.dp),
                        painter = painterResource(id = R.drawable.ic_chevron_down),
                        tint = Color(0xFF8B8D97),
                        contentDescription = ""
                    )
                }
                Text(
                    text = stringResource(id = R.string.preset_ephemeral_visibility_duration_explanation),
                    style = OlvidTypography.body2.copy(
                        color = colorResource(id = R.color.greyTint)
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .then(
                    if (locked)
                        Modifier.alpha(0.5f)
                    else Modifier
                        .clickable {
                            onExistenceClick()
                        }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.requiredSize(32.dp),
                painter = painterResource(id = R.drawable.ic_settings_clock),
                tint = colorResource(id = R.color.greyTint),
                contentDescription = stringResource(id = R.string.preset_ephemeral_existence_duration)
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row (verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.preset_ephemeral_existence_duration),
                        style = OlvidTypography.body1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ephemeralViewModel.getExistence()?.let {
                            StringUtils.getNiceDurationString(
                                LocalContext.current, it
                            )
                        } ?: stringResource(id = R.string.pref_text_duration_null),
                        style = OlvidTypography.body1.copy(
                            color = if (locked) Color.Unspecified else Color(0xFF8B8D97),
                        )
                    )
                    Icon(
                        modifier = Modifier
                            .padding(top = 4.dp, start = 4.dp)
                            .requiredSize(18.dp),
                        painter = painterResource(id = R.drawable.ic_chevron_down),
                        tint = Color(0xFF8B8D97),
                        contentDescription = ""
                    )
                }
                Text(
                    text = stringResource(id = R.string.preset_ephemeral_existence_duration_explanation),
                    style = OlvidTypography.body2.copy(
                        color = colorResource(id = R.color.greyTint)
                    )
                )
            }
        }
    }
}




@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
fun EphemeralSettingsPreview() {
    AppCompatTheme {
        EphemeralSettingsPopupLayout(modifier = Modifier.fillMaxWidth(), EphemeralViewModel(), {}, {}, false)
    }
}

private data class Setting(val label: String, val value: Long?, val enabled: Boolean)

@Composable
private fun EphemeralSetting(
    settings: List<Setting>,
    selected: Long? = null,
    offset: IntOffset,
    applySetting: (Long?) -> Unit,
    onCustomClick: () -> Unit,
    dismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.BottomEnd,
        offset = offset,
        properties = PopupProperties(focusable = true),
        onDismissRequest = dismiss
    ) {

        Surface(
            elevation = 8.dp, shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.width(Min)) {
                settings.forEach { setting ->
                    Row(modifier = Modifier
                        .then(
                            if (setting.enabled) {
                                Modifier.clickable {
                                    applySetting(setting.value)
                                }
                            } else {
                                Modifier.alpha(0.4f)
                            })
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text( text = setting.label)
                        if (selected == setting.value) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                modifier = Modifier.requiredSize(20.dp),
                                painter = painterResource(id = R.drawable.ic_ok),
                                tint = colorResource(id = R.color.olvid_gradient_light),
                                contentDescription = null)
                        }
                    }
                    Divider(color = Color(0x1F111111))
                }
                Row(modifier = Modifier
                    .clickable {
                        onCustomClick()
                    }
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.preset_ephemeral_settings_custom)
                    )
                    if (settings.filter { it.value == selected }.isEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            modifier = Modifier.requiredSize(20.dp),
                            painter = painterResource(id = R.drawable.ic_ok),
                            tint = colorResource(id = R.color.olvid_gradient_light),
                            contentDescription = null)
                    }
                }
            }
        }
    }
}
