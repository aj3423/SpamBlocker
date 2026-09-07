package spam.blocker.ui.setting.quick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import spam.blocker.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.config.Configs
import spam.blocker.db.BayesianSample
import spam.blocker.db.BayesianTable
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.screenHeightDp
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.misc.Backup_Last_Dir_Tag
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.FileChooser
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.InitFile
import spam.blocker.ui.widgets.LazyScrollbar
import spam.blocker.ui.widgets.MIME_GZ
import spam.blocker.ui.widgets.MIME_TEXT
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SearchBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.ui.widgets.SwipeWrapper
import spam.blocker.ui.widgets.ToggleButton
import spam.blocker.util.A
import spam.blocker.util.ComplementNaiveBayes
import spam.blocker.util.Contacts
import spam.blocker.util.FileUtils.readDataFromUri
import spam.blocker.util.LockedDebouncer
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.Util.getHistorySMSes
import spam.blocker.util.formatAnnotated
import spam.blocker.util.logi
import spam.blocker.util.truncate

private data class SmsCardInfo(
    val number: String,
    val time: Long,
    val isTest: Boolean = false,

    val content: String,
    val hash: Int,
    val category: Boolean? = null,
    var heuristic: Boolean? = null
)

@Composable
private fun SmsCard(info: SmsCardInfo, onClick: (() -> Unit)? = null) {
    val C = G.palette

    OutlineCard(
        containerBg = C.dialogBg,
        borderColor = when(info.category) {
            null -> C.textGrey
            true -> C.error
            false -> C.success
        },
        modifier = M.padding(vertical = 4.dp).let {
            if (onClick != null) it.clickable { onClick() } else it
        }
    ) {
        Box(
            modifier = M
                .wrapContentSize()
        ) {
            RowVCenterSpaced(
                4,
                modifier = M.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column(M.fillMaxWidth()) {
                    Text(info.content.truncate(100), color = when (info.heuristic) {
                        null -> C.textGrey
                        true -> C.error
                        false -> C.success
                    })
                }
            }
            if (info.isTest) {
                ResIcon(
                    R.drawable.ic_tube,
                    color = G.palette.teal200,
                    modifier = M
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-4).dp, y = (-4).dp)
                )
            }
        }
    }
}

