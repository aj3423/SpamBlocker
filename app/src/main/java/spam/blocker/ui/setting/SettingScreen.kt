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
import spam.blocker.ui.setting.regex.RuleSearchBox
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.White
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
                            LaunchedEffect(true) { G.NumberRuleVM.reload(ctx) }
                            RuleHeader(Def.ForNumber, G.NumberRuleVM)
                            RuleSearchBox(G.NumberRuleVM)
                            RuleList(Def.ForNumber, G.NumberRuleVM)

                            // Content Rules
                            LaunchedEffect(true) { G.ContentRuleVM.reload(ctx) }
                            RuleHeader(Def.ForSms, G.ContentRuleVM)
                            RuleSearchBox(G.ContentRuleVM)
                            RuleList(Def.ForSms, G.ContentRuleVM)

                            // QuickCopy Rules
                            LaunchedEffect(true) { G.QuickCopyRuleVM.reload(ctx) }
                            RuleHeader(Def.ForQuickCopy, G.QuickCopyRuleVM)
                            RuleSearchBox(G.QuickCopyRuleVM)
                            RuleList(Def.ForQuickCopy, G.QuickCopyRuleVM)
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
