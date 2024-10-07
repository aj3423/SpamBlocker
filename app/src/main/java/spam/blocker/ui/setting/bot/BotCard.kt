package spam.blocker.ui.setting.bot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced


@Composable
fun BotCard(
    bot: Bot,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    OutlineCard(
        containerBg = MaterialTheme.colorScheme.background
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // desc
            GreyLabel(text = bot.desc, fontWeight = FontWeight.SemiBold)

            RowVCenter(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = M.fillMaxWidth()
            ) {
                // schedule
                RowVCenter {
                    val isScheduled = bot.enabled && bot.schedule != null
                    // Green active dot
                    if (isScheduled) {
                        Canvas(
                            modifier = Modifier.size(6.dp)
                        ) {
                            drawCircle(color = Color.Green, radius = size.minDimension / 2)
                        }
                    }

                    GreyLabel(
                        text = if (isScheduled) {
                            bot.schedule!!.summary(ctx)
                        } else {
                            ctx.getString(R.string.manual)
                        },
                        modifier = M.padding(start = 10.dp)
                    )
                }

                // action icons
                RowVCenterSpaced(2) {
                    bot.actions.forEach { it.Icon() }
                }
            }
        }
    }
}
