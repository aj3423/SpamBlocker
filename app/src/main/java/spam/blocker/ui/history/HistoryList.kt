package spam.blocker.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.history.HistoryOptions.historyTimeColors
import spam.blocker.ui.history.HistoryOptions.showHistoryIndicator
import spam.blocker.ui.history.HistoryOptions.showHistoryTimeColor
import spam.blocker.ui.widgets.BgLaunchApp
import spam.blocker.ui.widgets.LazyScrollbar
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.util.Launcher
import spam.blocker.util.SimUtils
import spam.blocker.util.spf


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryList(
    lazyState: LazyListState,
    vm: HistoryViewModel,
) {
    val ctx = LocalContext.current
    val spf = spf.HistoryOptions(ctx)

    val coroutineScope = rememberCoroutineScope()

    val simCount = remember {
        SimUtils.listSimCards(ctx).size
    }

    // Just a short alias, that G.xxx it too long
    var showIndicator by remember(showHistoryIndicator.value) {
        mutableStateOf(showHistoryIndicator.value)
    }

    LazyScrollbar(state = lazyState) {
        IndicatorsWrapper(vm) { indicatorChecker, forceRefreshIndicators ->
            LazyColumn(
                state = lazyState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(items = vm.records, key = { _, it -> it.id }) { index, record ->
                    val indicators = remember { mutableStateOf(listOf<Indicator>()) }

                    LaunchedEffect(record.id, showIndicator, forceRefreshIndicators) {
                        indicators.value = if (showIndicator)
                            indicatorChecker(record.peer, record.cnap, record.extraInfo, record.simSlot)
                        else
                            listOf()
                    }

                    HistoryContextMenuWrapper(vm, index) { contextMenuExpanded ->
                        // Swipe <---->
                        LeftDeleteSwipeWrapper(
                            right = SwipeInfo(
                                veto = true,
                                background = { BgLaunchApp(StartToEnd) },
                                onSwipe = {
                                    val index = vm.records.indexOfFirst { it.id == record.id  }
                                    val record = vm.records[index]

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
                                    val index = vm.records.indexOfFirst { it.id == record.id  }
                                    val rec = vm.records[index]

                                    // 1. delete from db
                                    vm.table.delById(ctx, rec.id)

                                    // 2. remove from ArrayList
                                    vm.records.removeAt(index)

                                    // 3. show snackbar
                                    SnackBar.show(
                                        coroutineScope,
                                        rec.peer,
                                        ctx.getString(R.string.undelete),
                                    ) {
                                        vm.table.addRecordWithId(ctx, rec)
                                        vm.records.add(index, rec)
                                    }
                                },
                            )
                        ) {
                            HistoryCard(
                                forType = vm.forType,
                                record = record,
                                indicators = indicators.value,
                                simCount = simCount,
                                timeColors = if(showHistoryTimeColor.value) historyTimeColors else null,
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
