/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.onboarding.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON
import io.olvid.messenger.onboarding.flow.OnboardingActionType.BUTTON_OUTLINED
import io.olvid.messenger.onboarding.flow.OnboardingActionType.CHOICE
import io.olvid.messenger.onboarding.flow.OnboardingActionType.TEXT
import io.olvid.messenger.onboarding.flow.animations.shake
import io.olvid.messenger.onboarding.flow.animations.shimmer

@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    onBack: (() -> Unit)? = null,
    onClose: () -> Unit,
    scrollable: Boolean = true,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colorResource(id = color.almostWhite))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(BiasAlignment(horizontalBias = 0f, verticalBias = -0.3f))
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OnboardingHeader(step.title, step.subtitle)
            Spacer(modifier = Modifier.height(24.dp))

            content?.let {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    it.invoke(this)
                }
            }

            step.actions.filter { it.type == CHOICE }.forEachIndexed { index, action ->
                OnboardingButton(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = action.label,
                    description = action.description,
                    onClick = action.onClick
                )
                Spacer(modifier = Modifier.height(16.dp)).takeIf { index != step.actions.lastIndex }
            }

            step.actions.filter { it.type == TEXT }.forEach { action ->
                Spacer(modifier = Modifier.height(24.dp))
                ClickableText(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = action.label,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight(400),
                        color = Color(0xFF8B8D97),
                        textAlign = TextAlign.Center
                    )
                ) {
                    action.onClick.invoke()
                }
            }

            step.actions.filter { it.type == BUTTON || it.type == BUTTON_OUTLINED }.takeIf { it.isNotEmpty() }?.run {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(
                        16.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    forEach { action ->
                        if (action.type == BUTTON) {
                            Button(
                                modifier = Modifier.weight(weight = 1f, fill = false).fillMaxHeight(),
                                elevation = null,
                                onClick = action.onClick,
                                enabled = action.enabled
                            ) {
                                action.icon?.let {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(id = it),
                                        contentDescription = ""
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = action.label,
                                    textAlign = if (action.icon == null) TextAlign.Center else TextAlign.Start
                                )
                            }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.weight(weight = 1f, fill = false).fillMaxHeight(),
                                elevation = null,
                                onClick = action.onClick,
                                enabled = action.enabled,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorResource(id = color.blueOrWhite),)
                            ) {
                                action.icon?.let {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(id = it),
                                        contentDescription = ""
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = action.label,
                                    textAlign = if (action.icon == null) TextAlign.Center else TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            onBack?.let {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = drawable.ic_arrow_back),
                        tint = colorResource(id = color.almostBlack),
                        contentDescription = "back"
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(id = drawable.ic_close),
                    tint = colorResource(id = color.almostBlack),
                    contentDescription = "close"
                )
            }
        }

        footer?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.Center
            ) {
                it.invoke()
            }
        }
    }
}

@Composable
private fun OnboardingHeader(title: String, subtitle: String) {
    OlvidLogo()
    if (title.isNotEmpty()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = title,
            style = TextStyle(
                fontSize = 24.sp,
                color = colorResource(id = color.almostBlack),
                textAlign = TextAlign.Center
            )
        )
    }
    if (subtitle.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = subtitle,
            style = TextStyle(
                fontSize = 16.sp,
                color = Color(0xFF8B8D97),
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
fun OnboardingExplanationSteps(steps: List<String>) {
    Column (
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        steps.forEachIndexed { index, s ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${index + 1}.",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B8D97),
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    modifier = Modifier.weight(1f, true),
                    text = s,
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = Color(0xFF8B8D97),
                        textAlign = TextAlign.Start
                    )
                )
            }
        }
    }
}

@Composable
private fun OlvidLogo() {
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(
                shape = RoundedCornerShape(16.dp),
                brush = Brush.verticalGradient(
                    listOf(
                        colorResource(id = color.olvid_gradient_light),
                        colorResource(id = color.olvid_gradient_dark)
                    )
                )
            )
    ) {
        Image(
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp),
            painter = painterResource(id = drawable.icon_olvid_no_padding),
            contentDescription = "Olvid"
        )
    }
}

