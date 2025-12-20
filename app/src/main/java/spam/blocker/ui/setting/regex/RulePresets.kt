package spam.blocker.ui.setting.regex

import android.content.Context
import android.os.Build
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Notification
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.def.Def.ANDROID_12
import spam.blocker.util.Lambda
import spam.blocker.util.Lambda1
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.SimUtils


class RulePreset(
    val label: (Context) -> String,
    val tooltip: (Context) -> String,
    // Check permission, Android version, SIM count, etc..
    val preCheck: (ctx: Context, onSuccess: Lambda, onFail: Lambda1<String>) -> Unit = { ctx, onSuccess, onFail -> onSuccess() },
    val newInstance: (Context) -> List<RegexRule>,
)


val RegexRulePresets = mapOf(
    Def.ForNumber to listOf(
        RulePreset(
            label = { it.getString(R.string.private_number) },
            tooltip = {
                it.getString(R.string.help_private_number)
            },
        ) { ctx ->
            listOf(
                RegexRule(
                    pattern = "",
                    description = ctx.getString(R.string.private_number),
                    priority = 20,
                    isBlacklist = true,
                )
            )
        },
        RulePreset(
            label = { it.getString(R.string.foreign_number) },
            tooltip = {
                it.getString(R.string.help_foreign_number)
            },
        ) { ctx ->
            listOf(
                RegexRule(
                    pattern = "\\+(?!39).*",
                    description = ctx.getString(R.string.foreign_number),
                    priority = 20,
                    isBlacklist = true,
                    patternFlags = Def.FLAG_REGEX_RAW_NUMBER,
                )
            )
        },
        RulePreset(
            label = { it.getString(R.string.sim_2_contacts) },
            tooltip = {
                it.getString(R.string.help_sim_2_contacts)
            },
            preCheck = { ctx, onSuccess, onFail ->
                // 1. check Android version
                if (Build.VERSION.SDK_INT < ANDROID_12) {
                    onFail(ctx.getString(R.string.requires_min_android_ver).format("12"))
                    return@RulePreset
                }
                // 2. check sim count, must > 1
                val simCount = SimUtils.simCount(ctx)
                if (simCount < 2) {
                    onFail(ctx.getString(R.string.detected_sim_count).format("$simCount"))
                    return@RulePreset
                }
                // 3. check permission: contacts+phoneState
                G.permissionChain.ask(
                    ctx,
                    listOf(
                        PermissionWrapper(Permission.contacts),
                        PermissionWrapper(Permission.phoneState),
                    )
                ) { granted ->
                    if (granted) {
                        onSuccess()
                    }
                }
            }
        ) { ctx ->
            listOf(
                RegexRule(
                    pattern = ".*",
                    description = ctx.getString(R.string.sim_2_contacts),
                    priority = 12,
                    isBlacklist = false,
                    simSlot = 1,
                    patternFlags = Def.FLAG_REGEX_FOR_CONTACT,
                ),
                RegexRule(
                    pattern = ".*",
                    description = ctx.getString(R.string.sim_2_non_contacts),
                    priority = 11,
                    simSlot = 1,
                    isBlacklist = true,
                )
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
            listOf(
                RegexRule(
                    pattern = ctx.getString(R.string.verification_code_regex),
                    description = ctx.getString(R.string.verification_code),
                    priority = 1,
                    isBlacklist = false,
                    channel = Notification.CHANNEL_HIGH
                )
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
            listOf(
                RegexRule(
                    pattern = ctx.getString(R.string.copy_verification_code_regex),
                    flags = Def.FLAG_FOR_CONTENT or Def.FLAG_FOR_SMS or Def.FLAG_FOR_PASSED
                )
            )
        },
    )
)
