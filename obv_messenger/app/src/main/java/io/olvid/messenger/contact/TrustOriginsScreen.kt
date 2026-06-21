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

package io.olvid.messenger.contact

import android.content.DialogInterface
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.map
import io.olvid.engine.engine.types.identities.ObvTrustOrigin
import io.olvid.messenger.App
import io.olvid.messenger.AppSingleton
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder
import io.olvid.messenger.databases.entity.Contact
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun TrustOriginsScreen(
    contactDetailsViewModel: ContactDetailsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contact: Contact? by contactDetailsViewModel.contactAndInvitation?.map { it?.contact }?.observeAsState() ?: remember { mutableStateOf(null) }

    BackHandler {
        onBack()
    }

    contact?.let { contact ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(colorResource(R.color.almostWhite))
                .padding(top = 8.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // exchange digits button
            if (contact.trustLevel < 4) {
                OlvidTextButton(
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                    text = stringResource(
                        R.string.button_label_exchange_digits_with_user,
                        contact.getCustomDisplayName()
                    )
                ) {
                    val builder: AlertDialog.Builder =
                        SecureAlertDialogBuilder(context, R.style.CustomAlertDialog)
                    builder.setMessage(
                        context.getString(
                            R.string.dialog_message_exchange_digits,
                            contact.getCustomDisplayName()
                        )
                    )
                        .setTitle(R.string.dialog_title_exchange_digits)
                        .setPositiveButton(
                            R.string.button_label_ok
                        ) { _: DialogInterface?, _: Int ->
                            try {
                                AppSingleton.getEngine().startTrustEstablishmentProtocol(
                                    contact.bytesContactIdentity,
                                    contact.getCustomDisplayName(),
                                    contact.bytesOwnedIdentity
                                )
                                App.openOneToOneDiscussionActivity(
                                    context,
                                    contact.bytesOwnedIdentity,
                                    contact.bytesContactIdentity,
                                    true
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .setNegativeButton(R.string.button_label_cancel, null)
                    builder.create().show()
                }
            }

            // trust origins list
            val trustOrigins = contactDetailsViewModel.trustOrigins
            val originsCount = trustOrigins?.size ?: 0
            val iconSize = 48.dp
            val padding = 16.dp
            val color = colorResource(R.color.greyTint)

            if (trustOrigins != null) {
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .background(
                            color = colorResource(R.color.lighterGrey),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .drawBehind {
                            if (originsCount > 1) {
                                val dashEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(
                                        3.dp.toPx(),
                                        3.dp.toPx()
                                    ), 0f
                                )
                                val x = (iconSize / 2 + padding).toPx()
                                val startY = (padding + iconSize / 2).toPx()
                                val endY = size.height - (padding + iconSize / 2).toPx()

                                drawLine(
                                    color = color,
                                    start = Offset(x, startY),
                                    end = Offset(x, endY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }
                        }
                ) {
                    trustOrigins.forEachIndexed { index, (type, origins) ->
                        var isExpanded by remember { mutableStateOf(false) }
                        val rotation: Float by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f)
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (origins.size > 1) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple()
                                        ) { isExpanded = !isExpanded }
                                    } else Modifier
                                )
                                .padding(padding)
                                .then(
                                    if (index == trustOrigins.lastIndex) {
                                        // hide dashed line
                                        Modifier.background(
                                            color = colorResource(
                                                R.color.lighterGrey
                                            )
                                        )
                                    } else Modifier
                                )
                        ) {
                            Image(
                                modifier = Modifier.size(iconSize),
                                painter = painterResource(type.icon()), contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = type.localizedName(context),
                                        style = OlvidTypography.h3,
                                        color = colorResource(R.color.almostBlack)
                                    )
                                    if (origins.size > 1) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_chevron_right),
                                            contentDescription = null,
                                            tint = colorResource(R.color.almostBlack),
                                            modifier = Modifier.rotate(rotation)
                                        )
                                    }
                                }
                                if (origins.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = origins.first().details,
                                        style = OlvidTypography.subtitle1,
                                        color = colorResource(R.color.greyTint)
                                    )
                                    Text(
                                            text = origins.first().timestamp,
                                    style = OlvidTypography.caption,
                                    color = colorResource(R.color.mediumGrey)
                                    )
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column {
                                            origins.drop(1).forEach { trustOrigin ->
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = trustOrigin.details,
                                                    style = OlvidTypography.subtitle1,
                                                    color = colorResource(R.color.greyTint)
                                                )
                                                Text(
                                                    text = trustOrigin.timestamp,
                                                    style = OlvidTypography.caption,
                                                    color = colorResource(R.color.mediumGrey)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@DrawableRes
fun ObvTrustOrigin.TYPE.icon() = when (this) {
    ObvTrustOrigin.TYPE.KEYCLOAK -> R.drawable.ic_trust_origin_directory
    ObvTrustOrigin.TYPE.DIRECT -> R.drawable.ic_trust_origin_mutual
    ObvTrustOrigin.TYPE.INTRODUCTION -> R.drawable.ic_trust_origin_introduction
    ObvTrustOrigin.TYPE.GROUP, ObvTrustOrigin.TYPE.SERVER_GROUP_V2 -> R.drawable.ic_trust_origin_group
}
