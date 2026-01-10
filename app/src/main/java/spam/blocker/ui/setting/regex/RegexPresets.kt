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


class RegexPreset(
    val label: (Context) -> String,
    val tooltip: (Context) -> String,
    // Check permission, Android version, SIM count, etc..
    val preCheck: (ctx: Context, onSuccess: Lambda, onFail: Lambda1<String>) -> Unit = { ctx, onSuccess, onFail -> onSuccess() },
    val newInstance: (Context) -> List<RegexRule>,
)


val RegexPresets = mapOf(
    Def.ForNumber to listOf(
        RegexPreset(
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
        RegexPreset(
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
        RegexPreset(
            label = { it.getString(R.string.sim_2_contacts) },
            tooltip = {
                it.getString(R.string.help_sim_2_contacts)
            },
            preCheck = { ctx, onSuccess, onFail ->
                // 1. check Android version
                if (Build.VERSION.SDK_INT < ANDROID_12) {
                    onFail(ctx.getString(R.string.requires_min_android_ver).format("12"))
                    return@RegexPreset
                }
                // 2. check sim count, must > 1
                val simCount = SimUtils.simCount(ctx)
                if (simCount < 2) {
                    onFail(ctx.getString(R.string.detected_sim_count).format("$simCount"))
                    return@RegexPreset
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
        RegexPreset(
            label = { it.getString(R.string.forwarded_call) },
            tooltip = {
                it.getString(R.string.help_regex_preset_forwarded_call).format(
                    it.getString(R.string.explanation_forwarded_call)
                )
            },
        ) { ctx ->
            listOf(
                RegexRule(
                    pattern = ".+&.+",
                    description = ctx.getString(R.string.forwarded_call),
                    priority = 11,
                    isBlacklist = true,
                    flags = Def.FLAG_FOR_CALL,
                )
            )
        },
        RegexPreset(
            label = { it.getString(R.string.caller_name) },
            tooltip = {
                it.getString(R.string.help_regex_preset_caller_name)
            },
        ) { ctx ->
            listOf(
                RegexRule(
                    pattern = ".+",
                    description = ctx.getString(R.string.caller_name),
                    priority = 1,
                    isBlacklist = false,
                    flags = Def.FLAG_FOR_CALL,
                    patternFlags = Def.FLAG_REGEX_FOR_CNAP,
                )
            )
        },

        RegexPreset(
            label = { it.getString(R.string.local_number) },
            tooltip = {
                it.getString(R.string.help_regex_preset_local_number)
            },
            preCheck = { ctx, onSuccess, onFail ->
                G.permissionChain.ask(
                    ctx,
                    listOf(
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
                    pattern = "(?!___$).*",
                    description = ctx.getString(R.string.non_local_number),
                    priority = 0,
                    isBlacklist = true,
                    flags = Def.FLAG_FOR_CALL or Def.FLAG_FOR_SMS,
                    patternFlags = Def.FLAG_REGEX_FOR_GEO_LOCATION,
                )
            )
        },
    ),

    Def.ForSms to listOf(
        RegexPreset(
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
        RegexPreset(
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
