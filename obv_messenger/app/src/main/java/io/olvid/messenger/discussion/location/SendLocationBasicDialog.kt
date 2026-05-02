package io.olvid.messenger.discussion.location

import android.graphics.drawable.AnimationDrawable
import android.location.Location
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.olvid.messenger.App
import io.olvid.messenger.R
import io.olvid.messenger.customClasses.LocationShareQuality
import io.olvid.messenger.databases.entity.jsons.JsonLocation
import io.olvid.messenger.databases.tasks.PostLocationMessageInDiscussionTask
import io.olvid.messenger.designsystem.components.DialogSecure
import io.olvid.messenger.designsystem.components.OlvidDropdownMenu
import io.olvid.messenger.designsystem.components.OlvidDropdownMenuItem
import io.olvid.messenger.designsystem.components.OlvidTextButton
import io.olvid.messenger.designsystem.theme.olvidSwitchDefaults
import io.olvid.messenger.settings.SettingsActivity

private const val GREEN_PRECISION_LIMIT = 20.0
private const val ORANGE_PRECISION_LIMIT = 50.0

@Composable
fun SendLocationBasicDialog(
    discussionId: Long,
    currentLocation: Location?,
    continuousLocationSharing: Boolean = false,
    onRequestBackgroundPermission: () -> Unit = {},
    onDismissRequest: () -> Unit,
    onFinish: () -> Unit
) {
    DialogSecure(onDismissRequest = onDismissRequest) {
        SendLocationBasicScreen(
            discussionId = discussionId,
            currentLocation = currentLocation,
            initialContinuousLocationSharing = continuousLocationSharing,
            onRequestBackgroundPermission = onRequestBackgroundPermission,
            onDismissRequest = onDismissRequest,
            onFinish = onFinish
        )
    }
}

