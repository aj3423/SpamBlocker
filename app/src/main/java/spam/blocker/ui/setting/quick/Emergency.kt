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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.service.CallScreeningService
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.Button
import spam.blocker.ui.widgets.ColumnSpaced
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.Placeholder
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.PriorityLabel
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.spf


fun calcTimeLeft(
    timestamp: Long,
    duration: Int,
): Long {
    val lastEccCallTime: Long = timestamp
    val dur: Long = (duration * 60 * 1000).toLong()
    val now = System.currentTimeMillis()
    return lastEccCallTime + dur - now
}

@Composable
fun EmergencySituation() {
    val C = G.palette
    val ctx = LocalContext.current
    val spf = spf.EmergencySituation(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled) }
    var priority by remember { mutableIntStateOf(spf.priority) }
    var extraNumbers by remember { mutableStateOf(spf.getExtraNumbers().joinToString(", ")) }
    var duration by remember { mutableIntStateOf(spf.duration) }
    var collapsed by remember { mutableStateOf(spf.isCollapsed) }

    var timeLeft by remember(duration) {
        mutableLongStateOf(calcTimeLeft(
            duration = duration,
            timestamp = spf.timestamp
        ))
    }

    // Reset confirm
    val resetConfirm = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = resetConfirm,
        buttons = {
            StrokeButton(label = Str(R.string.reset), color = C.error) {
                resetConfirm.value = false
                timeLeft = 0
                spf.timestamp = timeLeft
            }
        }
    ) {
        GreyText(Str(R.string.confirm_to_reset))
    }

    // Test popup
    var callToNumber by remember { mutableStateOf("") }
    val testTrigger = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = testTrigger,
        buttons = {
            StrokeButton(label = Str(R.string.call_to), color = C.teal200) {
                CallScreeningService.updateOutgoingEmergencyTimestamp(ctx, callToNumber)
                timeLeft = calcTimeLeft(
                    duration = duration,
                    timestamp = spf.timestamp
                )
                testTrigger.value = false
            }
        }
    ) {
        StrInputBox(
            text = callToNumber,
            label = { Text(Str(R.string.call_to_number)) },
            placeholder = { Placeholder("911") },
            leadingIconId = R.drawable.ic_dial_pad,
            onValueChange = { callToNumber = it }
        )
    }

    // Config popup
    val configTrigger = remember { mutableStateOf(false) }

    PopupDialog(
        trigger = configTrigger,
        buttons = {
            StrokeButton(label = Str(R.string.test), color = C.teal200) {
                testTrigger.value = true
            }
        },
        content = {
            // Re-calculate the time left when the config dialog popups.
            LaunchedEffect(true) {
                timeLeft = calcTimeLeft(
                    duration = duration,
                    timestamp = spf.timestamp
                )
            }

            ColumnSpaced(4) {
                // Reset status
                LabeledRow(labelId = R.string.status) {
                    Text(
                        text = if (timeLeft > 0) {
                            "${timeLeft/1000/60} ${Str(R.string.min)}"
                        } else {
                            Str(R.string.inactive)
                        },
                        color = if (timeLeft > 0) C.warning else C.disabled,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = M.width(16.dp))
                    StrokeButton(label = Str(R.string.reset), color = if (timeLeft > 0) C.error else C.disabled) {
                        resetConfirm.value = true
                    }
                }

                // Priority
                PriorityBox(priority) { newValue, hasError ->
                    if (!hasError) {
                        priority = newValue!!
                        spf.priority = newValue
                    }
                }

                // Duration
                NumberInputBox(
                    intValue = duration,
                    onValueChange = { newValue, hasError ->
                        if (!hasError) {
                            duration = newValue!!
                            spf.duration = duration
                        }
                    },
                    labelId = R.string.within_minutes,
                    leadingIconId = R.drawable.ic_duration,
                )

                // Extra numbers
                StrInputBox(
                    text = extraNumbers,
                    label = { Text(Str(R.string.additional_numbers)) },
                    placeholder = { Placeholder("000, 123, ...") },
                    leadingIconId = R.drawable.ic_number_sign,
                    onValueChange = {
                        extraNumbers = it

                        spf.setExtraNumbers(
                            extraNumbers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    }
                )
            }
        }
    )

    LabeledRow(
        labelId = R.string.emergency,
        isCollapsed = if (!isEnabled) null else collapsed && extraNumbers.isNotBlank(),
        toggleCollapse = {
            if (isEnabled) {
                collapsed = !collapsed
                spf.isCollapsed = collapsed
            }
        },
        helpTooltip = Str(R.string.help_emergency_situation),
        content = {
            if (isEnabled) {
                Button(
                    borderColor = if (timeLeft > 0) C.warning else C.textGrey,
                    content = {
                        RowVCenterSpaced(6) {
                            Text("$duration ${Str(R.string.min)}", color = if (timeLeft > 0) C.warning else C.textGrey)
                            if (priority != Int.MAX_VALUE) {
                                PriorityLabel(priority)
                            }
                        }
                    }
                ) {
                    configTrigger.value = true
                }
            }
            SwitchBox(isEnabled) { isTurningOn ->
                spf.isEnabled = isTurningOn
                isEnabled = isTurningOn
            }
        }
    )

    // Extra Numbers
    AnimatedVisibleV (isEnabled && !collapsed && extraNumbers.isNotBlank()) {
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
                    color = C.success,
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

@Composable
fun EmergencySituationSummary() {
    val ctx = LocalContext.current
    val C = G.palette
    val spf = spf.EmergencySituation(ctx)

    val isEnabled by remember { mutableStateOf(spf.isEnabled) }
    if (isEnabled) {
        val priority by remember { mutableIntStateOf(spf.priority) }
        val duration by remember { mutableIntStateOf(spf.duration) }


        val timeLeft by remember(duration) {
            mutableLongStateOf(calcTimeLeft(
                duration = duration,
                timestamp = spf.timestamp
            ))
        }

        Button(
            enabled = false,
            borderColor = if (timeLeft > 0) C.warning else C.textGrey,
            content = {
                RowVCenterSpaced(6) {
                    GreyIcon18(R.drawable.ic_sos)
                    Text("$duration ${Str(R.string.min)}", color = if (timeLeft > 0) C.warning else C.textGrey)
                    if (priority != Int.MAX_VALUE) {
                        PriorityLabel(priority)
                    }
                }
            }
        )
    }
}