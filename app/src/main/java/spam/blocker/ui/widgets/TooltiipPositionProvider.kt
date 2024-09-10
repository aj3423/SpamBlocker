package spam.blocker.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

// This fixes: Tooltip from DropdownMenu aligns incorrectly
// ref: https://stackoverflow.com/a/78968130/2219196
@Composable
fun rememberPositionProvider(
    spacingBetweenTooltipAndAnchor: Dp = SpacingBetweenTooltipAndAnchor,
    userOffset: IntOffset = IntOffset.Zero
): PopupPositionProvider {
    val tooltipAnchorSpacing = with(LocalDensity.current) {
        spacingBetweenTooltipAndAnchor.roundToPx()
    }
    return remember(tooltipAnchorSpacing, userOffset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {

                val newBounds = anchorBounds.translate(userOffset)

                var x = newBounds.right
                // Try to shift it to the left of the anchor
                // if the tooltip would collide with the right side of the screen
                if (x + popupContentSize.width > windowSize.width) {
                    x = newBounds.left - popupContentSize.width
                    // Center if it'll also collide with the left side of the screen
                    if (x < 0)
                        x = newBounds.left +
                                (newBounds.width - popupContentSize.width) / 2
                }

                // 🔥 This is a line i added you might check for right side
                // overflowing as well
                x += popupContentSize.width / 2 + anchorBounds.width / 2

                // Tooltip prefers to be above the anchor,
                // but if this causes the tooltip to overlap with the anchor
                // then we place it below the anchor
                var y = newBounds.top - popupContentSize.height - tooltipAnchorSpacing
                if (y < 0)
                    y = newBounds.bottom + tooltipAnchorSpacing
                return IntOffset(x, y)
            }
        }
    }
}

internal val SpacingBetweenTooltipAndAnchor = 4.dp