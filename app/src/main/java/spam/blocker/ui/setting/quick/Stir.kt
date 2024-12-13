package spam.blocker.ui.setting.quick

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.spf

@Composable
fun Stir() {
    val ctx = LocalContext.current
    val C = LocalPalette.current
    val spf = spf.Stir(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled()) }
    var isExclusive by remember { mutableStateOf(spf.isExclusive()) }
    var includeUnverified by remember { mutableStateOf(spf.isIncludeUnverified()) }

    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        content = {
            Column {
                LabeledRow(labelId = R.string.type) {
                    val items = listOf(
                        RadioItem(Str(R.string.inclusive), color = C.textGrey),
                        RadioItem(Str(R.string.exclusive), color = Salmon),
                    )

                    var selected by remember(isExclusive) {
                        mutableIntStateOf(if (isExclusive) 1 else 0)
                    }

                    RadioGroup(items = items, selectedIndex = selected) { clickedIdx ->
                        selected = clickedIdx
                        isExclusive = clickedIdx == 1
                        spf.setExclusive(isExclusive)
                    }
                }
                LabeledRow(labelId = R.string.stir_include_unverified) {
                    SwitchBox(checked = includeUnverified, onCheckedChange = { isTurningOn ->
                        includeUnverified = isTurningOn
                        spf.setIncludeUnverified(isTurningOn)
                    })
                }
            }
        })

    LabeledRow(
        R.string.stir_attestation,
        helpTooltipId = R.string.help_stir,
        content = {
            if (isEnabled) {
                StrokeButton(
                    label = Str(
                        strId = if (isExclusive) R.string.exclusive else R.string.inclusive
                    ) + if (includeUnverified) " (?)" else "",
                    color = if (isExclusive) Salmon else C.textGrey,
                ) {
                    popupTrigger.value = true
                }
            }
            SwitchBox(isEnabled) { isTurningOn ->
                spf.setEnabled(isTurningOn)
                isEnabled = isTurningOn
            }
        }
    )
}