package spam.blocker.service.bot

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.ImportDbReason
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RuleTable
import spam.blocker.db.SmsTable
import spam.blocker.db.SpamNumber
import spam.blocker.db.SpamTable
import spam.blocker.db.listReportableAPIs
import spam.blocker.db.reScheduleBot
import spam.blocker.db.ruleTableForType
import spam.blocker.def.Def
import spam.blocker.service.checker.ByRegexRule
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.Checker.RegexRuleChecker
import spam.blocker.service.checker.ICheckResult
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.api.tagCategory
import spam.blocker.ui.theme.DimGrey
import spam.blocker.ui.theme.DodgeBlue
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Pink80
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.DimGreyLabel
import spam.blocker.ui.widgets.GreenDot
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.RingtonePicker
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.SummaryLabel
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.A
import spam.blocker.util.AdbLogger
import spam.blocker.util.Algorithm.compressString
import spam.blocker.util.Algorithm.decompressToString
import spam.blocker.util.CSVParser
import spam.blocker.util.Contacts
import spam.blocker.util.CountryCode
import spam.blocker.util.Now
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.PermissiveJson
import spam.blocker.util.PermissivePrettyJson
import spam.blocker.util.RingtoneUtil
import spam.blocker.util.Util
import spam.blocker.util.Util.domainFromUrl
import spam.blocker.util.Util.isAlphaNumber
import spam.blocker.util.Xml
import spam.blocker.util.formatAnnotated
import spam.blocker.util.hasFlag
import spam.blocker.util.httpRequest
import spam.blocker.util.logi
import spam.blocker.util.regexMatches
import spam.blocker.util.regexMatchesNumber
import spam.blocker.util.regexReplace
import spam.blocker.util.resolveBase64Tag
import spam.blocker.util.resolveCustomTag
import spam.blocker.util.resolveHttpAuthTag
import spam.blocker.util.resolveNumberTag
import spam.blocker.util.resolvePathTags
import spam.blocker.util.resolveSHA1Tag
import spam.blocker.util.resolveSmsTag
import spam.blocker.util.resolveTimeTags
import spam.blocker.util.spf
import spam.blocker.util.toMap
import spam.blocker.util.toStringMap
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.net.HttpURLConnection

@Composable
fun NoOptionNeeded() {
    GreyLabel(text = Str(R.string.no_config_needed))
}

@Serializable
@SerialName("CleanupHistory")
class CleanupHistory(
    var expiry: Int = 90 // days
) : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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

const val HTTP_GET = 0
const val HTTP_POST = 1

@Serializable
@SerialName("HttpDownload")
open class HttpDownload(
    var method: Int = HTTP_GET,
    var url: String = "",
    var header: String = "",
    var body: String = "", // post body

    // retry
    var enableRetry: Boolean = false,
    var retryTimes: Int = 0,
    var retryDelayMs: Int = 0,
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
    private fun splitHeader(
        allHeadersStr: String,
        customTags: Map<String, String>
    ): Map<String, String> {
        return allHeadersStr.lines().filter { it.trim().isNotEmpty() }.associate { line ->
            val resolved = line
                .resolveHttpAuthTag()
                .resolveBase64Tag()
                .resolveCustomTag(customTags)
            val (key, value) = resolved.split(":").map { it.trim() }
            key to value
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {

        var retryAttempts = 0

        do {

            try {
                val startTime = System.currentTimeMillis()

                // 1. Url
                val resolvedUrl = url
                    .resolveTimeTags()
                    .resolveNumberTag(
                        cc = aCtx.cc,
                        domestic = aCtx.domestic,
                        fullNumber = aCtx.fullNumber,
                        rawNumber = aCtx.rawNumber,
                    )
                    .replace(tagCategory, aCtx.realCategory ?: "")
                    .resolveSHA1Tag()
                    .resolveCustomTag(aCtx.customTags)
                aCtx.logger?.debug(ctx.getString(R.string.resolved_url).formatAnnotated(resolvedUrl.A(DimGrey)))

                // 2. Headers map
                val headersMap = splitHeader(header, aCtx.customTags)
                headersMap.forEach { (key, value) ->
                    aCtx.logger?.debug("${ctx.getString(R.string.http_header)}: %s -> %s".formatAnnotated(
                        key.A(DimGrey), value.A(Teal200)
                    ))
                }

                // 3. post body
                val resolvedBody = body
                    .resolveNumberTag(
                        cc = aCtx.cc,
                        domestic = aCtx.domestic,
                        fullNumber = aCtx.fullNumber,
                        rawNumber = aCtx.rawNumber,
                    )
                    .resolveSmsTag(aCtx.smsContent)
                    .replace(tagCategory, aCtx.realCategory ?: "")
                    .resolveCustomTag(aCtx.customTags)
                if (method == HTTP_POST) {
                    aCtx.logger?.debug("${ctx.getString(R.string.http_post_body)}: %s".formatAnnotated(resolvedBody.A(DimGrey)))
                }

                // 4. Send request
                val result = httpRequest(
                    scope = aCtx.scope,
                    urlString = resolvedUrl,
                    headersMap = headersMap,
                    method = method,
                    postBody = resolvedBody,
                )

                aCtx.logger?.info(
                    ctx.getString(R.string.time_cost)
                        .format("${System.currentTimeMillis() - startTime}")
                )
                if (result?.exception != null) {
                    throw Exception(result.exception)
                }

                aCtx.lastOutput = result?.bytes

                val echo = Util.truncate(String(result?.bytes ?: byteArrayOf()))
                if (result?.statusCode == HttpURLConnection.HTTP_OK) {
                    aCtx.logger?.success("HTTP: <${result.statusCode}>")
                    aCtx.logger?.debug(echo)
                    return true
                } else {
                    aCtx.logger?.error("HTTP <${result?.statusCode}>: $echo")
                    return false
                }
            } catch (_: CancellationException) {
                // For API query, when a winner is found, others will be cancelled
                aCtx.logger?.debug(ctx.getString(R.string.canceling_thread))
                return false // no need to retry when canceled
            } catch (e: Exception) {
                aCtx.logger?.error("$e")

                // Don't return here for it to retry
            } finally {
                // Save the url for following actions(ImportToSpamDb)
                aCtx.httpUrl = url
            }

            if (!enableRetry)
                break

            retryAttempts ++

            if (retryAttempts <= retryTimes) {
                aCtx.logger?.warn(ctx.getString(R.string.retry_attempt).formatAnnotated(
                    "$retryAttempts".A(Teal200), "$retryTimes".A(Teal200)
                ))

                Thread.sleep(retryDelayMs.toLong())
            }
        } while (retryAttempts <= retryTimes)

        return false
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_http_request)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(4) {
            GreyIcon20(R.drawable.ic_link)
            SummaryLabel(" $url")
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_http_download)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray, ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_http)
    }

    @Composable
    override fun Options() {
        Column {
            StrInputBox(
                text = url,
                label = { Text(Str(R.string.url)) },
                leadingIconId = R.drawable.ic_link,
                placeholder = { DimGreyLabel("https://...") },
                helpTooltip = Str(R.string.help_http_url).format(
                    Str(R.string.number_tags),
                    Str(R.string.time_tags),
                ),
                onValueChange = { url = it }
            )

            StrInputBox(
                text = header,
                label = { Text(Str(R.string.http_header)) },
                leadingIconId = R.drawable.ic_http_header,
                onValueChange = { header = it },
                helpTooltip = Str(R.string.help_http_header) + "<br>" + Str(R.string.tags_supported) + Str(
                    R.string.auth_tags
                ),
                placeholder = { DimGreyLabel("apikey: ABC\nAuth: key\n…") }
            )

            var selected by remember { mutableIntStateOf(method) }
            val options = remember {
                listOf("GET", "POST").mapIndexed { index, label ->
                    LabelItem(label = label) {
                        method = index
                        selected = index
                    }
                }
            }
            LabeledRow(R.string.http_method) {
                ComboBox(options, selected)
            }

            AnimatedVisibleV(selected == HTTP_POST) {

                StrInputBox(
                    text = body,
                    label = { Text(Str(R.string.http_post_body)) },
                    leadingIconId = R.drawable.ic_post,
                    onValueChange = { body = it },
                    helpTooltip = Str(R.string.help_http_post_body).format(
                        Str(R.string.number_tags) + "<br>" + Str(R.string.sms_tags)
                    ),
                )
            }

            var retryState by remember { mutableStateOf(enableRetry) }

            LabeledRow(labelId = R.string.retry) {
                SwitchBox(retryState) { on ->
                    enableRetry = on
                    retryState = on
                }
            }

            AnimatedVisibleV(retryState) {
                Column {
                    NumberInputBox(
                        intValue = retryTimes,
                        label = { Text(Str(R.string.retry_times)) },
                        onValueChange = { newVal, hasError ->
                            if (!hasError) {
                                retryTimes = newVal!!
                            }
                        }
                    )
                    NumberInputBox(
                        intValue = retryDelayMs,
                        label = { Text(Str(R.string.retry_delay_millis)) },
                        onValueChange = { newVal, hasError ->
                            if (!hasError) {
                                retryDelayMs = newVal!!
                            }
                        }
                    )
                }
            }
        }
    }
}


