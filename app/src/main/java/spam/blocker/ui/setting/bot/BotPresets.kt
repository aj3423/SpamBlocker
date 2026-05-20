package spam.blocker.ui.setting.bot

import android.content.Context
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.ImportDbReason
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.RegexRule
import spam.blocker.service.bot.BackupExport
import spam.blocker.service.bot.BackupImport
import spam.blocker.service.bot.CalendarEvent
import spam.blocker.service.bot.CallThrottling
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.FindRules
import spam.blocker.service.bot.GenerateTag
import spam.blocker.service.bot.HttpRequest
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.LoadBotTag
import spam.blocker.service.bot.ModifyRules
import spam.blocker.service.bot.ParseCSV
import spam.blocker.service.bot.ParseXML
import spam.blocker.service.bot.Periodically
import spam.blocker.service.bot.QuickTile
import spam.blocker.service.bot.Ringtone
import spam.blocker.service.bot.SaveBotTag
import spam.blocker.service.bot.Schedule
import spam.blocker.service.bot.SetTag
import spam.blocker.service.bot.SmsThrottling
import spam.blocker.service.bot.Time
import spam.blocker.service.bot.Weekly
import spam.blocker.service.bot.WriteFile
import spam.blocker.ui.setting.api.ApiSetupDialog
import spam.blocker.ui.setting.api.OAuthSetupDialog
import spam.blocker.ui.setting.api.PhoneBlock
import spam.blocker.util.Lambda1
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import java.time.DayOfWeek.MONDAY

class BotPreset(
    val descId: Int,
    val tooltip: (Context)->String,

    // While `actions.requiredPermissions()` is the ideal way for checking permissions, this approach is easier to implement.
    val requiredPermissions: List<PermissionWrapper> = listOf(),

    // Either of these must exist
    val setupDialog: ApiSetupDialog? = null,
    val doAdd: Lambda1<Context>? = null,
)


