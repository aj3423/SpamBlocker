package spam.blocker.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
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
        super.onCreate(savedInstanceState)

        val spf = Global(this)

        // language
        Util.setLocale(this, spf.getLanguage())

        // theme
        applyTheme(spf.getThemeType())

        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.DEBUG) {
            test.exec(this)

            // for debugging only, detecting resource leak, eg: db cursor not closed
            Class.forName("dalvik.system.CloseGuard")
                .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
                .invoke(null, true)
        }

        // launched by clicking notification
        val startFromNotification = intent.getStringExtra("startPage")

        // View Models
        run { // update the number indicator on the bottom nav view on data change
            callViewModel = ViewModelProvider(this)[CallViewModel::class.java]
            callViewModel.records.observe(this) {
                updateNavCallBadge()
            }
            smsViewModel = ViewModelProvider(this)[SmsViewModel::class.java]
            smsViewModel.records.observe(this) {
                updateNavSmsBadge()
            }
        }

        Permissions.initSetAsCallScreeningApp(this)

        // Bottom Nav View
        run {
            navView = binding.navView
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
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

            // go to tab
            when (startFromNotification) {
                "call" -> navView.selectedItemId = R.id.navigation_call
                "sms" -> navView.selectedItemId = R.id.navigation_sms
                else -> {
                    // if not launched by clicking notification, restore the last active tab
                    when (spf.getActiveTab()) {
                        "call" -> navView.selectedItemId = R.id.navigation_call
                        "sms" -> navView.selectedItemId = R.id.navigation_sms
                        else -> navView.selectedItemId = R.id.navigation_setting
                    }
                }
            }


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


        val ctx = this
        // update gui on incoming call
        run {
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


        // highlight the top status bar
        //   green == enabled,  red == disabled
        window.statusBarColor = ContextCompat.getColor(
            this,
            if (spf.isGloballyEnabled()) R.color.text_green else R.color.salmon
        )

        // require permission once
        if (!spf.hasAskedForAllPermissions()) {
            spf.setAskedForAllPermission()
            Permissions.requestAllManifestPermissions(this)

            Permissions.askAsScreeningApp(null)
        }

        // show warning if this app is running in work profile
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