@Serializable
@SerialName("CleanupSpamDB")
class CleanupSpamDB(
    var expiry: Int = 1 // days
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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
        SummaryLabel("$dir/$filename")
    }

    @SuppressLint("StringFormatInvalid")
    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_read_file).format(
            ctx.getString(R.string.path_tags),
            ctx.getString(R.string.time_tags)
        )
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
                helpTooltip = Str(R.string.tags_supported) + Str(R.string.path_tags),
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

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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

    @SuppressLint("StringFormatInvalid")
    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_write_file).format(
            ctx.getString(R.string.path_tags),
            ctx.getString(R.string.time_tags)
        )
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
                helpTooltip = Str(R.string.tags_supported) + Str(R.string.path_tags),
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

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        return try {
            // 1. Parse the csv to a `Csv` object that contains `header` and `rows`
            val csv = CSVParser(
                PushbackReader(BufferedReader(InputStreamReader(ByteArrayInputStream(input)))),
                JSONObject(columnMapping).toStringMap(),
            ).parse()

            // 2. Check if the headers contains required column `pattern`
            val colPattern = "pattern"
            if (!csv.headers.contains(colPattern)) {
                aCtx.logger?.error(
                    ctx.getString(R.string.csv_missing_column).formatAnnotated(
                        colPattern.A(Teal200), colPattern.A(Teal200), colPattern.A(Teal200)
                    )
                )
                return false
            }

            // 3. Map `Csv.rows` to `RegexRule`s
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
            helpTooltip = Str(R.string.import_csv_columns),
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

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        return try {
            val rules = Xml.parseRules(bytes = input, xpath).map {
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

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        return try {
            val opts = Util.flagsToRegexOptions(regexFlags)

            val haystack = input.toString(Charsets.UTF_8) // identical to `String(input)`

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
class ImportToSpamDB(
    // Only presets can use ImportDbReason.ByAPI
    // This is for internal app use, not displayed on GUI
    val importReason: ImportDbReason = ImportDbReason.Manually,
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rules = aCtx.lastOutput as List<*> // it's actually `List<RegexRule>`

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
    var importAs: Int = Def.ForNumber,
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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

    private fun getTable(): RuleTable {
        return when (importAs) {
            Def.ForNumber -> NumberRuleTable()
            Def.ForSms -> ContentRuleTable()
            else -> QuickCopyRuleTable()
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
                    RadioItem(Str(R.string.allow), C.pass),
                    RadioItem(Str(R.string.block), C.block),
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
                .formatAnnotated("${map.values.sumOf { it.size }}".A(Teal200), pattern.A(DimGrey))
        )

        aCtx.lastOutput = map
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_find_rules)
    }

    @Composable
    override fun Summary() {
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
input: Map<forType, List<RegexRule>>
output: none
 */
@Serializable
@SerialName("ModifyRules")
class ModifyRules(
    var config: String = "",
) : IPermissiveAction {

    fun modifyInMemory(ctx: Context, aCtx: ActionContext): Boolean {
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
                            rule.summary().A(Teal200),
                            config.A(DimGrey)
                        )
                )
                it.rule = newRule
            }
        }

        return true
    }
    @Suppress("UNCHECKED_CAST")
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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
                                rule.summary().A(Teal200),
                                config.A(DimGrey)
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

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
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

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        spf.Global(ctx).setGloballyEnabled(enable)
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

// For "API Query" only, not for Workflows.
// This action parses the incoming number and fill the ActionContext with cc/domestic/number,
//  which can be used in following actions like HttpRequest.
@Serializable
// The historical name, it should be renamed to `InterceptCall`.
// If you know how to rename it with history compatibility, please answer here:
//   https://stackoverflow.com/q/79415650/2219196
@SerialName("ParseIncomingNumber")
class InterceptCall(
    var numberFilter: String = ".*",
) : IAction {

    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(
            PermissionWrapper(
                Permission.phoneState,
                prompt = ctx.getString(R.string.auto_detect_cc_permission)
            )
        )
    }

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        // The `rawNumber` is set by the workflow caller before it's executed.
        val rawNumber = aCtx.rawNumber!!
        aCtx.logger?.debug("${label(ctx)}: $rawNumber")

        // Skip alpha/empty numbers, such as: Microsoft and ""
        if (rawNumber.isEmpty() || isAlphaNumber(rawNumber)) {
            aCtx.logger?.warn(ctx.getString(R.string.skip_alpha_empty_number))
            return false
        }

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
            if (ok) { // +1 222 333 4444 mobile format
                aCtx.cc = cc
                aCtx.domestic = domestic
            } else { // unknown error
                aCtx.logger?.error(ctx.getString(R.string.fail_detect_cc) + " " + ctx.getString(R.string.bug_number))
                return false
            }
            return true
        } else { // not start with "+"
            val cc = CountryCode.current(ctx)
            if (cc == null) {
                aCtx.logger?.error(ctx.getString(R.string.fail_detect_cc))
                return false
            }

            // Not sure if it's possible to have a number start with CC but has no leading `+`,
            //  for instance: 33xxxxxxxx
            // Check the part xxxxxxxx, if it's still a valid number for cc==33,
            //   then the xxxxxxxx is the domestic part.
            // Otherwise, the entire 33xxxxxxxx is a domestic number
            if (clearedNumber.startsWith(cc.toString())) {
                val rest = clearedNumber.substring(cc.toString().length)
                val pnUtil = PhoneNumberUtil.getInstance()
                val n = Phonenumber.PhoneNumber().apply {
                    countryCode = cc
                    nationalNumber = rest.toLong()
                }
                if (pnUtil.isValidNumber(n)) { // the number start with CC
                    aCtx.cc = cc.toString()
                    aCtx.domestic = rest
                } else { // it's simply a domestic number
                    aCtx.cc = cc.toString()
                    aCtx.domestic = clearedNumber
                }
            } else { // it's simply a domestic number
                aCtx.cc = cc.toString()
                aCtx.domestic = clearedNumber
            }
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_intercept_call)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(4) {
            GreyIcon20(R.drawable.ic_filter)
            SummaryLabel(numberFilter)
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_intercept_call).format(
            ctx.getString(R.string.number_tags)
        )
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
    }
}


