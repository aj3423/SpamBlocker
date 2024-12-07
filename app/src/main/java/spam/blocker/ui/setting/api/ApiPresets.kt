package spam.blocker.ui.setting.api

import android.content.Context
import spam.blocker.BuildConfig
import spam.blocker.R
import spam.blocker.db.Api
import spam.blocker.service.bot.FilterSpamResult
import spam.blocker.service.bot.HTTP_POST
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.ParseIncomingNumber
import spam.blocker.service.bot.ParseQueryResult
import spam.blocker.util.Algorithm.b64Decode
import spam.blocker.util.Lambda2
import spam.blocker.util.Util.isUUID
import spam.blocker.util.resolveBase64Tag
import kotlin.Int
import kotlin.String
import kotlin.collections.List


data class AuthConfig(
    val formLabels: List<Int>,
    // A tooltip for explaining how to obtain the API_KEY
    val tooltipId: Int,
    // Pre-process the actions, e.g.:
    //  fill the {username}, {password} in HttpDownload.header with user input credentials
    val preProcessor: Lambda2<List<IAction>, List<String>>,
    // For validating if the user has input the correct auth credentials.
    val validator: (List<String>) -> Boolean,
)

data class ApiPreset(
    val tooltipId: Int,
    // Show a dialog for inputting authorization information(API_KEY/Username/Password).
    val newAuthConfig: () -> AuthConfig?,
    val newApi: (Context) -> Api,
)

val defApiQueryActions = listOf(
    ParseIncomingNumber(),
    HttpDownload(),
    ParseQueryResult(),
    FilterSpamResult(),
    ImportToSpamDB()
)
val defApiReportActions = listOf(
    ParseIncomingNumber(),
    HttpDownload(),
)

val authConfig_PhoneBlock = AuthConfig(
    formLabels = listOf(
        R.string.username,
        R.string.password,
    ),
    tooltipId = R.string.help_api_preset_phoneblock_authorization,
    preProcessor = { actions, tags ->
        // replace the {username}, {password} in HttpDownload.header
        actions.find { it is HttpDownload }?.let {
            val http = it as HttpDownload
            http.header = http.header
                .replace("{username}", tags[0])
                .replace("{password}", tags[1])
                .resolveBase64Tag()
        }
    },
    validator = {
        val username = it[0]
        val password = it[1]
        if(!isUUID(username))
            return@AuthConfig false
        try {
            b64Decode(password).size == 16
            return@AuthConfig true
        } catch (_: Exception) {
            return@AuthConfig false
        }
    }
)

val ApiQueryPresets = listOf<ApiPreset>(
    // PhoneBlock
    ApiPreset(
        tooltipId = R.string.help_api_preset_phoneblock,
        newAuthConfig = { authConfig_PhoneBlock.copy() },
        newApi = { ctx ->
            Api(
                desc = ctx.getString(R.string.api_preset_phoneblock),
                enabled = true,
                actions = listOf(
                    ParseIncomingNumber(
                        numberFilter = ".*",
                    ),
                    HttpDownload(
                        url =
//                        if (BuildConfig.DEBUG)
                            "https://phoneblock.net/pb-test/api/num/00{cc}{domestic}"
//                        else
//                            "https://phoneblock.net/phoneblock/api/num/00{cc}{domestic}"
                        ,
                        header = "Authorization: Basic {base64({username}:{password})}"
                    ),
                    ParseQueryResult(
                        negativeSig = "(D_POLL|G_FRAUD|E_ADVERTISING|F_GAMBLE|B_MISSED)",
                        categorySig = "\"rating\":\"(.+?)\"",
                    ),
                    FilterSpamResult(),
                    ImportToSpamDB(),
                )
            )
        }
    )
)

val ApiReportPresets = listOf<ApiPreset>(
    // PhoneBlock
    ApiPreset(
        tooltipId = R.string.help_api_preset_phoneblock,
        newAuthConfig = { authConfig_PhoneBlock.copy() },
        newApi = { ctx ->
            Api(
                desc = ctx.getString(R.string.api_preset_phoneblock),
                enabled = true,
                actions = listOf(
                    ParseIncomingNumber(
                        numberFilter = ".*",
                    ),
                    HttpDownload(
                        url =
//                        if (BuildConfig.DEBUG) {
                            "https://phoneblock.net/pb-test/api/rate"
//                        } else {
//                            "https://phoneblock.net/phoneblock/api/rate"
//                        }
                        ,
                        header = "Authorization: Basic {base64({username}:{password})}",
                        method = HTTP_POST,

                        // rating number format: "+33123456789",
                        body = """
                            {
                                "phone": "00{cc}{domestic}",
                                "rating": "B_MISSED"
                            }
                        """.trimIndent()
                    ),
                )
            )
        }
    )
)
