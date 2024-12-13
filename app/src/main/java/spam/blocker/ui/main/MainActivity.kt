package spam.blocker.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.history.HistoryScreen
import spam.blocker.ui.setting.SettingScreen
import spam.blocker.ui.theme.AppTheme
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.MayaBlue
import spam.blocker.ui.widgets.Badge
import spam.blocker.ui.widgets.BottomBar
import spam.blocker.ui.widgets.BottomBarViewModel
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.ui.widgets.TabItem
import spam.blocker.util.Launcher
import spam.blocker.util.Permissions
import spam.blocker.util.Util
import spam.blocker.util.spf


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            debug(this)

            // Detect resource leak
            Class.forName("dalvik.system.CloseGuard")
                .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
                .invoke(null, true)
        }

        val ctx = this
        val spf = spf.Global(ctx)

        // language
        Util.setLocale(ctx, spf.getLanguage())

        G.globallyEnabled.value = spf.isGloballyEnabled()
        G.themeType.intValue = spf.getThemeType()

        val lastTab = spf.getActiveTab()

        G.bottomBarVM = BottomBarViewModel(
            onTabSelected = { this.onTabSelected(it) },
            onTabReSelected = { this.onTabReSelected(it) },
            tabItems = listOf(
                TabItem(
                    route = Def.CALL_TAB_ROUTE,
                    label = ctx.resources.getString(R.string.call),
                    icon = R.drawable.ic_call,
                    isSelected = mutableStateOf(lastTab == Def.CALL_TAB_ROUTE),
                    badge = {
                        Badge(count = G.callVM.records.count { !it.read })
                    }
                ) {
                    HistoryScreen(G.callVM)
                },
                TabItem(
                    route = Def.SMS_TAB_ROUTE,
                    label = ctx.resources.getString(R.string.sms),
                    icon = R.drawable.ic_sms,
                    isSelected = mutableStateOf(lastTab == Def.SMS_TAB_ROUTE),
                    badge = {
                        Badge(count = G.smsVM.records.count { !it.read })
                    },
                ) {
                    HistoryScreen(G.smsVM)
                },
                TabItem(
                    route = Def.SETTING_TAB_ROUTE,
                    label = ctx.resources.getString(R.string.setting),
                    icon = R.drawable.ic_settings,
                    isSelected = mutableStateOf(lastTab == Def.SETTING_TAB_ROUTE)
                ) {
                    SettingScreen()
                }
            )
        )

        Permissions.initLauncherSetAsCallScreeningApp(this)

        G.callVM.reload(ctx)
        G.smsVM.reload(ctx)

        setContent {
            val isDarkTheme = when (G.themeType.intValue) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            AppTheme(darkTheme = isDarkTheme) {
                // fix white statusbar text when forced to white theme
                WindowCompat.getInsetsController(window, LocalView.current)
                    .isAppearanceLightStatusBars = !isDarkTheme

                // Prepare for the permission launcher
                G.permissionChain.Compose()

                Main()

                // show warning if this app is running in work profile
                checkWorkProfile()
            }
        }
    }

    @Composable
    private fun Main() {
        // An extra surface to make the top-status-bar and bottom-system-bar
        //   to be the same color as the app background.
        Surface(modifier = M.fillMaxSize()) {
            Scaffold(
                modifier = M
                    .fillMaxSize()
                    .systemBarsPadding(), // fix bottom-bar behind system-bar
                snackbarHost = {
                    SnackbarHost(hostState = SnackBar.state) { // customize color
                        LeftDeleteSwipeWrapper(
                            left = SwipeInfo(
                                onSwipe = {
                                    SnackBar.dismiss()
                                },
                                background = {}, // don't show the red bg color and a "recycler bin"
                            )
                        ) {
                            Snackbar(
                                modifier = M.padding(horizontal = 10.dp),
                                containerColor = MayaBlue,
                                contentColor = Color.DarkGray,
                                actionColor = Color.DarkGray,
                                snackbarData = it
                            )
                        }
                    }
                },
                bottomBar = {
                    BottomBar(G.bottomBarVM)
                }
            ) { scaffoldPadding ->
                Column(
                    modifier = M
                        .padding(scaffoldPadding)
                        .fillMaxSize()
                ) {
                    G.bottomBarVM.tabItems.forEach {
                        if (it.isSelected.value) {
                            it.content()
                        }
                    }
                }
            }
        }
    }

    private fun onTabSelected(route: String) {
        spf.Global(this).setActiveTab(route)
        when (route) {
            Def.CALL_TAB_ROUTE -> Permissions.launcherSetAsCallScreeningApp(null)
            Def.SMS_TAB_ROUTE -> Permissions.requestReceiveSmsPermission(this)
        }
    }

    private fun onTabReSelected(route: String) {
        when (route) {
            Def.CALL_TAB_ROUTE -> Launcher.launchCallApp(this)
            Def.SMS_TAB_ROUTE -> Launcher.launchSMSApp(this)
        }
    }

    @SuppressLint("ComposableNaming")
    @Composable
    private fun checkWorkProfile() {
        val spf = spf.Global(this)
        val alreadyShown by remember { mutableStateOf(spf.hasPromptedForRunningInWorkProfile()) }
        val runningInWorkProf by remember { mutableStateOf(Util.isRunningInWorkProfile(this)) }

        val trigger = remember {
            mutableStateOf(!alreadyShown && runningInWorkProf)
        }
        if (trigger.value) {
            PopupDialog(
                trigger = trigger,
                content = {
                    GreyLabel(Str(R.string.warning_running_in_work_profile))
                },
                icon = { ResIcon(R.drawable.ic_warning, color = Color.Unspecified) },
                buttons = {
                    StrokeButton(
                        label = getString(R.string.dismiss),
                        color = DarkOrange,
                    ) {
                        trigger.value = false
                        spf.setPromptedForRunningInWorkProfile()
                    }
                }
            )
        }
    }
}