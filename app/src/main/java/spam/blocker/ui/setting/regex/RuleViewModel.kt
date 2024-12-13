package spam.blocker.ui.setting.regex

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RuleTable
import spam.blocker.def.Def
import spam.blocker.util.spf

open class RuleViewModel(
    val table: RuleTable,
    val forType: Int,
) {
    val rules = mutableStateListOf<RegexRule>()
    val searchEnabled = mutableStateOf(false)
    val listCollapsed = mutableStateOf(false)

    var filter = ""

    fun reloadDb(ctx: Context) {
        rules.clear()
        var all = table.listAll(ctx)
        if (filter.isNotEmpty()) {
            all = all.filter {
                it.pattern.contains(filter) || it.description.contains(filter)
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
            Def.ForNumber -> spf.setNumberCollapsed(listCollapsed.value)
            Def.ForSms -> spf.setContentCollapsed(listCollapsed.value)
            else -> spf.setQuickCopyCollapsed(listCollapsed.value)
        }
    }

    fun reloadOptions(ctx: Context) {
        val spf = spf.RegexOptions(ctx)

        listCollapsed.value = when (forType) {
            Def.ForNumber -> spf.isNumberCollapsed()
            Def.ForSms -> spf.isContentCollapsed()
            else -> spf.isQuickCopyCollapsed()
        }
    }

    fun reloadDbAndOptions(ctx: Context) {
        reloadOptions(ctx)
        reloadDb(ctx)
    }
}

class NumberRuleViewModel : RuleViewModel(NumberRuleTable(), Def.ForNumber)
class ContentRuleViewModel : RuleViewModel(ContentRuleTable(), Def.ForSms)
class QuickCopyRuleViewModel : RuleViewModel(QuickCopyRuleTable(), Def.ForQuickCopy)
