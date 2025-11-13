package spam.blocker.ui.history

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.SpamTable
import spam.blocker.def.Def
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTENT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_NUMBER
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTENT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER
import spam.blocker.def.Def.RESULT_BLOCKED_BY_SPAM_DB
import spam.blocker.service.checker.ByRegexRule
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.IChecker
import spam.blocker.service.checker.toChecker
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.util.spf

data class Indicator(
    val type: Int, // same as CheckResult.type, e.g.: RESULT_BLOCKED_BY_SPAM_DB
    val priority: Int, // 0 for spamDB, or rule.priority
)
typealias Indicators = List<Indicator>

@Composable
fun IndicatorIcons(indicators: Indicators) {
    val C = LocalPalette.current

    RowVCenterSpaced(2) {
        // Db existence indicator
        indicators.sortedByDescending { it.priority }.forEach {
            when (it.type) {
                RESULT_BLOCKED_BY_SPAM_DB -> {
                    ResIcon(R.drawable.ic_db_delete, modifier = M.size(16.dp), color = C.block)
                }

                RESULT_ALLOWED_BY_NUMBER -> {
                    ResIcon(R.drawable.ic_number_sign, modifier = M.size(16.dp), color = C.pass)
                }

                RESULT_BLOCKED_BY_NUMBER -> {
                    ResIcon(R.drawable.ic_number_sign, modifier = M.size(16.dp), color = C.block)
                }

                RESULT_ALLOWED_BY_CONTENT -> {
                    ResIcon(R.drawable.ic_sms_pass, modifier = M.size(14.dp), color = C.pass)
                }

                RESULT_BLOCKED_BY_CONTENT -> {
                    ResIcon(R.drawable.ic_sms_blocked, modifier = M.size(14.dp), color = C.block)
                }
            }
        }
    }
}

// Keep track of:
//  1. number's existence in the spam database.
//  2. number matches any regex rule
//  3. sms content matches regex rule
// and show red/green indicators(icons) before the number
@Composable
fun IndicatorsWrapper(
    vm: HistoryViewModel,
    content: @Composable (
            (number: String, smsContent: String?) -> Indicators,
            Boolean,
    ) -> Unit,
) {
    val ctx = LocalContext.current

    // Just a short alias, that G.xxx it too long
    var showIndicator by remember(G.showHistoryIndicator.value) {
        mutableStateOf(G.showHistoryIndicator.value)
    }

    var onRefresh by remember { mutableStateOf(false) }

    // load from tables and generate ICheckers
    fun loadNumberCheckers(): List<IChecker> {
        return if (showIndicator) {
            NumberRuleTable().listAll(ctx).map {
                it.toChecker(ctx)
            }
        } else listOf()
    }

    fun loadContentCheckers(): List<IChecker> {
        return if (showIndicator && vm.forType == Def.ForSms) {
            ContentRuleTable().listAll(ctx).map {
                Checker.Content(ctx, it)
            }
        } else listOf()
    }

    var numberCheckers = remember(showIndicator) { loadNumberCheckers() }
    var contentCheckers = remember(showIndicator) { loadContentCheckers() }

    // Refresh the list on regex change or spam db change
    Events.regexRuleUpdated.Listen {
        numberCheckers = loadNumberCheckers()
        contentCheckers = loadContentCheckers()
        onRefresh = !onRefresh // refresh all records
    }
    Events.spamDbUpdated.Listen {
        onRefresh = !onRefresh // refresh all records
    }

    fun check(number: String, smsContent: String?): Indicators {
        return buildList {
            // 1. Number exist in spam db?
            run {
                if (SpamTable.findByNumber(ctx, number) != null) {
                    add(
                        Indicator(type = RESULT_BLOCKED_BY_SPAM_DB, priority = spf.SpamDB(ctx).getPriority())
                    )
                }
            }

            if (vm.forType == Def.ForNumber) { // in Call Tab
                // 2. Check if the call number matches any Number Rule?
                run {
                    val checkResult = Checker.checkCallWithCheckers(
                        ctx = ctx,
                        logger = null,
                        rawNumber = number,
                        checkers = numberCheckers,
                    )
                    val resultType = checkResult.type

                    when (resultType) {
                        RESULT_ALLOWED_BY_NUMBER, RESULT_BLOCKED_BY_NUMBER -> {
                            add(
                                Indicator(
                                    type = resultType,
                                    priority = (checkResult as ByRegexRule).rule?.priority
                                        ?: -1, // -1: rule deleted
                                )
                            )
                        }
                    }
                }
            } else { // in SMS Tab

                // 2. Check if the sms number matches any Number Rule?
                run {
                    val checkResult = Checker.checkSmsWithCheckers(
                        ctx = ctx,
                        logger = null,
                        rawNumber = number,
                        messageBody = smsContent ?: "",
                        checkers = numberCheckers,
                    )
                    val resultType = checkResult.type

                    when (resultType) {
                        RESULT_ALLOWED_BY_NUMBER, RESULT_BLOCKED_BY_NUMBER -> {
                            add(
                                Indicator(
                                    type = resultType,
                                    priority = (checkResult as ByRegexRule).rule?.priority
                                        ?: -1, // -1: rule deleted
                                )
                            )
                        }
                    }
                }
                // 3. Check if the SMS matches any Content Rule?
                run {
                    val checkResult = Checker.checkSmsWithCheckers(
                        ctx = ctx,
                        logger = null,
                        rawNumber = number,
                        messageBody = smsContent ?: "",
                        checkers = contentCheckers,
                    )
                    val resultType = checkResult.type

                    when (checkResult.type) {
                        RESULT_ALLOWED_BY_CONTENT, RESULT_BLOCKED_BY_CONTENT -> {
                            add(
                                Indicator(
                                    type = resultType,
                                    priority = (checkResult as ByRegexRule).rule?.priority
                                        ?: -1, // -1: rule deleted
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    content(::check, onRefresh)
}
