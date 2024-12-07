package spam.blocker.ui.setting.api

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import spam.blocker.db.Api
import spam.blocker.ui.M
import spam.blocker.ui.widgets.GreenDot
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.NonLazyGrid
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.RowVCenterSpaced


@Composable
fun ApiCard(
    api: Api,
    modifier: Modifier,
) {
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
                GreyLabel(text = api.summary(), fontWeight = FontWeight.SemiBold)

                RowVCenterSpaced(8) {
                    // Green dot
                    if (api.enabled) {
                        GreenDot()
                    }
                    // first action summary
                    api.actions.firstOrNull()?.Summary()
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
