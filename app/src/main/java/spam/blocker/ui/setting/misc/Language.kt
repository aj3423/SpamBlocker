package spam.blocker.ui.setting.misc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.Spinner
import spam.blocker.util.Launcher
import spam.blocker.util.spf

// list of language codes: https://github.com/championswimmer/android-locales
// non-flag emoji: https://en.wikipedia.org/wiki/Enclosed_Alphanumeric_Supplement
object Languages {
    val map = sortedMapOf(
        Pair("de", "🇩🇪"),
        Pair("en", "🇬🇧"),
        Pair("es", "🇪🇸"),
        Pair("fr", "🇫🇷"),
        Pair("gal", "🄶🄰🄻"),
        Pair("it", "🇮🇹"),
        Pair("ja", "🇯🇵"),
        Pair("pt-rBR", "🇧🇷"),
        Pair("ru", "🇷🇺"),
        Pair("tr", "🇹🇷"),
        Pair("uk", "🇺🇦"),
        Pair("zh", "🇨🇳"),
    )
}

@Composable
fun Language() {
    val ctx = LocalContext.current
    val spf = spf.Global(ctx)

    var currLangCode by remember {
        mutableStateOf(spf.getLanguage())
    }

    val items = remember {

        val followSystem = ctx.getString(R.string.follow_system)

        // [de, en, ...]
        val codes = Languages.map.keys.toMutableList()
        // [🇩🇪 de, 🇬🇧 en, ...]
        val labels = codes.map { "${Languages.map[it]} $it" }.toMutableList()

        // ["", de, en, ...]
        codes.add(0, "")
        // ["Follow System", 🇩🇪 de, 🇬🇧 en, ...]
        labels.add(0, followSystem)

        labels.mapIndexed { index, label ->
            LabelItem(
                id = codes[index],
                label = label,
                onClick = {
                    val newLangCode = codes[index]
                    spf.setLanguage(newLangCode)
                    currLangCode = newLangCode
                    Launcher.selfRestart(ctx)
                }
            )
        }
    }

    val selected = remember (currLangCode) {
        items.indexOfFirst{
            it.id == currLangCode
        }
    }


    LabeledRow(
        R.string.language,
        helpTooltipId = R.string.help_language,
        content = {
            Spinner(
                items,
                selected,
            )
        }
    )
}