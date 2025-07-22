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

package io.olvid.messenger.troubleshooting

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Build.VERSION
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.main.Utils
import io.olvid.messenger.main.tips.installTimestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun AppVersionHeader(betaEnabled: Boolean) {
    val context = LocalContext.current
    var uptime by remember {
        mutableStateOf(Utils.getUptime(context))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.seconds)
            uptime = Utils.getUptime(context)
        }
    }
    Column {
        Text(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            text =
                buildAnnotatedString {
                    appendLine(stringResource(
                        R.string.troubleshooting_header_app_version,
                        BuildConfig.VERSION_NAME + if (betaEnabled) " beta" else "",
                        BuildConfig.VERSION_CODE))
                    installTimestamp()?.let { timestamp ->
                        appendLine(
                            stringResource(
                                R.string.troubleshooting_header_app_install_date,
                                StringUtils.getDateString(context, timestamp)
                            )
                        )
                    }
                    appendLine(stringResource(
                        R.string.troubleshooting_header_device_model,
                        "${Build.BRAND} ${Build.MODEL}"
                    ))
                    appendLine(stringResource(
                        R.string.troubleshooting_header_android_api,
                        VERSION.SDK_INT
                    ))
                    append(stringResource(
                        R.string.troubleshooting_header_app_running_time,
                        uptime
                    ))
                }.formatMarkdown(),
            color = colorResource(id = R.color.almostBlack),
            style = OlvidTypography.body1,
        )
    }
}

@Composable
fun RestartAppButton() {
    val context = LocalContext.current
    var showConfirmationDialog by remember { mutableStateOf(false) }

    Row (
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .widthIn(max = 400.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colorResource(id = R.color.red)
            ),
            border = BorderStroke(1.dp, colorResource(id = R.color.red)),
            shape = RoundedCornerShape(6.dp),
            onClick = {
                showConfirmationDialog = true
            }
        ) {
            Text(
                text = stringResource(id = R.string.button_label_force_restart)
            )
        }
    }

    if (showConfirmationDialog) {
        Dialog(
            onDismissRequest = {
                showConfirmationDialog = false
            }
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorResource(R.color.dialogBackground))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = spacedBy(8.dp),
                ) {
                    Image(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(R.drawable.ic_restart),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(end = 4.dp),
                        text = stringResource(R.string.dialog_message_restart_app),
                        style = OlvidTypography.body1,
                        color = colorResource(R.color.greyTint),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        modifier = Modifier.height(40.dp),
                        elevation = null,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        onClick = {
                            showConfirmationDialog = false
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.button_label_cancel),
                            color = colorResource(R.color.greyTint),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        modifier = Modifier.height(40.dp),
                        elevation = null,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors().copy(
                            containerColor = colorResource(R.color.red),
                            contentColor = colorResource(R.color.alwaysWhite)
                        ),
                        onClick = {
                            try {
                                val packageManager = context.packageManager
                                val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                                val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
                                mainIntent.setPackage(context.packageName)
                                context.startActivity(mainIntent)
                                Runtime.getRuntime().exit(0)
                            } catch (_: Exception) { }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.button_label_force_restart),
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun FaqLinkHeader(openFaq: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
    ) {
        IconButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 8.dp)
                .size(40.dp),
            onClick = onBack
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = R.drawable.ic_arrow_back),
                tint = colorResource(id = R.color.almostBlack),
                contentDescription = stringResource(R.string.content_description_back_button)
            )
        }

        Text(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 54.dp)
                .padding(top = 14.dp)
                .widthIn(max = 400.dp),
            text = buildAnnotatedString {
                append(stringResource(id = R.string.troubleshooting_faq_description))
                append("\n\n")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "",
                        styles = TextLinkStyles(SpanStyle(color = colorResource(id = R.color.olvid_gradient_light))),
                        linkInteractionListener = { openFaq() }
                    ),
                ) {
                    append(stringResource(id = R.string.troubleshooting_faq_link))
                }
            },
            textAlign = TextAlign.Center,
            color = colorResource(id = R.color.almostBlack),
            style = OlvidTypography.body1,
        )
    }
}



