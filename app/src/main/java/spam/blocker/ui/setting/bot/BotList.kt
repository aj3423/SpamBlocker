package spam.blocker.ui.setting.bot

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
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.BotTable
import spam.blocker.db.reScheduleBot
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.botPrettyJson
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BotList() {
    val ctx = LocalContext.current
    val vm = G.botVM
    val coroutineScope = rememberCoroutineScope()

    val editTrigger = rememberSaveable { mutableStateOf(false) }

    var clickedIndex by rememberSaveable { mutableIntStateOf(0) }

    if (editTrigger.value) {
        EditBotDialog(
            trigger = editTrigger,
            initial = vm.list[clickedIndex],
            onSave = { updatedBot ->
                // 1. update in db
                BotTable.updateById(ctx, updatedBot.id, updatedBot)

                // 2. reload UI
                vm.reload(ctx)
            }
        )
    }

    val exportTrigger = remember { mutableStateOf(false) }
    if (exportTrigger.value) {
        BotImportExportDialog(
            trigger = exportTrigger,
            isExport = true,
            initialText = botPrettyJson.encodeToString(vm.list[clickedIndex]),
        )
    }

    val menuLabels = listOf(
        R.string.export
    )
    val menuIcons = listOf(
        R.drawable.ic_backup_export
    )
    val contextMenuItems = menuLabels.mapIndexed { menuIndex, label ->
        LabelItem(
            label = ctx.getString(label),
            icon = { GreyIcon16(menuIcons[menuIndex]) }
        ) {
            when (menuIndex) {
                0 -> { // export
                    exportTrigger.value = true
                }
            }
        }
    }

    Column(
        modifier = M.nestedScroll(DisableNestedScrolling()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        vm.list.forEachIndexed { index, bot ->
            key(bot.id) {
                DropdownWrapper(items = contextMenuItems) { contextMenuExpanded ->

                    LeftDeleteSwipeWrapper(
                        left = SwipeInfo(
                            onSwipe = {
                                // 1. delete from db
                                BotTable.deleteById(ctx, bot.id)
                                // 2. remove from UI
                                vm.list.removeAt(index)
                                // 3. Stop previous schedule
                                MyWorkManager.cancelByTag(ctx, bot.workUUID)

                                // 4. show snackbar
                                SnackBar.show(
                                    coroutineScope,
                                    bot.desc,
                                    ctx.getString(R.string.undelete),
                                ) {
                                    // 1. add to db
                                    BotTable.addRecordWithId(ctx, bot)
                                    // 2. add to UI
                                    vm.list.add(index, bot)
                                    // 3. re-schedule
                                    reScheduleBot(ctx, bot)
                                }
                            }
                        )
                    ) {
                        BotCard(
                            bot,
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
