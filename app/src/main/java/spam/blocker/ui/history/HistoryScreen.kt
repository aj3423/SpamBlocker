package spam.blocker.ui.history

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import spam.blocker.db.HistoryRecord
import spam.blocker.ui.widgets.FabWrapper


@Composable
fun HistoryScreen(
    forType: Int,
    records: SnapshotStateList<HistoryRecord>,
) {
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
                modifier = positionModifier
            )
        }
    ) {
        HistoryList(
            lazyState = listState,
            forType = forType,
            records = records
        )
    }
}
