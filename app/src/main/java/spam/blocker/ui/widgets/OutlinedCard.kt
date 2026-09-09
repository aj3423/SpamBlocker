package spam.blocker.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.ui.slightDiff

@Composable
fun OutlineCard(
    modifier: Modifier = Modifier,
    containerBg: Color = G.palette.background,
    borderColor: Color = containerBg.slightDiff(),
    borderWidth: Int = 1,
    content: @Composable () -> Unit,
) {

    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerBg,
        ),
        border = BorderStroke(width = borderWidth.dp, color = borderColor),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}