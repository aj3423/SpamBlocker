package spam.blocker.ui.setting.misc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.Spinner
import spam.blocker.util.spf

@Composable
fun Theme() {
    val ctx = LocalContext.current
    val spf = spf.Global(ctx)

    val options = remember {

        val followSystem = ctx.resources.getString(R.string.follow_system)
        val themes = listOf(followSystem) + ctx.resources.getStringArray(R.array.theme_list).toList()

        themes.mapIndexed { index, label ->
            LabelItem(
                label = label,
                onClick = {
                    spf.setThemeType(index)
                    G.themeType.intValue = index
                }
            )
        }
    }


    LabeledRow(
        R.string.theme,
        content = {
            Spinner(
                options,
                G.themeType.intValue,
            )
        }
    )
}