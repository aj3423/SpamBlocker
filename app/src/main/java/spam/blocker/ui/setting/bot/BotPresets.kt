package spam.blocker.ui.setting.bot

import android.content.Context
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.service.bot.BackupExport
import spam.blocker.service.bot.BackupImport
import spam.blocker.service.bot.CalendarEvent
import spam.blocker.service.bot.CallThrottling
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.FindRules
import spam.blocker.service.bot.HttpRequest
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.Manual
import spam.blocker.service.bot.ModifyRules
import spam.blocker.service.bot.ParseCSV
import spam.blocker.service.bot.Ringtone
import spam.blocker.service.bot.Schedule
import spam.blocker.service.bot.SmsThrottling
import spam.blocker.service.bot.Time
import spam.blocker.service.bot.Weekly
import spam.blocker.service.bot.WriteFile
import spam.blocker.util.Lambda1
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

class BotPreset(
    val tooltip: (Context)->String,
    val onCreate: Lambda1<Context>? = null,
    val newInstance: (Context) -> Bot,
)


val BotPresets = listOf(

    // FTC Do Not Call, initial database
    BotPreset(
        tooltip = {
            it.getString(R.string.help_bot_preset_dnc_initial)
                .format(
                    "https://github.com/aj3423/DNC_snapshot"
                )
        },
    ) { ctx ->
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
    },

    // FTC Do Not Call, updated on Monday~Friday @ 18:00:00
    BotPreset(
        tooltip = { it.getString(R.string.help_bot_preset_dnc) },
    ) { ctx ->
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
    },

    // Ringtone <-> Regex Rule
    BotPreset(
        tooltip = { it.getString(R.string.help_preset_ringtone) },
    ) { ctx ->
        Bot(
            desc = ctx.getString(R.string.ringtone),
            trigger = Ringtone(bindTo = "{ \"regex\": \"${ctx.getString(R.string.replace_this)}\" }"),
        )
    },

    // Calendar Event
    BotPreset(
        tooltip = { it.getString(R.string.help_calendar_event_template) },
        onCreate = { ctx ->
            // Add a regex rule
            val ruleDesc = ctx.getString(R.string.calendar_event)
            NumberRuleTable().addNewRule(ctx, RegexRule(
                pattern = ".*",
                description = ruleDesc,
                flags = 0, // disabled for call/sms
            ))
            G.NumberRuleVM.reloadDb(ctx)
        },
    ) { ctx ->
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

    // Call Throttling
    BotPreset(
        tooltip = { it.getString(R.string.help_call_throttling_template) },
        onCreate = { ctx ->
            // Add a regex rule
            val ruleDesc = ctx.getString(R.string.throttled_call)
            NumberRuleTable().addNewRule(ctx, RegexRule(
                pattern = ".*",
                description = ruleDesc,
                flags = 0,
            ))
            G.NumberRuleVM.reloadDb(ctx)
        },
    ) { ctx ->
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

    // SMS Throttling
    BotPreset(
        tooltip = { it.getString(R.string.help_sms_throttling_template) },
        onCreate = { ctx ->
            // Add a regex rule
            val ruleDesc = ctx.getString(R.string.throttled_sms)
            NumberRuleTable().addNewRule(ctx, RegexRule(
                pattern = ".*",
                description = ruleDesc,
                flags = 0,
            ))
            G.NumberRuleVM.reloadDb(ctx)
        },
    ) { ctx ->
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

    // Remote Setup
    BotPreset(
        tooltip = { it.getString(R.string.help_remote_setup) },
    ) { ctx ->
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
    },

    // Auto Backup on Monday midnight
    BotPreset(
        tooltip = { it.getString(R.string.help_bot_preset_auto_backup) },
    ) { ctx ->
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
