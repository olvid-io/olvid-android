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

package io.olvid.messenger.billing


import android.app.Activity
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.components.OlvidCircularProgress
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidOutlinedActionButton
import io.olvid.messenger.designsystem.components.OlvidOutlinedSecondaryButton
import io.olvid.messenger.designsystem.theme.OlvidTypography
import io.olvid.messenger.olvid_plus.OlvidPlusDetails
import io.olvid.messenger.settings.SettingsActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SubscriptionOfferDialog(
    activity: Activity?,
    onDismissCallback: () -> Unit,
    onPurchaseCallback: () -> Unit,
    viewModel: SubscriptionOfferViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel.dismissDialog) {
        if (viewModel.dismissDialog) {
            onDismissCallback()
            if (viewModel.purchaseSuccessful) {
                onPurchaseCallback()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissCallback,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(8.dp)
                .wrapContentWidth()
                .widthIn(max = 400.dp),
            propagateMinConstraints = true
        ) {
            val maxWidth = maxWidth
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorResource(id = R.color.dialogBackground))
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dialog_title_subscription_plans),
                        style = OlvidTypography.h2,
                        color = colorResource(R.color.almostBlack)
                    )
                    Spacer(modifier = Modifier.weight(1f, true))
                    Icon(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false)
                            ) { onDismissCallback() }
                            .padding(4.dp),
                        painter = painterResource(id = R.drawable.ic_close),
                        tint = colorResource(id = R.color.almostBlack),
                        contentDescription = "close"
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.olvid_plus_logo),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (viewModel.loading || viewModel.purchasing) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colorResource(R.color.backgroundOverDialogBackground),
                                contentColor = colorResource(R.color.almostBlack)
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                OlvidCircularProgress(modifier = Modifier.align(Alignment.CenterHorizontally))
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    color = colorResource(id = R.color.almostBlack),
                                    text = stringResource(id = R.string.text_loading_subscription_plans),
                                    style = OlvidTypography.h3,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else if (viewModel.subscriptionsOffers.isNullOrEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colorResource(R.color.backgroundOverDialogBackground),
                                contentColor = colorResource(R.color.almostBlack)
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    modifier = Modifier.size(32.dp),
                                    painter = painterResource(id = R.drawable.ic_error_outline),
                                    tint = colorResource(id = R.color.red),
                                    contentDescription = ""
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (viewModel.subscriptionsOffers == null)
                                        stringResource(id = R.string.label_failed_to_query_subscription)
                                    else
                                        stringResource(id = R.string.label_no_subscription_available),
                                    color = colorResource(id = R.color.almostBlack),
                                    style = OlvidTypography.h3,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        OlvidOutlinedActionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.button_label_retry),
                            onClick = { viewModel.fetchOffers(addDelay = true) }
                        )
                    } else {
                        viewModel.subscriptionsOffers?.let { subscriptionsOffers ->
                            val width =
                                (maxWidth / (if (subscriptionsOffers.size > 1) 2 else 1) - 24.dp).coerceAtMost(
                                    300.dp
                                )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = subscriptionsOffers,
                                    key = { it.offerToken }) { subscriptionsOffer ->
                                    SubscriptionOfferDetails(
                                        modifier = Modifier
                                            .width(width),
                                        title = when (subscriptionsOffer.pricingPhase) {
                                            "P1Y" -> stringResource(id = R.string.label_subscription_offer_yearly)
                                            else -> stringResource(id = R.string.label_subscription_offer_monthly)
                                        },
                                        description = subscriptionsOffer.formattedPrice(),
                                        discount = viewModel.getDiscountString(
                                            context,
                                            subscriptionsOffer
                                        ),
                                        selected = viewModel.selectedOfferToken == subscriptionsOffer.offerToken
                                    ) {
                                        viewModel.selectedOfferToken =
                                            subscriptionsOffer.offerToken
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OlvidActionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(
                                id = R.string.button_label_subscribe_with_price,
                                viewModel.selectedOfferPrice
                            )
                        ) {
                            activity?.let { viewModel.launchBillingFlow(it) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box {
                            var remindMeLaterMenu by remember { mutableStateOf(false) }
                            val locale = Locale.getDefault()
                            val timeFormatter = remember(locale) {
                                DateTimeFormatter
                                    .ofLocalizedTime(FormatStyle.SHORT)
                                    .withLocale(locale)
                            }
                            OlvidOutlinedSecondaryButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.button_label_remind_me_later),
                                onClick = {
                                    remindMeLaterMenu = true
                                }
                            )
                            OlvidDropdownMenu(
                                expanded = remindMeLaterMenu,
                                containerColor = colorResource(R.color.backgroundOverDialogBackground),
                                onDismissRequest = { remindMeLaterMenu = false }
                            ) {

                                val remind = remember {
                                    { target: LocalDateTime ->
                                        val delay = java.time.Duration.between(LocalDateTime.now(), target).toMillis()
                                        if (delay > 0) {
                                            viewModel.scheduleSubscriptionReminder(delay)
                                            SettingsActivity.olvidPlusReminderTimestamp = System.currentTimeMillis() + delay
                                        }
                                    }
                                }
                                val ninePmTarget =
                                    remember { LocalDateTime.now().let {
                                        if (it.hour >= 21)
                                            it.plusDays(1)
                                        else
                                            it
                                    }.withHour(21).withMinute(0).withSecond(0) }
                                val ninePmIsTomorrow = remember { LocalDateTime.now().hour >= 21 }
                                val nineAmTarget = remember {
                                    LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0)
                                }

                                OlvidDropdownMenuItem(
                                    text = stringResource(
                                        if (ninePmIsTomorrow)
                                            R.string.label_remind_me_tomorrow
                                        else
                                            R.string.label_remind_me_tonight,
                                        timeFormatter.format(ninePmTarget)
                                    ), onClick = {
                                        remindMeLaterMenu = false
                                        remind(ninePmTarget)
                                        onDismissCallback()
                                        App.toast(R.string.toast_message_reminder_set, Toast.LENGTH_SHORT)
                                    })
                                OlvidDropdownMenuItem(
                                    text = stringResource(
                                        R.string.label_remind_me_tomorrow,
                                        timeFormatter.format(nineAmTarget)
                                    ), onClick = {
                                        remindMeLaterMenu = false
                                        remind(nineAmTarget)
                                        onDismissCallback()
                                        App.toast(R.string.toast_message_reminder_set, Toast.LENGTH_SHORT)
                                    })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    OlvidPlusDetails(price = viewModel.selectedOfferPrice)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SubscriptionPreview() {
    SubscriptionOfferDialog(activity = null, onDismissCallback = {}, onPurchaseCallback = {})
}

