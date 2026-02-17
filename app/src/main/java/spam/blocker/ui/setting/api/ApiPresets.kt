package spam.blocker.ui.setting.api

import android.content.Context
import spam.blocker.BuildConfig
import spam.blocker.R
import spam.blocker.db.IApi
import spam.blocker.db.ImportDbReason
import spam.blocker.db.QueryApi
import spam.blocker.db.ReportApi
import spam.blocker.service.bot.CategoryConfig
import spam.blocker.service.bot.FilterSpamResult
import spam.blocker.service.bot.ForwardType
import spam.blocker.service.bot.HTTP_GET
import spam.blocker.service.bot.HTTP_POST
import spam.blocker.service.bot.HttpRequest
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


data class AuthConfig(
    val formLabels: List<Int>,
    // A tooltip for explaining how to obtain the API_KEY
    val tooltipId: Int,
    // Pre-process the actions, e.g.:
    //  fill the {api_token} in HttpDownload.header with user input credentials
    val preProcessor: Lambda2<List<IAction>, List<String>>,
    // For validating if the user has input the correct auth credentials.
    // Params: ctx, fieldValues, callback(errStr)
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
    val leadingIconId: Int? = null,
    // Show a dialog for inputting authorization information(API_TOKEN/Username/Password).
    val newAuthConfig: () -> AuthConfig?,
    val newInstance: (Context) -> IApi,
    // APIs like PhoneBlock support both query/report.
    // When creating the query API, also create the report API to avoid asking for API key twice.
    val newReportApi: ((Context) -> IApi)? = null,
)

val defApiQueryActions = listOf(
    InterceptCall(),
    HttpRequest(),
    ParseQueryResult(),
    FilterSpamResult(),
    ImportToSpamDB()
)
val defApiReportActions = listOf(
    InterceptCall(),
    HttpRequest(),
)

val authConfig_PhoneBlock = AuthConfig(
    formLabels = listOf(
        R.string.api_key,
    ),
    tooltipId = R.string.help_api_preset_phoneblock_authorization,
    preProcessor = { actions, formValues ->
        // replace the tags in HttpDownload.header
        actions.find { it is HttpRequest }?.let {
            val http = it as HttpRequest
            http.header = http.header
                .replace("{api_key}", formValues[0])
        }
    },
    validator = { ctx, fieldValues, callback ->
        val apiKey = fieldValues[0]

        val result = httpRequest(
            urlString = if (BuildConfig.DEBUG)
                "https://phoneblock.net/pb-test/api/test"
            else
                "https://phoneblock.net/phoneblock/api/test",
            headersMap = mapOf("Authorization" to "Bearer $apiKey"),
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
        leadingIconId = R.drawable.ic_call,
        newAuthConfig = { authConfig_PhoneBlock.copy() },
        newInstance = { ctx ->
            QueryApi(
                desc = ctx.getString(R.string.api_preset_phoneblock),
                enabled = true,
                actions = listOf(
                    InterceptCall(
                        numberFilter = ".*",
                        forwardType = ForwardType.Original,
                    ),
                    HttpRequest(
                        url = if (BuildConfig.DEBUG)
                            "https://phoneblock.net/pb-test/api/check?sha1={sha1(+{cc}{domestic})}"
                        else
                            "https://phoneblock.net/phoneblock/api/check?sha1={sha1(+{cc}{domestic})}",
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
        },
        newReportApi = { ctx ->
            ApiReportPreset_PhoneBlock.newInstance(ctx)
        }
    ),
    // Groq
    ApiPreset(
        tooltipId = R.string.help_api_preset_groq,
        leadingIconId = R.drawable.ic_sms,
        newAuthConfig = {
            AuthConfig(
                formLabels = listOf(
                    R.string.api_key,
                ),
                tooltipId = R.string.help_api_preset_groq_authorization,
                preProcessor = { actions, formValues ->
                    // replace the tags in HttpDownload.header
                    actions.find { it is HttpRequest }?.let {
                        val http = it as HttpRequest
                        http.header = http.header
                            .replace("{api_key}", formValues[0])
                    }
                },
                validator = { ctx, fieldValues, callback ->
                    callback(null)
                }
            )
        },
        newInstance = { ctx ->
            QueryApi(
                desc = ctx.getString(R.string.api_preset_groq),
                enabled = true,
                actions = listOf(
                    InterceptSms(),
                    HttpRequest(
                        url = "https://api.groq.com/openai/v1/chat/completions",
                        header = """
                            Content-Type: application/json
                            Authorization: Bearer {api_key}
                        """.trimIndent(),
                        method = HTTP_POST,
                        body = """
                            {
                             "model": "openai/gpt-oss-120b",
                             "temperature": 1,
                             "max_completion_tokens": 8192,
                             "top_p": 1,
                             "stream": false,
                             "reasoning_effort": "medium",
                             "stop": null,
                             "messages": [
                               {
                            	 "role": "user",
                            	 "content": "%s"
                               }
                             ]
                            }
                        """.trimIndent()
                            .format(ctx.getString(R.string.spam_sms_prompt_template).escape())
                    ),
                    ParseQueryResult(
                        negativeSig = """
                            "content":"${ctx.getString(R.string.spam_sms_negative_category)}"
                            """.trimIndent(),
                        positiveSig = """
                            "content":"${ctx.getString(R.string.spam_sms_positive_category)}"
                            """.trimIndent(),
                        categorySig = "\"content\":\"(.*?)\"",
                    ),
                )
            )
        }
    ),
)

val ApiReportPreset_PhoneBlock = ApiPreset(
    tooltipId = R.string.help_api_preset_phoneblock,
    newAuthConfig = { authConfig_PhoneBlock.copy() },
    newInstance = { ctx ->
        ReportApi(
            desc = ctx.getString(R.string.api_preset_phoneblock),
            enabled = true,
            actions = listOf(
                InterceptCall(
                    numberFilter = ".*",
                    forwardType = ForwardType.Original,
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
                HttpRequest(
                    url = if (BuildConfig.DEBUG)
                        "https://phoneblock.net/pb-test/api/rate"
                    else
                        "https://phoneblock.net/phoneblock/api/rate",
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
val ApiReportPresets = listOf(
    ApiReportPreset_PhoneBlock
)
