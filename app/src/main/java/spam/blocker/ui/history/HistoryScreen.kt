package spam.blocker.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.ui.M
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.ui.widgets.SearchBox
import spam.blocker.util.LockedDebouncer
import spam.blocker.util.TimeUtils.FreshnessColor
import spam.blocker.util.spf

object HistoryOptions {
    val showHistoryIndicator : MutableState<Boolean> = mutableStateOf(false)
    val showHistoryGeoLocation : MutableState<Boolean> = mutableStateOf(false)
    val forceShowSIM : MutableState<Boolean> = mutableStateOf(false)
    val showHistoryPassed : MutableState<Boolean> = mutableStateOf(false)
    val showHistoryBlocked : MutableState<Boolean> = mutableStateOf(false)
    val showHistoryTimeColor : MutableState<Boolean> = mutableStateOf(false)
    val historyTimeColors : SnapshotStateList<FreshnessColor> = mutableStateListOf()
}

@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
) {
    val ctx = LocalContext.current

    // Load history options
    LaunchedEffect(true) {
        val spf = spf.HistoryOptions(ctx)
        HistoryOptions.apply {
            showHistoryIndicator.value = spf.showIndicator
            showHistoryGeoLocation.value = spf.showGeoLocation
            forceShowSIM.value = spf.forceShowSim
            // these two are loaded at startup for calculating the unread count on bottom bar
//            showHistoryPassed.value = spf.showPassed
//            showHistoryBlocked.value = spf.showBlocked
            showHistoryTimeColor.value = spf.showTimeColor
            historyTimeColors.apply {
                clear()
                addAll(spf.loadTimeColors())
            }
        }
    }

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
