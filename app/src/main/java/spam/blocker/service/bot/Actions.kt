package spam.blocker.service.bot

import android.content.Context
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.config.Configs
import spam.blocker.db.BotTable
import spam.blocker.db.CallTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.SmsTable
import spam.blocker.db.SpamNumber
import spam.blocker.db.SpamTable
import spam.blocker.db.reScheduleBot
import spam.blocker.def.Def
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.Spinner
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Algorithm.compressString
import spam.blocker.util.Algorithm.decompressToString
import spam.blocker.util.Csv
import spam.blocker.util.Now
import spam.blocker.util.PermissiveJson
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.Util
import spam.blocker.util.Xml
import spam.blocker.util.logi
import spam.blocker.util.resolvePathTags
import spam.blocker.util.resolveTimeTags
import spam.blocker.util.toMap
import spam.blocker.util.toStringMap
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun NoOptionNeeded() {
    GreyLabel(text = Str(R.string.no_config_needed))
}

@Serializable
@SerialName("CleanupHistory")
class CleanupHistory(
    var expiry: Int = 90 // days
) : IPermissiveAction {
    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val now = Now.currentMillis()
        val expireTimeMs = now - expiry.toLong() * 24 * 3600 * 1000

        logi("now clean up history db, deleting data before timestamp: $expireTimeMs")

        CallTable().clearRecordsBeforeTimestamp(ctx, expireTimeMs)
        SmsTable().clearRecordsBeforeTimestamp(ctx, expireTimeMs)

        return Pair(true, null)
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_cleanup_history)
    }

    override fun summary(ctx: Context): String {
        val nDays = ctx.resources.getQuantityString(R.plurals.days, expiry, expiry)
        return ctx.getString(R.string.expiry) + ": $nDays"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_cleanup_history)
    }

    override fun inputParamType(): ParamType {
        return ParamType.None
    }

    override fun outputParamType(): ParamType {
        return ParamType.None
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_history_cleanup)
    }

    @Composable
    override fun Options() {
        NumberInputBox(
            intValue = expiry,
            label = { Text(Str(R.string.expiry_days)) },
            onValueChange = { newVal, hasError ->
                if (!hasError) {
                    expiry = newVal!!
                }
            }
        )
    }
}

@Serializable
@SerialName("HttpDownload")
class HttpDownload(
    var url: String = "",
    var header: String = "",
) : IPermissiveAction {

    /*
    Split the header string:
        ua: chrome\n
        cookie: xxx\n
        some_key: yyy
    into:
        Map(
            ua => chrome,
            cookie => xxx,
            some_key => yyy,
        )
     */
    private fun splitHeader(allHeadersStr: String): Map<String, String> {
        return allHeadersStr.lines().filter { it.trim().isNotEmpty() }.associate { line ->
            val (key, value) = line.split(":").map { it.trim() }
            key to value
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        var ret: Pair<Boolean, Any?> = Pair(false, null)

        var connection: HttpURLConnection? = null

        val thread = GlobalScope.launch(Dispatchers.IO) {
            ret = try {
                connection = URL(url.resolveTimeTags()).openConnection() as HttpURLConnection
                // Add http headers
                splitHeader(header).forEach { (key, value) ->
                    connection!!.setRequestProperty(key, value)
                }

                connection!!.connect()

                val responseCode = connection!!.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val byteArray = connection!!.inputStream.use { it.readBytes() }
                    Pair(true, byteArray)
                } else {
                    Pair(false, "HTTP: $responseCode")
                }
            } catch (e: Exception) {
                Pair(false, "$e")
            } finally {
                connection?.disconnect()
            }
        }
        runBlocking {
            thread.join()
        }

        return ret
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_http_download)
    }

    override fun summary(ctx: Context): String {
        return "${ctx.getString(R.string.url)}: $url"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_http_download)
    }

    override fun inputParamType(): ParamType {
        return ParamType.None
    }

    override fun outputParamType(): ParamType {
        return ParamType.ByteArray
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_http)
    }

    @Composable
    override fun Options() {
        val C = LocalPalette.current

        StrInputBox(
            text = url,
            label = { Text(Str(R.string.url)) },
            onValueChange = { url = it }
        )

        StrInputBox(
            text = header,
            label = { Text(Str(R.string.http_header)) },
            onValueChange = { header = it },
            placeholder = {
                GreyLabel(
                    Str(R.string.help_http_header) + "\n\nUser-Agent: Chrome\nAuthorization: Basic ABC\n…",
                    color = C.textGrey.copy(alpha = 0.6f)
                )
            }
        )
    }
}

