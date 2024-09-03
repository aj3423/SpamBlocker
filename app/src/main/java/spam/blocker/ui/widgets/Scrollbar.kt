package spam.blocker.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import spam.blocker.ui.theme.DarkOrange
import kotlin.math.max


// from https://gist.github.com/XFY9326/2067efcc3c5899557cc6a334d76a92c8
fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    scrollBarWidth: Dp = 2.dp,
    minScrollBarHeight: Dp = 5.dp,
    scrollBarColor: Color = DarkOrange,
    cornerRadius: Dp = 2.dp,
    persistent: Boolean = false, // auto hide when not scrolling, set to `true` to always show
    offsetX: Int = 0
): Modifier = composed {
    val targetAlpha = if (persistent || scrollState.isScrollInProgress) 1f else 0f
    val duration = if (scrollState.isScrollInProgress) 150 else 500

    val alpha by animateFloatAsState(
        label = "",
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
    )

    drawWithContent {
        drawContent()

        val needDrawScrollbar = persistent || scrollState.isScrollInProgress || alpha > 0.0f

        if (needDrawScrollbar && scrollState.maxValue > 0) {
            val visibleHeight: Float = this.size.height - scrollState.maxValue
            val scrollBarHeight: Float = max(visibleHeight * (visibleHeight / this.size.height), minScrollBarHeight.toPx())
            val scrollPercent: Float = scrollState.value.toFloat() / scrollState.maxValue
            val scrollBarOffsetY: Float = scrollState.value + (visibleHeight - scrollBarHeight) * scrollPercent

            drawRoundRect(
                color = scrollBarColor,
                topLeft = Offset(this.size.width - scrollBarWidth.toPx() + offsetX, scrollBarOffsetY),
                size = Size(scrollBarWidth.toPx(), scrollBarHeight),
                alpha = alpha,
                cornerRadius = CornerRadius(cornerRadius.toPx())
            )
        }
    }
}
