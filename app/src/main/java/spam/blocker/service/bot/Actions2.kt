package spam.blocker.service.bot

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.ImportDbReason
import spam.blocker.db.RegexRule
import spam.blocker.db.RegexTable
import spam.blocker.db.SpamNumber
import spam.blocker.db.SpamTable
import spam.blocker.db.ruleTableForType
import spam.blocker.def.Def
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.Checker.RegexRuleChecker
import spam.blocker.ui.darken
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.Placeholder
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.SummaryLabel
import spam.blocker.util.A
import spam.blocker.util.PermissiveJson
import spam.blocker.util.Util.domainFromUrl
import spam.blocker.util.formatAnnotated
import spam.blocker.util.regexMatches
import spam.blocker.util.toMap

/*
input: List<RegexRule> or QueryResult
output: null
 */
@Serializable
@SerialName("ImportToSpamDB")
class ImportToSpamDB(
    // Only presets can use ImportDbReason.ByAPI
    // This is for internal app use, not displayed on GUI
    val importReason: ImportDbReason = ImportDbReason.Manually,
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rules = aCtx.lastOutput as List<*> // it's actually `List<RegexRule>`

        if (rules.isEmpty()) {
            return true
        }

        return try {
            val now = System.currentTimeMillis()

            val numbers = rules.map {
                SpamNumber(
                    peer = (it as RegexRule).pattern,
                    time = now,
                    importReason = importReason,
                    // when import after API query, log the api domain for future reporting
                    importReasonExtra = if (importReason == ImportDbReason.ByAPI)
                        domainFromUrl(aCtx.httpUrl) else null,
                )
            }

            aCtx.logger?.debug(
                ctx.getString(R.string.add_n_numbers_to_spam_db)
                    .format("${numbers.size}")
            )

            val errorStr = SpamTable.addAll(ctx, numbers)

            // Fire a global event to update UI
            Events.spamDbUpdated.fire()

            if (errorStr == null) {
                aCtx.logger?.success(ctx.getString(R.string.imported_successfully))
                true
            } else {
                aCtx.logger?.error(errorStr)
                false
            }
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_import_to_spam_db)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_import_to_spam_db)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_db_add)
    }

    @Composable
    override fun Options() {
        NoOptionNeeded()
    }
}

enum class ImportType {
    Create,
    Replace, // create if not found
    Merge, // create if not found
}

/*
input: List<RegexRule>
output: null
 */
