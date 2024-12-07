package spam.blocker.ui.setting.bot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.R
import spam.blocker.service.bot.IAction
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced


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
                    color = C.textGrey,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = M.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Summary
                action.Summary()
            }

            RowVCenter {
                BalloonQuestionMark(action.tooltip(ctx))

                // Reorder Icon
                GreyIcon16(iconId = R.drawable.ic_reorder)
            }
        }
    }
}