@Serializable
@SerialName("CleanupSpamDB")
class CleanupSpamDB(
    var expiry: Int = 1 // days
) : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val now = Now.currentMillis()
        val expireTimeMs = now - expiry.toLong() * 24 * 3600 * 1000

        logi("clean up spam db, deleting data before timestamp: $expireTimeMs")
        SpamTable.deleteBeforeTimestamp(ctx, expireTimeMs)

        // fire event to update the UI
        Events.spamDbUpdated.fire()

        return Pair(true, null)
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_cleanup_spam_db)
    }

    override fun summary(ctx: Context): String {
        val nDays = ctx.resources.getQuantityString(R.plurals.days, expiry, expiry)
        return ctx.getString(R.string.expiry) + ": $nDays"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_cleanup_spam_db)
    }

    override fun inputParamType(): ParamType {
        return ParamType.None
    }

    override fun outputParamType(): ParamType {
        return ParamType.None
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_db_delete)
    }

    @Composable
    override fun Options() {
        NumberInputBox(
            intValue = expiry,
            label = { Text(Str(R.string.expiry_days)) },
            onValueChange = { newVal, hasError ->
                if (!hasError) {
                    expiry = newVal!!
                }
            }
        )
    }
}

// Dump current settings to a ByteArray
@Serializable
@SerialName("BackupExport")
class BackupExport(
    var includeSpamDB: Boolean = false
) : IPermissiveAction {
    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        // Generate config data bytes
        val curr = Configs()
        curr.load(ctx, includeSpamDB)
        val compressed = compressString(curr.toJsonString())

        return Pair(true, compressed)
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_backup_export)
    }

    override fun summary(ctx: Context): String {
        val yes = ctx.getString(R.string.yes)
        val no = ctx.getString(R.string.no)
        return ctx.getString(R.string.include_spam_db) + ": ${if (includeSpamDB) yes else no}"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_backup_export)
    }

    override fun inputParamType(): ParamType {
        return ParamType.None
    }

    override fun outputParamType(): ParamType {
        return ParamType.ByteArray
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_backup_export)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var includeDB by remember { mutableStateOf(includeSpamDB) }

        LabeledRow(labelId = R.string.include_spam_db) {
            SwitchBox(includeDB) { isTurningOn ->
                includeDB = isTurningOn
                includeSpamDB = isTurningOn
            }
        }
    }
}

// Backup import from a ByteArray
@Serializable
@SerialName("BackupImport")
class BackupImport(
    var includeSpamDB: Boolean = false
) : IPermissiveAction {
    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val input = param.input

        if (input !is ByteArray) {
            return Pair(
                false, ctx.getString(R.string.invalid_input_type).format(
                    ParamType.ByteArray.name, input?.javaClass?.simpleName
                )
            )
        }

        try {
            val jsonStr = decompressToString(input)
            val newCfg = Configs.createFromJson(jsonStr)
            newCfg.apply(ctx, includeSpamDB)

            Events.configImported.fire()
            return Pair(true, null)
        } catch (e: Exception) {
            return Pair(false, "$e")
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_backup_import)
    }

    override fun summary(ctx: Context): String {
        val yes = ctx.getString(R.string.yes)
        val no = ctx.getString(R.string.no)
        return ctx.getString(R.string.include_spam_db) + ": ${if (includeSpamDB) yes else no}"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_backup_import)
    }

    override fun inputParamType(): ParamType {
        return ParamType.ByteArray
    }

    override fun outputParamType(): ParamType {
        return ParamType.None
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_backup_import)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var includeDB by remember { mutableStateOf(includeSpamDB) }

        LabeledRow(labelId = R.string.include_spam_db) {
            SwitchBox(includeDB) { isTurningOn ->
                includeDB = isTurningOn
                includeSpamDB = isTurningOn
            }
        }
    }
}

