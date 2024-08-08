package spam.blocker.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import spam.blocker.BuildConfig
import spam.blocker.R
import spam.blocker.databinding.MainActivityBinding
import spam.blocker.db.CallTable
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.ui.history.CallViewModel
import spam.blocker.ui.history.SmsViewModel
import spam.blocker.ui.util.UI.Companion.applyTheme
import spam.blocker.util.Launcher
import spam.blocker.util.Permissions
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.Util


class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainActivityBinding

    private lateinit var navView: BottomNavigationView

    private lateinit var callViewModel: CallViewModel
    private lateinit var smsViewModel: SmsViewModel

    private var callTable = CallTable()
    private var smsTable = SmsTable()

    private lateinit var broadcastReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        setupEdgeToEdge() // call before setContentView

        super.onCreate(savedInstanceState)

        val spf = Global(this)

        // language
        Util.setLocale(this, spf.getLanguage())

        // theme
        applyTheme(spf.getThemeType())

        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDebug()

        // update the number indicator on the bottom nav view on data change
        setupViewModels()

        // create the `ActivityResultLauncher`,
        // must be initialized in MainActivity, and before `setupBottomNavView`.
        Permissions.initSetAsCallScreeningApp(this)

        setupBottomNavView()

        // update gui on incoming call
        setupGuiEvents()

        // require permission once
        requirePermissionsOnce()

        // show warning if this app is running in work profile
        checkWorkProfile()
    }

    // This should be called before setContentView.
    private fun setupEdgeToEdge() {
        enableEdgeToEdge(
//            statusBarStyle = SystemBarStyle.light(
//                Color.TRANSPARENT,
//                Color.TRANSPARENT,
//            )
        )

        // preserve spaces for the top status bar and the bottom nav bar(back/home/tasks)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )

            // The status bar becomes opaque when there is padding-top, not sure why,
            // how to make it transparent with padding-top?
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }
    private fun setupDebug() {
        if (BuildConfig.DEBUG) {
            test.exec(this)

            // for debugging only, detecting resource leak, eg: db cursor not closed
            Class.forName("dalvik.system.CloseGuard")
                .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
                .invoke(null, true)
        }
    }

    // open the last tab
    private fun setupStartupPage() {
        val startPage = intent.getStringExtra("startPage")

        // go to tab
        when (startPage) {
            "call" -> navView.selectedItemId = R.id.navigation_call
            "sms" -> navView.selectedItemId = R.id.navigation_sms
            else -> {
                // if not launched by param, restore the last active tab
                val spf = Global(this)
                when (spf.getActiveTab()) {
                    "call" -> navView.selectedItemId = R.id.navigation_call
                    "sms" -> navView.selectedItemId = R.id.navigation_sms
                    else -> navView.selectedItemId = R.id.navigation_setting
                }
            }
        }
    }

    private fun setupBottomNavView() {
        val spf = Global(this)

        // Bottom Nav View
        run {
            navView = binding.navView
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            val navController = navHostFragment.navController
            navView.setupWithNavController(navController)

            val showPassed = spf.getShowPassed()
            val showBlocked = spf.getShowBlocked()

            // update number indicator
            callViewModel.records.clear()
            callViewModel.records.addAll(callTable.listRecords(this).filter {
                (showPassed && it.isNotBlocked()) || (showBlocked && it.isBlocked())
            })
            smsViewModel.records.clear()
            smsViewModel.records.addAll(smsTable.listRecords(this).filter {
                (showPassed && it.isNotBlocked()) || (showBlocked && it.isBlocked())
            })

            setupStartupPage()


            navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.navigation_call -> {
                        Permissions.askAsScreeningApp(null)

                        spf.setActiveTab("call")
                    }

                    R.id.navigation_sms -> {
                        Permissions.requestReceiveSmsPermission(this)

                        spf.setActiveTab("sms")
                    }

                    R.id.navigation_setting -> {
                        spf.setActiveTab("setting")
                    }
                }
            }
            navView.setOnItemReselectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.navigation_call -> Launcher.launchCallApp(this)
                    R.id.navigation_sms -> Launcher.launchSMSApp(this)
                }
            }
        }
    }

    private fun setupViewModels() {
        callViewModel = ViewModelProvider(this)[CallViewModel::class.java]
        callViewModel.records.observe(this) {
            updateNavCallBadge()
        }
        smsViewModel = ViewModelProvider(this)[SmsViewModel::class.java]
        smsViewModel.records.observe(this) {
            updateNavSmsBadge()
        }
    }
    private fun setupGuiEvents() {
        val ctx = this
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                Log.d(Def.TAG, "on receive broadcast: $action")

                when (action) {
                    Def.ON_NEW_CALL -> {
                        val id = intent.getLongExtra("record_id", 0)
                        val call = callTable.findRecordById(ctx, id)
                        callViewModel.records.add(0, call!!)
                    }
                    Def.ON_NEW_SMS -> {
                        val id = intent.getLongExtra("record_id", 0)
                        val sms = smsTable.findRecordById(ctx, id)
                        smsViewModel.records.add(0, sms!!)
                    }
                }
            }
        }
        registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(Def.ON_NEW_CALL)
            addAction(Def.ON_NEW_SMS)
        }, Context.RECEIVER_EXPORTED)
    }

    private fun requirePermissionsOnce() {
        val spf = Global(this)
        if (!spf.hasAskedForAllPermissions()) {
            spf.setAskedForAllPermission()
            Permissions.requestAllManifestPermissions(this)

            Permissions.askAsScreeningApp(null)
        }
    }

    private fun checkWorkProfile() {
        val spf = Global(this)
        if (Util.isRunningInWorkProfile(this)) {
            if (!spf.hasPromptedForRunningInWorkProfile()) {
                AlertDialog.Builder(this).apply {
                    setTitle(" ")
                    setIcon(R.drawable.ic_warning)
                    setMessage(resources.getString(R.string.warning_running_in_work_profile))
                    setPositiveButton(R.string.ignore) { _,_ ->
                        spf.setPromptedForRunningInWorkProfile()
                    }
                }.create().show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

//    private fun refreshCurrentFragment() {
//        val navHostFragment =
//            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
//        val navController = navHostFragment.findNavController()
//        navController.navigate(navController.currentDestination!!.id)
//    }

    private fun updateNavCallBadge() {
        val unreadCount = callViewModel.records.count { !it.read }
        val navView: BottomNavigationView = binding.navView
        navView.getOrCreateBadge(R.id.navigation_call).apply {
            number = unreadCount
            isVisible = unreadCount != 0
            setBackgroundColor(getColor(R.color.salmon));
        }
    }

    private fun updateNavSmsBadge() {
        val unreadCount = smsViewModel.records.count { !it.read }
        val navView: BottomNavigationView = binding.navView
        navView.getOrCreateBadge(R.id.navigation_sms).apply {
            number = unreadCount
            isVisible = unreadCount != 0
            setBackgroundColor(getColor(R.color.salmon));
        }
    }
}