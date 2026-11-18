package spam.blocker.service.bot

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import spam.blocker.def.Def.ForNumber
import spam.blocker.service.bot.stream.GroupedRuleStream
import spam.blocker.service.bot.stream.MappingRuleStream
import spam.blocker.service.bot.stream.RuleStream
import spam.blocker.service.bot.stream.readAll
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.Checker.RegexRuleChecker
import spam.blocker.ui.M
import spam.blocker.ui.darken
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.regex.RegexRuleFilterField
import spam.blocker.ui.setting.regex.EditRegexDialog
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.Placeholder
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.ResIcon20
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
        return try {
            aCtx.logger?.debug(ctx.getString(R.string.adding_numbers_to_spam_db))

            val now = System.currentTimeMillis()
            val extra = if (importReason == ImportDbReason.ByAPI) domainFromUrl(aCtx.httpUrl) else null
            var count = 0

            fun import(rules: List<RegexRule>): String? {
                if (rules.isEmpty()) return null
                count += rules.size
                return SpamTable.addAll(ctx, rules.map {
                    SpamNumber(
                        peer = it.pattern,
                        time = now,
                        importReason = importReason,
                        importReasonExtra = extra,
                    )
                })
            }

            val error = (aCtx.lastOutput as RuleStream).use { stream ->
                generateSequence { stream.batchReadRules().takeIf { it.isNotEmpty() } }
                    .map { import(it) }
                    .firstOrNull { it != null }
            }
            if (error != null) {
                aCtx.logger?.error(error)
                return false
            }

            aCtx.logger?.debug(ctx.getString(R.string.added_n_numbers_to_spam_db).format("$count"))
            Events.spamDbUpdated.fire()
            aCtx.logger?.success(ctx.getString(R.string.imported_successfully))
            true
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
        return listOf(ParamType.RuleStream)
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
        val numberList = (aCtx.lastOutput as? RuleStream)?.readAll()
            ?.map { it.pattern }
            ?.distinct()
            ?: return false

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
        aCtx.logger?.debug(
            ctx.getString(R.string.create_rule_with_numbers)
                .format("${numbers.size}")
        )

        val newRule = RegexRule(
            pattern = numbers.joinToString("|").let {
                if (numbers.size > 1) "($it)" else it
            }, 
            description = description,
            priority = priority,
            isBlacklist = !isWhitelist,
        )
        getTable().addNew(ctx, newRule)
    }

    private fun replace(ctx: Context, aCtx: ActionContext, numbers: List<String>) {
        aCtx.logger?.debug(
            ctx.getString(R.string.replace_rule_with_desc)
                .format(description)
        )

        val table = getTable()
        val oldRules = table.findByDesc(ctx, description)
        if (oldRules.isEmpty()) {
            aCtx.logger?.warn(
                ctx.getString(R.string.rule_with_desc_not_found)
                    .format(description)
            )
            create(ctx, aCtx, numbers)
        } else {
            val previous = oldRules[0]
            table.updateById(
                ctx,
                previous.id,
                previous.copy(
                    pattern = numbers.joinToString("|").let {
                        if (numbers.size > 1) "($it)" else it
                    }, 
                    description = description,
                    priority = priority,
                    isBlacklist = !isWhitelist,
                )
            )
        }
    }

    private fun merge(ctx: Context, aCtx: ActionContext, numbers: List<String>) {
        aCtx.logger?.debug(
            ctx.getString(R.string.merging_rule_with_desc)
                .format(description)
        )

        val table = getTable()
        val oldRules = table.findByDesc(ctx, description)
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

            table.updateById(
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
        return listOf(ParamType.RuleStream)
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
        return try {
            var count = 0
            fun import(rules: List<RegexRule>) {
                count += rules.size
                rules.forEach {
                    when (importType) {
                        ImportType.Create -> create(ctx, it)
                        ImportType.Replace -> replace(ctx, it)
                        else -> merge(ctx, it)
                    }
                }
            }

            (aCtx.lastOutput as RuleStream).use { stream ->
                generateSequence { stream.batchReadRules().takeIf { it.isNotEmpty() } }
                    .forEach(::import)
            }

            if (count == 0) {
                aCtx.logger?.warn(ctx.getString(R.string.nothing_to_import))
                return true
            }
            aCtx.logger?.info(ctx.getString(R.string.imported_n_rules).format("$count"))
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
        getTable().addNew(ctx, rule)
    }

    private fun replace(ctx: Context, rule: RegexRule) {
        val table = getTable()
        val oldRules = table.findByDesc(ctx, rule.description)
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
        val oldRules = table.findByDesc(ctx, rule.description)
        if (oldRules.isEmpty()) {
            create(ctx, rule)
        } else {
            val prevRule = oldRules[0]
            val oldNumbers = prevRule.pattern.trim('(', ')').split('|')

            val numbers = rule.pattern.trim('(', ')').split('|')

            val all = (oldNumbers + numbers).distinct()

            table.updateById(
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
        return listOf(ParamType.RuleStream)
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
                helpTooltip = Str(R.string.help_regex_action_add_mode_multiple)
                    .format(Str(R.string.help_regex_action_add_mode)),
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
        val ruleStream = aCtx.lastOutput as? RuleStream ?: return false

        aCtx.logger?.debug(
            ctx.getString(R.string.replace_number_from_to)
                .format(from, to)
        )

        val regex = from.toRegex()
        aCtx.lastOutput = MappingRuleStream(ruleStream) { rule ->
            rule.copy(pattern = regex.replace(rule.pattern, to))
        }
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
        return listOf(ParamType.RuleStream)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.RuleStream)
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
            enableNumberFlags = true
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

        aCtx.lastOutput = GroupedRuleStream(map)

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

        aCtx.lastOutput = GroupedRuleStream(map)
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
        return listOf(ParamType.RuleStream)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_find)
    }

    @Composable
    override fun Options() {
        var patternState by remember { mutableStateOf(pattern) }

        RegexRuleFilterField(
            pattern = patternState,
            onPatternChange = { newVal ->
                pattern = newVal
                patternState = newVal
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

        val rulesMap = (aCtx.lastOutput as? GroupedRuleStream)?.groups ?: return false

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

        val rulesMap = (aCtx.lastOutput as? GroupedRuleStream)?.groups ?: return false

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
                    table.updateById(ctx, rule.id, newRule)

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
        return listOf(ParamType.RuleStream)
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
        val C = G.palette

        val editTrigger = remember { mutableStateOf(false) }

        val actions = LocalActions.current
        val myIndex = actions.indexOf(this)
        val prevAction = if (myIndex > 0) actions[myIndex - 1] else null // get the prev action `FindRules`

        if (editTrigger.value) {
            // Find the target rule in the previous action `Find Rules`
            val baseRule = remember(prevAction) {
                if (prevAction is FindRules) {
                    val matchingRule = listOf(G.NumberRuleVM.rules, G.ContentRuleVM.rules, G.QuickCopyRuleVM.rules)
                        .firstNotNullOfOrNull { rules ->
                            rules.firstOrNull { rule ->
                                prevAction.pattern.regexMatches(rule.description, prevAction.flags)
                            }
                        }
                    matchingRule ?: RegexRule()
                } else {
                    RegexRule()
                }
            }

            val initRule = try {
                val baseMap = JSONObject(PermissiveJson.encodeToString(baseRule)).toMap()
                val cfgMap = JSONObject(config).toMap()
                PermissiveJson.decodeFromString<RegexRule>(JSONObject(baseMap + cfgMap).toString())
            } catch (e: Exception) {
                baseRule
            }

            EditRegexDialog(
                trigger = editTrigger,
                forType = ForNumber,
                initRule = initRule,
                onSave = { updatedRule ->
                    val defaultMap = JSONObject(PermissiveJson.encodeToString(baseRule)).toMap()
                    val updatedMap = JSONObject(PermissiveJson.encodeToString(updatedRule)).toMap()
                    val diff = updatedMap.filter { (k, v) -> defaultMap[k] != v }
                    config = JSONObject(diff).toString()
                }
            )
        }

        StrInputBox(
            label = { Text(Str(R.string.config_json)) },
            placeholder = { Placeholder(Str(R.string.action_modify_rules_placeholder)) },
            text = config,
            onValueChange = {
                config = it
            },
            leadingIcon = {
                ResIcon20(R.drawable.ic_note, color = C.infoBlue, modifier = M.clickable {
                    editTrigger.value = true
                })
            }
        )
    }
}
