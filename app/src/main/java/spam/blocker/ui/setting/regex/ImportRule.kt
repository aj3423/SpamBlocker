package spam.blocker.ui.setting.regex

import android.content.Context
import androidx.compose.runtime.MutableState
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.ui.widgets.FileReadChooser
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.util.CSVParser
import spam.blocker.util.Util
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.PushbackReader

fun importRuleItems(
    ctx: Context,
    vm: RuleViewModel,
    fileReader: FileReadChooser,
    warningTrigger: MutableState<Boolean>,
): List<IMenuItem> {
    return ctx.resources.getStringArray(R.array.import_csv_type)
        .mapIndexed { menuItemIndex, label ->

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
                                    }.joinToString(separator = "|")

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