// This action must be the first action of the workflow.
@Serializable
@SerialName("InterceptSms")
class InterceptSms(
) : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        // Nothing to do
        // It just needs to be there, as the first action in the workflow.
        // Because new SMS will only be checked by such workflows.
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_intercept_sms)
    }

    @Composable
    override fun Summary() {
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_intercept_sms)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon20(iconId = R.drawable.ic_sms)
    }

    @Composable
    override fun Options() {
        NoOptionNeeded()
    }
}

// The return value of the Action ParseQueryResult,
//  and it will be saved along with other information in HistoryTable.
@Serializable
@SerialName("ApiQueryResult")
data class ApiQueryResult(
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

    var categoryMapping: String = "",
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        var input = if (aCtx.lastOutput is ByteArray) {
            aCtx.lastOutput as ByteArray
        } else {
            aCtx.lastParsedQueryData!!
        }
        aCtx.lastParsedQueryData = input // Save for following `ParseQueryResult` actions

        val html = String(input)

        aCtx.logger?.debug(label(ctx))

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
                // 1. Get category
                val categoryOpts = Util.flagsToRegexOptions(categoryFlags)
                category = categorySig.trim().toRegex(categoryOpts).findAll(html)
                    .map {
                        it.groups
                            .drop(1)
                            .filterNotNull()
                            .firstOrNull()?.value
                    }
                    .filterNot { it.isNullOrEmpty() }
                    .joinToString(" ")

                // 2. Map category
                if (categoryMapping.isNotEmpty()) {
                    val map = JSONObject(categoryMapping).toMap()
                    category = map.getOrDefault(category, category) as? String
                }
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
            aCtx.logger?.debug(ctx.getString(R.string.unidentified_number))
        }

        // Only update the racingResult when it's the first `ParseQueryResult` or determined.
        if (determined || aCtx.racingResult == null) {
            val result = ApiQueryResult(
                determined = determined,
                isSpam = isNegative == true,
                category = category,
                serverEcho = html,
            )
            aCtx.lastOutput = result
            aCtx.racingResult = result
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_parse_query_result)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(4) {
            if (negativeSig.isNotEmpty()) {
                RowVCenter {
                    GreyIcon16(R.drawable.ic_no)
                    SummaryLabel(negativeSig)
                }
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
        return listOf(ParamType.ByteArray, ParamType.InstantQueryResult)
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

        var jsonStr by remember { mutableStateOf(categoryMapping) }
        StrInputBox(
            text = jsonStr,
            label = { Text(Str(R.string.category_mapping)) },
            leadingIconId = R.drawable.ic_category,
            placeholder = { DimGreyLabel(Str(R.string.category_mapping_placeholder)) },
            helpTooltip = Str(R.string.help_category_mapping),
            onValueChange = { newVal ->
                categoryMapping = newVal
            }
        )
    }
}

