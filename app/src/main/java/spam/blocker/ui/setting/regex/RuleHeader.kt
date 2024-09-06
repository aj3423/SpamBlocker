package spam.blocker.ui.setting.regex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import spam.blocker.R
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.defaultRegexRuleByType
import spam.blocker.def.Def
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton

// The row:
//   "Number Rule"       [Add] [Test]
@Composable
fun RuleHeader(
    forType: Int,
    ruleList: SnapshotStateList<RegexRule>,
) {
    val ctx = LocalContext.current

    val table = remember {
        when (forType) {
            Def.ForNumber -> NumberRuleTable()
            Def.ForSms -> ContentRuleTable()
            else -> QuickCopyRuleTable()
        }
    }
    val labelId = remember {
        when (forType) {
            Def.ForNumber -> R.string.label_number_filter
            Def.ForSms -> R.string.label_content_filter
            else -> R.string.quick_copy
        }
    }
    val helpTooltipId = remember {
        when (forType) {
            Def.ForNumber -> R.string.help_number_filter
            Def.ForSms -> R.string.help_sms_content_filter
            else -> R.string.help_quick_copy
        }
    }

    val addRuleTrigger = rememberSaveable { mutableStateOf(false) }

    if (addRuleTrigger.value) {
        RuleEditDialog(
            trigger = addRuleTrigger,
            initRule = defaultRegexRuleByType(forType),
            forType = forType,
            onSave = { newRule ->
                // 1. add to db
                table.addNewRule(ctx, newRule)

                // 2. reload from db
                ruleList.clear()
                ruleList.addAll(table.listAll(ctx))
            }
        )
    }

    LabeledRow(
        labelId = labelId,
        helpTooltipId = helpTooltipId,
    ) {
        if (forType == Def.ForNumber) {
            ImportRuleButton(
                ruleList = ruleList
            ) {
                addRuleTrigger.value = true
            }
        } else {
            StrokeButton(
                label = Str(R.string.new_),
                color = SkyBlue,
                onClick = { addRuleTrigger.value = true },
            )
        }
    }
}
