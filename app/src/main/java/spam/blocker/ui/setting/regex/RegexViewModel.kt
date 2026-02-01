package spam.blocker.ui.setting.regex

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.QuickCopyRegexTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RegexTable
import spam.blocker.def.Def
import spam.blocker.util.spf

open class RegexViewModel(
    val table: RegexTable,
    val forType: Int,
) {
    val rules = mutableStateListOf<RegexRule>()
    val searchEnabled = mutableStateOf(false)
    val listCollapsed = mutableStateOf(false)
    val filter = mutableStateOf("")

    fun reloadDb(ctx: Context) {
        rules.clear()
        var all = table.listAll(ctx)
        if (filter.value.isNotEmpty()) {
            all = all.filter {
                it.pattern.contains(filter.value) || it.description.contains(filter.value)
            }
        }
        rules.addAll(all)
    }

    fun toggleCollapse(ctx: Context) {
        // don't collapse if it's empty
        if (rules.size == 0 && !listCollapsed.value) {
            return
        }
        listCollapsed.value = !listCollapsed.value
        val spf = spf.RegexOptions(ctx)
        when (forType) {
            Def.ForNumber -> spf.isNumberCollapsed = listCollapsed.value
            Def.ForSms -> spf.isContentCollapsed = listCollapsed.value
            else -> spf.isQuickCopyCollapsed = listCollapsed.value
        }
    }

    fun reloadOptions(ctx: Context) {
        val spf = spf.RegexOptions(ctx)

        listCollapsed.value = when (forType) {
            Def.ForNumber -> spf.isNumberCollapsed
            Def.ForSms -> spf.isContentCollapsed
            else -> spf.isQuickCopyCollapsed
        }
    }

    fun reloadDbAndOptions(ctx: Context) {
        reloadOptions(ctx)
        reloadDb(ctx)
    }
}

class NumberRegexViewModel : RegexViewModel(NumberRegexTable(), Def.ForNumber)
class ContentRegexViewModel : RegexViewModel(ContentRegexTable(), Def.ForSms)
class QuickCopyRegexViewModel : RegexViewModel(QuickCopyRegexTable(), Def.ForQuickCopy)
