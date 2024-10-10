package spam.blocker.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.rememberSaveableMutableStateListOf
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda1
import java.time.DayOfWeek


// Two implementations using Calendar and DayOfWeek, the Calendar is for historical compatible,
// later code should always use DayOfWeek(use the WeekdayPick2)

/*
  Deprecated.
  For java's Calendar:   1 ~ 7 == Sunday ~ Saturday
  In strings.xml:        0 ~ 6 == Sunday ~ Saturday, need to add 1
 */
@Composable
fun WeekdayPicker1(
    selectedDays: SnapshotStateList<Int>,
) {
    RowVCenter(
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val C = LocalPalette.current
        val labels = LocalContext.current.resources.getStringArray(R.array.short_weekdays)

        labels.forEachIndexed { index, label ->
            val selected = selectedDays.contains(index + 1)
            val color = if (selected) C.enabled else C.disabled
            StrokeButton(
                modifier = M
                    .defaultMinSize(34.dp, 0.dp)
                    .alpha(if (selected) 1.0f else 0.7f)
                    .weight(1f),
                label = label,
                shape = RoundedCornerShape(4.dp),
                color = color,
                contentPadding = PaddingValues(0.dp, 0.dp),
                onClick = {
                    val i = index + 1
                    if (selectedDays.contains(i)) {
                        selectedDays.remove(i)
                    } else {
                        selectedDays.add(i)
                    }
                }
            )
        }
    }
}

/*
  For java's DayOfWeek:  1 ~ 7 == Monday ~ Sunday
  In strings.xml:        0 ~ 6 == Sunday ~ Saturday

  only the Sunday is different, need to map DayOfWeek's 7 -> strings' 0
 */
@Composable
fun WeekdayPicker2(
    initialDays: List<DayOfWeek>,
    onChange: Lambda1<List<DayOfWeek>>,
) {
    val selectedDays = rememberSaveableMutableStateListOf(*initialDays.toTypedArray())

    // Do not remove this...
    val onDaysChange = remember(selectedDays.size) {
        onChange(selectedDays)
        true
    }

    RowVCenter(
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val C = LocalPalette.current
        val labels = LocalContext.current.resources.getStringArray(R.array.short_weekdays)

        labels.forEachIndexed { index, label ->
            val selected = selectedDays.map {
                it.value
            }.contains(if (index == 0) 7 else index)
            val color = if (selected) C.enabled else C.disabled
            StrokeButton(
                modifier = M
                    .defaultMinSize(34.dp, 0.dp)
                    .alpha(if (selected) 1.0f else 0.7f)
                    .weight(1f),
                label = label,
                shape = RoundedCornerShape(4.dp),
                color = color,
                contentPadding = PaddingValues(0.dp, 0.dp),
                onClick = {
                    val i = if (index == 0) 7 else index
                    val day = DayOfWeek.of(i)
                    if (selectedDays.contains(day)) {
                        selectedDays.remove(day)
                    } else {
                        selectedDays.add(day)
                    }
                }
            )
        }
    }
}
