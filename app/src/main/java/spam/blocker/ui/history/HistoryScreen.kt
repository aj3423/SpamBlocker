package spam.blocker.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.ui.M
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.ui.widgets.SearchBox
import spam.blocker.util.LockedDebouncer


@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
) {
    val ctx = LocalContext.current

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
                vm = vm,
                modifier = positionModifier,
            )
        }
    ) {
        Column(modifier = M.padding(8.dp, 8.dp, 8.dp, 2.dp)) {
            // Use a Debouncer here as there could be LOTS of history records.
            val debouncer = remember { LockedDebouncer() }
            SearchBox(vm.searchEnabled, vm.filter) {
                debouncer.debounce {
                    vm.reload(ctx)
                }
            }
            HistoryList(
                lazyState = listState,
                vm = vm,
            )
        }
    }
}
