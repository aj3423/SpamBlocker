package spam.blocker.ui.setting.regex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LongPressButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.rememberFileReadChooser
import spam.blocker.util.CSVParser
import spam.blocker.util.Lambda
import spam.blocker.util.Util
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.PushbackReader

@Composable
fun ImportRuleButton(
    vm: RuleViewModel,
    onClick: Lambda,
) {
    val ctx = LocalContext.current

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


    val importRuleItems = remember {
        ctx.resources.getStringArray(R.array.import_csv_type).mapIndexed { menuItemIndex, label ->

            LabelItem(
                label = label,

                onClick = {
                    fileReader.popup(
                        type = "text/*",
                    ) { fn: String?, raw: ByteArray? ->
                        if (raw == null)
                            return@popup

                        val csv = CSVParser(
                            PushbackReader(BufferedReader(InputStreamReader(ByteArrayInputStream(raw)))),
                        ).parse()

                        // show error if there is no column `pattern`, because it will generate empty rows.
                        if (!csv.headers.contains("pattern")) {
                            warningTrigger.value = true
                            return@popup
                        }

                        val allRules = csv.rows.map { row ->
                            RegexRule.fromMap(csv.headers.zip(row).toMap())
                        }

                        when (menuItemIndex) {
                            0 -> { // import as single rule
                                fun add(rules: List<RegexRule>, isBlacklist: Boolean) {
                                    if (rules.isEmpty())
                                        return

                                    val joined = rules.map {
                                        Util.clearNumber(it.pattern)
                                    }.filter {
                                        it.isNotEmpty()
                                    }.joinToString ( separator = "|" )

                                    val rule = RegexRule().apply {
                                        pattern = "($joined)"
                                        description = fn ?: ""
                                        this.isBlacklist = isBlacklist
                                    }
                                    // 1. add to db
                                    vm.table.addNewRule(ctx, rule)

                                    // 2. refresh gui
                                    vm.reloadDb(ctx)
                                }
                                // Add two different rules for whitelist/blacklist
                                val white = allRules.filter { !it.isBlacklist }
                                val black = allRules.filter { it.isBlacklist }
                                add(white, false)
                                add(black, true)
                            }
                            1 -> { // import as multi rules
                                // 1. add to db
                                allRules.forEach {
                                    vm.table.addNewRule(ctx, it)
                                }

                                // 2. refresh gui
                                vm.reloadDb(ctx)
                            }
                        }
                    }
                }
            )
        }
    }
    DropdownWrapper(items = importRuleItems) { expanded ->
        LongPressButton(
            label = Str(R.string.new_),
            color = SkyBlue,
            onClick = onClick,
            onLongClick = {
                expanded.value = true
            },
        )
    }
}
