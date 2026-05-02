package io.olvid.messenger.discussion.location

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.StringUtils
import io.olvid.messenger.databases.ContactCacheSingleton
import io.olvid.messenger.databases.entity.Message
import io.olvid.messenger.designsystem.components.OlvidActionButton
import io.olvid.messenger.designsystem.cutoutHorizontalPadding
import io.olvid.messenger.designsystem.systemBarsHorizontalPadding
import io.olvid.messenger.designsystem.theme.OlvidTypography

@Composable
fun SharingLocationList(
    messages: List<Message>,
    onItemClick: (Message) -> Unit,
    onExternalClick: (Message) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        items(messages) { message ->
            SharingLocationItem(
                message = message,
                onClick = { onItemClick(message) },
                onExternalClick = { onExternalClick(message) }
            )
        }
    }
}

@Composable
private fun SharingLocationItem(
    message: Message,
    onClick: () -> Unit,
    onExternalClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        io.olvid.messenger.main.InitialView(
            modifier = Modifier.size(48.dp),
            initialViewSetup = {
                it.setFromCache(message.senderIdentifier)
            })

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = ContactCacheSingleton.getContactCustomDisplayName(message.senderIdentifier)
                    ?: "",
                style = OlvidTypography.h3,
                color = colorResource(R.color.primary700),
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp
            )
            Text(
                text = stringResource(
                    R.string.label_share_location_latest_update,
                    message.getJsonLocation()?.let {
                        StringUtils.getLongNiceDateString(
                            App.getContext(),
                            it.timestamp
                        )
                    } ?: ""),
                style = OlvidTypography.subtitle1,
                color = colorResource(R.color.greyTint),
            )
        }

        IconButton(onClick = onExternalClick) {
            Icon(
                painter = painterResource(R.drawable.ic_open_location_in_third_party_app_48),
                tint = colorResource(R.color.almostBlack),
                contentDescription = stringResource(R.string.button_label_open_in_external_viewer),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun CurrentLocationFab(
    onClick: () -> Unit,
    isCentered: Boolean,
    modifier: Modifier = Modifier
) {
    IconButton(
        modifier = modifier,
        shape = CircleShape,
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors().copy(
            containerColor = colorResource(R.color.almostWhite),
        )
    ) {
        Icon(
            painter = painterResource(if (isCentered) R.drawable.ic_location_current_location_enabled else R.drawable.ic_location_current_location_disabled),
            contentDescription = null,
            tint = Color.Unspecified
        )
    }
}

@Composable
fun CenterPointer(
    isMoving: Boolean,
    modifier: Modifier = Modifier
) {
    val offset = rememberMarkerOffset(isMoving = isMoving)
    Box(
        modifier = modifier
            .padding(bottom = 36.dp) // this padding is here to compensate for the Pin height
    ) {
        // Shadow
        Image(
            painter = painterResource(R.mipmap.location_pin_shadow),
            contentDescription = null,
            modifier = Modifier
                .width(24.dp)
                .height(12.dp)
                .align(Alignment.BottomCenter)
                .alpha(0.5f)
        )
        // Pin
        Image(
            painter = painterResource(R.drawable.ic_location_red),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.BottomCenter)
                .offset(y = offset)
        )
    }
}

@Composable
fun rememberMarkerOffset(isMoving: Boolean): Dp {
    val transition = updateTransition(targetState = isMoving, label = "MarkerAnimation")

    return transition.animateDp(
        transitionSpec = {
            if (targetState) {
                // moveCenterMarkerUp:
                tween(durationMillis = 100)
            } else {
                // moveCenterMarkerDown:
                keyframes {
                    durationMillis = 250
                    6.dp at 150 // Reaches bottom overshoot
                    0.dp at 250 // Settles at the final position
                }
            }
        },
        label = "MarkerOffset"
    ) { isUp ->
        if (isUp) (-24).dp else 0.dp
    }.value
}

@Composable
fun SendLocationBottomSheet(
    address: String?,
    isFetchingAddress: Boolean,
    onSendClick: () -> Unit,
) {
    val lightGreyColor = colorResource(R.color.lightGrey)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.almostWhite))
            .drawBehind {
                drawLine(
                    color = lightGreyColor,
                    strokeWidth = 1.dp.toPx(),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                )
            }
            .navigationBarsPadding()
            .cutoutHorizontalPadding()
            .systemBarsHorizontalPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .size(48.dp)
                .border(
                    width = 1.dp,
                    color = colorResource(R.color.olvid_gradient_light),
                    shape = CircleShape
                )
                .padding(8.dp),
            painter = painterResource(R.drawable.ic_location_blue_32dp),
            tint = colorResource(R.color.olvid_gradient_light),
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Address Text
        Box(modifier = Modifier.weight(1f, true)) {
            val resources = LocalResources.current
            var fetching by remember { mutableStateOf(true) }
            var fakeAddress by remember { mutableStateOf(true) }
            var addressText by remember { mutableStateOf("") }

            LaunchedEffect(address, isFetchingAddress) {
                if (!fetching && (isFetchingAddress || address == null)) {
                    fetching = true
                    return@LaunchedEffect
                }

                fetching = isFetchingAddress || address == null
                fakeAddress = isFetchingAddress || address == null || address == LocationActivity.ADDRESS_NOT_FOUND || address == LocationActivity.ADDRESS_ZOOM_IN || address == LocationActivity.ADDRESS_DISABLED
                when {
                    isFetchingAddress || address == null -> Unit
                    address == LocationActivity.ADDRESS_NOT_FOUND -> {
                        addressText = resources.getString(R.string.label_no_address_found)
                    }
                    address == LocationActivity.ADDRESS_ZOOM_IN -> {
                        addressText = resources.getString(R.string.label_zoom_in_for_address)
                    }
                    address == LocationActivity.ADDRESS_DISABLED -> {
                        addressText = resources.getString(R.string.label_address_lookup_disabled)
                    }
                    else -> {
                        addressText = address
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = fetching,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Text(
                    text = stringResource(R.string.label_fetching_address),
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = colorResource(R.color.greyTint),
                    fontStyle = FontStyle.Italic
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = fetching.not(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Text(
                    text = addressText,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = colorResource(if (fakeAddress) R.color.greyTint else R.color.primary700),
                    fontStyle = if (fakeAddress) FontStyle.Italic else FontStyle.Normal
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        OlvidActionButton(
            onClick = onSendClick,
            text = stringResource(R.string.button_label_send),
            large = true,
            trailingIcon = R.drawable.ic_send_up,
        )
    }
}
