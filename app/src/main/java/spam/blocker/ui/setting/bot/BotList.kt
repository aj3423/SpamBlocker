package spam.blocker.ui.setting.bot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkInfo.Companion.STOP_REASON_NOT_STOPPED
import androidx.work.WorkInfo.State.ENQUEUED
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.db.reScheduleBot
import spam.blocker.db.rememberSaveableBotState
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.Schedule
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.ConfigExportDialog
import spam.blocker.ui.widgets.CustomItem
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.util.A
import spam.blocker.util.BotPrettyJson
import spam.blocker.util.PermissiveJson
import spam.blocker.util.SaveableLogger
import spam.blocker.util.TimeUtils.durationString
import spam.blocker.util.TimeUtils.formatTime
import spam.blocker.util.applyAnnotatedMarkups
import spam.blocker.util.formatAnnotated
import java.time.Duration
import java.time.Instant


@Composable
fun CountdownMenuItem(bot: Bot) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var nextTick by remember { mutableLongStateOf(0) }

    var isFinished by remember { mutableStateOf(false) }
    // BLOCKED,CANCELLED,ENQUEUED,FAILED,RUNNING,SUCCEEDED
    var state by remember { mutableStateOf<WorkInfo.State>(ENQUEUED) }
    // https://developer.android.com/reference/androidx/work/WorkInfo#STOP_REASON_NOT_STOPPED()
    var stopReason by remember { mutableIntStateOf(STOP_REASON_NOT_STOPPED) }

    var label by remember { mutableStateOf("") }

    fun refreshLabel() {
        label = if (isFinished) { // Show stop reason
            val stateStr = ctx.getString(R.string.work_state_template)
                .format("$state")
            val stopReasonStr = ctx.getString(R.string.stop_reason_template)
                .format("$stopReason")

            "$stateStr\n$stopReasonStr"
        } else { // Show countdown if it's still running
            durationString(
                ctx,
                Duration.between(Instant.now(), Instant.ofEpochMilli(nextTick))
            )
        }
    }

    LaunchedEffect(true) {
        if (bot.trigger is Schedule) {
            MyWorkManager.getWorkInfoByTag(ctx, bot.trigger.workUUID)?.let {
                nextTick = it.nextScheduleTimeMillis

                isFinished = it.state.isFinished
                state = it.state
                stopReason = it.stopReason

                refreshLabel()
            }
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

        GreyLabel(text = label, modifier = M.weight(1f), maxLines = 3)
    }
}

@Composable
fun BotLog(
    trigger: MutableState<Boolean>,
    logJson: String,
    logTime: Long,
) {
    val ctx = LocalContext.current

    val annotatedLog = remember {
        try {
            val logger = PermissiveJson.decodeFromString<SaveableLogger>(logJson)
            logger.text.applyAnnotatedMarkups(logger.markups)
        } catch (_: Exception) {
            AnnotatedString("")
        }
    }


    PopupDialog(
        trigger = trigger,
    ) {
        if (logTime == 0L) {
            Text(
                text = Str(R.string.not_executed_yet).A(),
                color = LocalPalette.current.textGrey,
            )
        } else {
            Text(
                text = "${Str(R.string.executed_at)} ${formatTime(ctx, logTime)}\n\n"
                    .formatAnnotated(annotatedLog)
                ,
                color = LocalPalette.current.textGrey,
            )
        }
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

    var clickedBot by rememberSaveableBotState(Bot())

    // Edit
    val editTrigger = rememberSaveable { mutableStateOf(false) }
    if (editTrigger.value) {
        EditBotDialog(
            popupTrigger = editTrigger,
            initialBot = clickedBot,
            onDismiss = { vm.reload(ctx) },
            onSave = { updatedBot ->
                // 1. update in db
                BotTable.updateById(
                    ctx,
                    updatedBot.id,
                    trigger = updatedBot.trigger,
                    actions = updatedBot.actions,
                    desc = updatedBot.desc
                )

                // 2. reload UI
                vm.reload(ctx)
            }
        )
    }

    // View Log
    val logTrigger = rememberSaveable { mutableStateOf(false) }
    if (logTrigger.value) {
        val log = BotTable.getLastLog(ctx, clickedBot.id)!!
        BotLog(
            trigger = logTrigger,
            logJson = log.first,
            logTime = log.second,
        )
    }

    // Export
    val exportTrigger = remember { mutableStateOf(false) }
    if (exportTrigger.value) {
        ConfigExportDialog(
            trigger = exportTrigger,
            initialText = BotPrettyJson.encodeToString(clickedBot),
        )
    }

    val contextMenuItems = mutableListOf<IMenuItem>()
    // countdown timer for scheduled bot
    if ((clickedBot.trigger as? Schedule)?.enabled == true) {
        contextMenuItems += CustomItem { CountdownMenuItem(bot = clickedBot) }
        contextMenuItems += DividerItem()
    }

    val labels = listOf(
        ctx.getString(R.string.last_log),
        ctx.getString(R.string.export),
    )
    val icons = listOf(
        R.drawable.ic_log,
        R.drawable.ic_export,
    )
    labels.forEachIndexed { menuIndex, label ->
        contextMenuItems += LabelItem(
            label = label,
            leadingIcon = { GreyIcon20(icons[menuIndex]) }
        ) {
            when (menuIndex) {
                0 -> { logTrigger.value = true } // View log
                1 -> { exportTrigger.value = true } // Export
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
                                val index = vm.bots.indexOfFirst { it.id == bot.id }
                                val bot = vm.bots[index]

                                // 1. delete from db
                                BotTable.deleteById(ctx, bot.id)
                                // 2. remove from UI
                                vm.bots.removeAt(index)
                                // 3. Stop previous schedule
                                if (bot.trigger is Schedule) {
                                    MyWorkManager.cancelByTag(ctx, bot.trigger.workUUID)
                                }
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
                                    if (bot.trigger is Schedule) {
                                        reScheduleBot(ctx, bot)
                                    }
                                }
                            }
                        )
                    ) {
                        BotCard(
                            bot,
                            modifier = M.combinedClickable(
                                onClick = {
                                    clickedBot = bot
                                    editTrigger.value = true
                                },
                                onLongClick = {
                                    clickedBot = bot
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
