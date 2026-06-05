package spam.blocker.ui.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def.FLAG_FOR_CALL
import spam.blocker.def.Def.FLAG_FOR_SMS
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.Checker.Companion.defaultCallCheckers
import spam.blocker.service.checker.Checker.Companion.defaultSmsCheckers
import spam.blocker.service.checker.IChecker
import spam.blocker.service.checker.findConflicts
import spam.blocker.ui.M
import spam.blocker.ui.priorityInlineMap
import spam.blocker.ui.setting.api.ApiHeader
import spam.blocker.ui.setting.api.ApiList
import spam.blocker.ui.setting.api.ApiQueryPresets
import spam.blocker.ui.setting.api.ApiReportPresets
import spam.blocker.ui.setting.bot.BotHeader
import spam.blocker.ui.setting.bot.BotList
import spam.blocker.ui.setting.misc.About
import spam.blocker.ui.setting.misc.BackupRestore
import spam.blocker.ui.setting.misc.FAQ
import spam.blocker.ui.setting.misc.Language
import spam.blocker.ui.setting.misc.Theme
import spam.blocker.ui.setting.quick.Answered
import spam.blocker.ui.setting.quick.BlockType
import spam.blocker.ui.setting.quick.CallerID
import spam.blocker.ui.setting.quick.Contacts
import spam.blocker.ui.setting.quick.Dialed
import spam.blocker.ui.setting.quick.EmergencySituation
import spam.blocker.ui.setting.quick.MeetingMode
import spam.blocker.ui.setting.quick.Notification
import spam.blocker.ui.setting.quick.OffTime
import spam.blocker.ui.setting.quick.RecentApps
import spam.blocker.ui.setting.quick.RepeatedCall
import spam.blocker.ui.setting.quick.SpamDB
import spam.blocker.ui.setting.quick.Stir
import spam.blocker.ui.setting.regex.PushAlertHeader
import spam.blocker.ui.setting.regex.PushAlertList
import spam.blocker.ui.setting.regex.PushAlertViewModel
import spam.blocker.ui.setting.regex.RegexHeader
import spam.blocker.ui.setting.regex.RegexList
import spam.blocker.ui.setting.regex.RegexViewModel
import spam.blocker.ui.setting.regex.SmsAlert
import spam.blocker.ui.setting.regex.SmsBomb
import spam.blocker.ui.slightDiff
import spam.blocker.ui.theme.White
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.NormalColumnScrollbar
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SearchBox
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.util.A
import spam.blocker.util.Lambda
import spam.blocker.util.Util.isFreshInstall
import spam.blocker.util.hasFlag
import spam.blocker.util.spf
import android.view.ViewTreeObserver

const val SettingRowMinHeight = 40

