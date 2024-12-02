package spam.blocker.ui.setting.regex

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.R
import spam.blocker.db.defaultRegexRuleByType
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.SettingLabel
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton

// The row:
//   "Number Rule"       [Add] [Test]
@Composable
fun RuleHeader(
    vm: RuleViewModel,
) {
    val ctx = LocalContext.current
    val forType = vm.forType

    val labelId = remember {
        when (forType) {
            Def.ForNumber -> R.string.label_number_rules
            Def.ForSms -> R.string.label_content_rules
            else -> R.string.quick_copy
        }
    }
    val helpTooltipId = remember {
        when (forType) {
            Def.ForNumber -> R.string.help_number_rules
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
                vm.table.addNewRule(ctx, newRule)

                // 2. reload from db
                vm.reloadDb(ctx)
            }
        )
    }

    LabeledRow(
        modifier = M.clickable{ vm.toggleCollapse(ctx) },
        label = {
            RowVCenterSpaced(4) {
                SettingLabel(
                    labelId = labelId
                )
                if (vm.listCollapsed.value) {
                    GreyIcon16(
                        iconId = R.drawable.ic_dropdown_arrow,
                    )
                }
            }
        },
        helpTooltipId = helpTooltipId,
    ) {
        if (forType == Def.ForNumber || forType == Def.ForSms) {
            ImportRuleButton(
                vm = vm,
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
