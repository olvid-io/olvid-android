/*
 *  Olvid for Android
 *  Copyright © 2019-2026 Olvid SAS
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

package io.olvid.messenger.discussion.search

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.PreviewUtils
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao.FyleAndStatus
import io.olvid.messenger.designsystem.components.CustomDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.designsystem.theme.olvidDefaultTextFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

@Composable
fun DiscussionCalendarDialog(
    initialDateMillis: Long?,
    attachmentsByDate: Map<Long, FyleAndStatus>,
    onDateSelected: (Long) -> Unit,
    onMessageSelected: (Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    val locale = LocalLocale.current.platformLocale
    val weekFields = remember(locale) { WeekFields.of(locale) }
    val firstDayOfWeek = remember(weekFields) { weekFields.firstDayOfWeek }
    val orderedDaysOfWeek = remember(firstDayOfWeek) {
        val days = DayOfWeek.entries.toMutableList()
        while (days.first() != firstDayOfWeek) {
            days.add(days.removeAt(0))
        }
        days
    }

    val baseMonth = remember {
        initialDateMillis?.let {
            val zonedDateTime = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC)
            YearMonth.of(zonedDateTime.year, zonedDateTime.month)
        } ?: YearMonth.now()
    }
    val initialPage = Int.MAX_VALUE / 2
    val pagerState = rememberPagerState(initialPage = initialPage) { Int.MAX_VALUE }
    var currentMonth by remember { mutableStateOf(baseMonth) }
    val scope = rememberCoroutineScope()

    // Keep currentMonth in sync when user swipes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentMonth = baseMonth.plusMonths((page - initialPage).toLong())
        }
    }

    var showYearMonthPicker by remember { mutableStateOf(false) }
    var textInputMode by remember { mutableStateOf(false) }
    val datePattern = remember(locale) {
        DateFormat.getBestDateTimePattern(locale, "ddMMyyyy")
    }
    val dateFormatter = remember(datePattern, locale) {
        DateTimeFormatter.ofPattern(datePattern, locale)
    }
    val todayDigits = remember(dateFormatter) {
        LocalDate.now().format(dateFormatter).filter { it.isDigit() }
    }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(todayDigits, TextRange(0, todayDigits.length)))
    }

    DialogSecure(
        onDismissRequest = onDismissRequest,
    ) {
        CustomDialogContent(
            content = {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    // Header row — always visible
                    Crossfade(
                        targetState = textInputMode
                    ) { textInput ->
                        if (textInput) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    modifier = Modifier.padding(start = 16.dp).weight(1f, true),
                                    text = stringResource(id = R.string.dialog_title_go_to_date),
                                    style = OlvidTypography.h2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                IconButton(onClick = {
                                    textInputMode = !textInputMode
                                    showYearMonthPicker = false
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_date),
                                        contentDescription = null,
                                        tint = colorResource(R.color.almostBlack),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                        contentDescription = null
                                    )
                                }

                                // Clickable month/year label with dropdown indicator
                                Row(
                                    modifier = Modifier
                                        .weight(1f, true)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(
                                            indication = ripple(),
                                            interactionSource = remember { MutableInteractionSource() },
                                        ) {
                                            showYearMonthPicker = !showYearMonthPicker
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${
                                            currentMonth.month.getDisplayName(TextStyle.SHORT, locale)
                                                .replaceFirstChar { it.titlecase(locale) }
                                        } ${currentMonth.year}",
                                        style = OlvidTypography.h2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(onClick = {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }

                                IconButton(onClick = {
                                    textInputMode = !textInputMode
                                    showYearMonthPicker = false
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_pencil),
                                        contentDescription = null,
                                        tint = colorResource(R.color.almostBlack),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Year/Month picker dropdown (calendar mode only)
                    AnimatedVisibility(visible = showYearMonthPicker && !textInputMode) {
                        YearMonthPicker(
                            currentMonth = currentMonth,
                            onYearMonthSelected = { selected ->
                                val offset = baseMonth.until(selected, ChronoUnit.MONTHS).toInt()
                                scope.launch { pagerState.animateScrollToPage(initialPage + offset) }
                                showYearMonthPicker = false
                            }
                        )
                    }


                    var okPressCounts by remember { mutableIntStateOf(0) }

                    // Animated transition between calendar and text input
                    AnimatedContent(
                        modifier = Modifier.weight(1f, false).verticalScroll(state = rememberScrollState()),
                        targetState = textInputMode,
                        label = "calendarModeSwitch"
                    ) { isTextInput ->
                        if (isTextInput) {
                            DateInputView(
                                textFieldValue = textFieldValue,
                                onValueChange = { textFieldValue = it },
                                datePattern = datePattern,
                                dateFormatter = dateFormatter,
                                okPressCounts = okPressCounts,
                                onDateConfirmed = { date ->
                                    val selected = YearMonth.of(date.year, date.month)
                                    val offset = baseMonth.until(selected, ChronoUnit.MONTHS).toInt()
                                    scope.launch { pagerState.scrollToPage(initialPage + offset) }
                                    val timestamp = date.atStartOfDay()
                                        .toInstant(ZoneOffset.UTC).toEpochMilli()
                                    onDateSelected(timestamp)
                                    onDismissRequest()
                                }
                            )
                        } else {
                            Column {
                                // Days of week header (localized)
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                    orderedDaysOfWeek.forEach { day ->
                                        Text(
                                            text = day.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.Center,
                                            style = OlvidTypography.body2,
                                            color = colorResource(R.color.greyTint)
                                        )
                                    }
                                }
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                ) { page ->
                                    val month = baseMonth.plusMonths((page - initialPage).toLong())
                                    CalendarGrid(
                                        month = month,
                                        firstDayOfWeek = firstDayOfWeek,
                                        attachmentsByDate = attachmentsByDate,
                                        onDateSelected = onDateSelected,
                                        onMessageSelected = onMessageSelected,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OlvidTextButton(
                            text = stringResource(id = R.string.button_label_cancel),
                            onClick = onDismissRequest
                        )

                        AnimatedVisibility(
                            visible = textInputMode
                        ) {
                            OlvidTextButton(
                                modifier = Modifier.padding(start = 8.dp),
                                text = stringResource(id = R.string.button_label_ok),
                                onClick = { okPressCounts++ }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DateInputView(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    datePattern: String,
    dateFormatter: DateTimeFormatter,
    okPressCounts: Int,
    onDateConfirmed: (LocalDate) -> Unit
) {
    val initialOkPressCount = remember { okPressCounts }
    val focusRequester = remember { FocusRequester() }
    var isError by remember { mutableStateOf(false) }
    val mask = remember(datePattern) {
        datePattern.map { c -> if (c in "dMyHhmsS") '#' else c }.joinToString("")
    }
    val maxDigits = remember(mask) { mask.count { it == '#' } }
    fun applyMask(digits: String) = buildString {
        var i = 0
        for (ch in mask) {
            if (i >= digits.length) break
            if (ch == '#') append(digits[i++]) else append(ch)
        }
    }

    val confirmDate: () -> Unit = {
        runCatching {
            onDateConfirmed(LocalDate.parse(applyMask(textFieldValue.text), dateFormatter))
        }.onFailure {
            isError = true
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(okPressCounts) {
        if (okPressCounts != initialOkPressCount) {
            confirmDate()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                val digits = it.text.filter { c -> c.isDigit() }.take(maxDigits)
                onValueChange(TextFieldValue(digits, TextRange(digits.length)))
                isError = false
            },
            textStyle = OlvidTypography.body1,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text(datePattern) },
            singleLine = true,
            isError = isError,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = remember(mask) { DateMaskTransformation(mask) },
            colors = olvidDefaultTextFieldColors(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    confirmDate()
                }
            )
        )
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    attachmentsByDate: Map<Long, FyleAndStatus>,
    onDateSelected: (Long) -> Unit,
    onMessageSelected: (Long) -> Unit,
) {
    val daysInMonth = month.lengthOfMonth()
    val firstDayValue = month.atDay(1).dayOfWeek.value
    val firstDowValue = firstDayOfWeek.value
    val emptyDays = (firstDayValue - firstDowValue + 7) % 7

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(emptyDays) {
            Box(modifier = Modifier.aspectRatio(1f))
        }
        items(daysInMonth) { day ->
            val localDate = month.atDay(day + 1)
            val timestamp =
                localDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val attachment = attachmentsByDate[timestamp]
            val isVideo = attachment?.fyleMessageJoinWithStatus
                ?.getNonNullMimeType()?.startsWith("video/") == true

            // preview
            val context = LocalContext.current
            if (attachment != null) {
                val fyleId = attachment.fyleMessageJoinWithStatus.fyleId
                val filePath = attachment.fyle.filePath
                LaunchedEffect(fyleId, filePath) {
                    launch(Dispatchers.IO) {
                        PreviewUtils.getBitmapPreview(
                            attachment.fyle,
                            attachment.fyleMessageJoinWithStatus,
                            1
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .clickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        if (attachment != null) {
                            onMessageSelected(attachment.fyleMessageJoinWithStatus.messageId)
                        } else {
                            onDateSelected(timestamp)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (attachment != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(attachment.deterministicContentUriForGallery)
                            .build(),
                        imageLoader = App.imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorResource(R.color.blackSubtleOverlay))
                    )
                    if (isVideo) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = null,
                            tint = colorResource(R.color.alwaysWhiteOverlay),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = (day + 1).toString(),
                    style = OlvidTypography.body1.copy(
                        fontWeight = if (attachment != null)
                            FontWeight.Medium
                        else
                            FontWeight.Normal
                    ),
                    color = if (attachment != null)
                        colorResource(R.color.alwaysWhite)
                    else
                        colorResource(R.color.almostBlack)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearMonthPicker(
    currentMonth: YearMonth,
    onYearMonthSelected: (YearMonth) -> Unit
) {
    val currentYear = YearMonth.now().year
    val years = remember { (2007..currentYear).toList().reversed() } // 2007 is the year Android was first released
    val months = remember { (1..12).toList() }
    val locale = LocalLocale.current.platformLocale

    var selectedYear by remember(currentMonth) { mutableIntStateOf(currentMonth.year) }
    var selectedMonthValue by remember(currentMonth) { mutableIntStateOf(currentMonth.monthValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 8.dp, end = 8.dp),
    ) {
        // Month selector
        var monthExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            modifier = Modifier.weight(1.3f, true),
            expanded = monthExpanded,
            onExpandedChange = { monthExpanded = it }
        ) {
            OutlinedTextField(
                value = java.time.Month.of(selectedMonthValue)
                    .getDisplayName(TextStyle.FULL, locale)
                    .replaceFirstChar { it.titlecase(locale) },
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                textStyle = OlvidTypography.body1,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                singleLine = true,
                colors = olvidDefaultTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = monthExpanded,
                containerColor = colorResource(R.color.dialogBackground),
                shape = RoundedCornerShape(8.dp),
                onDismissRequest = { monthExpanded = false }
            ) {
                months.forEach { monthValue ->
                    OlvidDropdownMenuItem(
                        text = java.time.Month.of(monthValue)
                            .getDisplayName(TextStyle.FULL, locale)
                            .replaceFirstChar { it.titlecase(locale) },
                        onClick = {
                            selectedMonthValue = monthValue
                            monthExpanded = false
                            onYearMonthSelected(YearMonth.of(selectedYear, selectedMonthValue))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))


        // Year selector
        var yearExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            modifier = Modifier.weight(1f, true),
            expanded = yearExpanded,
            onExpandedChange = { yearExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedYear.toString(),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                textStyle = OlvidTypography.body1,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                singleLine = true,
                colors = olvidDefaultTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = yearExpanded,
                containerColor = colorResource(R.color.dialogBackground),
                shape = RoundedCornerShape(8.dp),
                onDismissRequest = { yearExpanded = false }
            ) {
                years.forEach { year ->
                    OlvidDropdownMenuItem(
                        text = year.toString(),
                        onClick = {
                            selectedYear = year
                            yearExpanded = false
                            onYearMonthSelected(YearMonth.of(selectedYear, selectedMonthValue))
                        }
                    )
                }
            }
        }
    }
}

private class DateMaskTransformation(private val mask: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = buildString {
            var i = 0
            for (ch in mask) {
                if (i >= digits.length) break
                if (ch == '#') append(digits[i++]) else append(ch)
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, digits.length)
                if (clamped == digits.length) return out.length
                var remaining = clamped
                for (i in mask.indices) {
                    if (mask[i] == '#') {
                        if (remaining == 0) return i
                        remaining--
                    }
                }
                return out.length
            }
            override fun transformedToOriginal(offset: Int): Int =
                mask.take(offset.coerceAtMost(out.length)).count { it == '#' }
                    .coerceAtMost(digits.length)
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
