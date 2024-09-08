package spam.blocker.ui.setting.regex

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.db.ruleTableForType
import spam.blocker.ui.M
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo


// From:
//   https://stackoverflow.com/a/75450792/2219196
class DisableNestedScrolling : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource
    ) = available.copy(x = 0f)

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ) = available.copy(x = 0f)
}

@Composable
fun RuleList(
    forType: Int,
    ruleList: SnapshotStateList<RegexRule>,
) {
    val ctx = LocalContext.current
    val coroutine = rememberCoroutineScope()

    val editRuleTrigger = rememberSaveable { mutableStateOf(false) }
    val clickedRule = remember { mutableStateOf(RegexRule()) }

    if (editRuleTrigger.value) {
        RuleEditDialog(
            trigger = editRuleTrigger,
            initRule = clickedRule.value,
            forType = forType,
            onSave = { newRule ->
                // 1. update in db
                val table = ruleTableForType(forType)
                table.updateRuleById(ctx, newRule.id, newRule)

                // 2. reload from db
                ruleList.clear()
                ruleList.addAll(table.listAll(ctx))
            }
        )
    }

    // Confirm dialog for "Delete All"
    val confirmDeleteAll = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = confirmDeleteAll,
        content = { GreyLabel(text = Str(R.string.confirm_delete_all_rule), fontSize = 18.sp) },
        buttons = {
            StrokeButton(label = Str(R.string.delete), color = DarkOrange) {
                confirmDeleteAll.value = false

                val table = ruleTableForType(forType)

                // 1. clear db
                table.clearAll(ctx)

                // 2. refresh gui
                ruleList.clear()
            }
        }
    )


    val contextMenuItems = remember {
        ctx.resources.getStringArray(R.array.rule_dropdown_menu).mapIndexed { menuIndex, label ->
            LabelItem(
                label = label,
            ) {
                val table = ruleTableForType(forType)
                when (menuIndex) {
                    0 -> { // clone rule
                        // 1. add to db
                        table.addNewRule(ctx, clickedRule.value)

                        // 2. refresh gui
                        ruleList.clear()
                        ruleList.addAll(table.listAll(ctx))
                    }

                    1 -> { // delete all rules
                        confirmDeleteAll.value = true
                    }
                }
            }
        }
    }

    // Nested LazyColumn is forbidden in jetpack compose, to workaround this:
    // when < 20 rules:
    //   show as normal column
    // else
    //   show as LazyColumn with fixed height: 70% height of the screen
    if (ruleList.size > 20) {
        val density = LocalDensity.current

        // Calculate x% of the screen width
        val halfScreenDp = remember {
            with(density) {
                val screenHeightPx = ctx.resources.displayMetrics.heightPixels
                screenHeightPx.toDp().value * 0.7
            }
        }

        LazyColumn(
            modifier = M.height(halfScreenDp.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(
                items = ruleList,
                key = { _, it -> it.id }
            ) { i, rule ->
                RuleItem(
                    coroutine, forType, i, ruleList,
                    clickedRule, editRuleTrigger, contextMenuItems
                )
            }
        }
    } else {
        Column(
            modifier = M.nestedScroll(DisableNestedScrolling()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ruleList.forEachIndexed { i, rule ->
                key(rule.id) {
                    RuleItem(
                        coroutine, forType, i, ruleList,
                        clickedRule, editRuleTrigger, contextMenuItems
                    )
                }
            }
        }
    }
}


// A wrapper for RuleCard to make it swipeable and clickable(short and long)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RuleItem(
    coroutineScope: CoroutineScope,
    forType: Int,
    ruleIndex: Int,
    ruleList: SnapshotStateList<RegexRule>,
    clickedRuleState: MutableState<RegexRule>,
    editRuleTrigger: MutableState<Boolean>,
    contextMenuItems: List<IMenuItem>,
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    DropdownWrapper(items = contextMenuItems) { contextMenuExpanded ->
        LeftDeleteSwipeWrapper(
            left = SwipeInfo(
                onSwipe = {
                    val ruleToDel = ruleList[ruleIndex]
                    val table = ruleTableForType(forType)

                    // 1. delete from db
                    table.delById(ctx, ruleToDel.id)

                    // 2. remove from ArrayList
                    ruleList.removeAt(ruleIndex)

                    // 3. show snackbar
                    SnackBar.show(
                        coroutineScope,
                        ruleToDel.pattern,
                        ctx.getString(R.string.undelete),
                    ) {
                        table.addRuleWithId(ctx, ruleToDel)
                        ruleList.add(ruleIndex, ruleToDel)
                    }
                }
            )
        ) {
            RuleCard(
                rule = ruleList[ruleIndex],
                forType = forType,
                modifier = M
                    .combinedClickable(
                        onClick = {
                            clickedRuleState.value = ruleList[ruleIndex]
                            editRuleTrigger.value = true
                        },
                        onLongClick = {
                            clickedRuleState.value = ruleList[ruleIndex]
                            contextMenuExpanded.value = true
                        }
                    )
            )
        }
    }

}
