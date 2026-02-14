package spam.blocker.ui.history

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.PruneHistory
import spam.blocker.service.bot.serialize
import spam.blocker.ui.history.HistoryOptions.forceShowSIM
import spam.blocker.ui.history.HistoryOptions.historyTimeColors
import spam.blocker.ui.history.HistoryOptions.showHistoryBlocked
import spam.blocker.ui.history.HistoryOptions.showHistoryGeoLocation
import spam.blocker.ui.history.HistoryOptions.showHistoryIndicator
import spam.blocker.ui.history.HistoryOptions.showHistoryPassed
import spam.blocker.ui.history.HistoryOptions.showHistoryTimeColor
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.ColorPickerButton
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.DimGreyText
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MultiColorButton
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Lambda2
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.TimeUtils.FreshnessColor
import spam.blocker.util.spf


private const val HISTORY_CLEANUP_WORK_TAG = "history_cleanup_work_tag"

fun reScheduleHistoryCleanup(ctx: Context) {
    MyWorkManager.cancelByTag(ctx, HISTORY_CLEANUP_WORK_TAG)

    val spf = spf.HistoryOptions(ctx)

    val loggingEnabled = spf.isLoggingEnabled
    val expiryEnabled = spf.isExpiryEnabled
    val ttl = spf.ttl

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
fun EditSingleTimeColorsDialog(
    trigger: MutableState<Boolean>,
    initial: FreshnessColor,
    onResult: Lambda2<FreshnessColor?, Boolean> // Pair<new item, isDelete, isAddOrEdit>
) {
    var dur by remember(initial) { mutableStateOf(initial.durationMin) }
    var color by remember(initial) { mutableIntStateOf(initial.argb) }

    PopupDialog(
        trigger = trigger,
        buttons = {
            RowVCenterSpaced(8) {
                StrokeButton(
                    label = Str(R.string.delete),
                    color = Salmon,
                ) {
                    trigger.value = false
                    onResult(null, true)
                }
                StrokeButton(
                    label = Str(R.string.ok),
                    color = Teal200,
                ) {
                    trigger.value = false

                    val r = FreshnessColor(dur, color)
                    if (r.isValid())
                        onResult(r, false)
                    else
                        onResult(null, false)
                }
            }
        }
    ) {
        StrInputBox(
            text = initial.durationMin,
            label = { Text(Str(R.string.duration)) },
            leadingIconId = R.drawable.ic_duration,
            supportingTextStr = if(!FreshnessColor(dur).isValid()) Str(R.string.invalid_value_see_tooltip) else null,
            placeholder = { DimGreyText("10min") },
            helpTooltip = Str(R.string.help_time_color_values),
            onValueChange = { dur = it }
        )
        LabeledRow(
            labelId = R.string.time_color,
        ) {
            ColorPickerButton(
                color = color,
            ) {
                it?.let { color = it }
            }
        }
    }
}

@Composable
fun EditTimeColorsDialog(
    trigger: MutableState<Boolean>,
    timeColors: SnapshotStateList<FreshnessColor>,
) {
    val ctx = LocalContext.current
    val spf = spf.HistoryOptions(ctx)

    var editing by remember { mutableStateOf(FreshnessColor()) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val editSingleTrigger = remember { mutableStateOf(false) }

    EditSingleTimeColorsDialog(
        trigger = editSingleTrigger,
        initial = editing,
        onResult = { newFc, isDeleted ->
            if (isDeleted) {
                editingIndex?.let {
                    timeColors.removeAt(it)
                }
            } else {
                newFc?.let {
                    if (newFc.isValid()) {
                        // 1. update the list
                        if (editingIndex != null) { // override
                            timeColors[editingIndex!!] = newFc
                        } else { // add
                            timeColors.add(newFc)
                        }
                        timeColors.sort()
                    }
                }
            }

            // save to spf
            spf.saveTimeColors(timeColors)
        }
    )

    PopupDialog(
        trigger = trigger,
        buttons = {
            StrokeButton(
                label = "+",
                color = LocalPalette.current.textGrey,
            ) {
                editing = FreshnessColor()
                editingIndex = null
                editSingleTrigger.value = true
            }
        }
    ) {

        // time color buttons
        FlowRowSpaced(
            space = 20,
            vSpace = 30,
        ) {
            timeColors.forEachIndexed { index, fc ->
                StrokeButton(
                    label = fc.durationMin,
                    color = Color(fc.argb),
                ) {
                    editing = fc
                    editingIndex = index
                    editSingleTrigger.value = true
                }
            }
        }
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

    var loggingEnabled by remember { mutableStateOf(spf.isLoggingEnabled) }
    var expiryEnabled by remember { mutableStateOf(spf.isExpiryEnabled) }
    var ttl by remember { mutableIntStateOf(spf.ttl) }
    var logSmsContent by remember { mutableStateOf(spf.isLogSmsContentEnabled) }
    var rows by remember { mutableStateOf<Int?>(spf.initialSmsRowCount) }


    val settingPopupTrigger = remember { mutableStateOf(false) }

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
                                spf.isLoggingEnabled = isOn
                                loggingEnabled = isOn

                                reScheduleHistoryCleanup(ctx)
                            })
                        }

                        // Log SMS Content
                        AnimatedVisibleV(loggingEnabled) {
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
                                                spf.initialSmsRowCount = rows!!
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
                                    spf.isLogSmsContentEnabled = isOn
                                    vm.reload(ctx)
                                })
                            }
                        }

                        // Expiry
                        AnimatedVisibleV(loggingEnabled) {
                            val trigger = remember { mutableStateOf(false) }
                            PopupDialog(
                                trigger = trigger,
                            ) {
                                // Expiry Enabled
                                LabeledRow(R.string.expiry) {
                                    SwitchBox(checked = expiryEnabled, onCheckedChange = { isOn ->
                                        spf.isExpiryEnabled = isOn
                                        expiryEnabled = isOn
                                        reScheduleHistoryCleanup(ctx)
                                    })
                                }

                                // Expiry days
                                if (expiryEnabled) {
                                    NumberInputBox(
                                        intValue = ttl,
                                        onValueChange = { newValue, hasError ->
                                            if (!hasError) {
                                                ttl = newValue!!
                                                spf.ttl = newValue
                                                reScheduleHistoryCleanup(ctx)
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
                            SwitchBox(checked = showHistoryPassed.value, onCheckedChange = { isOn ->
                                spf.showPassed = isOn
                                showHistoryPassed.value = isOn
                            })
                        }
                        // Blocked
                        LabeledRow(labelId = R.string.blocked_records) {
                            SwitchBox(checked = showHistoryBlocked.value, onCheckedChange = { isOn ->
                                spf.showBlocked = isOn
                                showHistoryBlocked.value = isOn
                            })
                        }

                        // Rule Indicator
                        LabeledRow(
                            labelId = R.string.rule_indicator,
                            helpTooltip = Str(R.string.help_show_rule_indicator),
                        ) {
                            SwitchBox(checked = showHistoryIndicator.value, onCheckedChange = { isOn ->
                                spf.showIndicator = isOn
                                showHistoryIndicator.value = isOn
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
                                                    spf.forceShowSim = false
                                                    forceShowSIM.value = false
                                                }
                                                1 -> {
                                                    G.permissionChain.ask(
                                                        ctx,
                                                        listOf(PermissionWrapper(Permission.phoneState))
                                                    ) { granted ->
                                                        if (granted) {
                                                            spf.forceShowSim = true
                                                            forceShowSIM.value = true
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                            }
                            var selected by remember(forceShowSIM.value) {
                                mutableIntStateOf(
                                    if (forceShowSIM.value) 1 else 0
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
                            SwitchBox(checked = showHistoryGeoLocation.value, onCheckedChange = { isOn ->
                                spf.showGeoLocation = isOn
                                showHistoryGeoLocation.value = isOn
                            })
                        }

                        // Time Color
                        val timeColorTrigger = remember { mutableStateOf(false) }
                        EditTimeColorsDialog(timeColorTrigger, historyTimeColors)
                        LabeledRow(
                            labelId = R.string.time_color,
                            helpTooltip = Str(R.string.help_time_color)
                        ) {
                            if (showHistoryTimeColor.value) {
                                MultiColorButton(
                                    colors = historyTimeColors.map { it.argb },
                                    emptyColor = if (historyTimeColors.isNotEmpty()) Color.Unspecified else C.textGrey
                                ) {
                                    timeColorTrigger.value = true
                                }
                            }
                            SwitchBox(checked = showHistoryTimeColor.value, onCheckedChange = { isOn ->
                                spf.showTimeColor = isOn
                                showHistoryTimeColor.value = isOn
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