@Composable
fun ClassifyDialog(trigger: MutableState<Boolean>) {
    if (trigger.value) {
        val ctx = LocalContext.current

        val C = G.palette
        val aDay = 86400
        val aYear = 365 * aDay

        val table = BayesianTable

        val realSmss = remember {
            getHistorySMSes(
                ctx, Def.DIRECTION_INCOMING, aYear * 1000L,
                limit = 2000
            ).filter { // exclude texts from contacts
                Contacts.findContactByRawNumber(ctx, it.rawNumber) == null
            }.map {
                SmsCardInfo(number = it.rawNumber, content = it.content, hash = it.content.hashCode(), time = it.time)
            }
        }
        val testSmss = remember {
            // Get from local db for testing
            SmsTable().getRecordsWithinSeconds(ctx, durationSeconds = aDay)
                .map {
                    SmsCardInfo(number = it.peer, content = it.extraInfo ?: "", hash = (it.extraInfo ?: "").hashCode(), time = it.time, isTest = true)
                }
        }

        val filter = remember { mutableStateOf("") }
        var refreshTrigger by remember { mutableStateOf(false) }
        var showHam by remember { mutableStateOf(true) }
        var showSpam by remember { mutableStateOf(true) }
        var showUnlabeled by remember { mutableStateOf(true) }
        val showFilter = remember { mutableStateOf(false) }

        val dbSmss = remember(refreshTrigger) {
            table.listAll(ctx).map {
                SmsCardInfo(
                    content = it.content, hash = it.hash, category = it.category,
                    number = "", time = 0,
                )
            }
        }

        var revision by remember { mutableIntStateOf(0) }

        val allSmss = remember(refreshTrigger) {
            (testSmss + realSmss + dbSmss)
                .distinctBy { it.hash }
                .map {
                    if (it.category == null) {
                        val dbCat = table.findByHash(ctx, it.hash)?.category
                        it.copy(category = dbCat)
                    } else {
                        it
                    }
                }
                .sortedByDescending { it.time }
                .toMutableStateList()
        }

        val filteredSmss = remember(revision, filter.value, refreshTrigger, showHam, showSpam, showUnlabeled) {
            allSmss
                .let { list ->
                    if (filter.value.isNotBlank()) {
                        list.filter { it.content.contains(filter.value, ignoreCase = true) }
                    } else {
                        list
                    }
                }
                .filter { sms ->
                    when (sms.category) {
                        true -> showHam
                        false -> showSpam
                        null -> showUnlabeled
                    }
                }
        }

        val ham = allSmss.count { it.category == false }
        val spam = allSmss.count { it.category == true }
        val unlabeled = allSmss.count { it.category == null }

        PopupDialog(
            trigger = trigger,
            buttons = {
                StrokeButton("Test it!", color = G.palette.infoBlue) {

                    val samples = table.listAll(ctx)
                    val cnb = ComplementNaiveBayes()

                    cnb.train(samples)


                    filteredSmss.forEach { info ->
                        val prob = cnb.spamProbability(info.content)
                        val isSpam = prob > 0.5
                        val updated = info.copy(heuristic = isSpam)
                        val allIdx = allSmss.indexOfFirst { it.hash == info.hash }
                        if (allIdx >= 0) {
                            allSmss[allIdx] = updated
                        }
                    }
                    revision++
                }
            }
        ) {
            Column {


                FlowRowSpaced(4) {
                    ToggleButton(
                        enabled = showHam,
                        content = { Text("Ham: %s".formatAnnotated("$ham".A(C.success))) },
                        onClick = { showHam = !showHam }
                    )
                    ToggleButton(
                        enabled = showSpam,
                        content = { Text("Spam: %s".formatAnnotated("$spam".A(C.error))) },
                        onClick = { showSpam = !showSpam}
                    )
                    ToggleButton(
                        enabled = showUnlabeled,
                        content = { Text("Unlabeled: $unlabeled") },
                        onClick = { showUnlabeled = !showUnlabeled}
                    )
                    // Filter
                    AnimatedVisibleV(!showFilter.value) {
                        StrokeButton("Search", color = C.textGrey, icon = { GreyIcon18(R.drawable.ic_find) }) {
                            showFilter.value = true
                        }
                    }
                    AnimatedVisibleV(showFilter.value) {
                        val debouncer = remember { LockedDebouncer(waitMs = 300) }
                        SearchBox(remember { mutableStateOf(true) }, filter, canExitSearch = false, autoFocus = false) {
                            debouncer.debounce {
                                if (filter.value.isEmpty()) {
                                    showFilter.value = false
                                }
                                refreshTrigger = !refreshTrigger
                            }
                        }
                    }
                }
                Spacer(modifier = M.height(10.dp))

                HorizontalDivider(modifier = M.padding(bottom = 4.dp))


                fun updateCategory(smsInfo: SmsCardInfo, category: Boolean?) {
                    if (category == null) {
                        table.delByHash(ctx, smsInfo.hash)
                    } else {
                        val rec = table.findByHash(ctx, smsInfo.hash)
                        if (rec == null) {
                            table.addNew(ctx, BayesianSample(
                                hash = smsInfo.hash,
                                category = category,
                                content = smsInfo.content
                            ) )
                        } else {
                            table.updateById(ctx, rec.id, rec.copy(category = category))
                        }
                    }
                    val updated = smsInfo.copy(category = category)
                    val allIdx = allSmss.indexOfFirst { it.hash == smsInfo.hash }
                    if (allIdx >= 0) {
                        allSmss[allIdx] = updated
                    }
                    revision++
                }

                val lazyState = rememberLazyListState()
                val percentage = 60 // Calculate x% of the screen height

                LazyScrollbar(
                    state = lazyState,
                    modifier = M.height((screenHeightDp() * percentage / 100).dp)
                ) {
                    LazyColumn(state = lazyState) {
                        itemsIndexed(filteredSmss, key = { _, item -> item.hash }) { _, smsInfo ->
                            SwipeWrapper(
                                left = SwipeInfo(
                                    veto = true,
                                    onSwipe = { updateCategory(smsInfo, true) }
                                ),
                                right = SwipeInfo(
                                    veto = true,
                                    onSwipe = { updateCategory(smsInfo, false) }
                                )
                            ) {
                                SmsCard(smsInfo) {
                                    updateCategory(smsInfo, null)
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
fun SettingDialog(trigger: MutableState<Boolean>) {
    val ctx = LocalContext.current

    PopupDialog(trigger) {
        val triggerClassify = remember { mutableStateOf(true) }
        ClassifyDialog(triggerClassify)

        GreyButton("classify") {
            triggerClassify.value = true
        }
    }
}

@Composable
fun LocalAI() {
    LabeledRow(R.string.test) {
        val ctx = LocalContext.current

        val trigger = remember { mutableStateOf(false) }
        SettingDialog(trigger)

        StrokeButton("Machine Learning", color = G.palette.warning) {
            G.permissionChain.ask(ctx, listOf(PermissionWrapper(Permission.readSMS))) { isGranted ->
                if (isGranted) {
                    trigger.value = true
                }
            }
        }
    }
}