package spam.blocker.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.widgets.BgLaunchApp
import spam.blocker.ui.widgets.LazyScrollbar
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.util.Launcher


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryList(
    lazyState: LazyListState,
    vm: HistoryViewModel,
) {
    val ctx = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(G.showHistoryPassed.value, G.showHistoryBlocked.value) {
        vm.reload(ctx)
    }

    // Just a short alias, that G.xxx it too long
    var showIndicator by remember(G.showHistoryIndicator.value) {
        mutableStateOf(G.showHistoryIndicator.value)
    }

    LazyScrollbar(state = lazyState) {
        IndicatorsWrapper(vm) { indicatorChecker, forceRefreshIndicators ->
            LazyColumn(
                state = lazyState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = M.padding(8.dp, 8.dp, 8.dp, 2.dp)
            ) {
                itemsIndexed(items = vm.records, key = { _, it -> it.id }) { index, record ->
                    val indicators = remember { mutableStateOf(listOf<Indicator>()) }

                    LaunchedEffect(record.id, showIndicator, forceRefreshIndicators) {
                        indicators.value = if (showIndicator)
                            indicatorChecker(record.peer, record.extraInfo)
                        else
                            listOf()
                    }

                    HistoryContextMenuWrapper(vm, index, indicators) { contextMenuExpanded ->
                        // Swipe <---->
                        LeftDeleteSwipeWrapper(
                            right = SwipeInfo(
                                veto = true,
                                background = { state -> BgLaunchApp(state, StartToEnd) },
                                onSwipe = {
                                    // Navigate to the default app, open the conversation to this number.
                                    if (!record.read) {
                                        // 1. update db
                                        vm.table.markAsRead(ctx, record.id)
                                        // 2. update UI
                                        vm.records[index] = vm.records[index].copy(read = true)
                                    }
                                    when (vm.forType) {
                                        Def.ForNumber -> Launcher.openCallConversation(ctx, record.peer)
                                        Def.ForSms -> Launcher.openSMSConversation(ctx, record.peer)
                                    }
                                }
                            ),
                            left = SwipeInfo(
                                onSwipe = {
                                    val recToDel = vm.records[index]

                                    // 1. delete from db
                                    vm.table.delById(ctx, recToDel.id)

                                    // 2. remove from ArrayList
                                    vm.records.removeAt(index)

                                    // 3. show snackbar
                                    SnackBar.show(
                                        coroutineScope,
                                        recToDel.peer,
                                        ctx.getString(R.string.undelete),
                                    ) {
                                        vm.table.addRecordWithId(ctx, recToDel)
                                        vm.records.add(index, recToDel)
                                    }
                                },
                            )
                        ) {
                            HistoryCard(
                                forType = vm.forType,
                                record = record,
                                indicators = indicators.value,
                                modifier = M.combinedClickable(
                                    onClick = {
                                        if (!record.read) {
                                            // 1. update db
                                            vm.table.markAsRead(ctx, record.id)
                                            // 2. update UI
                                            vm.records[index] = vm.records[index].copy(read = true)
                                        }

                                        // Toggle expanded
                                        val rec = vm.records[index]
                                        // 1. update db
                                        vm.table.setExpanded(ctx, record.id, !rec.expanded)
                                        // 2. update ui
                                        vm.records[index] = rec.copy(expanded = !rec.expanded)
                                    },
                                    onLongClick = {
                                        contextMenuExpanded.value = true
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
