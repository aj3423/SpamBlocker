package spam.blocker.ui.setting.bot

import android.app.NotificationManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.bot.IAction
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.util.Lambda
import spam.blocker.util.hasFlag


@Composable
fun ActionCard(
    action: IAction,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    OutlineCard(
        containerBg = C.dialogBg,
        modifier = modifier,
    ) {
        RowVCenterSpaced(
            space = 8,
            modifier = M.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // icon
            action.Icon()

            // Label / Summary
            Column(
                modifier = M
                    .weight(1f)
                    .padding(end = 4.dp), verticalArrangement = Arrangement.Center,
            ) {
                // Label
                Text(
                    text = action.label(ctx),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = M.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Summary
                val summary = action.summary(ctx)
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = C.textGrey,
                        modifier = M.padding(start = 10.dp),
                    )
                }
            }
            // Reorder Icon
            GreyIcon(iconId = R.drawable.ic_reorder)
        }
    }
}