// Generate a List<RegexRule> for next step ImportToSpamDB
@Serializable
@SerialName("FilterSpamResult")
class FilterSpamResult() : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ApiQueryResult

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

// Spam category config, for reporting number.
@Serializable
@SerialName("CategoryConfig")
class CategoryConfig(
    // e.g.:
    //  {
    //    "{fraud}" to "G_FRAUD",
    //    "{advertising}" to "E_ADVERTISING",
    //    ...
    //  }
    var map: Map<String, String> = mapOf()
) : IPermissiveAction {

    // It gets the ActionContext.tagCategory tag and fill the ActionContext.realCategory
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        // 1. check it in the map config
        var realCategoryStr = map[aCtx.tagCategory]

        if (realCategoryStr == null) {
            aCtx.logger?.warn(
                ctx.getString(R.string.missing_category).formatAnnotated(
                    aCtx.tagCategory!!.A(DodgeBlue),
                    ctx.getString(R.string.action_category_config).A(Pink80)
                )
            )
            return false
        }

        // 2. save it in ActionContext, it will be used in the next http Action
        aCtx.realCategory = realCategoryStr
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_category_config)
    }

    @Composable
    override fun Summary() {
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_category_config)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(iconId = R.drawable.ic_category)
    }

    @Composable
    override fun Options() {
        var jsonStr by remember { mutableStateOf(PermissivePrettyJson.encodeToString(map)) }
        StrInputBox(
            text = jsonStr,
            label = { Text(Str(R.string.action_category_config)) },
            leadingIconId = R.drawable.ic_category,
            placeholder = {
                DimGreyLabel(
                    """
                {
                  "{marketing}": "gym",
                  "{other}": "bitcoin",
                  ...
                }
            """.trimIndent()
                )
            },
            helpTooltip = Str(R.string.help_action_category_config),
            onValueChange = { newVal ->
                try {
                    map = PermissiveJson.decodeFromString(newVal)
                    jsonStr = newVal
                } catch (_: Exception) {
                }
            }
        )
    }
}

// Report number to all api endpoints.
// (For internal app usage only.)
@Serializable
@SerialName("ReportNumber")
class ReportNumber(
    val rawNumber: String,
    val asTagCategory: String,
    val domainFilter: List<String>? = null // only report to APIs that matches these domains
) : IAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(
            PermissionWrapper(
                Permission.callLog,
                prompt = ctx.getString(R.string.report_number_require_call_log_permission)
            ),
        )
    }

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val apis = listReportableAPIs(ctx = ctx, rawNumber = rawNumber, domainFilter = domainFilter)

        // Report
        val scope = CoroutineScope(IO)
        apis.forEach { api ->
            scope.launch {
                val aCtx = ActionContext(
                    scope = scope,
                    logger = AdbLogger(),
                    rawNumber = rawNumber,
                    tagCategory = asTagCategory,
                )
                val success = api.actions.executeAll(ctx, aCtx)
                logi("report number $rawNumber to ${api.summary()}, success: $success")
            }
            true
        }

        return true
    }

    override fun label(ctx: Context): String {
        return "Report Number" // it will not be displayed on UI
    }

    @Composable
    override fun Summary() {
    }

    override fun tooltip(ctx: Context): String {
        return ""
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
    }

    @Composable
    override fun Options() {
    }
}

// Continue or terminate the workflow according to ongoing calendar event.
@Serializable
@SerialName("CalendarEvent")
class CalendarEvent(
    var enabled: Boolean = true,
    var eventTitle: String = "",
    var eventTitleFlags: Int = Def.DefaultRegexFlags,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(PermissionWrapper(Permission.calendar))
    }
    override fun isActivated(): Boolean {
        return enabled && Permission.calendar.isGranted
    }
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(2, modifier = modifier) {
            GreyIcon18(R.drawable.ic_incoming)
            GreyIcon18(R.drawable.ic_calendar)
            GreyLabel(
                text = eventTitle,
                modifier = M.padding(start = 4.dp)
            )
        }
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (!Permission.calendar.isGranted || !enabled)
            return false

        val ongoingEvents = Util.ongoingCalendarEvents(ctx)
        val triggered = ongoingEvents.any { it ->
            eventTitle.regexMatches(it, eventTitleFlags)
        }
        if (triggered) {
            aCtx.logger?.warn(
                ctx.getString(R.string.calendar_event_is_triggered)
                    .formatAnnotated(
                        eventTitle.A(Teal200)
                    )
            )
        } else {
            aCtx.logger?.debug(
                ctx.getString(R.string.calendar_event_is_not_active)
                    .formatAnnotated(
                        eventTitle.A(Teal200)
                    )
            )
        }
        // Calendar Events modifies rules temporarily.
        aCtx.isInMemory = true

        return triggered
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.calendar_event)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(8) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            SummaryLabel(eventTitle)
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_calendar_event)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_calendar)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        val flags = remember { mutableIntStateOf(eventTitleFlags) }
        RegexInputBox(
            regexStr = eventTitle,
            label = { Text(Str(R.string.event_title)) },
            regexFlags = flags,
            helpTooltipId = R.string.help_calendar_event_title,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    eventTitle = newVal
                }
            },
            onFlagsChange = {
                flags.intValue = it
                eventTitleFlags = it
            }
        )
    }
}

