package spam.blocker.ui.history

import android.annotation.SuppressLint
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.HistoryRecord
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.executeAll
import spam.blocker.service.reporting.listReportableCallAPIs
import spam.blocker.service.reporting.listReportableSmsAPIs
import spam.blocker.service.reporting.updateRecordReportLog
import spam.blocker.ui.M
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.JetpackTextLogger
import spam.blocker.util.MultiLogger
import spam.blocker.util.SaveableLogger
import spam.blocker.util.TimeUtils.formatTime
import spam.blocker.util.getSaveableOutput
import java.util.concurrent.atomic.AtomicInteger


const val tagCategory = "{category}"
const val tagComment = "{comment}"

const val tagValid = "{valid}"
const val tagOther = "{other}"
const val tagFraud = "{fraud}"
const val tagPromotional = "{promotional}"
const val tagSurvey = "{survey}"
const val tagReminder = "{reminder}"

private const val BATCH_REPORT_WORKERS = 10
private val batchReportSlots = Semaphore(BATCH_REPORT_WORKERS)
private val batchReportLogMutex = Mutex()

data class BatchReportProgress(
    val total: Int,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val finished: Boolean = false,
)

fun spamCategoryNamesMap(ctx: Context, forType: Int): Map<String, String> {
    return mapOf(
        tagValid to ctx.getString(when (forType) {
            Def.ForNumber -> R.string.valid_number
            else -> R.string.valid_sms
        }),

        tagFraud to ctx.getString(R.string.fraud),
        tagPromotional to ctx.getString(R.string.promotional),
        tagSurvey to ctx.getString(R.string.survey),
        tagReminder to ctx.getString(R.string.reminder),

        tagOther to ctx.getString(R.string.others), // the last one
    )
}

private suspend fun batchReport(
    ctx: Context,
    forType: Int,
    record: HistoryRecord,
    category: String,
    comment: String?,
    scope: CoroutineScope,
): Boolean {
    val logger = SaveableLogger()
    logger.info(ctx.getString(R.string.manual_report))
    logger.debug("${ctx.getString(R.string.executed_at)} ${formatTime(ctx, System.currentTimeMillis())}\n")

    val success = try {
        val apis = when (forType) {
            Def.ForNumber -> listReportableCallAPIs(ctx, record.peer, null, null, isManualReport = true)
            else -> listReportableSmsAPIs(ctx, isManualReport = true)
        }
        var allSucceeded = apis.isNotEmpty()
        for (api in apis) {
            val actionContext = ActionContext(
                scope = scope,
                logger = logger,
                rawNumber = record.peer,
                smsContent = record.extraInfo,
                tagCategoryValue = category,
                tagCommentValue = comment,
            )
            if (!api.actions.executeAll(ctx, actionContext)) allSucceeded = false
        }
        allSucceeded
    } catch (error: Exception) {
        logger.error(error.toString())
        false
    }

    val table = if (forType == Def.ForNumber) CallTable() else SmsTable()
    val vm = if (forType == Def.ForNumber) G.callVM else G.smsVM
    val log = logger.output.serialize()
    batchReportLogMutex.withLock {
        table.setAutoReportLog(ctx, record.id, log, !success)
        withContext(Dispatchers.Main) {
            vm.updateRecord(record.id) {
                copy(autoReportingLog = log, anythingWrongReporting = !success)
            }
        }
    }
    return success
}


