package spam.blocker.ui.setting.quick

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
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.ui.widgets.TimeRangePicker
import spam.blocker.util.TimeUtils.timeRangeStr
import spam.blocker.util.spf

@Composable
fun OffTime() {
    val ctx = LocalContext.current
    val spf = spf.OffTime(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled) }

    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    var sHour by remember { mutableIntStateOf(spf.startHour) }
    var sMin by remember { mutableIntStateOf(spf.startMin) }
    var eHour by remember { mutableIntStateOf(spf.endHour) }
    var eMin by remember { mutableIntStateOf(spf.endMin) }

    if (popupTrigger.value) {
        TimeRangePicker(
            trigger = popupTrigger,
            sHour, sMin, eHour, eMin,
        ) { sH, sM, eH, eM ->
            spf.startHour = sH
            spf.startMin = sM
            spf.endHour = eH
            spf.endMin = eM
            sHour = sH
            sMin = sM
            eHour = eH
            eMin = eM
        }
    }
    LabeledRow(
        R.string.off_time,
        helpTooltip = Str(R.string.help_off_time),
        content = {
            if (isEnabled) {
                GreyButton(
                    label = timeRangeStr(
                        ctx, sHour, sMin, eHour, eMin
                    ),
                ) {
                    popupTrigger.value = true
                }
            }
            SwitchBox(isEnabled) { isTurningOn ->
                spf.isEnabled = isTurningOn
                isEnabled = isTurningOn
            }
        }
    )
}