@Composable
fun SettingScreen() {
    val C = G.palette
    val ctx = LocalContext.current

    val testingTrigger = rememberSaveable { mutableStateOf(false) }
    TestDialog(testingTrigger)

    // Hide FAB when scrolled to the bottom
    val scrollState = rememberScrollState()
    val bottomReached by remember {
        derivedStateOf {
            scrollState.maxValue > 0 && scrollState.value == scrollState.maxValue
        }
    }

    val priorityConflicts = remember { mutableStateListOf<IChecker>() }
    fun checkPriorityConflict() {
        val callCheckers = defaultCallCheckers(ctx)
            // ugly workaround, remove regex rules that are not enabled for call
            .filter { if(it is Checker.RegexRuleChecker) it.rule.flags.hasFlag(FLAG_FOR_CALL) else true }
            .findConflicts()

        val smsCheckers = defaultSmsCheckers(ctx)
            .filter { if(it is Checker.RegexRuleChecker) it.rule.flags.hasFlag(FLAG_FOR_SMS) else true }
            .findConflicts()

        priorityConflicts.apply {
            clear()

            addAll((callCheckers + smsCheckers).distinctBy { it.desc() })
        }
    }
    // Detect priority conflicts when recomposed
    LaunchedEffect(Unit) {
        checkPriorityConflict()
    }

    // Detect priority conflicts when this screen regains focus, e.g., after closing a popup
    val view = LocalView.current
    // This means "call the latest version of this `checkPriorityConflict`"
    //   as it captures `ctx` and `priorityConflicts`, it's the Compose-correct pattern
    val currentCheckPriorityConflict by rememberUpdatedState {
        checkPriorityConflict()
    }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                currentCheckPriorityConflict()
            }
        }

        val observer = view.viewTreeObserver
        observer.addOnWindowFocusChangeListener(listener)

        onDispose {
            if (observer.isAlive) {
                observer.removeOnWindowFocusChangeListener(listener)
            }
        }
    }

    // Detect conflicts when clicking the "Test" button, show a popup warning if there are conflicts.
    val priorityConflictTrigger = remember { mutableStateOf(false) }
    if (priorityConflictTrigger.value) {
        PopupDialog(
            trigger = priorityConflictTrigger,
            icon = { ResIcon(R.drawable.ic_warning, color = Color.Unspecified) },
        ) {
            Column {
                HtmlText(Str(R.string.warning_priority_conflict), modifier = M.padding(bottom = 8.dp))
                priorityConflicts.toList().sortedBy { it.priority() }.forEach {
                    Text(
                        text = buildAnnotatedString {
                            appendInlineContent(id = "priority")

                            append(if(it.priority() == Int.MAX_VALUE) {
                                Str(R.string.max).A(G.palette.priority)
                            } else {
                                it.priority().toString().A(G.palette.priority)
                            })

                            append(" ")

                            append(
                                if (it.listType() == true)
                                    it.desc().text.A(C.teal200) // whitelist
                                else //
                                    it.desc().text.A(C.error) // blacklist
                            )
                        },
                        inlineContent = priorityInlineMap()
                    )
                }
            }
        }
    }

    // Show text "Testing" on the testing tube icon, and hide this text once it's clicked.
    val spf = spf.Global(ctx)
    var alsoShowTestButtonLabel by remember {
        mutableStateOf(
            isFreshInstall(ctx) && !spf.isTestIconClicked
        )
    }
    FabWrapper(
        fabRow = { positionModifier ->
            Fab(
                visible = !bottomReached,
                text = if (alsoShowTestButtonLabel) Str(R.string.title_rule_testing) else null,
                iconId = R.drawable.ic_tube,
                iconColor = White,
                bgColor = if (priorityConflicts.isEmpty()) C.teal200 else C.warning,
                modifier = positionModifier
            ) {
                checkPriorityConflict()
                if (priorityConflicts.isEmpty()) {
                    testingTrigger.value = true

                    spf.isTestIconClicked = true
                    alsoShowTestButtonLabel = false
                } else {
                    priorityConflictTrigger.value = true
                }
            }
        }
    ) {

        NormalColumnScrollbar(state = scrollState) {
            Column(
                modifier = M
                    .verticalScroll(scrollState)
                    .padding(top = 8.dp)
            ) {
                // global
                GloballyEnabled()

                // quick settings
                Section(
                    title = Str(R.string.quick_settings),
                    horizontalPadding = 8
                ) {
                    Column {
                        Contacts()
                        Stir()
                        SpamDB()
                        RepeatedCall()
                        Dialed()
                        Answered()
                        OffTime()
                        EmergencySituation()
                        RecentApps()
                        MeetingMode()

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = G.palette.background.slightDiff()
                        )

                        BlockType()
                        Notification()
                        CallerID()
                    }
                }

                Section(
                    title = Str(R.string.regex_settings),
                    horizontalPadding = 8
                ) {
                    Column {
                        // NumberRule / ContentRule / QuickCopy
                        listOf<RegexViewModel>(
                            G.NumberRuleVM,
                            G.ContentRuleVM,
                            G.QuickCopyRuleVM,
                        ).forEach { vm ->
                            LaunchedEffect(true) { vm.reloadDbAndOptions(ctx) }

                            RegexHeader(vm)
                            AnimatedVisibleV(!vm.listCollapsed.value) {
                                Column {
                                    SearchBox(vm.searchEnabled, vm.filter) {
                                        vm.reloadDb(ctx)
                                    }
                                    RegexList(vm)
                                }
                            }
                        }

                        // Push Alert
                        LaunchedEffect(true) { PushAlertViewModel.reloadDbAndOptions(ctx) }
                        PushAlertHeader()
                        AnimatedVisibleV(!PushAlertViewModel.listCollapsed.value) {
                            PushAlertList()
                        }

                        // SMS Alert
                        SmsAlert()

                        // SMS Bomb
                        SmsBomb()
                    }
                }

                // Instant Query
                Section(
                    title = Str(R.string.instant_query),
                    horizontalPadding = 8
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        // Api Query list
                        LaunchedEffect(true) { G.apiQueryVM.reloadDb(ctx) }
                        ApiHeader(G.apiQueryVM, ApiQueryPresets)
                        AnimatedVisibleV(!G.apiQueryVM.listCollapsed.value) {
                            ApiList(G.apiQueryVM)
                        }
                    }
                }

                // Report Number
                Section(
                    title = Str(R.string.report_number),
                    horizontalPadding = 8
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        // Api Report list
                        LaunchedEffect(true) { G.apiReportVM.reloadDb(ctx) }
                        ApiHeader(G.apiReportVM, ApiReportPresets)
                        AnimatedVisibleV(!G.apiReportVM.listCollapsed.value) {
                            ApiList(G.apiReportVM)
                        }
                    }
                }

                // Automation
                Section(
                    title = Str(R.string.automation),
                    horizontalPadding = 8
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        // Bot list
                        LaunchedEffect(true) { G.botVM.reload(ctx) }
                        BotHeader(G.botVM)
                        AnimatedVisibleV(!G.botVM.listCollapsed.value) {
                            BotList()
                        }
                    }
                }

                // Miscellaneous
                Section(
                    title = Str(R.string.miscellaneous),
                    horizontalPadding = 8
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Language()
                        Theme()
                        BackupRestore()
                        SettingRow {
                            RowVCenterSpaced(8) {
                                FAQ()
                                About()
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SettingRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    RowVCenter(
        modifier = modifier
            .heightIn(min = SettingRowMinHeight.dp)
            .padding(vertical = 2.dp)
    ) {
        content()
    }
}

@Composable
fun SettingLabel(
    labelId: Int,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Text(
        text = stringResource(id = labelId),
        modifier = modifier,
        maxLines = 1,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = color ?: G.palette.infoBlue,
    )
}

// This is used in SettingScreen
@Composable
fun LabeledRow(
    labelId: Int?,
    modifier: Modifier = Modifier,
    color: Color? = null,

    // Padding indentation for labels in Section Group
    paddingHorizontal: Int = 0,

    // Show the question icon or not
    helpTooltip: String? = null,

    // Show a down arrow to indicate the content below is collapsed
    // - null: it's not collapsable
    // - true/false: if it's collapsed or not
    isCollapsed: Boolean? = false,
    toggleCollapse: Lambda? = null,

    // Items on the right side, e.g.: "New" button
    content: @Composable RowScope.() -> Unit,
) {

    SettingRow(
        modifier = modifier
            .clickable {
                // 1. expand/collapse
                if (toggleCollapse != null)
                    toggleCollapse()
            }
            .padding(horizontal = paddingHorizontal.dp),
    ) {
        RowVCenterSpaced(
            space = 2,
            modifier = M.wrapContentWidth()
        ) {
            // label
            if (labelId != null) {
                SettingLabel(
                    labelId,
                    color = color
                )
            }

            // collapsed indicator
            if (isCollapsed == true) {
                GreyIcon16(
                    iconId = R.drawable.ic_dropdown_arrow,
                )
            }

            // balloon tooltip
            helpTooltip?.let {
                BalloonQuestionMark(it)
            }

            // rest content
            RowVCenter(
                modifier = M.weight(1f),
                horizontalArrangement = Arrangement.End,
            ) {
                content()
            }
        }
    }
}