@Serializable
@SerialName("ReadFile")
class ReadFile(
    var dir: String = "{Downloads}",
    var filename: String = "",
) : IFileAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val path = dir.resolvePathTags()

        val fn = filename.resolveTimeTags()

        return try {
            val bytes = Util.readFile(path, fn)
            Pair(true, bytes)
        } catch (e: Exception) {
            Pair(false, "ReadFile: $e")
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_read_file)
    }

    override fun summary(ctx: Context): String {
        return "$dir/$filename"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_read_file)
    }

    override fun inputParamType(): ParamType {
        return ParamType.None
    }

    override fun outputParamType(): ParamType {
        return ParamType.ByteArray
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_file_read)
    }

    @Composable
    override fun Options() {
        Column {
            StrInputBox(
                text = dir,
                label = { Text(Str(R.string.directory)) },
                onValueChange = { dir = it }
            )
            StrInputBox(
                text = filename,
                label = { Text(Str(R.string.filename)) },
                onValueChange = { filename = it }
            )
        }
    }
}

@Serializable
@SerialName("WriteFile")
class WriteFile(
    var dir: String = "{Downloads}",
    var filename: String = "",
) : IFileAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val input = param.input
        if (input !is ByteArray) {
            return Pair(
                false, ctx.getString(R.string.invalid_input_type).format(
                    ParamType.ByteArray.name, input?.javaClass?.simpleName
                )
            )
        }
        val path = dir.resolvePathTags()
        val fn = filename.resolveTimeTags()

        return try {
            Util.writeFile(path, fn, input)
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, "WriteFile: $e")
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_write_file)
    }

    override fun summary(ctx: Context): String {
        return "$dir/$filename"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_write_file)
    }

    override fun inputParamType(): ParamType {
        return ParamType.ByteArray
    }

    override fun outputParamType(): ParamType {
        return ParamType.None
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_file_write)
    }

    @Composable
    override fun Options() {
        Column {
            StrInputBox(
                text = dir,
                label = { Text(Str(R.string.directory)) },
                onValueChange = { dir = it }
            )
            StrInputBox(
                text = filename,
                label = { Text(Str(R.string.filename)) },
                onValueChange = { filename = it }
            )
        }
    }
}

/*
input: ByteArray
output: List<RegexRule>
 */
@Serializable
@SerialName("ParseCSV")
class ParseCSV(
    // a json string contains column map
    var columnMapping: String = "{}"
) : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val input = param.input
        if (input !is ByteArray) {
            return Pair(
                false, ctx.getString(R.string.invalid_input_type).format(
                    ParamType.ByteArray.name, input?.javaClass?.simpleName
                )
            )
        }

        return try {
            val csv = Csv.parse(input, JSONObject(columnMapping).toStringMap())

            val rules = csv.rows.map { row ->
                RegexRule.fromMap(csv.headers.zip(row).toMap())
            }

            Pair(true, rules)
        } catch (e: Exception) {
            Pair(false, e.toString())
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_csv)
    }

    override fun summary(ctx: Context): String {
        return columnMapping
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_parse_csv)
    }

    override fun inputParamType(): ParamType {
        return ParamType.ByteArray
    }

    override fun outputParamType(): ParamType {
        return ParamType.RuleList
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_csv)
    }

    @Composable
    override fun Options() {
        StrInputBox(
            text = columnMapping,
            label = { Text(Str(R.string.column_mapping)) },
            onValueChange = { columnMapping = it }
        )
    }
}

/*
input: ByteArray
output: List<RegexRule>
 */
@Serializable
@SerialName("ParseXML")
class ParseXML(
    var xpath: String = ""
) : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val input = param.input
        if (input !is ByteArray) {
            return Pair(
                false, ctx.getString(R.string.invalid_input_type).format(
                    ParamType.ByteArray.name, input?.javaClass?.simpleName
                )
            )
        }

        return try {
            val rules = Xml.parse(bytes = input, xpath).map {
                RegexRule.fromMap(it)
            }

            Pair(true, rules)
        } catch (e: Exception) {
            Pair(false, e.toString())
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_xml)
    }

    override fun summary(ctx: Context): String {
        return "XPath: $xpath"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_parse_xml)
    }

    override fun inputParamType(): ParamType {
        return ParamType.ByteArray
    }

    override fun outputParamType(): ParamType {
        return ParamType.RuleList
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_xml)
    }

    @Composable
    override fun Options() {
        StrInputBox(
            text = xpath,
            label = { Text("XPath") },
            onValueChange = { xpath = it }
        )
    }
}

