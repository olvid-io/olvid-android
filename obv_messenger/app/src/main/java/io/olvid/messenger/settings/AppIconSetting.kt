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

package io.olvid.messenger.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import io.olvid.messenger.App
import io.olvid.messenger.R

data class AppIcon(val name: String, @DrawableRes val icon: Int, @StringRes val label: Int = R.string.app_name)

val appIcons = listOf(
    AppIcon(".main.MainActivityDefault", R.mipmap.ic_launcher),
    AppIcon(".main.MainActivityWhite", R.mipmap.ic_launcher_white),
    AppIcon(".main.MainActivityBlack", R.mipmap.ic_launcher_black),
    AppIcon(".main.MainActivityRainbow", R.mipmap.ic_launcher_rainbow),
    AppIcon(".main.MainActivityBlueHollow", R.mipmap.ic_launcher_blue_hollow),
    AppIcon(".main.MainActivityLightBlue", R.mipmap.ic_launcher_gradient_light_blue),
    AppIcon(".main.MainActivityPurple", R.mipmap.ic_launcher_gradient_purple),
    AppIcon(".main.MainActivityGhost", R.mipmap.ic_launcher_ghost, R.string.app_name_ghost),
    AppIcon(".main.MainActivityBubbles", R.mipmap.ic_launcher_bubbles, R.string.app_name_bubbles),
    AppIcon(".main.MainActivityGem", R.mipmap.ic_launcher_gem, R.string.app_name_gem),
    AppIcon(".main.MainActivityRosace", R.mipmap.ic_launcher_rosace, R.string.app_name_rosace),
    AppIcon(".main.MainActivityWeather", R.mipmap.ic_launcher_weather, R.string.app_name_weather),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconSettingScreen(isCurrentIcon: (appIcon: AppIcon) -> Boolean) {
    val context = LocalContext.current
    val currentIcon = appIcons.find { isCurrentIcon(it) } ?: appIcons.first()
    var selectedAppIcon by remember {
        mutableStateOf(currentIcon)
    }
    var showShortcutWarning by remember {
        mutableStateOf(false)
    }

    Column {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color = colorResource(id = R.color.greySubtleOverlay))
                .padding(6.dp)
        ) {
            FlowRow(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                appIcons.forEach { appIcon ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(interactionSource = remember {
                                MutableInteractionSource()
                            }, indication = ripple()) {
                                selectedAppIcon = appIcon
                            }
                            .then(
                                if (appIcon == selectedAppIcon)
                                    Modifier
                                        .border(
                                            width = 2.dp,
                                            color = colorResource(id = R.color.olvid_gradient_light),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(8.dp)
                                else Modifier.padding(8.dp)
                            ), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            painter = adaptiveIconPainterResource(id = appIcon.icon),
                            contentDescription = stringResource(id = appIcon.label)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(modifier = Modifier.width(64.dp),
                            text = stringResource(id = appIcon.label),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = colorResource(id = R.color.almostBlack),
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
            TextButton(
                modifier = Modifier.align(Alignment.End),
                enabled = selectedAppIcon != currentIcon,
                onClick = {
                    if (hasPinnedShortcuts()) {
                        showShortcutWarning = true
                    } else {
                        context.setIcon(selectedAppIcon)
                    }
                }) {
                Text(text = stringResource(id = R.string.pref_app_icon_apply))
            }
        }
    }

    if (showShortcutWarning) {
        AlertDialog(onDismissRequest = { showShortcutWarning = false },
            title = {
                Text(text = stringResource(id = R.string.pref_app_icon_shortcut_warning_title))
            }, text = {
                Text(text = stringResource(id = R.string.pref_app_icon_shortcut_warning_text))
            },
            confirmButton = {
                TextButton(onClick = { context.setIcon(selectedAppIcon) }) {
                    Text(text = stringResource(id = R.string.button_label_proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShortcutWarning = false }) {
                    Text(text = stringResource(id = R.string.button_label_cancel))
                }
            })
    }
}

fun hasPinnedShortcuts(): Boolean =
    if (Build.VERSION.SDK_INT > 26) {
        ((App.getContext()
            .getSystemService(Context.SHORTCUT_SERVICE) as? ShortcutManager)?.pinnedShortcuts?.size
            ?: 0) > 0
    } else {
        false
    }


@Composable
@Preview
private fun AppIconSettingScreenPreview() {
    AppCompatTheme {
        AppIconSettingScreen {
            it == appIcons[0]
        }
    }
}

fun getCurrentIcon(): AppIcon? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return null
    }
    return appIcons.firstOrNull {
        when (App.getContext().packageManager.getComponentEnabledSetting(
            ComponentName(
                App.getContext(),
                "io.olvid.messenger${it.name}"
            )
        )) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> it.name == ".main.MainActivityDefault"
            else -> false
        }
    } ?: run {
        App.getContext().setIcon(appIcons[0])
        appIcons[0]
    }
}

fun Context.setIcon(appIcon: AppIcon) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        packageManager.setComponentEnabledSetting(
            ComponentName(
                this,
                "io.olvid.messenger${appIcon.name}"
            ),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        App.currentIcon?.let {
            packageManager.setComponentEnabledSetting(
                ComponentName(
                    this,
                    "io.olvid.messenger${it.name}"
                ),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        App.currentIcon = appIcon
    }
}

@Composable
fun adaptiveIconPainterResource(@DrawableRes id: Int): Painter {
    val res = LocalContext.current.resources
    val theme = LocalContext.current.theme

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val adaptiveIcon = ResourcesCompat.getDrawable(res, id, theme) as? AdaptiveIconDrawable
        if (adaptiveIcon != null) {
            return BitmapPainter(adaptiveIcon.toBitmap().asImageBitmap())
        }
    }
    return ColorPainter(color = Color.Transparent)
}
