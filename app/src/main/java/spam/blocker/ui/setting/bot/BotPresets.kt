package spam.blocker.ui.setting.bot

import android.content.Context
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.service.bot.BackupExport
import spam.blocker.service.bot.CleanupHistory
import spam.blocker.service.bot.CleanupSpamDB
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.ParseCSV
import spam.blocker.service.bot.Time
import spam.blocker.service.bot.Weekly
import spam.blocker.service.bot.WriteFile
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

class BotPreset(
    val tooltipId: Int,
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
