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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.db.HistoryRecord
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.defaultRegexRuleByType
import spam.blocker.db.historyTableForType
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.RuleEditDialog
import spam.blocker.ui.widgets.BgLaunchApp
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LazyScrollbar
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.util.Clipboard
import spam.blocker.util.Launcher
import spam.blocker.util.Util

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryList(
    lazyState: LazyListState,
    forType: Int,
    records: SnapshotStateList<HistoryRecord>,
) {
    val ctx = LocalContext.current

    val clickedRecord = remember { mutableStateOf(HistoryRecord()) }

    val addToNumberRuleTrigger = rememberSaveable { mutableStateOf(false) }
    if (addToNumberRuleTrigger.value) {
        RuleEditDialog(
            trigger = addToNumberRuleTrigger,
            initRule = defaultRegexRuleByType(Def.ForNumber).apply {
                pattern = Util.clearNumber(clickedRecord.value.peer)
            },
            forType = Def.ForNumber,
            onSave = { newRule ->
                NumberRuleTable().addNewRule(ctx, newRule)
            }
        )
    }

    val contextMenuItems = remember(Unit) {
        ctx.resources.getStringArray(R.array.history_record_context_menu).asList()
            .mapIndexed { menuIndex, label ->
                LabelItem(label = label) {
                    val record = clickedRecord.value
                    when (menuIndex) {
                        0 -> { // copy as raw number
                            Clipboard.copy(ctx, record.peer)
                        }

                        1 -> { // add number to new rule
                            addToNumberRuleTrigger.value = true
                        }
                    }
                }
            }
    }

    val coroutineScope = rememberCoroutineScope()

    LazyScrollbar(state = lazyState) {
        LazyColumn(
            state = lazyState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = M.padding(8.dp, 8.dp, 8.dp, 2.dp)
        ) {
            itemsIndexed(items = records, key = { _, it -> it.id }) { index, record ->
                DropdownWrapper(items = contextMenuItems) { contextMenuExpanded ->

                    // Swipe <---->
                    LeftDeleteSwipeWrapper(
                        right = SwipeInfo(
                            veto = true,
                            background = { state -> BgLaunchApp(state, StartToEnd) },
                            onSwipe = {
                                // Navigate to the default app, open the conversation to this number.
                                if (!record.read) {
                                    // 1. update db
                                    historyTableForType(forType).markAsRead(ctx, record.id)
                                    // 2. update UI
                                    records[index] = records[index].copy(read = true)
                                }
                                when (forType) {
                                    Def.ForNumber -> Launcher.openCallConversation(ctx, record.peer)
                                    Def.ForSms -> Launcher.openSMSConversation(ctx, record.peer)
                                }
                                records[index] = records[index]
                            }

                        ),
                        left = SwipeInfo(
                            onSwipe = {
                                val recToDel = records[index]
                                val table = historyTableForType(forType)

                                // 1. delete from db
                                table.delById(ctx, recToDel.id)

                                // 2. remove from ArrayList
                                records.removeAt(index)

                                // 3. show snackbar
                                SnackBar.show(
                                    coroutineScope,
                                    recToDel.peer,
                                    ctx.getString(R.string.undelete),
                                ) {
                                    table.addRecordWithId(ctx, recToDel)
                                    records.add(index, recToDel)
                                }
                            },
                        )
                    ) {
                        HistoryCard(
                            forType = forType,
                            record = record,
                            modifier = M
                                .combinedClickable(
                                    onClick = {
                                        if (!record.read) {
                                            // 1. update db
                                            historyTableForType(forType).markAsRead(ctx, record.id)
                                            // 2. update UI
                                            records[index] = records[index].copy(read = true)
                                        }

                                        when (forType) {
                                            // Navigate to the default app, open the conversation to this number.
                                            Def.ForNumber -> {
                                                Launcher.openCallConversation(ctx, record.peer)
                                            }

                                            Def.ForSms -> {
                                                // Expand/Collapse the SMS body
                                                if (record.smsContent != null) {
                                                    val rec = records[index]
                                                    // 1. update db
                                                    historyTableForType(forType).setExpanded(
                                                        ctx,
                                                        record.id,
                                                        !rec.expanded
                                                    )
                                                    // 2. update ui
                                                    records[index] =
                                                        rec.copy(expanded = !rec.expanded)
                                                } else {
                                                    // Navigate to the default app, open the conversation to this number.
                                                    Launcher.openSMSConversation(ctx, record.peer)
                                                }
                                            }
                                        }
                                        clickedRecord.value = records[index]
                                    },
                                    onLongClick = {
                                        clickedRecord.value = record
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
