package io.olvid.messenger.plus_button.configuration

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.ConfigurationSettingsPojo
import io.olvid.messenger.customClasses.prettyPrint
import io.olvid.messenger.designsystem.components.OlvidTextButton

@Composable
internal fun SettingsUpdateContent(
    settingsPojo: ConfigurationSettingsPojo,
    onUpdate: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var settingsDetails by remember { mutableStateOf(AnnotatedString("")) }

    LaunchedEffect(settingsPojo) {
        runCatching {
            settingsDetails = settingsPojo.prettyPrint(context)
        }.onFailure {
            App.toast(
                R.string.toast_message_error_parsing_settings_update_link,
                Toast.LENGTH_SHORT
            )
            onCancel()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.almostWhite),
            contentColor = colorResource(R.color.almostBlack)
        )
    ) {
        Column {
            Text(
                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
                text = settingsDetails,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OlvidTextButton(
                    text = stringResource(R.string.button_label_cancel),
                    contentColor = colorResource(R.color.greyTint),
                    onClick = onCancel
                )
                Spacer(modifier = Modifier.width(8.dp))
                OlvidTextButton(
                    text = stringResource(R.string.button_label_update),
                    onClick = onUpdate
                )
            }
        }
    }
}