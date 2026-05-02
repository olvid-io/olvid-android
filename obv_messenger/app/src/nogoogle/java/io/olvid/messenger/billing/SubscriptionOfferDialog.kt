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

package io.olvid.messenger.billing

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.olvid.engine.Logger
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.formatMarkdownToAnnotatedString
import io.olvid.messenger.designsystem.components.BaseDialogContent
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.owneddetails.OwnedIdentityDetailsActivity


@Composable
fun SubscriptionOfferDialog(activity: Activity?, onDismissCallback: () -> Unit, onPurchaseCallback: () -> Unit) {
    DialogSecure(
        onDismissRequest = onDismissCallback
    ) {
        val context = LocalContext.current

        BaseDialogContent(
            title = stringResource(R.string.dialog_title_subscriptions_not_available),
            content = {
                Text(
                    text = stringResource(R.string.dialog_message_subscriptions_not_available).formatMarkdownToAnnotatedString(),
                    style = OlvidTypography.body1,
                    color = colorResource(R.color.greyTint)
                )
            },
            actions = {
                Spacer(modifier = Modifier.weight(1f))
                OlvidTextButton(
                    text = stringResource(R.string.button_label_cancel),
                    contentColor = colorResource(R.color.greyTint)
                ) {
                    onDismissCallback()
                }
                Spacer(Modifier.width(8.dp))

                OlvidActionButton(
                    text = stringResource(R.string.button_label_send_us_a_mail),
                ) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = "mailto:feedback@olvid.io".toUri()
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Logger.x(e)
                    }
                    onDismissCallback()
                }
            }
        )
    }
}
