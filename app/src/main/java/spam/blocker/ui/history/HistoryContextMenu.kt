package spam.blocker.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.SpamTable
import spam.blocker.db.defaultRegexRuleByType
import spam.blocker.def.Def
import spam.blocker.ui.setting.regex.EditRegexDialog
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.util.Clipboard
import spam.blocker.util.Util


@Composable
fun HistoryContextMenuWrapper(
    vm: HistoryViewModel,
    index: Int,
    content: @Composable (MutableState<Boolean>) -> Unit,
) {
    val ctx = LocalContext.current
    val record = vm.records[index]

    // Add number to rule
    val addToNumberRuleTrigger = rememberSaveable { mutableStateOf(false) }
    if (addToNumberRuleTrigger.value) {
        EditRegexDialog(
            trigger = addToNumberRuleTrigger,
            initRule = defaultRegexRuleByType(Def.ForNumber).apply {
                pattern = Util.clearNumber(record.peer)
            },
            forType = Def.ForNumber,
            onSave = { newRule ->
                NumberRegexTable().addNewRule(ctx, newRule)
            }
        )
    }

    var numberInDb by remember { mutableStateOf(SpamTable.findByNumber(ctx, record.peer) != null) }

    // Menu items
    val icons = remember(numberInDb) {
        listOf(
            R.drawable.ic_copy,
            R.drawable.ic_regex,
            if (numberInDb) R.drawable.ic_db_delete else R.drawable.ic_db_add,
            R.drawable.ic_filter,
        )
    }
    val labelIds = remember(numberInDb) {
        listOf(
            R.string.copy_number,
            R.string.add_num_to_regex_rule,
            if (numberInDb) R.string.remove_db_number else R.string.add_num_to_db,
            R.string.filter,
        )
    }


    val contextMenuItems = remember(numberInDb) {
        labelIds.mapIndexed { menuIndex, labelId ->
            LabelItem(
                label = ctx.getString(labelId),
                leadingIcon = {
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
                        if (numberInDb) {
                            val spamRecord = SpamTable.findByNumber(ctx, record.peer)
                            if (spamRecord != null)
                                SpamTable.deleteById(ctx, spamRecord.id)
                        } else {
                            SpamTable.add(ctx, record.peer)
                        }
                        Events.spamDbUpdated.fire()
                        // No need to toggle the Indicators,
                        //   because this event will force a list refresh

                        numberInDb = !numberInDb
                    }

                    3 -> { // filter records
                        vm.searchEnabled.value = true
                    }
                }
            }
        }
    }

    DropdownWrapper(items = contextMenuItems) { contextMenuExpanded ->
        content(contextMenuExpanded)
    }
}