// This will be triggered on receiving SMS messages
@Serializable
@SerialName("SmsEvent")
class SmsEvent(
    var enabled: Boolean = true,
    var number: String = ".*",
    var numberFlags: Int = Def.DefaultRegexFlags,
    var content: String = ".*",
    var contentFlags: Int = Def.DefaultRegexFlags,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(
            PermissionWrapper(Permission.receiveSMS),
            PermissionWrapper(Permission.batteryUnRestricted, isOptional = true),
        )
    }

    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(2, modifier = modifier) {
            GreyIcon18(R.drawable.ic_sms)
            GreyLabel(
                text = "$content <- $number",
                modifier = M.padding(start = 4.dp)
            )
        }
    }
    override fun isActivated(): Boolean {
        return enabled && Permission.receiveSMS.isGranted
    }

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rawNumber = aCtx.rawNumber
        val smsContent = aCtx.smsContent

        // It's testing in the workflow dialog
        if (rawNumber == null || smsContent == null) {
            aCtx.logger?.error(ctx.getString(R.string.use_global_testing_instead))
            return false
        }

        if (!Permission.receiveSMS.isGranted || !enabled)
            return false

        if (!number.regexMatchesNumber(rawNumber, numberFlags)) {
            return false
        }
        if (!content.regexMatches(smsContent, contentFlags)) {
            return false
        }

        aCtx.logger?.warn(
            ctx.getString(R.string.sms_event_triggered)
                .formatAnnotated(
                    "$content <- $number".A(Teal200)
                )
        )

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.sms_event)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(8) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            SummaryLabel("$content <- $number")
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_sms_event)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon20(R.drawable.ic_sms)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        val flagsNumber = remember { mutableIntStateOf(numberFlags) }
        RegexInputBox(
            regexStr = number,
            label = { Text(Str(R.string.phone_number)) },
            regexFlags = flagsNumber,
            placeholder = { DimGreyLabel(".*") },
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    number = newVal
                }
            },
            onFlagsChange = {
                flagsNumber.intValue = it
                numberFlags = it
            }
        )
        val flagsContent = remember { mutableIntStateOf(contentFlags) }
        RegexInputBox(
            regexStr = content,
            label = { Text(Str(R.string.sms_content)) },
            regexFlags = flagsContent,
            placeholder = { DimGreyLabel(".*") },
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    content = newVal
                }
            },
            onFlagsChange = {
                flagsContent.intValue = it
                contentFlags = it
            }
        )
    }
}


// Workflows that contain this preprocessor will be executed before checking the number.
@Serializable
@SerialName("CallEvent")
class CallEvent(
    var enabled : Boolean = true,
    var number: String = ".*",
    var numberFlags: Int = Def.DefaultRegexFlags,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(
            PermissionWrapper(Permission.callScreening),
        )
    }
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(2, modifier = modifier) {
            GreyIcon18(R.drawable.ic_incoming)
            GreyLabel(
                text = number,
                modifier = M.padding(start = 4.dp)
            )
        }
    }
    override fun isActivated(): Boolean {
        return enabled && Permission.callScreening.isGranted
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val rawNumber = aCtx.rawNumber

        // It's testing in the workflow dialog
        if (rawNumber == null) {
            aCtx.logger?.error(ctx.getString(R.string.use_global_testing_instead))
            return false
        }
        if (!enabled) {
            return false
        }

        if (!number.regexMatchesNumber(rawNumber, numberFlags)) {
            return false
        }

        aCtx.logger?.warn(
            ctx.getString(R.string.call_event_is_triggered)
                .formatAnnotated(
                    number.A(Teal200)
                )
        )
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.call_event)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(8) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            SummaryLabel(number)
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_call_event)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_incoming)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        val flagsNumber = remember { mutableIntStateOf(numberFlags) }
        RegexInputBox(
            regexStr = number,
            label = { Text(Str(R.string.phone_number)) },
            leadingIcon = { GreyIcon18(R.drawable.ic_filter) },
            regexFlags = flagsNumber,
            placeholder = { DimGreyLabel(".*") },
            showNumberFlags = true,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    number = newVal
                }
            },
            onFlagsChange = {
                flagsNumber.intValue = it
                numberFlags = it
            }
        )
    }
}

// Modify the incoming number before screening.
// Use case: Some MNO provide services that allow a secondary number for the same SIM card. For number
//  3377777777, the secondary number can be "1113377777777" (with an extra prefix 111),
//  the prefix 111 needs to be removed.
@Serializable
@SerialName("ModifyNumber")
class ModifyNumber(
    var from: String = "",
    var fromFlags: Int = Def.DefaultRegexFlags,
    var to: String = "",
) : IPermissiveAction {

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val cCtx = aCtx.cCtx!!

        val before = cCtx.rawNumber
        val after = before.regexReplace(from, to, fromFlags)

        if (before != after) {
            cCtx.rawNumber = after

            aCtx.logger?.warn(
                ctx.getString(R.string.modify_number_template)
                    .formatAnnotated(
                        before.A(DimGrey),
                        after.A(DimGrey)
                    )
            )
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_modify_number)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(8) {
            SummaryLabel("$from -> $to")
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_modify_number)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
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
        val fromFlagsState = remember { mutableIntStateOf(fromFlags) }
        RegexInputBox(
            regexStr = from,
            label = { Text(Str(R.string.replace_from)) },
            regexFlags = fromFlagsState,
            showNumberFlags = true,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    from = newVal
                }
            },
            onFlagsChange = {
                fromFlagsState.intValue = it
                fromFlags = it
            }
        )
        StrInputBox(
            text = to,
            label = { Text(Str(R.string.replace_to)) },
            onValueChange = {
                to = it
            }
        )
    }
}