val BotPresets = listOf(
    // PhoneBlock offline numbers
    BotPreset(
        descId = R.string.bot_preset_phoneblock,
        tooltip = {
            it.getString(R.string.help_bot_preset_phoneblock).format(
                "<a href=\"https://phoneblock.net\">https://phoneblock.net</a>"
            )
        },
        setupDialog = OAuthSetupDialog(
            spfTokenKey = PhoneBlock.spfTokenKey,
            oauthUrl = if (BuildConfig.DEBUG) PhoneBlock.oauthUrl_Debug else PhoneBlock.oauthUrl,
            doAdd = { ctx ->
                val newBot = Bot(
                    desc = ctx.getString(R.string.bot_preset_phoneblock),
                    trigger = Schedule(
                        schedule = Periodically(Time(hour = 23, min = 50)),
                    ),
                    actions = listOf(
                        LoadBotTag(tagName = "version", defaultValue = "0"),
                        HttpRequest(
                            url = if (BuildConfig.DEBUG)
                                "https://phoneblock.net/pb-test/api/blocklist?format=xml&since={version}"
                            else
                                "https://phoneblock.net/phoneblock/api/blocklist?format=xml&since={version}",
                            header = "{bearer_auth({shared_pref(${PhoneBlock.spfTokenKey})})}",
                        ),
                        GenerateTag(
                            tagName = "version",
                            parseType = 1, // 1 == xpath
                            xpath = "/blocklist/@version",
                        ),
                        ParseXML(xpath = "//phone-info[@votes > 4]/@phone"),
                        ImportToSpamDB(importReason = ImportDbReason.ByAPI),
                        SaveBotTag(tagName = "version"),
                    )
                )
                addBotToDB(ctx, newBot)
            }
        ),
    ),
    // FTC Do Not Call
    BotPreset(
        descId = R.string.bot_preset_dnc,
        tooltip = {
            it.getString(R.string.help_bot_preset_dnc)
                .format(
                    "https://github.com/aj3423/DNC_snapshot",
                    "<a href=\"https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data\">FTC - Do Not Call</a>"
                )
        },
        doAdd = { ctx ->
            val newBot = Bot(
                desc = ctx.getString(R.string.bot_preset_dnc),
                trigger = Schedule(schedule = Daily(Time(hour = 18))),
                actions = listOf(
                    LoadBotTag(tagName = "csv_name", defaultValue = "90days"),
                    HttpRequest(url = "https://raw.githubusercontent.com/aj3423/DNC_snapshot/refs/heads/master/{csv_name}.csv"),
                    ParseCSV(),
                    // no need to add ClearNumber here
                    ImportToSpamDB(),
                    SetTag(tagName = "csv_name", tagValue = "daily"),
                    SaveBotTag(tagName = "csv_name"),
                )
            )
            addBotToDB(ctx, newBot)
        }
    ),

    // Ringtone <-> Regex Rule
    BotPreset(
        descId = R.string.ringtone,
        tooltip = { it.getString(R.string.help_preset_ringtone) },
        requiredPermissions = listOf(PermissionWrapper(Permission.writeSettings)),
        doAdd = { ctx ->
            val newBot = Bot(
                desc = ctx.getString(R.string.ringtone),
                trigger = Ringtone(bindTo = "{ \"regex\": \"${ctx.getString(R.string.replace_this)}\" }"),
            )
            addBotToDB(ctx, newBot)
        }
    ),

    // Tile Switch
    BotPreset(
        descId = R.string.custom_tile,
        tooltip = { it.getString(R.string.help_custom_tile) },
        doAdd = { ctx ->
            val newBot = Bot(
                desc = ctx.getString(R.string.custom_tile),
                trigger = QuickTile(),
                actions = listOf(
                    FindRules(pattern = ctx.getString(R.string.toggled_by_tile)),
                    ModifyRules(config = "{\"flags\": 3}"),
                )
            )
            addBotToDB(ctx, newBot)

            // Add a regex rule
            NumberRegexTable().addNewRule(ctx, RegexRule(
                pattern = ".*",
                description = ctx.getString(R.string.toggled_by_tile),
                flags = 0, // disabled for call/sms
                priority = 100,
            ))
            G.NumberRuleVM.reloadDb(ctx)
        },
    ),

    // Calendar Event
    BotPreset(
        descId = R.string.calendar_event,
        tooltip = { it.getString(R.string.help_calendar_event_template) },
        requiredPermissions = listOf(PermissionWrapper(Permission.calendar)),
        doAdd = { ctx ->
            val ruleDesc = ctx.getString(R.string.calendar_event)
            val newBot = Bot(
                desc = ctx.getString(R.string.calendar_event),
                trigger = CalendarEvent(eventTitle = ctx.getString(R.string.working)),
                actions = listOf(
                    FindRules(pattern = ruleDesc),
                    ModifyRules(config = "{\"flags\": 3}"),
                )
            )
            addBotToDB(ctx, newBot)

            // Add a regex rule
            NumberRegexTable().addNewRule(ctx, RegexRule(
                pattern = ".*",
                description = ruleDesc,
                flags = 0, // disabled for call/sms
            ))
            G.NumberRuleVM.reloadDb(ctx)
        },
    ),

    // Call Throttling
    BotPreset(
        descId = R.string.throttled_call,
        tooltip = { it.getString(R.string.help_call_throttling_template) },
        requiredPermissions = listOf(PermissionWrapper(Permission.callLog)),
        doAdd = { ctx ->
            val ruleDesc = ctx.getString(R.string.throttled_call)
            val newBot = Bot(
                desc = ctx.getString(R.string.call_throttling),
                trigger = CallThrottling(
                    includingBlocked = true,
                    includingAnswered = true,
                ),
                actions = listOf(
                    FindRules(pattern = ruleDesc),
                    ModifyRules(config = "{\"flags\": 1}"),
                )
            )
            addBotToDB(ctx, newBot)

            // Add a regex rule
            NumberRegexTable().addNewRule(ctx, RegexRule(
                pattern = ".*",
                description = ruleDesc,
                flags = 0,
            ))
            G.NumberRuleVM.reloadDb(ctx)
        },
    ),

    // SMS Throttling
    BotPreset(
        descId = R.string.throttled_sms,
        tooltip = { it.getString(R.string.help_sms_throttling_template) },
        requiredPermissions = listOf(PermissionWrapper(Permission.readSMS)),
        doAdd = { ctx ->
            val ruleDesc = ctx.getString(R.string.throttled_sms)
            val newBot = Bot(
                desc = ctx.getString(R.string.sms_throttling),
                trigger = SmsThrottling(targetRuleDesc = ruleDesc),
                actions = listOf(
                    FindRules(pattern = ruleDesc),
                    ModifyRules(config = "{\"flags\": 2}"),
                )
            )
            addBotToDB(ctx, newBot)

            // Add a regex rule
            NumberRegexTable().addNewRule(ctx, RegexRule(
                pattern = ".*",
                description = ruleDesc,
                flags = 0,
            ))
            G.NumberRuleVM.reloadDb(ctx)
        },
    ),

    // Remote Setup
    BotPreset(
        descId = R.string.remote_setup,
        tooltip = { it.getString(R.string.help_remote_setup) },
        doAdd = { ctx ->
            val newBot = Bot(
                desc = ctx.getString(R.string.remote_setup),
                trigger = Schedule(
                    enabled = true,
                    schedule = Daily(
                        time = Time(0, 0)
                    )
                ),
                actions = listOf(
                    HttpRequest(url = ctx.getString(R.string.replace_this)),
                    BackupImport(),
                )
            )
            addBotToDB(ctx, newBot)
        }
    ),

    // Auto Backup on Monday midnight
    BotPreset(
        descId = R.string.bot_preset_auto_backup,
        tooltip = { it.getString(R.string.help_bot_preset_auto_backup) },
        doAdd = { ctx ->
            val newBot = Bot(
                desc = ctx.getString(R.string.bot_preset_auto_backup),
                trigger = Schedule(
                    enabled = true,
                    schedule = Weekly(
                        weekdays = listOf(MONDAY),
                        time = Time(0, 0)
                    ),
                ),
                actions = listOf(
                    BackupExport(),
                    WriteFile(filename = "SpamBlocker.auto.gz")
                )
            )
            addBotToDB(ctx, newBot)
        }
    )
)
