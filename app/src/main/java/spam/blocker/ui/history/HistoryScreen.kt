package spam.blocker.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SearchBox
import spam.blocker.ui.widgets.Str
import spam.blocker.util.LockedDebouncer
import spam.blocker.util.TimeUtils.FreshnessColor
import spam.blocker.util.spf

object HistoryOptions {
    val showHistoryIndicator : MutableState<Boolean> = mutableStateOf(false)
    val showHistoryGeoLocation : MutableState<Boolean> = mutableStateOf(false)
    val showHistoryCarrier : MutableState<Boolean> = mutableStateOf(false)
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

    BackHandler(enabled = vm.multiSelect.value) {
        vm.clearSelection()
    }

    // Load history options
    LaunchedEffect(true) {
        val spf = spf.HistoryOptions(ctx)
        HistoryOptions.apply {
            showHistoryIndicator.value = spf.showIndicator
            showHistoryGeoLocation.value = spf.showGeoLocation
            showHistoryCarrier.value = spf.showCarrier
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
            if (vm.multiSelect.value) {
                GreyText(
                    text = Str(R.string.history_selected_count).format(vm.selectedIds.size),
                    modifier = M.padding(8.dp)
                )
            } else {
                // Use a Debouncer here as there could be LOTS of history records.
                val debouncer = remember { LockedDebouncer() }
                SearchBox(vm.searchEnabled, vm.filter) {
                    debouncer.debounce {
                        vm.reload(ctx)
                    }
                }
            }
            HistoryList(
                lazyState = listState,
                vm = vm,
            )
        }
    }
}
