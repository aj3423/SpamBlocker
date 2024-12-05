package spam.blocker.service.bot

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
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
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.DimGreyLabel
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Spinner
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.SummaryLabel
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Algorithm.compressString
import spam.blocker.util.Algorithm.decompressToString
import spam.blocker.util.CSVParser
import spam.blocker.util.CountryCode
import spam.blocker.util.IPermission
import spam.blocker.util.NormalPermission
import spam.blocker.util.Now
import spam.blocker.util.PermissiveJson
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.Util
import spam.blocker.util.Xml
import spam.blocker.util.asyncHttpRequest
import spam.blocker.util.resolveNumberTag
import spam.blocker.util.resolvePathTags
import spam.blocker.util.resolveTimeTags
import spam.blocker.util.toMap
import spam.blocker.util.toStringMap
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.net.HttpURLConnection
import kotlin.collections.drop

@Composable
fun NoOptionNeeded() {
    GreyLabel(text = Str(R.string.no_config_needed))
}

@Serializable
@SerialName("CleanupHistory")
class CleanupHistory(
    var expiry: Int = 90 // days
) : IPermissiveAction {
    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val now = Now.currentMillis()
        val expireTimeMs = now - expiry.toLong() * 24 * 3600 * 1000

        aCtx.logger?.debug(
            ctx.getString(R.string.cleaning_up_history_db)
                .format("$expireTimeMs")
        )

        CallTable().clearRecordsBeforeTimestamp(ctx, expireTimeMs)
        SmsTable().clearRecordsBeforeTimestamp(ctx, expireTimeMs)

        Events.historyUpdated.fire()

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_cleanup_history)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current
        val nDays = ctx.resources.getQuantityString(R.plurals.days, expiry, expiry)
        SummaryLabel(ctx.getString(R.string.expiry) + ": $nDays")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_cleanup_history)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
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
open class HttpDownload(
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
    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val startTime = System.currentTimeMillis()

        val success = try {
            // 1. Url
            val resolvedUrl = url
                .resolveTimeTags()
                .resolveNumberTag(
                    cc = aCtx.cc,
                    domestic = aCtx.domestic,
                    fullNumber = aCtx.fullNumber,
                    rawNumber = aCtx.rawNumber,
                )
            aCtx.logger?.debug(ctx.getString(R.string.resolved_url).format(resolvedUrl))

            // 2. Headers map
            val headersMap = splitHeader(header)
            headersMap.forEach { (key, value) ->
                aCtx.logger?.debug("${ctx.getString(R.string.http_header)}: $key -> $value")
            }

            // 3. Send request
            val resultChannel = asyncHttpRequest(
                scope = aCtx.scope,
                urlString = resolvedUrl,
                headersMap = headersMap,
            )

            // 4. Get response
            val result = aCtx.scope.async {
                resultChannel.receiveCatching()
            }.await().getOrNull()

            aCtx.logger?.info(
                ctx.getString(R.string.time_cost)
                    .format("${System.currentTimeMillis() - startTime}")
            )

            aCtx.lastOutput = result?.bytes

            if (result?.statusCode == HttpURLConnection.HTTP_OK) {
                true
            } else {
                val echo = if (result?.bytes != null) String(result.bytes) else ""
                aCtx.logger?.error("HTTP: <${result?.statusCode}>: $echo")
                false
            }

        } catch (_: CancellationException) { // a winner is found, others are cancelled
            aCtx.logger?.debug(ctx.getString(R.string.another_thread_is_cancelled))
            false
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }

        return success
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_http_request)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current
        SummaryLabel("${ctx.getString(R.string.url)}: $url")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_http_download)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_http)
    }

    @Composable
    override fun Options() {
        StrInputBox(
            text = url,
            label = { Text(Str(R.string.url)) },
            leadingIconId = R.drawable.ic_link,
            placeholder = { DimGreyLabel("https://...") },
            helpTooltipId = R.string.help_http_url,
            onValueChange = { url = it }
        )

        StrInputBox(
            text = header,
            label = { Text(Str(R.string.http_header)) },
            leadingIconId = R.drawable.ic_http_header,
            onValueChange = { header = it },
            helpTooltipId = R.string.help_http_header,
            placeholder = { DimGreyLabel("apikey: ABC\nAuth: key\n…") }
        )
    }
}


