package spam.blocker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import spam.blocker.util.Lambda

typealias M = Modifier


fun String.parseColorString(): Pair<Int, Int>? {
    if (this.length != 8) {
        return null
    }

    val alpha = this.substring(0, 2).toIntOrNull(16)
    if (alpha == null)
        return null

    val rgb = this.substring(2, 8).toIntOrNull(16)
    if (rgb == null)
        return null

    return Pair(alpha, rgb)
}

fun Color.luminance(): Double {
    // Calculate the perceptive luminance (aka luma) - human eye favors green color...
    return (0.299 * red) + (0.587 * green) + (0.114 * blue)
}
fun Color.isLight(): Boolean {
    return luminance() > 0.55f
}

fun Color.contrastColor(): Color {
    return if (isLight())
        Color.Black
    else
        Color.White
}

// light_blue.darken() -> dark_blue
fun Color.darken(percent: Float = 0.3f): Color {   // 0.0 = original, 1.0 = black
    val f = 1f - percent.coerceIn(0f, 1f)
    return copy(
        red   = red   * f,
        green = green * f,
        blue  = blue  * f,
        alpha = alpha
    )
}

// dark_blue.lighten() -> light_blue
fun Color.lighten(percent: Float = 0.6f): Color =
    lerp(this, Color.White, percent.coerceIn(0f, 1f))

fun Color.slightDiff(percent: Float = 0.2f): Color {
    return if (luminance() > 0.55f)
        darken(percent)
    else
        lighten(percent)
}

@Composable
fun <T: Any> rememberSaveableMutableStateListOf(vararg elements: T): SnapshotStateList<T> {
    return rememberSaveable(saver = snapshotStateListSaver()) {
        elements.toList().toMutableStateList()
    }
}

private fun <T : Any> snapshotStateListSaver() = listSaver<SnapshotStateList<T>, T>(
    save = { stateList -> stateList.toList() },
    restore = { it.toMutableStateList() },
)

@Composable
fun screenWidthDp() : Float {
    val ctx = LocalContext.current

    return LocalDensity.current.run {
        val screenWidthPx = ctx.resources.displayMetrics.widthPixels
        screenWidthPx.toDp().value
    }
}
@Composable
fun screenHeightDp() : Float {
    val ctx = LocalContext.current

    return LocalDensity.current.run {
        val screenHeightPx = ctx.resources.displayMetrics.heightPixels
        screenHeightPx.toDp().value
    }
}
@Composable
fun Modifier.maxScreenHeight(percentage: Float): Modifier = composed {
    this.then(
        Modifier.heightIn(max = (screenHeightDp() * percentage).dp)
    )
}

// The built-in `LaunchedEffect(flag)` will get executed when
//   1. the first time it's composed
//   2. the flag changes
// This function only executes when the flag changes
@Composable
fun <T>LaunchedEffectOnlyOnChange(
    flag: T,
    onChange: Lambda,
) {
    var previousFlag by remember { mutableStateOf(flag) }

    LaunchedEffect(flag) {
        if (previousFlag != flag) {
            onChange()
        }
        previousFlag = flag
    }
}

@Composable
fun SizedBox(size: Int, content: @Composable ()->Unit) {
    Box(modifier = M.size(size.dp)) {
        content()
    }
}