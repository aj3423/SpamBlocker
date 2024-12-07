package spam.blocker

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.ui.history.CallViewModel
import spam.blocker.ui.history.SmsViewModel
import spam.blocker.ui.setting.TestingViewModel
import spam.blocker.ui.setting.api.ApiQueryViewModel
import spam.blocker.ui.setting.api.ApiReportViewModel
import spam.blocker.ui.setting.api.ApiViewModel
import spam.blocker.ui.setting.bot.BotViewModel
import spam.blocker.ui.setting.regex.ContentRuleViewModel
import spam.blocker.ui.setting.regex.NumberRuleViewModel
import spam.blocker.ui.setting.regex.QuickCopyRuleViewModel
import spam.blocker.ui.widgets.BottomBarViewModel
import spam.blocker.util.PermissionChain

@Immutable
object G {
    val globallyEnabled : MutableState<Boolean> = mutableStateOf(false)

    val themeType : MutableIntState  = mutableIntStateOf(0)

    val callVM : CallViewModel = CallViewModel()
    val smsVM : SmsViewModel = SmsViewModel()

    val NumberRuleVM : NumberRuleViewModel = NumberRuleViewModel()
    val ContentRuleVM : ContentRuleViewModel = ContentRuleViewModel()
    val QuickCopyRuleVM : QuickCopyRuleViewModel = QuickCopyRuleViewModel()
    val botVM : BotViewModel = BotViewModel()
    val apiQueryVM : ApiViewModel = ApiQueryViewModel()
    val apiReportVM : ApiViewModel = ApiReportViewModel()

    lateinit var bottomBarVM : BottomBarViewModel

    val testingVM : TestingViewModel = TestingViewModel()

    val permissionChain = PermissionChain()
}

