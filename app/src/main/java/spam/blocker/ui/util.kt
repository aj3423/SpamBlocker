package spam.blocker.ui

import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import spam.blocker.util.Lambda

typealias M = Modifier

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