/*
input: ByteArray
output: List<RegexRule>
 */
@Serializable
@SerialName("RegexExtract")
class RegexExtract(
    var pattern: String = "",
    var regexFlags: Int = Def.DefaultRegexFlags,
) : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val input = param.input
        if (input !is ByteArray) {
            return Pair(
                false, ctx.getString(R.string.invalid_input_type).format(
                    ParamType.ByteArray.name, input?.javaClass?.simpleName
                )
            )
        }

        return try {
            val opts = Util.flagsToRegexOptions(regexFlags)

            val haystack = input.toString(Charsets.UTF_8)
            val all = pattern.toRegex(opts).findAll(haystack)

            val rules = all.map {
                RegexRule(pattern = it.groupValues[1])
            }.toList()

            Pair(true, rules)
        } catch (e: Exception) {
            Pair(false, e.toString())
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_regex_extract)
    }

    override fun summary(ctx: Context): String {
        val label = ctx.getString(R.string.regex_pattern)
        return "$label: $pattern"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_regex_extract)
    }

    override fun inputParamType(): ParamType {
        return ParamType.ByteArray
    }

    override fun outputParamType(): ParamType {
        return ParamType.RuleList
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_regex_capture)
    }

    @Composable
    override fun Options() {
        val flags = remember { mutableIntStateOf(regexFlags) }
        RegexInputBox(
            regexStr = pattern,
            label = { Text(Str(R.string.regex_pattern)) },
            regexFlags = flags,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    pattern = newVal
                }
            },
            onFlagsChange = {
                flags.intValue = it
                regexFlags = it
            }
        )
    }
}

/*
input: List<RegexRule>
output: null
 */
