@file:OptIn(ExperimentalMaterial3Api::class)

package spam.blocker.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.util.Lambda
import kotlin.math.abs
import kotlin.math.roundToInt


const val AnimationDuration = 200

enum class Anchor { Left, Center, Right }


data class SwipeInfo(
    val onSwipe: Lambda,

    // When it gets swiped and triggers the onSwipe, whether to veto the swiping state or not.
    // It's used for history record, when right swiped, it opens the conversation in the
    //   system call/sms app without removing the record from UI.
    val veto: Boolean = false,

    val background: (@Composable () -> Unit)? = null
)


@Composable
fun SwipeWrapper(
    left: SwipeInfo? = null,
    right: SwipeInfo? = null,
    triggerDistance: Int = 110,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current

    val openOffsetPx = with(density) { triggerDistance.dp.toPx() }

    val anchors = remember(density) {
        DraggableAnchors {
            Anchor.Center at 0f

            if (left != null)
                Anchor.Left at -openOffsetPx

            if (right != null)
                Anchor.Right at openOffsetPx
        }
    }

    val state = remember {
        AnchoredDraggableState(
            initialValue = Anchor.Center,
            anchors = anchors,
        )
    }
    val currentLeft by rememberUpdatedState(left)
    val currentRight by rememberUpdatedState(right)

    val flingBehavior = remember(state, openOffsetPx) {
        DistanceOnlyFlingBehavior(state, openOffsetPx)
    }


    val alpha = remember {
        derivedStateOf {
            abs(state.offset.div(openOffsetPx))
        }
    }

    SideEffect {
        state.updateAnchors(anchors)
    }

    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }
            .collectLatest {
                if (it == Anchor.Left) {
                    currentLeft?.onSwipe?.invoke()
                    if (currentLeft?.veto == true)
                        state.animateTo(Anchor.Center)
                }
                if (it == Anchor.Right) {
                    currentRight?.onSwipe?.invoke()
                    if (currentRight?.veto == true)
                        state.animateTo(Anchor.Center)
                }
            }
    }

    // Track the height of the foreground rule card, adjust the background to have the same height as the foreground card.
    var cardHeight by remember { mutableIntStateOf(0) }

    Box {
        // 1. Background
        Row(modifier = M
            .clip(RoundedCornerShape(6.dp))
            .height(cardHeight.dp)
        ) {
            if (state.offset > 0) { // swiping <-
                RowVCenter(modifier = M.alpha(alpha.value)) {
                    right?.background?.let { it() }
                }
            } else { // swiping ->
                RowVCenter(modifier = M.alpha(alpha.value)) {
                    left?.background?.let { it() }
                }
            }
        }

        // 2. Content
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                // Synchronize the background height with this
                .onSizeChanged{ size->
                    cardHeight =  (size.height / density.density).roundToInt()
                }
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    flingBehavior = flingBehavior
                )
                .offset {
                    IntOffset(
                        x = state.requireOffset().roundToInt(),
                        y = 0
                    )
                },
        ) {
            content()
        }
        }
}

// Fixes issue: fast-short-swipe always trigger the swipe regardless of the swiping distance,
// A workaround to https://issuetracker.google.com/issues/367660226
private class DistanceOnlyFlingBehavior(
    private val state: AnchoredDraggableState<Anchor>,
    private val swipeDistance: Float,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
    ): Float {
        val currentOffset = state.requireOffset()
        val targetOffset = when {
            currentOffset <= -swipeDistance -> -swipeDistance
            currentOffset >= swipeDistance -> swipeDistance
            else -> 0f
        }
        var previousOffset = currentOffset
        animate(
            initialValue = currentOffset,
            targetValue = targetOffset,
            animationSpec = tween(),
        ) { offset, _ ->
            scrollBy(offset - previousOffset)
            previousOffset = offset
        }
        return 0f
    }
}

// Wraps `content` with animation
@Composable
fun LeftDeleteSwipeWrapper(
    left: SwipeInfo? = null,
    right: SwipeInfo? = null,
    content: @Composable () -> Unit,
) {
    var isDeleted by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Animate content+backgrounds to the left when the deletion is confirmed
    SwipeWrapper(
        left = left?.copy(
            onSwipe = {
                isDeleted = true
                scope.launch {
                    delay(AnimationDuration.toLong())
                    left.onSwipe()
                }
            },
            background = if (left.background != null) {
                left.background
            } else {
                {
                    AnimatedVisibility(
                        visible = !isDeleted,
                        exit = shrinkHorizontally(
                            animationSpec = tween(durationMillis = AnimationDuration),
                            shrinkTowards = Alignment.Start
                        )
                    ) {
                        BgDelete(EndToStart)
                    }
                }
            }
        ),
        right = right,
        content = {
            AnimatedVisibility(
                visible = !isDeleted,
                exit = shrinkHorizontally(
                    animationSpec = tween(durationMillis = AnimationDuration),
                    shrinkTowards = Alignment.Start
                )
            ) {
                content()
            }
        }
    )
}


// Red background with a "recycle bin" icon.
@Composable
fun BgDelete(
    direction: SwipeToDismissBoxValue = EndToStart,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(G.palette.error)
            .padding(horizontal = 16.dp),
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

// Blue background with an "Exit" icon.
@Composable
fun BgLaunchApp(
    direction: SwipeToDismissBoxValue = StartToEnd,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(G.palette.infoBlue)
            .padding(horizontal = 16.dp),
        contentAlignment = if (direction == StartToEnd) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        }
    ) {
        ResIcon(
            iconId = R.drawable.ic_exit,
            color = Color.White,
        )
    }
}

// Fulfilled with solid color.
@Composable
fun BgFulfilled(
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {}
}
