package spam.blocker.ui.setting.bot

import android.content.Context
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.service.bot.BackupExport
import spam.blocker.service.bot.CalendarEvent
import spam.blocker.service.bot.CallThrottling
import spam.blocker.service.bot.CleanupHistory
import spam.blocker.service.bot.CleanupSpamDB
import spam.blocker.service.bot.FindRules
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.ModifyRules
import spam.blocker.service.bot.ParseCSV
import spam.blocker.service.bot.SmsThrottling
import spam.blocker.service.bot.Time
import spam.blocker.service.bot.Weekly
import spam.blocker.service.bot.WriteFile
import spam.blocker.util.Lambda1
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

class BotPreset(
    val tooltipId: Int,
    val onCreate: Lambda1<Context>? = null,
    val newInstance: (Context) -> Bot,
)

val BotPresets = listOf(

    // FTC Do Not Call, update every Monday~Friday @ 18:00:00
    BotPreset(
        tooltipId = R.string.help_bot_preset_dnc,
    ) { ctx ->
        Bot(
            desc = ctx.getString(R.string.bot_preset_dnc),
            enabled = true,
            schedule = Weekly(
                weekdays = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
                time = Time(18, 0)
            ),
            actions = listOf(
                HttpDownload(url = "https://www.ftc.gov/sites/default/files/DNC_Complaint_Numbers_{year}-{month}-{day}.csv"),
                ParseCSV(columnMapping = "{'Company_Phone_Number': 'pattern'}"),
                // no need to add ClearNumber here
                ImportToSpamDB(),
            )
        )
    },

    // Call Throttling
    BotPreset(
        tooltipId = R.string.help_call_throttling_template,
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
            actions = listOf(
                CallThrottling(
                    includingBlocked = true,
                    includingAnswered = true,
                ),
                FindRules(pattern = ruleDesc),
                ModifyRules(config = "{\"flags\": 1}"),
            )
        )
    },

    // SMS Throttling
    BotPreset(
        tooltipId = R.string.help_sms_throttling_template,
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
            actions = listOf(
                SmsThrottling(targetRuleDesc = ruleDesc),
                FindRules(pattern = ruleDesc),
                ModifyRules(config = "{\"flags\": 2}"),
            )
        )
    },

    // Calendar Event
    BotPreset(
        tooltipId = R.string.help_calendar_event_template,
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
            actions = listOf(
                CalendarEvent(eventTitle = ctx.getString(R.string.working)),
                FindRules(pattern = ruleDesc),
                ModifyRules(config = "{\"flags\": 3}"),
            )
        )
    },

    // Cleanup spam database at every midnight, deleting expired records that are older than 90 days.
    BotPreset(
        tooltipId = R.string.help_bot_preset_cleanup_spam_db,
    ) { ctx ->
        Bot(
            desc = ctx.getString(R.string.bot_preset_cleanup_spam_db),
            enabled = true,
            schedule = Weekly(
                weekdays = listOf(
                    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
                ),
                time = Time(0, 0)
            ),
            actions = listOf(
                CleanupSpamDB(expiry = 90),
            )
        )
    },

    // Cleanup history records at every midnight, deleting expired records that are older than 30 days.
    BotPreset(
        tooltipId = R.string.help_bot_preset_cleanup_history,
    ) { ctx ->
        Bot(
            desc = ctx.getString(R.string.bot_preset_cleanup_history),
            enabled = true,
            schedule = Weekly(
                weekdays = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY),
                time = Time(0, 0)
            ),
            actions = listOf(
                CleanupHistory(expiry = 30),
            )
        )
    },

    // Auto Backup on Monday midnight
    BotPreset(
        tooltipId = R.string.help_bot_preset_auto_backup,
    ) { ctx ->
        Bot(
            desc = ctx.getString(R.string.bot_preset_auto_backup),
            enabled = true,
            schedule = Weekly(
                weekdays = listOf(MONDAY),
                time = Time(0, 0)
            ),
            actions = listOf(
                BackupExport(),
                WriteFile(filename = "SpamBlocker.auto.gz")
            )
        )
    }
)
