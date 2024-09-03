package spam.blocker

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.ui.history.CallViewModel
import spam.blocker.ui.history.SmsViewModel
import spam.blocker.ui.setting.TestingViewModel
import spam.blocker.ui.widgets.BottomBarViewModel

@Immutable
object G {
    val callVM : CallViewModel = CallViewModel()
    val smsVM : SmsViewModel = SmsViewModel()

    val globallyEnabled : MutableState<Boolean> = mutableStateOf(false)

    lateinit var bottomBarVM : BottomBarViewModel

    val testingVM : TestingViewModel = TestingViewModel()

    val themeType : MutableIntState  = mutableIntStateOf(0)
}