@Serializable
@SerialName("ImportToSpamDB")
class ImportToSpamDB : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val input = param.input
        // It must be written like this, cannot be inlined in the `if()`, seems to be kotlin bug
        val inputValid = (input is List<*>) && ((input as List<*>).all { it is RegexRule })
        if (!inputValid) {
            return Pair(
                false, ctx.getString(R.string.invalid_input_type).format(
                    "List<Rule>", input?.javaClass?.simpleName
                )
            )
        }

        return try {
            val now = System.currentTimeMillis()

            val numbers = (input as List<*>).map {
                SpamNumber(peer = (it as RegexRule).pattern, time = now)
            }
            val errorStr = SpamTable.addAll(ctx, numbers)

            // Fire a global event to update UI
            Events.spamDbUpdated.fire()

            Pair(errorStr == null, errorStr)
        } catch (e: Exception) {
            Pair(false, e.toString())
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_import_to_spam_db)
    }

    override fun summary(ctx: Context): String {
        return ""
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_import_to_spam_db)
    }

    override fun inputParamType(): ParamType {
        return ParamType.RuleList
    }

    override fun outputParamType(): ParamType {
        return ParamType.None
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_db_add)
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
) : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val input = param.input
        if (!(input is List<*> && input.all { it is RegexRule })) {
            return Pair(
                false, ctx.getString(R.string.invalid_input_type).format(
                    ParamType.RuleList.name, input?.javaClass?.simpleName
                )
            )
        }

        try {
            val numberList = (input as List<*>).map { (it as RegexRule).pattern }.distinct()

            // Do nothing when there isn't any number to add, to prevent adding a `()`
            if (numberList.isEmpty()) {
                return Pair(true, null)
            }

            when (importType) {
                ImportType.Create -> create(ctx, numberList)
                ImportType.Replace -> replace(ctx, numberList)
                else -> merge(ctx, numberList)
            }

            // fire event to update the UI
            Events.regexRuleUpdated.fire()

            return Pair(true, null)
        } catch (e: Exception) {
            return Pair(false, e.toString())
        }
    }

    private fun create(ctx: Context, numbers: List<String>) {
        // Join numbers to `11|22|33...`
        val combinedPattern = numbers.joinToString("|")

        val newRule = RegexRule(
            pattern = "($combinedPattern)",
            description = description,
            priority = priority,
            isBlacklist = !isWhitelist,
        )
        NumberRuleTable().addNewRule(ctx, newRule)
    }

    private fun replace(ctx: Context, numbers: List<String>) {
        val table = NumberRuleTable()
        val oldRules = table.findRuleByDesc(ctx, description)
        if (oldRules.isEmpty()) {
            create(ctx, numbers)
        } else {
            // 1. delete the previous rule
            table.deleteById(ctx, oldRules[0].id)
            // 2. create a new one
            create(ctx, numbers)
        }
    }

    private fun merge(ctx: Context, numbers: List<String>) {
        val table = NumberRuleTable()
        val oldRules = table.findRuleByDesc(ctx, description)
        if (oldRules.isEmpty()) {
            create(ctx, numbers)
        } else {
            val previous = oldRules[0]
            val oldNumbers = previous.pattern.trim('(', ')').split('|')

            val all = (oldNumbers + numbers).distinct()
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

    override fun summary(ctx: Context): String {
        return description
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_import_as_regex_rule)
    }

    override fun inputParamType(): ParamType {
        return ParamType.RuleList
    }

    override fun outputParamType(): ParamType {
        return ParamType.None
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_regex)
    }

    @Composable
    override fun Options() {
        val ctx = LocalContext.current
        val C = LocalPalette.current
        Column {
            StrInputBox(
                text = description,
                label = { Text(Str(R.string.description)) },
                onValueChange = { description = it }
            )
            NumberInputBox(
                intValue = priority,
                label = { Text(Str(R.string.priority)) },
                onValueChange = { newVal, hasError ->
                    if (!hasError) {
                        priority = newVal!!
                    }
                }
            )
            // Type Whitelist/ Blacklist
            LabeledRow(R.string.type) {
                var applyToWorB by rememberSaveable { mutableIntStateOf(if (isWhitelist) 0 else 1) }
                val items = listOf(
                    RadioItem(Str(R.string.whitelist), C.pass),
                    RadioItem(Str(R.string.blacklist), C.block),
                )
                RadioGroup(items = items, selectedIndex = applyToWorB) {
                    applyToWorB = it
                    isWhitelist = it == 0
                }
            }
            // Type Create/Replace/Merge
            LabeledRow(R.string.mode, helpTooltipId = R.string.help_regex_action_add_mode) {
                var selected by rememberSaveable {
                    mutableIntStateOf(ImportType.entries.indexOf(importType))
                }

                val items = ctx.resources.getStringArray(R.array.regex_action_add_mode)
                Spinner(
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

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val input = param.input
        if (!(input is List<*> && input.all { it is RegexRule })) {
            return Pair(
                false, ctx.getString(R.string.invalid_input_type).format(
                    ParamType.RuleList.name, input?.javaClass?.simpleName
                )
            )
        }

        val clearedRuleList = (input as List<*>).map {
            val r = it as RegexRule

            val newNum = from.toRegex().replace(r.pattern, to)

            r.copy(
                pattern = newNum
            )
        }
        return Pair(true, clearedRuleList)
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_convert_number)
    }

    override fun summary(ctx: Context): String {
        return "$from -> $to"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_convert_number)
    }

    override fun inputParamType(): ParamType {
        return ParamType.RuleList
    }

    override fun outputParamType(): ParamType {
        return ParamType.RuleList
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_replace)
    }

    @Composable
    override fun Options() {

        val flagsState = remember { mutableIntStateOf(flags) }
        RegexInputBox(
            label = { Text(Str(R.string.replace_from)) },
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
            }
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
output: List<RegexRule>
 */
@Serializable
@SerialName("FindRules")
class FindRules(
    var pattern: String = "",
    var flags: Int = Def.DefaultRegexFlags,
) : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val opts = Util.flagsToRegexOptions(flags)
        val patternRegex = pattern.toRegex(opts)

        val found = NumberRuleTable().listAll(ctx).filter {
            patternRegex.matches(it.description)
        }
        return Pair(true, found)
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_find_rules)
    }

    override fun summary(ctx: Context): String {
        return "${ctx.getString(R.string.description)}: $pattern"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_find_rules)
    }

    override fun inputParamType(): ParamType {
        return ParamType.None
    }

    override fun outputParamType(): ParamType {
        return ParamType.RuleList
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_find)
    }

    @Composable
    override fun Options() {
        val flagsState = remember { mutableIntStateOf(flags) }
        RegexInputBox(
            label = { Text(Str(R.string.description)) },
            placeholder = { Text(Str(R.string.regex_pattern)) },
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
input: List<RegexRule>
output: none
 */
@Serializable
@SerialName("ModifyRules")
class ModifyRules(
    var config: String = "",
) : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val input = param.input
        if (!(input is List<*> && input.all { it is RegexRule })) {
            return Pair(
                false, ctx.getString(R.string.invalid_input_type).format(
                    ParamType.RuleList.name, input?.javaClass?.simpleName
                )
            )
        }

        try {
            (input as List<*>).forEach {
                val origin = it as RegexRule
                val strOrigin = PermissiveJson.encodeToString(origin)
                val mapOrigin = JSONObject(strOrigin).toMap()

                val mapConfig = JSONObject(config).toMap()

                val mapModified = mapOrigin + mapConfig // override with mapModify

                val newRule =
                    PermissiveJson.decodeFromString<RegexRule>(JSONObject(mapModified).toString())
                NumberRuleTable().updateRuleById(ctx, origin.id, newRule)
            }
        } catch (e: Exception) {
            return Pair(false, "$e")
        }

        // fire event to update the UI
        Events.regexRuleUpdated.fire()

        return Pair(true, null)
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_modify_rules)
    }

    override fun summary(ctx: Context): String {
        return config
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_modify_rules)
    }

    override fun inputParamType(): ParamType {
        return ParamType.RuleList
    }

    override fun outputParamType(): ParamType {
        return ParamType.None
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_replace)
    }

    @Composable
    override fun Options() {
        StrInputBox(
            label = { Text(Str(R.string.config_text)) },
            placeholder = { Text(Str(R.string.action_modify_rules_placeholder)) },
            text = config,
            onValueChange = {
                config = it
            }
        )
    }
}

