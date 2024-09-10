package spam.blocker.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.round
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.CheckBox
import spam.blocker.ui.widgets.CheckItem
import spam.blocker.ui.widgets.CustomItem
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RowCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.SharedPref.HistoryOptions

@Composable
fun HistoryFabs(
    modifier: Modifier,
    visible: Boolean,
    forType: Int,
    showPassed: MutableState<Boolean>,
    showBlocked: MutableState<Boolean>,
    logSmsContent: MutableState<Boolean>
) {
    val ctx = LocalContext.current
    val spf = HistoryOptions(ctx)

    // a fix for Tooltip+DropdownMenu
    val dropdownOffset = remember { mutableStateOf(Offset.Zero) }

    RowVCenterSpaced(space = 8, modifier = modifier) {
        // Context Menu
        val menuItems: MutableList<IMenuItem> = remember {

            // add "Show Passed" and "Show Blocked"
            val ret = mutableListOf<IMenuItem>()
            ret += ctx.resources.getStringArray(R.array.history_display_filter)
                .mapIndexed { idx, label ->
                    CheckItem(
                        state = if (idx == 0) showPassed else showBlocked,
                        label = label
                    ) { isOn ->
                        if (idx == 0) {
                            showPassed.value = isOn
                            spf.setShowPassed(isOn)
                        } else {
                            showBlocked.value = isOn
                            spf.setShowBlocked(isOn)
                        }
                        if (forType == Def.ForNumber)
                            G.callVM.reload(ctx)
                        else
                            G.smsVM.reload(ctx)
                    }

                }

            // add "SMS Log"
            if (forType == Def.ForSms) {
                ret.add(DividerItem())
                ret.add(
                    CustomItem {
                        RowCenter {
                            CheckBox(
                                checked = logSmsContent.value,
                                label = { GreyLabel(Str(R.string.log_sms_content)) },
                                onCheckChange = {
                                    logSmsContent.value = !logSmsContent.value
                                    spf.setLogSmsContentEnabled(!spf.isLogSmsContentEnabled())
                                }
                            )
                            BalloonQuestionMark(
                                helpTooltipId = R.string.help_log_sms_content,
                                dropdownOffset.value.round()
                            )
                        }
                    }
                )
            }

            ret
        }

        // Show Passed/Blocked
        DropdownWrapper(
            items = menuItems,
            modifier = M.onGloballyPositioned {
                dropdownOffset.value = it.positionOnScreen()
            }
        ) { expanded ->
            Fab(
                visible = visible,
                iconId = R.drawable.ic_display_filter,
                bgColor = SkyBlue
            ) {
                expanded.value = true
            }
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
