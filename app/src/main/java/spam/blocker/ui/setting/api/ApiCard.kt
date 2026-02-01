package spam.blocker.ui.setting.api

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.R
import spam.blocker.db.IApi
import spam.blocker.db.ReportApi
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.widgets.GreenDot
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.NonLazyGrid
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.util.spf


@Composable
fun ApiCard(
    forType: Int,
    api: IApi,
    modifier: Modifier,
) {
    val ctx = LocalContext.current

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

                RowVCenterSpaced(8) {
                    // Green dot
                    if (api.enabled) {
                        GreenDot()
                    }

                    // Desc
                    GreyLabel(text = api.summary(), fontWeight = FontWeight.SemiBold)
                }

                // Auto report types icons
                if (api is ReportApi) {
                    AutoReportIcons(api.autoReportTypes)
                }
            }

            // [Priority]
            if (forType == Def.ForApiQuery) {
                RowVCenterSpaced(6) {
                    val priority = spf.ApiQueryOptions(ctx).priority
                    ResIcon(R.drawable.ic_priority, color = LightMagenta, modifier = M.size(18.dp).offset(6.dp))
                    Text(
                        text = "$priority",
                        color = LightMagenta,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            // action icons
            NonLazyGrid(
                columns = 3,
                itemCount = api.actions.size,
            ) {
                api.actions[it].Icon()
            }
        }
    }
}