@Serializable
@SerialName("CleanupSpamDB")
class CleanupSpamDB(
    var expiry: Int = 1 // days
) : IPermissiveAction {

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val now = Now.currentMillis()
        val expireTimeMs = now - expiry.toLong() * 24 * 3600 * 1000

        val deletedCount = SpamTable.deleteBeforeTimestamp(ctx, expireTimeMs)

        aCtx.logger?.debug(
            ctx.getString(R.string.cleaning_up_spam_db)
                .format("$deletedCount", "$expireTimeMs")
        )

        // fire event to update the UI
        Events.spamDbUpdated.fire()

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_cleanup_spam_db)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        val nDays = ctx.resources.getQuantityString(R.plurals.days, expiry, expiry)
        SummaryLabel(ctx.getString(R.string.expiry) + ": $nDays")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_cleanup_spam_db)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
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
    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        // Generate config data bytes
        val curr = Configs()
        curr.load(ctx, includeSpamDB)
        val compressed = compressString(curr.toJsonString())

        aCtx.logger?.debug(ctx.getString(R.string.action_backup_export))

        aCtx.lastOutput = compressed
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_backup_export)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        val yes = ctx.getString(R.string.yes)
        val no = ctx.getString(R.string.no)
        SummaryLabel(ctx.getString(R.string.include_spam_db) + ": ${if (includeSpamDB) yes else no}")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_backup_export)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
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
            SwitchBox(includeDB) { on ->
                includeDB = on
                includeSpamDB = on
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
    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        try {
            val jsonStr = decompressToString(input)
            val newCfg = Configs.createFromJson(jsonStr)
            newCfg.apply(ctx, includeSpamDB)

            aCtx.logger?.debug(ctx.getString(R.string.action_backup_import))

            Events.configImported.fire()
            return true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            return false
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_backup_import)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        val yes = ctx.getString(R.string.yes)
        val no = ctx.getString(R.string.no)
        SummaryLabel(ctx.getString(R.string.include_spam_db) + ": ${if (includeSpamDB) yes else no}")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_backup_import)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
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
            SwitchBox(includeDB) { on ->
                includeDB = on
                includeSpamDB = on
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

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val path = dir.resolvePathTags()

        val fn = filename.resolveTimeTags()

        return try {
            aCtx.logger?.debug(label(ctx) + ": $path/$fn")

            val bytes = Util.readFile(path, fn)
            aCtx.lastOutput = bytes
            true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_read_file)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        SummaryLabel("$dir/$filename")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_read_file)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
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

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        val path = dir.resolvePathTags()
        val fn = filename.resolveTimeTags()

        aCtx.logger?.debug(label(ctx) + ": $path/$fn")

        return try {
            Util.writeFile(path, fn, input)
            true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_write_file)
    }

    @Composable
    override fun Summary() {
        SummaryLabel("$dir/$filename")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_write_file)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
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

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        return try {
            val csv = CSVParser(
                PushbackReader(BufferedReader(InputStreamReader(ByteArrayInputStream(input)))),
                JSONObject(columnMapping).toStringMap(),
            ).parse()

            val rules = csv.rows.map { row ->
                RegexRule.fromMap(csv.headers.zip(row).toMap())
            }
            aCtx.logger?.debug(ctx.getString(R.string.parsed_n_rules).format("${rules.size}"))

            aCtx.lastOutput = rules
            true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_csv)
    }

    @Composable
    override fun Summary() {
        SummaryLabel(columnMapping)
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_parse_csv)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
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

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        return try {
            val rules = Xml.parse(bytes = input, xpath).map {
                RegexRule.fromMap(it)
            }

            aCtx.logger?.debug(ctx.getString(R.string.parsed_n_rules).format("${rules.size}"))

            aCtx.lastOutput = rules
            true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_xml)
    }

    @Composable
    override fun Summary() {
        SummaryLabel("XPath: $xpath")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_parse_xml)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
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

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        return try {
            val opts = Util.flagsToRegexOptions(regexFlags)

            val haystack = input.toString(Charsets.UTF_8)
            val all = pattern.toRegex(opts).findAll(haystack)

            val rules = all.map {
                RegexRule(pattern = it.groupValues[1])
            }.toList()

            aCtx.logger?.debug(ctx.getString(R.string.parsed_n_rules).format("${rules.size}"))

            aCtx.lastOutput = rules
            true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_regex_extract)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        val label = ctx.getString(R.string.regex_pattern)
        SummaryLabel("$label: $pattern")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_regex_extract)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
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
input: List<RegexRule> or QueryResult
output: null
 */
