package spam.blocker.ui.history

import androidx.compose.foundation.layout.width
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
import spam.blocker.service.CleanerSchedule
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.SharedPref.HistoryOptions

@Composable
fun HistoryFabs(
    modifier: Modifier,
    visible: Boolean,
    forType: Int,
) {
    val ctx = LocalContext.current
    val spf = HistoryOptions(ctx)

    var showPassed by rememberSaveable { mutableStateOf(spf.getShowPassed()) }
    var showBlocked by rememberSaveable { mutableStateOf(spf.getShowBlocked()) }
    var historyTTL by rememberSaveable { mutableIntStateOf(spf.getHistoryTTL()) }
    var logSmsContent by rememberSaveable { mutableStateOf(spf.isLogSmsContentEnabled()) }

    val settingPopupTrigger = rememberSaveable { mutableStateOf(false) }

    fun reloadVM() {
        if (forType == Def.ForNumber)
            G.callVM.reload(ctx)
        else
            G.smsVM.reload(ctx)
    }

    PopupDialog(
        trigger = settingPopupTrigger,
        popupSize = PopupSize(percentage = 0.8f, minWidth = 340, maxWidth = 500),
        content = {
            // TTL
            LabeledRow(
                labelId = R.string.expire,
                helpTooltipId = R.string.help_history_ttl
            ) {
                NumberInputBox(
                    intValue = historyTTL,
                    onValueChange = { newValue, hasError ->
                        if (!hasError) {
                            historyTTL = newValue!!
                            spf.setHistoryTTL(newValue)

                            CleanerSchedule.cancelPrevious(ctx)
                            if (newValue > 0) {
                                CleanerSchedule.scheduleNext(ctx)
                            } else if (newValue == 0) {
                                // it will not record data, no need to cleanup
                            } else {
                                // it will record data, but it will never expire, no need to cleanup
                            }
                        }
                    },
                    label = { Text(Str(R.string.days)) },
                    leadingIconId = R.drawable.ic_recycle_bin,
                )
            }

            // Log SMS Content
            if (forType == Def.ForSms) {
                LabeledRow(
                    labelId = R.string.log_sms_content,
                    helpTooltipId = R.string.help_log_sms_content
                ) {
                    SwitchBox(checked = logSmsContent, onCheckedChange = { isTurningOn ->
                        logSmsContent = isTurningOn
                        spf.setLogSmsContentEnabled(isTurningOn)
                    })
                }
            }
            HorizontalDivider(thickness = 1.dp, color = LocalPalette.current.disabled)
            LabeledRow(labelId = R.string.show_passed) {
                SwitchBox(checked = showPassed, onCheckedChange = { isOn ->
                    showPassed = isOn
                    spf.setShowPassed(isOn)
                    reloadVM()
                })
            }
            LabeledRow(labelId = R.string.show_blocked) {
                SwitchBox(checked = showBlocked, onCheckedChange = { isOn ->
                    showBlocked = isOn
                    spf.setShowBlocked(isOn)
                    reloadVM()
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
                    when (forType) {
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
