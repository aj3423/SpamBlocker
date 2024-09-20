package spam.blocker.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import my.nanihadesuka.compose.ColumnScrollbar
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionActionable
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import spam.blocker.ui.theme.DodgeBlue
import spam.blocker.ui.theme.SkyBlue
import kotlin.math.max


// ref: https://gist.github.com/XFY9326/2067efcc3c5899557cc6a334d76a92c8
// Only used in Balloon, and PopupDialog, others are using LazyColumnScrollbar below, because it
//  has a bug that always maximizes the popup window.
fun Modifier.simpleVerticalScrollbar(
    scrollState: ScrollState,
    scrollBarWidth: Dp = 2.dp,
    minScrollBarHeight: Dp = 5.dp,
    scrollBarColor: Color = SkyBlue,
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

@Composable
fun LazyScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable ()->Unit
) {
    LazyColumnScrollbar(
        modifier = modifier,
        state = state,
        settings = ScrollbarSettings.Default.copy(
            alwaysShowScrollbar = false,
            thumbThickness = 4.dp,
            scrollbarPadding = 4.dp,
            thumbUnselectedColor = SkyBlue,
            thumbSelectedColor = DodgeBlue,
            selectionMode = ScrollbarSelectionMode.Full,
            selectionActionable = ScrollbarSelectionActionable.Always,
            hideDelayMillis = 400,
        )
    ) {
        content()
    }
}
@Composable
fun NormalColumnScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable ()->Unit
) {
    ColumnScrollbar(
        modifier = modifier,
        state = state,
        settings = ScrollbarSettings.Default.copy(
            alwaysShowScrollbar = false,
            thumbThickness = 4.dp,
            scrollbarPadding = 4.dp,
            thumbUnselectedColor = SkyBlue,
            thumbSelectedColor = DodgeBlue,
            selectionMode = ScrollbarSelectionMode.Full,
            selectionActionable = ScrollbarSelectionActionable.Always,
            hideDelayMillis = 400,
        )
    ) {
        content()
    }
}