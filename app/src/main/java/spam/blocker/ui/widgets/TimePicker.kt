package spam.blocker.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda2
import spam.blocker.util.Lambda4

// Add an `onChange` to the built-in TimeInput
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourMinInput(
    hour: Int,
    min: Int,
    onChange: Lambda2<Int, Int>
) {
    val state = rememberTimePickerState(hour, min, true)
    val eventMonitor = remember(state.hour, state.minute) {
        onChange(state.hour, state.minute)
        true
    }
    TimeInput(state = state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangePicker(
    trigger: MutableState<Boolean>,
    sHour: Int, sMin: Int, eHour: Int, eMin: Int,
    onDismiss: Lambda4<Int, Int, Int, Int>,
) {
    val sState = rememberTimePickerState(sHour, sMin, true)
    val eState = rememberTimePickerState(eHour, eMin, true)

    PopupDialog(
        trigger = trigger,
        onDismiss = {
            onDismiss(sState.hour, sState.minute, eState.hour, eState.minute)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Str(R.string.start_time))
            RowCenter(modifier = M.fillMaxWidth()) {
                TimeInput(state = sState)
            }
            HorizontalDivider(thickness = 1.dp, color = LocalPalette.current.disabled)
            Text(Str(R.string.end_time))
            RowCenter(modifier = M.fillMaxWidth()) {
                TimeInput(state = eState)
            }
        }
    }
}