@Composable
fun TroubleShootItem(
    title: String,
    description: String,
    titleInvalid: String? = null,
    descriptionInvalid: String? = null,
    valid: Boolean,
    critical: Boolean = true,
    checkState: CheckState<out Any>? = null,
    additionalContent: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit
) {
    var expanded by rememberSaveable {
        mutableStateOf(valid.not())
    }
    val mute: Boolean by checkState?.isMute?.collectAsState(true) ?: remember { mutableStateOf(false) }
    val borderWidth: Float by animateFloatAsState(targetValue = if (critical && valid.not() && mute == false) 2f else 1f)
    val borderColor: Color by animateColorAsState(targetValue = if (critical && valid.not() && mute == false) colorResource(id = R.color.red) else Color(0x6E111111))
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 400.dp)
            .border(
                border = BorderStroke(borderWidth.dp, borderColor),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = colorResource(id = R.color.itemBackground),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                expanded = !expanded
            }
            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        )
        {
            Column(
                modifier = Modifier.weight(1f, fill = true),
            ) {
                Row {
                    val rotation: Float by animateFloatAsState(targetValue = if (expanded) 90f else 0f)
                    Image(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(12.dp)
                            .rotate(degrees = rotation)
                            .align(Alignment.CenterVertically),
                        painter = painterResource(id = R.drawable.ic_chevron_right_compact),
                        contentDescription = ""
                    )
                    Text(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f, true)
                            .align(Alignment.CenterVertically),
                        text = if (valid) title else titleInvalid ?: title,
                        color = colorResource(id = R.color.almostBlack),
                        style = OlvidTypography.body1,
                    )
                    Image(
                        modifier = Modifier
                            .size(32.dp),
                        painter = painterResource(id = if (valid) R.drawable.ic_ok_green else R.drawable.ic_error_outline),
                        colorFilter = ColorFilter.tint(color = colorResource(id = R.color.golden))
                            .takeIf { valid.not() && critical.not() },
                        contentDescription = ""
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        Text(
                            modifier = Modifier.padding(top = 4.dp, bottom = if (valid && additionalContent == null) 4.dp else 0.dp),
                            text = if (valid) description else descriptionInvalid ?: description,
                            color = colorResource(id = R.color.greyTint),
                            style = OlvidTypography.body2,
                        )
                        additionalContent?.let {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                it()
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = valid.not()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                checkState?.let { checkState ->
                    val coroutineScope = rememberCoroutineScope()
                    val interactionSource = remember { MutableInteractionSource() }
                    Image(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = false)
                            ) { coroutineScope.launch { checkState.updateMute(mute.not()) } },
                        painter = painterResource(id = R.drawable.ic_notification_muted),
                        colorFilter = ColorFilter.tint(colorResource(id = R.color.almostBlack)),
                        contentDescription = "mute",
                        alpha = if (mute) 1f else 0.3f
                    )
                    AnimatedVisibility(visible = mute) {
                        Text(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) { coroutineScope.launch { checkState.updateMute(mute.not()) } }
                                .padding(start = 4.dp),
                            text = stringResource(id = R.string.troubleshooting_ignored),
                            color = colorResource(R.color.almostBlack),
                            style = OlvidTypography.subtitle1,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f, true))
                actions()
            }
        }
        AnimatedVisibility(visible = valid && (expanded.not() || additionalContent == null)) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

enum class TroubleshootingItemType {
    NOTIFICATIONS,
    CAMERA,
    MICROPHONE,
    LOCATION,
    LOCATION_PERMISSIONS,
    BATTERY_OPTIMIZATION,
    BACKGROUND_RESTRICTION,
    ALARM,
    FULL_SCREEN_INTENT,
    PERMANENT_WEBSOCKET,
    BACKUPS,
    CONNECTIVITY,
    STORAGE,
    DB_SYNC,
}

@Preview
@Composable
fun AppVersionHeaderPreview() {
    AppVersionHeader(true)
}

@Preview(locale = "fr-rFR")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TroubleShootItemPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp)
    ) {
        TroubleShootItem(
            title = "Title",
            description = "description",
            valid = false,
            critical = true,
            checkState = CheckState(
                "test",
                TroubleshootingDataStore(LocalContext.current),
            ) { true }
        ) {
            TextButton(
                shape = RoundedCornerShape(size = 6.dp),
                onClick = {}
            ) {
                Text(text = stringResource(id = R.string.troubleshooting_request_permission))
            }
        }
    }
}

@Preview
@Composable
fun FaqLinkPreview() {
    FaqLinkHeader({}, {})
}


@Preview
@Composable
fun RestartButtonPreview() {
    Box(
        modifier = Modifier.background(colorResource(R.color.almostWhite))
    ) {
        RestartAppButton()
    }
}