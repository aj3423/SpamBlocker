package spam.blocker.ui.setting.api

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.widgets.ConfigExportDialog
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.util.BotPrettyJson
import spam.blocker.util.spf


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ApiList(vm: ApiViewModel) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val editTrigger = rememberSaveable { mutableStateOf(false) }

    var clickedIndex by rememberSaveable { mutableIntStateOf(-1) }

    if (editTrigger.value) {
        EditApiDialog(
            trigger = editTrigger,
            initial = vm.apis[clickedIndex],
            onSave = { updatedApi ->
                // 1. update in db
                vm.table.updateById(ctx, updatedApi.id, updatedApi)

                // 2. reload UI
                vm.reloadDb(ctx)
            }
        )
    }

    val exportTrigger = remember { mutableStateOf(false) }
    if (exportTrigger.value) {
        ConfigExportDialog(
            trigger = exportTrigger,
            initialText = BotPrettyJson.encodeToString(vm.apis[clickedIndex]),
        )
    }

    val spf = spf.ApiQueryOptions(ctx)
    val priorityTrigger = remember { mutableStateOf(false) }
    PopupDialog(priorityTrigger) {
        var apiPriority by remember { mutableIntStateOf(spf.priority) }
        PriorityBox(apiPriority) { newValue, hasError ->
            if (!hasError) {
                apiPriority = newValue!!
                spf.priority = apiPriority
                G.apiQueryVM.reloadDb(ctx)
            }
        }
    }

    // Context Menu
    val contextMenuItems = mutableListOf<IMenuItem>(
        // Export
        LabelItem(
            label = ctx.getString(R.string.export),
            leadingIcon = { GreyIcon20(R.drawable.ic_export) }
        ) {
            exportTrigger.value = true
        },
    )
    if (vm.forType == Def.ForApiQuery) {
        contextMenuItems.add(
            // Priority
            LabelItem(
                label = ctx.getString(R.string.priority),
                leadingIcon = { ResIcon(R.drawable.ic_priority, modifier = M.size(18.dp), color = LightMagenta) }
            ) {
                priorityTrigger.value = true
            }
        )
    }

    Column(
        modifier = M.nestedScroll(DisableNestedScrolling()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        vm.apis.forEachIndexed { index, api ->
            key(api.id) {
                DropdownWrapper(items = contextMenuItems) { contextMenuExpanded ->

                    LeftDeleteSwipeWrapper(
                        left = SwipeInfo(
                            onSwipe = {
                                val index = vm.apis.indexOfFirst { it.id == api.id }
                                val api = vm.apis[index]

                                // 1. delete from db
                                vm.table.deleteById(ctx, api.id)
                                // 2. remove from UI
                                vm.apis.removeAt(index)
                                // 3. show snackbar
                                SnackBar.show(
                                    coroutineScope,
                                    api.desc,
                                    ctx.getString(R.string.undelete),
                                ) {
                                    // 1. add to db
                                    vm.table.addRecordWithId(ctx, api)
                                    // 2. add to UI
                                    vm.apis.add(index, api)
                                }
                            }
                        )
                    ) {
                        ApiCard(
                            vm.forType,
                            api,
                            modifier = M.combinedClickable(
                                onClick = {
                                    clickedIndex = index
                                    editTrigger.value = true
                                },
                                onLongClick = {
                                    clickedIndex = index
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
