package spam.blocker.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import spam.blocker.R
import spam.blocker.db.Db
import spam.blocker.db.HistoryRecord
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.defaultRegexRuleByType
import spam.blocker.db.historyTableForType
import spam.blocker.def.Def
import spam.blocker.ui.setting.regex.RuleEditDialog
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.util.M
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeToDeleteContainer
import spam.blocker.util.Clipboard
import spam.blocker.util.Launcher
import spam.blocker.util.Util

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryList(
    listState: LazyListState,
    forType: Int,
    records: SnapshotStateList<HistoryRecord>,
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    val clickedRecord = remember { mutableStateOf(HistoryRecord()) }

    val addToNumberRuleTrigger = remember { mutableStateOf(false) }
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

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = M.padding(8.dp, 8.dp, 8.dp, 2.dp)
    ) {
        // The first/last Card in LazyColumn are missing top/bottom elevation,
        //   use a 0dp Spacer to workaround it.
        item { Spacer(modifier = M.size(0.dp)) } // TODO : verify in release: is this necessary?


        itemsIndexed(items = records, key = { _, it -> it.id }) { index, record ->
            DropdownWrapper(items = contextMenuItems) { contextMenuExpanded ->
                SwipeToDeleteContainer(
                    item = record,
                    onDelete = { recToDel ->
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
                    }
                ) {
                    HistoryCard(
                        forType = forType,
                        record = record,
                        modifier = M
                            .combinedClickable(
                                onClick = {
                                    clickedRecord.value = record
                                    if(!record.read) {
                                        // 1. update db
                                        historyTableForType(forType).markAsRead(ctx, record.id)
                                        // 2. update UI
                                        records[index] = record.copy( read = true )
                                    }

                                    // Navigate to the default Call/SMS app, and open the conversation to this number
                                    when(forType) {
                                        Def.ForNumber -> {
                                            Launcher.openCallConversation(ctx, record.peer)
                                        }
                                        Def.ForSms -> {
                                            Launcher.openSMSConversation(ctx, record.peer)
                                        }
                                    }
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
        item { Spacer(modifier = M.size(0.dp)) }
    }
}
