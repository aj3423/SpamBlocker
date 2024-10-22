package spam.blocker.ui.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.bot.BotHeader
import spam.blocker.ui.setting.bot.BotList
import spam.blocker.ui.setting.misc.About
import spam.blocker.ui.setting.misc.BackupRestore
import spam.blocker.ui.setting.misc.Language
import spam.blocker.ui.setting.misc.Theme
import spam.blocker.ui.setting.quick.BlockType
import spam.blocker.ui.setting.quick.Contacts
import spam.blocker.ui.setting.quick.Dialed
import spam.blocker.ui.setting.quick.OffTime
import spam.blocker.ui.setting.quick.RecentApps
import spam.blocker.ui.setting.quick.RepeatedCall
import spam.blocker.ui.setting.quick.SpamDB
import spam.blocker.ui.setting.quick.Stir
import spam.blocker.ui.setting.regex.RuleHeader
import spam.blocker.ui.setting.regex.RuleList
import spam.blocker.ui.setting.regex.RuleSearchBox
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.White
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.ui.widgets.NormalColumnScrollbar
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str

const val SettingRowMinHeight = 40

@Composable
fun SettingScreen() {
    val ctx = LocalContext.current

    val testingTrigger = rememberSaveable { mutableStateOf(false) }
    PopupTesting(testingTrigger)

    // Hide FAB when scrolled to the bottom
    val scrollState = rememberScrollState()
    val bottomReached by remember {
        derivedStateOf {
            scrollState.maxValue > 0 && scrollState.value == scrollState.maxValue
        }
    }

    FabWrapper(
        fabRow = { positionModifier ->
            Fab(
                visible = !bottomReached,
                iconId = R.drawable.ic_tube,
                iconColor = White,
                bgColor = SkyBlue,
                modifier = positionModifier
            ) {
                testingTrigger.value = true
            }
        }
    ) {

        val focusManager = LocalFocusManager.current

        NormalColumnScrollbar(state = scrollState) {
            Column(
                modifier = M
                    .verticalScroll(scrollState)
                    .padding(top = 8.dp)

                    // For hiding the RuleSearchBox when clicking around
                    .clickable(
                        // Disable the clicking ripple effect
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // The RuleSearchBox will be hidden when it loses focus
                        focusManager.clearFocus(true)
                    }
            ) {
                // global
                GloballyEnabled()

                // quick settings
                if (G.globallyEnabled.value) {
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
                            RecentApps()
                            OffTime()
                            BlockType()
                        }
                    }
                    Section(
                        title = Str(R.string.regex_settings),
                        horizontalPadding = 8
                    ) {
                        Column {
                            // Number Rules
                            val vm1 = G.NumberRuleVM
                            LaunchedEffect(true) { vm1.reloadDbAndOptions(ctx) }

                            RuleHeader(vm1)
                            AnimatedVisibleV(!vm1.listCollapsed.value) {
                                Column {
                                    RuleSearchBox(vm1)
                                    RuleList(vm1)
                                }
                            }

                            // Content Rules
                            val vm2 = G.ContentRuleVM
                            LaunchedEffect(true) { vm2.reloadDbAndOptions(ctx) }
                            RuleHeader(vm2)
                            AnimatedVisibleV(!vm2.listCollapsed.value) {
                                Column {
                                    RuleSearchBox(vm2)
                                    RuleList(vm2)
                                }
                            }

                            // QuickCopy Rules
                            val vm3 = G.QuickCopyRuleVM
                            LaunchedEffect(true) { vm3.reloadDbAndOptions(ctx) }
                            RuleHeader(vm3)
                            AnimatedVisibleV(!vm3.listCollapsed.value) {
                                Column {
                                    RuleSearchBox(vm3)
                                    RuleList(vm3)
                                }
                            }
                        }
                    }
                }
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
                Section(
                    title = Str(R.string.miscellaneous),
                    horizontalPadding = 8
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Theme()
                        Language()
                        BackupRestore()
                        About()
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
        modifier = modifier.heightIn(min = SettingRowMinHeight.dp),
    ) {
        content()
    }
}

@Composable
fun SettingLabel(
    labelId: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(id = labelId),
        modifier = modifier,
        maxLines = 1,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = SkyBlue,
    )
}

@Composable
fun LabeledRow(
    label: @Composable () -> Unit,

    // optional
    modifier: Modifier = Modifier,
    paddingHorizontal: Int = 0,
    helpTooltipId: Int? = null,
    content: @Composable RowScope.() -> Unit,
) {
    SettingRow(
        modifier = modifier.padding(horizontal = paddingHorizontal.dp),
    ) {
        Row(
            modifier = M.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // label
            label()

            // balloon tooltip
            if (helpTooltipId != null)
                BalloonQuestionMark(Str(helpTooltipId))

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

@Composable
fun LabeledRow(
    labelId: Int,

    // optional
    modifier: Modifier = Modifier,
    paddingHorizontal: Int = 0,
    helpTooltipId: Int? = null,
    content: @Composable RowScope.() -> Unit,
) {
    LabeledRow(
        label = {
            SettingLabel(labelId)
        },
        modifier = modifier,
        paddingHorizontal = paddingHorizontal,
        helpTooltipId = helpTooltipId,
        content = content,
    )
}
