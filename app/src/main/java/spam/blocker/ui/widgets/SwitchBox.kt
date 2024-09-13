package spam.blocker.ui.widgets

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import spam.blocker.ui.theme.LocalPalette


private const val Width = 40
private const val Height = 30
private const val ThumbRadius = 10
private const val TrackWidth = Width / 2
private const val TrackHeight = TrackWidth / 2
private const val TrackRadius = TrackHeight

@Composable
fun SwitchBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val C = LocalPalette.current

    val thumbOffset by animateIntAsState(
        targetValue = if (checked) ThumbRadius else -ThumbRadius,
        label = "",
    )

    val trackColor = if (checked) C.switchTrackOn else C.switchTrackOff
    val thumbColor = if (checked) C.switchThumbOn else C.switchThumbOff

    Box(
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .size(width = Width.dp, height = Height.dp),
        contentAlignment = Alignment.Center
    ) {
        // track
        RowCenter {
            Box(
                modifier = Modifier
                    .size(TrackWidth.dp, TrackHeight.dp)
                    .clip(RoundedCornerShape(TrackRadius.dp))
                    .background(trackColor)
            )
        }
        // round thumb
        RowCenter {
            Box(
                modifier = Modifier
                    .size((ThumbRadius*2).dp)
                    .offset(x = thumbOffset.dp)
                    .clip(RoundedCornerShape(ThumbRadius.dp))
                    .background(thumbColor)
            )
        }
    }
}

