package spam.blocker.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The green dot in BotCard and ApiCard, indicating the bot is scheduled or the api is enabled.
@Composable
fun GreenDot() {
    Canvas(
        modifier = Modifier.size(6.dp)
    ) {
        drawCircle(color = Color.Green, radius = size.minDimension / 2)
    }
}