@Serializable
@SerialName("CallThrottling")
class CallThrottling(
    var enabled: Boolean = true,
    var durationSec: Int = 30, // 30 seconds
    var includingBlocked: Boolean = false,
    var includingAnswered: Boolean = false,
    var minCallDurationSec: Int = 15, // 15 seconds
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(PermissionWrapper(Permission.callLog))
    }
    override fun isActivated(): Boolean {
        return enabled && Permission.callLog.isGranted
    }
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(6, modifier = modifier) {
            GreyIcon18(R.drawable.ic_multi_call)

            RowVCenterSpaced(2) {
                if (includingAnswered) {
                    GreyIcon16(R.drawable.ic_call)
                }
                if (includingBlocked) {
                    GreyIcon16(R.drawable.ic_call_blocked)
                }
            }
            GreyLabel(Str(R.string.seconds_template).format(durationSec))
        }
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (aCtx.cCtx?.rawNumber == null) {
            aCtx.logger?.error(ctx.getString(R.string.use_global_testing_instead))
            return false
        }

        if (!isActivated())
            return false

        // Check real call history
        val hasBlockedRealCalls = Util.recentCalls(
            ctx,
            withinMillis = durationSec.toLong() * 1000,
            includingBlocked = includingBlocked,
            includingAnswered = includingAnswered,
            minCallDurationSec = minCallDurationSec,
        ).isNotEmpty()


        // Check testing calls in local db
        var hasBlockedTestingCalls = aCtx.cCtx?.callDetails == null && // Is testing
                includingBlocked &&
                CallTable().hasBlockedRecordsWithinSeconds(ctx, durationSeconds = durationSec)

        if (!hasBlockedRealCalls && !hasBlockedTestingCalls) {
            return false // no recently blocked calls, nothing to do
        }

        aCtx.logger?.warn(ctx.getString(R.string.call_throttling_triggered))

        // Throttling Event modifies rules temporarily.
        aCtx.isInMemory = true

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.call_throttling)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(8) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(2) {
                if (includingAnswered) {
                    GreyIcon18(R.drawable.ic_call)
                }
                if (includingBlocked) {
                    GreyIcon18(R.drawable.ic_call_blocked)
                }
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_call_throttling)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_multi_call)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        // Enabled
        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        // Duration
        NumberInputBox(
            intValue = durationSec,
            label = { Text(Str(R.string.duration_in_seconds)) },
            onValueChange = { newVal, hasError ->
                if (!hasError) {
                    durationSec = newVal!!
                }
            }
        )

        // Include Blocked
        // Must use a state, otherwise the switch doesn't change on click
        var includeBlocked by remember { mutableStateOf(includingBlocked) }

        LabeledRow(labelId = R.string.including_blocked) {
            SwitchBox(includeBlocked) { on ->
                includeBlocked = on
                includingBlocked = on
            }
        }

        // Include Answered
        // Must use a state, otherwise the switch doesn't change on click
        var includeAnswered by remember { mutableStateOf(includingAnswered) }

        LabeledRow(labelId = R.string.including_answered) {
            SwitchBox(includeAnswered) { on ->
                includeAnswered = on
                includingAnswered = on
            }
        }

        // Minimal allowed call duration
        AnimatedVisibleV(includeAnswered) {
            NumberInputBox(
                intValue = minCallDurationSec,
                label = { Text(Str(R.string.minimal_call_duration)) },
                onValueChange = { newVal, hasError ->
                    if (!hasError) {
                        minCallDurationSec = newVal!!
                    }
                }
            )
        }
    }
}


@Serializable
@SerialName("SmsThrottling")
class SmsThrottling(
    var enabled: Boolean = true,
    var durationSec: Int = 60, // 60 seconds
    var countLimit: Int = 3,
    var targetRuleDesc: String = "",
    var targetRuleDescFlags: Int = Def.DefaultRegexFlags,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf(PermissionWrapper(Permission.readSMS))
    }
    override fun isActivated(): Boolean {
        return enabled && Permission.readSMS.isGranted
    }
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(6, modifier = modifier) {
            GreyIcon18(R.drawable.ic_multi_sms)

            RowVCenterSpaced(4) {
                GreyLabel("$countLimit/$durationSec")
                GreyLabel(targetRuleDesc)
            }
        }
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (aCtx.cCtx?.rawNumber == null || aCtx.cCtx?.smsContent == null) {
            aCtx.logger?.error(ctx.getString(R.string.use_global_testing_instead))
            return false
        }

        if (!isActivated())
            return false

        val rules = NumberRuleTable().findRuleByDesc(ctx, targetRuleDesc, targetRuleDescFlags)
        if (rules.isEmpty())
            return false
        val rule = rules[0]

        val forContact = rule.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT)
        val forContactGroup = rule.patternFlags.hasFlag(Def.FLAG_REGEX_FOR_CONTACT_GROUP)

        val matches = fun(rawNumber: String) : Boolean {
            return if (forContact) {
                val contactInfo = Contacts.findContactByRawNumber(ctx, rawNumber)
                contactInfo != null && rule.matches(contactInfo.name)
            } else if (forContactGroup) {
                Contacts.findGroupsContainNumber(ctx, rawNumber)
                    .find { groupName ->
                        rule.matches(groupName)
                    } != null
            } else {
                rule.pattern.regexMatchesNumber(rawNumber, rule.patternFlags)
            }
        }

        // Get from system SMS history
        var smses = Util.getHistorySMSes(ctx, Def.DIRECTION_INCOMING, durationSec.toLong()*1000)
            .filter {
                matches(it.rawNumber) // by sender category
                // it.rawNumber == aCtx.rawNumber // by sender
            }
            .map {it.rawNumber}

        if (smses.isEmpty()) {
            // Get from local db for testing
            smses = SmsTable().getRecordsWithinSeconds(ctx, durationSec)
                .filter {
                    matches(it.peer) // by sender category
                    // it.peer == aCtx.rawNumber // by sender
                }
                .map { it.peer }
            if (smses.isEmpty())
                return false
        }

        val matchCount = smses.size

        if (matchCount < countLimit) {
            return false
        }

        aCtx.logger?.warn(ctx.getString(R.string.sms_throttling_triggered))

        // Throttling Event modifies rules temporarily.
        aCtx.isInMemory = true

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.sms_throttling)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(8) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            RowVCenterSpaced(2) {
                GreyLabel("$countLimit/$durationSec")
                GreyLabel(targetRuleDesc)
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_sms_throttling)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_multi_sms)
    }

    @Composable
    override fun Options() {
        // Must use a state, otherwise the switch doesn't change on click
        var enabledState by remember { mutableStateOf(enabled) }

        // Enabled
        LabeledRow(labelId = R.string.enable) {
            SwitchBox(enabledState) { on ->
                enabled = on
                enabledState = on
            }
        }

        // Duration
        NumberInputBox(
            intValue = durationSec,
            label = { Text(Str(R.string.duration_in_seconds)) },
            onValueChange = { newVal, hasError ->
                if (!hasError) {
                    durationSec = newVal!!
                }
            }
        )

        // Count Limit
        NumberInputBox(
            intValue = countLimit,
            label = { Text(Str(R.string.count_limit)) },
            onValueChange = { newVal, hasError ->
                if (!hasError) {
                    countLimit = newVal!!
                }
            }
        )

        // Target rule desc
        val flagsState = remember { mutableIntStateOf(targetRuleDescFlags) }
        RegexInputBox(
            regexStr = targetRuleDesc,
            label = { Text(Str(R.string.target_rule_desc)) },
            regexFlags = flagsState,
            helpTooltipId = R.string.help_target_rule_desc,
            onRegexStrChange = { newVal, hasError ->
                if (!hasError) {
                    targetRuleDesc = newVal
                }
            },
            onFlagsChange = {
                flagsState.intValue = it
                targetRuleDescFlags = it
            }
        )
    }
}

