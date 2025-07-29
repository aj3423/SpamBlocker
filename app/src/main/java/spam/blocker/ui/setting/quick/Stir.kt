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
import spam.blocker.ui.widgets.Button
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.PriorityLabel
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.spf

@Composable
fun Stir() {
    val ctx = LocalContext.current
    val spf = spf.Stir(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled()) }
    var priority by remember { mutableIntStateOf(spf.getPriority()) }

    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        content = {
            Column {
                PriorityBox(priority) { newValue, hasError ->
                    if (!hasError) {
                        priority = newValue!!
                        spf.setPriority(newValue)
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
                // Priority Button
                Button(
                    content = {
                        PriorityLabel(priority)
                    },
    //                            borderColor = Salmon
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