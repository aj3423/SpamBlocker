package spam.blocker.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable

//@Composable
//fun AnimateVisibleH(
//    visible: Boolean,
//    content: @Composable ()->Unit
//) {
//    AnimatedVisibility(
//        visible = visible,
//        enter = expandHorizontally(
//            animationSpec = tween(durationMillis = 300),
//            expandFrom = Alignment.Start
//        ) + fadeIn(),
//        exit = shrinkHorizontally(
//            animationSpec = tween(durationMillis = 300),
//            shrinkTowards = Alignment.Start
//        ) + fadeOut()
//    ) {
//        content()
//    }
//}

@Composable
fun AnimatedVisibleV(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        content()
    }
}
