package spam.blocker.ui.setting.api

import android.content.Context
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.ImportDbReason
import spam.blocker.db.QueryApi
import spam.blocker.db.ReportApi
import spam.blocker.service.bot.CategoryConfig
import spam.blocker.service.bot.FilterSpamResult
import spam.blocker.service.bot.ForwardType
import spam.blocker.service.bot.HTTP_POST
import spam.blocker.service.bot.HttpRequest
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.InterceptCall
import spam.blocker.service.bot.ParseQueryResult
import spam.blocker.util.Lambda1
import spam.blocker.util.Launcher

const val tagCategory = "{category}"
const val tagComment = "{comment}"

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
    val descId: Int,
    val tooltipId: Int,
    val leadingIconId: Int? = null,

    // Either of these must exist
    val setupDialog: ApiSetupDialog? = null,
    val onClick: Lambda1<Context>? = null,
)

val defApiQueryActions by lazy {
    listOf(
        InterceptCall(),
        HttpRequest(),
        ParseQueryResult(),
        FilterSpamResult(),
        ImportToSpamDB()
    )
}
val defApiReportActions by lazy {
    listOf(
        InterceptCall(),
        HttpRequest(),
    )
}

object PhoneBlock {
    const val spfTokenKey = "PhoneBlock_token"
    const val OAuthState = "PhoneBlock_OAuth_State"

    const val oauthUrl = "https://phoneblock.net/phoneblock/mobile/login?appId=SpamBlocker&state=$OAuthState"
    const val oauthUrl_Debug = "https://phoneblock.net/pb-test/mobile/login?appId=SpamBlocker&state=$OAuthState"
}

val ApiQueryPresets by lazy {
    listOf(

        // 1. PhoneBlock
        ApiPreset(
            descId = R.string.api_preset_phoneblock,
            tooltipId = R.string.help_api_preset_phoneblock,
            leadingIconId = R.drawable.ic_call,
            setupDialog = OAuthSetupDialog(
                spfTokenKey = PhoneBlock.spfTokenKey,
                oauthUrl = if (BuildConfig.DEBUG) PhoneBlock.oauthUrl_Debug else PhoneBlock.oauthUrl,
                doAdd = { ctx ->
                    val newApi = QueryApi(
                        desc = ctx.getString(R.string.api_preset_phoneblock),
                        enabled = true,
                        actions = listOf(
                            InterceptCall(
                                numberFilter = ".*",
                                forwardType = ForwardType.Original,
                            ),
                            HttpRequest(
                                url = if (BuildConfig.DEBUG)
                                    "https://phoneblock.net/pb-test/api/check?sha1={sha1(+{cc}{domestic})}&prefix10={sha1({drop_last(+{cc}{domestic},1)})}&prefix100={sha1({drop_last(+{cc}{domestic},2)})}"
                                else
                                    "https://phoneblock.net/phoneblock/api/check?sha1={sha1(+{cc}{domestic})}&prefix10={sha1({drop_last(+{cc}{domestic},1)})}&prefix100={sha1({drop_last(+{cc}{domestic},2)})}",
                                header = "{bearer_auth({shared_pref(${PhoneBlock.spfTokenKey})})}",
                            ),
                            ParseQueryResult(
                                negativeSig = "(D_POLL|G_FRAUD|E_ADVERTISING|F_GAMBLE|B_MISSED)",
                                categorySig = "\"rating\":\"(.+?)\""
//                        commentSig = "\"comment\":\"(.+?)\"" // waiting for PhoneBlock to support this
                            ),
                            FilterSpamResult(),
                            ImportToSpamDB(
                                importReason = ImportDbReason.ByAPI,
                            ),
                        )
                    )
                    addApiToDB(ctx, G.apiQueryVM, newApi)
                }
            ),
        ),
        // Groq
        ApiPreset(
            descId = R.string.api_preset_remote_llm,
            tooltipId = R.string.help_api_preset_remote_llm,
            leadingIconId = R.drawable.ic_sms,

            onClick = { ctx ->
                Launcher.openUrl(ctx, "https://github.com/aj3423/SpamBlocker/wiki/Check-SMS-using-remote-LLM")
            }
        ),
    )
}
val ApiReportPreset_PhoneBlock by lazy {
    ApiPreset(
        descId = R.string.api_preset_phoneblock,
        tooltipId = R.string.help_api_preset_phoneblock,
        setupDialog = OAuthSetupDialog(
            spfTokenKey = PhoneBlock.spfTokenKey,
            oauthUrl = if (BuildConfig.DEBUG) PhoneBlock.oauthUrl_Debug else PhoneBlock.oauthUrl,
            doAdd = { ctx ->
                val newApi = ReportApi(
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
                            header = "{bearer_auth({shared_pref(${PhoneBlock.spfTokenKey})})}",
                            method = HTTP_POST,
                            body = """
                            {
                                "phone": "00{cc}{domestic}",
                                "rating": "{category}",
                                "comment": "{comment}"
                            }
                        """.trimIndent()
                        )
                    )
                )

                addApiToDB(ctx, G.apiReportVM, newApi)
            }
        )
    )
}

val ApiReportPresets by lazy {
    listOf(
        ApiReportPreset_PhoneBlock
    )
}
