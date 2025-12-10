package spam.blocker.ui.setting.regex

import android.content.Context
import androidx.compose.runtime.Composable
import spam.blocker.R
import spam.blocker.db.Notification
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.ui.widgets.GreyText


class RulePreset(
    val label: (Context) -> String,
    val tooltip: (Context) -> String,
    val newInstance: (Context) -> RegexRule,
)


val RegexRulePresets = mapOf(
    Def.ForNumber to listOf(
        RulePreset(
            label = { it.getString(R.string.private_number) },
            tooltip = {
                it.getString(R.string.help_private_number)
            },
        ) { ctx ->
            RegexRule(
                pattern = "",
                description = ctx.getString(R.string.private_number),
                priority = 20,
                isBlacklist = true,
            )
        },
        RulePreset(
            label = { it.getString(R.string.foreign_number) },
            tooltip = {
                it.getString(R.string.help_foreign_number)
            },
        ) { ctx ->
            RegexRule(
                pattern = "\\+(?!39).*",
                description = ctx.getString(R.string.foreign_number),
                priority = 20,
                isBlacklist = true,
                patternFlags = Def.FLAG_REGEX_RAW_NUMBER,
            )
        },
    ),
    Def.ForSms to listOf(
        RulePreset(
            label = { it.getString(R.string.verification_code) },
            tooltip = {
                it.getString(R.string.help_allow_verification_code)
            },
        ) { ctx ->
            RegexRule(
                pattern = ctx.getString(R.string.verification_code_regex),
                description = ctx.getString(R.string.verification_code),
                priority = 1,
                isBlacklist = false,
                channel = Notification.CHANNEL_HIGH
            )
        },
    ),
    Def.ForQuickCopy to listOf(
        RulePreset(
            label = { it.getString(R.string.verification_code) },
            tooltip = {
                it.getString(R.string.help_copy_verification_code)
            },
        ) { ctx ->
            RegexRule(
                pattern = ctx.getString(R.string.copy_verification_code_regex),
                flags = Def.FLAG_FOR_CONTENT or Def.FLAG_FOR_SMS or Def.FLAG_FOR_PASSED
            )
        },
    )
)
