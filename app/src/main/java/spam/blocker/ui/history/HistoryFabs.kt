package spam.blocker.ui.history

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.PruneHistory
import spam.blocker.service.bot.serialize
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
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
            actionsConfig = listOf(PruneHistory(ttl)).serialize(),
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
    val C = LocalPalette.current
    val ctx = LocalContext.current
    val spf = spf.HistoryOptions(ctx)

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
            Column {
                // Logging enabled / TTL
                Section(
                    Str(R.string.enable_history_logging),
                    bgColor = C.dialogBg
                ) {
                    Column {
                        LabeledRow(
                            labelId = R.string.enable,
                            helpTooltip = Str(R.string.help_history_logging)
                        ) {
                            // Enabled
                            SwitchBox(checked = loggingEnabled, onCheckedChange = { isOn ->
                                spf.setLoggingEnabled(isOn)
                                loggingEnabled = isOn

                                reScheduleHistoryCleanup(ctx)
                            })
                        }

                        // Log SMS Content
                        AnimatedVisibleV(loggingEnabled && vm.forType == Def.ForSms) {
                            LabeledRow(
                                labelId = R.string.sms_content,
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

                        // Expiry
                        AnimatedVisibleV(loggingEnabled) {
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

                            LabeledRow(
                                labelId = R.string.expiry,
                                helpTooltip = Str(R.string.help_history_expiry)
                            ) {
                                GreyButton(
                                    label = if (expiryEnabled) {
                                        ctx.resources.getQuantityString(R.plurals.days, ttl, ttl)
                                    } else {
                                        Str(R.string.never_expire)
                                    }
                                ) {
                                    trigger.value = true
                                }
                            }
                        }
                    }
                }

                Section(
                    Str(R.string.show_hide),
                    bgColor = C.dialogBg
                ) {
                    Column {
                        // Allowed
                        LabeledRow(labelId = R.string.allowed_records) {
                            SwitchBox(checked = G.showHistoryPassed.value, onCheckedChange = { isOn ->
                                spf.setShowPassed(isOn)
                                G.showHistoryPassed.value = isOn
                            })
                        }
                        // Blocked
                        LabeledRow(labelId = R.string.blocked_records) {
                            SwitchBox(checked = G.showHistoryBlocked.value, onCheckedChange = { isOn ->
                                spf.setShowBlocked(isOn)
                                G.showHistoryBlocked.value = isOn
                            })
                        }

                        // Rule Indicator
                        LabeledRow(
                            labelId = R.string.rule_indicator,
                            helpTooltip = Str(R.string.help_show_rule_indicator),
                        ) {
                            SwitchBox(checked = G.showHistoryIndicator.value, onCheckedChange = { isOn ->
                                spf.setShowIndicator(isOn)
                                G.showHistoryIndicator.value = isOn
                            })
                        }

                        // SIM slot
                        LabeledRow(
                            labelId = R.string.sim_icon,
                            helpTooltip = Str(R.string.help_show_sim_icon),
                        ) {
                            val items = remember {
                                listOf(R.string.automatic, R.string.always)
                                    .mapIndexed { idx, strId ->
                                        LabelItem(label = ctx.getString(strId)) {
                                            when (idx) {
                                                0 -> {
                                                    spf.setForceShowSim(false)
                                                    G.forceShowSIM.value = false
                                                }
                                                1 -> {
                                                    G.permissionChain.ask(
                                                        ctx,
                                                        listOf(PermissionWrapper(Permission.phoneState))
                                                    ) { granted ->
                                                        if (granted) {
                                                            spf.setForceShowSim(true)
                                                            G.forceShowSIM.value = true
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                            }
                            var selected by remember(G.forceShowSIM.value) {
                                mutableIntStateOf(
                                    if (G.forceShowSIM.value) 1 else 0
                                )
                            }
                            ComboBox(
                                items = items,
                                selected = selected,
                            )
                        }

                        // Geo Location
                        LabeledRow(
                            labelId = R.string.geo_location,
                        ) {
                            SwitchBox(checked = G.showHistoryGeoLocation.value, onCheckedChange = { isOn ->
                                spf.setShowGeoLocation(isOn)
                                G.showHistoryGeoLocation.value = isOn
                            })
                        }
                    }
                }
            }
        }
    )

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
