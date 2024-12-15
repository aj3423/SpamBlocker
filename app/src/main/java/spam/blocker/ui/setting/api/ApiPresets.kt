package spam.blocker.ui.setting.api

import android.content.Context
import spam.blocker.R
import spam.blocker.db.Api
import spam.blocker.db.ImportDbReason
import spam.blocker.service.bot.CategoryConfig
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
import kotlin.collections.mapOf


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

const val tagCategory = "{category}"

const val tagValid = "{valid}"
const val tagOther = "{other}"
const val tagFraud = "{fraud}"
const val tagMarketing = "{marketing}"
const val tagSurvey = "{survey}"
const val tagPolitical = "{political}"

// Only add to this, don't modify existing items
// When adding items to this map, make sure to add to R.array.spam_categories as well.
fun spamCategoryNamesMap(ctx: Context): Map<String, String> {
    val array = ctx.resources.getStringArray(R.array.spam_categories)
    return mapOf(
        tagValid to array[0],

        tagFraud to array[1],
        tagMarketing to array[2],
        tagSurvey to array[3],
        tagPolitical to array[4],

        tagOther to array[5], // the last one, it's also ordered on UI
    )
}

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
    preProcessor = { actions, formValues ->
        // replace the {username}, {password} in HttpDownload.header
        actions.find { it is HttpDownload }?.let {
            val http = it as HttpDownload
            http.header = http.header
                .replace("{username}", formValues[0])
                .replace("{password}", formValues[1])
                .resolveBase64Tag()
        }
    },
    validator = validator@ {
        val username = it[0]
        val password = it[1]
        if (!isUUID(username))
            return@validator false

        return@validator try {
            b64Decode(password).size == 16
            true
        } catch (_: Exception) {
            false
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
                    ImportToSpamDB(
                        importReason = ImportDbReason.ByAPI,
                    ),
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
                    CategoryConfig(
                        map = mapOf(
                            tagValid to "A_LEGITIMATE",
                            tagOther to "B_MISSED",
                            tagSurvey to "D_POLL",
                            tagMarketing to "E_ADVERTISING",
                            tagPolitical to "E_ADVERTISING",
                            tagFraud to "G_FRAUD",
                        )
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

                        // rating number format: "+3312345",
                        body = """
                            {
                                "phone": "00{cc}{domestic}",
                                "rating": "{category}"
                            }
                        """.trimIndent()
                    )
                )
            )
        }
    )
)