@Serializable
class Features(
    var regex: String?,
    // var repeatedCall: Boolean?,
    // ...
)

@Serializable
@SerialName("Ringtone")
class Ringtone(
    var enabled: Boolean = true, // always enabled, can't be disabled
    var mute: Boolean = false,
    var ringtoneUri: String? = null,
    var bindTo: String = "{ \"regex\": \"\" }",
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return if (mute) // mute doesn't write to system settings
            listOf()
        else
            listOf(PermissionWrapper(Permission.writeSettings))
    }
    override fun isActivated(): Boolean {
        return enabled &&
                if (mute) true else Permission.writeSettings.isGranted
    }
    private fun labelBindTo(): String {
        try {
            val j = PermissiveJson.decodeFromString<Features>(bindTo)
            if (j.regex != null) {
                return j.regex!!
            }
//        if (j.repeatedCall != null) {
//            return ctx.getString("repeated call")
//        }
        } catch (_: Exception) {
        }
        return ""
    }
    @Composable
    override fun TriggerType(modifier: Modifier) {
        val ctx = LocalContext.current

        RowVCenterSpaced(6, modifier = modifier) {
            GreyIcon18(R.drawable.ic_music)
            GreyLabel(labelBindTo())

            if (mute) {
                GreyIcon18(R.drawable.ic_bell_mute)
            } else {
                val uri = ringtoneUri?.toUri() ?: RingtoneUtil.getCurrent(ctx)
                GreyLabel(RingtoneUtil.getName(ctx, uri), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (!isActivated())
            return false

        val isTesting = aCtx.lastOutput == null

        if (isTesting) {
            aCtx.logger?.error(ctx.getString(R.string.call_to_test_ringtone))
            return false
        }

        var j: Features
        try {
            j = PermissiveJson.decodeFromString<Features>(bindTo)
        } catch (_: Exception) {
            return false
        }

        val r = aCtx.lastOutput as? ICheckResult

        var anyFeatureMatches = false

        if (j.regex != null && r is ByRegexRule) {
            // If the call was allowed by this regex
            anyFeatureMatches = j.regex!!.regexMatches(r.rule!!.description, Def.DefaultRegexFlags)
        }
//        if (j.repeatedCall != null) { // change ringtone for other features
//            anyFeatureMatches = ...
//        }

        // No feature matches, nothing to do.
        if (!anyFeatureMatches)
            return false

        // Apply the ringtone if any feature matches
        if (!mute) {
            RingtoneUtil.setDefaultUri(ctx, (ringtoneUri ?: "").toUri())

            // Reset the ringtone after 2 seconds, it should've already started ringing.
            CoroutineScope(IO).launch {
                delay(2000)
                resetRingtone(ctx)
            }
        }

        // this will be used in CallScreeningService, ugly workaround..
        aCtx.shouldMute = mute

        return true
    }
    private fun resetRingtone(ctx: Context) {
        // 1. check if it was set in CallScreeningService
        val spf = spf.Temporary(ctx)
        val previousRingtone = spf.getRingtone()
        if (previousRingtone.isEmpty())
            return
        spf.setRingtone("") // clear in spf

        // 2. check permission
        if (!Permission.writeSettings.isGranted)
            return

        // 3. restore to the previous ringtone
        RingtoneUtil.setDefaultUri(ctx, previousRingtone.toUri())
    }


    override fun label(ctx: Context): String {
        return ctx.getString(R.string.ringtone)
    }

    @Composable
    override fun Summary() {
        val ctx = LocalContext.current

        RowVCenterSpaced(8) {
            // Green dot
            if (enabled) {
                GreenDot()
            }
            GreyLabel(labelBindTo())

            if (mute) {
                GreyIcon18(R.drawable.ic_bell_mute)
            } else {
                val uri = ringtoneUri?.toUri() ?: RingtoneUtil.getCurrent(ctx)
                GreyLabel(RingtoneUtil.getName(ctx, uri), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_ringtone)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_music)
    }

    @Composable
    override fun Options() {
        val ctx = LocalContext.current

        var bindToState by remember { mutableStateOf(bindTo) }
        var error by remember(bindToState) {
            val label = labelBindTo()

            mutableStateOf<String?>(
                if (label.isEmpty())
                    ctx.getString(R.string.invalid_config)
                else
                    null
            )
        }
        // Target features
        StrInputBox(
            text = bindToState,
            label = { Text(Str(R.string.set_to)) },
            leadingIconId = R.drawable.ic_link,
            placeholder = { DimGreyLabel("{\"regex\": \"rule_desc\"}") },
            helpTooltip = Str(R.string.help_set_ringtone_to),
            supportingTextStr = error,
            onValueChange = {
                bindTo = it
                bindToState = it
            }
        )

        // Mute + Sound
        var sound by remember { mutableStateOf(
            ringtoneUri?.toUri() ?: RingtoneUtil.getCurrent(ctx)
        ) }
        var soundName by remember(sound) {
            mutableStateOf(RingtoneUtil.getName(ctx, sound))
        }

        val soundTrigger = remember { mutableStateOf(false) }
        RingtonePicker(soundTrigger) { uri, name ->
            uri?.let {
                sound = uri.toUri()
                ringtoneUri = uri
            }
        }
        // Mute
        var muteState by remember { mutableStateOf(mute) }
        LabeledRow(R.string.mute) {
            SwitchBox(muteState) { on ->
                mute = on
                muteState = on
            }
        }

        // Ringtone
        AnimatedVisibleV(!muteState) {
            LabeledRow(
                labelId = R.string.ringtone,
            ) {
                RowVCenterSpaced(6) {
                    GreyButton(soundName) {
                        soundTrigger.value = true
                    }
                }
            }
        }
    }
}

@Serializable
@SerialName("QuickTile")
class QuickTile(
    val tileIndex : Int = 0,
) : ITriggerAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf()
    }
    override fun isActivated(): Boolean {
        return state().value
    }
    private fun state() : MutableState<Boolean> {
        // Currently only 1 tile, always return this
        return when (tileIndex) {
//            0 -> G.dynamicTileEnabled
            else -> G.dynamicTile0Enabled
        }
    }
    @Composable
    override fun TriggerType(modifier: Modifier) {
        RowVCenterSpaced(6, modifier = modifier) {
            // Green dot
            GreyIcon18(R.drawable.ic_tile_custom)
            if (tileIndex > 0) {
                GreyLabel("[$tileIndex]")
            }
        }
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val active = isActivated()
        if (active) {
            aCtx.logger?.warn(ctx.getString(R.string.quick_tile_is_active))
            aCtx.isInMemory = true
        }
        return active
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.quick_tile)
    }

    @Composable
    override fun Summary() {
        RowVCenterSpaced(8) {
            // Green dot
            if (isActivated()) {
                GreenDot()
            }
            if (tileIndex > 0) {
                GreyLabel("[$tileIndex]")
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_quick_tile)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_tile_custom)
    }

    @Composable
    override fun Options() {
        NoOptionNeeded()
    }
}