@Serializable
@SerialName("ImportAsRegexRule")
class ImportAsRegexRule(
    var description: String = "",
    var priority: Int = 0,
    var isWhitelist: Boolean = false,

    var importType: ImportType = ImportType.Create,
    var importAs: Int = Def.ForNumber,
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        // lastOutput is `List<RegexRule>`
        val numberList = (aCtx.lastOutput as List<*>)
            .map { (it as RegexRule).pattern }
            .distinct()

        return try {

            // Do nothing when there aren't any numbers to add, to prevent adding a `()`
            if (numberList.isEmpty()) {
                aCtx.logger?.warn(ctx.getString(R.string.nothing_to_import))
                return true
            }

            aCtx.logger?.info(
                ctx.getString(R.string.importing_n_numbers)
                    .format("${numberList.size}")
            )

            when (importType) {
                ImportType.Create -> create(ctx, aCtx, numberList)
                ImportType.Replace -> replace(ctx, aCtx, numberList)
                else -> merge(ctx, aCtx, numberList)
            }

            // fire event to update the UI
            Events.regexRuleUpdated.fire()

            true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }
    }

    private fun getTable(): RegexTable {
        return ruleTableForType(importAs)
    }

    private fun create(ctx: Context, aCtx: ActionContext, numbers: List<String>) {
        // Join numbers to `11|22|33...`
        val combinedPattern = numbers.joinToString("|")

        aCtx.logger?.debug(
            ctx.getString(R.string.create_rule_with_numbers)
                .format("${numbers.size}")
        )

        val newRule = RegexRule(
            pattern = "($combinedPattern)",
            description = description,
            priority = priority,
            isBlacklist = !isWhitelist,
        )
        getTable().addNewRule(ctx, newRule)
    }

    private fun replace(ctx: Context, aCtx: ActionContext, numbers: List<String>) {
        aCtx.logger?.debug(
            ctx.getString(R.string.replace_rule_with_desc)
                .format(description)
        )

        val table = getTable()
        val oldRules = table.findRuleByDesc(ctx, description)
        if (oldRules.isEmpty()) {
            aCtx.logger?.warn(
                ctx.getString(R.string.rule_with_desc_not_found)
                    .format(description)
            )
            create(ctx, aCtx, numbers)
        } else {
            // 1. delete the previous rule
            table.deleteById(ctx, oldRules[0].id)
            // 2. create a new one
            create(ctx, aCtx, numbers)
        }
    }

    private fun merge(ctx: Context, aCtx: ActionContext, numbers: List<String>) {
        aCtx.logger?.debug(
            ctx.getString(R.string.merging_rule_with_desc)
                .format(description)
        )

        val table = getTable()
        val oldRules = table.findRuleByDesc(ctx, description)
        if (oldRules.isEmpty()) {
            aCtx.logger?.warn(
                ctx.getString(R.string.rule_with_desc_not_found)
                    .format(description)
            )

            create(ctx, aCtx, numbers)
        } else {
            val previous = oldRules[0]
            val oldNumbers = previous.pattern.trim('(', ')').split('|')

            val all = (oldNumbers + numbers).distinct()

            aCtx.logger?.debug(
                ctx.getString(R.string.regex_count_after_merge)
                    .format("${oldNumbers.size}", "${all.size}")
            )

            table.updateRuleById(
                ctx, previous.id, previous.copy(
                    pattern = "(" + all.joinToString("|") + ")"
                )
            )
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_import_as_regex_rule)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        if(description.isNotEmpty()) {
            SummaryLabel(description)
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_import_as_regex_rule)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_regex)
    }

    @Composable
    override fun Options() {
        val ctx = LocalContext.current
        Column {
            StrInputBox(
                text = description,
                label = { Text(Str(R.string.description)) },
                onValueChange = { description = it }
            )
            PriorityBox(priority) { newVal, hasError ->
                if (!hasError) {
                    priority = newVal!!
                }
            }
            // Import to Number/Content/QuickCopy
            LabeledRow(R.string.import_as) {
                var selected by rememberSaveable {
                    mutableIntStateOf(importAs)
                }

                val items = listOf(
                    Str(R.string.number_rule),
                    Str(R.string.content_rule),
                    Str(R.string.quick_copy),
                )
                ComboBox(
                    items = items.mapIndexed { index, label ->
                        LabelItem(
                            label = label,
                        ) {
                            selected = index
                            importAs = index
                        }
                    },
                    selected = selected,
                )
            }
            // Type Whitelist/ Blacklist
            LabeledRow(R.string.type) {
                var applyToWorB by rememberSaveable { mutableIntStateOf(if (isWhitelist) 0 else 1) }
                val items = listOf(
                    RadioItem(Str(R.string.allow), G.palette.success),
                    RadioItem(Str(R.string.block), G.palette.error),
                )
                RadioGroup(items = items, selectedIndex = applyToWorB) {
                    applyToWorB = it
                    isWhitelist = it == 0
                }
            }
            // Type Create/Replace/Merge
            LabeledRow(
                R.string.mode,
                helpTooltip = Str(R.string.help_regex_action_add_mode),
            ) {
                var selected by rememberSaveable {
                    mutableIntStateOf(ImportType.entries.indexOf(importType))
                }

                val items = ctx.resources.getStringArray(R.array.regex_action_add_mode)
                ComboBox(
                    items = items.mapIndexed { index, label ->
                        LabelItem(
                            label = label,
                        ) {
                            selected = index
                            importType = ImportType.entries[index]
                        }
                    },
                    selected = selected,
                )
            }
        }
    }
}


/*
input: List<RegexRule>
output: null
 */
