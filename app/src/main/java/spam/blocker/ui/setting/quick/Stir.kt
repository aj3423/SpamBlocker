package spam.blocker.ui.setting.quick

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
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
import spam.blocker.ui.widgets.Button
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.PriorityLabel
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.spf

@Composable
fun Stir() {
    val ctx = LocalContext.current
    val C = LocalPalette.current
    val spf = spf.Stir(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled()) }
    var isStrict by remember { mutableStateOf(spf.isStrict()) }
    var includeUnverified by remember { mutableStateOf(spf.isIncludeUnverified()) }
    var priLenient by remember { mutableIntStateOf(spf.getLenientPriority()) }
    var priStrict by remember { mutableIntStateOf(spf.getStrictPriority()) }

    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        content = {
            Column {
                LabeledRow(labelId = R.string.type) {
                    val items = listOf(
                        RadioItem(Str(R.string.lenient), color = C.textGrey),
                        RadioItem(Str(R.string.strict), color = Salmon),
                    )

                    RadioGroup(items = items, selectedIndex = if (isStrict) 1 else 0) { clickedIdx ->
                        isStrict = clickedIdx == 1
                        spf.setStrict(isStrict)
                    }
                }
                LabeledRow(labelId = R.string.stir_include_unverified) {
                    SwitchBox(checked = includeUnverified, onCheckedChange = { isTurningOn ->
                        includeUnverified = isTurningOn
                        spf.setIncludeUnverified(isTurningOn)
                    })
                }
                if (isStrict) {
                    PriorityBox(priStrict) { newValue, hasError ->
                        if (!hasError) {
                            priStrict = newValue!!
                            spf.setStrictPriority(newValue)
                        }
                    }
                } else {
                    PriorityBox(priLenient) { newValue, hasError ->
                        if (!hasError) {
                            priLenient = newValue!!
                            spf.setLenientPriority(newValue)
                        }
                    }
                }
            }
        }
    )

    LabeledRow(
        R.string.stir_attestation,
        helpTooltip = Str(R.string.help_stir),
        content = {
            if (isEnabled) {
                Button(
                    content = {
                        RowVCenterSpaced(6) {
                            Text(
                                text = Str(
                                    strId = if (isStrict) R.string.strict else R.string.lenient
                                ) + if (includeUnverified) " (*)" else "",
                                color = if (isStrict) Salmon else C.textGrey,
                            )
                            if (isStrict && priStrict != 0) {
                                PriorityLabel(priStrict)
                            }
                            if (!isStrict && priLenient != 10) {
                                PriorityLabel(priLenient)
                            }
                        }
                    },
                    borderColor = if (isStrict) Salmon else C.textGrey
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