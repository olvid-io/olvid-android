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

package io.olvid.messenger.olvid_plus

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.theme.OlvidTypography

const val OLVID_STORE_URL = "https://store.olvid.io"

/**
 * Common frame of the Olvid+ subscription dialog shared by the `full` (Play Billing) and
 * `nogoogle` (Olvid Store link only) flavors: title bar, Olvid+ logo, flavor-specific
 * [content], then the Olvid+ feature details.
 */
@Composable
fun OlvidPlusOfferDialogScaffold(
    onDismiss: () -> Unit,
    price: String = "",
    showStoreLink: Boolean = true,
    content: @Composable ColumnScope.(maxWidth: Dp) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                            ) { onDismiss() }
                            .padding(4.dp),
                        painter = painterResource(id = R.drawable.ic_close),
                        tint = colorResource(id = R.color.almostBlack),
                        contentDescription = stringResource(R.string.content_description_close_button)
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

                    content(maxWidth)

                    Spacer(modifier = Modifier.height(24.dp))
                    OlvidPlusDetails(price = price, showStoreLink = showStoreLink)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * A subscription offer card. When [onSelected] is null the card is purely informational
 * (not clickable, never highlighted).
 */
@Composable
fun OlvidPlusOfferCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    discount: String? = null,
    selected: Boolean = false,
    onSelected: (() -> Unit)? = null
) {
    val topLeftGradient by animateColorAsState(if (selected) Color(0xFF6BB700) else Color.Transparent)
    val bottomRightGradient by animateColorAsState(if (selected) colorResource(id = R.color.olvid_gradient_light) else Color.Transparent)
    val colors = CardDefaults.cardColors(
        containerColor = colorResource(R.color.backgroundOverDialogBackground),
        contentColor = colorResource(R.color.almostBlack)
    )
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = OlvidTypography.h2,
                color = colorResource(R.color.almostBlack)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                minLines = if (onSelected == null) 3 else 1,
                maxLines = if (onSelected == null) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                style = if (onSelected == null) OlvidTypography.subtitle1 else OlvidTypography.body2,
                color = colorResource(R.color.greyTint)
            )
        }
    }

    Box {
        val cardModifier = modifier
            .widthIn(min = 165.dp)
            .padding(top = 9.dp)
        val border = BorderStroke(
            width = 2.dp,
            brush = Brush.linearGradient(colors = listOf(topLeftGradient, bottomRightGradient))
        )
        val shape = RoundedCornerShape(20.dp)
        if (onSelected != null) {
            Card(
                modifier = cardModifier,
                border = border,
                shape = shape,
                colors = colors,
                onClick = onSelected,
                content = cardContent
            )
        } else {
            Card(
                modifier = cardModifier,
                border = border,
                shape = shape,
                colors = colors,
                content = cardContent
            )
        }
        discount?.let { discount ->
            Text(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 8.dp)
                    .background(
                        colorResource(R.color.olvid_gradient_dark),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                text = discount,
                style = OlvidTypography.caption,
                color = colorResource(R.color.alwaysWhite)
            )
        }
    }
}

/**
 * Small outlined chip "Need more options? Visit the Olvid Store ↗" opening the Olvid Store.
 */
@Composable
fun OlvidStoreLinkChip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val linkColor = colorResource(R.color.blueOrWhite)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = colorResource(R.color.lightGrey),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                indication = ripple(color = colorResource(R.color.olvid_gradient_light)),
                interactionSource = remember { MutableInteractionSource() },
                onClick = { App.openLink(context, OLVID_STORE_URL.toUri()) }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(R.drawable.ic_storefront),
            tint = colorResource(R.color.greyTint),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.store_link_need_more_options),
            style = OlvidTypography.subtitle1,
            fontWeight = FontWeight.SemiBold,
            color = colorResource(R.color.greyTint)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.store_link_olvid_store),
            style = OlvidTypography.subtitle1,
            fontWeight = FontWeight.SemiBold,
            color = linkColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            modifier = Modifier.size(12.dp),
            painter = painterResource(R.drawable.ic_open_in_new),
            tint = linkColor,
            contentDescription = null
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OlvidPlusOfferCardPreview() {
    Row(modifier = Modifier.background(colorResource(R.color.dialogBackground)).padding(8.dp)) {
        OlvidPlusOfferCard(
            title = "Monthly",
            description = "€4.99 per month",
            discount = "Save 20%",
            selected = true
        ) { }
        Spacer(modifier = Modifier.width(8.dp))
        OlvidPlusOfferCard(
            modifier = Modifier.width(165.dp),
            title = "Family",
            description = "A pack of 6 licenses for you and your family."
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OlvidStoreLinkChipPreview() {
    Box(modifier = Modifier.background(colorResource(R.color.dialogBackground)).padding(16.dp)) {
        OlvidStoreLinkChip()
    }
}
