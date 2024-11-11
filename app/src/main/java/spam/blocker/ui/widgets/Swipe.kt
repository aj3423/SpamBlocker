@file:OptIn(ExperimentalMaterial3Api::class)

package spam.blocker.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.material3.SwipeToDismissBoxValue.Settled
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import spam.blocker.R
import spam.blocker.ui.theme.MayaBlue
import spam.blocker.ui.theme.Salmon
import spam.blocker.util.Lambda

private const val SwipeThresholdPercent = 0.35f

private const val AnimationDuration = 200

typealias SwipeDir = SwipeToDismissBoxValue

val SwipeToDismissBoxState.dir: SwipeDir
    get() = this.dismissDirection

data class SwipeInfo(
    val onSwipe: Lambda,
    // When it gets swiped and triggers the onSwipe, whether to veto the swiping state or not.
    val veto: Boolean = false,
    val background: (@Composable RowScope.(SwipeToDismissBoxState) -> Unit)? = null
)


// Wrap a @Composable to make it swipeable in both directions.
@Composable
fun SwipeWrapper(
    left: SwipeInfo? = null,
    right: SwipeInfo? = null,
    content: @Composable RowScope.() -> Unit,
) {
    var trigger by remember { mutableStateOf(false) }
    var triggeredDir by remember { mutableStateOf(Settled) }

    // ref: https://stackoverflow.com/a/78960161/2219196
    var state: SwipeToDismissBoxState? = null
    state = rememberSwipeToDismissBoxState(
        positionalThreshold = {
            it * SwipeThresholdPercent
        },
        confirmValueChange = { dir ->
            triggeredDir = dir

            when (dir) {
                EndToStart -> {
                    if (state!!.progress > SwipeThresholdPercent) {
                        trigger = true
                        left?.veto != true
                    } else {
                        false
                    }
                }

                StartToEnd -> {
                    if (state!!.progress > SwipeThresholdPercent) {
                        trigger = true
                        right?.veto != true
                    } else {
                        false
                    }
                }

                else -> false
            }
        }
    )

    LaunchedEffect(trigger) {
        if (trigger) {
            trigger = false

            when (triggeredDir) {
                StartToEnd -> right?.onSwipe?.invoke()
                EndToStart -> left?.onSwipe?.invoke()
                else -> {}
            }
        }
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            when (state.dir) {
                StartToEnd -> right?.background?.invoke(this, state)
                EndToStart -> left?.background?.invoke(this, state)
                else -> {}
            }
        },
        enableDismissFromEndToStart = left != null,
        enableDismissFromStartToEnd = right != null,
        content = content,
    )
}

// Wraps `content` with animation
@Composable
fun LeftDeleteSwipeWrapper(
    left: SwipeInfo? = null,
    right: SwipeInfo? = null,
    content: @Composable RowScope.() -> Unit,
) {
    var isDeleted by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    SwipeWrapper(
        left = left?.copy(
            onSwipe = {
                isDeleted = true
                scope.launch {
                    delay(AnimationDuration.toLong())
                    left.onSwipe()
                }
            },
            background = left.background ?: { state -> BgDelete(state, EndToStart) }
        ),
        right = right,
        content = {
            AnimatedVisibility(
                visible = !isDeleted,
                exit = shrinkHorizontally(
                    animationSpec = tween(durationMillis = AnimationDuration),
                    shrinkTowards = Alignment.Start
                ) + fadeOut()
            ) {
                content()
            }
        }
    )
}


// Red background with a "recycler bin" icon.
@Composable
fun BgDelete(
    state: SwipeToDismissBoxState,
    direction: SwipeToDismissBoxValue = EndToStart,
) {
    val color = if (state.dismissDirection == direction) {
        Salmon.copy(
            alpha = if (state.progress >= SwipeThresholdPercent)
                1.0f
            else
                (state.progress / SwipeThresholdPercent) * 0.7f
        )
    } else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(16.dp),
        contentAlignment = if (direction == StartToEnd) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        }
    ) {
        ResIcon(
            iconId = R.drawable.ic_recycle_bin,
            color = Color.White
        )
    }
}

// Red background with a "Exit" icon.
@Composable
fun BgLaunchApp(
    state: SwipeToDismissBoxState,
    direction: SwipeToDismissBoxValue = StartToEnd,
) {
    val color = if (state.dismissDirection == direction) {
        MayaBlue.copy(
            alpha = if (state.progress >= SwipeThresholdPercent)
                1.0f
            else
                (state.progress / SwipeThresholdPercent) * 0.7f
        )
    } else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(16.dp),
        contentAlignment = if (direction == StartToEnd) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        }
    ) {
        ResIcon(
            iconId = R.drawable.ic_exit,
            color = Color.White
        )
    }
}

