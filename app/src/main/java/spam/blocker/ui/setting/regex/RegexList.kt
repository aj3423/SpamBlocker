package spam.blocker.ui.setting.regex

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.db.ruleTableForType
import spam.blocker.ui.M
import spam.blocker.ui.maxScreenHeight
import spam.blocker.ui.screenHeightDp
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LazyScrollbar
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.util.spf


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
fun RegexSettingsPopup(
    trigger: MutableState<Boolean>,
    vm: RegexViewModel,
) {
    val ctx = LocalContext.current
    var dirty by rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = trigger,
        onDismiss = {
            if (dirty)
                vm.reloadDb(ctx)
        }
    ) {
        val spf = spf.RegexOptions(ctx)

        // max none scroll items: []
        LabeledRow(
            labelId = null,
            helpTooltip = Str(R.string.help_max_none_scroll_items)
        ) {
            var max by remember { mutableIntStateOf(spf.maxNoneScrollRows) }
            NumberInputBox(
                intValue = max,
                label = { Text(Str(R.string.label_max_none_scroll_items)) },
                onValueChange = { newVal, hasError ->
                    if (!hasError) {
                        max = newVal!!
                        spf.maxNoneScrollRows = max
                        dirty = true
                    }
                }
            )
        }

        // max scroll height: []
        LabeledRow(
            labelId = null,
            helpTooltip = Str(R.string.help_max_scroll_height)
        ) {
            var height by remember { mutableIntStateOf(spf.ruleListHeightPercentage) }
            NumberInputBox(
                intValue = height,
                label = { Text(Str(R.string.label_max_scroll_height)) },
                onValueChange = { newVal, hasError ->
                    if (!hasError) {
                        height = newVal!!
                        spf.ruleListHeightPercentage = height
                        dirty = true
                    }
                }
            )
        }

        // max regex lines: []
        LabeledRow(
            labelId = null,
            helpTooltip = Str(R.string.help_max_regex_lines)
        ) {
            var rows by remember { mutableIntStateOf(spf.maxRegexRows) }
            NumberInputBox(
                intValue = rows,
                label = { Text(Str(R.string.label_max_regex_lines)) },
                onValueChange = { newVal, hasError ->
                    if (!hasError) {
                        rows = newVal!!
                        spf.maxRegexRows = rows
                        dirty = true
                    }
                }
            )
        }

        // max description lines: []
        LabeledRow(
            labelId = null,
            helpTooltip = Str(R.string.help_max_desc_lines)
        ) {
            var rows by remember { mutableIntStateOf(spf.maxDescRows) }
            NumberInputBox(
                intValue = rows,
                label = { Text(Str(R.string.label_max_desc_lines)) },
                onValueChange = { newVal, hasError ->
                    if (!hasError) {
                        rows = newVal!!
                        spf.maxDescRows = rows
                        dirty = true
                    }
                }
            )
        }
    }
}

@Composable
fun RegexList(
    vm: RegexViewModel,
) {
    val forType = vm.forType
    val ctx = LocalContext.current
    val spf = spf.RegexOptions(ctx)
    val coroutine = rememberCoroutineScope()

    val ruleSettingsTrigger = rememberSaveable { mutableStateOf(false) }
    RegexSettingsPopup(trigger = ruleSettingsTrigger, vm = vm)

    val editRuleTrigger = rememberSaveable { mutableStateOf(false) }
    val clickedRule = remember { mutableStateOf(RegexRule()) }

    // Refresh UI on global events, such as workflow action AddToRegexRule
    Events.regexRuleUpdated.Listen {
        vm.reloadDb(ctx)
    }

    if (editRuleTrigger.value) {
        EditRegexDialog(
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

                GreyText(
                    text = Str(R.string.confirm_delete_duplicated_rule).format(duplicatedRules.size),
                    fontSize = 18.sp
                )

                LazyColumn(modifier = M.maxScreenHeight(0.5f)) {
                    items(duplicatedRules, key = { it.id }) {
                        RegexCard(rule = it, forType = forType)
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


    // Suggest to swipe instead of "Delete Rule" menu item
    val suggestSwipeToDelTrigger = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = suggestSwipeToDelTrigger,
    ) {
        HtmlText(Str(R.string.suggest_to_swipe))
    }

    val icons = listOf(
        R.drawable.ic_find,
        R.drawable.ic_copy,
        R.drawable.ic_recycle_bin,
        R.drawable.ic_recycle_bin,
        R.drawable.ic_recycle_bin
    )
    val contextMenuItems =
        ctx.resources.getStringArray(R.array.rule_dropdown_menu).mapIndexed { menuIndex, label ->
            LabelItem(
                label = when (menuIndex) {
                    4 -> label.format(vm.table.count(ctx)) // Delete All(%d) Rules
                    else -> label
                },
                leadingIcon = { GreyIcon20(icons[menuIndex]) }
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

                    2 -> { // delete a rule
                        // Show prompt for swipe right
                        suggestSwipeToDelTrigger.value = true
                    }
                    3 -> { // delete duplicated rules
                        confirmDeleteDuplicated.value = true
                    }

                    4 -> { // delete all rules
                        confirmDeleteAll.value = true
                    }
                }
            } as IMenuItem
        }.toMutableList()
    contextMenuItems += DividerItem()
    contextMenuItems += LabelItem(
        label = Str(R.string.setting),
        leadingIcon = { GreyIcon20(R.drawable.ic_settings) }
    ) {
        ruleSettingsTrigger.value = true
    }

    // Nested scrollable Column/LazyColumn is forbidden in jetpack compose, to workaround this:
    // when < 10 rules:
    //   show as normal Column (dynamic height)
    // else
    //   show as LazyColumn (Fixed height)
    val useLazy = vm.rules.size > spf.maxNoneScrollRows
    if (useLazy) { // LazyColumn
        val lazyState = rememberLazyListState()

        // Calculate x% of the screen height
        val percentage = spf.ruleListHeightPercentage

        LazyScrollbar(
            state = lazyState,
            modifier = M.height((screenHeightDp() * percentage / 100).dp),
        ) {
            LazyColumn(
                state = lazyState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = vm.rules,
                    key = { _, it -> it.id }
                ) { i, rule ->
                    RegexItem(
                        coroutine, forType, rule, i, vm.rules,
                        clickedRule, editRuleTrigger, contextMenuItems
                    )
                }
            }
        }
    } else { // normal column
        Column(
            modifier = M.nestedScroll(DisableNestedScrolling()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            vm.rules.forEachIndexed { i, rule ->
                key(rule.id) {
                    RegexItem(
                        coroutine, forType, rule, i, vm.rules,
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
fun RegexItem(
    coroutineScope: CoroutineScope,
    forType: Int,
    rule: RegexRule,
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
                    val index = ruleList.indexOfFirst { it.id == rule.id  }
                    val ruleToDel = ruleList[index]

                    val table = ruleTableForType(forType)

                    // 1. delete from db
                    table.deleteById(ctx, ruleToDel.id)

                    // 2. remove from ArrayList
                    ruleList.removeAt(index)

                    // 3. show snackbar
                    SnackBar.show(
                        coroutineScope,
                        ruleToDel.pattern,
                        ctx.getString(R.string.undelete),
                    ) {
                        table.addRuleWithId(ctx, ruleToDel)
                        ruleList.add(index, ruleToDel)
                    }
                }
            )
        ) {
            RegexCard(
                rule = rule,
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
