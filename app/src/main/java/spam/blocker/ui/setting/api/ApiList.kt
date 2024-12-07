package spam.blocker.ui.setting.api

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import kotlinx.serialization.encodeToString
import spam.blocker.R
import spam.blocker.service.bot.botPrettyJson
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.widgets.ConfigExportDialog
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeInfo


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
            initialText = botPrettyJson.encodeToString(vm.apis[clickedIndex]),
        )
    }

    val contextMenuItems = mutableListOf<IMenuItem>(
        LabelItem(
            label = ctx.getString(R.string.export),
            icon = { GreyIcon20(R.drawable.ic_backup_export) }
        ) {
            exportTrigger.value = true
        },
    )

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
