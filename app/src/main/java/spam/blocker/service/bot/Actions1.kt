package spam.blocker.service.bot

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.config.Configs
import spam.blocker.db.BotTable
import spam.blocker.db.CallTable
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.ImportDbReason
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.QuickCopyRegexTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RegexTable
import spam.blocker.db.SmsTable
import spam.blocker.db.SpamNumber
import spam.blocker.db.SpamTable
import spam.blocker.db.listReportableAPIs
import spam.blocker.db.reScheduleBot
import spam.blocker.db.ruleTableForType
import spam.blocker.def.Def
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.Checker.RegexRuleChecker
import spam.blocker.ui.M
import spam.blocker.ui.darken
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.api.tagCategory
import spam.blocker.ui.setting.api.tagComment
import spam.blocker.ui.setting.api.tagValid
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.DirButton
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.Placeholder
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.SummaryLabel
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.A
import spam.blocker.util.AdbLogger
import spam.blocker.util.CSVParser
import spam.blocker.util.CountryCode
import spam.blocker.util.FileUtils.readFileFromTree
import spam.blocker.util.FileUtils.writeFileInTree
import spam.blocker.util.Now
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.PermissiveJson
import spam.blocker.util.PermissivePrettyJson
import spam.blocker.util.Util
import spam.blocker.util.Util.domainFromUrl
import spam.blocker.util.Xml
import spam.blocker.util.formatAnnotated
import spam.blocker.util.hasFolderAccess
import spam.blocker.util.httpRequest
import spam.blocker.util.logi
import spam.blocker.util.regexExtract
import spam.blocker.util.regexMatches
import spam.blocker.util.regexReplace
import spam.blocker.util.resolveBase64Tag
import spam.blocker.util.resolveCustomTag
import spam.blocker.util.resolveEscapeTag
import spam.blocker.util.resolveHttpAuthTag
import spam.blocker.util.resolveNumberTag
import spam.blocker.util.resolveSHA1Tag
import spam.blocker.util.resolveSmsTag
import spam.blocker.util.resolveTimeTags
import spam.blocker.util.spf
import spam.blocker.util.toFolderDisplayName
import spam.blocker.util.toMap
import spam.blocker.util.toStringMap
import spam.blocker.util.unescapeUnicode
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.PushbackReader

@Composable
fun NoOptionNeeded() {
    GreyText(text = Str(R.string.no_config_needed))
}

