package spam.blocker.ui.setting.misc

import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.def.Def.ANDROID_13
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.ComboDisplayType
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.util.Launcher
import spam.blocker.util.Util
import spam.blocker.util.spf

// list of language codes: https://github.com/championswimmer/android-locales
// non-flag emoji: https://en.wikipedia.org/wiki/Enclosed_Alphanumeric_Supplement

class Lang(
    val code: String, // "de", "en", ...
    val emoji: String? = null, // "🇩🇪", "🇬🇧", ...
    val iconId: Int? = null, // show svg icon if there is no standard emoji for this language.
)

val languages = listOf(
    Lang("ar", null, R.drawable.ic_flag_arabic),
    Lang("ca", null, R.drawable.ic_flag_catalan),
    Lang("de", "🇩🇪"),
    Lang("en", "🇬🇧"),
    Lang("es", "🇪🇸"),
    Lang("fa", "🇮🇷"),
    Lang("fr", "🇫🇷"),
    Lang("gal", null, R.drawable.ic_flag_galician),
    Lang("in", "🇮🇩"),
    Lang("it", "🇮🇹"),
    Lang("iw", "🇮🇱"),
    Lang("ja", "🇯🇵"),
    Lang("pt-rBR", "🇧🇷"),
    Lang("ru", "🇷🇺"),
    Lang("tr", "🇹🇷"),
    Lang("uk", "🇺🇦"),
    Lang("zh-rCN", "🇨🇳"),
    Lang("zh-rTW", "🇹🇼"),

)

@Composable
fun Language() {
    val ctx = LocalContext.current
    val spf = spf.Global(ctx)

    var currLangCode by remember {
        mutableStateOf(spf.language)
    }

    val items = remember {
        // concat "Follow System" and all languages
        val all = listOf<Lang>(
            Lang("", ctx.getString(R.string.follow_system))
        ) + languages

        all.map { lang ->
            LabelItem(
                id = lang.code,
                label = if (lang.emoji != null) "${lang.emoji}${if (lang.code.isEmpty()) "" else "  " + lang.code}" else lang.code,
                leadingIcon = if (lang.iconId != null) {
                    { ResIcon(lang.iconId, modifier = M.size(20.dp), color = Color.Unspecified) }
                } else null,
                onClick = {
                    spf.language = lang.code

                    if (Build.VERSION.SDK_INT >= ANDROID_13) {
                        Util.setLocale(ctx, lang.code)
                    } else {
                        spf.flush() // flush before restarting the process
                        Thread.sleep(300)
                        Launcher.restartProcess(ctx)
                    }
                }
            )
        }
    }

    val selected = remember(currLangCode) {
        items.indexOfFirst {
            it.id == currLangCode
        }
    }


    LabeledRow(
        R.string.language,
        helpTooltip = Str(R.string.help_language),
        content = {
            ComboBox(
                items,
                selected,
                displayType = ComboDisplayType.IconLabel
            )
        }
    )
}