@Serializable
@SerialName("GenerateTag")
class GenerateTag(
    var tagName: String = "",

    var parseType: Int = 0, // 0: regex, 1: xpath, 2: json path (in the future)

    // Regex
    var regex: String = "",
    var regexFlags: Int = Def.DefaultRegexFlags,

    // XPath
    var xpath: String = "",

    // Json path
//    var jsonPath: String = "",
) : IPermissiveAction {
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as ByteArray

        val text = input.toString(Charsets.UTF_8)

        var tagValue: String? = null
        try {
            when(parseType) {
                0 -> { // regex
                    val opts = Util.flagsToRegexOptions(regexFlags)
                    val reg = regex.toRegex(opts)
                    tagValue = Util.extractString(reg, text)
                }
                1 -> { // xpath
                    tagValue = Xml.parseString(input, xpath)
                }
            }

            aCtx.customTags[tagName] = tagValue ?: ""

            aCtx.logger?.info(ctx.getString(R.string.tag_generated)
                .formatAnnotated(
                    tagName.A(DimGrey),
                    (tagValue ?: "").A(Teal200),
                )
            )
        } catch (e: Exception) {
            aCtx.logger?.error(ctx.getString(R.string.failed_to_parse_tag))
            aCtx.logger?.error(e.message ?: "")
        }

        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.generate_tag)
    }

    @Composable
    override fun Summary() {
        when(parseType) {
            0 -> {
                SummaryLabel("{${tagName}} = RegEx: $regex")
            }
            1 -> {
                SummaryLabel("{${tagName}} = XPath: $xpath")
            }
        }
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_generate_tag)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.ByteArray)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None, ParamType.ByteArray)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_tag)
    }

    @Composable
    override fun Options() {
        Column {
            // Tag Name
            var tagNameState by remember { mutableStateOf(tagName) }
            StrInputBox(
                text = tagNameState,
                label = { Text(Str(R.string.tag_name)) },
                leadingIconId = R.drawable.ic_tag,
                onValueChange = {
                    tagName = it
                    tagNameState = it
                },
                supportingTextStr = if (tagNameState.trim().startsWith("{")) {
                    Str(R.string.invalid_tag_name)
                } else null
            )
            // Parse Type
            var selectedParseType by remember { mutableIntStateOf(parseType) }
            val options = remember {
                listOf("RegEx", "XPath").mapIndexed { index, label ->
                    LabelItem(label = label) {
                        parseType = index
                        selectedParseType = index
                    }
                }
            }
            LabeledRow(R.string.parse_type) {
                ComboBox(options, selectedParseType)
            }

            // Regex / XPath
            when (selectedParseType) {
                0 -> {
                    val flags = remember { mutableIntStateOf(regexFlags) }
                    RegexInputBox(
                        regexStr = regex,
                        label = { Text(Str(R.string.regex_pattern)) },
                        regexFlags = flags,
                        leadingIcon = { GreyIcon(R.drawable.ic_regex) },
                        placeholder = { DimGreyLabel("code: (\\d+)") },
                        onRegexStrChange = { newVal, hasError ->
                            if (!hasError) {
                                regex = newVal
                            }
                        },
                        onFlagsChange = {
                            flags.intValue = it
                            regexFlags = it
                        }
                    )
                }
                1 -> {
                    StrInputBox(
                        text = xpath,
                        label = { Text("XPath") },
                        leadingIconId = R.drawable.ic_xpath,
                        onValueChange = {
                            xpath = it
                        },
                    )
                }
            }
        }
    }
}
