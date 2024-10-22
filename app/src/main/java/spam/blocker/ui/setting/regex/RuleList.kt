package spam.blocker.ui.setting.regex

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.db.ruleTableForType
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LazyScrollbar
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
    vm: RuleViewModel,
) {
    val forType = vm.forType
    val ctx = LocalContext.current
    val coroutine = rememberCoroutineScope()

    val editRuleTrigger = rememberSaveable { mutableStateOf(false) }
    val clickedRule = remember { mutableStateOf(RegexRule()) }

    // Refresh UI on global events, such as workflow action AddToRegexRule
    if (forType == Def.ForNumber) {
        Events.regexRuleUpdated.Listen {
            vm.reloadDb(ctx)
        }
    }

    if (editRuleTrigger.value) {
        RuleEditDialog(
            trigger = editRuleTrigger,
            initRule = clickedRule.value,
            forType = forType,
            onSave = { updatedRule ->
                // 1. update in db
                val table = ruleTableForType(forType)
                table.updateRuleById(ctx, updatedRule.id, updatedRule)

                // 2. reload from db
                vm.reloadDb(ctx)
            }
        )
    }

    // Confirm dialog for "Delete All"
    val confirmDeleteAll = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = confirmDeleteAll,
        content = { GreyLabel(text = Str(R.string.confirm_delete_all_rule), fontSize = 18.sp) },
        buttons = {
            StrokeButton(label = Str(R.string.delete), color = Salmon) {
                confirmDeleteAll.value = false

                val table = ruleTableForType(forType)

                // 1. clear db
                table.clearAll(ctx)

                // 2. refresh gui
                vm.rules.clear()
            }
        }
    )

    // Confirm dialog for "Delete duplicated rules"
    val confirmDeleteDuplicated = remember { mutableStateOf(false) }
    val duplicatedRules = remember { mutableListOf<RegexRule>() }
    PopupDialog(
        trigger = confirmDeleteDuplicated,
        scrollEnabled = false,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                duplicatedRules.clear()
                duplicatedRules.addAll(vm.table.listDuplicated(ctx))

                GreyLabel(
                    text = Str(R.string.confirm_delete_duplicated_rule).format(duplicatedRules.size),
                    fontSize = 18.sp
                )

                val density = LocalDensity.current

                // Calculate x% of the screen width
                val halfScreenDp = remember {
                    with(density) {
                        val screenHeightPx = ctx.resources.displayMetrics.heightPixels
                        screenHeightPx.toDp().value * 0.5
                    }
                }
                LazyColumn(modifier = M.heightIn(max = halfScreenDp.dp)) {
                    items(duplicatedRules, key = { it.id }) {
                        RuleCard(rule = it, forType = forType)
                    }
                }
            }
        },
        buttons = {
            StrokeButton(label = Str(R.string.delete), color = Salmon) {
                confirmDeleteDuplicated.value = false

                val table = ruleTableForType(forType)

                // 1. clear db
                table.deleteByIds(ctx, duplicatedRules.map { it.id })

                // 2. clear cache
                duplicatedRules.clear()

                // 3. refresh gui
                vm.reloadDb(ctx)
            }
        }
    )

    val contextMenuItems =
        ctx.resources.getStringArray(R.array.rule_dropdown_menu).mapIndexed { menuIndex, label ->
            LabelItem(
                label = when (menuIndex) {
                    3 -> label.format(vm.table.count(ctx)) // Delete All(%d) Rules
                    else -> label
                },
            ) {
                when (menuIndex) {
                    0 -> { // search rule
                        vm.searchEnabled.value = true
                    }

                    1 -> { // clone rule
                        // 1. add to db
                        vm.table.addNewRule(ctx, clickedRule.value)

                        // 2. refresh gui
                        vm.reloadDb(ctx)
                    }

                    2 -> { // delete duplicated rules
                        confirmDeleteDuplicated.value = true
                    }

                    3 -> { // delete all rules
                        confirmDeleteAll.value = true
                    }
                }
            }
        }

    // Nested LazyColumn is forbidden in jetpack compose, to workaround this:
    // when < 20 rules:
    //   show as normal Column
    // else
    //   show as LazyColumn with fixed height: 60% height of the screen
    if (vm.rules.size > 20) {
        val density = LocalDensity.current

        // Calculate x% of the screen width
        val halfScreenDp = remember {
            with(density) {
                val screenHeightPx = ctx.resources.displayMetrics.heightPixels
                screenHeightPx.toDp().value * 0.6
            }
        }
        val lazyState = rememberLazyListState()

        LazyScrollbar(
            state = lazyState,
            modifier = M.height(halfScreenDp.dp),
        ) {
            LazyColumn(
                state = lazyState,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = vm.rules,
                    key = { _, it -> it.id }
                ) { i, _ ->
                    RuleItem(
                        coroutine, forType, i, vm.rules,
                        clickedRule, editRuleTrigger, contextMenuItems
                    )
                }
            }
        }
    } else {
        Column(
            modifier = M.nestedScroll(DisableNestedScrolling()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            vm.rules.forEachIndexed { i, rule ->
                key(rule.id) {
                    RuleItem(
                        coroutine, forType, i, vm.rules,
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
                    table.deleteById(ctx, ruleToDel.id)

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