@Composable
private fun OnboardingButton(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    description: AnnotatedString? = null,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier.widthIn(max = 400.dp),
        border = BorderStroke(1.dp, Color(0x6E111111)),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = if (description == null) 24.dp else 16.dp,
            end = 16.dp,
            bottom = if (description == null) 24.dp else 16.dp
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = true)
        ) {
            Text(
                text = text,
                color = colorResource(id = color.almostBlack),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
            )
            description?.let {
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = description,
                    color = colorResource(id = color.greyTint),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        Icon(
            painter = painterResource(id = drawable.ic_chevron_right),
            tint = colorResource(id = color.almostBlack),
            contentDescription = ""
        )
    }
}

@Composable
fun BoxedCharTextField(
    modifier: Modifier = Modifier,
    text: String,
    count: Int = 8,
    enabled: Boolean = true,
    error: Boolean = false,
    shimmer: Boolean = false,
    onTextChange: (String) -> Unit = { _ -> }
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    BasicTextField(
        modifier = modifier
            .focusRequester(focusRequester)
            .shake(error),
        enabled = enabled,
        value = TextFieldValue(text, selection = TextRange(text.length)),
        onValueChange = {
            if (it.text.length <= count) {
                onTextChange.invoke(it.text)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(modifier = Modifier.widthIn(max = 300.dp),
                horizontalArrangement = Arrangement.Center) {
                repeat(count) { index ->
                    BoxedChar(
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        error = error,
                        shimmer = shimmer,
                        index = index,
                        text = text
                    )
                    if (index != count - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    )
}

@Composable
private fun BoxedChar(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    error: Boolean,
    shimmer: Boolean,
    index: Int,
    text: String
) {
    val isFocused = text.length == index
    val char = when {
        index == text.length -> if (enabled) "_" else ""
        index > text.length -> ""
        else -> text[index].toString()
    }
    Text(
        modifier = modifier
            .border(
                1.dp, if (error) Color(0xFFE2594E) else when {
                    isFocused -> colorResource(id = R.color.blueOrWhite)
                    else -> colorResource(id = R.color.grey)
                }, RoundedCornerShape(12.dp)
            )
            .shimmer(shimmer)
            .padding(vertical = 8.dp),
        text = char,
        style = TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color =
        if (error) {
            Color(0xFFE2594E)
        } else if (isFocused) {
            colorResource(id = R.color.blueOrWhite)
        } else {
            colorResource(id = R.color.almostBlack)
        },
        textAlign = TextAlign.Center
    )
}

//@Composable
//fun BoxScope.Indicators(size: Int, index: Int) {
//    Row(
//        verticalAlignment = Alignment.CenterVertically,
//        horizontalArrangement = Arrangement.spacedBy(12.dp),
//        modifier = Modifier.align(Alignment.CenterStart)
//    ) {
//        repeat(size) {
//            Indicator(isSelected = it == index)
//        }
//    }
//}

//@Composable
//fun Indicator(isSelected: Boolean) {
//    val width = animateDpAsState(
//        targetValue = if (isSelected) 25.dp else 10.dp,
//        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = ""
//    )
//
//    Box(
//        modifier = Modifier
//            .height(10.dp)
//            .width(width.value)
//            .clip(CircleShape)
//            .background(
//                color = if (isSelected) MaterialTheme.colors.primary else Color(0XFFF8E2E7)
//            )
//    ) {
//
//    }
//}

@Preview(showBackground = true)
@Composable
fun OnboardingPreview() {
    AppCompatTheme {
        OnboardingScreen(
            step = OnboardingStep(
                title = "Bien le bonjour !",
                subtitle = "Est-ce qu'on se connaît ?",
                actions = listOf(
                    OnboardingAction(label = AnnotatedString("J'ai un profil Olvid")) {},
                    OnboardingAction(label = AnnotatedString("Je n'ai pas encore de profil Olvid")) {},
                )
            ),
            onBack = {},
            onClose = {}
        )
    }
}

@Preview()
@Composable
fun OnboardingPreview2() {
    AppCompatTheme {
        OnboardingScreen(
            step = OnboardingStep(
                title = "Other onboarding screen",
                actions = listOf(
                    OnboardingAction(label = AnnotatedString("Button 1"), type = BUTTON_OUTLINED) {},
                    OnboardingAction(label = AnnotatedString("Button 2"), type = BUTTON) {},
                )
            ),
            onBack = {},
            onClose = {}
        )
    }
}

