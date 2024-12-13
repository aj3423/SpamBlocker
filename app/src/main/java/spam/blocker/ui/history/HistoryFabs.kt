package spam.blocker.ui.history

import android.content.Context
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.bot.CleanupHistory
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.serialize
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.spf


private const val HISTORY_CLEANUP_WORK_TAG = "history_cleanup_work_tag"

fun reScheduleHistoryCleanup(ctx: Context) {
    MyWorkManager.cancelByTag(ctx, HISTORY_CLEANUP_WORK_TAG)

    val ttl = spf.HistoryOptions(ctx).getTTL()
    if (ttl > 0) {
        MyWorkManager.schedule(
            ctx,
            scheduleConfig = Daily().serialize(),
            actionsConfig = listOf(CleanupHistory(ttl)).serialize(),
            workTag = HISTORY_CLEANUP_WORK_TAG
        )
    } else if (ttl == 0) {
        // no logging, no need to cleanup
    } else {
        // logging + never expire, no need to cleanup
    }
}


@Composable
fun HistoryFabs(
    modifier: Modifier,
    visible: Boolean,
    vm: HistoryViewModel,
) {
    val ctx = LocalContext.current
    val spf = spf.HistoryOptions(ctx)

    var showPassed by rememberSaveable { mutableStateOf(spf.getShowPassed()) }
    var showBlocked by rememberSaveable { mutableStateOf(spf.getShowBlocked()) }
    var historyTTL by rememberSaveable { mutableIntStateOf(spf.getTTL()) }
    var logSmsContent by rememberSaveable { mutableStateOf(spf.isLogSmsContentEnabled()) }
    var rows by rememberSaveable { mutableStateOf<Int?>(spf.getInitialSmsRowCount()) }

    val settingPopupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = settingPopupTrigger,
        popupSize = PopupSize(percentage = 0.8f, minWidth = 340, maxWidth = 500),
        content = {
            // TTL
            LabeledRow(
                labelId = R.string.expiry,
                helpTooltipId = R.string.help_history_ttl
            ) {
                NumberInputBox(
                    intValue = historyTTL,
                    onValueChange = { newValue, hasError ->
                        if (!hasError) {
                            historyTTL = newValue!!
                            spf.setTTL(newValue)

                            reScheduleHistoryCleanup(ctx)
                        }
                    },
                    label = { Text(Str(R.string.days)) },
                    leadingIconId = R.drawable.ic_recycle_bin,
                )
            }

            // Log SMS Content
            if (vm.forType == Def.ForSms) {
                LabeledRow(
                    labelId = R.string.log_sms_content_to_db,
                    helpTooltipId = R.string.help_log_sms_content
                ) {
                    val trigger = remember { mutableStateOf(false) }
                    PopupDialog(trigger = trigger) {
                        NumberInputBox(
                            label = { GreyLabel(Str(R.string.initial_row_count)) },
                            intValue = rows,
                            onValueChange = { newVal, hasError ->
                                if (!hasError) {
                                    rows = newVal
                                    spf.setInitialSmsRowCount(rows!!)
                                    vm.reload(ctx)
                                }
                            }
                        )
                    }
                    if (logSmsContent) {
                        val nRows = ctx.resources.getQuantityString(R.plurals.rows, rows!!, rows)

                        GreyButton(label = nRows) { trigger.value = true }
                    }
                    SwitchBox(checked = logSmsContent, onCheckedChange = { isOn ->
                        logSmsContent = isOn
                        spf.setLogSmsContentEnabled(isOn)
                        vm.reload(ctx)
                    })
                }
            }
            HorizontalDivider(thickness = 1.dp, color = LocalPalette.current.disabled)
            LabeledRow(labelId = R.string.show_passed) {
                SwitchBox(checked = showPassed, onCheckedChange = { isOn ->
                    showPassed = isOn
                    spf.setShowPassed(isOn)
                    vm.reload(ctx)
                })
            }
            LabeledRow(labelId = R.string.show_blocked) {
                SwitchBox(checked = showBlocked, onCheckedChange = { isOn ->
                    showBlocked = isOn
                    spf.setShowBlocked(isOn)
                    vm.reload(ctx)
                })
            }
        })

    RowVCenterSpaced(space = 8, modifier = modifier) {
        Fab(
            visible = visible,
            iconId = R.drawable.ic_display_filter,
            bgColor = SkyBlue
        ) {
            settingPopupTrigger.value = true
        }

        // Delete all
        val deleteConfirm = remember { mutableStateOf(false) }
        PopupDialog(
            trigger = deleteConfirm,
            buttons = {
                StrokeButton(label = Str(R.string.delete), color = Salmon) {
                    deleteConfirm.value = false
                    when (vm.forType) {
                        Def.ForNumber -> {
                            G.callVM.table.clearAll(ctx)
                            G.callVM.records.clear()
                        }

                        Def.ForSms -> {
                            G.smsVM.table.clearAll(ctx)
                            G.smsVM.records.clear()
                        }
                    }
                }
            }
        ) {
            GreyLabel(Str(R.string.confirm_delete_all_records))
        }
        Fab(
            visible = visible,
            iconId = R.drawable.ic_recycle_bin,
            bgColor = Salmon
        ) {
            deleteConfirm.value = true
        }
    }
}
