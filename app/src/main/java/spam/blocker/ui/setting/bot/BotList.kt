package spam.blocker.ui.setting.bot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.db.reScheduleBot
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.botPrettyJson
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.widgets.ConfigExportDialog
import spam.blocker.ui.widgets.CustomItem
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.util.Util
import java.time.Duration
import java.time.Instant


@Composable
fun CountdownMenuItem(bot: Bot) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var nextTick by remember { mutableLongStateOf(0) }

    var label by remember { mutableStateOf("" ) }

    fun refreshLabel() {
        label = Util.durationString(
            ctx,
            Duration.between(Instant.now(), Instant.ofEpochMilli(nextTick))
        )
    }

    LaunchedEffect(true) {
        MyWorkManager.getWorkInfoByTag(ctx, bot.workUUID)?.let {
            nextTick = it.nextScheduleTimeMillis
            refreshLabel()
        }
    }

    DisposableEffect(true) {
        val job = coroutineScope.launch {
            withContext(IO) {
                while (true) {
                    delay(500) // Delay for 1 second
                    refreshLabel()
                }
            }
        }
        onDispose {
            job.cancel()
        }
    }

    RowVCenterSpaced(10) {
        GreyIcon20(R.drawable.ic_hourglass)

        GreyLabel(text = label, modifier = M.weight(1f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BotList() {
    val ctx = LocalContext.current
    val vm = G.botVM
    val coroutineScope = rememberCoroutineScope()


    // workflow actions may update bots
    Events.botUpdated.Listen {
        vm.reload(ctx)
    }

    val editTrigger = rememberSaveable { mutableStateOf(false) }

    var clickedIndex by rememberSaveable { mutableIntStateOf(-1) }

    if (editTrigger.value) {
        EditBotDialog(
            trigger = editTrigger,
            initial = vm.bots[clickedIndex],
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
        ConfigExportDialog(
            trigger = exportTrigger,
            initialText = botPrettyJson.encodeToString(vm.bots[clickedIndex]),
        )
    }

    val contextMenuItems = mutableListOf<IMenuItem>()
    // countdown timer for scheduled bot
    if (clickedIndex >= 0 && clickedIndex < vm.bots.size && vm.bots[clickedIndex].enabled) {
        contextMenuItems += CustomItem { CountdownMenuItem(bot = vm.bots[clickedIndex]) }
        contextMenuItems += DividerItem()
    }

    val labels = listOf(
        ctx.getString(R.string.export),
    )
    val icons = listOf(
        R.drawable.ic_backup_export,
    )
    labels.forEachIndexed { menuIndex, label ->
        contextMenuItems += LabelItem(
            label = label,
            icon = { GreyIcon20(icons[menuIndex]) }
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
        vm.bots.forEachIndexed { index, bot ->
            key(bot.id) {
                DropdownWrapper(items = contextMenuItems) { contextMenuExpanded ->

                    LeftDeleteSwipeWrapper(
                        left = SwipeInfo(
                            onSwipe = {
                                // 1. delete from db
                                BotTable.deleteById(ctx, bot.id)
                                // 2. remove from UI
                                vm.bots.removeAt(index)
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
                                    vm.bots.add(index, bot)
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
