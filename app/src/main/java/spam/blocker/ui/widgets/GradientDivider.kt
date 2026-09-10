package spam.blocker.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import spam.blocker.G

@Composable
fun GradientDivider(
    modifier: Modifier = Modifier,
    centerColor: Color = G.palette.disabled,
    leftColor: Color = centerColor,
    rightColor: Color = centerColor,
    thickness: Dp = 1.dp
) {
    Canvas(
        modifier = modifier.fillMaxWidth().height(thickness)
    ) {
        val gradientBrush = Brush.horizontalGradient(
            colors = listOf(leftColor, centerColor, rightColor)
        )

        drawLine(
            brush = gradientBrush,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = thickness.toPx()
        )
    }
}