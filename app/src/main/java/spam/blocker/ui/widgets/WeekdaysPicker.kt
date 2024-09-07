package spam.blocker.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette


/*
  In Config:      1 ~ 7 == Sunday ~ Saturday
  In SharedPref:  0 ~ 6 == Sunday ~ Saturday, need to add 1
 */
@Composable
fun WeekdayPicker(
    selectedDays: SnapshotStateList<Int>,
) {
    RowVCenter(
        modifier = M.fillMaxWidth(),
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
                    .weight(1f)
                ,
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