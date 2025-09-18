package spam.blocker

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import spam.blocker.db.Notification.Channel
import spam.blocker.db.Notification.ChannelTable
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
import spam.blocker.util.Notification.syncSystemChannels
import spam.blocker.util.Permission
import spam.blocker.util.PermissionChain
import spam.blocker.util.spf

@Immutable
object G {
    val globallyEnabled : MutableState<Boolean> = mutableStateOf(false)
    val callEnabled : MutableState<Boolean> = mutableStateOf(false)
    val smsEnabled : MutableState<Boolean> = mutableStateOf(false)
    val dynamicTile0Enabled : MutableState<Boolean> = mutableStateOf(false)

    val notificationChannels : SnapshotStateList<Channel> = mutableStateListOf()
    val themeType : MutableIntState  = mutableIntStateOf(0)
    val showHistoryIndicator : MutableState<Boolean> = mutableStateOf(false)
    val showHistoryPassed : MutableState<Boolean> = mutableStateOf(false)
    val showHistoryBlocked : MutableState<Boolean> = mutableStateOf(false)

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

    fun initialize(ctx: Context) {
        // Global switches
        run {
            val spf = spf.Global(ctx)
            globallyEnabled.value = spf.isGloballyEnabled()
            callEnabled.value = spf.isCallEnabled() && Permission.callScreening.isGranted
            smsEnabled.value = spf.isSmsEnabled() && Permission.receiveSMS.isGranted
            themeType.intValue = spf.getThemeType()
        }

        // Workflow switches
        run {
            val spf = spf.BotOptions(ctx)
            dynamicTile0Enabled.value = spf.isDynamicTileEnabled(0)
        }

        // History options
        run {
            val spf = spf.HistoryOptions(ctx)
            showHistoryIndicator.value = spf.getShowIndicator()
            showHistoryPassed.value = spf.getShowPassed()
            showHistoryBlocked.value = spf.getShowBlocked()
        }

        // Notifications
        run {
            syncSystemChannels(ctx)
            notificationChannels.clear()
            notificationChannels.addAll(ChannelTable.listAll(ctx))
        }
    }
}

