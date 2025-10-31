package spam.blocker.ui.history

import android.content.Context
import androidx.compose.material3.HorizontalDivider
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
import spam.blocker.ui.widgets.GreyText
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

    val spf = spf.HistoryOptions(ctx)

    val loggingEnabled = spf.isLoggingEnabled()
    val expiryEnabled = spf.isExpiryEnabled()
    val ttl = spf.getTTL()

    if (loggingEnabled && expiryEnabled && ttl >= 0) {
        MyWorkManager.schedule(
            ctx,
            scheduleConfig = Daily().serialize(),
            actionsConfig = listOf(CleanupHistory(ttl)).serialize(),
            workTag = HISTORY_CLEANUP_WORK_TAG
        )
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
    var showIndicator by rememberSaveable { mutableStateOf(spf.getShowIndicator()) }

    var loggingEnabled by remember { mutableStateOf(spf.isLoggingEnabled()) }
    var expiryEnabled by remember { mutableStateOf(spf.isExpiryEnabled()) }
    var ttl by rememberSaveable { mutableIntStateOf(spf.getTTL()) }
    var logSmsContent by rememberSaveable { mutableStateOf(spf.isLogSmsContentEnabled()) }
    var rows by rememberSaveable { mutableStateOf<Int?>(spf.getInitialSmsRowCount()) }

    val settingPopupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = settingPopupTrigger,
        popupSize = PopupSize(percentage = 0.8f, minWidth = 340, maxWidth = 500),
        content = {
            // Logging enabled / TTL
            LabeledRow(
                labelId = R.string.enable_history_logging,
                helpTooltip = Str(R.string.help_history_logging)
            ) {
                if (loggingEnabled) {
                    val trigger = remember { mutableStateOf(false) }
                    PopupDialog(
                        trigger = trigger,
                        onDismiss = {
                            reScheduleHistoryCleanup(ctx)
                        }
                    ) {
                        // Expiry Enabled
                        LabeledRow(R.string.expiry) {
                            SwitchBox(checked = expiryEnabled, onCheckedChange = { isOn ->
                                spf.setExpiryEnabled(isOn)
                                expiryEnabled = isOn
                            })
                        }

                        // Expiry days
                        if (expiryEnabled) {
                            NumberInputBox(
                                intValue = ttl,
                                onValueChange = { newValue, hasError ->
                                    if (!hasError) {
                                        ttl = newValue!!
                                        spf.setTTL(newValue)
                                    }
                                },
                                labelId = R.string.days,
                                leadingIconId = R.drawable.ic_recycle_bin,
                            )
                        }
                    }

                    // Button
                    GreyButton(
                        label = if (expiryEnabled) {
                            ctx.resources.getQuantityString(R.plurals.days, ttl, ttl)
                        } else {
                            Str(R.string.never_expire)
                        }
                    ) { trigger.value = true }
                }

                // Enabled
                SwitchBox(checked = loggingEnabled, onCheckedChange = { isOn ->
                    spf.setLoggingEnabled(isOn)
                    loggingEnabled = isOn

                    reScheduleHistoryCleanup(ctx)
                })
            }

            // Log SMS Content
            if (vm.forType == Def.ForSms) {
                LabeledRow(
                    labelId = R.string.log_sms_content_to_db,
                    helpTooltip = Str(R.string.help_log_sms_content)
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
                    G.showHistoryPassed.value = isOn
                })
            }
            LabeledRow(labelId = R.string.show_blocked) {
                SwitchBox(checked = showBlocked, onCheckedChange = { isOn ->
                    showBlocked = isOn
                    spf.setShowBlocked(isOn)
                    G.showHistoryBlocked.value = isOn
                })
            }
            HorizontalDivider(thickness = 1.dp, color = LocalPalette.current.disabled)

            LabeledRow(
                labelId = R.string.show_indicator,
                helpTooltip = Str(R.string.help_show_indicator),
            ) {
                SwitchBox(checked = showIndicator, onCheckedChange = { isOn ->
                    showIndicator = isOn
                    spf.setShowIndicator(isOn)
                    G.showHistoryIndicator.value = showIndicator
                })
            }
        })

    RowVCenterSpaced(space = 8, modifier = modifier) {
        Fab(
            visible = visible,
            iconId = R.drawable.ic_settings,
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
            GreyText(Str(R.string.confirm_delete_all_records))
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
