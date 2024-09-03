package spam.blocker.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

@Composable
fun ShowAnimated(
    visible: Boolean,
    content: @Composable ()->Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(
            animationSpec = tween(durationMillis = 300),
            expandFrom = Alignment.Start
        ) + fadeIn(),
        exit = shrinkHorizontally(
            animationSpec = tween(durationMillis = 300),
            shrinkTowards = Alignment.Start
        ) + fadeOut()
    ) {
        content()
    }
}