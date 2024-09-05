package spam.blocker.ui.setting

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.def.Def
import spam.blocker.ui.M
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
import spam.blocker.ui.setting.quick.Stir
import spam.blocker.ui.setting.regex.RuleHeader
import spam.blocker.ui.setting.regex.RuleList
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.theme.White
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str

const val SettingRowMinHeight = 36

@Composable
fun SettingPage() {
    val ctx = LocalContext.current

    val testingTrigger = remember { mutableStateOf(false) }
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
                iconId = R.drawable.ic_testing,
                iconColor = White,
                bgColor = Teal200,
                modifier = positionModifier
            ) {
                testingTrigger.value = true
            }
        }
    ) {

        Column(
            modifier = M
                .verticalScroll(scrollState)
                .padding(top = 8.dp)
        ) {
            // global
            GloballyEnabled()

            // quick settings
            if (G.globallyEnabled.value) {
                Section(title = Str(R.string.quick_settings)) {
                    Column {
                        Contacts()
                        Stir()
                        RepeatedCall()
                        Dialed()
                        RecentApps()
                        OffTime()
                        BlockType()
                    }
                }
                Section(
                    title = Str(R.string.regex_settings),
                ) {
                    Column {
                        // Number Rules
                        val numberRules = remember {
                            mutableStateListOf(*NumberRuleTable().listAll(ctx).toTypedArray())
                        }

                        RuleHeader(Def.ForNumber, numberRules)
                        RuleList(Def.ForNumber, numberRules)

                        // Content Rules
                        val contentRules = remember {
                            mutableStateListOf(*ContentRuleTable().listAll(ctx).toTypedArray())
                        }
                        RuleHeader(Def.ForSms, contentRules)
                        RuleList(Def.ForSms, contentRules)

                        // QuickCopy Rules
                        val quickCopyRules = remember {
                            mutableStateListOf(*QuickCopyRuleTable().listAll(ctx).toTypedArray())
                        }
                        RuleHeader(Def.ForQuickCopy, quickCopyRules)
                        RuleList(Def.ForQuickCopy, quickCopyRules)
                    }
                }
            }
            Section(title = Str(R.string.miscellaneous)) {
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


@Composable
fun SettingRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    RowVCenter(
        modifier = modifier.heightIn(min = SettingRowMinHeight.dp),
    ) {
        content()
    }
}

@Composable
fun SettingLabel(labelId: Int) {
    Text(
        text = stringResource(id = labelId),
        maxLines = 1,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = SkyBlue,
    )
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
    SettingRow(
        modifier = modifier.padding(horizontal = paddingHorizontal.dp),
    ) {
        Row(
            modifier = M.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // label
            SettingLabel(labelId)

            // balloon tooltip
            if (helpTooltipId != null)
                BalloonQuestionMark(helpTooltipId)

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

