package spam.blocker.ui.setting.regex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.db.ruleTableForType
import spam.blocker.def.Def.ForNumber
import spam.blocker.def.Def.ForQuickCopy
import spam.blocker.def.Def.ForSms
import spam.blocker.ui.M
import spam.blocker.ui.screenHeightDp
import spam.blocker.ui.slightDiff
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.ColumnSpaced
import spam.blocker.ui.widgets.Placeholder
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.ResIcon20
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.simpleLazyScrollbar
import spam.blocker.util.Lambda1
import spam.blocker.util.regexMatches


private class SelectionWrapper(
    val forType: Int,
    val rule: RegexRule
)
private fun pattern_2_descs(pattern: String) : List<String> {
    val ps = pattern.trim()
    return if (ps.isEmpty()) { // empty
        listOf()
    } else if (ps.startsWith("(") && ps.endsWith(")")) { // multiple strings (a|b)
        ps.removeSurrounding("(", ")").split("|")
    } else { // the whole string is a regex
        listOf(ps)
    }
}
private fun descs_2_pattern(descs: List<String>, defaultPattern: String) : String {
    return if (descs.isEmpty()) {
        defaultPattern
    } else if(descs.size == 1) {
        descs[0]
    } else {
        "(${descs.joinToString("|")})"
    }
}

// Select regex by desc, for binding   Workflows <-> RegexRules.
@Composable
fun RegexSelectionList(
    pattern: String,
//    singleSelect: Boolean = false,
    defaultPattern: String = ".*",
    onPatternChange: Lambda1<String> // e.g.  (a|b|c)
) {
    val ctx = LocalContext.current
    val C = G.palette

    val targetDescs = remember(pattern) {
        mutableStateListOf(
            *pattern_2_descs(pattern).toTypedArray()
        )
    }

    val recs = remember {
        listOf(ForNumber, ForSms, ForQuickCopy)
            .flatMap { forType ->
                ruleTableForType(forType).listAll(ctx)
                    .map { SelectionWrapper(forType = forType, rule = it) }
            }
            .filter { it.rule.description.isNotEmpty() }
    }

    val lazyState = rememberLazyListState()
    val percentage = 60 // Calculate x% of the screen height

    LazyColumn(
        state = lazyState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = M
            .heightIn(max = (screenHeightDp() * percentage / 100).dp)
            .simpleLazyScrollbar(lazyState)
    ) {
        items(recs, key = { "${it.forType} ${it.rule.id}" }) { wrapper ->
            Row(
                modifier = M
                    .clickable {
                        // 1. update the desc list
                        val desc = wrapper.rule.description
                        if (targetDescs.size == 1 && targetDescs[0] == defaultPattern) { // if it's List(".*")
                            targetDescs.clear()
                            targetDescs += desc
                        } else if (targetDescs.contains(desc)) {
                            targetDescs -= desc
                        } else {
                            targetDescs += desc
                        }
                        if (targetDescs.isEmpty()) {
                            targetDescs += defaultPattern
                        }

                        // 2. rebuild the pattern string from the list
                        val newPattern = descs_2_pattern(targetDescs, defaultPattern)

                        onPatternChange(newPattern)
                    }
            ) {
                RegexCard(
                    rule = wrapper.rule, forType = wrapper.forType, containerBg = C.dialogBg,
                    borderColor = if (targetDescs.any { it.regexMatches(wrapper.rule.description) }) {
                        C.teal200
                    } else {
                        C.dialogBg.slightDiff()
                    }
                )
            }
        }
    }
}

@Composable
fun RegexRuleFilterField(
    pattern: String,
    defaultPattern: String = ".*",
    helpTooltipId: Int? = R.string.help_target_rule_desc,
    onPatternChange: Lambda1<String>,
) {
    val C = G.palette

    var patternState by remember { mutableStateOf(pattern) }

    var showList by remember { mutableStateOf(false) }

    ColumnSpaced(10) {
        // Regex field
        RegexInputBox(
            label = { Text(Str(R.string.target_rule_desc)) },
            helpTooltipId = helpTooltipId,
            leadingIcon = {
                Box {
                    ResIcon20(
                        iconId = if (showList) R.drawable.ic_dropdown_arrow else R.drawable.ic_select,
                        color = C.infoBlue,
                        modifier = M.clickable {
                            showList = !showList
                        }
                    )
                    // Footer arrow
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_dropdown_footer),
                        contentDescription = "",
                        tint = C.infoBlue,
                        modifier = Modifier
                            .size(6.dp)
                            .align(Alignment.BottomEnd)
                            .offset((4).dp, (4).dp)
                            .clickable { showList = !showList }
                    )
                }
            },
            regexStr = patternState,
            onRegexStrChange = { newVal, hasErr ->
                if (!hasErr) {
                    patternState = newVal
                    onPatternChange(newVal)
                }
            },
            placeholder = { Placeholder(defaultPattern) },
            regexFlags = remember { mutableIntStateOf(0) },
            onFlagsChange = { },
            showFlagsIcon = false
        )

        // Rule List
        AnimatedVisibleV(showList) {
            RegexSelectionList(
                pattern = patternState,
                defaultPattern = defaultPattern
            ) {
                patternState = it
                onPatternChange(it)
            }
        }
    }
}