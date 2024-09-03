package spam.blocker.ui.history

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.util.M
import spam.blocker.ui.widgets.CheckItem
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.util.SharedPref.Global

@Composable
fun HistoryFabs(
    modifier: Modifier,
    visible: Boolean,
    forType: Int,
    showPassed: MutableState<Boolean>,
    showBlocked: MutableState<Boolean>,
) {
    val ctx = LocalContext.current
    val spf = Global(ctx)

    RowVCenterSpaced(space = 8, modifier = modifier) {
        // add "Show Passed" and "Show Blocked"
        val menuItems: List<IMenuItem> =
            ctx.resources.getStringArray(R.array.history_display_filter)
                .mapIndexed { i, it ->
                    CheckItem(
                        checked = if (i == 0) showPassed.value else showBlocked.value,
                        label = it,
                    ) { isOn ->
                        if (i == 0) {
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

        // Show Passed/Blocked
        DropdownWrapper(items = menuItems) { expanded ->
            Fab(
                visible = visible,
                iconId = R.drawable.ic_display_filter,
                bgColor = SkyBlue
            ) {
                expanded.value = true
            }
        }
        // Delete all
        Fab(
            visible = visible,
            iconId = R.drawable.ic_recycle_bin,
            bgColor = Salmon
        ) {
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
}
