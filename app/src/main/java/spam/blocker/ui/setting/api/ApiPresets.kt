package spam.blocker.ui.setting.api

import android.content.Context
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.AutoReportTypes
import spam.blocker.db.ImportDbReason
import spam.blocker.db.QueryApi
import spam.blocker.db.ReportApi
import spam.blocker.service.bot.CategoryConfig
import spam.blocker.service.bot.CopyTag
import spam.blocker.service.bot.FilterSpamResult
import spam.blocker.service.bot.ForwardType
import spam.blocker.service.bot.HTTP_POST
import spam.blocker.service.bot.HttpRequest
import spam.blocker.service.bot.ImportToSpamDB
import spam.blocker.service.bot.InterceptCall
import spam.blocker.service.bot.InterceptSms
import spam.blocker.service.bot.ParseQueryResult
import spam.blocker.service.bot.SendSms
import spam.blocker.service.bot.SetTag
import spam.blocker.ui.history.tagFraud
import spam.blocker.ui.history.tagMarketing
import spam.blocker.ui.history.tagOther
import spam.blocker.ui.history.tagPolitical
import spam.blocker.ui.history.tagSurvey
import spam.blocker.ui.history.tagValid
import spam.blocker.util.Lambda1
import spam.blocker.util.Launcher

data class ApiPreset(
    val desc: (Context) -> String,
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
            desc = { ctx -> ctx.getString(R.string.api_preset_phoneblock) },
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
            desc = { ctx -> ctx.getString(R.string.api_preset_remote_llm) },
            tooltipId = R.string.help_api_preset_remote_llm,
            leadingIconId = R.drawable.ic_sms,

            onClick = { ctx ->
                Launcher.openUrl(ctx, "https://github.com/aj3423/SpamBlocker/wiki/Regex-Workflow-Templates#check-sms-using-google-gemini")
            }
        ),
    )
}

val ApiReportPresets by lazy {
    listOf(
        // PhoneBlock
        ApiPreset(
            desc = { ctx -> ctx.getString(R.string.api_preset_phoneblock) },
            leadingIconId = R.drawable.ic_call,
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
        ),

        // 7726
        ApiPreset(
            desc = { "7726" },
            leadingIconId = R.drawable.ic_sms_blocked,
            tooltipId = R.string.help_bot_preset_7726,
            onClick = { ctx ->
                val newApi = ReportApi(
                    desc = "7726",
                    enabled = true,
                    autoReportTypes = AutoReportTypes.Regex,
                    actions = listOf(
                        InterceptSms(
                            contentFilter = ".*",
                        ),
                        SetTag(
                            tagName = "send_sms_to",
                            tagValue = "7726"
                        ),
                        CopyTag(
                            tagFrom = "sms_content",
                            tagTo = "send_sms_content"
                        ),
                        SendSms()
                    )
                )

                addApiToDB(ctx, G.apiReportVM, newApi)
            }
        ),
    )
}
