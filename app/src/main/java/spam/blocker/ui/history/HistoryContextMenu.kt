package spam.blocker.ui.history

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.HistoryRecord
import spam.blocker.db.RegexRule
import spam.blocker.db.SpamTable
import spam.blocker.db.defaultRegexRuleByType
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.setting.TestDialog
import spam.blocker.ui.setting.regex.EditRegexDialog
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.util.Clipboard
import spam.blocker.util.Util


@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HistoryContextMenuWrapper(
    vm: HistoryViewModel,
    record: HistoryRecord,
    content: @Composable (MutableState<Boolean>) -> Unit,
) {
    val ctx = LocalContext.current
    val C = G.palette

    var menuExpanded by remember { mutableStateOf(false) }

    // Whether the number exists in spam database
    var numberInDb by remember { mutableStateOf(false) }
    // The regex rule that matches this number
    var existingRule by remember { mutableStateOf<RegexRule?>(null) }
    // Context menu items
    var menuItems by remember { mutableStateOf(listOf<IMenuItem>())}

    // trigger of "Add regex rule"/"Edit regex rule"
    val editRuleTrigger = rememberSaveable { mutableStateOf(false) }

    if (editRuleTrigger.value) {
        EditRegexDialog(
            trigger = editRuleTrigger,
            initRule = existingRule ?: RegexRule().apply {
                pattern = record.peer
                patternFlags = Def.FLAG_REGEX_RAW_NUMBER
            },
            forType = Def.ForNumber,
            showDeleteButton = existingRule != null,
            onSave = { newRule ->
                if (existingRule == null) {
                    // 1. add to db
                    G.NumberRuleVM.table.addNewRule(ctx, newRule)
                } else {
                    // 1. update in db
                    G.NumberRuleVM.table.updateRuleById(ctx, existingRule!!.id, newRule)
                }

                // rule update event is handled by the dialog
            }
        )
    }

    // Test
    val testTrigger = rememberSaveable { mutableStateOf(false) }
    TestDialog(testTrigger)

    // Refresh context menu items when it's being expanded
    LaunchedEffect(menuExpanded) {
        if (menuExpanded) { // don't do anything when not expanded, for better performance
            // 1.
            numberInDb = SpamTable.findByNumber(ctx, record.peer) != null

            // 2.
            existingRule = G.NumberRuleVM.table.findRuleByPattern(ctx, record.peer)

            // 3. build context menu
            menuItems = listOf(
                // Test
                LabelItem(
                    label = ctx.getString(R.string.test),
                    leadingIcon = {
                        ResIcon(
                            R.drawable.ic_tube,
                            modifier = M.size(20.dp),
                            color = C.teal200
                        )
                    }
                ) {
                    G.testingVM.apply {
                        selectedType.intValue = if (vm.forType == Def.ForNumber) 0 else 1
                        phone.value = record.peer
                        sms.value = record.extraInfo ?: ""
                    }
                    testTrigger.value = true
                },

                // Copy
                LabelItem(
                    label = ctx.getString(R.string.copy_number),
                    leadingIcon = { GreyIcon20(R.drawable.ic_copy) }
                ) {
                    Clipboard.copy(ctx, record.peer)
                },

                // Add/Edit regex rule
                LabelItem(
                    label = ctx.getString(if (existingRule != null) R.string.edit_regex_rule else R.string.add_to_regex_rule),
                    leadingIcon = {
                        ResIcon(
                            R.drawable.ic_regex,
                            modifier = M.size(20.dp),
                            color = if (existingRule != null) C.infoBlue else C.textGrey
                        )
                    }
                ) {
                    editRuleTrigger.value = true
                },

                // Add/Delete in spam database
                LabelItem(
                    label = ctx.getString(if (numberInDb) R.string.remove_from_db else R.string.add_to_db),
                    leadingIcon = {
                        ResIcon(
                            if (numberInDb) R.drawable.ic_db_delete else R.drawable.ic_db_add,
                            modifier = M.size(20.dp),
                            color = if (numberInDb) C.error else C.textGrey
                        )
                    }
                ) {
                    if (numberInDb) {
                        val spamRecord = SpamTable.findByNumber(ctx, record.peer)
                        if (spamRecord != null)
                            SpamTable.deleteById(ctx, spamRecord.id)
                    } else {
                        SpamTable.add(ctx, record.peer)
                    }
                    Events.spamDbUpdated.fire()
                },

                // Filter
                LabelItem(
                    label = ctx.getString(R.string.filter),
                    leadingIcon = { GreyIcon20(R.drawable.ic_filter) }
                ) {
                    vm.searchEnabled.value = true
                }
            )
        }
    }

    DropdownWrapper(
        items = menuItems,
        content = { contextMenuExpanded ->
            // Refresh all menu items on expanding
            LaunchedEffect(contextMenuExpanded.value) {
                menuExpanded = contextMenuExpanded.value
            }
            content(contextMenuExpanded)
        }
    )
}
