package spam.blocker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import spam.blocker.G


@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = G.palette.background, // main screen bg
            surface = G.palette.background, // top/bottom systembar
            surfaceContainer = G.palette.background, // balloon tooltip bg
        ),
        content = content,
    )
}