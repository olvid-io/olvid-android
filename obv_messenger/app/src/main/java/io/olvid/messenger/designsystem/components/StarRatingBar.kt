package io.olvid.messenger.designsystem.components

import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.olvid.messenger.R
import io.olvid.messenger.designsystem.icons.OlvidIcons
import io.olvid.messenger.designsystem.icons.StarOutline
import kotlin.math.floor

@Composable
fun StarRatingBar(
    modifier: Modifier = Modifier,
    initialRating: Int = 0,
    maxStars: Int = 5,
    starSize: Dp = 32.dp,
    starSpacing: Dp = 4.dp,
    selectedStarColor: Color = colorResource(R.color.olvid_gradient_light),
    unselectedStarColor: Color = colorResource(R.color.greyTint),
    onRatingChanged: (Int) -> Unit
) {
    var currentRating by remember { mutableIntStateOf(initialRating) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) } // To store the size of the Row

    Box(
        modifier = modifier
            .wrapContentWidth()
            .onSizeChanged { componentSize = it }
            .pointerInput(maxStars, componentSize) {
                if (componentSize.width == 0) return@pointerInput
                val singleStarZoneWidth = componentSize.width.toFloat() / maxStars

                detectTapGestures(
                    onTap = { offset ->
                        val tappedRating =
                            getRatingFromPosition(offset.x, singleStarZoneWidth, maxStars)
                        val newRating =
                            if (currentRating == tappedRating) currentRating else tappedRating
                        if (currentRating != newRating) {
                            currentRating = newRating
                            onRatingChanged(newRating)
                        }
                    }
                )
            }
            .pointerInput(maxStars, componentSize) {
                if (componentSize.width == 0) return@pointerInput
                val singleStarZoneWidth = componentSize.width.toFloat() / maxStars

                detectDragGestures(
                    onDragStart = { offset ->
                        val draggedRating =
                            getRatingFromPosition(offset.x, singleStarZoneWidth, maxStars)
                        if (currentRating != draggedRating) {
                            currentRating = draggedRating
                            onRatingChanged(draggedRating)
                        }
                    },
                    onDrag = { change: PointerInputChange, _: Offset ->
                        val draggedRating =
                            getRatingFromPosition(change.position.x, singleStarZoneWidth, maxStars)
                        if (currentRating != draggedRating) {
                            currentRating = draggedRating
                            onRatingChanged(draggedRating)
                        }
                        change.consume()
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(vertical = starSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            for (starIndex in 0 until maxStars) {
                val ratingValue = starIndex + 1
                val isSelected = ratingValue <= currentRating

                Icon(
                    imageVector = if (isSelected) Icons.Filled.Star else OlvidIcons.StarOutline,
                    contentDescription = if (isSelected) "Selected Star $ratingValue" else "Unselected Star $ratingValue",
                    tint = if (isSelected) selectedStarColor else unselectedStarColor,
                    modifier = Modifier
                        .size(starSize)
                        .padding(horizontal = starSpacing / 2)
                )
            }
        }
    }
}

private fun getRatingFromPosition(
    xPosition: Float,
    singleStarZoneWidth: Float,
    maxStars: Int
): Int {
    if (singleStarZoneWidth <= 0) return 0
    val starIndex = floor(xPosition / singleStarZoneWidth).toInt()
    val rating = (starIndex + 1).coerceIn(1, maxStars)
    return rating
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun StarRatingBarPreviewThreeStars() {
    StarRatingBar(initialRating = 3, onRatingChanged = {})
}

