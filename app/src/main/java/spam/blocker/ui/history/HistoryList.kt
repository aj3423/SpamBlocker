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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.SpamTable
import spam.blocker.db.defaultRegexRuleByType
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.RuleEditDialog
import spam.blocker.ui.widgets.BgLaunchApp
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LazyScrollbar
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.util.Clipboard
import spam.blocker.util.Launcher
import spam.blocker.util.Util


@Composable
fun HistoryContextMenuWrapper(
    vm: HistoryViewModel,
    index: Int,
    dbExistence: MutableMap<String, Boolean>,
    content: @Composable (MutableState<Boolean>) -> Unit,
) {
    val ctx = LocalContext.current
    val record = vm.records[index]

    // Add number to rule
    val addToNumberRuleTrigger = rememberSaveable { mutableStateOf(false) }
    if (addToNumberRuleTrigger.value) {
        RuleEditDialog(
            trigger = addToNumberRuleTrigger,
            initRule = defaultRegexRuleByType(Def.ForNumber).apply {
                pattern = Util.clearNumber(record.peer)
            },
            forType = Def.ForNumber,
            onSave = { newRule ->
                NumberRuleTable().addNewRule(ctx, newRule)
            }
        )
    }
    val existInDb = remember(dbExistence[record.peer]) {
        dbExistence[record.peer] == true
    }

    // Menu items
    val icons = remember(existInDb) {
        listOf(
            R.drawable.ic_copy,
            R.drawable.ic_regex,
            if (existInDb) R.drawable.ic_db_delete else R.drawable.ic_db_add,
            R.drawable.ic_check_circle,
        )
    }
    val labelIds = remember(existInDb) {
        listOf(
            R.string.copy_number,
            R.string.add_num_to_regex_rule,
            if (existInDb) R.string.remove_db_number else R.string.add_num_to_db,
            R.string.mark_all_as_read,
        )
    }


    val contextMenuItems = remember(existInDb) {
        labelIds.mapIndexed { menuIndex, labelId ->
            LabelItem(
                label = ctx.getString(labelId),
                icon = {
                    GreyIcon20(
                        icons[menuIndex]
                    )
                },
            ) {
                when (menuIndex) {
                    0 -> { // copy as raw number
                        Clipboard.copy(ctx, record.peer)
                    }

                    1 -> { // add number to new rule
                        addToNumberRuleTrigger.value = true
                    }

                    2 -> { // add/delete number to spam database
                        if (existInDb) {
                            val spamRecord = SpamTable.findByNumber(ctx, record.peer)
                            if (spamRecord != null)
                                SpamTable.deleteById(ctx, spamRecord.id)
                        } else {
                            SpamTable.add(ctx, record.peer)
                        }
                        dbExistence[record.peer] = !existInDb
                    }

                    3 -> { // mark all as read
                        vm.table.markAllAsRead(ctx)
                        vm.reload(ctx)
                    }
                }
            }
        }
    }

    DropdownWrapper(items = contextMenuItems) { contextMenuExpanded ->
        content(contextMenuExpanded)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryList(
    lazyState: LazyListState,
    vm: HistoryViewModel,
) {
    val ctx = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    // Keep track of numbers' existences in the spam database.
    // A map of Pair<number, ifExist>
    val dbExistence = remember { mutableStateMapOf<String, Boolean>() }

    LazyScrollbar(state = lazyState) {
        LazyColumn(
            state = lazyState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = M.padding(8.dp, 8.dp, 8.dp, 2.dp)
        ) {
            itemsIndexed(items = vm.records, key = { _, it -> it.id }) { index, record ->
                LaunchedEffect(record.id) {
                    dbExistence[record.peer] = SpamTable.findByNumber(ctx, record.peer) != null
                }

                HistoryContextMenuWrapper(vm, index, dbExistence) { contextMenuExpanded ->
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
                            existInDb = dbExistence[record.peer] == true,
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