/*
input: None
output: None
 */
@Serializable
@SerialName("EnableWorkflow")
class EnableWorkflow(
    var enable: Boolean = false,
) : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        val workTag = param.workTag

        val bot = BotTable
            .findByWorkUuid(ctx, workTag)
            ?.copy(enabled = enable)

        if (bot != null) {
            // 1. enable/disable bot
            BotTable.updateById(ctx, bot.id, bot)

            // 2. reSchedule
            reScheduleBot(ctx, bot)

            // 3. fire event
            Events.botUpdated.fire()
        }

        return Pair(true, null)
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_enable_workflow)
    }

    override fun summary(ctx: Context): String {
        return "${ctx.getString(R.string.enable)}: ${ctx.getString(if(enable) R.string.yes else R.string.no)}"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_enable_workflow)
    }

    override fun inputParamType(): ParamType {
        return ParamType.None
    }

    override fun outputParamType(): ParamType {
        return ParamType.None
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_workflow)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabled by remember { mutableStateOf(enable) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabled) { isTurningOn ->
                enabled = isTurningOn
                enable = isTurningOn
            }
        }
    }
}

/*
input: None
output: None
 */
@Serializable
@SerialName("EnableApp")
class EnableApp(
    var enable: Boolean = true,
) : IPermissiveAction {

    override fun execute(ctx: Context, param: ActionParam): Pair<Boolean, Any?> {
        Global(ctx).setGloballyEnabled(enable)
        G.globallyEnabled.value = enable

        return Pair(true, null)
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_enable_app)
    }

    override fun summary(ctx: Context): String {
        return "${ctx.getString(R.string.enable)}: ${ctx.getString(if(enable) R.string.yes else R.string.no)}"
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_enable_app)
    }

    override fun inputParamType(): ParamType {
        return ParamType.None
    }

    override fun outputParamType(): ParamType {
        return ParamType.None
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_toggle)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabled by remember { mutableStateOf(enable) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabled) { isTurningOn ->
                enabled = isTurningOn
                enable = isTurningOn
            }
        }
    }
}
