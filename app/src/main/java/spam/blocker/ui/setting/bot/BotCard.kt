package spam.blocker.ui.setting.bot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.GreenDot
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.NonLazyGrid
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
        RowVCenterSpaced(
            space = 10,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = M.weight(1f)
            ) {
                // desc
                GreyLabel(text = bot.desc, fontWeight = FontWeight.SemiBold)

                // schedule
                RowVCenter {
                    val isScheduled = bot.enabled && bot.schedule != null

                    // Green active dot
                    if (isScheduled) {
                        RowVCenterSpaced(8) {
                            GreenDot()

                            GreyIcon16(bot.schedule!!.iconId())
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
            }

            // action icons
            NonLazyGrid(
                columns = 3,
                itemCount = bot.actions.size,
            ) {
                bot.actions[it].Icon()
            }
        }
    }
}