@Serializable
@SerialName("ImportAsMultipleRegexRules")
class ImportAsMultipleRegexRules(
    var importType: ImportType = ImportType.Create,
    var importAs: Int = Def.ForNumber,
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rules = (aCtx.lastOutput as List<*>) // lastOutput is `List<RegexRule>`
            .map { it as RegexRule } // convert List<*> to List<RegexRule>

        return try {

            // Do nothing when there isn't any number to add, to prevent adding a `()`
            if (rules.isEmpty()) {
                aCtx.logger?.warn(ctx.getString(R.string.nothing_to_import))
                return true
            }

            aCtx.logger?.info(
                ctx.getString(R.string.importing_n_rules)
                    .format("${rules.size}")
            )

            rules.forEach {
                when (importType) {
                    ImportType.Create -> create(ctx, it)
                    ImportType.Replace -> replace(ctx, it)
                    else -> merge(ctx, it)
                }
            }


            // fire event to update the UI
            Events.regexRuleUpdated.fire()

            true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }
    }

    private fun getTable(): RegexTable {
        return ruleTableForType(importAs)
    }

    private fun create(ctx: Context, rule: RegexRule) {
        getTable().addNewRule(ctx, rule)
    }

    private fun replace(ctx: Context, rule: RegexRule) {
        val table = getTable()
        val oldRules = table.findRuleByDesc(ctx, rule.description)
        if (oldRules.isEmpty()) {
            create(ctx, rule)
        } else {
            // 1. delete the previous rule
            table.deleteById(ctx, oldRules[0].id)
            // 2. create a new one
            create(ctx, rule)
        }
    }

    private fun merge(ctx: Context, rule: RegexRule) {
        val table = getTable()
        val oldRules = table.findRuleByDesc(ctx, rule.description)
        if (oldRules.isEmpty()) {
            create(ctx, rule)
        } else {
            val prevRule = oldRules[0]
            val oldNumbers = prevRule.pattern.trim('(', ')').split('|')

            val numbers = rule.pattern.trim('(', ')').split('|')

            val all = (oldNumbers + numbers).distinct()

            table.updateRuleById(
                ctx, prevRule.id, prevRule.copy(
                    pattern = "(" + all.joinToString("|") + ")"
                )
            )
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_import_as_multiple_regex_rules)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_import_as_multiple_regex_rules)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_regex)
    }

    @Composable
    override fun Options() {
        val ctx = LocalContext.current
        Column {
            // Import to Number/Content/QuickCopy
            LabeledRow(R.string.import_as) {
                var selected by rememberSaveable {
                    mutableIntStateOf(importAs)
                }

                val items = listOf(
                    Str(R.string.number_rule),
                    Str(R.string.content_rule),
                    Str(R.string.quick_copy),
                )
                ComboBox(
                    items = items.mapIndexed { index, label ->
                        LabelItem(
                            label = label,
                        ) {
                            selected = index
                            importAs = index
                        }
                    },
                    selected = selected,
                )
            }

            // Type Create/Replace/Merge
            LabeledRow(
                R.string.mode,
                helpTooltip = Str(R.string.help_regex_action_add_mode),
            ) {
                var selected by rememberSaveable {
                    mutableIntStateOf(ImportType.entries.indexOf(importType))
                }

                val items = ctx.resources.getStringArray(R.array.regex_action_add_mode)
                ComboBox(
                    items = items.mapIndexed { index, label ->
                        LabelItem(
                            label = label,
                        ) {
                            selected = index
                            importType = ImportType.entries[index]
                        }
                    },
                    selected = selected,
                )
            }
        }
    }
}

/*
input: List<RegexRule>
output: List<RegexRule>
 */
@Serializable
@SerialName("ConvertNumber")
class ConvertNumber(
    var from: String = "",
    var flags: Int = Def.DefaultRegexFlags,
    var to: String = "",
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rules = aCtx.lastOutput as List<*> // List<RegexRule>

        aCtx.logger?.debug(
            ctx.getString(R.string.replace_number_from_to)
                .format(from, to)
        )

        val clearedRuleList = rules.map {
            val r = it as RegexRule

            val newNum = from.toRegex().replace(r.pattern, to)

            r.copy(
                pattern = newNum
            )
        }
        aCtx.lastOutput = clearedRuleList
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_replace_number)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        SummaryLabel("$from -> $to")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_replace_number)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_replace)
    }

    @Composable
    override fun Options() {

        val flagsState = remember { mutableIntStateOf(flags) }
        RegexInputBox(
            label = { Text(Str(R.string.replace_from)) },
            placeholder = {
                Placeholder(
                    Str(R.string.regex_pattern) + "\n"
                            + Str(R.string.for_example) + " " + "(\"|-)"
                )
            },
            regexStr = from,
            onRegexStrChange = { newVal, hasErr ->
                if (!hasErr) {
                    from = newVal
                }
            },
            regexFlags = flagsState,
            onFlagsChange = {
                flagsState.intValue = it
                flags = it
            },
            showNumberFlags = true
        )
        StrInputBox(
            label = { Text(Str(R.string.replace_to)) },
            text = to,
            onValueChange = {
                to = it
            }
        )
    }
}

/*
input: None
output: Map<forType, List<RegexRule>>
use case: auto disable rules: https://github.com/aj3423/SpamBlocker/issues/190
 */
