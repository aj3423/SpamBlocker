package spam.blocker.ui.setting.quick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.BayesSample
import spam.blocker.db.BayesTable
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.screenHeightDp
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.Button
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.LazyScrollbar
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.PriorityLabel
import spam.blocker.ui.widgets.ResIcon16
import spam.blocker.ui.widgets.ResIcon18
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SearchBox
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.ui.widgets.SwipeWrapper
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.ui.widgets.ToggleButton
import spam.blocker.util.A
import spam.blocker.util.ComplementNaiveBayes
import spam.blocker.util.Contacts
import spam.blocker.util.FileUtils.deleteInternalFile
import spam.blocker.util.FileUtils.writeInternalFile
import spam.blocker.util.LockedDebouncer
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.Util.getHistorySMSes
import spam.blocker.util.formatAnnotated
import spam.blocker.util.spf
import spam.blocker.util.truncate

object Bayes {
    const val Model_File = "bayes_model"
    const val Default_Threshold = 0.5f
    const val Default_Priority_Ham = 1
    const val Default_Priority_Spam = -1
}


private data class SmsCardInfo(
    val number: String,
    val time: Long,
    val isTest: Boolean = false,

    val content: String,
    val hash: Int,
    val isSpam: Boolean? = null,
    val heuristic: Double? = null
)

private data class SmsCounts(
    val ham: Int,
    val spam: Int,
    val unlabeled: Int,
)

@Composable
private fun SmsCard(info: SmsCardInfo) {
    val C = G.palette

    val ctx = LocalContext.current
    val spf = spf.NaiveBayes(ctx)

    OutlineCard(
        containerBg = C.dialogBg,
        borderWidth = if (info.isSpam == null) 1 else 2,
        borderColor = when(info.isSpam) {
            null -> C.textGrey
            true -> C.error
            false -> C.success
        },
        modifier = M.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = M
                .wrapContentSize()
        ) {
            // SMS Content
            val isSpam = info.heuristic?.let { it > spf.threshold }
            Text(
                text = info.content.truncate(100),
                modifier = M.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                color = when (isSpam) {
                    null -> C.textGrey
                    true -> C.error
                    false -> C.success
                }
            )
            // Probability
            RowVCenterSpaced(4, modifier = M
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-4).dp)
                .background(C.dialogBg)
            ) {
                // Probability
                if (info.heuristic != null) {
                    GreyLabel("%.4f".format(info.heuristic))
                }

                // Tube icon
                if (info.isTest) {
                    ResIcon16(R.drawable.ic_tube, color = G.palette.teal200)
                }
            }
        }
    }
}


