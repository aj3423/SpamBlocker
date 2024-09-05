package spam.blocker.ui.history

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import spam.blocker.db.HistoryRecord
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.util.SharedPref.Global


@Composable
fun HistoryPage(
    forType: Int,
    records: SnapshotStateList<HistoryRecord>,
) {
    val ctx = LocalContext.current
    val spf = Global(ctx)

    val showPassed = remember { mutableStateOf(spf.getShowPassed()) }
    val showBlocked = remember { mutableStateOf(spf.getShowBlocked()) }
    val logSms = remember { mutableStateOf(spf.isLogSmsContentEnabled()) }

    // Hide FAB on scrolling to the last item
    val listState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
            lastVisibleIndex < lastItemIndex || listState.firstVisibleItemIndex == 0
        }
    }

    FabWrapper(
        fabRow = { positionModifier ->
            // Two FABs
            HistoryFabs(
                visible = fabVisible,
                forType = forType,
                showPassed = showPassed,
                showBlocked = showBlocked,
                logSmsContent = logSms,
                modifier = positionModifier
            )
        }
    ) {
        HistoryList(
            listState = listState,
            forType = forType,
            records = records
        )
    }
}