@Serializable
@SerialName("CleanupHistory")
class PruneHistory(
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
        return ctx.getString(R.string.action_prune_history_record)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        val ctx = LocalContext.current
        val nDays = ctx.resources.getQuantityString(R.plurals.days, expiry, expiry)
        SummaryLabel(Str(R.string.expiry) + ": $nDays")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_prune_history_records)
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
open class HttpRequest(
    var method: Int = HTTP_GET,
    var url: String = "",
    // one attribute a line, e.g.:
    //   User-Agent: SpamBlocker/1.0
    //   Content-Type: application/json
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
        val C = G.palette

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
                    .replace(tagCategory, aCtx.realCategoryValue ?: "")
                    .replace(tagComment, aCtx.tagCommentValue ?: "")
                    .resolveSHA1Tag()
                    .resolveCustomTag(aCtx.customTags)
                aCtx.logger?.debug(ctx.getString(R.string.resolved_url).formatAnnotated(resolvedUrl.A(C.textGrey.darken())))

                // 2. Headers
                val headersMap = splitHeader(header, aCtx.customTags)
                headersMap.forEach { (key, value) ->
                    aCtx.logger?.debug("${ctx.getString(R.string.http_header)}: %s -> %s".formatAnnotated(
                        key.A(C.textGrey.darken()), value.A(C.textGrey.darken())
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
                    .resolveEscapeTag()
                    .replace(tagCategory, aCtx.realCategoryValue ?: "")
                    .replace(tagComment, aCtx.tagCommentValue ?: "")
                    .resolveCustomTag(aCtx.customTags)
                if (method == HTTP_POST) {
                    aCtx.logger?.debug("${ctx.getString(R.string.http_post_body)}: %s".formatAnnotated(resolvedBody.A(C.textGrey.darken())))
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

                aCtx.lastOutput = result?.echo

                val echo = Util.truncate(String(result?.echo ?: byteArrayOf()), limit = 1000)
                if (result?.statusCode in 200..299) {
                    aCtx.logger?.success("HTTP: <${result?.statusCode}>")
                    aCtx.logger?.debug("%s".formatAnnotated(echo.A(C.textGrey.darken())))
                    return true
                } else {
                    aCtx.logger?.error("HTTP <${result?.statusCode}>: $echo")
                    aCtx.cCtx?.anythingWrong = true
                    return false
                }
            } catch (_: CancellationException) {
                // For API query, when a winner is found, others will be canceled
                aCtx.logger?.debug(ctx.getString(R.string.canceling_thread))
                return false // no need to retry when canceled
            } catch (e: Exception) {
                aCtx.logger?.error("$e")
                aCtx.cCtx?.anythingWrong = true

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
                    "$retryAttempts".A(C.teal200), "$retryTimes".A(C.teal200)
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
    override fun Summary(showIcon: Boolean) {
        RowVCenterSpaced(4) {
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
        GreyIcon(R.drawable.ic_http)
    }

    @Composable
    override fun Options() {
        Column {
            StrInputBox(
                text = url,
                label = { Text(Str(R.string.url)) },
                leadingIconId = R.drawable.ic_link,
                placeholder = { Placeholder("https://...") },
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
                placeholder = { Placeholder("apikey: ABC\nAuth: key\n…") }
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
                        Str(R.string.number_tags) + "<br>"
                                + Str(R.string.sms_tags) + "<br>"
                        + Str(R.string.escape_tags)
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
class PruneDatabase(
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
        return ctx.getString(R.string.action_prune_database)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        val ctx = LocalContext.current

        val nDays = ctx.resources.getQuantityString(R.plurals.days, expiry, expiry)
        SummaryLabel(Str(R.string.expiry) + ": $nDays")
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_prune_database)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_db_delete)
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
        val compressed = curr.toByteArray()

        aCtx.logger?.debug(ctx.getString(R.string.action_backup_export))

        aCtx.lastOutput = compressed
        return true
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.action_backup_export)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        val yes = Str(R.string.yes)
        val no = Str(R.string.no)
        SummaryLabel(Str(R.string.include_spam_db) + ": ${if (includeSpamDB) yes else no}")
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
        GreyIcon(R.drawable.ic_export)
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
            val newCfg = Configs.fromByteArray(input)
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
    override fun Summary(showIcon: Boolean) {
        val ctx = LocalContext.current

        val yes = Str(R.string.yes)
        val no = Str(R.string.no)
        SummaryLabel(Str(R.string.include_spam_db) + ": ${if (includeSpamDB) yes else no}")
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
        GreyIcon(R.drawable.ic_import)
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
abstract class FileAction : IPermissiveAction {
    // These must be implemented by subclasses
    abstract var uriStr: String?
    abstract var filename: String

    // Helper to get the resolved URI
    protected fun getTreeUri() = uriStr?.toUri()

    @Composable
    override fun Summary(showIcon: Boolean) {
        val ctx = LocalContext.current

        RowVCenterSpaced(2) {
            val uri = getTreeUri()
            if (uri?.hasFolderAccess(ctx) == false) {
                ResIcon(R.drawable.ic_lock, modifier = M.size(16.dp), color = G.palette.warning)
            }
            SummaryLabel("${uri?.toFolderDisplayName() ?: "?"} / $filename")
        }
    }

    @Composable
    override fun Options() {
        Column {
            val uriState = remember { mutableStateOf(getTreeUri()) }
            LaunchedEffect(uriState.value) {
                uriStr = uriState.value?.toString()
            }
            LabeledRow(R.string.directory_abbrev) {
                DirButton(uri = uriState)
            }
            StrInputBox(
                text = filename,
                label = { Text(Str(R.string.filename)) },
                helpTooltip = Str(R.string.tags_supported) + Str(R.string.time_tags),
                onValueChange = { filename = it }
            )
        }
    }

    // Shared validation and logging logic to keep execute() clean
    protected fun validateAndLog(ctx: Context, aCtx: ActionContext): Uri? {
        val treeUri = getTreeUri()
        val fn = filename.resolveTimeTags()

        if (treeUri == null) {
            aCtx.logger?.error(label(ctx) + ": " + ctx.getString(R.string.dir_not_specified))
            return null
        }
        if (!treeUri.hasFolderAccess(ctx)) {
            aCtx.logger?.error(label(ctx) + ": " + ctx.getString(R.string.no_access_to_dir).format(treeUri.toFolderDisplayName()))
            return null
        }

        aCtx.logger?.debug(label(ctx) + ": ${treeUri.toFolderDisplayName() ?: "?"} / $fn")
        return treeUri
    }
}

@Serializable
@SerialName("ReadFile")
class ReadFile(
    override var uriStr: String? = null,
    override var filename: String = "",
) : FileAction() {
    override fun label(ctx: Context) = ctx.getString(R.string.action_read_file)
    override fun tooltip(ctx: Context) = ctx.getString(R.string.help_action_read_file)
    override fun inputParamType() = listOf(ParamType.None)
    override fun outputParamType() = listOf(ParamType.ByteArray)

    @Composable
    override fun Icon() = GreyIcon(R.drawable.ic_file_read)

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val treeUri = validateAndLog(ctx, aCtx) ?: return false
        return try {
            val bytes = readFileFromTree(ctx, treeUri, filename)
            aCtx.lastOutput = bytes
            true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
        }
    }
}

@Serializable
@SerialName("WriteFile")
class WriteFile(
    override var uriStr: String? = null,
    override var filename: String = "",
) : FileAction() {
    override fun label(ctx: Context) = ctx.getString(R.string.action_write_file)
    override fun tooltip(ctx: Context) = ctx.getString(R.string.help_action_write_file)
    override fun inputParamType() = listOf(ParamType.ByteArray)
    override fun outputParamType() = listOf(ParamType.None)

    @Composable
    override fun Icon() = GreyIcon(R.drawable.ic_file_write)

    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val input = aCtx.lastOutput as? ByteArray ?: return false
        val treeUri = validateAndLog(ctx, aCtx) ?: return false
        return try {
            writeFileInTree(ctx, treeUri, filename, input)
            true
        } catch (e: Exception) {
            aCtx.logger?.error("$e")
            false
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
        val C = G.palette

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
                        colPattern.A(C.teal200), colPattern.A(C.teal200), colPattern.A(C.teal200)
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
    override fun Summary(showIcon: Boolean) {
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
        GreyIcon(R.drawable.ic_csv)
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
    override fun Summary(showIcon: Boolean) {
        SummaryLabel(xpath)
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
        GreyIcon(R.drawable.ic_xml)
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
    override fun Summary(showIcon: Boolean) {
        SummaryLabel(pattern)
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
        GreyIcon(R.drawable.ic_regex_capture)
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