@Serializable
@SerialName("FindRules")
class FindRules(
    var pattern: String = "", // description pattern
    var flags: Int = Def.DefaultRegexFlags,
) : IPermissiveAction {

    // This is used in CalendarPreprocessor.
    // Rules have been loaded into memory as List<Checker.RegexRule>
    fun findInMemory(ctx: Context, aCtx: ActionContext) : Boolean {
        val cCtx = aCtx.cCtx!!

        val map = listOf(
            Def.ForNumber, Def.ForSms,
        ).associateWith { type ->
            cCtx.checkers.filter {
                if (type == Def.ForNumber)
                    it is RegexRuleChecker && it !is Checker.Content
                else
                    it is Checker.Content
            }.map {
                (it as RegexRuleChecker).rule
            }.filter {
                pattern.regexMatches(it.description, flags)
            }
        }

        aCtx.lastOutput = map

        return true
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (aCtx.isInMemory) {
            return findInMemory(ctx, aCtx)
        }
        val C = G.palette

        // foundPair is Pair<tableIndex, List<RegexRule>>?
        val map = listOf(
            Def.ForNumber, Def.ForSms, Def.ForQuickCopy
        )
            .associateWith {
                ruleTableForType(it).listAll(ctx).filter {
                    pattern.regexMatches(it.description, flags)
                }
            }

        aCtx.logger?.debug(
            ctx.getString(R.string.find_rule_with_desc)
                .formatAnnotated("${map.values.sumOf { it.size }}".A(C.teal200), pattern.A(C.textGrey.darken()))
        )

        aCtx.lastOutput = map
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_find_rules)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        val ctx = LocalContext.current

        SummaryLabel(pattern)
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_find_rules)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_find)
    }

    @Composable
    override fun Options() {
        val flagsState = remember { mutableIntStateOf(flags) }
        RegexInputBox(
            label = { Text(Str(R.string.description)) },
            placeholder = {
                Placeholder(
                    Str(R.string.regex_pattern) + "\n"
                            + Str(R.string.for_example) + "\n"
                            + ".*"
                )
            },
            regexStr = pattern,
            onRegexStrChange = { newVal, hasErr ->
                if (!hasErr) {
                    pattern = newVal
                }
            },
            regexFlags = flagsState,
            onFlagsChange = {
                flagsState.intValue = it
                flags = it
            }
        )
    }
}

/*
input: Map<forType, List<RegexRule>>
output: none
 */
@Serializable
@SerialName("ModifyRules")
class ModifyRules(
    var config: String = "",
) : IPermissiveAction {
    fun modifyInMemory(ctx: Context, aCtx: ActionContext): Boolean {
        val C = G.palette

        val rulesMap = aCtx.lastOutput as Map<Int, List<RegexRule>>

        val cCtx = aCtx.cCtx!!

        // e.g.: {"flag": 2}
        val mapConfig = JSONObject(config).toMap()

        rulesMap.forEach { (forType, ruleList) ->

            cCtx.checkers.filter {
                if (forType == Def.ForNumber)
                    it is RegexRuleChecker && it !is Checker.Content
                else
                    it is Checker.Content
            }.filter { checker ->
                ruleList.any { it.id == (checker as RegexRuleChecker).rule.id }
            }.forEach {
                val rule = (it as RegexRuleChecker).rule

                val strOrigin = PermissiveJson.encodeToString(rule)
                val mapOrigin = JSONObject(strOrigin).toMap()

                val mapModified = mapOrigin + mapConfig // override with mapModify

                val newRule = PermissiveJson.decodeFromString<RegexRule>(JSONObject(mapModified).toString())

                aCtx.logger?.debug(
                    ctx.getString(R.string.rule_updated_temporarily)
                        .formatAnnotated(
                            rule.descOrPattern().A(C.teal200),
                            config.A(C.textGrey.darken())
                        )
                )
                it.rule = newRule
            }
        }

        return true
    }
    @Suppress("UNCHECKED_CAST")
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val C = G.palette

        if (aCtx.isInMemory) {
            return modifyInMemory(ctx, aCtx)
        }

        val rulesMap = aCtx.lastOutput as Map<Int, List<RegexRule>>

        aCtx.logger?.debug(
            ctx.getString(R.string.modify_n_rules)
                .format("${rulesMap.values.sumOf { it.size} }")
        )

        try {
            val mapConfig = JSONObject(config).toMap()

            rulesMap.forEach { (forType, ruleList) ->
                val table = ruleTableForType(forType)

                ruleList.forEach { rule ->
                    val strOrigin = PermissiveJson.encodeToString(rule)
                    val mapOrigin = JSONObject(strOrigin).toMap()

                    val mapModified = mapOrigin + mapConfig // override with mapModify

                    val newRule =
                        PermissiveJson.decodeFromString<RegexRule>(JSONObject(mapModified).toString())
                    table.updateRuleById(ctx, rule.id, newRule)

                    aCtx.logger?.debug(
                        ctx.getString(R.string.rule_updated)
                            .formatAnnotated(
                                rule.descOrPattern().A(C.teal200),
                                config.A(C.textGrey.darken())
                            )
                    )
                }
            }
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            return false
        }

        // fire event to update the UI
        Events.regexRuleUpdated.fire()

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_modify_rules)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        SummaryLabel(config)
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_modify_rules)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_replace)
    }

    @Composable
    override fun Options() {
        StrInputBox(
            label = { Text(Str(R.string.config_text)) },
            placeholder = { Placeholder(Str(R.string.action_modify_rules_placeholder)) },
            text = config,
            onValueChange = {
                config = it
            }
        )
    }
}
