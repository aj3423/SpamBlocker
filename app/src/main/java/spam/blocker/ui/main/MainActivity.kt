package spam.blocker.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
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
import spam.blocker.util.Launcher
import spam.blocker.util.Permission
import spam.blocker.util.SharedPref
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

        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.DEBUG) {
            test.exec(this)
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

        val spf = SharedPref(this)
        val promptIfScreeningServiceNotEnabled = Permission.promptSetAsDefaultCallScreeningApp(this)


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

            // go to last tab
            when (startFromNotification) {
                "call" -> navView.selectedItemId = R.id.navigation_call
                "sms" -> navView.selectedItemId = R.id.navigation_sms
                else -> {
                    // if not launched by clicking notification, restore the last active page
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
                        promptIfScreeningServiceNotEnabled()

                        spf.setActiveTab("call")
                    }

                    R.id.navigation_sms -> {
                        if (!Permission.isSmsPermissionGranted(this)) {
                            Permission.requestSmsPermissions(this)
                        }
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


        // theme
        Util.applyTheme(SharedPref(this).isDarkTheme())

        // require permission once
        if (!spf.hasAskedForAllPermissions()) {
            spf.setAskedForAllPermission()
            Permission.requestAllManifestPermissions(this)
            promptIfScreeningServiceNotEnabled()
        }

        // highlight the top status bar
        //   green == enabled,  red == disabled
        window.statusBarColor = ContextCompat.getColor(
            this,
            if (spf.isGloballyEnabled()) R.color.dark_sea_green else R.color.salmon
        )


//         for debugging only, detecting resource leak, eg: db cursor not closed
//        Class.forName("dalvik.system.CloseGuard")
//            .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
//            .invoke(null, true)
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun refreshCurrentFragment() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.findNavController()
        navController.navigate(navController.currentDestination!!.id)
    }

    private fun updateNavCallBadge() {
        val unreadCount = callViewModel.records.count { !it.read }
        val navView: BottomNavigationView = binding.navView
        navView.getOrCreateBadge(R.id.navigation_call).apply {
            number = unreadCount
            isVisible = unreadCount != 0
        }
    }

    private fun updateNavSmsBadge() {
        val unreadCount = smsViewModel.records.count { !it.read }
        val navView: BottomNavigationView = binding.navView
        navView.getOrCreateBadge(R.id.navigation_sms).apply {
            number = unreadCount
            isVisible = unreadCount != 0
        }
    }
}