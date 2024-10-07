package spam.blocker.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import spam.blocker.ui.theme.LocalPalette

@Composable
fun OutlineCard(
    modifier: Modifier = Modifier,
    containerBg: Color = MaterialTheme.colorScheme.background,
    content: @Composable () -> Unit,
) {
    val C = LocalPalette.current

    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerBg,
        ),
        border = BorderStroke(width = 1.dp, color = C.cardBorder),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}