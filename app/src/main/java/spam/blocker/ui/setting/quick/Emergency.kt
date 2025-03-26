package spam.blocker.ui.setting.quick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.SettingLabel
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.widgets.DimGreyLabel
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.spf


@Composable
fun EmergencySituation() {
    val C = LocalPalette.current
    val ctx = LocalContext.current
    val spf = spf.EmergencySituation(ctx)

    var isEnabled by rememberSaveable { mutableStateOf(spf.isEnabled()) }
    var isStirEnabled by rememberSaveable { mutableStateOf(spf.isStirEnabled()) }
    var extraNumbers by rememberSaveable { mutableStateOf(spf.getExtraNumbers().joinToString(", ")) }
    var duration by rememberSaveable { mutableIntStateOf(spf.getDuration()) }
    var collapsed by rememberSaveable { mutableStateOf(spf.isCollapsed()) }

    fun calcTimeLeft(): Long {
        val lastEccCallTime: Long = spf.getTimestamp()
        val duration: Long = (duration * 60 * 1000).toLong()
        val now = System.currentTimeMillis()
        return lastEccCallTime + duration - now
    }
    var timeLeft by rememberSaveable(duration) {
        mutableLongStateOf(calcTimeLeft())
    }

    // Reset confirm
    val resetConfirm = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = resetConfirm,
        buttons = {
            StrokeButton(label = Str(R.string.reset), color = Salmon) {
                resetConfirm.value = false
                timeLeft = 0
                spf.setTimestamp(timeLeft)
            }
        }
    ) {
        GreyLabel(Str(R.string.confirm_reset))
    }

    // popup
    val configTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = configTrigger,
        onDismiss = {
            spf.setDuration(duration)
            spf.setStirEnabled(isStirEnabled)
            spf.setExtraNumbers(
                extraNumbers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            )
        },
        content = {
            // Re-calculate the time left when the config dialog popups.
            LaunchedEffect(true) {
                timeLeft = calcTimeLeft()
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Reset status
                LabeledRow(labelId = R.string.status) {
                    Text(
                        text = if (timeLeft > 0) {
                            "${timeLeft/1000/60} ${Str(R.string.min)}"
                        } else {
                            Str(R.string.inactive)
                        },
                        color = if (timeLeft > 0) C.enabled else C.disabled,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = M.width(16.dp))
                    StrokeButton(label = Str(R.string.reset), color = Salmon) {
                        resetConfirm.value = true
                    }
                }

                // STIR Check
                LabeledRow(labelId = R.string.check_stir_attestation) {
                    SwitchBox(isStirEnabled) { isTurningOn ->
                        isStirEnabled = isTurningOn
                    }
                }

                // Duration
                NumberInputBox(
                    intValue = duration,
                    onValueChange = { newValue, hasError ->
                        if (!hasError) {
                            duration = newValue!!
                        }
                    },
                    label = { Text(Str(R.string.within_minutes)) },
                    leadingIconId = R.drawable.ic_duration,
                )

                // Extra numbers
                StrInputBox(
                    text = extraNumbers,
                    label = { Text(Str(R.string.additional_numbers)) },
                    placeholder = { DimGreyLabel("000, 123, ...") },
                    leadingIconId = R.drawable.ic_number_sign,
                    onValueChange = { extraNumbers = it }
                )
            }
        }
    )


    LabeledRow(
        modifier = M.clickable{
            collapsed = !collapsed
            spf.setCollapsed(collapsed)
        },
        label = {
            RowVCenterSpaced(4) {
                SettingLabel(R.string.emergency)
                if (isEnabled && collapsed && extraNumbers.isNotBlank()) {
                    GreyIcon16(
                        iconId = R.drawable.ic_dropdown_arrow,
                    )
                }
            }
        },
        helpTooltip = Str(R.string.help_emergency_situation),
        content = {
            if (isEnabled) {
                GreyButton(
                    label = "$duration ${Str(R.string.min)}${if (isStirEnabled) "" else " (?)"}",
                ) {
                    configTrigger.value = true
                }
            }
            SwitchBox(isEnabled) { isTurningOn ->
                spf.setEnabled(isTurningOn)
                isEnabled = isTurningOn
            }
        }
    )
    if (isEnabled && !collapsed) {
        OutlineCard {
            Row(
                modifier = M
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clickable {
                        configTrigger.value = true
                    }
            ) {
                // Regex
                Text(
                    text = extraNumbers,
                    color = C.textGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = M.padding(top = 2.dp),
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}