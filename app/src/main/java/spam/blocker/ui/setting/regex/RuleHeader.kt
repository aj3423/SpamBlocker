package spam.blocker.ui.setting.regex

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.db.defaultRegexRuleByType
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MenuButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.rememberFileReadChooser

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


    var initRule by remember { mutableStateOf<RegexRule?>(null) }
    val addRuleTrigger = rememberSaveable { mutableStateOf(false) }

    if (addRuleTrigger.value) {
        RuleEditDialog(
            trigger = addRuleTrigger,
            initRule = initRule ?: defaultRegexRuleByType(forType),
            forType = forType,
            onSave = { newRule ->
                // 1. add to db
                vm.table.addNewRule(ctx, newRule)

                // 2. reload from db
                vm.reloadDb(ctx)
            }
        )
    }

    val shortClickItems = remember {
        val ret = mutableListOf(
            LabelItem(
                label = ctx.getString(R.string.customize),
                leadingIcon = { GreyIcon(R.drawable.ic_note) }
            ) {
                initRule = null
                addRuleTrigger.value = true
            },
            DividerItem(),
        )
        ret += RegexRulePresets.getValue(forType).map { preset ->
            LabelItem(
                label = preset.label(ctx),
                tooltip = preset.tooltip(ctx)
            ) {
                initRule = preset.newInstance(ctx)
                addRuleTrigger.value = true
            }
        }
        ret
    }

    val fileReader = rememberFileReadChooser()
    fileReader.Compose()
    val warningTrigger = rememberSaveable { mutableStateOf(false) }
    if (warningTrigger.value) {
        PopupDialog(
            trigger = warningTrigger,
            content = {
                HtmlText(html = ctx.getString(R.string.failed_to_import_from_csv))
            },
            icon = { ResIcon(R.drawable.ic_fail_red, color = Salmon) },
        )
    }
    val longClickItems = remember {
        importRuleItems(ctx, vm, fileReader, warningTrigger)
    }

    val helpTooltip = remember {
        when (forType) {
            Def.ForNumber -> ctx.getString(R.string.help_number_rules) + ctx.getString(R.string.import_csv_columns)
            Def.ForSms -> ctx.getString(R.string.help_sms_content_filter)
            else -> ctx.getString(R.string.help_quick_copy)
        }
    }

    LabeledRow(
        labelId = labelId,
        modifier = M.clickable{ vm.toggleCollapse(ctx) },
        isCollapsed = vm.listCollapsed.value,
        toggleCollapse = { vm.toggleCollapse(ctx) },
        helpTooltip = helpTooltip,
    ) {
        if (forType == Def.ForNumber || forType == Def.ForSms) {
            MenuButton(
                label = Str(R.string.new_),
                color = SkyBlue,
                items = shortClickItems,
                longTapItems = longClickItems,
            )
        } else {
            MenuButton(
                label = Str(R.string.new_),
                color = SkyBlue,
                items = shortClickItems,
            )
        }
    }
}