@Composable
private fun SendLocationBasicScreen(
    discussionId: Long,
    currentLocation: Location?,
    initialContinuousLocationSharing: Boolean,
    onRequestBackgroundPermission: () -> Unit,
    onDismissRequest: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var isSharing by remember { mutableStateOf(initialContinuousLocationSharing) }
    var shareDuration by remember { mutableLongStateOf(SettingsActivity.locationDefaultSharingDurationValue) }
    var shareQuality by remember { mutableStateOf(SettingsActivity.locationDefaultShareQuality) }

    // Background permission request when switch is toggled
    LaunchedEffect(isSharing) {
        if (isSharing && !LocationUtils.isBackgroundLocationPermissionGranted(context)) {
            onRequestBackgroundPermission()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.almostWhite), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Location Info Card
        LocationInfoCard(currentLocation)

        // Duration Selector
        ShareDurationRow(
            duration = shareDuration,
            isSharing = isSharing,
            onDurationChange = {
                shareDuration = it
                if (!isSharing && !initialContinuousLocationSharing) isSharing = true
            },
            onSwitchChange = { if (!initialContinuousLocationSharing) isSharing = it },
            continuousLocationSharing = initialContinuousLocationSharing
        )

        // Interval/Quality Selector (Only visible if sharing)
        if (isSharing) {
            ShareQualityRow(
                quality = shareQuality,
                onQualityChange = { shareQuality = it }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            OlvidTextButton(
                text = stringResource(R.string.button_label_cancel),
                onClick = onDismissRequest,
            )

            OlvidTextButton(
                text = stringResource(if (isSharing) R.string.button_label_start_sharing else R.string.button_label_send),
                enabled = currentLocation != null
            ) {
                currentLocation?.let { loc ->
                    if (isSharing) {
                        val expiration =
                            if (shareDuration < 0L) null else System.currentTimeMillis() + shareDuration
                        App.runThread(
                            PostLocationMessageInDiscussionTask.startLocationSharingInDiscussionTask(
                                loc, discussionId, true, expiration, shareQuality
                            )
                        )
                    } else {
                        App.runThread(
                            PostLocationMessageInDiscussionTask.postSendLocationMessageInDiscussionTask(
                                loc,
                                discussionId,
                                true
                            )
                        )
                    }
                    onFinish()
                }
            }
        }
    }
}

@Composable
private fun LocationInfoCard(currentLocation: Location?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(
                color = colorResource(R.color.almostWhite),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = colorResource(R.color.locationBorder),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colorResource(id = R.color.almostWhite), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            if (currentLocation == null) {
                val infiniteTransition = rememberInfiniteTransition()
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
                Image(
                    painter = painterResource(R.drawable.ic_waiting_for_location),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(angle)
                )
            } else {
                AndroidView(factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        val drawable = AppCompatResources.getDrawable(
                            ctx,
                            R.drawable.ic_map_and_pin_animated
                        )
                        setImageDrawable(drawable)
                        if (drawable is AnimationDrawable) {
                            drawable.start()
                        }
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            if (currentLocation == null) {
                Text(
                    stringResource(R.string.label_waiting_for_location),
                    color = colorResource(R.color.primary700),
                    fontSize = 14.sp
                )
            } else {
                val jsonLocation = JsonLocation.sendLocationMessage(currentLocation)
                Text(
                    stringResource(
                        R.string.label_location_message_content_position,
                        jsonLocation.truncatedLatitudeString,
                        jsonLocation.truncatedLongitudeString
                    ),
                    color = colorResource(R.color.primary700),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val context = LocalContext.current

                val accuracyColor = when {
                    currentLocation.accuracy < GREEN_PRECISION_LIMIT -> colorResource(R.color.green)
                    currentLocation.accuracy < ORANGE_PRECISION_LIMIT -> colorResource(R.color.orange)
                    else -> colorResource(R.color.red)
                }
                Text(
                    stringResource(
                        R.string.label_location_message_content_precision,
                        jsonLocation.getTruncatedPrecisionString(context)
                    ),
                    color = accuracyColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(
                        R.string.label_location_message_content_altitude,
                        jsonLocation.getTruncatedAltitudeString(context)
                    ),
                    color = colorResource(R.color.greyTint),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ShareDurationRow(
    duration: Long,
    isSharing: Boolean,
    onDurationChange: (Long) -> Unit,
    onSwitchChange: (Boolean) -> Unit,
    continuousLocationSharing: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val durationValues =
        stringArrayResource(R.array.share_location_duration_values).map { it.toLong() }
    val shortDurationLabel = stringArrayResource(R.array.share_location_duration_short_strings)
    val longDurationLabel = stringArrayResource(R.array.share_location_duration_long_strings)
    val selectedIndex = durationValues.indexOf(duration).coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable { expanded = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_dropdown_almostblack),
                    contentDescription = null,
                    tint = colorResource(R.color.almostBlack),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (selectedIndex >= 0 && selectedIndex < longDurationLabel.size) longDurationLabel[selectedIndex] else "",
                    fontSize = 16.sp,
                    color = colorResource(R.color.almostBlack)
                )
            }
            OlvidDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                durationValues.forEachIndexed { index, value ->
                    if (index < shortDurationLabel.size) {
                        OlvidDropdownMenuItem(
                            text = shortDurationLabel[index],
                            onClick = {
                                onDurationChange(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        if (!continuousLocationSharing) {
            Switch(
                checked = isSharing,
                onCheckedChange = onSwitchChange,
                colors = olvidSwitchDefaults()
            )
        }
    }
}

@Composable
private fun ShareQualityRow(
    quality: LocationShareQuality,
    onQualityChange: (LocationShareQuality) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val qualityValues =
        stringArrayResource(R.array.share_location_quality_values).map { it.toInt() }
    val shortQualityLabels = stringArrayResource(R.array.share_location_quality_short_strings)
    val longQualityLabels = stringArrayResource(R.array.share_location_quality_long_strings)
    val currentQualityValue = quality.value
    val selectedIndex = qualityValues.indexOf(currentQualityValue).coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable { expanded = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_dropdown_almostblack),
                    contentDescription = null,
                    tint = colorResource(R.color.almostBlack),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (selectedIndex >= 0 && selectedIndex < longQualityLabels.size) longQualityLabels[selectedIndex] else "",
                    fontSize = 16.sp,
                    color = colorResource(R.color.almostBlack)
                )
            }
            OlvidDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                qualityValues.forEachIndexed { index, value ->
                    if (index < shortQualityLabels.size) {
                        OlvidDropdownMenuItem(
                            text = shortQualityLabels[index],
                            onClick = {
                                onQualityChange(LocationShareQuality.fromValue(value))
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
