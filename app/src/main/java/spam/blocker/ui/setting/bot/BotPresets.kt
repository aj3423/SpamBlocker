package spam.blocker.ui.setting.bot

import android.content.Context
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.IApi
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
import spam.blocker.service.bot.Manual
import spam.blocker.service.bot.ModifyRules
import spam.blocker.service.bot.ParseCSV
import spam.blocker.service.bot.ParseXML
import spam.blocker.service.bot.Periodically
import spam.blocker.service.bot.QuickTile
import spam.blocker.service.bot.Ringtone
import spam.blocker.service.bot.SaveBotTag
import spam.blocker.service.bot.Schedule
import spam.blocker.service.bot.SmsThrottling
import spam.blocker.service.bot.Time
import spam.blocker.service.bot.Weekly
import spam.blocker.service.bot.WriteFile
import spam.blocker.ui.setting.api.ApiReportPreset_PhoneBlock
import spam.blocker.ui.setting.api.AuthConfig
import spam.blocker.ui.setting.api.authConfig_PhoneBlock
import spam.blocker.util.Lambda1
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

class BotPreset(
    val tooltip: (Context)->String,
    val newInstance: (Context) -> Bot,
    val afterCreated: Lambda1<Context>? = null,
    // APIs like PhoneBlock support both query/report.
    // When creating the query API, also create the report API to avoid asking for API key twice.
    val newReportApi: ((Context) -> IApi)? = null,
    // Show a dialog for inputting authorization information(API_TOKEN/Username/Password).
    val newAuthConfig: (() -> AuthConfig)? = null,
)


val BotPresets = listOf(
    // PhoneBlock offline numbers
    BotPreset(
        tooltip = { it.getString(R.string.help_bot_preset_phoneblock) },
        newInstance = { ctx ->
            Bot(
                desc = ctx.getString(R.string.bot_preset_phoneblock),
                trigger = Schedule(
                    schedule = Periodically(Time(hour = 23)),
                ),
                actions = listOf(
                    LoadBotTag(tagName = "version", defaultValue = "0"),
                    HttpRequest(
                        url = if (BuildConfig.DEBUG)
                            "https://phoneblock.net/pb-test/api/blocklist?format=xml&since={version}"
                        else
                            "https://phoneblock.net/phoneblock/api/blocklist?format=xml&since={version}",
                        header = "{bearer_auth({api_key})}",
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
        },
        newAuthConfig = { authConfig_PhoneBlock.copy() },
        newReportApi = { ctx ->
            ApiReportPreset_PhoneBlock.newInstance(ctx)
        }
    ),
    // FTC Do Not Call, initial database
    BotPreset(
        tooltip = {
            it.getString(R.string.help_bot_preset_dnc_initial)
                .format(
                    "https://github.com/aj3423/DNC_snapshot"
                )
        },
        newInstance = { ctx ->
            Bot(
                desc = ctx.getString(R.string.bot_preset_dnc_initial),
                trigger = Manual(),
                actions = listOf(
                    HttpRequest(url = "https://raw.githubusercontent.com/aj3423/DNC_snapshot/refs/heads/master/90days.csv"),
                    ParseCSV(),
                    // no need to add ClearNumber here
                    ImportToSpamDB(),
                )
            )
        }
    ),

    // FTC Do Not Call, updated on Monday~Friday @ 18:00:00
    BotPreset(
        tooltip = { it.getString(R.string.help_bot_preset_dnc_daily) },
        newInstance = { ctx ->
            Bot(
                desc = ctx.getString(R.string.bot_preset_dnc_daily),
                trigger = Schedule(
                    enabled = true,
                    schedule = Weekly(
                        weekdays = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
                        time = Time(18, 0)
                    ),
                ),
                actions = listOf(
                    HttpRequest(url = "https://www.ftc.gov/sites/default/files/DNC_Complaint_Numbers_{year}-{month}-{day}.csv"),
                    ParseCSV(columnMapping = "{'Company_Phone_Number': 'pattern'}"),
                    // no need to add ClearNumber here
                    ImportToSpamDB(),
                )
            )
        }
    ),

    // Ringtone <-> Regex Rule
    BotPreset(
        tooltip = { it.getString(R.string.help_preset_ringtone) },
        newInstance = { ctx ->
            Bot(
                desc = ctx.getString(R.string.ringtone),
                trigger = Ringtone(bindTo = "{ \"regex\": \"${ctx.getString(R.string.replace_this)}\" }"),
            )
        }
    ),

    // Tile Switch
    BotPreset(
        tooltip = { it.getString(R.string.help_custom_tile) },
        newInstance = { ctx ->
            Bot(
                desc = ctx.getString(R.string.custom_tile),
                trigger = QuickTile(),
                actions = listOf(
                    FindRules(pattern = ctx.getString(R.string.toggled_by_tile)),
                    ModifyRules(config = "{\"flags\": 3}"),
                )
            )
        },
        afterCreated = { ctx ->
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
        tooltip = { it.getString(R.string.help_calendar_event_template) },
        newInstance = { ctx ->
            val ruleDesc = ctx.getString(R.string.calendar_event)
            Bot(
                desc = ctx.getString(R.string.calendar_event),
                trigger = CalendarEvent(eventTitle = ctx.getString(R.string.working)),
                actions = listOf(
                    FindRules(pattern = ruleDesc),
                    ModifyRules(config = "{\"flags\": 3}"),
                )
            )
        },
        afterCreated = { ctx ->
            // Add a regex rule
            val ruleDesc = ctx.getString(R.string.calendar_event)
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
        tooltip = { it.getString(R.string.help_call_throttling_template) },
        newInstance = { ctx ->
            val ruleDesc = ctx.getString(R.string.throttled_call)
            Bot(
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
        },
        afterCreated = { ctx ->
            // Add a regex rule
            val ruleDesc = ctx.getString(R.string.throttled_call)
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
        tooltip = { it.getString(R.string.help_sms_throttling_template) },
        newInstance = { ctx ->
            val ruleDesc = ctx.getString(R.string.throttled_sms)
            Bot(
                desc = ctx.getString(R.string.sms_throttling),
                trigger = SmsThrottling(targetRuleDesc = ruleDesc),
                actions = listOf(
                    FindRules(pattern = ruleDesc),
                    ModifyRules(config = "{\"flags\": 2}"),
                )
            )
        },
        afterCreated = { ctx ->
            // Add a regex rule
            val ruleDesc = ctx.getString(R.string.throttled_sms)
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
        tooltip = { it.getString(R.string.help_remote_setup) },
        newInstance = { ctx ->
            Bot(
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
        }
    ),

    // Auto Backup on Monday midnight
    BotPreset(
        tooltip = { it.getString(R.string.help_bot_preset_auto_backup) },
        newInstance = { ctx ->
            Bot(
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
        }
    )
)