@Serializable
@SerialName("ImportToSpamDB")
class ImportToSpamDB : IPermissiveAction {

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rules = aCtx.lastOutput as List<*> // it's actually `List<RegexRule>`

        return try {
            val now = System.currentTimeMillis()

            val numbers = rules.map {
                SpamNumber(peer = (it as RegexRule).pattern, time = now)
            }

            if (numbers.isNotEmpty()) {
                aCtx.logger?.debug(
                    ctx.getString(R.string.add_n_numbers_to_spam_db)
                        .format("${numbers.size}")
                )
            }

            val errorStr = SpamTable.addAll(ctx, numbers)

            // Fire a global event to update UI
            Events.spamDbUpdated.fire()

            if (errorStr == null) {
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
    override fun Summary() {
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

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rules = aCtx.lastOutput as List<*> // it's actually `List<RegexRule>`

        return try {
            val numberList = rules.map { (it as RegexRule).pattern }.distinct()

            // Do nothing when there isn't any number to add, to prevent adding a `()`
            if (numberList.isEmpty()) {
                aCtx.logger?.warn(ctx.getString(R.string.nothing_to_import))
                true
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
        NumberRuleTable().addNewRule(ctx, newRule)
    }

    private fun replace(ctx: Context, aCtx: ActionContext, numbers: List<String>) {
        aCtx.logger?.debug(
            ctx.getString(R.string.replace_rule_with_desc)
                .format(description)
        )

        val table = NumberRuleTable()
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

        val table = NumberRuleTable()
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
    override fun Summary() {
        SummaryLabel(description)
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

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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
        return ctx.getString(R.string.action_convert_number)
    }

    @Composable
    override fun Summary() {
        SummaryLabel("$from -> $to")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_convert_number)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
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
            placeholder = {
                DimGreyLabel(
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
    var pattern: String = "", // description pattern
    var flags: Int = Def.DefaultRegexFlags,
) : IPermissiveAction {

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val opts = Util.flagsToRegexOptions(flags)
        val patternRegex = pattern.toRegex(opts)

        val found = NumberRuleTable().listAll(ctx).filter {
            patternRegex.matches(it.description)
        }

        aCtx.logger?.debug(
            ctx.getString(R.string.find_rule_with_desc)
                .format("${found.size}", pattern)
        )

        aCtx.lastOutput = found
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_find_rules)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        SummaryLabel("${ctx.getString(R.string.description)}: $pattern")
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
        GreyIcon(iconId = R.drawable.ic_find)
    }

    @Composable
    override fun Options() {
        val flagsState = remember { mutableIntStateOf(flags) }
        RegexInputBox(
            label = { Text(Str(R.string.description)) },
            placeholder = {
                DimGreyLabel(
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
input: List<RegexRule>
output: none
 */
@Serializable
@SerialName("ModifyRules")
class ModifyRules(
    var config: String = "",
) : IPermissiveAction {

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rules = aCtx.lastOutput as List<*>

        aCtx.logger?.debug(
            ctx.getString(R.string.modify_n_rules)
                .format("${rules.size}")
        )

        try {
            rules.forEach {
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
    override fun Summary() {
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
        GreyIcon(iconId = R.drawable.ic_replace)
    }

    @Composable
    override fun Options() {
        StrInputBox(
            label = { Text(Str(R.string.config_text)) },
            placeholder = { DimGreyLabel(Str(R.string.action_modify_rules_placeholder)) },
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

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val workTag = aCtx.workTag

        aCtx.logger?.debug("${label(ctx)}: $workTag")

        // null when Testing, do nothing
        if (workTag == null) {
            aCtx.logger?.debug(ctx.getString(R.string.skip_for_testing))
            return true
        }

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

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_enable_workflow)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        SummaryLabel("${ctx.getString(R.string.enable)}: ${ctx.getString(if (enable) R.string.yes else R.string.no)}")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_enable_workflow)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
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
            SwitchBox(enabled) { on ->
                enabled = on
                enable = on
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

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        Global(ctx).setGloballyEnabled(enable)
        G.globallyEnabled.value = enable

        aCtx.logger?.debug("${ctx.getString(R.string.action_enable_app)}: $enable")

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_enable_app)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        SummaryLabel("${ctx.getString(R.string.enable)}: ${ctx.getString(if (enable) R.string.yes else R.string.no)}")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_enable_app)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
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
            SwitchBox(enabled) { on ->
                enabled = on
                enable = on
            }
        }
    }
}


// This action parses the incoming number and fill the ActionContext with cc/domestic/number,
//  which will be used in following actions like HttpRequest.
@Serializable
@SerialName("ParseIncomingNumber")
class ParseIncomingNumber(
    var numberFilter: String = "",
    var autoCC: Boolean = false, // for people who often travel around between different countries
    var fixedCC: String = "", // for people who don't go abroad
) : IAction {

    override fun missingPermissions(ctx: Context): List<IPermission> {
        return if (autoCC) {
            listOf(
                NormalPermission(Manifest.permission.READ_PHONE_STATE,
                    prompt = ctx.getString(R.string.auto_detect_cc_permission)),
            )
        } else {
            listOf()
        }
    }

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        // The `rawNumber` is set by the workflow caller before it's executed.
        val rawNumber = aCtx.rawNumber!!
        aCtx.logger?.debug("${label(ctx)}: $rawNumber")

        val matchesFilter = numberFilter.toRegex().matches(rawNumber)
        if (!matchesFilter) {
            aCtx.logger?.debug(
                ctx.getString(R.string.number_not_match_filter)
                    .format(rawNumber, numberFilter)
            )
            return false
        }

        val clearedNumber = Util.clearNumber(rawNumber)

        if (rawNumber.startsWith("+")) {
            aCtx.fullNumber = clearedNumber
            val (ok, cc, domestic) = CountryCode.parseCcDomestic(clearedNumber)
            if (ok) {
                aCtx.cc = cc
                aCtx.domestic = domestic
            }
            return true
        } else { // not start with "+"
            if (autoCC) {
                val cc = CountryCode.current(ctx)
                if (cc == null) {
                    aCtx.logger?.error(ctx.getString(R.string.fail_detect_cc))
                    return false
                }
                aCtx.cc = cc.toString()
                aCtx.domestic = clearedNumber
            } else {
                aCtx.cc = fixedCC
                aCtx.domestic = Util.clearNumber(rawNumber)
            }
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_incoming_number)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        val auto = ctx.getString(R.string.automatic)

        RowVCenterSpaced(4) {
            GreyIcon20(R.drawable.ic_filter)
            SummaryLabel(numberFilter)


            GreyIcon20(R.drawable.ic_airplane)
            SummaryLabel(if (autoCC) auto else fixedCC)
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_parse_incoming_number)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon20(iconId = R.drawable.ic_call)
    }

    @Composable
    override fun Options() {
        val dummyFlags = remember { mutableIntStateOf(Def.FLAG_REGEX_RAW_NUMBER) }
        RegexInputBox(
            regexStr = numberFilter,
            label = { Text(Str(R.string.number_filter)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_filter) },
            helpTooltipId = R.string.help_number_filter,
            placeholder = { DimGreyLabel(".*") },
            regexFlags = dummyFlags,
            showFlagsIcon = false,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    numberFilter = newVal
                }
            },
            onFlagsChange = { }
        )

        Section(
            title = Str(R.string.country_code),
            bgColor = LocalPalette.current.dialogBg
        ) {
            Column(modifier = M.fillMaxWidth()) {
                var autoCCState by remember { mutableStateOf(autoCC) }
                LabeledRow(
                    R.string.auto_detect,
                    helpTooltipId = R.string.help_auto_detect_country_code,
                ) {
                    SwitchBox(autoCCState) { on ->
                        autoCCState = on
                        autoCC = on
                    }
                }

                AnimatedVisibleV(visible = !autoCCState) {
                    StrInputBox(
                        text = fixedCC,
                        label = { Text(Str(R.string.country_code)) },
                        leadingIconId = R.drawable.ic_airplane,
                        placeholder = { DimGreyLabel(Str(R.string.for_example) + " 1") },
                        onValueChange = { fixedCC = it }
                    )
                }
            }
        }
    }
}

data class QueryResult(
    val determined: Boolean = false,
    // These values are only useful when determined == true
    val isSpam: Boolean = false,
    val category: String? = null,
    val serverEcho: String? = null,
)

@Serializable
@SerialName("ParseQueryResult")
class ParseQueryResult(
    var negativeSig: String = "", // positive signature
    var negativeFlags: Int = Def.DefaultRegexFlags,

    var positiveSig: String = "",
    var positiveFlags: Int = Def.DefaultRegexFlags,

    var categorySig: String = "",
    var categoryFlags: Int = Def.DefaultRegexFlags,
) : IPermissiveAction {

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        val html = String(input)

        aCtx.logger?.debug("${label(ctx)}: ${Util.truncate(html)}")

        // 1. negative
        val negativeOpts = Util.flagsToRegexOptions(negativeFlags)
        val isNegative = if (negativeSig.isEmpty())
            null
        else
            negativeSig.toRegex(negativeOpts).containsMatchIn(html)

        // 2. positive
        val positiveOpts = Util.flagsToRegexOptions(positiveFlags)
        val isPositive = if (positiveSig.isEmpty())
            null
        else
            positiveSig.toRegex(positiveOpts).containsMatchIn(html)

        var category: String? = null

        val determined = isNegative == true || isPositive == true

        // 3. category
        if (determined) {
            if (categorySig.trim().isNotEmpty()) {
                val categoryOpts = Util.flagsToRegexOptions(categoryFlags)
                category = categorySig.trim().toRegex(categoryOpts).find(html)
                    ?.groups
                    ?.drop(1)
                    ?.filterNotNull()
                    ?.first()?.value
            }
        }

        // show log
        if (isNegative == true) {
            aCtx.logger?.error(
                ctx.getString(R.string.identified_as_spam)
                    .format(category ?: "")
            )
        } else if (isPositive == true) {
            aCtx.logger?.success(
                ctx.getString(R.string.identified_as_valid)
                    .format(category ?: "")
            )
        } else {
            aCtx.logger?.info(ctx.getString(R.string.unidentified_number))
        }

        val result = QueryResult(
            determined = determined,
            isSpam = isNegative == true,
            category = category,
            serverEcho = html,
        )
        aCtx.lastOutput = result
        aCtx.racingResult = result
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_query_result)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(4) {
            RowVCenter {
                GreyIcon16(R.drawable.ic_no)
                SummaryLabel(negativeSig)
            }

            if (positiveSig.isNotEmpty()) {
                RowVCenter {
                    GreyIcon16(R.drawable.ic_yes)
                    SummaryLabel(positiveSig)
                }
            }

            if (categorySig.isNotEmpty()) {
                RowVCenter {
                    GreyIcon16(R.drawable.ic_category)
                    SummaryLabel(categorySig)
                }
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_parse_query_result)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None, ParamType.InstantQueryResult)
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_find_check)
    }

    @Composable
    override fun Options() {
        val negativeFlagsCopy = remember { mutableIntStateOf(negativeFlags) }
        RegexInputBox(
            regexStr = negativeSig,
            label = { Text(Str(R.string.negative_identifier)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_no) },
            helpTooltipId = R.string.help_negative_identifier,
            placeholder = { DimGreyLabel(Str(R.string.hint_negative_identifier)) },
            regexFlags = negativeFlagsCopy,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    negativeSig = newVal
                }
            },
            onFlagsChange = {
                negativeFlagsCopy.intValue = it
                negativeFlags = it
            }
        )
        val positiveFlagsCopy = remember { mutableIntStateOf(positiveFlags) }
        RegexInputBox(
            regexStr = positiveSig,
            label = { Text(Str(R.string.positive_identifier)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_yes) },
            helpTooltipId = R.string.help_positive_identifier,
            placeholder = { DimGreyLabel(Str(R.string.hint_positive_identifier)) },
            regexFlags = positiveFlagsCopy,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    positiveSig = newVal
                }
            },
            onFlagsChange = {
                positiveFlagsCopy.intValue = it
                positiveFlags = it
            }
        )
        val reasonFlagsCopy = remember { mutableIntStateOf(categoryFlags) }
        RegexInputBox(
            regexStr = categorySig,
            label = { Text(Str(R.string.category_identifier)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_category) },
            helpTooltipId = R.string.help_category_identifier,
            placeholder = { DimGreyLabel(Str(R.string.hint_category_identifier)) },
            regexFlags = reasonFlagsCopy,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    categorySig = newVal
                }
            },
            onFlagsChange = {
                reasonFlagsCopy.intValue = it
                categoryFlags = it
            }
        )
    }
}

@Serializable
@SerialName("FilterQueryResult")
class FilterQueryResult(
) : IPermissiveAction {

    override suspend fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as QueryResult

        aCtx.lastOutput = if (input.determined && input.isSpam) {
            listOf(
                RegexRule(
                    pattern = aCtx.rawNumber!!,
                    isBlacklist = true,
                )
            )
        } else
            listOf()

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_filter_query_result)
    }

    @Composable
    override fun Summary() {
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_filter_query_result)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.InstantQueryResult)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.RuleList)
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_filter)
    }

    @Composable
    override fun Options() {
        NoOptionNeeded()
    }
}
