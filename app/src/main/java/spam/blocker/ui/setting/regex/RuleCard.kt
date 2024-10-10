package spam.blocker.ui.setting.regex

import android.app.NotificationManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.util.hasFlag

@Composable
fun RuleCard(
    rule: RegexRule,
    forType: Int,
    modifier: Modifier = Modifier,
) {
    val C = LocalPalette.current

    OutlineCard {
        Row(
            modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Regex and Description
            Column(M.weight(1f).padding(end = 4.dp), verticalArrangement = Arrangement.Center) {
                // Regex
                Text(
                    text = rule.colorfulRegexStr(
                        ctx = LocalContext.current,
                        forType = forType,
                        palette = C,
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = M.padding(top = 2.dp),
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
                // Description
                if (rule.description.isNotEmpty()) {
                    Text(
                        text = rule.description,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = C.textGrey,
                        modifier = M.padding(start = 10.dp),
                    )
                }
            }

            // Icons
            Column(horizontalAlignment = Alignment.End) {
                // [Number, Message]  [BlockType]  [Call, SMS]
                RowVCenterSpaced(space = 6, modifier = M.padding(top = 6.dp)) {
                    if (forType == Def.ForQuickCopy) {
                        // [Number, Message]
                        RowVCenterSpaced(space = 4) {
                            if (rule.flags.hasFlag(Def.FLAG_FOR_NUMBER))
                                GreyIcon16( iconId = R.drawable.ic_number_sign )
                            if (rule.flags.hasFlag(Def.FLAG_FOR_CONTENT))
                                GreyIcon16(iconId = R.drawable.ic_open_msg)
                        }
                    }
                    if (forType == Def.ForNumber && rule.isBlacklist) {
                        // [BlockType]
                        when (rule.blockType) {
                            0 -> GreyIcon16( iconId = R.drawable.ic_call_blocked )
                            1 -> GreyIcon( iconId = R.drawable.ic_call_miss )
                            2 -> GreyIcon(iconId = R.drawable.ic_hang)
                        }
                    }
                    // [Call, SMS]
                    RowVCenterSpaced(space = 2, M.padding(start = 4.dp)) {
                        if (forType != Def.ForSms)
                            ResIcon(
                                iconId = R.drawable.ic_call,
                                modifier = M.size(20.dp),
                                color = if (rule.isForCall()) C.enabled else C.disabled
                            )
                        ResIcon(
                            iconId = R.drawable.ic_sms,
                            modifier = M.size(20.dp),
                            color = if (rule.isForSms()) C.enabled else C.disabled
                        )
                    }
                }

                // [NotifyType]  [Priority]
                RowVCenterSpaced(space = 8) {

                    // [NotifyType]
                    RowVCenterSpaced(space = 2) {
                        if (rule.isBlacklist && rule.importance >= NotificationManager.IMPORTANCE_DEFAULT) {
                            GreyIcon16(iconId = R.drawable.ic_bell_ringing)
                        }
                        if (rule.isBlacklist && rule.importance == NotificationManager.IMPORTANCE_HIGH) {
                            GreyIcon16(iconId = R.drawable.ic_heads_up)
                        }
                        if (rule.isBlacklist && rule.importance == NotificationManager.IMPORTANCE_MIN) {
                            GreyIcon16(iconId = R.drawable.ic_shade)
                        }
                        if (rule.isBlacklist && rule.importance >= NotificationManager.IMPORTANCE_LOW) {
                            GreyIcon16( iconId = R.drawable.ic_statusbar_shade )
                        }
                    }

                    // [Priority]
                    Text(
                        text = "${Str(R.string.priority)}: ${rule.priority}",
                        color = LightMagenta,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

