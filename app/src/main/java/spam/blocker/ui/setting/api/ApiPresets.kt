package spam.blocker.ui.setting.api

import android.content.Context
import spam.blocker.R
import spam.blocker.db.Api
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.ParseCSV
import spam.blocker.service.bot.ParseIncomingNumber
import spam.blocker.service.bot.ParseQueryResult

class ApiPreset(
    val tooltipId: Int,
    val newInstance: (Context) -> Api,
)

val ApiPresets = listOf<ApiPreset>(

    // PhoneBlock
    ApiPreset(
        tooltipId = R.string.help_api_preset_phoneblock,
    ) { ctx ->
        Api(
            desc = ctx.getString(R.string.api_preset_phoneblock),
            enabled = true,
            actions = listOf(
                ParseIncomingNumber(
                    numberFilter = "0.*",
                ),
                HttpDownload(url = "https://phoneblock.net/phoneblock/api/num/{origin_number}"),
                ParseQueryResult(
                    negativeSig = "(D_POLL|G_FRAUD|E_ADVERTISING|F_GAMBLE)",
                    categorySig = "\"rating\":\"(.+?)\"",
                ),
//                ImportToSpamDB(),
            )
        )
    },
)
