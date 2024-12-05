package spam.blocker.ui.setting.api

import android.content.Context
import spam.blocker.R
import spam.blocker.db.Api
import spam.blocker.service.bot.FilterQueryResult
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.ParseIncomingNumber
import spam.blocker.service.bot.ParseQueryResult
import spam.blocker.util.Lambda2
import spam.blocker.util.resolveBase64Tag
import kotlin.Int
import kotlin.String
import kotlin.collections.List


data class AuthConfig(
    val formLabels: List<Int>,
    // A tooltip for explaining how to obtain the API_KEY
    val tooltipId: Int,
    val overrider: Lambda2<List<IAction>, List<String>>,
)

class ApiPreset(
    val tooltipId: Int,
    // Show a dialog for inputting authorization information(API_KEY/Username/Password).
    val authConfig: AuthConfig? = null,
    val newInstance: (Context) -> Api,
)

val ApiPresets = listOf<ApiPreset>(

    // PhoneBlock
    ApiPreset(
        tooltipId = R.string.help_api_preset_phoneblock,
        authConfig = AuthConfig(
            formLabels = listOf(
                R.string.username,
                R.string.password,
            ),
            tooltipId = R.string.help_api_preset_phoneblock_authorization,
            overrider = { actions, tags ->
                // replace the {username}, {password} in HttpDownload.header
                actions.find { it is HttpDownload }?.let {
                    val http = it as HttpDownload
                    http.header = http.header
                        .replace("{username}", tags[0])
                        .replace("{password}", tags[1])
                        .resolveBase64Tag()
                }
            }
        ),
        newInstance = { ctx ->
            Api(
                desc = ctx.getString(R.string.api_preset_phoneblock),
                enabled = true,
                actions = listOf(
                    ParseIncomingNumber(
                        numberFilter = "0.*",
                        autoCC = true,
                    ),
                    HttpDownload(
                        url = "https://phoneblock.net/phoneblock/api/num/{origin_number}",
                        header = "Authorization: Basic {base64({username}:{password})}"
                    ),
                    ParseQueryResult(
                        negativeSig = "(D_POLL|G_FRAUD|E_ADVERTISING|F_GAMBLE)",
                        categorySig = "\"rating\":\"(.+?)\"",
                    ),
                    FilterQueryResult(),
                    ImportToSpamDB(),
                )
            )
        }
    )
)
