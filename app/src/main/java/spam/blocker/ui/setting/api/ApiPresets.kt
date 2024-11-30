package spam.blocker.ui.setting.api

import android.content.Context
import spam.blocker.R
import spam.blocker.db.Api
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.ParseCSV

class ApiPreset(
    val tooltipId: Int,
    val newInstance: (Context) -> Api,
)

val ApiPresets = listOf(

    // FTC Do Not Call, update every Monday~Friday @ 18:00:00
    ApiPreset(
        tooltipId = R.string.help_bot_preset_dnc,
    ) { ctx ->
        Api(
            desc = ctx.getString(R.string.bot_preset_dnc),
            enabled = true,
            actions = listOf(
                HttpDownload(url = "https://www.ftc.gov/sites/default/files/DNC_Complaint_Numbers_{year}-{month}-{day}.csv"),
                ParseCSV(columnMapping = "{'Company_Phone_Number': 'pattern'}"),
                // no need to add ClearNumber here
                ImportToSpamDB(),
            )
        )
    },


)
