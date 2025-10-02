package spam.blocker.ui.setting.api

import android.content.Context
import spam.blocker.BuildConfig
import spam.blocker.R
import spam.blocker.db.Api
import spam.blocker.db.ImportDbReason
import spam.blocker.service.bot.CategoryConfig
import spam.blocker.service.bot.FilterSpamResult
import spam.blocker.service.bot.HTTP_GET
import spam.blocker.service.bot.HTTP_POST
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.InterceptCall
import spam.blocker.service.bot.InterceptSms
import spam.blocker.service.bot.ParseQueryResult
import spam.blocker.util.Lambda1
import spam.blocker.util.Lambda2
import spam.blocker.util.Lambda3
import spam.blocker.util.escape
import spam.blocker.util.httpRequest
import spam.blocker.util.resolveBearerTag


data class AuthConfig(
    val formLabels: List<Int>,
    // A tooltip for explaining how to obtain the API_KEY
    val tooltipId: Int,
    // Pre-process the actions, e.g.:
    //  fill the {api_token} in HttpDownload.header with user input credentials
    val preProcessor: Lambda2<List<IAction>, List<String>>,
    // For validating if the user has input the correct auth credentials.
    val validator: Lambda3<Context, List<String>, Lambda1<String?>>,
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
    // Show a dialog for inputting authorization information(API_TOKEN/Username/Password).
    val newAuthConfig: () -> AuthConfig?,
    val newApi: (Context) -> Api,
)

val defApiQueryActions = listOf(
    InterceptCall(),
    HttpDownload(),
    ParseQueryResult(),
    FilterSpamResult(),
    ImportToSpamDB()
)
val defApiReportActions = listOf(
    InterceptCall(),
    HttpDownload(),
)

val authConfig_PhoneBlock = AuthConfig(
    formLabels = listOf(
        R.string.api_key,
    ),
    tooltipId = R.string.help_api_preset_phoneblock_authorization,
    preProcessor = { actions, formValues ->
        // replace the tags in HttpDownload.header
        actions.find { it is HttpDownload }?.let {
            val http = it as HttpDownload
            http.header = http.header
                .replace("{api_key}", formValues[0])
        }
    },
    validator = { ctx, fieldValues, callback ->
        val apiKey = fieldValues[0]

        val result = httpRequest(
            urlString = "https://phoneblock.net/phoneblock/api/test",
            headersMap = mapOf("Authorization" to "{bearer_auth($apiKey)}".resolveBearerTag()),
            method = HTTP_GET,
        )

        callback(
            if (result == null) {
                ctx.getString(R.string.unknown_error)
            } else {
                when (result.statusCode) {
                    200 -> null
                    401 -> ctx.getString(R.string.invalid_auth_credentials)
                    else -> {
                        result.exception
                            ?: ctx.getString(R.string.http_status_code_template).format(result.statusCode)
                    }
                }
            }
        )
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
                    InterceptCall(
                        numberFilter = ".*",
                    ),
                    HttpDownload(
                        url = "https://phoneblock.net/phoneblock/api/check?sha1={sha1(+{cc}{domestic})}",
                        header = "{bearer_auth({api_key})}",
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
    ),
    // Google Gemini
    ApiPreset(
        tooltipId = R.string.help_api_preset_gemini,
        newAuthConfig = {
            AuthConfig(
                formLabels = listOf(
                    R.string.gemini_api_key,
                ),
                tooltipId = R.string.help_api_preset_gemini_authorization,
                preProcessor = { actions, formValues ->
                    // replace the tags in HttpDownload.header
                    actions.find { it is HttpDownload }?.let {
                        val http = it as HttpDownload
                        http.url = http.url
                            .replace("{api_key}", formValues[0])
                    }
                },
                validator = { ctx, fieldValues, callback ->
                    callback(null)
                }
            )
        },
        newApi = { ctx ->
            Api(
                desc = ctx.getString(R.string.api_preset_gemini),
                enabled = true,
                actions = listOf(
                    InterceptSms(),
                    HttpDownload(
                        url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={api_key}",
                        header = "Content-Type: application/json",
                        method = HTTP_POST,
                        body = "{\n  \"contents\": [{\n    \"parts\":[{\n\t  \"text\": \"%s\"\n\n\t}]\n  }]\n}"
                            .format(ctx.getString(R.string.spam_sms_prompt_template).escape())
                    ),
                    ParseQueryResult(
                        negativeSig = ctx.getString(R.string.spam_sms_negative_category),
                        positiveSig = ctx.getString(R.string.spam_sms_positive_category),
                        categorySig = "\"text\": \"(.*?)..\"",
                    ),
                )
            )
        }
    ),
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
                    InterceptCall(
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
                        url = "https://phoneblock.net/phoneblock/api/rate",
                        header = "{bearer_auth({api_key})}",
                        method = HTTP_POST,
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