@Composable
fun TrainingDialog(trigger: MutableState<Boolean>) {
    if (trigger.value) {
        val ctx = LocalContext.current

        val C = G.palette
        val aDay = 86400
        val aYear = 365 * aDay

        val table = BayesTable

        fun trainCNB(): ComplementNaiveBayes {
            val model = ComplementNaiveBayes()
            val dbSamples = table.listAll(ctx).map { Pair(it.content, it.isSpam) }
            model.train(dbSamples)
            return model
        }
        fun loadSmsCards() = run {
            // Get all real sms
            val realSmss = getHistorySMSes(
                ctx, Def.DIRECTION_INCOMING, withinMillis = aYear * 1000L, limit = 1000
            ).filter { // exclude texts from contacts
                Contacts.findContactByRawNumber(ctx, it.rawNumber) == null
            }.map {
                SmsCardInfo(number = it.rawNumber, content = it.content, hash = it.content.hashCode(), time = it.time)
            }

            // Get all training data
            val dbSmss = table.listAll(ctx).map {
                SmsCardInfo(
                    content = it.content, hash = it.hash, isSpam = it.isSpam,
                    number = "", time = 0,
                )
            }

            // Get from local db for testing
            val testSmss = SmsTable().getRecordsWithinSeconds(ctx, durationSeconds = aDay)
                .map {
                    SmsCardInfo(number = it.peer, content = it.extraInfo ?: "", hash = (it.extraInfo ?: "").hashCode(), time = it.time, isTest = true)
                }

            (testSmss + realSmss + dbSmss)
                .distinctBy { it.hash }
                .map {
                    if (it.isSpam == null) {
                        val dbCat = table.findByHash(ctx, it.hash)?.isSpam
                        it.copy(isSpam = dbCat)
                    } else {
                        it
                    }
                }
                .sortedByDescending { it.time }
                .toMutableStateList()
        }

        var cnb = remember { trainCNB() }
        val allSmss = remember { loadSmsCards() }
        val filter = remember { mutableStateOf("") }
        var showHam by remember { mutableStateOf(true) }
        var showSpam by remember { mutableStateOf(true) }
        var showUnlabeled by remember { mutableStateOf(true) }
        val showFilter = remember { mutableStateOf(false) }

        val visibleSmss by remember {
        derivedStateOf {
            allSmss
                .filter { sms ->
                    when (sms.isSpam) {
                        true -> showSpam
                        false -> showHam
                        null -> showUnlabeled
                    }
                }
                .let { list ->
                    if (filter.value.isNotBlank()) {
                        list.filter { it.content.contains(filter.value, ignoreCase = true) }
                    } else {
                        list
                    }
                }
            }
        }

        val counts by remember {
            derivedStateOf {
                allSmss.fold(SmsCounts(ham = 0, spam = 0, unlabeled = 0)) { counts, sms ->
                    when (sms.isSpam) {
                        false -> counts.copy(ham = counts.ham + 1)
                        true -> counts.copy(spam = counts.spam + 1)
                        null -> counts.copy(unlabeled = counts.unlabeled + 1)
                    }
                }
            }
        }

        // Settings Dialog
        PopupDialog(
            trigger = trigger,
            buttons = {
                FlowRowSpaced(20) {

                    // Delete model
                    val resetTrigger = remember { mutableStateOf(false) }
                    PopupDialog(resetTrigger, buttons = {
                        StrokeButton(Str(R.string.reset), color = G.palette.error) {
                            deleteInternalFile(ctx, Bayes.Model_File)
                            table.clearAll(ctx)
                            cnb = trainCNB()
                            allSmss.clear()
                            allSmss.addAll(loadSmsCards())
                            resetTrigger.value = false
                        }
                    }) {
                        HtmlText(Str(R.string.confirm_delete_trained_model))
                    }
                    StrokeButton(Str(R.string.reset), color = G.palette.error) {
                        resetTrigger.value = true
                    }

                    // Priority
                    val priTrigger = remember { mutableStateOf(false) }
                    PopupDialog(priTrigger) {
                        val spf = spf.NaiveBayes(ctx)
                        var priHam by remember { mutableIntStateOf(spf.priorityHam) }
                        var priSpam by remember { mutableIntStateOf(spf.prioritySpam) }

                        Section(Str(R.string.ham), bgColor = C.dialogBg, titleColor = C.success) {
                            PriorityBox(priHam) { newValue, hasError ->
                                if (!hasError) {
                                    priHam = newValue!!
                                    spf.priorityHam = newValue
                                }
                            }
                        }
                        Section(Str(R.string.spam), bgColor = C.dialogBg, titleColor = C.error) {
                            PriorityBox(priSpam) { newValue, hasError ->
                                if (!hasError) {
                                    priSpam = newValue!!
                                    spf.prioritySpam = newValue
                                }
                            }
                        }
                    }
                    StrokeButton(color = C.priority, icon = {
                        ResIcon18(R.drawable.ic_priority, color = C.priority, modifier = M.clickable {
                            priTrigger.value = true
                        })
                    })

                    // Test
                    StrokeButton(Str(R.string.test), color = G.palette.teal200) {
                        allSmss.indices.forEach { index ->
                            val info = allSmss[index]
                            val prob = cnb.spamProbability(info.content)
                            allSmss[index] = info.copy(heuristic = prob)
                        }
                    }
                }
            }
        ) {
            Column {
                // Filters
                Section(Str(R.string.filters), bgColor = C.dialogBg) {
                    FlowRowSpaced(4) {
                        ToggleButton(
                            enabled = showHam,
                            content = { Text("${Str(R.string.ham)} %s".formatAnnotated("${counts.ham}".A(C.success))) },
                            onClick = { showHam = !showHam }
                        )
                        ToggleButton(
                            enabled = showSpam,
                            content = { Text("${Str(R.string.spam)} %s".formatAnnotated("${counts.spam}".A(C.error))) },
                            onClick = { showSpam = !showSpam}
                        )
                        ToggleButton(
                            enabled = showUnlabeled,
                            content = { Text("${Str(R.string.unlabeled)} %s".formatAnnotated("${counts.unlabeled}".A(C.disabled))) },
                            onClick = { showUnlabeled = !showUnlabeled}
                        )
                        // Filter
                        AnimatedVisibleV(!showFilter.value) {
                            StrokeButton(Str(R.string.search), color = C.textGrey, icon = { GreyIcon18(R.drawable.ic_find) }) {
                                showFilter.value = true
                            }
                        }
                        AnimatedVisibleV(showFilter.value) {
                            val debouncer = remember { LockedDebouncer(waitMs = 300) }
                            SearchBox(showFilter, filter, autoFocus = false) {
                                debouncer.debounce {
                                }
                            }
                        }
                    }
                }

                fun saveModel() {
                    writeInternalFile(ctx, Bayes.Model_File, cnb.serialize().toByteArray())
                }

                fun updateCategory(smsInfo: SmsCardInfo, asSpam: Boolean?) {
                    val isSpam = smsInfo.isSpam
                    val content = smsInfo.content
                    val hash = smsInfo.hash

                    if (asSpam == null) { // unlabel it
                        // 1. update model
                        cnb.removeSample(content, isSpam!!)

                        // 2. update sample db
                        table.delByHash(ctx, hash)
                    } else { // swipe left/right
                        // 1. update model
                        if (isSpam == null) { // currently unlabeled, label it
                            cnb.addSample(content, asSpam)
                        } else { // already labeled, negate it
                            cnb.changeCategory(content, isSpam, asSpam)
                       }

                        // 2. update sample db
                        val rec = table.findByHash(ctx, hash)
                        if (rec == null) {
                            table.addNew(ctx, BayesSample(
                                hash = hash,
                                isSpam = asSpam,
                                content = content
                            ) )
                        } else {
                            table.updateById(ctx, rec.id, rec.copy(isSpam = asSpam))
                        }
                    }

                    // 3. update UI
                    val updated = smsInfo.copy(isSpam = asSpam)

                    val i = allSmss.indexOfFirst { it.hash == smsInfo.hash }
                    if (i >= 0) {
                        allSmss[i] = updated
                    }

                    // 4. save
                    saveModel()
                }

                val lazyState = rememberLazyListState()
                val percentage = 60 // Calculate x% of the screen height

                LazyScrollbar(
                    state = lazyState,
                    modifier = M.height((screenHeightDp() * percentage / 100).dp)
                ) {
                    LazyColumn(state = lazyState) {
                        itemsIndexed(visibleSmss, key = { _, item -> item.hash }) { _, item ->
                            SwipeWrapper(
                                left = SwipeInfo(
                                    veto = true,
                                    onSwipe = {
                                        if (item.isSpam != true)
                                            updateCategory(item, true)
                                    }
                                ),
                                right = SwipeInfo(
                                    veto = true,
                                    onSwipe = {
                                        if (item.isSpam != false)
                                            updateCategory(item, false)
                                    }
                                )
                            ) {
                                Box(modifier = M.clickable {
                                    if (item.isSpam != null)
                                        updateCategory(item, null)
                                }) {
                                    SmsCard(item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun MachineLearningButtonText(
    forceRefresh: Boolean
) {
    val C = G.palette

    val ctx = LocalContext.current
    val spf = spf.NaiveBayes(ctx)
    val priSpam = remember(forceRefresh) { spf.prioritySpam }
    val priHam = remember(forceRefresh) { spf.priorityHam }

    val hamCount = remember(forceRefresh) { BayesTable.count(ctx, false) }
    val spamCount = remember(forceRefresh) { BayesTable.count(ctx, true) }

    RowVCenterSpaced(6) {
        // Ham
        RowVCenterSpaced(2) {
            Text("$hamCount".A(C.textGrey))
            if (priHam != Bayes.Default_Priority_Ham) {
                PriorityLabel(priHam)
            }
        }

        // Spam
        RowVCenterSpaced(2) {
            Text("$spamCount".A(C.error))
            if (priSpam != Bayes.Default_Priority_Spam) {
                PriorityLabel(priSpam)
            }
        }
    }
}


@Composable
fun LocalAI() {
    LabeledRow(R.string.local_ai, helpTooltip = Str(R.string.help_local_ai)) {
        val ctx = LocalContext.current
        val spf = spf.NaiveBayes(ctx)

        var isEnabled by remember { mutableStateOf(spf.isEnabled) }

        if (isEnabled) {
            val trigger = remember { mutableStateOf(false) }
            TrainingDialog(trigger)

            Button(
                content = {
                    //  Refresh MachineLearningButtonText when dialog gets closed
                    MachineLearningButtonText(trigger.value)
                }
            ) {
                G.permissionChain.ask(
                    ctx,
                    listOf(PermissionWrapper(Permission.readSMS))
                ) { granted ->
                    if (granted) {
                        trigger.value = true
                    }
                }
            }
        }

        SwitchBox(isEnabled) { isTurningOn ->
            spf.isEnabled = isTurningOn
            isEnabled = isTurningOn
        }
    }
}


@Composable
fun LocalAISummary() {
    val ctx = LocalContext.current

    val spf = spf.NaiveBayes(ctx)

    val isEnabled = remember { spf.isEnabled }

    if (isEnabled) {
        Button(
            enabled = false,
            content = {
                RowVCenterSpaced(6) {
                    // Icon
                    GreyIcon(R.drawable.ic_ai, modifier = M.size(22.dp))

                    MachineLearningButtonText(false)
                }
            }
        )
    }
}