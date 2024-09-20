package spam.blocker.ui.setting.regex

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RuleTable

open class RuleViewModel(
    val table: RuleTable,
) {
    val rules = mutableStateListOf<RegexRule>()
    val searchEnabled = mutableStateOf(false)

    var filter = ""

    fun reload(ctx: Context) {
        rules.clear()
        var all = table.listAll(ctx)
        if (filter.isNotEmpty()) {
            all = all.filter {
                it.pattern.contains(filter) || it.description.contains(filter)
            }
        }
        rules.addAll(all)
    }
}

class NumberRuleViewModel : RuleViewModel(NumberRuleTable())
class ContentRuleViewModel : RuleViewModel(ContentRuleTable())
class QuickCopyRuleViewModel : RuleViewModel(QuickCopyRuleTable())
