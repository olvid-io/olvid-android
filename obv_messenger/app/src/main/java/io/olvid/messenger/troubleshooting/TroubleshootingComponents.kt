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

package io.olvid.messenger.troubleshooting

import android.content.DialogInterface
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.ripple.rememberRipple
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
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.BuildConfig
import io.olvid.messenger.R
import io.olvid.messenger.R.color
import io.olvid.messenger.R.drawable
import io.olvid.messenger.R.string
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.customClasses.formatMarkdown
import io.olvid.messenger.main.Utils
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
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            text = AnnotatedString(stringResource(
                string.troubleshooting_header,
                BuildConfig.VERSION_NAME + if (betaEnabled) " beta" else "",
                BuildConfig.VERSION_CODE,
                "${Build.BRAND} ${Build.MODEL}",
                VERSION.SDK_INT,
                uptime
            )).formatMarkdown(),
            color = colorResource(id = color.almostBlack),
            fontSize = 16.sp,
        )
    }
}

@Composable
fun RestartAppButton() {
    val context = LocalContext.current
    Row (
        modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorResource(id = R.color.red)),
            border = BorderStroke(1.dp, colorResource(id = R.color.red)),
            onClick = {
                SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                    .setTitle(R.string.dialog_title_restart_app)
                    .setMessage(R.string.dialog_message_restart_app)
                    .setNegativeButton(R.string.button_label_cancel, null)
                    .setPositiveButton(R.string.button_label_force_restart, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            try {
                                val packageManager = context.packageManager
                                val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                                val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
                                mainIntent.setPackage(context.packageName)
                                context.startActivity(mainIntent)
                                Runtime.getRuntime().exit(0)
                            } catch (_: Exception) {}
                        }
                    })
                    .create().show()
            }) {
            Text(text = stringResource(id = R.string.button_label_force_restart))
        }
    }
}


@Composable
fun FaqLinkHeader(openFaq: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .padding(end = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    modifier = Modifier.size(28.dp),
                    painter = painterResource(id = drawable.ic_arrow_back),
                    tint = colorResource(id = color.almostBlack),
                    contentDescription = "back"
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                modifier = Modifier.weight(1f, true),
                text = stringResource(id = string.troubleshooting_faq_description),
                color = colorResource(id = color.almostBlack),
                fontSize = 16.sp,
            )
        }

        ClickableText(
            modifier = Modifier
                .align(CenterHorizontally)
                .padding(8.dp),
            text = AnnotatedString(
                text = stringResource(id = R.string.troubleshooting_faq_link),
                spanStyle = SpanStyle(color = colorResource(id = color.olvid_gradient_light))
            ),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight(400),
                color = Color(0xFF8B8D97),
                textAlign = TextAlign.Center
            )
        ) {
            openFaq()
        }
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
    actions: @Composable RowScope.() -> Unit
) {
    var expanded by rememberSaveable {
        mutableStateOf(valid.not())
    }
    val mute: Boolean by checkState?.let{ it.isMute.collectAsState(true) } ?: remember { mutableStateOf(false) }
    val borderWidth: Float by animateFloatAsState(targetValue = if (critical && valid.not() && mute == false) 2f else 1f)
    val borderColor: Color by animateColorAsState(targetValue = if (critical && valid.not() && mute == false) colorResource(id = R.color.red) else Color(0x6E111111))
    Column(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .border(
                border = BorderStroke(borderWidth.dp, borderColor),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = colorResource(id = color.itemBackground),
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
                            .align(CenterVertically),
                        painter = painterResource(id = R.drawable.ic_chevron_right_compact),
                        contentDescription = "")
                    Text(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f, true)
                            .align(CenterVertically),
                        text = if (valid) title else titleInvalid ?: title,
                        color = colorResource(id = color.almostBlack),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    Image(
                        modifier = Modifier
                            .size(32.dp),
                        painter = painterResource(id = if (valid) drawable.ic_ok_green else drawable.ic_error_outline),
                        colorFilter = ColorFilter.tint(color = colorResource(id = R.color.golden))
                            .takeIf { valid.not() && critical.not() },
                        contentDescription = ""
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp, bottom = if (valid) 4.dp else 0.dp),
                        text = if (valid) description else descriptionInvalid ?: description,
                        color = colorResource(id = color.greyTint),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 18.sp,
                    )
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
                            .align(CenterVertically)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = rememberRipple(bounded = false)
                            ) { coroutineScope.launch { checkState.updateMute(mute.not()) } },
                        painter = painterResource(id = drawable.ic_notification_muted),
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
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f, true))
                actions()
            }
        }
        AnimatedVisibility(visible = valid) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

enum class TroubleshootingItemType {
    NOTIFICATIONS,
    CAMERA,
    MICROPHONE,
    BATTERY_OPTIMIZATION,
    BACKGROUND_RESTRICTION,
    ALARM,
    FULL_SCREEN_INTENT,
    PERMANENT_WEBSOCKET,
    BACKUPS,
    CONNECTIVITY,
    STORAGE,
}

@Preview
@Composable
fun AppVersionHeaderPreview() {
    AppCompatTheme {
        AppVersionHeader(true)
    }
}

@Preview(locale = "fr-rFR")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TroubleShootItemPreview() {
    AppCompatTheme {
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
                    shape = RoundedCornerShape(size = 8.dp),
                    onClick = {}
                ) {
                    Text(text = stringResource(id = string.troubleshooting_request_permission))
                }
            }
        }
    }
}

@Preview
@Composable
fun FaqLinkPreview() {
    AppCompatTheme {
        FaqLinkHeader({}, {})
    }
}