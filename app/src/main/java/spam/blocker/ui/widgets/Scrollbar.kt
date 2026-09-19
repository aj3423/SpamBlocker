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
import spam.blocker.G
import kotlin.math.max


// ref: https://gist.github.com/XFY9326/2067efcc3c5899557cc6a334d76a92c8
// For normal `Column` only
// Doesn't support thumb dragging, but also doesn't expand the parent dialog to max height
fun Modifier.simpleVerticalScrollbar(
    scrollState: ScrollState,
    scrollBarWidth: Dp = 2.dp,
    minScrollBarHeight: Dp = 5.dp,
    scrollBarColor: Color = G.palette.infoBlue,
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

// For `LazyColumn` only
// Doesn't support thumb dragging, but also doesn't expand the parent dialog to max height
fun Modifier.simpleLazyScrollbar(
    state: LazyListState,
    scrollBarWidth: Dp = 2.dp,
    minScrollBarHeight: Dp = 5.dp,
    scrollBarColor: Color = G.palette.infoBlue,
    cornerRadius: Dp = 2.dp,
    persistent: Boolean = false,
    offsetX: Int = 0,
): Modifier = composed {
    val targetAlpha = if (persistent ||
        state.isScrollInProgress) 1f else 0f
    val duration = if (state.isScrollInProgress) 150 else
        500

    val alpha by animateFloatAsState(
        label = "",
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
    )

    drawWithContent {
        drawContent()

        val layoutInfo = state.layoutInfo
        val totalItemsCount = layoutInfo.totalItemsCount
        val visibleItemsInfo = layoutInfo.visibleItemsInfo

        if (visibleItemsInfo.isEmpty() || totalItemsCount ==
            0) return@drawWithContent

        val needDrawScrollbar = persistent ||
                state.isScrollInProgress || alpha > 0.0f
        if (!needDrawScrollbar) return@drawWithContent

        val firstVisibleItem = visibleItemsInfo.first()
        val lastVisibleItem = visibleItemsInfo.last()

        val itemHeight = (lastVisibleItem.offset +
                lastVisibleItem.size - firstVisibleItem.offset).toFloat() /
                visibleItemsInfo.size
        val estimatedTotalHeight = itemHeight *
                totalItemsCount
        val visibleHeight = this.size.height

        if (estimatedTotalHeight <= visibleHeight)
            return@drawWithContent

        val scrollBarHeight = max(visibleHeight *
                (visibleHeight / estimatedTotalHeight),
            minScrollBarHeight.toPx())
        val scrollOffset = (firstVisibleItem.index *
                itemHeight) - firstVisibleItem.offset
        val maxOffset = estimatedTotalHeight - visibleHeight
        val scrollPercent = (scrollOffset /
                maxOffset).coerceIn(0f, 1f)
        val scrollBarOffsetY = (visibleHeight -
                scrollBarHeight) * scrollPercent

        drawRoundRect(
            color = scrollBarColor,
            topLeft = Offset(this.size.width -
                    scrollBarWidth.toPx() + offsetX, scrollBarOffsetY),
            size = Size(scrollBarWidth.toPx(),
                scrollBarHeight),
            alpha = alpha,
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }
}

// For `LazyColumn` only
// Supports thumb dragging, but it also expands the parent dialog to max height
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
            thumbUnselectedColor = G.palette.infoBlue,
            thumbSelectedColor = G.palette.infoBlue,
            selectionMode = ScrollbarSelectionMode.Full,
            selectionActionable = ScrollbarSelectionActionable.Always,
            hideDelayMillis = 400,
        )
    ) {
        content()
    }
}

// For normal `Column` only
// Supports thumb dragging, but it also expands the parent dialog to max height
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
            thumbUnselectedColor = G.palette.infoBlue,
            thumbSelectedColor = G.palette.infoBlue,
            selectionMode = ScrollbarSelectionMode.Full,
            selectionActionable = ScrollbarSelectionActionable.Always,
            hideDelayMillis = 400,
        )
    ) {
        content()
    }
}