@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BaseReportSpamDialog(
    trigger: MutableState<Boolean>,
    forType: Int,
    doReport: (String, String?) -> Unit,
    result: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val C = G.palette

    PopupDialog(trigger = trigger) {
        val nameMap = spamCategoryNamesMap(ctx, forType)
        var comment by remember { mutableStateOf<String?>(null) }
        val focusRequester = remember { FocusRequester() }
        val view = LocalView.current

        FlowRowSpaced(space = 20, vSpace = 30) {
            nameMap.forEach { (keyTag, name) ->
                StrokeButton(
                    label = name,
                    color = if(keyTag == tagValid) Color.Cyan else Color(keyTag.hashCode().toLong() or 0xffc08080),
                ) {
                    val inputMethodManager = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
                    doReport(keyTag, comment)
                }
            }
        }

        // Comment
        HorizontalDivider(modifier = M.padding(top = 10.dp))
        if(comment == null) {
            RowVCenter(
                horizontalArrangement = Arrangement.End,
                modifier = M
                    .padding(top = 10.dp)
                    .align(Alignment.End)
            ) {
                StrokeButton(
                    Str(R.string.comment),
                    color = C.textGrey,
                    icon = { GreyIcon16(R.drawable.ic_note) }
                ) {
                    comment = ""
                }
            }
        }
        if(comment != null) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            StrInputBox(
                text = comment!!,
                label = { Text(Str(R.string.comment)) },
                modifier = M.focusRequester(focusRequester).fillMaxWidth(),
                onValueChange = {
                    comment = it
                }
            )
        }

        result()
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SingleReportSpamDialog(
    trigger: MutableState<Boolean>,
    forType: Int,
    recordId: Long,
    rawNumber: String,
    smsContent: String? = null,
) {
    if (!trigger.value) return

    val ctx = LocalContext.current
    val C = G.palette
    val scope = CoroutineScope(IO)
    val reportResult = remember { mutableStateOf(buildAnnotatedString {  }) }

    BaseReportSpamDialog(
        trigger = trigger,
        forType = forType,
        doReport = { keyTag, comment ->
            reportResult.value = buildAnnotatedString {  }

            val apis = when (forType) {
                Def.ForNumber -> listReportableCallAPIs(ctx = ctx, rawNumber = rawNumber, domainFilter = null, isManualReport = true, checkResult = null)
                else -> listReportableSmsAPIs(ctx = ctx, isManualReport = true, checkResult = null)
            }

            val multiLogger = MultiLogger(listOf(
                JetpackTextLogger(reportResult),
                SaveableLogger()
            ))
            multiLogger.info(ctx.getString(R.string.manual_report))
            multiLogger.debug("${ctx.getString(R.string.executed_at)} ${formatTime(ctx, System.currentTimeMillis())}\n")

            val deferredResults = apis.map { api ->
                scope.async {
                    val aCtx = ActionContext(
                        scope = scope,
                        logger = multiLogger,
                        rawNumber = rawNumber,
                        smsContent = smsContent,
                        tagCategoryValue = keyTag,
                        tagCommentValue = comment
                    )

                    api.actions.executeAll(ctx, aCtx)
                }
            }
            scope.launch {
                val anythingWrong = deferredResults.awaitAll().contains(false)
                val log = multiLogger.getSaveableOutput()?.serialize() ?: ""

                updateRecordReportLog(
                    ctx,
                    table = if (forType == Def.ForNumber) CallTable() else SmsTable(),
                    vm = if(forType == Def.ForNumber) G.callVM else G.smsVM,
                    recordId = recordId, log = log, anythingWrong = anythingWrong
                )
            }
        },
        result = {
            if (reportResult.value.text.isNotEmpty()) {
                Spacer(modifier = M.height(10.dp))
                Text(text = reportResult.value, color = C.textGrey)
            }
        }
    )
}

@Composable
fun BatchReportSpamDialog(
    trigger: MutableState<Boolean>,
    forType: Int,
    records: List<HistoryRecord>,
) {
    if (!trigger.value) return

    val ctx = LocalContext.current
    val C = G.palette
    val batchProgress = remember { mutableStateOf<BatchReportProgress?>(null) }

    BaseReportSpamDialog(
        trigger = trigger,
        forType = forType,
        doReport = { keyTag, comment ->
            if (records.isNotEmpty()) {
                val appContext = ctx.applicationContext
                val batchScope = CoroutineScope(SupervisorJob() + IO)
                val nextIndex = AtomicInteger(0)
                batchProgress.value = BatchReportProgress(total = records.size)
                batchScope.launch {
                    coroutineScope {
                        repeat(minOf(BATCH_REPORT_WORKERS, records.size)) {
                            launch {
                                while (true) {
                                    val index = nextIndex.getAndIncrement()
                                    if (index >= records.size) break
                                    val success = try {
                                        batchReportSlots.withPermit {
                                            batchReport(appContext, forType, records[index], keyTag, comment, batchScope)
                                        }
                                    } catch (_: Exception) {
                                        false
                                    }
                                    withContext(IO) {
                                        val progress = batchProgress.value!!
                                        val succeeded = progress.succeeded + if (success) 1 else 0
                                        val failed = progress.failed + if (success) 0 else 1
                                        batchProgress.value = progress.copy(
                                            succeeded = succeeded,
                                            failed = failed,
                                            finished = succeeded + failed >= progress.total,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        result = {
            val progress = batchProgress.value
            if (progress != null) {
                Spacer(modifier = M.height(10.dp))
                if (progress.finished) {
                    GreyText(Str(R.string.all_finished))
                } else {
                    GreyText(Str(R.string.reporting))
                }
                LinearProgressIndicator(
                    progress = { (progress.succeeded + progress.failed).toFloat() / progress.total },
                    modifier = M.fillMaxWidth(),
                    color = C.infoBlue,
                    drawStopIndicator = {}
                )
                FlowRowSpaced(space = 12, vSpace = 4) {
                    Text(Str(R.string.n_succeeded).format(progress.succeeded), color = C.success)
                    Text(Str(R.string.n_failed).format(progress.failed), color = C.error)
                }

            }
        }
    )
}
